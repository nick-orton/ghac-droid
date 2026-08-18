package com.nickorton.ghac.data.snapcast

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

/**
 * SnapCast JSON-RPC 2.0 client over TCP.
 *
 * Requests and server-initiated notifications share one connection, so a reader
 * coroutine demultiplexes the stream: messages carrying an `id` are responses
 * and complete the waiting caller's [CompletableDeferred]; messages carrying a
 * `method` but no `id` are notifications and are published on [notifications].
 *
 * This mirrors the structure of the Go original's `readLoop`
 * (`internal/snapcast/client.go:79`), with a deferred map standing in for the
 * channel-per-request map.
 */
class SnapcastClient private constructor(
    private val socket: Socket,
    private val reader: BufferedReader,
    private val writer: BufferedWriter,
) : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pendingMutex = Mutex()
    private val pending = mutableMapOf<Long, CompletableDeferred<JsonElement>>()
    private val writeMutex = Mutex()
    private val nextId = AtomicLong(0)

    /**
     * Notification method names, e.g. `Client.OnVolumeChanged`.
     *
     * Only the fact that *something* changed matters: like the Go version, the
     * repository responds by re-fetching the whole server status rather than
     * trying to apply a delta. Overflow drops the oldest, since a dropped
     * notification is harmless when the next one triggers a full re-fetch
     * anyway.
     */
    private val _notifications = MutableSharedFlow<String>(
        extraBufferCapacity = NOTIFY_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notifications: SharedFlow<String> = _notifications.asSharedFlow()

    /** Completes when the reader loop stops, carrying the reason if it failed. */
    private val closedReason = CompletableDeferred<Throwable?>()

    val isConnected: Boolean
        get() = !socket.isClosed && socket.isConnected && !closedReason.isCompleted

    /**
     * Suspends until the reader loop stops, returning the failure that ended it
     * (or null for an orderly close).
     *
     * Callers need this because [notifications] is a [SharedFlow], and a
     * SharedFlow collector never completes on its own — collecting it alone
     * would park forever and silently miss the connection dying.
     */
    suspend fun awaitClosed(): Throwable? = closedReason.await()

    private fun startReadLoop() {
        scope.launch {
            var failure: Throwable? = null
            try {
                while (isActive) {
                    val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                    if (line.isBlank()) continue
                    dispatch(line)
                }
            } catch (e: Throwable) {
                failure = e
            } finally {
                val reason = failure ?: IOException("SnapCast connection closed")
                failAllPending(reason)
                closedReason.complete(failure)
            }
        }
    }

    private suspend fun dispatch(line: String) {
        val element = runCatching { SnapcastProtocol.json.parseToJsonElement(line) }
            .getOrNull() ?: return
        when (element) {
            // SnapCast may batch messages into a single array.
            is JsonArray -> element.forEach { it.asObject()?.let { obj -> handle(obj) } }
            else -> element.asObject()?.let { handle(it) }
        }
    }

    private fun JsonElement.asObject(): JsonObject? = this as? JsonObject

    private suspend fun handle(message: JsonObject) {
        val id = message["id"]?.jsonPrimitive?.longOrNull
        if (id != null) {
            val waiter = pendingMutex.withLock { pending.remove(id) } ?: return
            val error = message["error"]
            if (error != null && error != JsonNull) {
                waiter.completeExceptionally(SnapcastRpcException(error.toString()))
            } else {
                waiter.complete(message["result"] ?: JsonNull)
            }
            return
        }
        // No id: a server-initiated notification.
        val method = message["method"]?.jsonPrimitive?.contentOrNullSafe()
        if (method != null) _notifications.tryEmit(method)
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        runCatching { content }.getOrNull()?.takeIf { it.isNotBlank() }

    private suspend fun failAllPending(reason: Throwable) {
        val waiters = pendingMutex.withLock {
            val copy = pending.values.toList()
            pending.clear()
            copy
        }
        waiters.forEach { it.completeExceptionally(reason) }
    }

    private suspend fun call(method: String, params: JsonElement? = null): JsonElement {
        val id = nextId.incrementAndGet()
        val waiter = CompletableDeferred<JsonElement>()
        pendingMutex.withLock { pending[id] = waiter }

        try {
            val request = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                if (params != null) put("params", params)
            }
            val encoded = SnapcastProtocol.json.encodeToString(JsonObject.serializer(), request)

            writeMutex.withLock {
                withContext(Dispatchers.IO) {
                    writer.write(encoded)
                    writer.write("\n")
                    writer.flush()
                }
            }

            return withTimeout(REQUEST_TIMEOUT_MS) { waiter.await() }
        } catch (e: Throwable) {
            pendingMutex.withLock { pending.remove(id) }
            throw e
        }
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    suspend fun getServerStatus(): List<SnapClientInfo> =
        SnapcastProtocol.parseServerStatus(call("Server.GetStatus"))

    /**
     * Sets volume and mute together.
     *
     * SnapCast carries both in a single `volume` object, so a caller that knows
     * only one of them must still supply the other — sending a partial object
     * would clear the value it omitted. Every mutation therefore goes through
     * this one method.
     */
    suspend fun setVolume(clientId: String, percent: Int, muted: Boolean) {
        call(
            "Client.SetVolume",
            buildJsonObject {
                put("id", clientId)
                putJsonObject("volume") {
                    put("muted", muted)
                    put("percent", percent.coerceIn(MIN_VOLUME, MAX_VOLUME))
                }
            },
        )
    }

    suspend fun setMuted(clientId: String, muted: Boolean, currentVolume: Int) =
        setVolume(clientId, currentVolume, muted)

    suspend fun setName(clientId: String, name: String) {
        call(
            "Client.SetName",
            buildJsonObject {
                put("id", clientId)
                put("name", name)
            },
        )
    }

    override fun close() {
        runCatching { socket.close() }
        scope.cancel()
    }

    companion object {
        const val DEFAULT_PORT = 1705
        const val MIN_VOLUME = 0
        const val MAX_VOLUME = 100

        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val REQUEST_TIMEOUT_MS = 5_000L
        private const val NOTIFY_BUFFER = 16

        suspend fun connect(host: String, port: Int = DEFAULT_PORT): SnapcastClient =
            withContext(Dispatchers.IO) {
                val socket = Socket()
                try {
                    socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                    socket.tcpNoDelay = true
                    SnapcastClient(
                        socket = socket,
                        reader = socket.getInputStream().bufferedReader(),
                        writer = socket.getOutputStream().bufferedWriter(),
                    ).also { it.startReadLoop() }
                } catch (e: Throwable) {
                    runCatching { socket.close() }
                    throw e
                }
            }
    }
}
