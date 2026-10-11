@file:Suppress("DEPRECATION")   // characteristic ctor/setValue: deprecated in 33, functional everywhere

package com.example.metrotune.audio

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import java.util.Collections
import java.util.UUID

/**
 * BLE-MIDI peripheral (Apple BLE-MIDI spec, MIDI Association adopted):
 * advertises the standard MIDI service so any central — DAW, rtpMIDI,
 * BLE-MIDI host — can connect and receive notes/pitch bend/clock.
 *
 * Outgoing packet: header byte (0x80 | ts[12:7]) + timestamp byte
 * (0x80 | ts[6:0]) + raw MIDI message. One message per notification,
 * no running status — decodable by every host implementation.
 */
class BleMidiServer(context: Context) {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("03B80E5A-E8C8-4DB9-A358-B74708229691")
        val CHAR_UUID: UUID = UUID.fromString("7772E5DB-3868-4112-A1A9-B14E87BD662C")
        const val PITCH_BEND_SEMITONES = 2.0
    }

    @Volatile var channel = 0
    var isRunning = false; private set
    var connectedCount = 0; private set
    var statusListener: ((String) -> Unit)? = null

    private val appContext = context.applicationContext
    private var gatt: BluetoothGattServer? = null
    private var advertiser: android.bluetooth.le.BluetoothLeAdvertiser? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private val subscribers =
        Collections.newSetFromMap(java.util.HashMap<android.bluetooth.BluetoothDevice, Boolean>())

    fun start(): Boolean {
        if (isRunning) return true
        val bm = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return false
        val adapter = bm.adapter?.takeIf { it.isEnabled } ?: return false

        val chr = BluetoothGattCharacteristic(
            CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE)
        val svc = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        svc.addCharacteristic(chr)
        characteristic = chr

        val server = try { bm.openGattServer(appContext, gattCb) }
                      catch (_: SecurityException) { null } ?: return false
        gatt = server
        server.addService(svc)

        advertiser = try { adapter.bluetoothLeAdvertiser } catch (_: Exception) { null }
        if (advertiser == null) { stop(); return false }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(true)
            .build()
        try { advertiser?.startAdvertising(settings, data, advCb) } catch (_: SecurityException) {}

        isRunning = true
        post("advertising")
        return true
    }

    fun stop() {
        isRunning = false
        try { advertiser?.stopAdvertising(advCb) } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        advertiser = null; gatt = null; characteristic = null
        subscribers.clear(); connectedCount = 0
        post("off")
    }

    fun sendNoteOn(midi: Int, velocity: Int) =
        send(byteArrayOf3(0x90 or (channel and 15), midi.coerceIn(0, 127), velocity.coerceIn(1, 127)))

    fun sendNoteOff(midi: Int) =
        send(byteArrayOf3(0x80 or (channel and 15), midi.coerceIn(0, 127), 64))

    /** Continuous tuning data: ±PITCH_BEND_SEMITONES → 14-bit bend, 8192 center. */
    fun sendPitchBend(cents: Double) {
        val v = (8192.0 + (cents / (PITCH_BEND_SEMITONES * 100.0)) * 8191.0)
            .toInt().coerceIn(0, 16383)
        send(byteArrayOf3(0xE0 or (channel and 15), v and 0x7F, (v shr 7) and 0x7F))
    }

    /** Standard real-time messages; send() wraps them in BLE-MIDI header+timestamp. */
    fun sendClock() = send(byteArrayOf(0xF8.toByte()))   // 24 per quarter note
    fun sendStart() = send(byteArrayOf(0xFA.toByte()))
    fun sendStop()  = send(byteArrayOf(0xFC.toByte()))

    // ---------------- internals ----------------

    private val advCb = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) = post("advertising failed ($errorCode)")
    }

    private val gattCb = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(
            device: android.bluetooth.BluetoothDevice?, status: Int, newState: Int
        ) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> device?.let { subscribers.add(it) }
                BluetoothProfile.STATE_DISCONNECTED -> device?.let { subscribers.remove(it) }
            }
            connectedCount = subscribers.size
            post(if (subscribers.isEmpty()) "advertising" else "connected ×${subscribers.size}")
        }

        override fun onDescriptorWriteRequest(
            device: android.bluetooth.BluetoothDevice?, requestId: Int,
            characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean,
            responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            if (characteristic?.uuid == CHAR_UUID) device?.let { subscribers.add(it) }
            if (responseNeeded) gatt?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onCharacteristicWriteRequest(
            device: android.bluetooth.BluetoothDevice?, requestId: Int,
            characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean,
            responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            // We only transmit; incoming MIDI from the host is acknowledged and ignored.
            if (responseNeeded) gatt?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }

    @Synchronized
    private fun send(midi: ByteArray) {
        if (!isRunning || subscribers.isEmpty()) return
        val chr = characteristic ?: return
        val ts = (System.currentTimeMillis() and 0x1FFF).toInt()
        val packet = ByteArray(midi.size + 2)
        packet[0] = (0x80 or ((ts shr 7) and 0x3F)).toByte()   // header: ts bits 12:7
        packet[1] = (0x80 or (ts and 0x7F)).toByte()           // timestamp: ts bits 6:0
        System.arraycopy(midi, 0, packet, 2, midi.size)
        try { chr.setValue(packet) } catch (_: Exception) { return }
        for (d in subscribers.toList()) {
            try { gatt?.notifyCharacteristicChanged(d, chr, false) } catch (_: SecurityException) {}
        }
    }

    private fun byteArrayOf3(a: Int, b: Int, c: Int) =
        byteArrayOf(a.toByte(), b.toByte(), c.toByte())

    private fun post(s: String) { statusListener?.invoke(s) }
}
