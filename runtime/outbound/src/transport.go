package main

import (
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"sync"
	"sync/atomic"
	"time"

	"github.com/quic-go/quic-go"
	"github.com/quic-go/quic-go/http3"
	"golang.org/x/net/http2"
)

type phaseError struct {
	phase string
	err   error
}

func (e *phaseError) Error() string { return e.phase + ": " + e.err.Error() }
func (e *phaseError) Unwrap() error { return e.err }

type session struct {
	rt       http.RoundTripper
	close    func()
	alive    func() bool
	protocol string
	ech      bool
	used     time.Time
	reusable func() bool
	active   atomic.Int32
	retiring atomic.Bool
}

func (s *session) canReuse() bool {
	if s.reusable != nil {
		return s.reusable()
	}
	return s.alive()
}
func (s *session) release() {
	if s.active.Add(-1) == 0 && s.retiring.Load() {
		s.close()
	}
}

type sessionBody struct {
	io.ReadCloser
	session *session
	once    sync.Once
}

func (b *sessionBody) Close() error {
	err := b.ReadCloser.Close()
	b.once.Do(b.session.release)
	return err
}

type hostState struct {
	gate         chan struct{}
	session      *session
	failures     int
	cooldown     time.Time
	lastProtocol string
}
type outboundTransport struct {
	resolve func(context.Context, string) (targetRecords, error)
	dial    func(context.Context, string, targetRecords, string) (*session, error)

	resolver *dnsResolver
	version  string
	timeout  time.Duration
	mu       sync.Mutex
	hosts    map[string]*hostState
}

func newOutboundTransport(resolver *dnsResolver, version string, timeout time.Duration) *outboundTransport {
	return &outboundTransport{resolver: resolver, version: version, timeout: timeout, hosts: make(map[string]*hostState), dial: dialAddresses, resolve: resolver.resolve}
}
func isECHRejection(err error) bool {
	var rejection *tls.ECHRejectionError
	return errors.As(err, &rejection)
}
func tlsFor(host string, records targetRecords, alpn string) *tls.Config {
	minimum := uint16(tls.VersionTLS12)
	if alpn == "h3" || targetPolicies[host].requireECH {
		minimum = tls.VersionTLS13
	}
	config := &tls.Config{ServerName: host, MinVersion: minimum, NextProtos: []string{alpn}, EncryptedClientHelloConfigList: records.ech}

	return config
}
func verifyECH(host string, state tls.ConnectionState) error {
	if targetPolicies[host].requireECH && !state.ECHAccepted {
		return fmt.Errorf("TLS: %s connection did not accept ECH", host)
	}
	return nil
}
func dialH2(ctx context.Context, host, ip string, records targetRecords) (*session, error) {
	conn, err := (&tls.Dialer{NetDialer: &net.Dialer{KeepAlive: -1}, Config: tlsFor(host, records, "h2")}).DialContext(ctx, "tcp", net.JoinHostPort(ip, "443"))
	if err != nil {
		return nil, err
	}
	tlsConn := conn.(*tls.Conn)
	state := tlsConn.ConnectionState()
	if err = verifyECH(host, state); err != nil {
		conn.Close()
		return nil, err
	}
	if state.NegotiatedProtocol != "h2" {
		conn.Close()
		return nil, fmt.Errorf("TLS: target did not negotiate h2")
	}
	return newH2Session(conn, state.ECHAccepted)
}
func newH2Session(conn net.Conn, ech bool) (*session, error) {
	// Mobile connections may sit unused for hours. Do not periodically ping the
	// source; retain short bursts of reuse and release idle connections instead.
	client, err := (&http2.Transport{IdleConnTimeout: 60 * time.Second, StrictMaxConcurrentStreams: true}).NewClientConn(conn)
	if err != nil {
		conn.Close()
		return nil, err
	}
	return &session{
		rt:       client,
		close:    func() { client.Close() },
		alive:    func() bool { return !client.State().Closed },
		reusable: func() bool { state := client.State(); return !state.Closed && !state.Closing },
		protocol: "h2", ech: ech,
	}, nil
}

func dialH3(ctx context.Context, host, ip string, records targetRecords) (*session, error) {
	conn, err := quic.DialAddr(ctx, net.JoinHostPort(ip, "443"), tlsFor(host, records, "h3"), &quic.Config{HandshakeIdleTimeout: 3 * time.Second, MaxIdleTimeout: 60 * time.Second})
	if err != nil {
		return nil, err
	}
	state := conn.ConnectionState().TLS
	if err = verifyECH(host, state); err != nil {
		conn.CloseWithError(0, "ECH required")
		return nil, err
	}
	client := (&http3.Transport{MaxResponseHeaderBytes: 1 << 20}).NewClientConn(conn)
	return &session{rt: client, close: func() { conn.CloseWithError(0, "connection closed") }, alive: func() bool { return conn.Context().Err() == nil }, protocol: "h3", ech: state.ECHAccepted}, nil
}

type dialResult struct {
	connection *session
	err        error
	protocol   string
}

// Race handshakes, never HTTP requests. The loser cannot send a business body.
func dialAddresses(ctx context.Context, host string, records targetRecords, protocol string) (*session, error) {
	attempt, cancel := context.WithCancel(ctx)
	defer cancel()
	count := min(len(records.ips), 4)
	results := make(chan dialResult, count)
	for i, ip := range records.ips[:count] {
		go func(i int, ip string) {
			timer := time.NewTimer(time.Duration(i) * 150 * time.Millisecond)
			defer timer.Stop()
			select {
			case <-attempt.Done():
				results <- dialResult{err: attempt.Err()}
				return
			case <-timer.C:
			}
			var connection *session
			var err error
			if protocol == "h3" {
				connection, err = dialH3(attempt, host, ip, records)
			} else {
				connection, err = dialH2(attempt, host, ip, records)
			}
			results <- dialResult{connection: connection, err: err}
		}(i, ip)
	}
	var failures []error
	for i := 0; i < count; i++ {
		result := <-results
		if result.err == nil {
			cancel()
			go func(remaining int) {
				for j := 0; j < remaining; j++ {
					loser := <-results
					if loser.connection != nil {
						loser.connection.close()
					}
				}
			}(count - i - 1)
			return result.connection, nil
		}
		failures = append(failures, result.err)
	}
	return nil, errors.Join(failures...)
}
func (t *outboundTransport) connect(ctx context.Context, host string, records targetRecords, state *hostState) (*session, error) {
	attempt, cancel := context.WithTimeout(ctx, t.timeout)
	defer cancel()
	if t.version == "h2" || (t.version == "auto" && (!records.h3 || time.Now().Before(state.cooldown))) {
		return t.dial(attempt, host, records, "h2")
	}
	if t.version == "h3" {
		return t.dial(attempt, host, records, "h3")
	}
	results := make(chan dialResult, 2)
	preferred := "h3"
	if state.lastProtocol == "h2" && state.cooldown.IsZero() {
		preferred = "h2"
	}
	for _, protocol := range []string{"h3", "h2"} {
		go func(protocol string) {
			if protocol != preferred {
				timer := time.NewTimer(200 * time.Millisecond)
				defer timer.Stop()
				select {
				case <-attempt.Done():
					results <- dialResult{err: attempt.Err(), protocol: protocol}
					return
				case <-timer.C:
				}
			}
			c, e := t.dial(attempt, host, records, protocol)
			results <- dialResult{c, e, protocol}
		}(protocol)
	}
	var failures []error
	h3Failed := false
	for i := 0; i < 2; i++ {
		result := <-results
		if result.err == nil {
			if result.protocol == "h3" {
				state.failures = 0
				state.cooldown = time.Time{}
			} else if !h3Failed {
				// A UDP probe that loses to H2 is also unsuccessful; otherwise a
				// blocked UDP path would be re-probed on every new connection.
				state.failures++
				if state.failures >= 2 {
					state.cooldown = time.Now().Add(5 * time.Minute)
				}
			}
			cancel()
			go func(remaining int) {
				for j := 0; j < remaining; j++ {
					loser := <-results
					if loser.connection != nil {
						loser.connection.close()
					}
				}
			}(1 - i)
			return result.connection, nil
		}
		failures = append(failures, result.err)
		if result.protocol == "h3" && ctx.Err() == nil {
			h3Failed = true
			state.failures++
			if state.failures >= 2 {
				state.cooldown = time.Now().Add(5 * time.Minute)
			}
		}
	}
	return nil, errors.Join(failures...)
}
func (t *outboundTransport) getSession(ctx context.Context, host string) (*session, error) {
	t.mu.Lock()
	state := t.hosts[host]
	if state == nil {
		state = &hostState{gate: make(chan struct{}, 1)}
		t.hosts[host] = state
	}
	t.mu.Unlock()
	select {
	case state.gate <- struct{}{}:
	case <-ctx.Done():
		return nil, ctx.Err()
	}
	defer func() { <-state.gate }()
	if state.session != nil && state.session.alive() && state.session.canReuse() && (time.Since(state.session.used) < time.Minute || state.session.active.Load() > 0) {
		state.session.used = time.Now()
		return state.session, nil
	}
	if state.session != nil {
		previous := state.session
		previous.retiring.Store(true)
		// GOAWAY/idle retirement lets existing streams finish. Capacity alone
		// never retires a connection; ClientConn.RoundTrip queues at the limit.
		if previous.active.Load() == 0 {
			previous.close()
		}
		state.session = nil
	}
	records, err := t.resolve(ctx, host)
	if err != nil {
		return nil, &phaseError{phase: "DNS", err: err}
	}
	for refresh := 0; refresh < 2; refresh++ {
		if targetPolicies[host].requireECH && len(records.ech) == 0 {
			return nil, &phaseError{phase: "ECH configuration", err: fmt.Errorf("configuration required for %s", host)}
		}
		connection, err := t.connect(ctx, host, records, state)
		if err == nil {
			connection.used = time.Now()
			state.session = connection
			state.lastProtocol = connection.protocol
			return connection, nil
		}
		if !isECHRejection(err) || !targetPolicies[host].requireECH || refresh == 1 {
			phase := "TLS/QUIC handshake"
			if isECHRejection(err) {
				phase = "ECH negotiation"
			}
			return nil, &phaseError{phase: phase, err: err}
		}
		t.resolver.invalidate(host)
		t.resolver.invalidateHTTPSChain(host)
		if targetPolicies[host].sharedECH {
			t.resolver.invalidateHTTPSChain("cloudflare-ech.com")
		}
		records, err = t.resolve(ctx, host)
		if err != nil {
			return nil, &phaseError{phase: "DNS refresh", err: err}
		}
	}
	return nil, fmt.Errorf("ECH retry exhausted")
}
func (t *outboundTransport) roundTrip(req *http.Request) (*http.Response, *session, error) {
	connection, err := t.getSession(req.Context(), req.URL.Hostname())
	if err != nil {
		return nil, nil, err
	}
	connection.active.Add(1)
	response, err := connection.rt.RoundTrip(req)
	if err != nil {
		connection.release()
	} else if response.Body != nil {
		response.Body = &sessionBody{ReadCloser: response.Body, session: connection}
	} else {
		connection.release()
	}
	// A stream cancellation/reset must not close the shared H2/H3 connection
	// and interrupt unrelated concurrent requests. Only retire dead sessions.
	if err != nil && req.Context().Err() == nil && !connection.alive() {
		t.mu.Lock()
		state := t.hosts[req.URL.Hostname()]
		t.mu.Unlock()
		if state != nil {
			// Do not delay a cancelled caller behind another DNS/handshake.
			select {
			case state.gate <- struct{}{}:
				if state.session == connection {
					connection.close()
					state.session = nil
				}
				<-state.gate
			case <-req.Context().Done():
			}
		}
	}
	// Never replay here: after RoundTrip starts, a server may have received a POST.
	return response, connection, err
}
func (t *outboundTransport) close() {
	t.mu.Lock()
	defer t.mu.Unlock()
	for _, state := range t.hosts {
		state.gate <- struct{}{}
		if state.session != nil {
			state.session.close()
		}
		<-state.gate
	}
	t.resolver.close()
}
