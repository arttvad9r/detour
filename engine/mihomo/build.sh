#!/usr/bin/env bash
# Builds the pinned mihomo Android engine AAR into engine/libs/engine.aar.
# Applies only the compatibility patches required by the Android embedding.
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

# Ordinary Android apps cannot read /data/system/packages.xml. Detour resolves
# ownership host-side and excludes its own UID through VpnService.
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
	// Patched for Detour embedding: host-side UID resolution is used instead.
	return nil
}'''
if old in s:
    open(p, 'w').write(s.replace(old, new, 1))
    print("Android rules embedding patch applied")
elif new in s:
    print("Android rules embedding patch already applied")
else:
    raise SystemExit(f"FATAL: buildAndroidRules layout changed: {p}")
PYEOF

# Close named custom listeners as well as TUN across Engine.stop/Start cycles.
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
    open(p, 'w').write(s.replace(old, new, 1))
    print("listener cleanup patch applied")
elif new in s:
    print("listener cleanup patch already applied")
else:
    raise SystemExit(f"FATAL: listener Cleanup layout changed: {p}")
PYEOF

# XTLS Vision is a raw-TCP VLESS flow. Ignore the invalid gRPC+Vision
# combination emitted by some subscription generators.
python3 - <<'PYEOF'
p = 'adapter/outbound/vless.go'
s = open(p).read()
old = '''func NewVless(option VlessOption) (*Vless, error) {
	var addons *vless.Addons'''
new = '''func NewVless(option VlessOption) (*Vless, error) {
	if strings.EqualFold(option.Network, "grpc") && option.Flow == vless.XRV {
		option.Flow = ""
	}
	var addons *vless.Addons'''
if old in s:
    open(p, 'w').write(s.replace(old, new, 1))
    print("VLESS gRPC Vision compatibility patch applied")
elif new in s:
    print("VLESS gRPC Vision compatibility patch already applied")
else:
    raise SystemExit(f"FATAL: VLESS NewVless layout changed: {p}")
PYEOF

# Xray keeps an omitted VLESS gRPC serviceName empty. Its Tun RPC therefore
# uses //Tun. Mihomo substitutes GunService for an empty value, producing
# /GunService/Tun. Apply Xray semantics only to VLESS gRPC with an omitted
# service name; explicit names are preserved.
python3 - <<'PYEOF'
p = 'adapter/outbound/vless.go'
s = open(p).read()
old = '''		gunConfig := &gun.Config{
			ServiceName:  option.GrpcOpts.GrpcServiceName,
			UserAgent:    option.GrpcOpts.GrpcUserAgent,
			Host:         option.ServerName,
			PingInterval: option.GrpcOpts.PingInterval,
		}'''
new = '''		serviceName := option.GrpcOpts.GrpcServiceName
		if serviceName == "" {
			serviceName = "//Tun"
		}
		gunConfig := &gun.Config{
			ServiceName:  serviceName,
			UserAgent:    option.GrpcOpts.GrpcUserAgent,
			Host:         option.ServerName,
			PingInterval: option.GrpcOpts.PingInterval,
		}'''
if old in s:
    open(p, 'w').write(s.replace(old, new, 1))
    print("VLESS empty gRPC service-name Xray compatibility patch applied")
elif new in s:
    print("VLESS empty gRPC service-name Xray compatibility patch already applied")
else:
    raise SystemExit(f"FATAL: VLESS gRPC gun config layout changed: {p}")
PYEOF

# Modern Xray REALITY defaults minClientVer to 26.3.27. Mihomo v1.19.30 still
# advertises 1.8.2, so advertise the minimum accepted modern Xray version.
python3 - <<'PYEOF'
p = 'component/tls/reality.go'
s = open(p).read()
old_const = '''const RealityMaxShortIDLen = 8'''
new_const = '''const (
	RealityMaxShortIDLen = 8
	RealityClientVersionMajor byte = 26
	RealityClientVersionMinor byte = 3
	RealityClientVersionPatch byte = 27
)'''
old_version = '''		hello.SessionId[0] = 1
		hello.SessionId[1] = 8
		hello.SessionId[2] = 2'''
new_version = '''		hello.SessionId[0] = RealityClientVersionMajor
		hello.SessionId[1] = RealityClientVersionMinor
		hello.SessionId[2] = RealityClientVersionPatch'''
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

# Expose sanitized URLTest error classification to the Android bridge without
# adding transport logging to Mihomo.
python3 - <<'PYEOF'
p = 'adapter/adapter.go'
s = open(p).read()
old_import = '''	"encoding/json"
	"fmt"'''
new_import = '''	"encoding/json"
	"errors"
	"fmt"'''
old_const = '''const (
	defaultHistoriesNum = 10
)'''
new_const = '''const (
	defaultHistoriesNum = 10
)

func DetourURLTestErrorClass(err error) string {
	if err == nil {
		return ""
	}
	lower := strings.ToLower(err.Error())
	switch {
	case errors.Is(err, context.DeadlineExceeded):
		return "timeout"
	case strings.Contains(lower, "reality"):
		return "reality"
	case strings.Contains(lower, "no such host"), strings.Contains(lower, "lookup "), strings.Contains(lower, "dns"):
		return "dns"
	case strings.Contains(lower, "tls"), strings.Contains(lower, "x509"), strings.Contains(lower, "certificate"), strings.Contains(lower, "handshake"):
		return "tls"
	case strings.Contains(lower, "grpc"):
		return "grpc"
	case strings.Contains(lower, "eof"), strings.Contains(lower, "connection closed"), strings.Contains(lower, "reset by peer"), strings.Contains(lower, "broken pipe"):
		return "connection"
	case strings.Contains(lower, "dial "), strings.Contains(lower, "connect:"), strings.Contains(lower, "connection refused"), strings.Contains(lower, "network is unreachable"):
		return "dial"
	default:
		if timeoutErr, ok := err.(interface{ Timeout() bool }); ok && timeoutErr.Timeout() {
			return "timeout"
		}
		return "other"
	}
}

func DetourURLTestErrorText(err error) string {
	if err == nil {
		return ""
	}
	text := strings.Map(func(r rune) rune {
		if r < 0x20 || r == 0x7f {
			return ' '
		}
		return r
	}, err.Error())
	if index := strings.Index(strings.ToLower(text), "vless://"); index >= 0 {
		text = text[:index] + "vless://<redacted>"
	}
	if len(text) > 800 {
		text = text[:800]
	}
	return text
}'''
if old_import in s and old_const in s:
    s = s.replace(old_import, new_import, 1)
    s = s.replace(old_const, new_const, 1)
    open(p, 'w').write(s)
    print("URLTest error classification patch applied")
elif new_import in s and new_const in s:
    print("URLTest error classification patch already applied")
else:
    raise SystemExit(f"FATAL: URLTest classification layout changed: {p}")
PYEOF

# Host-side UID resolver bridge.
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
    print("triplet tunnel patch already applied")
elif s.count(anchor) == 1:
    open(p, 'w').write(s.replace(anchor, inject, 1))
    print("triplet tunnel patch applied")
else:
    raise SystemExit(f"FATAL: tunnel.go anchor not found or ambiguous: {p}")
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
    print("triplet process patch already applied")
elif s.count(anchor) == 1:
    open(p, 'w').write(s.replace(anchor, decl + anchor, 1))
    print("triplet process patch applied")
else:
    raise SystemExit(f"FATAL: process.go anchor not found or ambiguous: {p}")
PYEOF

# Make panics during the void upstream configuration dispatcher observable to
# the embedding bridge. The dispatcher has no ordinary error return in this
# pinned release, but callers can still propagate a failed apply.
python3 - <<'PYEOF'
p = 'hub/executor/executor.go'
s = open(p).read()
old = '''func ApplyConfig(cfg *config.Config, force bool) {\n\tmux.Lock()'''
new = '''func ApplyConfig(cfg *config.Config, force bool) (err error) {\n\tdefer func() {\n\t\tif recovered := recover(); recovered != nil {\n\t\t\terr = fmt.Errorf("apply config panic: %v", recovered)\n\t\t}\n\t}()\n\tmux.Lock()'''
if old in s:
    s = s.replace(old, new, 1)
    print("ApplyConfig error propagation patch applied")
elif new in s:
    print("ApplyConfig error propagation patch already applied")
else:
    raise SystemExit(f"FATAL: ApplyConfig layout changed: {p}")
old_end = '''\tresolver.ResetConnection()\n}'''
if old_end in s:
    old_end = '''\tresolver.ResetConnection()\n}'''
    if s.count(old_end) != 1:
        raise SystemExit(f"FATAL: ApplyConfig end layout changed: {p}")
    s = s.replace(old_end, '''\tresolver.ResetConnection()\n\treturn nil\n}''', 1)
    print("ApplyConfig return patch applied")
open(p, 'w').write(s)
PYEOF

cp "$BIND_DIR/go.mod" "$WORK_DIR/go.mod"
cp "$BIND_DIR/go.sum" "$WORK_DIR/go.sum"
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
go test ./...
gomobile bind -target android/arm64,android/amd64 -androidapi 24 -javapkg=dev.triplet.engine .

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
