#!/usr/bin/env bash
set -euo pipefail

AAB="${1:?usage: verify_aab_signature.sh <bundle.aab>}"
if [[ ! -f "$AAB" ]]; then
  echo "Android App Bundle not found: $AAB" >&2
  exit 2
fi

set +e
VERIFY_OUTPUT="$(LC_ALL=C jarsigner -verify "$AAB" 2>&1)"
VERIFY_EXIT=$?
set -e
printf '%s\n' "$VERIFY_OUTPUT"

if [[ "$VERIFY_EXIT" -ne 0 ]]; then
  echo "AAB signature verification failed with exit code $VERIFY_EXIT" >&2
  exit "$VERIFY_EXIT"
fi

# jarsigner intentionally returns 0 for an unsigned JAR. Require its positive
# signed-container result as well as the cryptographic verification exit code.
if ! grep -Fxq 'jar verified.' <<< "$VERIFY_OUTPUT"; then
  echo "AAB is not a cryptographically verified signed JAR" >&2
  exit 1
fi

echo "Verified signed Android App Bundle: $AAB"
