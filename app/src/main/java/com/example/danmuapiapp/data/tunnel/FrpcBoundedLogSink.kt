package com.example.danmuapiapp.data.tunnel

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

/** Keeps the existing inode, bounds retained data, preserves split UTF-8, exits on EOF. */
internal fun pumpFrpcBoundedLog(input: InputStream, log: File, onText: (String) -> Unit) {
    RandomAccessFile(log, "rw").use { file ->
        fun rotate() {
            if (file.length() > 1024 * 1024) {
                val tail = ByteArray(256 * 1024)
                file.seek(file.length() - tail.size); file.readFully(tail)
                file.setLength(0); file.seek(0); file.write(tail)
            }
        }
        rotate()
        input.use { reader ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                val text = String(buffer, 0, count, Charsets.UTF_8)
                onText(text)
                file.seek(file.length()); file.write(buffer, 0, count); rotate()
            }
            onText("\n")
        }
    }
}
