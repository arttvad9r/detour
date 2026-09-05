#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <bundle.aab> <expected-cert-sha256> <expected-abi>" >&2
  exit 2
fi

AAB="$1"
EXPECTED_CERT_SHA256="$(printf '%s' "$2" | tr -d ':' | tr '[:lower:]' '[:upper:]')"
EXPECTED_ABI="$3"

if [[ ! -f "$AAB" ]]; then
  echo "Release AAB not found: $AAB" >&2
  exit 1
fi
if [[ ! "$EXPECTED_CERT_SHA256" =~ ^[0-9A-F]{64}$ ]]; then
  echo "Expected certificate fingerprint must be SHA-256" >&2
  exit 1
fi
if [[ -z "$EXPECTED_ABI" || "$EXPECTED_ABI" == */* ]]; then
  echo "Invalid expected ABI: $EXPECTED_ABI" >&2
  exit 1
fi

# Android signing certificates are normally self-signed. jarsigner -strict
# treats that expected property as a trust-chain error, so use normal signature
# verification here and enforce signer identity separately by exact SHA-256.
jarsigner -verify "$AAB"

CERT_OUTPUT="$(keytool -printcert -jarfile "$AAB")"
printf '%s\n' "$CERT_OUTPUT"
ACTUAL_CERT_SHA256="$(
  printf '%s\n' "$CERT_OUTPUT" |
    sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' |
    head -n 1 |
    tr -d ':' |
    tr '[:lower:]' '[:upper:]'
)"
if [[ ! "$ACTUAL_CERT_SHA256" =~ ^[0-9A-F]{64}$ ]]; then
  echo "Could not read SHA-256 certificate fingerprint from AAB" >&2
  exit 1
fi
if [[ "$ACTUAL_CERT_SHA256" != "$EXPECTED_CERT_SHA256" ]]; then
  echo "Release AAB signing certificate mismatch" >&2
  echo "Expected: $EXPECTED_CERT_SHA256" >&2
  echo "Actual:   $ACTUAL_CERT_SHA256" >&2
  exit 1
fi

ABI_SET="$(
  unzip -Z1 "$AAB" |
    sed -n 's#^base/lib/\([^/]*\)/.*#\1#p' |
    sort -u
)"
if [[ "$ABI_SET" != "$EXPECTED_ABI" ]]; then
  echo "Release AAB must contain exactly ABI: $EXPECTED_ABI" >&2
  printf 'Found ABI set:\n%s\n' "$ABI_SET" >&2
  exit 1
fi

printf 'Verified AAB certificate SHA-256: %s\n' "$ACTUAL_CERT_SHA256"
printf 'Verified AAB native ABI: %s\n' "$ABI_SET"
