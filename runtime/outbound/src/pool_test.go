package main

import (
	"context"
	"crypto/tls"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"golang.org/x/net/http2"
)

func TestH2FullCapacityQueuesWithoutClosingActiveStream(t *testing.T) {
	started := make(chan struct{})
	release := make(chan struct{})
	var releaseOnce sync.Once
	unblock := func() { releaseOnce.Do(func() { close(release) }) }
	server := httptest.NewUnstartedServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/one" {
			close(started)
			<-release
		}
		io.WriteString(w, "ok")
	}))
	server.EnableHTTP2 = true
	server.Config.HTTP2 = &http.HTTP2Config{MaxConcurrentStreams: 1}
	if err := http2.ConfigureServer(server.Config, &http2.Server{MaxConcurrentStreams: 1}); err != nil {
		t.Fatal(err)
	}
	server.StartTLS()
	defer server.Close()
	defer unblock()
	transport := newOutboundTransport(newResolver("", time.Second), "h2", time.Second)
	defer transport.close()
	transport.resolve = func(context.Context, string) (targetRecords, error) { return targetRecords{}, nil }
	var handshakes atomic.Int32
	var client *http2.ClientConn
	transport.dial = func(context.Context, string, targetRecords, string) (*session, error) {
		handshakes.Add(1)
		config := server.Client().Transport.(*http.Transport).TLSClientConfig.Clone()
		config.NextProtos = []string{"h2"}
		conn, err := tls.Dial("tcp", server.Listener.Addr().String(), config)
		if err != nil {
			return nil, err
		}
		result, err := newH2Session(conn, false)
		if err == nil {
			client = result.rt.(*http2.ClientConn)
		}
		return result, err
	}
	first, _ := http.NewRequest("GET", "https://api.tmdb.org/one", nil)
	firstDone := make(chan error, 1)
	go func() {
		response, _, err := transport.roundTrip(first)
		if response != nil {
			io.Copy(io.Discard, response.Body)
			response.Body.Close()
		}
		firstDone <- err
	}()
	select {
	case <-started:
	case <-time.After(time.Second):
		t.Fatal("first stream not started")
	}
	settingsDeadline := time.Now().Add(time.Second)
	for client.State().MaxConcurrentStreams != 1 && time.Now().Before(settingsDeadline) {
		time.Sleep(time.Millisecond)
	}
	state := client.State()
	if state.MaxConcurrentStreams != 1 || state.StreamsActive != 1 || state.Closed {
		t.Fatalf("unexpected H2 state %+v", state)
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	second, _ := http.NewRequestWithContext(ctx, "GET", "https://api.tmdb.org/two", nil)
	secondDone := make(chan error, 1)
	go func() {
		response, _, err := transport.roundTrip(second)
		if response != nil {
			response.Body.Close()
		}
		secondDone <- err
	}()
	deadline := time.Now().Add(time.Second)
	for client.State().StreamsPending == 0 && time.Now().Before(deadline) {
		time.Sleep(time.Millisecond)
	}
	if client.State().StreamsPending != 1 || client.State().Closed || handshakes.Load() != 1 {
		unblock()
		t.Fatalf("capacity retired connection or failed to queue: %+v handshakes=%d", client.State(), handshakes.Load())
	}
	cancel()
	select {
	case err := <-secondDone:
		if err == nil {
			t.Fatal("queued stream did not cancel")
		}
	case <-time.After(time.Second):
		t.Fatal("queued cancellation did not settle")
	}
	if client.State().Closed {
		unblock()
		t.Fatal("cancel closed the shared connection")
	}
	unblock()
	if err := <-firstDone; err != nil {
		t.Fatalf("first request interrupted: %v", err)
	}
}
func TestRetiringConnectionDrainsExistingResponseBody(t *testing.T) {
	transport := newOutboundTransport(newResolver("", time.Second), "h2", time.Second)
	defer transport.close()
	transport.resolve = func(context.Context, string) (targetRecords, error) { return targetRecords{}, nil }
	var closed atomic.Int32
	var first *session
	var count int
	var retiring atomic.Bool
	transport.dial = func(context.Context, string, targetRecords, string) (*session, error) {
		count++
		conn := stubSession("h2")
		if count == 1 {
			first = conn
			conn.close = func() { closed.Add(1) }
			conn.reusable = func() bool { return !retiring.Load() }
			conn.rt = roundTripFunc(func(*http.Request) (*http.Response, error) {
				return &http.Response{StatusCode: 200, Body: io.NopCloser(strings.NewReader("body"))}, nil
			})
		}
		return conn, nil
	}
	request, _ := http.NewRequest("GET", "https://api.tmdb.org/x", nil)
	response, _, err := transport.roundTrip(request)
	if err != nil {
		t.Fatal(err)
	}
	retiring.Store(true)
	next, err := transport.getSession(context.Background(), "api.tmdb.org")
	if err != nil {
		t.Fatal(err)
	}
	if next == first || closed.Load() != 0 {
		t.Fatal("GOAWAY retirement closed an active body")
	}
	response.Body.Close()
	if closed.Load() != 1 {
		t.Fatal("retired connection did not close after drain")
	}
}
