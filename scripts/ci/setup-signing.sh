#!/usr/bin/env bash
# Decodes the release keystore from CI secrets and exports the LOQIN_RELEASE_* variables that
# app/build.gradle.kts reads. Fails loudly when a secret is missing so CI never publishes an
# unsigned APK.
#
# Inputs (environment): LOQIN_RELEASE_KEYSTORE_BASE64, LOQIN_RELEASE_STORE_PASSWORD,
#                       LOQIN_RELEASE_KEY_ALIAS, LOQIN_RELEASE_KEY_PASSWORD
set -euo pipefail

missing=()
for name in LOQIN_RELEASE_KEYSTORE_BASE64 LOQIN_RELEASE_STORE_PASSWORD LOQIN_RELEASE_KEY_ALIAS LOQIN_RELEASE_KEY_PASSWORD; do
  if [ -z "${!name:-}" ]; then missing+=("$name"); fi
done
if [ "${#missing[@]}" -gt 0 ]; then
  echo "::error::Missing repository secrets: ${missing[*]}. See docs/RELEASING.md."
  exit 1
fi

keystore="${RUNNER_TEMP:-/tmp}/loqin-release.jks"
printf '%s' "$LOQIN_RELEASE_KEYSTORE_BASE64" | base64 --decode > "$keystore"
chmod 600 "$keystore"

# Passwords stay in the step environment; only the keystore path is shared with later steps.
if [ -n "${GITHUB_ENV:-}" ]; then
  echo "LOQIN_RELEASE_STORE_FILE=$keystore" >> "$GITHUB_ENV"
fi
echo "Keystore written to $keystore"
