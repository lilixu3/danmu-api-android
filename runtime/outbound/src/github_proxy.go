package main

import (
	"context"
	"crypto/subtle"
	"fmt"
	"io"
	"net"
	"net/http"
	"sync"
	"time"

	"github.com/miekg/dns"
)

// GitHub uses reliable DNS + an authenticated CONNECT tunnel. TLS, HTTP/2,
// redirects, streaming and cancellation stay with the Android HTTP client.
// This is deliberately separate from the source /proxy allowlist and ECH policy.
var githubHosts = map[string]bool{
	"github.com":                            true,
	"api.github.com":                        true,
	"raw.githubusercontent.com":             true,
	"codeload.github.com":                   true,
	"release-assets.githubusercontent.com":  true,
	"objects.githubusercontent.com":         true,
	"github-releases.githubusercontent.com": true,
}

type githubTunnelServer struct {
	token       string
	resolver    *dnsResolver
	timeout     time.Duration
	mu          sync.Mutex
	connections map[net.Conn]bool
	lastUsed    time.Time
	closed      bool
	changed     chan struct{}
	// Injectable for bounded, local-only transport tests.
	addresses func(context.Context, string) ([]string, error)
	dial      func(context.Context, string, string) (net.Conn, error)
}

func newGithubTunnelServer(token string, resolver *dnsResolver, timeout time.Duration) *githubTunnelServer {
	s := &githubTunnelServer{token: token, resolver: resolver, timeout: timeout, connections: make(map[net.Conn]bool), lastUsed: time.Now(), changed: make(chan struct{}, 1)}
	s.dial = (&net.Dialer{KeepAlive: -1}).DialContext
	return s
}

func (s *githubTunnelServer) drainingIdle() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return len(s.connections) == 0
}

func (s *githubTunnelServer) waitDrained(ctx context.Context) {
	for !s.drainingIdle() {
		select {
		case <-ctx.Done():
			return
		case <-s.changed:
		}
	}
}

func (s *githubTunnelServer) track(conn net.Conn) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed {
		conn.Close()
		return false
	}
	s.connections[conn] = true
	s.lastUsed = time.Now()
	return true
}
func (s *githubTunnelServer) untrack(conn net.Conn) {
	conn.Close()
	s.mu.Lock()
	delete(s.connections, conn)
	s.lastUsed = time.Now()
	s.mu.Unlock()
	select {
	case s.changed <- struct{}{}:
	default:
	}
}
func (s *githubTunnelServer) idle(now time.Time, timeout time.Duration) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return len(s.connections) == 0 && now.Sub(s.lastUsed) >= timeout
}
func (s *githubTunnelServer) close() {
	s.mu.Lock()
	s.closed = true
	for conn := range s.connections {
		conn.Close()
	}
	s.mu.Unlock()
	s.resolver.close()
}

// The deadline resets on traffic, not on a timer. Idle pooled tunnels do not
// keep the helper alive indefinitely or send background keep-alive packets.
type tunnelActivity struct {
	mu          sync.Mutex
	connections []net.Conn
}

func (a *tunnelActivity) touch() {
	a.mu.Lock()
	defer a.mu.Unlock()
	deadline := time.Now().Add(65 * time.Second)
	for _, conn := range a.connections {
		conn.SetDeadline(deadline)
	}
}
func (a *tunnelActivity) add(conn net.Conn) {
	a.mu.Lock()
	a.connections = append(a.connections, conn)
	a.mu.Unlock()
	a.touch()
}

type idleTunnelReader struct {
	reader   io.Reader
	activity *tunnelActivity
}

func (r idleTunnelReader) Read(p []byte) (int, error) {
	r.activity.touch()
	n, err := r.reader.Read(p)
	if n > 0 {
		r.activity.touch()
	}
	return n, err
}

func (s *githubTunnelServer) ServeHTTP(w http.ResponseWriter, request *http.Request) {
	if request.Method != "CONNECT" {
		http.Error(w, "CONNECT required", 405)
		return
	}
	expected := "Bearer " + s.token
	if subtle.ConstantTimeCompare([]byte(request.Header.Get("Proxy-Authorization")), []byte(expected)) != 1 {
		w.Header().Set("Proxy-Authenticate", "Bearer")
		http.Error(w, "proxy authentication required", 407)
		return
	}
	host, port, err := net.SplitHostPort(request.Host)
	if err != nil || port != "443" || !githubHosts[host] || request.URL.Host != request.Host {
		http.Error(w, "target not allowed", 403)
		return
	}
	// Hijack before DNS so that a cancelled caller closes the connection and
	// immediately cancels resolver/dial work instead of finishing in background.
	hijacker, ok := w.(http.Hijacker)
	if !ok {
		http.Error(w, "tunnel unavailable", 500)
		return
	}
	local, buffered, err := hijacker.Hijack()
	if err != nil {
		return
	}
	if !s.track(local) {
		return
	}
	defer s.untrack(local)
	ctx, cancel := context.WithTimeout(request.Context(), s.timeout)
	defer cancel()
	// CONNECT clients wait for 200 before sending TLS; one reader handles both
	// this waiting period and subsequently the upstream TLS stream.
	activity := &tunnelActivity{connections: []net.Conn{local}}
	reader, writer := io.Pipe()
	defer reader.Close()
	go func() {
		_, err := io.Copy(writer, idleTunnelReader{buffered, activity})
		writer.CloseWithError(err)
		cancel()
	}()
	var upstream net.Conn
	if s.addresses != nil {
		// Offline tests inject a complete address list.
		ips, err := s.addresses(ctx, host)
		if err == nil {
			upstream, err = s.dialList(ctx, ips)
		}
	} else {
		upstream, err = s.connect(ctx, host)
	}
	if upstream == nil {
		io.WriteString(local, "HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\nContent-Length: 0\r\n\r\n")
		return
	}
	activity.add(upstream)
	// DNS/dial timeout must not cap a multi-minute file download.
	defer upstream.Close()
	if _, err = io.WriteString(local, "HTTP/1.1 200 Connection Established\r\n\r\n"); err != nil {
		return
	}
	outboundDone := make(chan struct{})
	go func() { io.Copy(upstream, reader); upstream.Close(); close(outboundDone) }()
	io.Copy(local, idleTunnelReader{upstream, activity})
	local.Close()
	reader.Close()
	upstream.Close()
	<-outboundDone
	// Never log request paths, signed asset URLs or authentication headers.
}

func (s *githubTunnelServer) dialList(ctx context.Context, ips []string) (net.Conn, error) {
	var last error
	for index, ip := range ips {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		budget := s.timeout
		if index+1 < len(ips) {
			budget = min(budget, 900*time.Millisecond)
		}
		attempt, cancel := context.WithTimeout(ctx, budget)
		conn, err := s.dial(attempt, "tcp", net.JoinHostPort(ip, "443"))
		cancel()
		if err == nil {
			return conn, nil
		}
		last = err
	}
	return nil, fmt.Errorf("GitHub connection failed: %v", last)
}

func (s *githubTunnelServer) connect(ctx context.Context, host string) (net.Conn, error) {
	// Do not let an unusable IPv6 route hide working IPv4, or a slow AAAA
	// query delay an IPv4 connection. Close every losing connection.
	type result struct {
		conn net.Conn
		err  error
	}
	raceCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	results := make(chan result, 2)
	for _, kind := range []uint16{dns.TypeA, dns.TypeAAAA} {
		go func(kind uint16) {
			// Reserve at least one third of the CONNECT budget for TCP.
			dnsCtx, dnsCancel := context.WithTimeout(raceCtx, s.timeout*2/3)
			ips, err := s.resolver.addressRecords(dnsCtx, host, kind)
			dnsCancel()
			var conn net.Conn
			if err == nil {
				conn, err = s.dialList(raceCtx, ips)
			}
			results <- result{conn, err}
		}(kind)
	}
	var last error
	for consumed := 0; consumed < 2; consumed++ {
		select {
		case <-ctx.Done():
			cancel()
			go func(remaining int) {
				for range remaining {
					if losing := <-results; losing.conn != nil {
						losing.conn.Close()
					}
				}
			}(2 - consumed)
			return nil, ctx.Err()
		case attempt := <-results:
			if attempt.conn != nil {
				cancel()
				if consumed == 0 {
					go func() {
						if losing := <-results; losing.conn != nil {
							losing.conn.Close()
						}
					}()
				}
				return attempt.conn, nil
			}
			last = attempt.err
		}
	}
	return nil, fmt.Errorf("GitHub address families unavailable: %v", last)
}
