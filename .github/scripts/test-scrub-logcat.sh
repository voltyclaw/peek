#!/usr/bin/env bash
# Logcat times stay. Real IPv6, including compressed ::1 and full form, becomes <ip6>.
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cd "$work"
cat > raw.txt <<'EOF'
10-12 08:05:01.123  1482  1503 I Pane: opened history
10-12 00:00:00.000  1482  1503 D Pane: midnight tick
10-12 23:59:59.999  1482  1503 V Pane: end of day 12:34:56
10-12 08:05:01.128  1482  1503 I Pane: stamp 08:05:01 and 08:05:01.123
10-12 07:45:01.001  1482  1503 I Pane: not an address 1:2:3
10-12 08:05:01.124  1482  1503 I Pane: peer ::1
10-12 08:05:01.125  1482  1503 I Pane: peer 2001:0db8:85a3:0000:0000:8a2e:0370:7334
10-12 08:05:01.129  1482  1503 I Pane: peer 2001:0DB8:85A3:0000:0000:8A2E:0370:7334
10-12 08:05:01.126  1482  1503 I Pane: peer fe80::1
10-12 08:05:01.127  1482  1503 I Pane: peer 2001:db8::8a2e:370:7334
EOF
cat > expected.txt <<'EOF'
10-12 08:05:01.123  1482  1503 I Pane: opened history
10-12 00:00:00.000  1482  1503 D Pane: midnight tick
10-12 23:59:59.999  1482  1503 V Pane: end of day 12:34:56
10-12 08:05:01.128  1482  1503 I Pane: stamp 08:05:01 and 08:05:01.123
10-12 07:45:01.001  1482  1503 I Pane: not an address 1:2:3
10-12 08:05:01.124  1482  1503 I Pane: peer <ip6>
10-12 08:05:01.125  1482  1503 I Pane: peer <ip6>
10-12 08:05:01.129  1482  1503 I Pane: peer <ip6>
10-12 08:05:01.126  1482  1503 I Pane: peer <ip6>
10-12 08:05:01.127  1482  1503 I Pane: peer <ip6>
EOF
bash "$root/.github/scripts/scrub-logcat.sh" raw.txt device-logs/logcat-app.txt >/dev/null
diff -u expected.txt device-logs/logcat-app.txt
