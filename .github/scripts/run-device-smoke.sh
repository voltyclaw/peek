#!/usr/bin/env bash
# Runs the @DeviceSmoke instrumented set on the already-booted emulator.
# Called as a single line from android-emulator-runner's `script:` (which runs each
# line with `sh`), so all logic lives here.
# "No tests found" (annotation present but nothing matched yet) is treated as a pass.
set -uo pipefail

annotation="${DEVICE_SMOKE_ANNOTATION:-app.pane.android.DeviceSmoke}"
log="$(mktemp)"

./gradlew :app:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.annotation=${annotation}" \
  --stacktrace 2>&1 | tee "$log"
status=${PIPESTATUS[0]}

if [ "$status" -ne 0 ] && grep -qiE 'No tests found' "$log"; then
  echo "::warning::No tests matched @${annotation##*.} (${annotation}); treating as pass."
  echo "### Device smoke: no tests matched \`${annotation}\` (treated as pass)" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
  exit 0
fi
exit "$status"
