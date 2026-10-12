#!/usr/bin/env bash
# Fixture data only: debug APK + pane://sample links + the x-image fixture URL.
# No accounts, no real History. The raw log never leaves the runner.
set -euo pipefail
APK=app/build/outputs/apk/debug/app-debug.apk
OUT=build/maestro
PKG=app.pane.android
mkdir -p "$OUT"
adb wait-for-device
# Pixel Launcher ANRs on this slow image, and the dialog sits on top of Pane.
# The setting blocks later dialogs. force-stop clears one that is already up.
adb shell settings put global hide_error_dialogs 1
adb shell settings get global hide_error_dialogs >> "$OUT/locale.txt" || true
adb shell am force-stop com.google.android.apps.nexuslauncher || true
adb install -r "$APK"
adb shell settings put system accelerometer_rotation 1
adb shell getprop persist.sys.locale >> "$OUT/locale.txt" || true
adb shell settings get system system_locales >> "$OUT/locale.txt" || true
# Share-in precondition for 01_share_in.yaml (fixture URL from SamplePosts x-image).
adb shell am start -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "https://x.com/mayaren/status/1842700101" \
  -n "$PKG/.MainActivity"
adb logcat -c
# App-only logcat. `logcat --pid` stays bound to the first process, so clearState
# used to leave the rest of the run uncaptured. Restart the stream on each new pid.
(
  current=""
  logcat_pid=""
  while true; do
    pid=$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)
    if [ -n "$pid" ] && [ "$pid" != "$current" ]; then
      if [ -n "$logcat_pid" ]; then
        kill "$logcat_pid" 2>/dev/null || true
        wait "$logcat_pid" 2>/dev/null || true
      fi
      current=$pid
      echo "----- pid $pid -----" >> "$OUT/logcat-app.raw"
      adb logcat -v threadtime --pid="$pid" >> "$OUT/logcat-app.raw" 2>/dev/null &
      logcat_pid=$!
    fi
    sleep 1
  done
) &
LOGLOOP=$!
# AndroidRuntime crash lines, captured separately and filtered to PKG below.
adb logcat -v threadtime -s AndroidRuntime:E > "$OUT/crash.raw" 2>/dev/null &
CRASH=$!
set +e
# -e is required. config.yaml env did not expand ${TEXT_*} on the emulator.
# Pass files in order. Maestro 2.11 did not honor executionOrder.flowsOrder
# and ran Undo (clearState) before the share-in that depends on the SEND intent.
maestro test \
  .maestro/01_share_in.yaml \
  .maestro/02_swipe_commit_45.yaml \
  .maestro/03_star_first_then_remove.yaml \
  .maestro/04_undo.yaml \
  .maestro/05_filters.yaml \
  .maestro/06a_fullscreen_auto_rotate.yaml \
  -e "TEXT_REMOVED=Removed from History" \
  -e "TEXT_UNDO=Undo" \
  -e "TEXT_APPS=APPS" \
  -e "TEXT_SHOW=Show 1 post" \
  --format junit \
  --output "$OUT/junit.xml" \
  --test-output-dir "$OUT/artifacts"
rc1=$?
adb shell settings put system accelerometer_rotation 0
maestro test .maestro/06b_fullscreen_rotate_button.yaml \
  -e "TEXT_REMOVED=Removed from History" \
  -e "TEXT_UNDO=Undo" \
  -e "TEXT_APPS=APPS" \
  -e "TEXT_SHOW=Show 1 post" \
  --format junit \
  --output "$OUT/junit-06b.xml" \
  --test-output-dir "$OUT/artifacts"
rc2=$?
adb shell settings put system accelerometer_rotation 1
{
  echo "after-06b"
  adb shell dumpsys window | grep -m1 mCurrentRotation || true
} >> "$OUT/rotation.txt"
pkill -P "$LOGLOOP" 2>/dev/null || true
kill "$LOGLOOP" "$CRASH" 2>/dev/null || true
wait "$LOGLOOP" "$CRASH" 2>/dev/null || true
# Keep only crash blocks that name our process.
if [ -f "$OUT/crash.raw" ]; then
  awk -v p="Process: $PKG" '
    /FATAL EXCEPTION/ { buf=$0; keep=0; next }
    buf != "" {
      buf = buf "\n" $0
      if (index($0, p)) keep=1
      if ($0 !~ /AndroidRuntime/) {
        if (keep) print buf
        buf=""
      }
    }
  ' "$OUT/crash.raw" >> "$OUT/logcat-app.raw" || true
  rm -f "$OUT/crash.raw"
fi
[ "$rc1" -eq 0 ] && [ "$rc2" -eq 0 ]
