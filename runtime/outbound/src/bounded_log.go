package main

import (
	"fmt"
	"io"
	"os"
)

const logLimit = 1024 * 1024
const logKeep = 256 * 1024

// Event-driven log sink: no polling, no network, no whole-file read, EOF exits.
func boundedLog(input io.Reader, filename string, observers ...*frpcLinkObserver) error {
	file, err := os.OpenFile(filename, os.O_CREATE|os.O_RDWR|os.O_APPEND, 0600)
	if err != nil {
		return err
	}
	defer file.Close()
	info, err := file.Stat()
	if err != nil || !info.Mode().IsRegular() {
		return fmt.Errorf("invalid log file")
	}
	tail := make([]byte, logKeep)
	rotate := func() error {
		info, err := file.Stat()
		if err != nil {
			return err
		}
		if info.Size() <= logLimit {
			return nil
		}
		n, err := file.ReadAt(tail, info.Size()-logKeep)
		if err != nil && err != io.EOF {
			return err
		}
		// Retain the inode so app clear/read and the writer agree on the same file.
		if err = file.Truncate(0); err != nil {
			return err
		}
		_, err = file.Write(tail[:n])
		return err
	}
	if err = rotate(); err != nil {
		return err
	}
	buffer := make([]byte, 8192)
	for {
		n, readErr := input.Read(buffer)
		if n > 0 {
			for _, observer := range observers {
				if err := observer.accept(buffer[:n]); err != nil {
					return err
				}
			}
			if _, err = file.Write(buffer[:n]); err != nil {
				return err
			}
			if err = rotate(); err != nil {
				return err
			}
		}
		if readErr == io.EOF {
			for _, observer := range observers {
				if err := observer.accept([]byte("\n")); err != nil {
					return err
				}
			}
			return nil
		}
		if readErr != nil {
			return readErr
		}
	}
}
