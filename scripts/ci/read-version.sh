#!/usr/bin/env bash
# Prints "versionName versionCode" from app/build.gradle.kts (the single source of truth).
set -euo pipefail
file="${1:-app/build.gradle.kts}"
name=$(sed -n 's/^val loqinVersionName = "\(.*\)"$/\1/p' "$file")
code=$(sed -n 's/^val loqinVersionCode = \([0-9]*\)$/\1/p' "$file")
if [ -z "$name" ] || [ -z "$code" ]; then
  echo "::error::Could not read loqinVersionName/loqinVersionCode from $file" >&2
  exit 1
fi
echo "$name $code"
