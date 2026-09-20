package org.unforge.bridge

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

object BridgeProtocol {
    const val PROTOCOL = "unforge.bridge"
    const val VERSION = 1

    /** Connection roles. A missing role is treated as [ROLE_CLIENT] for backwards compatibility. */
    const val ROLE_CLIENT = "client"
    const val ROLE_STUDIO = "studio"

    val mapper: ObjectMapper = ObjectMapper()

    fun parse(payload: String): JsonNode = mapper.readTree(payload)

    fun event(
        type: String,
        taskId: String? = null,
        requestId: String? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): String {
        val node = mapper.createObjectNode()
        node.put("protocol", PROTOCOL)
        node.put("version", VERSION)
        node.put("type", type)
        taskId?.let { node.put("taskId", it) }
        requestId?.let { node.put("requestId", it) }
        fields.forEach { (key, value) -> put(node, key, value) }
        return mapper.writeValueAsString(node)
    }

    fun copyWithSource(
        node: JsonNode,
        source: String,
        taskId: String? = null,
        requestId: String? = null,
    ): String {
        val copy = node.deepCopy<ObjectNode>()
        copy.put("protocol", PROTOCOL)
        copy.put("version", VERSION)
        copy.put("source", source)
        taskId?.let { copy.put("taskId", it) }
        requestId?.let { copy.put("requestId", it) }
        return mapper.writeValueAsString(copy)
    }

    /**
     * Re-emits an already validated inbound frame unchanged. Used to relay client-originated frames
     * (for example `player.state`) to studio subscribers without reshaping the payload.
     */
    fun relay(payload: String): String = payload

    fun string(node: JsonNode, name: String): String? =
        node.get(name)?.takeUnless { it.isNull }?.asText()?.trim()?.takeUnless { it.isEmpty() }

    /**
     * Resolves the connection role from a frame. Anything that is not exactly [ROLE_STUDIO]
     * resolves to [ROLE_CLIENT], so older clients that never send a role keep working.
     */
    fun roleOf(node: JsonNode): String =
        if (string(node, "role") == ROLE_STUDIO) ROLE_STUDIO else ROLE_CLIENT

    /** Role names that the bridge is willing to register. */
    fun isKnownRole(role: String?): Boolean = role == ROLE_CLIENT || role == ROLE_STUDIO

    /**
     * Writes one field onto a frame.
     *
     * Structured values (maps, collections, arrays) are serialized as real JSON so nested payloads
     * such as `state` in a `player.state` frame arrive as objects rather than as a `toString()`
     * rendering. This matters because the bridge validates `state` with `isObject`.
     */
    private fun put(node: ObjectNode, key: String, value: Any?) {
        when (value) {
            null -> node.putNull(key)
            is Boolean -> node.put(key, value)
            is Int -> node.put(key, value)
            is Long -> node.put(key, value)
            is Double -> node.put(key, value)
            is JsonNode -> node.set<JsonNode>(key, value)
            is Map<*, *>,
            is Collection<*>,
            is Array<*> -> node.set<JsonNode>(key, mapper.valueToTree(value))
            else -> node.put(key, value.toString())
        }
    }
}
