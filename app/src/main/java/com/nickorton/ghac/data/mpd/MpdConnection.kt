package com.nickorton.ghac.data.mpd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * A single TCP connection to MPD, speaking the raw text protocol.
 *
 * One connection handles one command at a time; a [Mutex] serialises callers so
 * two coroutines cannot interleave their request and response lines on the same
 * socket.
 *
 * Blocking socket reads do not observe coroutine cancellation, so [close] works
 * by closing the socket out from under the reader, which makes the pending read
 * throw. That is the intended way to break out of a parked [idle] call.
 */
class MpdConnection private constructor(
    private val socket: Socket,
    private val reader: BufferedReader,
    private val writer: BufferedWriter,
    /** Protocol version reported in the server greeting, e.g. "0.23.5". */
    val serverVersion: String,
) : Closeable {

    private val mutex = Mutex()

    /** Sends [commandLine] and returns the `key: value` pairs of the reply. */
    suspend fun command(commandLine: String): List<MpdPair> = mutex.withLock {
        withContext(Dispatchers.IO) { exchange(commandLine) }
    }

    /**
     * Blocks until MPD reports a change in one of [subsystems], returning the
     * names that changed.
     *
     * MPD's `idle` monopolises its connection: no other command may be sent
     * while it is parked. The client therefore keeps this on a dedicated
     * connection, exactly as the Go original did.
     */
    suspend fun idle(subsystems: List<String>): List<String> = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Subsystem names are fixed protocol identifiers, so they are sent
            // bare rather than through the quoting path.
            val reply = exchange("idle " + subsystems.joinToString(" "))
            reply.filter { it.key == "changed" }.map { it.value }
        }
    }

    private fun exchange(commandLine: String): List<MpdPair> {
        writer.write(commandLine)
        writer.write("\n")
        writer.flush()
        return readResponse()
    }

    private fun readResponse(): List<MpdPair> {
        val pairs = mutableListOf<MpdPair>()
        while (true) {
            val line = reader.readLine()
                ?: throw IOException("MPD closed the connection mid-response")
            // Exact match: the greeting also begins with "OK", so a prefix
            // check here would end a response early.
            if (line == MpdProtocol.OK) return pairs
            MpdProtocol.parseAck(line)?.let { throw it }
            MpdProtocol.parseLine(line)?.let { pairs += it }
        }
    }

    val isConnected: Boolean
        get() = !socket.isClosed && socket.isConnected

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        const val DEFAULT_PORT = 6600

        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val COMMAND_TIMEOUT_MS = 10_000

        /** No read timeout: an idle connection is meant to park indefinitely. */
        const val NO_READ_TIMEOUT = 0

        suspend fun connect(
            host: String,
            port: Int = DEFAULT_PORT,
            readTimeoutMs: Int = COMMAND_TIMEOUT_MS,
        ): MpdConnection = withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = readTimeoutMs
                socket.tcpNoDelay = true

                val reader = socket.getInputStream().bufferedReader()
                val writer = socket.getOutputStream().bufferedWriter()

                val greeting = reader.readLine()
                    ?: throw IOException("MPD closed the connection before sending a greeting")
                if (!greeting.startsWith(MpdProtocol.GREETING_PREFIX)) {
                    throw IOException("not an MPD server: greeting was \"$greeting\"")
                }

                MpdConnection(
                    socket = socket,
                    reader = reader,
                    writer = writer,
                    serverVersion = greeting.removePrefix(MpdProtocol.GREETING_PREFIX).trim(),
                )
            } catch (e: Throwable) {
                runCatching { socket.close() }
                throw e
            }
        }
    }
}
