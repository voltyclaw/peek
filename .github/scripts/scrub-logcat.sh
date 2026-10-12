#!/usr/bin/env bash
# Usage: scrub-logcat.sh <raw-app-logcat> <out: device-logs/logcat-app.txt>
# Policy: redact known-sensitive shapes; DROP any line that still looks risky afterwards.
# Release Engineer conditions, 2026-10-10: failure-only upload, app lines only,
# this fixed path, then gitleaks --no-git. Fixture data only.
set -euo pipefail
in=$1; out=$2
[ "$out" = "device-logs/logcat-app.txt" ] || { echo "::error::fixed output path required"; exit 2; }
mkdir -p device-logs
[ -s "$in" ] || { echo "(no app log lines captured)" > "$out"; exit 0; }
sed -E \
  -e 's#[A-Za-z][A-Za-z0-9+.-]*://[^[:space:]"'"'"'<>]+#<url>#g' \
  -e 's#\?[^[:space:]]*=[^[:space:]]*#?<query>#g' \
  -e 's#([Aa]uthorization|[Bb]earer|[Cc]ookie|[Ss]et-[Cc]ookie|[Xx]-[A-Za-z-]*[Kk]ey)[:= ]+[^[:space:]]+#\1 <redacted>#g' \
  -e 's#(api[_-]?key|access[_-]?token|refresh[_-]?token|id[_-]?token|token|secret|password|YOUTUBE_API_KEY|key)(["'"'"']?[:=][[:space:]]*)[^[:space:],;]+#\1\2<redacted>#Ig' \
  -e 's#AIza[0-9A-Za-z_-]{35}#<gkey>#g' \
  -e 's#eyJ[0-9A-Za-z_-]+\.[0-9A-Za-z_-]+\.[0-9A-Za-z_-]+#<jwt>#g' \
  -e 's#[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}#<email>#g' \
  -e 's#\b([0-9]{1,3}\.){3}[0-9]{1,3}\b#<ip>#g' \
  -e 's#\b([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}\b#<ip6>#g' \
  -e 's#(@)[A-Za-z0-9_.]{2,30}\b#\1<user>#g' \
  -e 's#(u/|user[=: ]|account[=: ]|accountName[=: ]|author[=: ])[^[:space:],;]+#\1<account>#Ig' \
  "$in" \
| grep -Ev '(www\.|\.com|\.net|\.org|\.io|\.me|\.tv|https?|[Bb]earer|[Aa]uthorization|[Cc]ookie|[A-Za-z0-9_-]{32,}|[0-9a-fA-F]{24,})' \
> "$out" || true
echo "kept $(wc -l < "$out") of $(wc -l < "$in") lines"
