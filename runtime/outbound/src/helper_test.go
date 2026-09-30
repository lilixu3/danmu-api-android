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

type roundTripFunc func(*http.Request) (*http.Response, error)

func (fn roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) { return fn(req) }

type cancelledBody struct {
	ctx  context.Context
	done chan struct{}
}

func (body cancelledBody) Read([]byte) (int, error) {
	<-body.ctx.Done()
	close(body.done)
	return 0, body.ctx.Err()
}
func (body cancelledBody) Close() error { return nil }

func TestNodeDisconnectCancelsUpstreamBody(t *testing.T) {
	token := "helper-test-token-0123456789012345"
	started := make(chan struct{})
	cancelled := make(chan struct{})
	transport := newOutboundTransport(newResolver("", time.Second), "h2", time.Second)
	transport.resolve = func(context.Context, string) (targetRecords, error) { return targetRecords{}, nil }
	transport.dial = func(context.Context, string, targetRecords, string) (*session, error) {
		conn := stubSession("h2")
		conn.rt = roundTripFunc(func(req *http.Request) (*http.Response, error) {
			close(started)
			return &http.Response{StatusCode: 200, Header: make(http.Header), Body: cancelledBody{req.Context(), cancelled}}, nil
		})
		return conn, nil
	}
	server := httptest.NewServer(helperHandler(token, transport))
	defer server.Close()
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	request, _ := http.NewRequestWithContext(ctx, "POST", server.URL+"/request", strings.NewReader(`{"url":"https://api.tmdb.org/x","method":"GET","timeoutMs":10000}`))
	request.Header.Set("Authorization", "Bearer "+token)
	done := make(chan error, 1)
	go func() {
		response, err := server.Client().Do(request)
		if response != nil {
			io.Copy(io.Discard, response.Body)
			response.Body.Close()
		}
		done <- err
	}()
	select {
	case <-started:
	case <-time.After(time.Second):
		t.Fatal("upstream did not start")
	}
	cancel()
	select {
	case <-cancelled:
	case <-time.After(time.Second):
		t.Fatal("Node disconnect did not cancel upstream")
	}
	<-done
}
