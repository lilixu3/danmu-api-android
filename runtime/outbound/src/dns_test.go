package main

import (
	"context"
	"crypto/sha256"
	"crypto/tls"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/miekg/dns"
)

func TestDNSCacheUsesTTLAndRejectsMismatchedAnswers(t *testing.T) {
	var count atomic.Int32
	var ttl atomic.Uint32
	ttl.Store(60)
	var mismatch atomic.Bool
	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		count.Add(1)
		wire, _ := io.ReadAll(r.Body)
		query := new(dns.Msg)
		query.Unpack(wire)
		answer := new(dns.Msg)
		answer.SetReply(query)
		if mismatch.Load() {
			answer.Question[0].Name = "wrong.example."
		}
		answer.Answer = []dns.RR{&dns.A{Hdr: dns.RR_Header{Name: query.Question[0].Name, Rrtype: dns.TypeA, Class: dns.ClassINET, Ttl: ttl.Load()}, A: net.ParseIP("203.0.113.9")}}
		packed, _ := answer.Pack()
		w.Header().Set("Content-Type", "application/dns-message")
		w.Write(packed)
	}))
	defer server.Close()
	resolver := newResolver(server.URL, time.Second)
	parsed, _ := url.Parse(server.URL)
	key := fmt.Sprintf("%s|%x", parsed.Host, sha256.Sum256(nil))
	resolver.transports[key] = server.Client().Transport.(*http.Transport)
	for i := 0; i < 3; i++ {
		if _, err := resolver.query(context.Background(), "api.tmdb.org", dns.TypeA); err != nil {
			t.Fatal(err)
		}
	}
	if count.Load() != 1 {
		t.Fatal("TTL cache not reused")
	}
	resolver.invalidate("api.tmdb.org")
	ttl.Store(0)
	for i := 0; i < 2; i++ {
		if _, err := resolver.query(context.Background(), "api.tmdb.org", dns.TypeA); err != nil {
			t.Fatal(err)
		}
	}
	if count.Load() != 3 {
		t.Fatal("zero TTL was cached")
	}
	mismatch.Store(true)
	if _, err := resolver.query(context.Background(), "api.tmdb.org", dns.TypeA); err == nil {
		t.Fatal("mismatched DNS accepted")
	}
}
func TestHelperAuthenticationAndAllowlist(t *testing.T) {
	token := "test-token-012345678901234567890123"
	handler := helperHandler(token, newOutboundTransport(newResolver("", time.Second), "h2", time.Second))
	for _, tc := range []struct {
		auth, url string
		status    int
	}{{"", "https://api.tmdb.org/x", 401}, {token, "http://api.tmdb.org/x", 403}, {token, "https://api.tmdb.org.evil.test/x", 403}, {token, "https://api.tmdb.org:444/x", 403}} {
		request := httptest.NewRequest("POST", "/request", nil)
		request.Body = io.NopCloser(strings.NewReader(fmt.Sprintf(`{"url":%q,"method":"GET","timeoutMs":10}`, tc.url)))
		if tc.auth != "" {
			request.Header.Set("Authorization", "Bearer "+tc.auth)
		}
		response := httptest.NewRecorder()
		handler.ServeHTTP(response, request)
		if response.Code != tc.status {
			t.Fatalf("status=%d want=%d", response.Code, tc.status)
		}
	}
}

func TestHTTPSConfigOnlyComesFromTargetAliasChain(t *testing.T) {
	message := new(dns.Msg)
	message.SetQuestion("api.gamer.com.tw.", dns.TypeHTTPS)
	record := func(name string, config []byte) *dns.HTTPS {
		return &dns.HTTPS{SVCB: dns.SVCB{Hdr: dns.RR_Header{Name: name, Rrtype: dns.TypeHTTPS, Class: dns.ClassINET, Ttl: 60}, Priority: 1, Target: ".", Value: []dns.SVCBKeyValue{&dns.SVCBECHConfig{ECH: config}}}}
	}
	message.Answer = []dns.RR{record("unrelated.test.", []byte{1})}
	if echFromAnswer(message) != nil {
		t.Fatal("accepted ECH for unrelated owner")
	}
	message.Answer = append(message.Answer, &dns.CNAME{Hdr: dns.RR_Header{Name: "api.gamer.com.tw.", Rrtype: dns.TypeCNAME, Class: dns.ClassINET, Ttl: 60}, Target: "alias.test."}, record("alias.test.", []byte{2}))
	if got := echFromAnswer(message); len(got) != 1 || got[0] != 2 {
		t.Fatal("did not follow authenticated alias chain")
	}
}

func TestHTTPSAliasesFollowAndRespectLoopBounds(t *testing.T) {
	for _, aliasMode := range []bool{false, true} {
		t.Run(map[bool]string{false: "CNAME", true: "AliasMode"}[aliasMode], func(t *testing.T) {
			var aliases atomic.Int32
			server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				wire, _ := io.ReadAll(r.Body)
				query := new(dns.Msg)
				query.Unpack(wire)
				answer := new(dns.Msg)
				answer.SetReply(query)
				owner := query.Question[0].Name
				if owner == "api.tmdb.org." {
					if aliasMode {
						answer.Answer = []dns.RR{&dns.HTTPS{SVCB: dns.SVCB{Hdr: dns.RR_Header{Name: owner, Rrtype: dns.TypeHTTPS, Class: dns.ClassINET, Ttl: 20}, Priority: 0, Target: "alias.test."}}}
					} else {
						answer.Answer = []dns.RR{&dns.CNAME{Hdr: dns.RR_Header{Name: owner, Rrtype: dns.TypeCNAME, Class: dns.ClassINET, Ttl: 20}, Target: "alias.test."}}
					}
				} else {
					aliases.Add(1)
					answer.Answer = []dns.RR{&dns.HTTPS{SVCB: dns.SVCB{Hdr: dns.RR_Header{Name: owner, Rrtype: dns.TypeHTTPS, Class: dns.ClassINET, Ttl: 60}, Priority: 1, Target: ".", Value: []dns.SVCBKeyValue{&dns.SVCBAlpn{Alpn: []string{"h3", "h2"}}, &dns.SVCBECHConfig{ECH: []byte{1, 2}}}}}}
				}
				packed, _ := answer.Pack()
				w.Write(packed)
			}))
			defer server.Close()
			resolver := newResolver(server.URL, time.Second)
			defer resolver.close()
			u, _ := url.Parse(server.URL)
			resolver.transports[fmt.Sprintf("%s|%x", u.Host, sha256.Sum256(nil))] = server.Client().Transport.(*http.Transport)
			for i := 0; i < 2; i++ {
				message, err := resolver.httpsAnswer(context.Background(), "api.tmdb.org")
				if err != nil {
					t.Fatal(err)
				}
				records := httpsRecords(message)
				if len(records) != 1 || len(echFromAnswer(message)) != 2 {
					t.Fatal("alias service record missing")
				}
			}
			if aliases.Load() != 1 {
				t.Fatalf("alias query TTL cache not reused: %d", aliases.Load())
			}
		})
	}
	resolver := newResolver("https://custom.test/dns-query", time.Second)
	resolver.wire = func(_ context.Context, host string, kind uint16, _ string, _ []byte) (*dns.Msg, error) {
		message := new(dns.Msg)
		message.SetQuestion(dns.Fqdn(host), kind)
		next := "two.test."
		if host == "two.test" {
			next = "one.test."
		}
		message.Answer = []dns.RR{&dns.CNAME{Hdr: dns.RR_Header{Name: dns.Fqdn(host), Rrtype: dns.TypeCNAME, Class: dns.ClassINET, Ttl: 60}, Target: next}}
		return message, nil
	}
	if _, err := resolver.httpsAnswer(context.Background(), "one.test"); err == nil || !strings.Contains(err.Error(), "cycle") {
		t.Fatalf("alias cycle accepted: %v", err)
	}
}
func TestCustomCloudflareDoHRefreshesECHOnce(t *testing.T) {
	for _, persistent := range []bool{false, true} {
		t.Run(map[bool]string{false: "rotates", true: "bounded failure"}[persistent], func(t *testing.T) {
			resolver := newResolver("https://cloudflare-dns.com/dns-query", time.Second)
			var bootstraps, targets int
			resolver.wire = func(_ context.Context, host string, kind uint16, _ string, ech []byte) (*dns.Msg, error) {
				message := new(dns.Msg)
				message.SetQuestion(dns.Fqdn(host), kind)
				if host == "cloudflare-ech.com" {
					bootstraps++
					message.Answer = []dns.RR{&dns.HTTPS{SVCB: dns.SVCB{Hdr: dns.RR_Header{Name: dns.Fqdn(host), Rrtype: dns.TypeHTTPS, Class: dns.ClassINET, Ttl: 60}, Priority: 1, Target: ".", Value: []dns.SVCBKeyValue{&dns.SVCBECHConfig{ECH: []byte{byte(bootstraps)}}}}}}
					return message, nil
				}
				targets++
				if len(ech) == 0 {
					t.Fatal("DoH downgraded to ordinary SNI")
				}
				if ech[0] == 1 || persistent {
					return nil, &tls.ECHRejectionError{RetryConfigList: []byte{2}}
				}
				return message, nil
			}
			_, err := resolver.query(context.Background(), "api.tmdb.org", dns.TypeA)
			if persistent && err == nil || !persistent && err != nil {
				t.Fatalf("unexpected result: %v", err)
			}
			if targets != 2 || bootstraps != 2 {
				t.Fatalf("ECH refresh not bounded: targets=%d bootstraps=%d", targets, bootstraps)
			}
		})
	}
}

func TestBusinessECHRotationInvalidatesHTTPSAliasCache(t *testing.T) {
	var key atomic.Uint32
	key.Store(1)
	var aliasQueries atomic.Int32
	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		wire, _ := io.ReadAll(r.Body)
		query := new(dns.Msg)
		query.Unpack(wire)
		answer := new(dns.Msg)
		answer.SetReply(query)
		name := query.Question[0].Name
		switch query.Question[0].Qtype {
		case dns.TypeA:
			answer.Answer = []dns.RR{&dns.A{Hdr: dns.RR_Header{Name: name, Rrtype: dns.TypeA, Class: dns.ClassINET, Ttl: 60}, A: net.ParseIP("203.0.113.9")}}
		case dns.TypeHTTPS:
			if strings.EqualFold(name, "api.gamer.com.tw.") {
				answer.Answer = []dns.RR{&dns.HTTPS{SVCB: dns.SVCB{Hdr: dns.RR_Header{Name: name, Rrtype: dns.TypeHTTPS, Class: dns.ClassINET, Ttl: 60}, Priority: 0, Target: "Alias.TEST."}}}
			} else {
				aliasQueries.Add(1)
				answer.Answer = []dns.RR{&dns.HTTPS{SVCB: dns.SVCB{Hdr: dns.RR_Header{Name: name, Rrtype: dns.TypeHTTPS, Class: dns.ClassINET, Ttl: 60}, Priority: 1, Target: ".", Value: []dns.SVCBKeyValue{&dns.SVCBECHConfig{ECH: []byte{byte(key.Load())}}}}}}
			}
		}
		packed, _ := answer.Pack()
		w.Write(packed)
	}))
	defer server.Close()
	resolver := newResolver(server.URL, time.Second)
	u, _ := url.Parse(server.URL)
	resolver.transports[fmt.Sprintf("%s|%x", u.Host, sha256.Sum256(nil))] = server.Client().Transport.(*http.Transport)
	transport := newOutboundTransport(resolver, "h2", time.Second)
	defer transport.close()
	var handshakes int
	transport.dial = func(_ context.Context, _ string, records targetRecords, _ string) (*session, error) {
		handshakes++
		if len(records.ech) != 1 {
			t.Fatal("missing target alias ECH")
		}
		if records.ech[0] == 1 {
			key.Store(2)
			return nil, &tls.ECHRejectionError{RetryConfigList: []byte{2}}
		}
		return stubSession("h2"), nil
	}
	if _, err := transport.getSession(context.Background(), "api.gamer.com.tw"); err != nil {
		t.Fatalf("rotation did not refresh cached alias: %v", err)
	}
	if handshakes != 2 || aliasQueries.Load() != 2 {
		t.Fatalf("alias refresh/retry count: handshakes=%d aliasQueries=%d", handshakes, aliasQueries.Load())
	}
}

func TestConcurrentDNSBootstrapIsCoalesced(t *testing.T) {
	var calls atomic.Int32
	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		time.Sleep(30 * time.Millisecond)
		raw, _ := io.ReadAll(r.Body)
		question := new(dns.Msg)
		question.Unpack(raw)
		answer := new(dns.Msg)
		answer.SetReply(question)
		answer.Answer = []dns.RR{&dns.A{Hdr: dns.RR_Header{Name: question.Question[0].Name, Rrtype: dns.TypeA, Class: dns.ClassINET, Ttl: 60}, A: net.ParseIP("203.0.113.8")}}
		packed, _ := answer.Pack()
		w.Write(packed)
	}))
	defer server.Close()
	resolver := newResolver(server.URL, time.Second)
	endpoint, _ := url.Parse(server.URL)
	key := fmt.Sprintf("%s|%x", endpoint.Host, sha256.Sum256(nil))
	resolver.transports[key] = server.Client().Transport.(*http.Transport)
	start := make(chan struct{})
	done := make(chan error, 24)
	for i := 0; i < 24; i++ {
		go func() {
			<-start
			_, err := resolver.query(context.Background(), "cloudflare-ech.com", dns.TypeA)
			done <- err
		}()
	}
	close(start)
	for i := 0; i < 24; i++ {
		if err := <-done; err != nil {
			t.Fatal(err)
		}
	}
	if calls.Load() != 1 {
		t.Fatalf("cold parallel bootstrap sent %d duplicate queries", calls.Load())
	}
}

func TestCancelledDNSLeaderDoesNotCancelFollowers(t *testing.T) {
	var calls atomic.Int32
	entered := make(chan struct{})
	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if calls.Add(1) == 1 {
			io.Copy(io.Discard, r.Body)
			close(entered)
			select {
			case <-r.Context().Done():
			case <-time.After(time.Second):
			}
			return
		}
		raw, _ := io.ReadAll(r.Body)
		question := new(dns.Msg)
		question.Unpack(raw)
		answer := new(dns.Msg)
		answer.SetReply(question)
		answer.Answer = []dns.RR{&dns.A{Hdr: dns.RR_Header{Name: question.Question[0].Name, Rrtype: dns.TypeA, Class: dns.ClassINET, Ttl: 60}, A: net.ParseIP("203.0.113.8")}}
		packed, _ := answer.Pack()
		w.Write(packed)
	}))
	defer server.Close()
	resolver := newResolver(server.URL, time.Second)
	endpoint, _ := url.Parse(server.URL)
	key := fmt.Sprintf("%s|%x", endpoint.Host, sha256.Sum256(nil))
	resolver.transports[key] = server.Client().Transport.(*http.Transport)
	leader, cancel := context.WithCancel(context.Background())
	leaderDone := make(chan error, 1)
	go func() { _, err := resolver.query(leader, "api.animeko.org", dns.TypeA); leaderDone <- err }()
	<-entered
	follower, stop := context.WithTimeout(context.Background(), time.Second)
	defer stop()
	followerDone := make(chan error, 1)
	go func() { _, err := resolver.query(follower, "api.animeko.org", dns.TypeA); followerDone <- err }()
	time.Sleep(20 * time.Millisecond)
	cancel()
	if err := <-leaderDone; err == nil {
		t.Fatal("leader did not cancel")
	}
	if err := <-followerDone; err != nil {
		t.Fatalf("follower inherited leader cancellation: %v", err)
	}
}

func TestBuiltinFallbackCanWinWhileECHBootstrapIsBlocked(t *testing.T) {
	resolver := newResolver("", 3*time.Second)
	resolver.wire = func(ctx context.Context, host string, kind uint16, endpoint string, ech []byte) (*dns.Msg, error) {
		if endpoint == "https://dns.google/dns-query" {
			message := new(dns.Msg)
			message.SetQuestion(dns.Fqdn(host), kind)
			return message, nil
		}
		<-ctx.Done()
		return nil, ctx.Err()
	}
	ctx, cancel := context.WithTimeout(context.Background(), 400*time.Millisecond)
	defer cancel()
	start := time.Now()
	if _, err := resolver.query(ctx, "api.github.com", dns.TypeA); err != nil {
		t.Fatal(err)
	}
	if time.Since(start) > 350*time.Millisecond {
		t.Fatal("fallback did not retain its budget")
	}
}
