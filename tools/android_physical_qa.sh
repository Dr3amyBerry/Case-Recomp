#!/usr/bin/env bash
set -euo pipefail

BASELINE_APK="${1:?baseline versionCode 7 synthetic APK path}"
CANDIDATE_APK="${2:?candidate versionCode 8 synthetic APK path}"
OUT="${3:?new output directory}"
SERIAL="${ANDROID_SERIAL:?set ANDROID_SERIAL to one explicitly selected physical device}"
PKG="org.rigorcore.caserecomp.synthetic.debug"
ACT="$PKG/org.rigorcore.caserecomp.app.MainActivity"
REMOTE_SAVE="/data/local/tmp/case-recomp-physical-save.xml"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required" >&2
  exit 1
fi
test -f "$BASELINE_APK"
test -f "$CANDIDATE_APK"
if [ -e "$OUT" ]; then
  echo "output directory must not already exist" >&2
  exit 1
fi
mkdir "$OUT"

ADB=(adb -s "$SERIAL")
test "$("${ADB[@]}" get-state | tr -d '\r')" = "device"
QEMU="$("${ADB[@]}" shell getprop ro.kernel.qemu | tr -d '\r')"
case "$SERIAL" in emulator-*) QEMU=1 ;; esac
if [ "$QEMU" = "1" ]; then
  echo "physical-device QA refuses emulators" >&2
  exit 1
fi

if "${ADB[@]}" shell pm path "$PKG" 2>/dev/null | tr -d '\r' | grep -q '^package:'; then
  echo "synthetic debug package already exists on selected device; refusing to overwrite its data" >&2
  exit 1
fi

INSTALLED_BY_SCRIPT=0
cleanup() {
  "${ADB[@]}" shell rm -f "$REMOTE_SAVE" >/dev/null 2>&1 || true
  if [ "$INSTALLED_BY_SCRIPT" = "1" ]; then
    "${ADB[@]}" uninstall "$PKG" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

{
  printf 'physical_device=true\n'
  printf 'api=%s\n' "$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
  printf 'abi=%s\n' "$("${ADB[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
  printf 'screen=%s\n' "$("${ADB[@]}" shell wm size | tr -d '\r')"
  printf 'density=%s\n' "$("${ADB[@]}" shell wm density | tr -d '\r')"
} > "$OUT/device-properties.txt"

"${ADB[@]}" install "$BASELINE_APK" >/dev/null
INSTALLED_BY_SCRIPT=1
BASELINE_VERSION="$("${ADB[@]}" shell dumpsys package "$PKG" | sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' | head -1 | tr -d '\r')"
test "$BASELINE_VERSION" = "7"

START_OUT="$("${ADB[@]}" shell am start -S -W -n "$ACT")"
printf '%s\n' "$START_OUT" > "$OUT/startup.txt"
START_MS="$(printf '%s\n' "$START_OUT" | awk -F: '/TotalTime/{gsub(/ /,"",$2);print $2;exit}')"
: "${START_MS:=0}"
test "$START_MS" -gt 0

sleep 1
MAP_READY=0
for _ in $(seq 1 12); do
  "${ADB[@]}" shell input tap 400 300 >/dev/null
  sleep 0.4
  if "${ADB[@]}" exec-out run-as "$PKG" cat shared_prefs/case-recomp-session-slots.xml 2>/dev/null | grep -q 'screen=MAP'; then
    MAP_READY=1
    break
  fi
done
test "$MAP_READY" = "1"
"${ADB[@]}" exec-out run-as "$PKG" cat shared_prefs/case-recomp-session-slots.xml > "$OUT/save-before-force-stop.xml"
grep -q 'screen=MAP' "$OUT/save-before-force-stop.xml"

"${ADB[@]}" shell am force-stop "$PKG"
"${ADB[@]}" shell am start -W -n "$ACT" >/dev/null
sleep 1
"${ADB[@]}" exec-out run-as "$PKG" cat shared_prefs/case-recomp-session-slots.xml > "$OUT/save-after-force-stop.xml"
grep -q 'screen=MAP' "$OUT/save-after-force-stop.xml"

"${ADB[@]}" install -r "$CANDIDATE_APK" >/dev/null
CANDIDATE_VERSION="$("${ADB[@]}" shell dumpsys package "$PKG" | sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' | head -1 | tr -d '\r')"
test "$CANDIDATE_VERSION" = "8"
"${ADB[@]}" shell am force-stop "$PKG"
"${ADB[@]}" shell am start -W -n "$ACT" >/dev/null
sleep 1
"${ADB[@]}" exec-out run-as "$PKG" cat shared_prefs/case-recomp-session-slots.xml > "$OUT/save-after-upgrade.xml"
grep -q 'screen=MAP' "$OUT/save-after-upgrade.xml"

"${ADB[@]}" push "$OUT/save-after-upgrade.xml" "$REMOTE_SAVE" >/dev/null
"${ADB[@]}" shell pm clear "$PKG" >/dev/null
"${ADB[@]}" shell "run-as '$PKG' mkdir -p shared_prefs && run-as '$PKG' cp '$REMOTE_SAVE' shared_prefs/case-recomp-session-slots.xml"
"${ADB[@]}" shell am start -W -n "$ACT" >/dev/null
sleep 1
"${ADB[@]}" exec-out run-as "$PKG" cat shared_prefs/case-recomp-session-slots.xml > "$OUT/save-after-restore.xml"
grep -q 'screen=MAP' "$OUT/save-after-restore.xml"

MEM="$("${ADB[@]}" shell dumpsys meminfo "$PKG")"
printf '%s\n' "$MEM" > "$OUT/meminfo.txt"
PSS="$(printf '%s\n' "$MEM" | awk '/TOTAL PSS:/{print $3;exit} /TOTAL/{if ($2 ~ /^[0-9]+$/) {print $2;exit}}')"
: "${PSS:=0}"
test "$PSS" -gt 0

"${ADB[@]}" shell dumpsys gfxinfo "$PKG" reset >/dev/null || true
for _ in $(seq 1 20); do "${ADB[@]}" shell input tap 100 100 >/dev/null; done
sleep 1
GFX="$("${ADB[@]}" shell dumpsys gfxinfo "$PKG" || true)"
printf '%s\n' "$GFX" > "$OUT/gfxinfo.txt"
JANKY_PERCENT="$(printf '%s\n' "$GFX" | sed -n 's/.*Janky frames:.*(\([0-9.][0-9.]*\)%).*/\1/p' | head -1)"
if [ -n "$JANKY_PERCENT" ]; then JANKY_JSON="$JANKY_PERCENT"; else JANKY_JSON="null"; fi

"${ADB[@]}" shell dumpsys package "$PKG" > "$OUT/package.txt"
if grep -q 'ALLOW_BACKUP' "$OUT/package.txt"; then
  echo "OS backup unexpectedly enabled" >&2
  exit 1
fi

API="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
cat > "$OUT/runtime-metrics.json" <<EOF
{"format":"case-recomp-post-phase8-physical-metrics","version":1,"physical_device":true,"api":"$API","baseline_version_code":$BASELINE_VERSION,"candidate_version_code":$CANDIDATE_VERSION,"cold_start_ms":$START_MS,"total_pss_kb":$PSS,"render_janky_percent":$JANKY_JSON,"process_kill_restore":"pass","upgrade_install_preserves_save":"pass","synthetic_save_backup_restore":"pass","os_backup_enabled":false,"budget_mode":"informational"}
EOF
