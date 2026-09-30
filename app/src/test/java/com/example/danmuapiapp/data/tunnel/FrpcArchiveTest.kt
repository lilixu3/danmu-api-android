package com.example.danmuapiapp.data.tunnel

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class FrpcArchiveTest {
    private fun tar(content: ByteArray, size: Long = content.size.toLong(), type: Byte = '0'.code.toByte(), truncate: Boolean = false): ByteArray {
        val header = ByteArray(512)
        "frp_test/frpc".toByteArray().copyInto(header)
        (size.toString(8).padStart(11, '0') + "\u0000").toByteArray().copyInto(header, 124)
        for (i in 148..155) header[i] = 32
        header[156] = type
        val sum = header.sumOf { it.toInt() and 255 }
        (sum.toString(8).padStart(6, '0') + "\u0000 ").toByteArray().copyInto(header, 148)
        val raw = ByteArrayOutputStream().apply {
            write(header); write(content)
            if (!truncate) { write(ByteArray((512 - content.size % 512) % 512)); write(ByteArray(1024)) }
        }.toByteArray()
        return ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(raw) } }.toByteArray()
    }
    @Test fun `valid archive extracts exact bytes`() { assertArrayEquals("kernel".toByteArray(), extractFrpcFromTarGz(tar("kernel".toByteArray()))) }
    @Test fun `rejects incomplete huge overflowing and linked entries`() {
        assertNull(extractFrpcFromTarGz(tar("short".toByteArray(), size = 30, truncate = true)))
        assertNull(extractFrpcFromTarGz(tar(ByteArray(0), size = Long.MAX_VALUE)))
        assertNull(extractFrpcFromTarGz(tar(ByteArray(0), size = FRPC_MAX_KERNEL_BYTES + 1)))
        assertNull(extractFrpcFromTarGz(tar("target".toByteArray(), type = '2'.code.toByte())))
        val corrupt = tar("kernel".toByteArray()).also { it[it.size - 8] = (it[it.size - 8].toInt() xor 1).toByte() }
        assertNull(extractFrpcFromTarGz(corrupt))
    }
    @Test fun `cancellation interrupts extraction`() {
        try { extractFrpcArchive(ByteArrayInputStream(tar("kernel".toByteArray())), ByteArrayOutputStream()) { throw java.util.concurrent.CancellationException() }; fail("must cancel") }
        catch (_: java.util.concurrent.CancellationException) {}
    }
}
