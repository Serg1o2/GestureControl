#!/usr/bin/env bash
# Measures battery drain and temperature of a device while GestureControl runs.
#
# Usage: scripts/measure-power.sh <label> [minutes] [package]
#   label    free text, e.g. "active-hand" or "idle-no-hand"
#   minutes  test duration, default 30
#
# Prefer wireless ADB (adb pair / adb connect): USB charging heats the phone and
# distorts the result even though the script marks the battery as unplugged.
set -euo pipefail

LABEL="${1:?label required}"
MINUTES="${2:-30}"
PKG="${3:-com.gesturecontrol}"
OUT="measurements/$(date +%Y%m%d_%H%M%S)_${LABEL}"
mkdir -p "$OUT"

battery_field() { adb shell dumpsys battery | tr -d '\r' | awk -F': ' -v k="  $1" '$1==k {print $2}'; }

echo "Device: $(adb shell getprop ro.product.model | tr -d '\r'), Android $(adb shell getprop ro.build.version.release | tr -d '\r')" | tee "$OUT/summary.txt"
echo "Label: $LABEL, duration: ${MINUTES} min" | tee -a "$OUT/summary.txt"

adb shell dumpsys battery unplug
adb shell dumpsys batterystats --reset > /dev/null
START_LEVEL=$(battery_field level)
START_TEMP=$(battery_field temperature)

echo "time_s,level_pct,battery_temp_c" > "$OUT/samples.csv"
END=$(( $(date +%s) + MINUTES * 60 ))
T0=$(date +%s)
while [ "$(date +%s)" -lt "$END" ]; do
    L=$(battery_field level)
    T=$(battery_field temperature)
    echo "$(( $(date +%s) - T0 )),$L,$(awk "BEGIN{print $T/10}")" | tee -a "$OUT/samples.csv"
    sleep 30
done

END_LEVEL=$(battery_field level)
END_TEMP=$(battery_field temperature)
adb shell dumpsys batterystats "$PKG" > "$OUT/batterystats_app.txt" || true
adb shell dumpsys batterystats > "$OUT/batterystats_full.txt" || true
adb shell dumpsys thermalservice > "$OUT/thermal.txt" 2>/dev/null || true
adb shell dumpsys battery reset

{
    echo "Battery level: ${START_LEVEL}% -> ${END_LEVEL}% ($(( START_LEVEL - END_LEVEL )) pp in ${MINUTES} min)"
    echo "Battery temperature: $(awk "BEGIN{print $START_TEMP/10}") C -> $(awk "BEGIN{print $END_TEMP/10}") C"
    echo "App power estimate (mAh), see batterystats_full.txt 'Estimated power use':"
    UID_NUM=$(adb shell dumpsys package "$PKG" | tr -d '\r' | awk -F'=' '/userId=/{print $2; exit}' | awk '{print $1}')
    grep -E "UID u0a$(( UID_NUM - 10000 ))[: ]" "$OUT/batterystats_full.txt" | head -5 || echo "  (not found)"
} | tee -a "$OUT/summary.txt"

echo "Results: $OUT"
