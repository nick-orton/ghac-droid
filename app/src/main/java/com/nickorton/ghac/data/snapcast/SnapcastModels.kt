package com.nickorton.ghac.data.snapcast

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** One SnapCast client — in practice, one speaker in one room. */
data class SnapClientInfo(
    val id: String,
    val name: String,
    /** 0–100. */
    val volume: Int,
    val muted: Boolean,
    val connected: Boolean,
)

/** Raised when SnapCast answers a request with a JSON-RPC `error` object. */
class SnapcastRpcException(message: String) : Exception(message)

// ── Wire types for Server.GetStatus ──────────────────────────────────────────
// Only the fields ghac uses are declared; ignoreUnknownKeys covers the rest of
// SnapCast's fairly large status document.

@Serializable
internal data class ServerStatusResult(val server: ServerNode = ServerNode())

@Serializable
internal data class ServerNode(val groups: List<GroupNode> = emptyList())

@Serializable
internal data class GroupNode(val clients: List<ClientNode> = emptyList())

@Serializable
internal data class ClientNode(
    val id: String = "",
    val connected: Boolean = false,
    val config: ClientConfigNode = ClientConfigNode(),
    val host: HostNode = HostNode(),
)

@Serializable
internal data class ClientConfigNode(
    val name: String = "",
    val volume: VolumeNode = VolumeNode(),
)

@Serializable
internal data class VolumeNode(
    val muted: Boolean = false,
    val percent: Int = 0,
)

@Serializable
internal data class HostNode(val name: String = "")

/** Pure parsing helpers, kept separate from the socket so they can be tested. */
internal object SnapcastProtocol {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Flattens a `Server.GetStatus` result into a client list.
     *
     * SnapCast nests clients inside groups, and ghac presents one flat list of
     * speakers, so every group is walked and its clients concatenated. A client
     * with no configured name falls back to its host name, which is what the
     * SnapCast web UI shows and what the Go original did.
     */
    fun parseServerStatus(result: JsonElement): List<SnapClientInfo> {
        val status = json.decodeFromJsonElement(ServerStatusResult.serializer(), result)
        return status.server.groups
            .flatMap { it.clients }
            .map { node ->
                SnapClientInfo(
                    id = node.id,
                    name = node.config.name.ifBlank { node.host.name },
                    volume = node.config.volume.percent,
                    muted = node.config.volume.muted,
                    connected = node.connected,
                )
            }
    }

    /** Overload for tests and for parsing a raw response body. */
    fun parseServerStatus(raw: String): List<SnapClientInfo> =
        parseServerStatus(json.parseToJsonElement(raw))
}
