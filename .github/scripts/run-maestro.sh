#!/usr/bin/env bash
# Fixture data only: debug APK + pane://sample links + the x-image fixture URL.
# No accounts, no real History. The raw log never leaves the runner.
set -euo pipefail
APK=app/build/outputs/apk/debug/app-debug.apk
OUT=build/maestro
PKG=app.pane.android
mkdir -p "$OUT"
adb wait-for-device
adb install -r "$APK"
adb shell settings put system accelerometer_rotation 1
adb shell getprop persist.sys.locale >> "$OUT/locale.txt" || true
adb shell settings get system system_locales >> "$OUT/locale.txt" || true
# Share-in precondition for 01_share_in.yaml (fixture URL from SamplePosts x-image).
adb shell am start -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "https://x.com/mayaren/status/1842700101" \
  -n "$PKG/.MainActivity"
adb logcat -c
# App-only logcat: re-attach to the app PID whenever it (re)starts (clearState kills it).
(
  while true; do
    pid=$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true)
    if [ -n "$pid" ]; then
      adb logcat -v threadtime --pid="$pid" >> "$OUT/logcat-app.raw" 2>/dev/null || true
    fi
    sleep 1
  done
) &
LOGLOOP=$!
# AndroidRuntime crash lines, captured separately and filtered to PKG below.
adb logcat -v threadtime -s AndroidRuntime:E > "$OUT/crash.raw" 2>/dev/null &
CRASH=$!
set +e
maestro test .maestro \
  --exclude-tags=rotate-button-off \
  --format junit \
  --output "$OUT/junit.xml" \
  --test-output-dir "$OUT/artifacts"
rc1=$?
adb shell settings put system accelerometer_rotation 0
maestro test .maestro/06b_fullscreen_rotate_button.yaml \
  --format junit \
  --output "$OUT/junit-06b.xml" \
  --test-output-dir "$OUT/artifacts"
rc2=$?
adb shell settings put system accelerometer_rotation 1
{
  echo "after-06b"
  adb shell dumpsys window | grep -m1 mCurrentRotation || true
} >> "$OUT/rotation.txt"
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
