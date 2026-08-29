#!/usr/bin/env bash
# Builds the pinned mihomo Android engine AAR into engine/libs/engine.aar.
# Applies the embedding patch: buildAndroidRules -> nil (apps cannot read
# /data/system/packages.xml; host excludes own UID via VpnService).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MIHOMO_VERSION="v1.19.30"
MIHOMO_COMMIT="ac017cdd246ce8bd547653d927e7bf77d7ee73d5"
CACHE="${MIHOMO_CACHE:-$REPO_ROOT/.cache/mihomo-src}"
BIND_DIR="$REPO_ROOT/engine/mihomo/go"
OUT="$REPO_ROOT/engine/libs/engine.aar"

command -v go >/dev/null || { echo "run inside nix develop" >&2; exit 127; }
command -v gomobile >/dev/null || { echo "gomobile missing" >&2; exit 127; }

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

if [[ ! -d "$CACHE/.git" ]]; then
  mkdir -p "$CACHE"
  git clone --depth 1 --branch "$MIHOMO_VERSION" https://github.com/MetaCubeX/mihomo.git "$CACHE"
fi
cd "$CACHE"
git fetch --tags --force origin "$MIHOMO_VERSION"
git reset --hard "$MIHOMO_COMMIT"
git clean -fdx
echo "mihomo: $MIHOMO_VERSION ($MIHOMO_COMMIT)"

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
elif new in s:
    print("patch already applied")
else:
    raise SystemExit(f"FATAL: buildAndroidRules layout changed: {p}")
PYEOF

# Triplet host-side UID resolver bridge (Task 3 round 2):
# tunnel.go consults process.TripletHostFinder (wired to the app's
# ConnectivityManager.getConnectionOwnerUid via engine.SetProcessResolver)
# instead of netlink/procfs, which are banned for ordinary apps.
python3 - <<'PYEOF'
p = 'tunnel/tunnel.go'
s = open(p).read()
marker = 'tripletFinder := process.TripletHostFinder'
anchor = '\t\t\t\tattemptProcessLookup = false\n\t\t\t\tif !features.CMFA {'
inject = (
    '\t\t\t\tattemptProcessLookup = false\n'
    '\t\t\t\tif tripletFinder := process.TripletHostFinder; tripletFinder != nil {\n'
    '\t\t\t\t\ttripletUID, tripletPkg, tripletOK := tripletFinder(metadata.NetWork.String(), metadata.SrcIP, int(metadata.SrcPort), metadata.DstIP, int(metadata.DstPort))\n'
    '\t\t\t\t\tif tripletOK {\n'
    '\t\t\t\t\t\tmetadata.Uid = tripletUID\n'
    '\t\t\t\t\t\tmetadata.Process = tripletPkg\n'
    '\t\t\t\t\t\tmetadata.ProcessPath = tripletPkg\n'
    '\t\t\t\t\t}\n'
    '\t\t\t\t} else if !features.CMFA {'
)
if marker in s:
    print("triplet tunnel patch: already applied")
elif s.count(anchor) == 1:
    open(p, 'w').write(s.replace(anchor, inject, 1))
    print("triplet tunnel patch applied")
else:
    raise SystemExit(f"FATAL: tunnel.go anchor not found or ambiguous ({p})")
PYEOF

python3 - <<'PYEOF'
p = 'component/process/process.go'
s = open(p).read()
marker = 'TripletHostFinder func(network string'
anchor = 'func FindProcessName('
decl = (
    '// TripletHostFinder: optional host-side owner resolution (Android embedding).\n'
    'var TripletHostFinder func(network string, srcIP netip.Addr, srcPort int, dstIP netip.Addr, dstPort int) (uint32, string, bool)\n\n'
)
if marker in s:
    print("triplet process patch: already applied")
elif s.count(anchor) == 1:
    open(p, 'w').write(s.replace(anchor, decl + anchor, 1))
    print("triplet process patch applied")
else:
    raise SystemExit(f"FATAL: process.go anchor not found or ambiguous ({p})")
PYEOF

cp "$BIND_DIR/go.mod" "$WORK_DIR/go.mod"
cp "$BIND_DIR/go.sum" "$WORK_DIR/go.sum"
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
export GOFLAGS="-mod=mod -tags=with_gvisor"
# -libname dropped: gomobile@latest no longer supports it; output defaults to <package>.aar == engine.aar
gomobile bind -target android/arm64,android/amd64 -androidapi 24 -javapkg=dev.triplet.engine .

# Verify the shipped c-shared libraries were built by the Go toolchain selected
# by this dev shell. `go version` reads the linker-stamped version from c-shared
# ELF files; checking every ABI prevents a stale/vulnerable runtime from being
# packaged even if gomobile itself was built by another Go release.
expected_go="$(go env GOVERSION)"
mapfile -t go_libs < <(unzip -Z1 engine.aar | grep -E '^jni/[^/]+/libgojni\.so$')
if (( ${#go_libs[@]} == 0 )); then
  echo "FATAL: engine.aar contains no libgojni.so" >&2
  exit 1
fi
for entry in "${go_libs[@]}"; do
  extracted="$WORK_DIR/$(basename "$(dirname "$entry")")-libgojni.so"
  unzip -p engine.aar "$entry" > "$extracted"
  actual_go="$(go version "$extracted" | awk '{print $2}')"
  if [[ "$actual_go" != "$expected_go" ]]; then
    echo "FATAL: $entry built with $actual_go, expected $expected_go" >&2
    exit 1
  fi
done
echo "embedded Go runtime: $expected_go (${#go_libs[@]} ABIs)"

mkdir -p "$(dirname "$OUT")"
cp engine.aar "$OUT"
echo "Artifact: $OUT ($(du -h engine.aar | cut -f1))"
