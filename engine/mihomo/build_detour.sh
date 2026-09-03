#!/usr/bin/env bash
# Canonical Detour Mihomo build entrypoint.
#
# build.sh contains the pinned upstream embedding patches accumulated for the
# Android engine. This wrapper applies the narrow Xray gRPC compatibility delta
# to a temporary copy so omitted VLESS gRPC serviceName values stay omitted and
# resolve to Xray's //Tun method path instead of Mihomo's GunService default.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMPL="$ROOT/engine/mihomo/build.sh"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

python3 - "$IMPL" "$TMP" <<'PYEOF'
from pathlib import Path
import sys

src = Path(sys.argv[1])
out = Path(sys.argv[2])
s = src.read_text()

# The earlier URI compatibility experiment forced omitted serviceName to "grpc".
# That is not Xray semantics. Preserve the empty value so YAML and URI providers
# behave identically; the VLESS-only runtime patch below maps it to //Tun.
old_uri_fragment = r'''\t\tserviceName := query.Get("serviceName")
\t\tif serviceName == "" {
\t\t\tserviceName = "grpc"
\t\t}
\t\tgrpcOpts["grpc-service-name"] = serviceName'''
new_uri_fragment = r'''\t\tgrpcOpts["grpc-service-name"] = query.Get("serviceName")'''
if old_uri_fragment not in s:
    raise SystemExit("FATAL: VLESS URI gRPC compatibility block layout changed")
s = s.replace(old_uri_fragment, new_uri_fragment, 1)

marker = '# Preserve the exact VLESS gRPC request/response exchange in device diagnostics.\n'
if marker not in s:
    raise SystemExit("FATAL: gRPC diagnostics marker not found")

xray_empty_service_patch = r"""# Xray-core keeps an omitted gRPC serviceName empty. Its normal Tun RPC method
# therefore resolves to //Tun. Mihomo's gun transport instead substitutes
# "GunService" for an empty value, producing /GunService/Tun and grpc-status=12
# (unknown service GunService) against Xray servers configured with the default
# empty service name. Apply the Xray path only to VLESS gRPC with an omitted
# service name; explicit service names and other protocols are untouched.
python3 - <<'PYEOF'
p = 'adapter/outbound/vless.go'
s = open(p).read()
old = '''\t\tgunConfig := &gun.Config{
\t\t\tServiceName:  option.GrpcOpts.GrpcServiceName,
\t\t\tUserAgent:    option.GrpcOpts.GrpcUserAgent,
\t\t\tHost:         option.ServerName,
\t\t\tPingInterval: option.GrpcOpts.PingInterval,
\t\t}'''
new = '''\t\tserviceName := option.GrpcOpts.GrpcServiceName
\t\tif serviceName == "" {
\t\t\tserviceName = "//Tun"
\t\t}
\t\tgunConfig := &gun.Config{
\t\t\tServiceName:  serviceName,
\t\t\tUserAgent:    option.GrpcOpts.GrpcUserAgent,
\t\t\tHost:         option.ServerName,
\t\t\tPingInterval: option.GrpcOpts.PingInterval,
\t\t}'''
if old in s:
    open(p, 'w').write(s.replace(old, new, 1))
    print("VLESS empty gRPC service-name Xray compatibility patch applied")
elif new in s:
    print("VLESS empty gRPC service-name Xray compatibility patch already applied")
else:
    raise SystemExit(f"FATAL: VLESS gRPC gun config layout changed: {p}")
PYEOF

"""
s = s.replace(marker, xray_empty_service_patch + marker, 1)
out.write_text(s)
PYEOF

exec bash "$TMP"
