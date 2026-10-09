#!/usr/bin/env bash
# Fail if an upload set contains anything that must never leave the runner:
# keystores, .env files, local.properties, private keys, or SQLite databases
# (real History lives in SQLite). Also looks inside APK/AAB/ZIP/JAR entries.
# Usage: artifact-guard.sh <staging-dir>
set -euo pipefail

dir="${1:?staging dir required}"
if [ ! -d "$dir" ]; then
  echo "Nothing staged at $dir; nothing to guard."
  exit 0
fi

# Name patterns (case-insensitive, matched against the base name).
forbidden_re='(\.keystore|\.jks|\.p12|\.pfx|\.db|\.db-wal|\.db-shm|\.db-journal|\.sqlite|\.sqlite3)$|^\.env$|^\.env\..*|^local\.properties$'

command -v unzip >/dev/null || { echo "::error::unzip is required to inspect APK/ZIP entries"; exit 1; }

bad=0
while IFS= read -r -d '' f; do
  base=$(basename "$f")
  if printf '%s\n' "$base" | grep -qiE "$forbidden_re"; then
    echo "::error::Forbidden file in upload set: ${f#"$dir"/}"
    bad=1
  fi
  if [ -L "$f" ]; then
    echo "::error::Symlink in upload set: ${f#"$dir"/}"
    bad=1
  fi
  case "${base,,}" in
    *.apk|*.aab|*.zip|*.jar)
      if entries=$(unzip -Z1 "$f" 2>/dev/null); then
        hits=$(printf '%s\n' "$entries" | awk -F/ '{print $NF}' | grep -iE "$forbidden_re" || true)
        if [ -n "$hits" ]; then
          echo "::error::Forbidden entries inside ${f#"$dir"/}: $(echo "$hits" | tr '\n' ' ')"
          bad=1
        fi
      fi
      ;;
  esac
done < <(find "$dir" -print0)

# Private key material in any text file.
if keyhits=$(grep -rIlE -- '-----BEGIN ([A-Z]+ )?PRIVATE KEY-----' "$dir" 2>/dev/null); then
  if [ -n "$keyhits" ]; then
    echo "::error::Private key material found in: $(echo "$keyhits" | tr '\n' ' ')"
    bad=1
  fi
fi

if [ "$bad" -ne 0 ]; then
  echo "::error::Artifact guard failed. Fix the allowlist; do not upload."
  exit 1
fi
echo "Artifact guard passed for $dir ($(find "$dir" -type f | wc -l) files)."
