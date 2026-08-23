#!/usr/bin/env bash
# Builds the pinned mihomo Android engine AAR into engine/libs/engine.aar.
# Applies the embedding patch: buildAndroidRules -> nil (apps cannot read
# /data/system/packages.xml; host excludes own UID via VpnService).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MIHOMO_VERSION="v1.19.29"
CACHE="${MIHOMO_CACHE:-$REPO_ROOT/.cache/mihomo-src}"
BIND_DIR="$REPO_ROOT/engine/mihomo/go"
OUT="$REPO_ROOT/engine/libs/engine.aar"

command -v go >/dev/null || { echo "run inside nix-shell" >&2; exit 127; }
command -v gomobile >/dev/null || { echo "gomobile missing" >&2; exit 127; }

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

if [[ ! -d "$CACHE/.git" ]]; then
  mkdir -p "$CACHE"
  git clone --depth 1 --branch "$MIHOMO_VERSION" https://github.com/MetaCubeX/mihomo.git "$CACHE"
fi
cd "$CACHE"
echo "mihomo: $(git describe --tags --exact-match 2>/dev/null || echo "$MIHOMO_VERSION")"

python3 - <<'PYEOF'
p = 'listener/sing_tun/server_android.go'
s = open(p).read()
old = '''func (l *Listener) buildAndroidRules(tunOptions *tun.Options) error {
	packageManager, err := getPackageManager()
	if err != nil {
		return err
	}
	tunOptions.BuildAndroidRules(packageManager, l.handler)
	return nil
}'''
new = '''func (l *Listener) buildAndroidRules(tunOptions *tun.Options) error {
	// Patched for Triplet embedding: reading /data/system/packages.xml is
	// forbidden for ordinary Android apps. Host excludes own UID via VpnService.
	return nil
}'''
if old in s:
    open(p, 'w').write(s.replace(old, new))
    print("patch applied")
else:
    print("patch: already applied or upstream changed")
PYEOF

cp "$BIND_DIR/go.mod" "$WORK_DIR/go.mod"
cp "$BIND_DIR/engine.go" "$WORK_DIR/engine.go"
cd "$WORK_DIR"
python3 - <<PYEOF
import re
line = 'replace github.com/metacubex/mihomo => $CACHE'
s = open('go.mod').read()
s = re.sub(r'replace github\.com/metacubex/mihomo => .*', line, s)
if 'replace github.com/metacubex/mihomo =>' not in s:
    s += '\n' + line + '\n'
open('go.mod', 'w').write(s)
PYEOF

export PATH="$PATH:$(go env GOPATH)/bin"
export GOFLAGS="-mod=mod"
go mod tidy
# -libname dropped: gomobile@latest no longer supports it; output defaults to <package>.aar == engine.aar
gomobile bind -target android/arm64,android/amd64 -androidapi 24 -javapkg=dev.triplet.engine .
mkdir -p "$(dirname "$OUT")"
cp engine.aar "$OUT"
echo "Artifact: $OUT ($(du -h engine.aar | cut -f1))"
