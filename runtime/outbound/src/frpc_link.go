package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"syscall"
	"time"
)

// The launch token binds these events to the process identity published by the
// controller. A live PID or an older log alone is never connection evidence.
type frpcLinkObserver struct {
	filename string
	instance string
	state    string
	pending  []byte
	dropping bool
}

func newFrpcLinkObserver(filename, instance string) (*frpcLinkObserver, error) {
	if filename == "" || instance == "" || len(instance) > 128 || strings.ContainsAny(instance, "\r\n") {
		return nil, fmt.Errorf("invalid frpc instance")
	}
	observer := &frpcLinkObserver{filename: filename, instance: instance, state: "Unknown"}
	return observer, observer.publish()
}

func frpcLinkEvent(line string) string {
	line = strings.ToLower(line)
	if strings.Contains(line, "local service") {
		return ""
	}
	switch {
	case strings.Contains(line, "start frpc service for config file"), strings.Contains(line, "[app] frpc process adopted"):
		return "Unknown"
	case strings.Contains(line, "start error") && strings.Contains(line, "already exists"):
		return "Conflict"
	case strings.Contains(line, "start proxy success"):
		return "Connected"
	case strings.Contains(line, "login to server success"), strings.Contains(line, "try to connect to server"):
		return "Connecting"
	case strings.Contains(line, "login to server failed"), strings.Contains(line, "heartbeat timeout"),
		strings.Contains(line, "connect to server error"), strings.Contains(line, "start error"),
		strings.Contains(line, "i/o timeout"), strings.Contains(line, "connection refused"):
		return "Error"
	}
	return ""
}

func (o *frpcLinkObserver) accept(data []byte) error {
	for len(data) > 0 {
		end := bytes.IndexByte(data, '\n')
		part := data
		if end >= 0 {
			part = data[:end]
		}
		if !o.dropping {
			if len(o.pending)+len(part) > 64*1024 {
				o.pending = nil
				o.dropping = true
			} else {
				o.pending = append(o.pending, part...)
			}
		}
		if end < 0 {
			return nil
		}
		if !o.dropping {
			if state := frpcLinkEvent(string(o.pending)); state != "" && state != o.state {
				o.state = state
				if err := o.publish(); err != nil {
					return err
				}
			}
		}
		o.pending = o.pending[:0]
		o.dropping = false
		data = data[end+1:]
	}
	return nil
}

func (o *frpcLinkObserver) publish() error {
	data, err := json.Marshal(struct {
		Instance string `json:"instanceId"`
		State    string `json:"state"`
		Updated  int64  `json:"updatedAt"`
	}{o.instance, o.state, time.Now().UnixMilli()})
	if err != nil {
		return err
	}
	dir := filepath.Dir(o.filename)
	file, err := os.CreateTemp(dir, ".frpc-link-*")
	if err != nil {
		return err
	}
	defer os.Remove(file.Name())
	defer file.Close()
	if _, err = file.Write(data); err != nil {
		return err
	}
	// Boot launches the sink as root, but the App must own its private snapshot.
	info, err := os.Stat(dir)
	if err != nil {
		return err
	}
	if owner, ok := info.Sys().(*syscall.Stat_t); ok && int(owner.Uid) != os.Geteuid() {
		if err = file.Chown(int(owner.Uid), int(owner.Gid)); err != nil {
			return err
		}
	}
	if err = file.Close(); err != nil {
		return err
	}
	return os.Rename(file.Name(), o.filename)
}
