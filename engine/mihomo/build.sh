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

# XTLS Vision is defined for VLESS over the raw TCP transport. Some subscription
# generators nevertheless attach flow=xtls-rprx-vision to gRPC nodes. Xray treats
# Vision as TCP-only, while Mihomo v1.19.30 attempts to wrap the gRPC gun.Conn in
# vision.NewConn and cannot find the required outer TLS connection. Ignore only
# that invalid gRPC+Vision combination; valid TCP Vision profiles are untouched.
python3 - <<'PYEOF'
p = 'adapter/outbound/vless.go'
s = open(p).read()
old = '''func NewVless(option VlessOption) (*Vless, error) {
\tvar addons *vless.Addons'''
new = '''func NewVless(option VlessOption) (*Vless, error) {
\tif strings.EqualFold(option.Network, "grpc") && option.Flow == vless.XRV {
\t\toption.Flow = ""
\t}
\tvar addons *vless.Addons'''
if old in s:
    open(p, 'w').write(s.replace(old, new, 1))
    print("VLESS gRPC Vision compatibility patch applied")
elif new in s:
    print("VLESS gRPC Vision compatibility patch already applied")
else:
    raise SystemExit(f"FATAL: VLESS NewVless layout changed: {p}")
PYEOF

# Preserve the exact VLESS gRPC request/response exchange in device diagnostics.
# This is diagnostic-only: it does not change serviceName, request path, headers,
# retries, fallback behavior, or any proxy selection. Immediate response headers
# distinguish an HTTP/2 path rejection; trailers capture grpc-status when the
# server returns it only after the streaming body terminates.
python3 - <<'PYEOF'
p = 'transport/gun/gun.go'
s = open(p).read()
old_import = '''\tC "github.com/metacubex/mihomo/constant"
\t"github.com/metacubex/mihomo/transport/vmess"'''
new_import = '''\tC "github.com/metacubex/mihomo/constant"
\t"github.com/metacubex/mihomo/log"
\t"github.com/metacubex/mihomo/transport/vmess"'''
old_config = '''type Config struct {
\tServiceName  string
\tUserAgent    string
\tHost         string
\tPingInterval int
}'''
new_config = '''type Config struct {
\tServiceName  string
\tUserAgent    string
\tHost         string
\tPingInterval int
}

type detourGRPCResponseBody struct {
\tio.ReadCloser
\tresponse *http.Response
\tpath     string
\tonce     sync.Once
}

func (b *detourGRPCResponseBody) logEnd(err error) {
\tb.once.Do(func() {
\t\tlog.Infoln("[DETOUR_GRPC] stage=body_end path=%q status=%d grpc-status=%q grpc-message=%q error=%v", b.path, b.response.StatusCode, b.response.Trailer.Get("Grpc-Status"), b.response.Trailer.Get("Grpc-Message"), err)
\t})
}

func (b *detourGRPCResponseBody) Read(p []byte) (int, error) {
\tn, err := b.ReadCloser.Read(p)
\tif err != nil {
\t\tb.logEnd(err)
\t}
\treturn n, err
}

func (b *detourGRPCResponseBody) Close() error {
\terr := b.ReadCloser.Close()
\tb.logEnd(err)
\treturn err
}'''
old_path = '''\tpath := ServiceNameToPath(serviceName)

\treader, writer := io.Pipe()'''
new_path = '''\tpath := ServiceNameToPath(serviceName)
\tlog.Infoln("[DETOUR_GRPC] stage=request host=%q service=%q path=%q", t.cfg.Host, serviceName, path)

\treader, writer := io.Pipe()'''
old_response = '''\t\t\tresponse, err := t.transport.RoundTrip(request)
\t\t\tif err != nil {
\t\t\t\treturn nil, err
\t\t\t}
\t\t\treturn response.Body, nil'''
new_response = '''\t\t\tresponse, err := t.transport.RoundTrip(request)
\t\t\tif err != nil {
\t\t\t\tlog.Infoln("[DETOUR_GRPC] stage=roundtrip_error path=%q error=%v", path, err)
\t\t\t\treturn nil, err
\t\t\t}
\t\t\tlog.Infoln("[DETOUR_GRPC] stage=response path=%q status=%d content-type=%q grpc-status=%q grpc-message=%q", path, response.StatusCode, response.Header.Get("Content-Type"), response.Header.Get("Grpc-Status"), response.Header.Get("Grpc-Message"))
\t\t\treturn &detourGRPCResponseBody{ReadCloser: response.Body, response: response, path: path}, nil'''
if old_import in s and old_config in s and old_path in s and old_response in s:
    s = s.replace(old_import, new_import, 1)
    s = s.replace(old_config, new_config, 1)
    s = s.replace(old_path, new_path, 1)
    s = s.replace(old_response, new_response, 1)
    open(p, 'w').write(s)
    print("VLESS gRPC transport diagnostics patch applied")
elif new_import in s and new_config in s and new_path in s and new_response in s:
    print("VLESS gRPC transport diagnostics patch already applied")
else:
    raise SystemExit(f"FATAL: gRPC transport diagnostics layout changed: {p}")
PYEOF

# Xray-core v26.7.11+ defaults REALITY minClientVer to 26.3.27. Mihomo
# v1.19.30 still advertises 1.8.2 in the encrypted ClientHello SessionId, so a
# default modern Xray REALITY server rejects the handshake and the client sees
# an EOF/connection-closed fallback. Detour embeds Mihomo, so advertise the
# minimum accepted modern Xray version while keeping the pinned engine otherwise
# unchanged.
#
# At the same layer, preserve the REALITY stage in returned errors and emit a
# stable DETOUR_REALITY log line. Upstream otherwise returns the bare transport
# error (often just EOF/connection closed), which makes runtime failures
# indistinguishable from unrelated HTTP/TCP closures in Android diagnostics.
python3 - <<'PYEOF'
p = 'component/tls/reality.go'
s = open(p).read()
old_const = '''const RealityMaxShortIDLen = 8'''
new_const = '''const (
\tRealityMaxShortIDLen = 8
\tRealityClientVersionMajor byte = 26
\tRealityClientVersionMinor byte = 3
\tRealityClientVersionPatch byte = 27
\tDetourRealityDiagnosticsEnabled = true
)'''
old_version = '''\t\thello.SessionId[0] = 1
\t\thello.SessionId[1] = 8
\t\thello.SessionId[2] = 2'''
new_version = '''\t\thello.SessionId[0] = RealityClientVersionMajor
\t\thello.SessionId[1] = RealityClientVersionMinor
\t\thello.SessionId[2] = RealityClientVersionPatch'''
old_import = '''\t"errors"
\t"net"'''
new_import = '''\t"errors"
\t"fmt"
\t"net"'''
old_handshake = '''\t\terr = uConn.HandshakeContext(ctx)
\t\tif err != nil {
\t\t\treturn nil, err
\t\t}'''
new_handshake = '''\t\terr = uConn.HandshakeContext(ctx)
\t\tif err != nil {
\t\t\tlog.Errorln("[DETOUR_REALITY] server=%q fingerprint=%q stage=handshake error=%v", serverName, fingerprint.Client, err)
\t\t\treturn nil, fmt.Errorf("REALITY handshake failed: %w", err)
\t\t}'''
old_auth = '''\t\tif !verifier.verified {
\t\t\tgo realityClientFallback(uConn, uConfig.ServerName, fingerprint)
\t\t\treturn nil, errors.New("REALITY authentication failed")
\t\t}'''
new_auth = '''\t\tif !verifier.verified {
\t\t\tlog.Errorln("[DETOUR_REALITY] server=%q fingerprint=%q stage=authentication error=authentication_failed", serverName, fingerprint.Client)
\t\t\tgo realityClientFallback(uConn, uConfig.ServerName, fingerprint)
\t\t\treturn nil, errors.New("REALITY authentication failed")
\t\t}'''
if old_const in s and old_version in s and old_import in s and old_handshake in s and old_auth in s:
    s = s.replace(old_const, new_const, 1)
    s = s.replace(old_version, new_version, 1)
    s = s.replace(old_import, new_import, 1)
    s = s.replace(old_handshake, new_handshake, 1)
    s = s.replace(old_auth, new_auth, 1)
    open(p, 'w').write(s)
    print("REALITY compatibility/diagnostics patch applied")
elif new_const in s and new_version in s and new_import in s and new_handshake in s and new_auth in s:
    print("REALITY compatibility/diagnostics patch already applied")
else:
    raise SystemExit(f"FATAL: REALITY compatibility/diagnostics layout changed: {p}")
PYEOF

# URLTest already receives the exact transport error, but Mihomo v1.19.30 drops
# that error after updating alive/history state. Detour's UI consequently sees
# only a missing delay. Emit one stable, node-attributed diagnostic record for
# every failed URLTest. The classifier intentionally prioritizes REALITY before
# generic connection-closed/EOF so wrapped REALITY failures remain actionable.
python3 - <<'PYEOF'
p = 'adapter/adapter.go'
s = open(p).read()
old_import = '''\t"encoding/json"
\t"fmt"'''
new_import = '''\t"encoding/json"
\t"errors"
\t"fmt"'''
old_const = '''const (
\tdefaultHistoriesNum = 10
)'''
new_const = '''const (
\tdefaultHistoriesNum = 10
\tDetourURLTestDiagnosticsEnabled = true
)

func DetourURLTestErrorClass(err error) string {
\tif err == nil {
\t\treturn ""
\t}
\tlower := strings.ToLower(err.Error())
\tswitch {
\tcase errors.Is(err, context.DeadlineExceeded):
\t\treturn "timeout"
\tcase strings.Contains(lower, "reality"):
\t\treturn "reality"
\tcase strings.Contains(lower, "no such host"), strings.Contains(lower, "lookup "), strings.Contains(lower, "dns"):
\t\treturn "dns"
\tcase strings.Contains(lower, "tls"), strings.Contains(lower, "x509"), strings.Contains(lower, "certificate"), strings.Contains(lower, "handshake"):
\t\treturn "tls"
\tcase strings.Contains(lower, "grpc"):
\t\treturn "grpc"
\tcase strings.Contains(lower, "eof"), strings.Contains(lower, "connection closed"), strings.Contains(lower, "reset by peer"), strings.Contains(lower, "broken pipe"):
\t\treturn "connection"
\tcase strings.Contains(lower, "dial "), strings.Contains(lower, "connect:"), strings.Contains(lower, "connection refused"), strings.Contains(lower, "network is unreachable"):
\t\treturn "dial"
\tdefault:
\t\tif timeoutErr, ok := err.(interface{ Timeout() bool }); ok && timeoutErr.Timeout() {
\t\t\treturn "timeout"
\t\t}
\t\treturn "other"
\t}
}

func DetourURLTestErrorText(err error) string {
\tif err == nil {
\t\treturn ""
\t}
\ttext := strings.Map(func(r rune) rune {
\t\tif r < 0x20 || r == 0x7f {
\t\t\treturn ' '
\t\t}
\t\treturn r
\t}, err.Error())
\tif index := strings.Index(strings.ToLower(text), "vless://"); index >= 0 {
\t\ttext = text[:index] + "vless://<redacted>"
\t}
\tif len(text) > 800 {
\t\ttext = text[:800]
\t}
\treturn text
}'''
old_defer = '''\tdefer func() {
\t\talive := err == nil'''
new_defer = '''\tdefer func() {
\t\tif err != nil {
\t\t\tlog.Errorln("[DETOUR_URLTEST] node=%q type=%s class=%s error=%s", p.Name(), p.Type().String(), DetourURLTestErrorClass(err), DetourURLTestErrorText(err))
\t\t}
\t\talive := err == nil'''
if old_import in s and old_const in s and old_defer in s:
    s = s.replace(old_import, new_import, 1)
    s = s.replace(old_const, new_const, 1)
    s = s.replace(old_defer, new_defer, 1)
    open(p, 'w').write(s)
    print("URLTest diagnostics patch applied")
elif new_import in s and new_const in s and new_defer in s:
    print("URLTest diagnostics patch already applied")
else:
    raise SystemExit(f"FATAL: URLTest diagnostics layout changed: {p}")
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

# Older branch revisions dropped offline/pre-URLTest errors in the bridge. Keep
# this fallback patch for reproducible historical builds, but skip it when the
# bridge already contains native per-node error diagnostics.
python3 - "$WORK_DIR/subscription_prepare.go" <<'PYEOF'
import sys
p = sys.argv[1]
s = open(p).read()
old_import = '''\tC "github.com/metacubex/mihomo/constant"
\t"github.com/metacubex/mihomo/tunnel"'''
new_import = '''\tC "github.com/metacubex/mihomo/constant"
\t"github.com/metacubex/mihomo/log"
\t"github.com/metacubex/mihomo/tunnel"'''
old_result = '''\t\t\tdelay, testErr := probe(ctx)
\t\t\tif testErr == nil && delay > 0 {
\t\t\t\tresults[i].DelayMs = delay
\t\t\t}'''
new_result = '''\t\t\tdelay, testErr := probe(ctx)
\t\t\tif testErr == nil && delay > 0 {
\t\t\t\tresults[i].DelayMs = delay
\t\t\t} else if testErr != nil {
\t\t\t\tlog.Errorln("[DETOUR_SUBSCRIPTION_TEST] node=%q class=%s error=%s", results[i].Name, adapter.DetourURLTestErrorClass(testErr), adapter.DetourURLTestErrorText(testErr))
\t\t\t}'''
if '[DETOUR_SUBSCRIPTION_TEST]' in s and 'ErrorClass string `json:"errorClass,omitempty"`' in s:
    print("subscription outer diagnostics already implemented in bridge")
elif old_import in s and old_result in s:
    s = s.replace(old_import, new_import, 1)
    s = s.replace(old_result, new_result, 1)
    open(p, 'w').write(s)
    print("subscription outer diagnostics patch applied")
elif new_import in s and new_result in s:
    print("subscription outer diagnostics patch already applied")
else:
    raise SystemExit(f"FATAL: subscription diagnostics layout changed: {p}")
PYEOF

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
