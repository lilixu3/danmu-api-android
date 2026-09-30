package main

import (
	"bytes"
	"context"
	"crypto/subtle"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
)

const internalPrefix = "x-danmu-outbound-"

func hopHeaders(h http.Header) map[string]bool {
	skip := map[string]bool{"connection": true, "proxy-connection": true, "proxy-authorization": true, "keep-alive": true, "transfer-encoding": true, "upgrade": true, "te": true, "trailer": true}
	for _, value := range h.Values("Connection") {
		for _, name := range strings.Split(value, ",") {
			skip[strings.ToLower(strings.TrimSpace(name))] = true
		}
	}
	return skip
}
func proxyError(w http.ResponseWriter, code int, message string) {
	w.Header().Set("X-Danmu-Outbound-Error", "1")
	http.Error(w, message, code)
}

// App-owned HTTP adapter. It sends exactly one business request and never follows
// redirects; the caller retains fetch/ClientRequest redirect and cancellation semantics.
func proxyHandler(token string, transport *outboundTransport) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, local *http.Request) {
		if subtle.ConstantTimeCompare([]byte(local.Header.Get("X-Danmu-Outbound-Token")), []byte(token)) != 1 {
			proxyError(w, 401, "unauthorized")
			return
		}
		target, err := url.Parse(local.Header.Get("X-Danmu-Outbound-Target"))
		if err != nil || !allowedTarget(target) {
			proxyError(w, 403, "target not allowed")
			return
		}
		switch local.Method {
		case "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS":
		default:
			proxyError(w, 405, "method not allowed")
			return
		}
		budget, err := strconv.Atoi(local.Header.Get("X-Danmu-Outbound-Timeout"))
		if err != nil || budget < 1 || budget > 3600000 {
			proxyError(w, 400, "invalid deadline")
			return
		}
		ctx, cancel := context.WithTimeout(local.Context(), time.Duration(budget)*time.Millisecond)
		defer cancel()
		// Buffer the small source API body before sending it. Exceeding the limit or
		// cancellation cannot leave a partially sent non-idempotent upstream request.
		body, err := readProxyBody(w, local)
		if err != nil {
			proxyError(w, 413, "request body failed or exceeds 32 MiB")
			return
		}
		request, err := http.NewRequestWithContext(ctx, local.Method, target.String(), bytes.NewReader(body))
		if err != nil {
			proxyError(w, 400, "invalid upstream request")
			return
		}
		skip := hopHeaders(local.Header)
		for name, values := range local.Header {
			lower := strings.ToLower(name)
			if skip[lower] || strings.HasPrefix(lower, internalPrefix) || lower == "host" || lower == "content-length" {
				continue
			}
			for _, value := range values {
				request.Header.Add(name, value)
			}
		}
		start := time.Now()
		response, session, err := transport.roundTrip(request)
		if err != nil {
			log.Printf("host=%s proxy request failed", target.Hostname())
			proxyError(w, 502, "enhanced outbound connection failed")
			return
		}
		defer response.Body.Close()
		skip = hopHeaders(response.Header)
		for name, values := range response.Header {
			lower := strings.ToLower(name)
			if skip[lower] || strings.HasPrefix(lower, internalPrefix) {
				continue
			}
			for _, value := range values {
				w.Header().Add(name, value)
			}
		}
		w.WriteHeader(response.StatusCode)
		// Preserve encoding and stream bytes unchanged for node-fetch/native fetch.
		// The context stays alive until EOF. Downstream disconnect cancels upstream.
		if local.Method != "HEAD" {
			_, err = io.Copy(w, response.Body)
		}
		if err != nil {
			panic(http.ErrAbortHandler)
		}
		log.Printf("host=%s protocol=%s ech=%t status=%d duration=%s", target.Hostname(), session.protocol, session.ech, response.StatusCode, time.Since(start).Round(time.Millisecond))
	})
}
func readProxyBody(w http.ResponseWriter, r *http.Request) ([]byte, error) {
	if r.ContentLength > maxBody {
		return nil, fmt.Errorf("body too large")
	}
	return io.ReadAll(http.MaxBytesReader(w, r.Body, maxBody))
}
