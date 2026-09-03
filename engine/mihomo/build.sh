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

command -v go >/dev/null || { echo "Go is required (release builds use Go 1.26.7)" >&2; exit 127; }
command -v gomobile >/dev/null || { echo "gomobile missing; install golang.org/x/mobile/cmd/gomobile" >&2; exit 127; }

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

# Mihomo v1.19.30's listener.Cleanup closes only the TUN listener. Detour also
# uses named custom mixed listeners for route probes; leaving them alive across
# Engine.stop makes the next profile fail to bind the same loopback port and,
# worse, can leave the old profile reachable through that stale listener.
python3 - <<'PYEOF'
p = 'listener/listener.go'
s = open(p).read()
old = '''func Cleanup() {
	closeTunListener()
}'''
new = '''func Cleanup() {
	closeTunListener()
	inboundMux.Lock()
	for name, inboundListener := range inboundListeners {
		_ = inboundListener.Close()
		delete(inboundListeners, name)
	}
	inboundMux.Unlock()
}'''
if old in s:
    open(p, 'w').write(s.replace(old, new))
    print("listener cleanup patch applied")
elif new in s:
    print("listener cleanup patch already applied")
else:
    raise SystemExit(f"FATAL: listener Cleanup layout changed: {p}")
PYEOF

# Mihomo v1.19.30 converts a VLESS gRPC share link with an omitted serviceName
# into grpc-service-name: "". Xray/sing-box-compatible clients use the conventional
# "grpc" fallback, while Mihomo then fails the transport with the connection
# closing before the useful stream is established. Apply the compatibility
# default only when the URI omitted/emptied serviceName; explicit values win.
python3 - <<'PYEOF'
p = 'common/convert/v.go'
s = open(p).read()
old = '''\tcase "grpc":
\t\tgrpcOpts := make(map[string]any)
\t\tgrpcOpts["grpc-service-name"] = query.Get("serviceName")
\t\tproxy["grpc-opts"] = grpcOpts'''
new = '''\tcase "grpc":
\t\tgrpcOpts := make(map[string]any)
\t\tserviceName := query.Get("serviceName")
\t\tif serviceName == "" {
\t\t\tserviceName = "grpc"
\t\t}
\t\tgrpcOpts["grpc-service-name"] = serviceName
\t\tproxy["grpc-opts"] = grpcOpts'''
if old in s:
    open(p, 'w').write(s.replace(old, new, 1))
    print("VLESS gRPC service-name compatibility patch applied")
elif new in s:
    print("VLESS gRPC service-name compatibility patch already applied")
else:
    raise SystemExit(f"FATAL: VLESS gRPC converter layout changed: {p}")
PYEOF

# Xray-core v26.7.11+ defaults REALITY minClientVer to 26.3.27. Mihomo
# v1.19.30 still advertises 1.8.2 in the encrypted ClientHello SessionId, so a
# default modern Xray REALITY server rejects the handshake and the client sees
# an EOF/connection-closed fallback. Detour embeds Mihomo, so advertise the
# minimum accepted modern Xray version while keeping the pinned engine otherwise
# unchanged. Export constants so the bridge tests verify the exact patched AAR
# source rather than only checking this patch script text.
python3 - <<'PYEOF'
p = 'component/tls/reality.go'
s = open(p).read()
old_const = '''const RealityMaxShortIDLen = 8'''
new_const = '''const (
\tRealityMaxShortIDLen = 8
\tRealityClientVersionMajor byte = 26
\tRealityClientVersionMinor byte = 3
\tRealityClientVersionPatch byte = 27
)'''
old_version = '''\t\thello.SessionId[0] = 1
\t\thello.SessionId[1] = 8
\t\thello.SessionId[2] = 2'''
new_version = '''\t\thello.SessionId[0] = RealityClientVersionMajor
\t\thello.SessionId[1] = RealityClientVersionMinor
\t\thello.SessionId[2] = RealityClientVersionPatch'''
if old_const in s and old_version in s:
    s = s.replace(old_const, new_const, 1)
    s = s.replace(old_version, new_version, 1)
    open(p, 'w').write(s)
    print("REALITY client-version compatibility patch applied")
elif new_const in s and new_version in s:
    print("REALITY client-version compatibility patch already applied")
else:
    raise SystemExit(f"FATAL: REALITY client-version layout changed: {p}")
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
# Keep the gomobile package source set in one place. New engine bridge files must
# be bound as well as engine.go; *_test.go is ignored by normal package builds.
cp "$BIND_DIR"/*.go "$WORK_DIR"/
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
# Run bridge unit tests against the exact pinned Mihomo source after Detour's
# embedding patches have been applied. This keeps subscription/runtime tests in
# the same source environment that is subsequently packaged into the AAR.
go test ./...
# -libname dropped: current gomobile no longer supports it; output defaults to <package>.aar == engine.aar
gomobile bind -target android/arm64,android/amd64 -androidapi 24 -javapkg=dev.triplet.engine .

# Verify the shipped c-shared libraries were built by the selected Go toolchain.
# `go version` reads the linker-stamped version from c-shared ELF files; checking
# every ABI prevents a stale/vulnerable runtime from being packaged.
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
