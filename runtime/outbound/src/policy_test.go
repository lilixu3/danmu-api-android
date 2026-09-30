package main

import (
	"context"
	"crypto/tls"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"sync/atomic"
	"testing"
	"time"

	"github.com/miekg/dns"
)

func TestNewTargetsKeepExactAllowlist(t *testing.T) {
	for host := range targetPolicies {
		target, _ := url.Parse("https://" + host + "/test")
		if !allowedTarget(target) {
			t.Fatalf("legitimate host rejected: %s", host)
		}
		for _, raw := range []string{"https://" + host + ".evil.test/test", "http://" + host + "/test", "https://" + host + ":444/test", "https://user:secret@" + host + "/test"} {
			target, _ = url.Parse(raw)
			if allowedTarget(target) {
				t.Fatalf("invalid target allowed: %s", raw)
			}
		}
	}
}

func TestNewECHTargetsNeverDowngradeAndRefreshBeforeSending(t *testing.T) {
	for _, host := range []string{"api.danmaku.weeblify.app", "danmaku-global.myani.org", "api.bangumi.vip"} {
		t.Run(host, func(t *testing.T) {
			if verifyECH(host, tls.ConnectionState{}) == nil {
				t.Fatal("ordinary SNI accepted")
			}
			if verifyECH(host, tls.ConnectionState{ECHAccepted: true}) != nil {
				t.Fatal("ECH rejected")
			}
			config := tlsFor(host, targetRecords{ech: []byte{1}}, "h2")
			if config.ServerName != host || config.InsecureSkipVerify {
				t.Fatal("original hostname/certificate verification lost")
			}
			transport := newOutboundTransport(newResolver("", time.Second), "h2", time.Second)
			transport.resolve = func(context.Context, string) (targetRecords, error) { return targetRecords{}, nil }
			transport.dial = func(context.Context, string, targetRecords, string) (*session, error) {
				t.Fatal("missing ECH attempted a connection")
				return nil, nil
			}
			if _, err := transport.getSession(context.Background(), host); err == nil {
				t.Fatal("missing ECH accepted")
			}
			for _, persistent := range []bool{false, true} {
				transport = newOutboundTransport(newResolver("", time.Second), "h2", time.Second)
				var resolved, handshakes int
				var sends atomic.Int32
				transport.resolve = func(context.Context, string) (targetRecords, error) {
					resolved++
					return targetRecords{ech: []byte{byte(resolved)}}, nil
				}
				transport.dial = func(_ context.Context, _ string, records targetRecords, _ string) (*session, error) {
					handshakes++
					if records.ech[0] == 1 || persistent {
						return nil, &tls.ECHRejectionError{RetryConfigList: []byte{2}}
					}
					conn := stubSession("h2")
					conn.rt = countedRoundTripper{&sends}
					return conn, nil
				}
				request, _ := http.NewRequest("POST", "https://"+host+"/search", nil)
				_, _, err := transport.roundTrip(request)
				expected := int32(1)
				if persistent {
					expected = 0
				}
				if err == nil || resolved != 2 || handshakes != 2 || sends.Load() != expected {
					t.Fatalf("unbounded retry or replay: resolved=%d handshakes=%d sends=%d", resolved, handshakes, sends.Load())
				}
			}
		})
	}
}

func TestPerHostDNSDoesNotAssignSharedECH(t *testing.T) {
	for host, policy := range targetPolicies {
		if host == "api.gamer.com.tw" {
			continue
		}
		t.Run(host, func(t *testing.T) {
			for _, advertised := range []bool{false, true} {
				resolver := newResolver("https://resolver.test/dns-query", time.Second)
				resolver.wire = func(_ context.Context, asked string, kind uint16, _ string, _ []byte) (*dns.Msg, error) {
					if asked != host {
						return nil, fmt.Errorf("unexpected shared-key lookup for %s", asked)
					}
					message := new(dns.Msg)
					message.SetQuestion(dns.Fqdn(host), kind)
					switch kind {
					case dns.TypeA:
						message.Answer = []dns.RR{&dns.A{Hdr: dns.RR_Header{Name: dns.Fqdn(host), Rrtype: dns.TypeA, Class: dns.ClassINET, Ttl: 30}, A: net.ParseIP("203.0.113.5")}}
					case dns.TypeHTTPS:
						if advertised {
							message.Answer = []dns.RR{&dns.HTTPS{SVCB: dns.SVCB{Hdr: dns.RR_Header{Name: dns.Fqdn(host), Rrtype: dns.TypeHTTPS, Class: dns.ClassINET, Ttl: 30}, Priority: 1, Target: ".", Value: []dns.SVCBKeyValue{&dns.SVCBECHConfig{ECH: []byte{1, 2, 3}}}}}}
						}
					}
					return message, nil
				}
				records, err := resolver.resolve(context.Background(), host)
				if err != nil {
					t.Fatal(err)
				}
				if (len(records.ech) > 0) != (advertised && policy.requireECH) {
					t.Fatalf("ECH inherited or erased incorrectly: required=%t advertised=%t got=%x", policy.requireECH, advertised, records.ech)
				}
			}
		})
	}
}

func TestOrdinaryH2KeepsTLS12WhileECHAndQUICRequireTLS13(t *testing.T) {
	for _, host := range []string{"api.tmdb.org", "api.animeko.org", "nipaplay.aimes-soft.com"} {
		if config := tlsFor(host, targetRecords{}, "h2"); config.MinVersion != tls.VersionTLS12 || config.InsecureSkipVerify {
			t.Fatalf("ordinary H2 TLS policy changed for %s", host)
		}
		if tlsFor(host, targetRecords{}, "h3").MinVersion != tls.VersionTLS13 {
			t.Fatal("QUIC allowed TLS 1.2")
		}
	}
	if tlsFor("api.danmaku.weeblify.app", targetRecords{}, "h2").MinVersion != tls.VersionTLS13 {
		t.Fatal("ECH allowed TLS 1.2")
	}
}
