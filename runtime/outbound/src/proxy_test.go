package main

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func proxyFixture(t *testing.T, fn roundTripFunc) (*httptest.Server, string) {
	t.Helper()
	token := "app-test-token-01234567890123456789"
	transport := newOutboundTransport(newResolver("", time.Second), "h2", time.Second)
	transport.resolve = func(context.Context, string) (targetRecords, error) { return targetRecords{ech: []byte{1}}, nil }
	transport.dial = func(context.Context, string, targetRecords, string) (*session, error) {
		s := stubSession("h2")
		s.rt = fn
		return s, nil
	}
	server := httptest.NewServer(helperHandler(token, transport))
	t.Cleanup(func() { server.Close(); transport.close() })
	return server, token
}
func proxyRequest(t *testing.T, endpoint, token, target string) *http.Request {
	t.Helper()
	r, _ := http.NewRequest("POST", endpoint+"/proxy", strings.NewReader("business-body"))
	r.Header.Set("X-Danmu-Outbound-Token", token)
	r.Header.Set("X-Danmu-Outbound-Target", target)
	r.Header.Set("X-Danmu-Outbound-Timeout", "1000")
	return r
}
func TestAppProxyPreservesBytesCookiesAndRedirects(t *testing.T) {
	calls := 0
	server, token := proxyFixture(t, func(r *http.Request) (*http.Response, error) {
		calls++
		body, _ := io.ReadAll(r.Body)
		if string(body) != "business-body" || r.Method != "POST" {
			t.Error("body/method changed")
		}
		if r.Header.Get("X-Danmu-Outbound-Token") != "" || r.Header.Get("X-Remove") != "" {
			t.Error("internal/hop headers leaked")
		}
		if r.Header.Get("Authorization") != "business-auth" {
			t.Error("business auth lost")
		}
		return &http.Response{StatusCode: 307, Header: http.Header{"Location": {"https://api.tmdb.org/next"}, "Set-Cookie": {"a=1", "b=2"}, "Content-Encoding": {"gzip"}}, Body: io.NopCloser(strings.NewReader("encoded-bytes"))}, nil
	})
	request := proxyRequest(t, server.URL, token, "https://api.gamer.com.tw/test")
	request.Header.Set("Authorization", "business-auth")
	request.Header.Set("Connection", "X-Remove")
	request.Header.Set("X-Remove", "secret")
	client := server.Client()
	client.CheckRedirect = func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }
	client.Transport = &http.Transport{DisableCompression: true}
	response, err := client.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	data, _ := io.ReadAll(response.Body)
	if calls != 1 || response.StatusCode != 307 || len(response.Header.Values("Set-Cookie")) != 2 || string(data) != "encoded-bytes" || response.Header.Get("Content-Encoding") != "gzip" {
		t.Fatalf("response changed: %#v %s calls=%d", response, string(data), calls)
	}
}
func TestAppProxyRejectsUnauthenticatedAndUnlistedTargets(t *testing.T) {
	server, token := proxyFixture(t, func(*http.Request) (*http.Response, error) {
		t.Fatal("upstream must not be contacted")
		return nil, nil
	})
	for _, test := range []struct {
		token, target string
		status        int
	}{{"bad", "https://api.tmdb.org/x", 401}, {token, "https://example.com/x", 403}, {token, "https://api.gamer.com.tw:444/x", 403}} {
		response, err := server.Client().Do(proxyRequest(t, server.URL, test.token, test.target))
		if err != nil {
			t.Fatal(err)
		}
		response.Body.Close()
		if response.StatusCode != test.status || response.Header.Get("X-Danmu-Outbound-Error") != "1" {
			t.Fatal("rejection not marked")
		}
	}
}
func TestAppProxyDisconnectCancelsUpstream(t *testing.T) {
	started, cancelled := make(chan struct{}), make(chan struct{})
	server, token := proxyFixture(t, func(r *http.Request) (*http.Response, error) {
		close(started)
		return &http.Response{StatusCode: 200, Header: make(http.Header), Body: cancelledBody{r.Context(), cancelled}}, nil
	})
	ctx, cancel := context.WithCancel(context.Background())
	req := proxyRequest(t, server.URL, token, "https://api.tmdb.org/x").WithContext(ctx)
	done := make(chan struct{})
	go func() {
		res, _ := server.Client().Do(req)
		if res != nil {
			res.Body.Close()
		}
		close(done)
	}()
	select {
	case <-started:
	case <-time.After(time.Second):
		t.Fatal("did not start")
	}
	cancel()
	select {
	case <-cancelled:
	case <-time.After(time.Second):
		t.Fatal("upstream was not cancelled")
	}
	<-done
}
