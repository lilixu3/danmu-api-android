package main

import (
	"bytes"
	"context"
	"crypto/sha256"
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/miekg/dns"
)

type dnsEntry struct {
	message *dns.Msg
	expires time.Time
}
type dnsFlight struct {
	done    chan struct{}
	message *dns.Msg
	err     error
}
type dnsResolver struct {
	wire        func(context.Context, string, uint16, string, []byte) (*dns.Msg, error)
	custom      string
	timeout     time.Duration
	mu          sync.Mutex
	cache       map[string]dnsEntry
	transports  map[string]*http.Transport
	httpsChains map[string][]string
	flights     map[string]*dnsFlight
}
type targetRecords struct {
	ips []string
	ech []byte
	h3  bool
}

func newResolver(custom string, timeout time.Duration) *dnsResolver {
	resolver := &dnsResolver{custom: custom, timeout: timeout, cache: make(map[string]dnsEntry), transports: make(map[string]*http.Transport), httpsChains: make(map[string][]string), flights: make(map[string]*dnsFlight)}
	resolver.wire = resolver.exchange
	return resolver
}

// These are resolver infrastructure bootstrap addresses, never business/CDN IP snapshots.
// Certificate verification always uses the original HTTPS resolver hostname.
func bootstrapIPs(host string) []string {
	switch host {
	case "dns.alidns.com":
		return []string{"223.5.5.5", "223.6.6.6"}
	case "doh.pub":
		return []string{"1.12.12.12", "120.53.53.53"}
	case "cloudflare-dns.com":
		return []string{"104.16.248.249", "104.16.249.249"}
	case "dns.google":
		return []string{"8.8.8.8", "8.8.4.4"}
	}
	return nil
}

// Coalesce overlapping wire queries, including the common ECH bootstrap.
// Followers retain their own deadlines; a cancelled leader does not cancel them.
func (r *dnsResolver) exchange(ctx context.Context, host string, kind uint16, endpoint string, ech []byte) (*dns.Msg, error) {
	key := fmt.Sprintf("%s|%s|%d|%x", endpoint, host, kind, sha256.Sum256(ech))
	for {
		if err := ctx.Err(); err != nil {
			return nil, err
		}
		r.mu.Lock()
		if flight := r.flights[key]; flight != nil {
			r.mu.Unlock()
			select {
			case <-ctx.Done():
				return nil, ctx.Err()
			case <-flight.done:
				if (errors.Is(flight.err, context.Canceled) || errors.Is(flight.err, context.DeadlineExceeded)) && ctx.Err() == nil {
					continue
				}
				if flight.message != nil {
					return flight.message.Copy(), flight.err
				}
				return nil, flight.err
			}
		}
		flight := &dnsFlight{done: make(chan struct{})}
		r.flights[key] = flight
		r.mu.Unlock()
		message, err := r.exchangeOnce(ctx, host, kind, endpoint, ech)
		r.mu.Lock()
		flight.message, flight.err = message, err
		delete(r.flights, key)
		close(flight.done)
		r.mu.Unlock()
		return message, err
	}
}
func (r *dnsResolver) exchangeOnce(ctx context.Context, host string, kind uint16, endpoint string, ech []byte) (*dns.Msg, error) {
	key := fmt.Sprintf("%s|%s|%d|%x", endpoint, host, kind, sha256.Sum256(ech))
	r.mu.Lock()
	cached, found := r.cache[key]
	r.mu.Unlock()
	if found && time.Now().Before(cached.expires) {
		return cached.message.Copy(), nil
	}
	u, err := url.Parse(endpoint)
	if err != nil || u.Scheme != "https" || u.Hostname() == "" {
		return nil, fmt.Errorf("invalid DoH endpoint")
	}
	transportKey := fmt.Sprintf("%s|%x", u.Host, sha256.Sum256(ech))
	r.mu.Lock()
	tr := r.transports[transportKey]
	if tr == nil {
		tlsConfig := &tls.Config{ServerName: u.Hostname(), MinVersion: tls.VersionTLS13, EncryptedClientHelloConfigList: ech}
		tr = &http.Transport{Proxy: nil, ForceAttemptHTTP2: true, TLSClientConfig: tlsConfig, IdleConnTimeout: 60 * time.Second, MaxIdleConnsPerHost: 8, TLSHandshakeTimeout: r.timeout}
		ips := bootstrapIPs(u.Hostname())
		if len(ips) > 0 {
			tr.DialContext = func(ctx context.Context, network, address string) (net.Conn, error) {
				_, port, e := net.SplitHostPort(address)
				if e != nil {
					return nil, e
				}
				var last error
				for _, ip := range ips {
					attempt, cancel := context.WithTimeout(ctx, min(r.timeout, 900*time.Millisecond))
					conn, e := (&net.Dialer{}).DialContext(attempt, "tcp", net.JoinHostPort(ip, port))
					cancel()
					if e == nil {
						return conn, nil
					}
					last = e
				}
				return nil, last
			}
		}
		// ECH rotation creates a new transport; bound retained resolver generations.
		if len(r.transports) >= 12 {
			for k, old := range r.transports {
				old.CloseIdleConnections()
				delete(r.transports, k)
			}
		}
		r.transports[transportKey] = tr
	}
	r.mu.Unlock()
	query := new(dns.Msg)
	query.SetQuestion(dns.Fqdn(host), kind)
	wire, err := query.Pack()
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, "POST", endpoint, bytes.NewReader(wire))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/dns-message")
	req.Header.Set("Accept", "application/dns-message")
	client := &http.Client{Transport: tr, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	response, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode != 200 {
		return nil, fmt.Errorf("DoH returned %d", response.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(response.Body, 65536))
	if err != nil {
		return nil, err
	}
	answer := new(dns.Msg)
	if err = answer.Unpack(body); err != nil {
		return nil, fmt.Errorf("invalid DoH DNS message: %w", err)
	}
	if !answer.Response || answer.Id != query.Id || answer.Truncated || len(answer.Question) != 1 || !strings.EqualFold(answer.Question[0].Name, query.Question[0].Name) || answer.Question[0].Qtype != kind {
		return nil, fmt.Errorf("mismatched DoH DNS response")
	}
	if answer.Rcode != dns.RcodeSuccess {
		return nil, fmt.Errorf("DNS rcode %s", dns.RcodeToString[answer.Rcode])
	}
	ttl := uint32(15)
	if len(answer.Answer) > 0 {
		ttl = answer.Answer[0].Header().Ttl
		for _, rr := range answer.Answer {
			ttl = min(ttl, rr.Header().Ttl)
		}
	} else {
		for _, rr := range answer.Ns {
			if soa, ok := rr.(*dns.SOA); ok {
				ttl = min(soa.Hdr.Ttl, soa.Minttl)
			}
		}
	}
	r.mu.Lock()
	if len(r.cache) > 512 {
		for k, v := range r.cache {
			if time.Now().After(v.expires) {
				delete(r.cache, k)
			}
		}
	}
	if ttl > 0 {
		r.cache[key] = dnsEntry{answer.Copy(), time.Now().Add(time.Duration(ttl) * time.Second)}
	}
	r.mu.Unlock()
	return answer, nil
}
func httpsRecords(message *dns.Msg) []*dns.HTTPS {
	if message == nil || len(message.Question) != 1 {
		return nil
	}
	allowed := map[string]bool{strings.ToLower(message.Question[0].Name): true}
	for i := 0; i < 6; i++ {
		for _, rr := range message.Answer {
			switch rr := rr.(type) {
			case *dns.CNAME:
				if allowed[strings.ToLower(rr.Hdr.Name)] {
					allowed[strings.ToLower(rr.Target)] = true
				}
			case *dns.HTTPS:
				if rr.Priority == 0 && allowed[strings.ToLower(rr.Hdr.Name)] {
					allowed[strings.ToLower(rr.Target)] = true
				}
			}
		}
	}
	var records []*dns.HTTPS
	for _, rr := range message.Answer {
		if rr, ok := rr.(*dns.HTTPS); ok && rr.Priority > 0 && allowed[strings.ToLower(rr.Hdr.Name)] {
			records = append(records, rr)
		}
	}
	return records
}
func echFromAnswer(message *dns.Msg) []byte {
	for _, record := range httpsRecords(message) {
		for _, kv := range record.Value {
			if config, ok := kv.(*dns.SVCBECHConfig); ok && len(config.ECH) > 0 {
				return append([]byte(nil), config.ECH...)
			}
		}
	}
	return nil
}

func (r *dnsResolver) sharedECH(ctx context.Context, refresh bool) ([]byte, error) {
	if refresh {
		r.invalidateHTTPSChain("cloudflare-ech.com")
	}
	if r.custom != "" {
		u, _ := url.Parse(r.custom)
		if u.Hostname() != "cloudflare-dns.com" {
			answer, err := r.wire(ctx, "cloudflare-ech.com", dns.TypeHTTPS, r.custom, nil)
			if err != nil {
				return nil, err
			}
			if config := echFromAnswer(answer); len(config) > 0 {
				return config, nil
			}
			return nil, fmt.Errorf("custom DoH returned no usable Cloudflare ECH config")
		}
	}
	// Domestic resolvers are only used to bootstrap Cloudflare's public ECH key;
	// target business records come from the encrypted Cloudflare DoH path.
	for _, endpoint := range []string{"https://dns.alidns.com/dns-query", "https://doh.pub/dns-query"} {
		attempt, cancel := context.WithTimeout(ctx, min(r.timeout, 1000*time.Millisecond))
		answer, err := r.wire(attempt, "cloudflare-ech.com", dns.TypeHTTPS, endpoint, nil)
		cancel()
		if err == nil {
			if ech := echFromAnswer(answer); len(ech) > 0 {
				return ech, nil
			}
		}
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
	}
	return nil, fmt.Errorf("DNS bootstrap: no usable Cloudflare ECH config")
}

// Both built-in and custom Cloudflare DoH refresh a rejected ECH key once.
// Each attempt and refresh remains inside the caller's total context deadline.
func (r *dnsResolver) encryptedQuery(ctx context.Context, host string, kind uint16, endpoint string, budget time.Duration) (*dns.Msg, error) {
	ech, err := r.sharedECH(ctx, false)
	if err != nil {
		return nil, err
	}
	for retry := 0; retry < 2; retry++ {
		attempt, cancel := context.WithTimeout(ctx, budget)
		answer, err := r.wire(attempt, host, kind, endpoint, ech)
		cancel()
		if err == nil {
			return answer, nil
		}
		if !isECHRejection(err) || retry == 1 {
			return nil, err
		}
		ech, err = r.sharedECH(ctx, true)
		if err != nil {
			return nil, err
		}
	}
	return nil, fmt.Errorf("DoH ECH retry exhausted")
}
func (r *dnsResolver) query(ctx context.Context, host string, kind uint16) (*dns.Msg, error) {
	if r.custom != "" {
		u, _ := url.Parse(r.custom)
		if u.Hostname() == "cloudflare-dns.com" {
			return r.encryptedQuery(ctx, host, kind, r.custom, r.timeout)
		}
		return r.wire(ctx, host, kind, r.custom, nil)
	}
	// Independent paths race inside the caller's budget. An ECH bootstrap
	// blackhole must not consume the time reserved for usable fallback DNS.
	type result struct {
		answer *dns.Msg
		err    error
	}
	race, cancel := context.WithCancel(ctx)
	defer cancel()
	results := make(chan result, 3)
	for i, endpoint := range []string{"https://cloudflare-dns.com/dns-query", "https://dns.google/dns-query", "https://cloudflare-dns.com/dns-query"} {
		go func(i int, endpoint string) {
			if i > 0 {
				timer := time.NewTimer(150 * time.Millisecond)
				defer timer.Stop()
				select {
				case <-timer.C:
				case <-race.Done():
					results <- result{err: race.Err()}
					return
				}
			}
			var answer *dns.Msg
			var err error
			if i == 0 {
				answer, err = r.encryptedQuery(race, host, kind, endpoint, min(r.timeout, 1800*time.Millisecond))
			} else {
				attempt, stop := context.WithTimeout(race, min(r.timeout, 1200*time.Millisecond))
				answer, err = r.wire(attempt, host, kind, endpoint, nil)
				stop()
			}
			results <- result{answer, err}
		}(i, endpoint)
	}
	var failures []error
	for range 3 {
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		case item := <-results:
			if item.err == nil {
				return item.answer, nil
			}
			failures = append(failures, item.err)
		}
	}
	return nil, fmt.Errorf("DNS resolution failed: %w", errors.Join(failures...))
}
func (r *dnsResolver) httpsAnswer(ctx context.Context, host string) (*dns.Msg, error) {
	root := strings.ToLower(strings.TrimSuffix(host, "."))
	chain := []string{}
	defer func() {
		r.mu.Lock()
		r.httpsChains[root] = append([]string(nil), chain...)
		r.mu.Unlock()
	}()
	seen := make(map[string]bool)
	for hop := 0; hop < 6; hop++ {
		owner := strings.ToLower(dns.Fqdn(host))
		if seen[owner] {
			return nil, fmt.Errorf("DNS HTTPS alias cycle")
		}
		seen[owner] = true
		chain = append(chain, strings.TrimSuffix(owner, "."))
		answer, err := r.query(ctx, host, dns.TypeHTTPS)
		if err != nil {
			return nil, err
		}
		if len(httpsRecords(answer)) > 0 {
			return answer, nil
		}
		next := ""
		for _, rr := range answer.Answer {
			if !strings.EqualFold(rr.Header().Name, owner) {
				continue
			}
			switch rr := rr.(type) {
			case *dns.CNAME:
				next = rr.Target
			case *dns.HTTPS:
				if rr.Priority == 0 {
					next = rr.Target
				}
			}
		}
		if next == "" || next == "." {
			return answer, nil
		}
		host = strings.TrimSuffix(next, ".")
	}
	return nil, fmt.Errorf("DNS HTTPS alias chain too long")
}

func (r *dnsResolver) invalidate(host string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	for key, entry := range r.cache {
		if len(entry.message.Question) == 1 && strings.EqualFold(entry.message.Question[0].Name, dns.Fqdn(host)) {
			delete(r.cache, key)
		}
	}
}
func (r *dnsResolver) invalidateHTTPSChain(host string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	root := strings.ToLower(strings.TrimSuffix(host, "."))
	owners := map[string]bool{dns.Fqdn(root): true}
	for _, owner := range r.httpsChains[root] {
		owners[dns.Fqdn(owner)] = true
	}
	for key, entry := range r.cache {
		if len(entry.message.Question) == 1 && entry.message.Question[0].Qtype == dns.TypeHTTPS && owners[strings.ToLower(entry.message.Question[0].Name)] {
			delete(r.cache, key)
		}
	}
	delete(r.httpsChains, root)
}
func (r *dnsResolver) addressRecords(ctx context.Context, host string, kind uint16) ([]string, error) {
	for aliases := 0; aliases < 6; aliases++ {
		answer, err := r.query(ctx, host, kind)
		if err != nil {
			return nil, err
		}
		allowed := map[string]bool{strings.ToLower(dns.Fqdn(host)): true}
		next := ""
		for i := 0; i < 6; i++ {
			for _, rr := range answer.Answer {
				if cn, ok := rr.(*dns.CNAME); ok && allowed[strings.ToLower(cn.Hdr.Name)] {
					allowed[strings.ToLower(cn.Target)] = true
					next = strings.TrimSuffix(cn.Target, ".")
				}
			}
		}
		var ips []string
		for _, rr := range answer.Answer {
			if !allowed[strings.ToLower(rr.Header().Name)] {
				continue
			}
			var ip net.IP
			switch rr := rr.(type) {
			case *dns.A:
				ip = rr.A
			case *dns.AAAA:
				ip = rr.AAAA
			}
			if ip != nil && ip.IsGlobalUnicast() && !ip.IsPrivate() {
				ips = append(ips, ip.String())
			}
		}
		if len(ips) > 0 || next == "" {
			return ips, nil
		}
		host = next
	}
	return nil, fmt.Errorf("DNS CNAME chain too long")
}
func (r *dnsResolver) resolve(ctx context.Context, host string) (targetRecords, error) {
	type result struct {
		kind    uint16
		ips     []string
		message *dns.Msg
		err     error
	}
	results := make(chan result, 3)
	for _, kind := range []uint16{dns.TypeA, dns.TypeAAAA, dns.TypeHTTPS} {
		go func(kind uint16) {
			if kind == dns.TypeHTTPS {
				m, e := r.httpsAnswer(ctx, host)
				results <- result{kind: kind, message: m, err: e}
			} else {
				ips, e := r.addressRecords(ctx, host, kind)
				results <- result{kind: kind, ips: ips, err: e}
			}
		}(kind)
	}
	records := targetRecords{}
	var ipv6 []string
	var last error
	for i := 0; i < 3; i++ {
		select {
		case <-ctx.Done():
			return records, ctx.Err()
		case item := <-results:
			if item.err != nil {
				last = item.err
				continue
			}
			if item.kind == dns.TypeAAAA {
				ipv6 = item.ips
			} else if item.kind == dns.TypeA {
				records.ips = item.ips
			} else {
				records.ech = echFromAnswer(item.message)
				for _, https := range httpsRecords(item.message) {
					for _, kv := range https.Value {
						if alpn, ok := kv.(*dns.SVCBAlpn); ok {
							for _, p := range alpn.Alpn {
								if p == "h3" {
									records.h3 = true
								}
							}
						}
					}
				}
			}
		}
	}
	ipv4 := records.ips
	records.ips = nil
	for i := 0; i < max(len(ipv4), len(ipv6)); i++ {
		if i < len(ipv4) {
			records.ips = append(records.ips, ipv4[i])
		}
		if i < len(ipv6) {
			records.ips = append(records.ips, ipv6[i])
		}
	}
	if len(records.ips) == 0 {
		return records, fmt.Errorf("DNS: no public target address: %v", last)
	}
	if targetPolicies[host].sharedECH && len(records.ech) == 0 {
		var err error
		records.ech, err = r.sharedECH(ctx, false)
		if err != nil {
			return records, err
		}
	}
	// DNS/H2 targets (including TMDB) do not inherit another CDN's ECH key.
	// ECH-required targets other than Bahamut use only their own HTTPS records.
	if !targetPolicies[host].requireECH {
		records.ech = nil
	}
	return records, nil
}
func (r *dnsResolver) close() {
	r.mu.Lock()
	defer r.mu.Unlock()
	for _, tr := range r.transports {
		tr.CloseIdleConnections()
	}
}
