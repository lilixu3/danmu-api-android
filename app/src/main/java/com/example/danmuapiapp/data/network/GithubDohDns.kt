package com.example.danmuapiapp.data.network

import com.example.danmuapiapp.data.service.AppOutboundSettings
import okhttp3.Call
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Cancellation is owned by one download, including its outstanding DoH calls. */
internal class GithubDnsCancellation {
    private val cancelled = AtomicBoolean()
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    fun check() { if (cancelled.get()) throw IOException("Canceled") }
    fun track(call: Call) { calls.add(call); if (cancelled.get()) call.cancel() }
    fun untrack(call: Call) { calls.remove(call) }
    fun cancel() { cancelled.set(true); calls.forEach { it.cancel() } }
}

internal data class GithubDnsAnswer(val addresses: List<InetAddress>, val ttl: Long, val alias: String? = null)

/** Bounded DNS wire decoder. Only answers reachable through the queried CNAME chain count. */
internal object GithubDnsWire {
    fun query(host: String, kind: Int, id: Int): ByteArray {
        val labels = host.trimEnd('.').split('.')
        require(host.length <= 253 && labels.all { it.length in 1..63 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } })
        return ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeShort(id); out.writeShort(0x100); out.writeShort(1)
                repeat(3) { out.writeShort(0) }
                labels.forEach { out.writeByte(it.length); out.write(it.toByteArray(Charsets.US_ASCII)) }
                out.writeByte(0); out.writeShort(kind); out.writeShort(1)
            }
        }.toByteArray()
    }

    fun parse(data: ByteArray, query: ByteArray): GithubDnsAnswer {
        fun need(offset: Int, count: Int) { if (offset < 0 || count < 0 || offset > data.size - count) throw IOException("Truncated DNS response") }
        fun u16(offset: Int): Int { need(offset, 2); return ((data[offset].toInt() and 255) shl 8) or (data[offset + 1].toInt() and 255) }
        fun name(start: Int): Pair<String, Int> {
            var offset = start; var end = -1; var length = 0
            val seen = HashSet<Int>(); val labels = ArrayList<String>()
            repeat(128) {
                need(offset, 1); if (!seen.add(offset)) throw IOException("DNS compression cycle")
                val size = data[offset++].toInt() and 255
                if (size and 0xc0 == 0xc0) {
                    need(offset, 1); if (end < 0) end = offset + 1
                    offset = ((size and 63) shl 8) or (data[offset].toInt() and 255)
                } else {
                    if (size and 0xc0 != 0) throw IOException("Invalid DNS label")
                    if (size == 0) return labels.joinToString(".").lowercase() to (if (end < 0) offset else end)
                    need(offset, size); length += size + 1
                    if (length > 254) throw IOException("DNS name too long")
                    labels.add(String(data, offset, size, Charsets.US_ASCII)); offset += size
                }
            }
            throw IOException("DNS name too deep")
        }
        need(0, 12)
        val flags = u16(2)
        if (u16(0) != (((query[0].toInt() and 255) shl 8) or (query[1].toInt() and 255)) ||
            flags and 0x8000 == 0 || flags and 0x7a0f != 0 || u16(4) != 1) throw IOException("Invalid DNS response")
        val (host, questionEnd) = name(12)
        need(questionEnd, 4)
        if (!data.copyOfRange(12, questionEnd + 4).contentEquals(query.copyOfRange(12, query.size))) throw IOException("Mismatched DNS question")
        val kind = u16(questionEnd)
        data class Record(val owner: String, val target: String?, val address: InetAddress?, val ttl: Long)
        val records = ArrayList<Record>(); var offset = questionEnd + 4
        val count = u16(6)
        if (count > 2048) throw IOException("Too many DNS answers")
        repeat(count) {
            val (owner, next) = name(offset); offset = next; need(offset, 10)
            val type = u16(offset); val klass = u16(offset + 2)
            val ttl = ((u16(offset + 4).toLong() shl 16) or u16(offset + 6).toLong()).coerceAtMost(86400)
            val size = u16(offset + 8); offset += 10; need(offset, size)
            if (klass == 1) {
                val address = if (type == kind && ((type == 1 && size == 4) || (type == 28 && size == 16))) InetAddress.getByAddress(data.copyOfRange(offset, offset + size)) else null
                val target = if (type == 5) name(offset).also { if (it.second != offset + size) throw IOException("Invalid CNAME") }.first else null
                records.add(Record(owner, target, address, ttl))
            }
            offset += size
        }
        val allowed = hashSetOf(host)
        repeat(6) { records.forEach { if (it.owner in allowed) it.target?.let(allowed::add) } }
        val relevant = records.filter { it.owner in allowed }
        return GithubDnsAnswer(relevant.mapNotNull { it.address }.filter(::publicAddress), relevant.minOfOrNull { it.ttl } ?: 15,
            relevant.lastOrNull { it.target != null }?.target)
    }

    fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 255 }
        if (bytes.size == 16) return bytes[0] in 0x20..0x3f && !(bytes[0] == 0x20 && bytes[1] == 1 && bytes[2] == 0xd && bytes[3] == 0xb8)
        val a = bytes[0]; val b = bytes[1]; val c = bytes[2]
        return a in 1..223 && a != 10 && a != 127 && !(a == 100 && b in 64..127) &&
            !(a == 169 && b == 254) && !(a == 172 && b in 16..31) && !(a == 192 && b in listOf(0, 168)) &&
            !(a == 198 && b in listOf(18, 19, 51)) && !(a == 203 && b == 0 && c == 113)
    }
}

/** Android-native DoH. TLS, H2, redirects and body streaming remain in OkHttp. */
internal class GithubDohResolver(private val settings: AppOutboundSettings) {
    private data class Entry(val answer: GithubDnsAnswer, val expires: Long)
    private val cache = ConcurrentHashMap<String, Entry>()
    private val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
        .dns { host -> bootstrap[host]?.map(InetAddress::getByName) ?: Dns.SYSTEM.lookup(host) }
        .connectionPool(okhttp3.ConnectionPool(4, 60, TimeUnit.SECONDS)).build()

    private fun query(host: String, kind: Int, cancellation: GithubDnsCancellation, deadline: Long): GithubDnsAnswer {
        val key = "$host|$kind"
        cache[key]?.takeIf { it.expires > System.nanoTime() }?.let { return it.answer }
        val endpoints = settings.dohUrl.takeIf { it.isNotBlank() }?.let(::listOf) ?: listOf(
            "https://dns.alidns.com/dns-query", "https://doh.pub/dns-query", "https://dns.google/dns-query", "https://cloudflare-dns.com/dns-query"
        )
        val wire = GithubDnsWire.query(host, kind, random.nextInt(65536))
        var last: Exception? = null
        for (endpoint in endpoints) {
            cancellation.check()
            val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            if (remaining <= 0) break
            val request = Request.Builder().url(endpoint).header("Accept", "application/dns-message")
                .post(wire.toRequestBody("application/dns-message".toMediaType())).build()
            val call = client.newBuilder().callTimeout(if (settings.dohUrl.isBlank()) minOf(remaining, 900) else remaining, TimeUnit.MILLISECONDS).build().newCall(request)
            cancellation.track(call)
            try {
                val answer = call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("DoH HTTP failure")
                    val bytes = response.body.byteStream().use { input ->
                        val buffer = ByteArray(4096); val output = ByteArrayOutputStream()
                        while (output.size() < 65536) {
                            val count = input.read(buffer, 0, minOf(buffer.size, 65536 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    if (bytes.size > 65535) throw IOException("DoH response too large")
                    GithubDnsWire.parse(bytes, wire)
                }
                if (cache.size >= 256) cache.keys.firstOrNull()?.let(cache::remove)
                if (answer.ttl > 0) cache[key] = Entry(answer, System.nanoTime() + TimeUnit.SECONDS.toNanos(answer.ttl))
                return answer
            } catch (error: Exception) { last = error }
            finally { cancellation.untrack(call) }
        }
        throw UnknownHostException("GitHub DoH lookup failed").apply { initCause(last) }
    }

    fun lookup(host: String, cancellation: GithubDnsCancellation): List<InetAddress> {
        if (host !in GithubOutboundNetwork.hosts) return Dns.SYSTEM.lookup(host)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(settings.connectTimeoutMs.toLong())
        val completions = ExecutorCompletionService<GithubDnsAnswer>(workers)
        val futures = listOf(1, 28).map { kind -> completions.submit {
            var owner = host
            val seen = HashSet<String>()
            var answer = GithubDnsAnswer(emptyList(), 0)
            for (hop in 0 until 6) {
                if (!seen.add(owner)) throw UnknownHostException("DNS alias cycle")
                answer = query(owner, kind, cancellation, deadline)
                if (answer.addresses.isNotEmpty() || answer.alias == null) break
                owner = answer.alias
            }
            answer
        } }
        val addresses = ArrayList<InetAddress>()
        try {
            repeat(2) {
                cancellation.check()
                val remaining = (deadline - System.nanoTime()).coerceAtLeast(0)
                // Once a family has usable addresses, do not wait a full DNS budget for the other.
                val wait = if (addresses.isEmpty()) remaining else minOf(remaining, TimeUnit.MILLISECONDS.toNanos(150))
                val next = completions.poll(wait, TimeUnit.NANOSECONDS) ?: return@repeat
                runCatching { next.get() }.getOrNull()?.let { addresses.addAll(it.addresses) }
            }
            cancellation.check()
            if (addresses.isEmpty()) throw UnknownHostException("GitHub DNS returned no public address")
            return addresses.distinct()
        } finally { futures.forEach { it.cancel(true) } }
    }

    companion object {
        private val random = SecureRandom()
        private val workers = Executors.newCachedThreadPool { Thread(it, "github-doh").apply { isDaemon = true } }
        private val bootstrap = mapOf(
            "dns.alidns.com" to listOf("223.5.5.5", "223.6.6.6"), "doh.pub" to listOf("1.12.12.12", "120.53.53.53"),
            "dns.google" to listOf("8.8.8.8", "8.8.4.4"), "cloudflare-dns.com" to listOf("104.16.248.249", "104.16.249.249")
        )
    }
}

internal class GithubCallDns(private val resolver: GithubDohResolver, private val cancellation: GithubDnsCancellation) : Dns {
    override fun lookup(hostname: String): List<InetAddress> = resolver.lookup(hostname, cancellation)
    // Equal resolver identity allows pooling within a frozen settings generation.
    override fun equals(other: Any?): Boolean = other is GithubCallDns && resolver === other.resolver
    override fun hashCode(): Int = System.identityHashCode(resolver)
}
