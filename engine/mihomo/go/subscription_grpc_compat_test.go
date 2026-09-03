package engine

import "testing"

func TestPreparedSubscriptionDefaultsMissingGRPCServiceName(t *testing.T) {
	const link = "vless://a1b2c3d4-eacc-4433-981b-7e5f9a8b1234@grpc.example.com:443?encryption=none&security=reality&type=grpc&sni=www.starlink.com&fp=firefox&pbk=" + compatRealityPublicKey + "&sid=6ba85179f3a2b4c5#GrpcDefault"

	proxies, err := parsePreparedSubscriptionProxies([]byte(link))
	if err != nil {
		t.Fatalf("gRPC Reality VLESS subscription body was rejected: %v", err)
	}
	if len(proxies) != 1 {
		t.Fatalf("got %d proxies, want 1", len(proxies))
	}
	assertGRPCServiceName(t, proxies[0], "grpc")
}

func TestPreparedSubscriptionPreservesExplicitGRPCServiceName(t *testing.T) {
	const link = "vless://a1b2c3d4-eacc-4433-981b-7e5f9a8b1234@grpc.example.com:443?encryption=none&security=reality&type=grpc&serviceName=custom-service&sni=www.starlink.com&fp=firefox&pbk=" + compatRealityPublicKey + "&sid=6ba85179f3a2b4c5#GrpcExplicit"

	proxies, err := parsePreparedSubscriptionProxies([]byte(link))
	if err != nil {
		t.Fatalf("gRPC Reality VLESS subscription body was rejected: %v", err)
	}
	if len(proxies) != 1 {
		t.Fatalf("got %d proxies, want 1", len(proxies))
	}
	assertGRPCServiceName(t, proxies[0], "custom-service")
}

func TestPreparedYAMLSubscriptionDefaultsMissingGRPCServiceName(t *testing.T) {
	body := []byte(`proxies:
  - name: GrpcYamlDefault
    type: vless
    server: grpc.example.com
    port: 443
    uuid: a1b2c3d4-eacc-4433-981b-7e5f9a8b1234
    network: grpc
    tls: true
    servername: www.starlink.com
    client-fingerprint: firefox
    reality-opts:
      public-key: ` + compatRealityPublicKey + `
      short-id: 6ba85179f3a2b4c5
`)

	proxies, err := parsePreparedSubscriptionProxies(body)
	if err != nil {
		t.Fatalf("YAML gRPC Reality VLESS subscription body was rejected: %v", err)
	}
	if len(proxies) != 1 {
		t.Fatalf("got %d proxies, want 1", len(proxies))
	}
	assertGRPCServiceName(t, proxies[0], "grpc")
}

func TestPreparedYAMLSubscriptionPreservesExplicitGRPCServiceName(t *testing.T) {
	body := []byte(`proxies:
  - name: GrpcYamlExplicit
    type: vless
    server: grpc.example.com
    port: 443
    uuid: a1b2c3d4-eacc-4433-981b-7e5f9a8b1234
    network: grpc
    tls: true
    servername: www.starlink.com
    client-fingerprint: firefox
    grpc-opts:
      grpc-service-name: custom-service
    reality-opts:
      public-key: ` + compatRealityPublicKey + `
      short-id: 6ba85179f3a2b4c5
`)

	proxies, err := parsePreparedSubscriptionProxies(body)
	if err != nil {
		t.Fatalf("YAML explicit gRPC Reality VLESS subscription body was rejected: %v", err)
	}
	if len(proxies) != 1 {
		t.Fatalf("got %d proxies, want 1", len(proxies))
	}
	assertGRPCServiceName(t, proxies[0], "custom-service")
}

func assertGRPCServiceName(t *testing.T, proxy map[string]any, want string) {
	t.Helper()
	if got := proxy["network"]; got != "grpc" {
		t.Fatalf("network = %v, want grpc", got)
	}
	grpcOpts, ok := proxy["grpc-opts"].(map[string]any)
	if !ok {
		t.Fatalf("grpc-opts = %#v, want map", proxy["grpc-opts"])
	}
	if got := grpcOpts["grpc-service-name"]; got != want {
		t.Fatalf("grpc-service-name = %v, want %q", got, want)
	}
}
