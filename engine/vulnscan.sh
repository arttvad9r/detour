#!/usr/bin/env bash
# govulncheck over the engine's real dependency set (pinned mihomo + triplet patches).
# Run after :buildMihomoAar so .cache/mihomo-src holds the patched source.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="$REPO_ROOT/.cache/mihomo-src"

[[ -d "$CACHE" ]] || { echo "run :buildMihomoAar first (need patched $CACHE)" >&2; exit 1; }

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

cp "$REPO_ROOT/engine/mihomo/go/go.mod" "$WORK_DIR/"
cp "$REPO_ROOT/engine/mihomo/go/go.sum" "$WORK_DIR/"
cp "$REPO_ROOT/engine/mihomo/go/engine.go" "$WORK_DIR/"
cd "$WORK_DIR"
sed -i "s|^replace github.com/metacubex/mihomo => .*|replace github.com/metacubex/mihomo => $CACHE|" go.mod
grep -q '^replace github.com/metacubex/mihomo' go.mod || \
  echo "replace github.com/metacubex/mihomo => $CACHE" >> go.mod

export GOFLAGS=-mod=mod
exec govulncheck ./...
