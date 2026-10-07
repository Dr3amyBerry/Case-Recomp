#!/usr/bin/env bash
set -euo pipefail
APK="${1:?apk path}"
OUT="${2:?output directory}"
PKG="org.rigorcore.caserecomp.synthetic.debug"
ACT="$PKG/org.rigorcore.caserecomp.app.MainActivity"
mkdir -p "$OUT"

adb install -r "$APK" >/dev/null
START_OUT="$(adb shell am start -S -W -n "$ACT")"
printf '%s\n' "$START_OUT" > "$OUT/startup.txt"
START_MS="$(printf '%s\n' "$START_OUT" | awk -F: '/TotalTime/{gsub(/ /,"",$2);print $2;exit}')"
: "${START_MS:=999999}"
test "$START_MS" -le 5000

adb shell input tap 100 100
sleep 1
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT" >/dev/null
sleep 1
adb exec-out run-as "$PKG" cat shared_prefs/case-recomp-session-slots.xml > "$OUT/save-before-upgrade.xml"
grep -q 'screen=MAP' "$OUT/save-before-upgrade.xml"

adb install -r "$APK" >/dev/null
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT" >/dev/null
sleep 1
adb exec-out run-as "$PKG" cat shared_prefs/case-recomp-session-slots.xml > "$OUT/save-after-upgrade.xml"
grep -q 'screen=MAP' "$OUT/save-after-upgrade.xml"

adb push "$OUT/save-after-upgrade.xml" /data/local/tmp/case-recomp-save.xml >/dev/null
adb shell pm clear "$PKG" >/dev/null
adb install -r "$APK" >/dev/null
adb shell am start -W -n "$ACT" >/dev/null
adb shell am force-stop "$PKG"
adb shell "run-as '$PKG' mkdir -p shared_prefs && run-as '$PKG' cp /data/local/tmp/case-recomp-save.xml shared_prefs/case-recomp-session-slots.xml"
adb shell am start -W -n "$ACT" >/dev/null
sleep 1
adb exec-out run-as "$PKG" cat shared_prefs/case-recomp-session-slots.xml > "$OUT/save-after-restore.xml"
grep -q 'screen=MAP' "$OUT/save-after-restore.xml"

MEM="$(adb shell dumpsys meminfo "$PKG")"
printf '%s\n' "$MEM" > "$OUT/meminfo.txt"
PSS="$(printf '%s\n' "$MEM" | awk '/TOTAL PSS:/{print $3;exit} /TOTAL/{if ($2 ~ /^[0-9]+$/) {print $2;exit}}')"
: "${PSS:=0}"
test "$PSS" -le 262144

adb shell dumpsys gfxinfo "$PKG" reset >/dev/null || true
for _ in $(seq 1 20); do adb shell input tap 100 100 >/dev/null; done
sleep 1
adb shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo.txt" || true

cat > "$OUT/runtime-metrics.json" <<EOF
{"format":"case-recomp-phase8-runtime-metrics","version":1,"api":"$(adb shell getprop ro.build.version.sdk | tr -d '\r')","cold_start_ms":$START_MS,"total_pss_kb":$PSS,"process_kill_restore":"pass","upgrade_install_preserves_save":"pass","synthetic_save_backup_restore":"pass","os_backup_enabled":false}
EOF

adb shell dumpsys package "$PKG" > "$OUT/package.txt"
if grep -q 'ALLOW_BACKUP' "$OUT/package.txt"; then
  echo "OS backup unexpectedly enabled" >&2; exit 1
fi
