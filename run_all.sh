#!/usr/bin/env bash
# MetroTune — run all generator parts found next to this script.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
T="${1:-metrotune}"
shopt -s nullglob
scripts=( "$DIR"/metrotune_*.sh )
if [ ${#scripts[@]} -lt 2 ]; then
  echo "No metrotune_*.sh parts found in $DIR"; exit 1
fi
for s in "${scripts[@]}"; do
  [ "$(basename "$s")" = "run_all.sh" ] && continue
  echo ">>> $(basename "$s")"
  bash "$s" "$T"
done
KT=$(find "$T/app/src" -name '*.kt' 2>/dev/null | wc -l)
echo ""
echo "=================================================="
echo " MetroTune generated into ./$T"
echo " Kotlin files: $KT  (expected 91 = 74 main + 17 test)"
echo " Next:"
echo "   cd $T"
echo "   gradle wrapper --gradle-version 8.7"
echo "   ./gradlew spotlessApply"
echo "   ./gradlew testDebugUnitTest lintDebug assembleDebug"
echo "=================================================="
