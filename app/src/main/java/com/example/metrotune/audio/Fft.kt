package com.example.metrotune.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 FFT (size must be a power of two). */
object Fft {

    fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val half = len shr 1
            val wStepRe = cos(2.0 * PI / len)
            val wStepIm = -sin(2.0 * PI / len)
            for (start in 0 until n step len) {
                var wRe = 1.0
                var wIm = 0.0
                for (k in 0 until half) {
                    val a = start + k
                    val b = a + half
                    val vRe = re[b] * wRe - im[b] * wIm
                    val vIm = re[b] * wIm + im[b] * wRe
                    re[b] = re[a] - vRe.toFloat()
                    im[b] = im[a] - vIm.toFloat()
                    re[a] += vRe.toFloat()
                    im[a] += vIm.toFloat()
                    val nWRe = wRe * wStepRe - wIm * wStepIm
                    wIm = wRe * wStepIm + wIm * wStepRe
                    wRe = nWRe
                }
            }
            len = len shl 1
        }
    }

    /** Inverse FFT via the conjugate method. */
    fun ifft(re: FloatArray, im: FloatArray) {
        val n = re.size
        for (i in 0 until n) im[i] = -im[i]
        fft(re, im)
        val inv = 1f / n
        for (i in 0 until n) { re[i] *= inv; im[i] = -im[i] }
    }
}
