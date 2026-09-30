package main

import (
	"bytes"
	"context"
	"crypto/subtle"
	"encoding/base64"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"runtime"
	"strings"
	"syscall"
	"time"
)

const maxBody = 32 << 20

type helperRequest struct {
	URL       string      `json:"url"`
	Method    string      `json:"method"`
	Headers   [][2]string `json:"headers"`
	Body      string      `json:"body"`
	TimeoutMS int64       `json:"timeoutMs"`
}
type helperResponse struct {
	Status   int         `json:"status,omitempty"`
	Headers  [][2]string `json:"headers,omitempty"`
	Body     string      `json:"body,omitempty"`
	Protocol string      `json:"protocol,omitempty"`
	ECH      bool        `json:"ech,omitempty"`
	Error    string      `json:"error,omitempty"`
}

func allowedTarget(u *url.URL) bool {
	if u.Scheme != "https" || u.User != nil || u.Port() != "" && u.Port() != "443" || u.Fragment != "" {
		return false
	}
	_, ok := targetPolicies[u.Hostname()]
	return ok
}
func respond(w http.ResponseWriter, status int, result helperResponse) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(result)
}
func helperHandler(token string, transport *outboundTransport) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, local *http.Request) {
		if local.URL.Path == "/proxy" {
			proxyHandler(token, transport).ServeHTTP(w, local)
			return
		}
		if local.URL.Path != "/request" || local.Method != "POST" {
			http.NotFound(w, local)
			return
		}
		supplied := strings.TrimPrefix(local.Header.Get("Authorization"), "Bearer ")
		if subtle.ConstantTimeCompare([]byte(supplied), []byte(token)) != 1 {
			respond(w, 401, helperResponse{Error: "unauthorized"})
			return
		}
		var input helperRequest
		decoder := json.NewDecoder(http.MaxBytesReader(w, local.Body, (maxBody*4/3)+(1<<20)))
		if err := decoder.Decode(&input); err != nil {
			respond(w, 400, helperResponse{Error: "invalid helper request"})
			return
		}
		u, err := url.Parse(input.URL)
		if err != nil || !allowedTarget(u) {
			respond(w, 403, helperResponse{Error: "target not allowed"})
			return
		}
		switch input.Method {
		case "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS":
		default:
			respond(w, 400, helperResponse{Error: "method not allowed"})
			return
		}
		if input.TimeoutMS <= 0 || input.TimeoutMS > 3600000 {
			respond(w, 400, helperResponse{Error: "invalid request deadline"})
			return
		}
		body, err := base64.StdEncoding.DecodeString(input.Body)
		if err != nil || len(body) > maxBody {
			respond(w, 400, helperResponse{Error: "invalid request body"})
			return
		}
		ctx, cancel := context.WithTimeout(local.Context(), time.Duration(input.TimeoutMS)*time.Millisecond)
		defer cancel()
		request, err := http.NewRequestWithContext(ctx, input.Method, u.String(), bytes.NewReader(body))
		if err != nil {
			respond(w, 400, helperResponse{Error: "invalid upstream request"})
			return
		}
		for _, header := range input.Headers {
			switch strings.ToLower(header[0]) {
			case "host", "connection", "proxy-connection", "proxy-authorization", "transfer-encoding", "content-length", "accept-encoding", "upgrade", "te", "trailer":
				continue
			}
			request.Header.Add(header[0], header[1])
		}
		start := time.Now()
		response, connection, err := transport.roundTrip(request)
		if err != nil {
			phase := "connection"
			var failure *phaseError
			if errors.As(err, &failure) {
				phase = failure.phase
			}
			if connection != nil {
				phase = "request"
			}
			if ctx.Err() != nil {
				phase = "timeout/cancel"
			}
			log.Printf("host=%s phase=%s failed", u.Hostname(), phase)
			respond(w, 502, helperResponse{Error: "enhanced outbound failed during " + phase})
			return
		}
		defer response.Body.Close()
		data, err := io.ReadAll(io.LimitReader(response.Body, maxBody+1))
		if err != nil || len(data) > maxBody {
			respond(w, 502, helperResponse{Error: "upstream response body failed or exceeds 32 MiB"})
			return
		}
		if ctx.Err() != nil {
			respond(w, 504, helperResponse{Error: "enhanced outbound response deadline exceeded"})
			return
		}
		headers := make([][2]string, 0)
		for name, values := range response.Header {
			switch strings.ToLower(name) {
			case "connection", "transfer-encoding", "content-length":
				continue
			}
			for _, value := range values {
				headers = append(headers, [2]string{name, value})
			}
		}
		log.Printf("host=%s protocol=%s ech=%t status=%d duration=%s", u.Hostname(), connection.protocol, connection.ech, response.StatusCode, time.Since(start).Round(time.Millisecond))
		respond(w, 200, helperResponse{Status: response.StatusCode, Headers: headers, Body: base64.StdEncoding.EncodeToString(data), Protocol: connection.protocol, ECH: connection.ech})
	})
}
func main() {
	if runtime.GOOS == "android" && os.Getenv("SSL_CERT_DIR") == "" {
		os.Setenv("SSL_CERT_DIR", "/apex/com.android.conscrypt/cacerts:/system/etc/security/cacerts")
	}
	version := flag.String("http-version", "auto", "auto, h2 or h3")
	timeoutMS := flag.Int("connect-timeout-ms", 3000, "connection budget in milliseconds")
	dohURL := flag.String("doh-url", "", "custom DNS-over-HTTPS endpoint")
	boundedLogPath := flag.String("bounded-log", "", "bounded local frpc log sink")
	githubProxy := flag.Bool("github-proxy", false, "App GitHub CONNECT proxy (independent of Node)")
	flag.Parse()
	if *boundedLogPath != "" {
		if err := boundedLog(os.Stdin, *boundedLogPath); err != nil {
			log.Fatal("local log sink failed")
		}
		return
	}
	if *version != "auto" && *version != "h2" && *version != "h3" || *timeoutMS <= 0 || *timeoutMS > 60000 {
		log.Fatal("invalid outbound configuration")
	}
	if *dohURL != "" {
		u, e := url.Parse(*dohURL)
		if e != nil || u.Scheme != "https" || u.Hostname() == "" || u.User != nil || u.Fragment != "" {
			log.Fatal("invalid HTTPS DoH endpoint")
		}
	}
	token := os.Getenv("DANMU_OUTBOUND_TOKEN")
	if len(token) < 32 {
		log.Fatal("missing helper authentication token")
	}
	timeout := time.Duration(*timeoutMS) * time.Millisecond
	transport := newOutboundTransport(newResolver(*dohURL, timeout), *version, timeout)
	listener, err := net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		log.Fatal("cannot bind loopback helper")
	}
	var handler http.Handler = helperHandler(token, transport)
	var githubServer *githubTunnelServer
	if *githubProxy {
		githubServer = newGithubTunnelServer(token, transport.resolver, timeout)
		handler = githubServer
	}
	server := &http.Server{Handler: handler, ReadHeaderTimeout: 5 * time.Second, IdleTimeout: 60 * time.Second, MaxHeaderBytes: 16384}
	fmt.Printf("{\"ready\":true,\"appProtocol\":1,\"url\":\"http://%s\"}\n", listener.Addr())
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	go func() {
		io.Copy(io.Discard, os.Stdin)
		if githubServer == nil {
			stop()
			return
		}
		// Event-driven retirement: no timer wakes while an old download drains.
		githubServer.waitDrained(ctx)
		stop()
	}()
	if githubServer != nil {
		go func() {
			ticker := time.NewTicker(10 * time.Second)
			defer ticker.Stop()
			for {
				select {
				case <-ctx.Done():
					return
				case now := <-ticker.C:
					if githubServer.idle(now, 60*time.Second) {
						stop()
						return
					}
				}
			}
		}()
	}
	go func() {
		<-ctx.Done()
		server.Close()
		if githubServer != nil {
			githubServer.close()
		}
		transport.close()
	}()
	if err = server.Serve(listener); err != nil && err != http.ErrServerClosed {
		log.Fatal("helper server failed")
	}
}
