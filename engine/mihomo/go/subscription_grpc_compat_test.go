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
	if got := proxies[0]["network"]; got != "grpc" {
		t.Fatalf("network = %v, want grpc", got)
	}
	grpcOpts, ok := proxies[0]["grpc-opts"].(map[string]any)
	if !ok {
		t.Fatalf("grpc-opts = %#v, want map", proxies[0]["grpc-opts"])
	}
	if got := grpcOpts["grpc-service-name"]; got != "grpc" {
		t.Fatalf("grpc-service-name = %v, want compatibility default grpc", got)
	}
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
	grpcOpts, ok := proxies[0]["grpc-opts"].(map[string]any)
	if !ok {
		t.Fatalf("grpc-opts = %#v, want map", proxies[0]["grpc-opts"])
	}
	if got := grpcOpts["grpc-service-name"]; got != "custom-service" {
		t.Fatalf("grpc-service-name = %v, want explicit custom-service", got)
	}
}
