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
  -e 's#(@)[A-Za-z0-9_.]{2,30}\b#\1<user>#g' \
  -e 's#(u/|user[=: ]|account[=: ]|accountName[=: ]|author[=: ])[^[:space:],;]+#\1<account>#Ig' \
  "$in" \
| awk '
# IPv6 only: two or more colons, and either "::" or a hex group with a letter
# or 3+ digits. HH:MM:SS and HH:MM:SS.mmm fail that, so logcat times stay.
function is_time(s) {
  return s ~ /^[0-9]{1,2}:[0-9]{2}:[0-9]{2}$/
}
function is_ipv6(s,    t, n, i, parts, g) {
  t = s
  if (gsub(/:/, ":", t) < 2) return 0
  if (is_time(s)) return 0
  if (index(s, "::") > 0) return 1
  n = split(s, parts, ":")
  for (i = 1; i <= n; i++) {
    g = parts[i]
    if (g ~ /[A-Fa-f]/) return 1
    if (length(g) >= 3 && g ~ /^[0-9A-Fa-f]+$/) return 1
  }
  return 0
}
function scrub(line,    out, rest, cand, pre) {
  out = ""
  rest = line
  while (match(rest, /[0-9A-Fa-f]*:[0-9A-Fa-f:]*:[0-9A-Fa-f:]*/)) {
    pre = substr(rest, 1, RSTART - 1)
    cand = substr(rest, RSTART, RLENGTH)
    rest = substr(rest, RSTART + RLENGTH)
    if (is_ipv6(cand)) out = out pre "<ip6>"
    else out = out pre cand
  }
  return out rest
}
{ print scrub($0) }
' \
| grep -Ev '(www\.|\.com|\.net|\.org|\.io|\.me|\.tv|https?|[Bb]earer|[Aa]uthorization|[Cc]ookie|[A-Za-z0-9_-]{32,}|[0-9a-fA-F]{24,})' \
> "$out" || true
echo "kept $(wc -l < "$out") of $(wc -l < "$in") lines"
