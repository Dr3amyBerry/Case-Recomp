#!/usr/bin/env bash
set -euo pipefail
BASELINE_APK="${1:?baseline apk path}"
CANDIDATE_APK="${2:?candidate apk path}"
OUT="${3:?output directory}"
PKG="org.rigorcore.caserecomp.synthetic.debug"
ACT="$PKG/org.rigorcore.caserecomp.app.MainActivity"
mkdir -p "$OUT"

adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install "$BASELINE_APK" >/dev/null
BASELINE_VERSION="$(adb shell dumpsys package "$PKG" | sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' | head -1 | tr -d '\r')"
test "$BASELINE_VERSION" = "7"

START_OUT="$(adb shell am start -S -W -n "$ACT")"
printf '%s\n' "$START_OUT" > "$OUT/startup.txt"
START_MS="$(printf '%s\n' "$START_OUT" | awk -F: '/TotalTime/{gsub(/ /,"",$2);print $2;exit}')"
: "${START_MS:=999999}"
test "$START_MS" -le 5000

# Synthetic MENU -> MAP dispatch must autosave before any lifecycle callback.
adb shell input tap 100 100
sleep 1
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT" >/dev/null
sleep 1
adb exec-out run-as "$PKG" cat shared_prefs/case-recomp-session-slots.xml > "$OUT/save-after-force-stop.xml"
grep -q 'screen=MAP' "$OUT/save-after-force-stop.xml"

# True in-place upgrade: same package, strictly higher versionCode.
adb install -r "$CANDIDATE_APK" >/dev/null
CANDIDATE_VERSION="$(adb shell dumpsys package "$PKG" | sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' | head -1 | tr -d '\r')"
test "$CANDIDATE_VERSION" = "8"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT" >/dev/null
sleep 1
adb exec-out run-as "$PKG" cat shared_prefs/case-recomp-session-slots.xml > "$OUT/save-after-upgrade.xml"
grep -q 'screen=MAP' "$OUT/save-after-upgrade.xml"

# Deliberate app-level backup/restore of the synthetic save only. Android OS/cloud
# backup remains disabled because private content can contain locally owned media.
adb push "$OUT/save-after-upgrade.xml" /data/local/tmp/case-recomp-save.xml >/dev/null
adb shell pm clear "$PKG" >/dev/null
adb install -r "$CANDIDATE_APK" >/dev/null
adb shell "run-as '$PKG' mkdir -p shared_prefs && run-as '$PKG' cp /data/local/tmp/case-recomp-save.xml shared_prefs/case-recomp-session-slots.xml"
adb shell am start -W -n "$ACT" >/dev/null
sleep 1
adb exec-out run-as "$PKG" cat shared_prefs/case-recomp-session-slots.xml > "$OUT/save-after-restore.xml"
grep -q 'screen=MAP' "$OUT/save-after-restore.xml"

MEM="$(adb shell dumpsys meminfo "$PKG")"
printf '%s\n' "$MEM" > "$OUT/meminfo.txt"
PSS="$(printf '%s\n' "$MEM" | awk '/TOTAL PSS:/{print $3;exit} /TOTAL/{if ($2 ~ /^[0-9]+$/) {print $2;exit}}')"
: "${PSS:=0}"
test "$PSS" -gt 0
test "$PSS" -le 262144

adb shell dumpsys gfxinfo "$PKG" reset >/dev/null || true
for _ in $(seq 1 20); do adb shell input tap 100 100 >/dev/null; done
sleep 1
GFX="$(adb shell dumpsys gfxinfo "$PKG" || true)"
printf '%s\n' "$GFX" > "$OUT/gfxinfo.txt"
JANKY_PERCENT="$(printf '%s\n' "$GFX" | sed -n 's/.*Janky frames:.*(\([0-9.][0-9.]*\)%).*/\1/p' | head -1)"
if [ -n "$JANKY_PERCENT" ]; then
  awk -v value="$JANKY_PERCENT" 'BEGIN { exit !(value <= 25.0) }'
  JANKY_JSON="$JANKY_PERCENT"
else
  JANKY_JSON="null"
fi

adb shell dumpsys package "$PKG" > "$OUT/package.txt"
if grep -q 'ALLOW_BACKUP' "$OUT/package.txt"; then
  echo "OS backup unexpectedly enabled" >&2
  exit 1
fi

cat > "$OUT/runtime-metrics.json" <<EOF
{"format":"case-recomp-phase8-runtime-metrics","version":2,"api":"$(adb shell getprop ro.build.version.sdk | tr -d '\r')","baseline_version_code":$BASELINE_VERSION,"candidate_version_code":$CANDIDATE_VERSION,"cold_start_ms":$START_MS,"total_pss_kb":$PSS,"render_janky_percent":$JANKY_JSON,"process_kill_restore":"pass","upgrade_install_preserves_save":"pass","synthetic_save_backup_restore":"pass","os_backup_enabled":false}
EOF
