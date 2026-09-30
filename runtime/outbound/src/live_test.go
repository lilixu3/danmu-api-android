package main

import (
	"context"
	"os"
	"testing"
	"time"
)

func TestLiveH3ECH(t *testing.T) {
	if os.Getenv("DANMU_OUTBOUND_LIVE_TEST") != "1" {
		t.Skip("opt-in live network check")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	resolver := newResolver("", 3*time.Second)
	defer resolver.close()
	records, err := resolver.resolve(ctx, "api.gamer.com.tw")
	if err != nil {
		t.Fatal(err)
	}
	t.Logf("addresses=%d h3=%t echBytes=%d", len(records.ips), records.h3, len(records.ech))
	connection, err := dialAddresses(ctx, "api.gamer.com.tw", records, "h3")
	if err != nil {
		t.Fatalf("H3/ECH handshake: %v", err)
	}
	defer connection.close()
	if !connection.ech {
		t.Fatal("ECH not accepted")
	}
}
