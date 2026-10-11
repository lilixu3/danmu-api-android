package main

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func readFrpcSnapshot(t *testing.T, path string) map[string]any {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	var snapshot map[string]any
	if err := json.Unmarshal(data, &snapshot); err != nil {
		t.Fatal(err)
	}
	return snapshot
}

func TestFrpcConnectionSurvivesLogRotationAndClear(t *testing.T) {
	dir := t.TempDir()
	snapshot, log := filepath.Join(dir, "link-state.json"), filepath.Join(dir, "frpc.log")
	observer, err := newFrpcLinkObserver(snapshot, "launch-A")
	if err != nil {
		t.Fatal(err)
	}
	noise := bytes.Repeat([]byte("connect to local service [127.0.0.1:9321] error: connection refused\n"), 50000)
	input := append([]byte("[I] start proxy success\n"), noise...)
	if err := boundedLog(bytes.NewReader(input), log, observer); err != nil {
		t.Fatal(err)
	}
	data, _ := os.ReadFile(log)
	if bytes.Contains(data, []byte("start proxy success")) {
		t.Fatal("fixture must rotate success away")
	}
	if err := os.WriteFile(log, nil, 0600); err != nil {
		t.Fatal(err)
	}
	state := readFrpcSnapshot(t, snapshot)
	if state["state"] != "Connected" || state["instanceId"] != "launch-A" {
		t.Fatal(state)
	}
	// A new launch must not inherit the previous process's registration.
	if _, err := newFrpcLinkObserver(snapshot, "launch-B"); err != nil {
		t.Fatal(err)
	}
	state = readFrpcSnapshot(t, snapshot)
	if state["state"] != "Unknown" || state["instanceId"] != "launch-B" {
		t.Fatal(state)
	}
}

func TestFrpcObserverHandlesSplitEventsReconnectionAndConflict(t *testing.T) {
	path := filepath.Join(t.TempDir(), "link-state.json")
	observer, err := newFrpcLinkObserver(path, "launch")
	if err != nil {
		t.Fatal(err)
	}
	feed := func(text, want string) {
		t.Helper()
		for _, b := range []byte(text) {
			if err := observer.accept([]byte{b}); err != nil {
				t.Fatal(err)
			}
		}
		if got := readFrpcSnapshot(t, path)["state"]; got != want {
			t.Fatalf("got %v want %s", got, want)
		}
	}
	feed("[I] start proxy success\n", "Connected")
	feed("[W] heartbeat timeout\n", "Error")
	feed("[I] try to connect to server...\n[I] login to server success\n", "Connecting")
	feed("[W] start error: proxy [api] already exists\n", "Conflict")
	feed("[I] start proxy success\n", "Connected")
	feed("[E] connect to local service error: connection refused\n", "Connected")
	feed("[I] start frpc service for config file [frpc.conf]\n", "Unknown")
}

func TestFrpcObserverBoundsLinesAndFlushesFinalEvent(t *testing.T) {
	path := filepath.Join(t.TempDir(), "link-state.json")
	observer, err := newFrpcLinkObserver(path, "launch")
	if err != nil {
		t.Fatal(err)
	}
	input := append(bytes.Repeat([]byte("x"), 100000), []byte("start proxy success\n")...)
	if err := observer.accept(input); err != nil {
		t.Fatal(err)
	}
	if got := readFrpcSnapshot(t, path)["state"]; got != "Unknown" {
		t.Fatal(got)
	}
	if err := boundedLog(bytes.NewBufferString("[I] start proxy success"), filepath.Join(filepath.Dir(path), "frpc.log"), observer); err != nil {
		t.Fatal(err)
	}
	if got := readFrpcSnapshot(t, path)["state"]; got != "Connected" {
		t.Fatal(got)
	}
}
