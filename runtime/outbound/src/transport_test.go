package main

import (
	"context"
	"crypto/tls"
	"errors"
	"net/http"
	"sync/atomic"
	"testing"
	"time"
)

type countedRoundTripper struct{ calls *atomic.Int32 }

func (r countedRoundTripper) RoundTrip(*http.Request) (*http.Response, error) {
	r.calls.Add(1)
	return nil, errors.New("failed after send")
}
func stubSession(protocol string) *session {
	return &session{protocol: protocol, close: func() {}, alive: func() bool { return true }}
}
func TestUDPFailureFallsBackAndCoolsDown(t *testing.T) {
	transport := newOutboundTransport(newResolver("", time.Second), "auto", time.Second)
	var h2, h3 atomic.Int32
	transport.dial = func(ctx context.Context, _ string, _ targetRecords, protocol string) (*session, error) {
		if protocol == "h3" {
			h3.Add(1)
			<-ctx.Done()
			return nil, ctx.Err()
		}
		h2.Add(1)
		return stubSession("h2"), nil
	}
	state := &hostState{}
	records := targetRecords{h3: true}
	for i := 0; i < 3; i++ {
		start := time.Now()
		conn, err := transport.connect(context.Background(), "api.tmdb.org", records, state)
		if err != nil || conn.protocol != "h2" {
			t.Fatalf("expected h2: %v", err)
		}
		if time.Since(start) > 500*time.Millisecond {
			t.Fatal("blocked UDP delayed request")
		}
	}
	if h3.Load() != 2 || h2.Load() != 3 || time.Now().After(state.cooldown) {
		t.Fatalf("cooldown did not suppress probe: h3=%d h2=%d", h3.Load(), h2.Load())
	}
}
func TestForcedH3DoesNotFallback(t *testing.T) {
	transport := newOutboundTransport(newResolver("", time.Second), "h3", time.Second)
	transport.dial = func(_ context.Context, _ string, _ targetRecords, protocol string) (*session, error) {
		if protocol != "h3" {
			t.Fatal("forced h3 fell back")
		}
		return nil, errors.New("UDP blocked")
	}
	if _, err := transport.connect(context.Background(), "api.tmdb.org", targetRecords{}, &hostState{}); err == nil {
		t.Fatal("expected error")
	}
}
func TestECHRefreshIsBoundedAndBusinessRequestIsNotReplayed(t *testing.T) {
	for _, failAlways := range []bool{false, true} {
		t.Run(map[bool]string{false: "rotation", true: "persistent rejection"}[failAlways], func(t *testing.T) {
			transport := newOutboundTransport(newResolver("", time.Second), "h2", time.Second)
			var resolutions, handshakes int
			var sent atomic.Int32
			transport.resolve = func(context.Context, string) (targetRecords, error) {
				resolutions++
				return targetRecords{ech: []byte{byte(resolutions)}}, nil
			}
			transport.dial = func(_ context.Context, _ string, records targetRecords, _ string) (*session, error) {
				handshakes++
				if records.ech[0] == 1 || failAlways {
					return nil, &tls.ECHRejectionError{RetryConfigList: []byte{2}}
				}
				conn := stubSession("h2")
				conn.rt = countedRoundTripper{&sent}
				return conn, nil
			}
			request, _ := http.NewRequest("POST", "https://api.gamer.com.tw/x", nil)
			_, _, err := transport.roundTrip(request)
			if err == nil || resolutions != 2 || handshakes != 2 {
				t.Fatalf("retry not bounded: resolutions=%d handshakes=%d err=%v", resolutions, handshakes, err)
			}
			expected := int32(1)
			if failAlways {
				expected = 0
			}
			if sent.Load() != expected {
				t.Fatalf("unexpected business sends %d", sent.Load())
			}
		})
	}
}
func TestECHNeverDowngradesAndTMDBDoesNotRequireIt(t *testing.T) {
	if verifyECH("api.gamer.com.tw", tls.ConnectionState{}) == nil {
		t.Fatal("Bahamut accepted ordinary SNI")
	}
	if verifyECH("api.tmdb.org", tls.ConnectionState{}) != nil {
		t.Fatal("TMDB incorrectly requires ECH")
	}
	transport := newOutboundTransport(newResolver("", time.Second), "h2", time.Second)
	transport.resolve = func(context.Context, string) (targetRecords, error) { return targetRecords{}, nil }
	transport.dial = func(context.Context, string, targetRecords, string) (*session, error) {
		t.Fatal("connected with missing ECH")
		return nil, nil
	}
	if _, err := transport.getSession(context.Background(), "api.gamer.com.tw"); err == nil {
		t.Fatal("missing config must fail")
	}
}
func TestPoolReusesHandshake(t *testing.T) {
	transport := newOutboundTransport(newResolver("", time.Second), "h2", time.Second)
	var count int
	transport.resolve = func(context.Context, string) (targetRecords, error) { return targetRecords{}, nil }
	transport.dial = func(context.Context, string, targetRecords, string) (*session, error) {
		count++
		return stubSession("h2"), nil
	}
	for i := 0; i < 5; i++ {
		if _, err := transport.getSession(context.Background(), "api.tmdb.org"); err != nil {
			t.Fatal(err)
		}
	}
	if count != 1 {
		t.Fatalf("repeated handshakes %d", count)
	}
}
func TestDialCancellation(t *testing.T) {
	transport := newOutboundTransport(newResolver("", time.Second), "auto", time.Second)
	transport.dial = func(ctx context.Context, _ string, _ targetRecords, _ string) (*session, error) {
		<-ctx.Done()
		return nil, ctx.Err()
	}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Millisecond)
	defer cancel()
	start := time.Now()
	_, err := transport.connect(ctx, "api.tmdb.org", targetRecords{h3: true}, &hostState{})
	if err == nil || time.Since(start) > 200*time.Millisecond {
		t.Fatal("cancellation failed")
	}
}

func TestCooldownExpiresAndProbeCanRecover(t *testing.T) {
	transport := newOutboundTransport(newResolver("", time.Second), "auto", time.Second)
	state := &hostState{failures: 2, cooldown: time.Now().Add(-time.Second), lastProtocol: "h2"}
	transport.dial = func(ctx context.Context, _ string, _ targetRecords, protocol string) (*session, error) {
		if protocol == "h3" {
			return stubSession("h3"), nil
		}
		<-ctx.Done()
		return nil, ctx.Err()
	}
	conn, err := transport.connect(context.Background(), "api.tmdb.org", targetRecords{h3: true}, state)
	if err != nil || conn.protocol != "h3" || state.failures != 0 {
		t.Fatalf("H3 did not recover: %v", err)
	}
}

func TestConcurrentRequestsShareOneHandshake(t *testing.T) {
	transport := newOutboundTransport(newResolver("", time.Second), "h2", time.Second)
	var handshakes atomic.Int32
	transport.resolve = func(context.Context, string) (targetRecords, error) { return targetRecords{}, nil }
	transport.dial = func(context.Context, string, targetRecords, string) (*session, error) {
		handshakes.Add(1)
		return stubSession("h2"), nil
	}
	results := make(chan error, 20)
	for i := 0; i < 20; i++ {
		go func() { _, err := transport.getSession(context.Background(), "api.tmdb.org"); results <- err }()
	}
	for i := 0; i < 20; i++ {
		if err := <-results; err != nil {
			t.Fatal(err)
		}
	}
	if handshakes.Load() != 1 {
		t.Fatalf("concurrent requests created %d handshakes", handshakes.Load())
	}
}

func TestCancelledStreamKeepsSharedConnection(t *testing.T) {
	transport := newOutboundTransport(newResolver("", time.Second), "h2", time.Second)
	var handshakes, closed atomic.Int32
	transport.resolve = func(context.Context, string) (targetRecords, error) { return targetRecords{}, nil }
	transport.dial = func(context.Context, string, targetRecords, string) (*session, error) {
		handshakes.Add(1)
		conn := stubSession("h2")
		conn.close = func() { closed.Add(1) }
		conn.rt = roundTripFunc(func(req *http.Request) (*http.Response, error) {
			<-req.Context().Done()
			return nil, req.Context().Err()
		})
		return conn, nil
	}
	ctx, cancel := context.WithCancel(context.Background())
	request, _ := http.NewRequestWithContext(ctx, "GET", "https://api.tmdb.org/x", nil)
	done := make(chan error, 1)
	go func() { _, _, err := transport.roundTrip(request); done <- err }()
	// Wait until the pooled connection exists before cancelling only this stream.
	for handshakes.Load() == 0 {
		time.Sleep(time.Millisecond)
	}
	cancel()
	if err := <-done; err == nil {
		t.Fatal("request did not cancel")
	}
	if _, err := transport.getSession(context.Background(), "api.tmdb.org"); err != nil {
		t.Fatal(err)
	}
	if closed.Load() != 0 || handshakes.Load() != 1 {
		t.Fatalf("cancel retired shared session: closed=%d handshakes=%d", closed.Load(), handshakes.Load())
	}
}
