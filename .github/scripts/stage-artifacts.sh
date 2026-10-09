#!/usr/bin/env bash
# Copy ONLY allowlisted paths into a staging directory that is then guarded and uploaded.
# Usage: stage-artifacts.sh <staging-dir> <allowlisted-path-or-glob>...
#   - Paths are repo-relative; absolute paths and '..' are refused.
#   - Globs are expanded here (globstar on), so quote them at the call site.
#   - Missing paths are skipped (e.g. no Roborazzi output yet); symlinks are refused.
set -euo pipefail
shopt -s globstar nullglob dotglob

dest="${1:?staging dir required}"; shift
[ "$#" -gt 0 ] || { echo "::error::No allowlist given"; exit 2; }
mkdir -p "$dest"

copied=0
for pat in "$@"; do
  case "$pat" in
    /*|*..*) echo "::error::Refusing non-repo-relative allowlist entry: $pat"; exit 2 ;;
  esac
  IFS=$'\n'
  # shellcheck disable=SC2206 # intentional glob expansion of the allowlist pattern
  matches=( $pat )
  unset IFS
  for src in "${matches[@]}"; do
    [ -e "$src" ] || continue
    if [ -n "$(find "$src" -type l -print -quit)" ]; then
      echo "::error::Symlink inside allowlisted path $src; refusing to stage it."
      exit 1
    fi
    cp -R --parents -- "$src" "$dest"/
    copied=$((copied + 1))
    echo "staged: $src"
  done
done
echo "Staged $copied allowlisted path(s) into $dest"
