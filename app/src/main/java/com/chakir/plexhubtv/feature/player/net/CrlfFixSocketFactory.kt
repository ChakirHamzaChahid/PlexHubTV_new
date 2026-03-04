package com.chakir.plexhubtv.feature.player.net

import java.io.FilterInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Socket factory that wraps socket InputStreams with a CRLF fixer.
 *
 * Many Xtream/IPTV servers send HTTP response headers with bare `\r` (CR)
 * instead of `\r\n` (CRLF) as required by RFC 7230. Both Android's built-in
 * HttpURLConnection and OkHttp reject these malformed responses with:
 *   EOFException: \n not found: limit=1 content=0d…
 *
 * This factory creates sockets whose InputStream transparently inserts `\n`
 * after bare `\r` ONLY during HTTP header parsing (detected via \r\n\r\n state machine).
 * Once headers are fully read, the stream switches to direct bulk reads for
 * maximum performance with no corruption of binary video data.
 */
class CrlfFixSocketFactory : SocketFactory() {

    override fun createSocket(): Socket = CrlfFixSocket()

    override fun createSocket(host: String, port: Int): Socket =
        CrlfFixSocket(host, port)

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        CrlfFixSocket(host, port, localHost, localPort)

    override fun createSocket(host: InetAddress, port: Int): Socket =
        CrlfFixSocket(host, port)

    override fun createSocket(host: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        CrlfFixSocket(host, port, localAddress, localPort)
}

/**
 * Socket subclass that wraps getInputStream() with [CrlfFixInputStream].
 */
private class CrlfFixSocket : Socket {
    @Volatile
    private var wrappedInput: CrlfFixInputStream? = null

    constructor() : super() { applyReceiveBuffer() }
    constructor(host: String, port: Int) : super(host, port) { applyReceiveBuffer() }
    constructor(host: String, port: Int, localAddr: InetAddress, localPort: Int) : super(host, port, localAddr, localPort) { applyReceiveBuffer() }
    constructor(host: InetAddress, port: Int) : super(host, port) { applyReceiveBuffer() }
    constructor(host: InetAddress, port: Int, localAddr: InetAddress, localPort: Int) : super(host, port, localAddr, localPort) { applyReceiveBuffer() }

    /**
     * Enlarge the TCP receive buffer so the kernel can absorb data while
     * ExoPlayer's loader briefly pauses (buffer full → waits for playback
     * to consume ~2s before resuming). With a large receive buffer the TCP
     * window stays open and the server keeps sending instead of seeing us
     * as idle and closing the connection. 4 MB handles streams up to ~25 Mbps
     * with a 1-2 second loader gap.
     *
     * OkHttp calls createSocket() (no-args) then connect() separately, so
     * the buffer is set BEFORE the TCP handshake for optimal window negotiation.
     */
    private fun applyReceiveBuffer() {
        try { receiveBufferSize = RECV_BUFFER_SIZE } catch (_: Exception) { /* kernel may cap */ }
    }

    companion object {
        private const val RECV_BUFFER_SIZE = 4 * 1024 * 1024 // 4 MB
    }

    override fun getInputStream(): InputStream {
        if (wrappedInput == null) {
            wrappedInput = CrlfFixInputStream(super.getInputStream())
        }
        return wrappedInput!!
    }
}

/**
 * InputStream wrapper that inserts `\n` after bare `\r` during HTTP header parsing.
 *
 * State machine tracks `\r\n\r\n` in the OUTPUT stream to detect end-of-headers.
 * Once headers are done, all reads pass through directly (zero overhead for video body).
 */
private class CrlfFixInputStream(`in`: InputStream) : FilterInputStream(`in`) {
    private var headersDone = false
    private var headerEndState = 0 // 0→\r→1→\n→2→\r→3→\n→4 (done)
    private val pending = ArrayDeque<Int>()

    /** Track output bytes to detect \r\n\r\n (end of HTTP headers). */
    private fun trackByte(b: Int): Int {
        headerEndState = when {
            b == '\r'.code && (headerEndState == 0 || headerEndState == 2) -> headerEndState + 1
            b == '\n'.code && (headerEndState == 1 || headerEndState == 3) -> headerEndState + 1
            else -> 0
        }
        if (headerEndState == 4) headersDone = true
        return b
    }

    override fun read(): Int {
        if (pending.isNotEmpty()) return trackByte(pending.removeFirst())

        val b = `in`.read()
        if (b == -1) return -1
        if (headersDone) return b

        if (b != '\r'.code) return trackByte(b)

        // Got \r — peek next byte to decide if \r\n (normal) or bare \r
        val next = `in`.read()
        if (next == '\n'.code) {
            // Normal \r\n
            pending.addLast('\n'.code)
            return trackByte('\r'.code)
        }
        // Bare \r — insert \n after it
        pending.addLast('\n'.code)
        if (next != -1) pending.addLast(next)
        return trackByte('\r'.code)
    }

    override fun read(buf: ByteArray, off: Int, len: Int): Int {
        if (headersDone && pending.isEmpty()) {
            // Body: direct bulk read — zero overhead for video streaming
            return `in`.read(buf, off, len)
        }
        // Headers: byte-by-byte with CRLF fix (headers are < 2KB, negligible cost)
        if (len == 0) return 0
        var pos = off
        val end = off + len
        while (pos < end) {
            val b = read()
            if (b == -1) return if (pos == off) -1 else pos - off
            buf[pos++] = b.toByte()
            // Once headers are done, switch to bulk for remaining bytes
            if (headersDone && pending.isEmpty() && pos < end) {
                val n = `in`.read(buf, pos, end - pos)
                if (n > 0) pos += n
                break
            }
        }
        return pos - off
    }
}
