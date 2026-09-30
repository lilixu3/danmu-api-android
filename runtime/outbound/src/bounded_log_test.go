package main

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"
)

func TestBoundedLogRetainsTailAndExitsAtEOF(t *testing.T) {
	name := filepath.Join(t.TempDir(), "frpc.log")
	input := append(bytes.Repeat([]byte("x"), 5*logLimit), []byte("LATEST\n")...)
	if err := boundedLog(bytes.NewReader(input), name); err != nil {
		t.Fatal(err)
	}
	data, err := os.ReadFile(name)
	if err != nil || len(data) > logLimit+8192 || !bytes.HasSuffix(data, []byte("LATEST\n")) {
		t.Fatalf("invalid retained log: size=%d err=%v", len(data), err)
	}
	old, _ := os.Stat(name)
	if err := boundedLog(bytes.NewReader(bytes.Repeat([]byte("z"), 2*logLimit)), name); err != nil {
		t.Fatal(err)
	}
	current, _ := os.Stat(name)
	if !os.SameFile(old, current) {
		t.Fatal("rotation replaced the inode")
	}
}
