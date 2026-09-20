package org.unforge.bridge

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.concurrent.ConcurrentHashMap
import org.java_websocket.WebSocket

/**
 * Connection bookkeeping for the Unforge bridge.
 *
 * The bridge serves two kinds of loopback WebSocket peers:
 * - **client** — the bundled RuneLite-style client plugin. It submits tasks and publishes
 *   `player.state` / `player.event` frames.
 * - **studio** — the AI4FUN Studio agent. It receives every client frame, can submit tasks of its
 *   own, and may act as the task executor.
 *
 * A peer that never announces a role is treated as a client, which keeps older plugin builds
 * working unchanged. All state here is loopback-only and in-memory; nothing is persisted and
 * nothing is exposed off-host.
 */
class StudioRelay(private val config: BridgeConfig) {

    /** Immutable view of the most recent player snapshot for one client. */
    data class ClientSnapshot(
        val clientId: String,
        val role: String,
        val payload: String,
        val connectedAt: Long,
        val updatedAt: Long,
    )

    private val roles = ConcurrentHashMap<WebSocket, String>()
    private val clients = ConcurrentHashMap<WebSocket, ClientSnapshot>()
    private val studios = ConcurrentHashMap.newKeySet<WebSocket>()

    @Volatile private var lastStudioSeenAt: Long = 0L

    // ---------------------------------------------------------------- roles

    /** Registers the role announced by a `connection.hello` frame. */
    fun registerRole(connection: WebSocket, role: String) {
        val resolved = if (BridgeProtocol.isKnownRole(role)) role else BridgeProtocol.ROLE_CLIENT
        roles[connection] = resolved
        if (resolved == BridgeProtocol.ROLE_STUDIO) {
            studios.add(connection)
            lastStudioSeenAt = System.currentTimeMillis()
        }
    }

    fun roleOf(connection: WebSocket): String = roles[connection] ?: BridgeProtocol.ROLE_CLIENT

    fun isStudio(connection: WebSocket): Boolean = roleOf(connection) == BridgeProtocol.ROLE_STUDIO

    fun studioConnections(): Set<WebSocket> = studios.toSet()

    fun hasStudio(): Boolean = studios.isNotEmpty()

    fun studioCount(): Int = studios.size

    fun lastStudioSeenAt(): Long = lastStudioSeenAt

    // -------------------------------------------------------------- clients

    /** Binds a connection to the client id it publishes. */
    fun registerClient(connection: WebSocket, clientId: String) {
        val previous = clients[connection]
        val now = System.currentTimeMillis()
        clients[connection] =
            ClientSnapshot(
                clientId = clientId,
                role = roleOf(connection),
                payload = previous?.payload ?: "",
                connectedAt = previous?.connectedAt ?: now,
                updatedAt = previous?.updatedAt ?: 0L,
            )
    }

    fun clientIdOf(connection: WebSocket): String? = clients[connection]?.clientId

    fun isKnownConnection(connection: WebSocket): Boolean = clients.containsKey(connection)

    /**
     * Stores the newest player snapshot for a client, registering the connection on first use so a
     * client that publishes state before its hello frame is still tracked.
     *
     * @return `false` when the payload exceeds [BridgeConfig.maxPlayerStateBytes], so the caller
     *   can report a protocol error instead of silently buffering oversized frames.
     */
    fun recordPlayerState(connection: WebSocket, clientId: String, payload: String): Boolean {
        if (payload.length > config.maxPlayerStateBytes) return false
        registerClient(connection, clientId)
        val existing = clients.getValue(connection)
        clients[connection] =
            existing.copy(payload = payload, updatedAt = System.currentTimeMillis())
        return true
    }

    /** Newest snapshot for one client id, or `null` when unknown. */
    fun latestState(clientId: String): ClientSnapshot? =
        clients.values.firstOrNull { it.clientId == clientId }

    /** Every known client, ordered by client id for stable output. */
    fun allClients(): List<ClientSnapshot> = clients.values.sortedBy { it.clientId }

    fun clientCount(): Int = clients.size

    fun unregister(connection: WebSocket) {
        roles.remove(connection)
        clients.remove(connection)
        if (studios.remove(connection)) {
            lastStudioSeenAt = System.currentTimeMillis()
        }
    }

    // --------------------------------------------------------------- roster

    /**
     * The `clients` array of a `connection.roster` frame: who is connected, what role they hold,
     * and whether a usable player snapshot has arrived yet.
     */
    fun rosterArray(): ArrayNode {
        val array: ArrayNode = BridgeProtocol.mapper.createArrayNode()
        for (client in allClients()) {
            val node: ObjectNode = array.addObject()
            node.put("clientId", client.clientId)
            node.put("role", client.role)
            node.put("connectedAt", client.connectedAt)
            node.put("hasPlayerState", client.payload.isNotEmpty())
            node.put("playerStateAt", client.updatedAt)
            node.put("playerStateBytes", client.payload.length)
        }
        return array
    }

    fun rosterFrame(): String =
        BridgeProtocol.event(
            "connection.roster",
            fields =
                mapOf(
                    "clients" to rosterArray(),
                    "studioConnected" to hasStudio(),
                    "studioCount" to studioCount(),
                    "executor" to config.executor,
                ),
        )

    /** Parsed, sanitised status used by the HTTP surface. */
    fun statusNode(): JsonNode {
        val node: ObjectNode = BridgeProtocol.mapper.createObjectNode()
        node.put("executor", config.executor)
        node.put("studioConnected", hasStudio())
        node.put("studioCount", studioCount())
        node.put("studioPort", config.studioPort)
        node.put("clientCount", clientCount())
        node.put("lastStudioSeenAt", lastStudioSeenAt)
        node.put("maxPlayerStateBytes", config.maxPlayerStateBytes)
        return node
    }
}
