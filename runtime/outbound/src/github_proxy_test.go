package main

import (
	"bufio"
	"context"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

const githubTestToken = "github-test-token-012345678901234567890"

func TestGithubCONNECTRejectsUntrustedTargetsAndAuthentication(t *testing.T) {
	server := newGithubTunnelServer(githubTestToken, newResolver("", time.Second), time.Second)
	defer server.close()
	server.addresses = func(context.Context, string) ([]string, error) {
		t.Error("unexpected DNS query")
		return nil, fmt.Errorf("invalid target")
	}
	tests := []struct {
		method, target, token string
		code                  int
	}{
		{"GET", "https://github.com/", githubTestToken, 405},
		{"CONNECT", "github.com:443", "", 407},
		{"CONNECT", "github.com.evil.test:443", githubTestToken, 403},
		{"CONNECT", "github.com:80", githubTestToken, 403},
		{"CONNECT", "127.0.0.1:443", githubTestToken, 403},
		{"CONNECT", "api.gamer.com.tw:443", githubTestToken, 403},
	}
	for _, test := range tests {
		request := httptest.NewRequest(test.method, test.target, nil)
		request.Header.Set("Proxy-Authorization", "Bearer "+test.token)
		response := httptest.NewRecorder()
		server.ServeHTTP(response, request)
		if response.Code != test.code {
			t.Fatalf("%s: got %d want %d", test.target, response.Code, test.code)
		}
	}
}

func connectGithubTest(t *testing.T, server *githubTunnelServer) (net.Conn, *bufio.Reader) {
	t.Helper()
	httpServer := httptest.NewServer(server)
	t.Cleanup(httpServer.Close)
	conn, err := net.Dial("tcp", strings.TrimPrefix(httpServer.URL, "http://"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { conn.Close() })
	conn.SetDeadline(time.Now().Add(5 * time.Second))
	fmt.Fprintf(conn, "CONNECT github.com:443 HTTP/1.1\r\nHost: github.com:443\r\nProxy-Authorization: Bearer %s\r\n\r\n", githubTestToken)
	reader := bufio.NewReader(conn)
	return conn, reader
}

func TestGithubTunnelStreamsPastAPIBodyLimitAndKeepsCredentialsLocal(t *testing.T) {
	server := newGithubTunnelServer(githubTestToken, newResolver("", time.Second), time.Second)
	defer server.close()
	server.addresses = func(context.Context, string) ([]string, error) { return []string{"203.0.113.9"}, nil }
	const total = 33 << 20
	upstreamRead := make(chan string, 1)
	server.dial = func(context.Context, string, string) (net.Conn, error) {
		tunnel, remote := net.Pipe()
		go func() {
			defer remote.Close()
			sample := make([]byte, 8)
			io.ReadFull(remote, sample)
			upstreamRead <- string(sample)
			block := make([]byte, 32*1024)
			for remaining := total; remaining > 0; {
				n, err := remote.Write(block[:min(len(block), remaining)])
				if err != nil {
					return
				}
				remaining -= n
			}
		}()
		return tunnel, nil
	}
	conn, reader := connectGithubTest(t, server)
	response, err := http.ReadResponse(reader, &http.Request{Method: "CONNECT"})
	if err != nil || response.StatusCode != 200 {
		t.Fatalf("CONNECT: %v %v", response, err)
	}
	conn.Write([]byte("TLSBYTES"))
	received, err := io.Copy(io.Discard, reader)
	if err != nil || received != total {
		t.Fatalf("stream truncated: %d/%d %v", received, total, err)
	}
	if actual := <-upstreamRead; actual != "TLSBYTES" {
		t.Fatalf("authentication or HTTP headers leaked upstream: %q", actual)
	}
}

func TestGithubTunnelCallerDisconnectCancelsDNS(t *testing.T) {
	server := newGithubTunnelServer(githubTestToken, newResolver("", time.Second), time.Second)
	defer server.close()
	started := make(chan struct{})
	cancelled := make(chan struct{})
	server.addresses = func(ctx context.Context, _ string) ([]string, error) {
		close(started)
		<-ctx.Done()
		close(cancelled)
		return nil, ctx.Err()
	}
	conn, _ := connectGithubTest(t, server)
	<-started
	conn.Close()
	select {
	case <-cancelled:
	case <-time.After(300 * time.Millisecond):
		t.Fatal("cancelled caller left DNS running")
	}
}

func TestGithubHelperIdleExitDoesNotInterruptActiveTunnel(t *testing.T) {
	server := newGithubTunnelServer(githubTestToken, newResolver("", time.Second), time.Second)
	defer server.close()
	if !server.idle(time.Now().Add(2*time.Minute), time.Minute) {
		t.Fatal("unused helper did not become idle")
	}
	a, b := net.Pipe()
	defer b.Close()
	server.track(a)
	if server.idle(time.Now().Add(2*time.Minute), time.Minute) {
		t.Fatal("active tunnel counted as idle")
	}
	server.untrack(a)
	if server.idle(time.Now(), time.Minute) {
		t.Fatal("last use not refreshed")
	}
}

func TestGithubRetirementWaitsForExistingTunnel(t *testing.T) {
	server := newGithubTunnelServer(githubTestToken, newResolver("", time.Second), time.Second)
	defer server.close()
	a, b := net.Pipe()
	defer b.Close()
	if !server.drainingIdle() {
		t.Fatal("new helper cannot retire")
	}
	server.track(a)
	if server.drainingIdle() {
		t.Fatal("retirement would interrupt a download")
	}
	received := make(chan byte, 1)
	go func() { data := make([]byte, 1); b.Read(data); received <- data[0] }()
	if _, err := a.Write([]byte{42}); err != nil {
		t.Fatal(err)
	}
	if <-received != 42 {
		t.Fatal("retiring tunnel lost data")
	}
	server.untrack(a)
	if !server.drainingIdle() {
		t.Fatal("finished generation never retires")
	}
}

func TestGithubDrainWaitIsNotifiedByTheLastDisconnect(t *testing.T) {
	server := newGithubTunnelServer(githubTestToken, newResolver("", time.Second), time.Second)
	defer server.close()
	a, b := net.Pipe()
	defer b.Close()
	server.track(a)
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	defer cancel()
	done := make(chan struct{})
	go func() { server.waitDrained(ctx); close(done) }()
	select {
	case <-done:
		t.Fatal("drained a live tunnel")
	default:
	}
	server.untrack(a)
	select {
	case <-done:
	case <-ctx.Done():
		t.Fatal("disconnect did not notify retirement")
	}
}
