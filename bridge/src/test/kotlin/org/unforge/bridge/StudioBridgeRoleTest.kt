package org.unforge.bridge

import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Covers the studio/client role split and the player-state relay added for the AI4FUN Studio link:
 * role registration, relay of `player.state` and `player.event` to studio subscribers, rejection of
 * player frames from a studio, the roster broadcast and the new read-only HTTP surface.
 */
@Execution(ExecutionMode.SAME_THREAD)
class StudioBridgeRoleTest {
    private fun allocatePort(): Int = ServerSocket(0).use { it.localPort }

    private class Peer(
        url: String,
        private val received: CopyOnWriteArrayList<String> = CopyOnWriteArrayList(),
        private val latch: CountDownLatch = CountDownLatch(1),
    ) : WebSocketClient(URI(url)) {
        val messages: List<String>
            get() = received

        override fun onOpen(handshakedata: ServerHandshake) {
            latch.countDown()
        }

        override fun onMessage(message: String) {
            received += message
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) = Unit

        override fun onError(ex: Exception) = Unit

        fun awaitOpen(seconds: Long = 5): Boolean = latch.await(seconds, TimeUnit.SECONDS)

        fun types(): List<String> =
            received.mapNotNull { BridgeProtocol.string(BridgeProtocol.parse(it), "type") }

        fun firstOfType(type: String) =
            received
                .map { BridgeProtocol.parse(it) }
                .firstOrNull { BridgeProtocol.string(it, "type") == type }

        fun awaitType(type: String, seconds: Long = 5): Boolean {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            while (System.nanoTime() < deadline) {
                if (types().contains(type)) return true
                Thread.sleep(25)
            }
            return types().contains(type)
        }

        /**
         * The bridge sends an unsolicited `connection.ready` on open and a second one in reply to
         * `connection.hello`. Only the reply carries the resolved role, so waits for that one
         * specifically.
         */
        fun awaitRoleInReady(seconds: Long = 5): String? {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            while (System.nanoTime() < deadline) {
                val role =
                    received
                        .map { BridgeProtocol.parse(it) }
                        .lastOrNull {
                            BridgeProtocol.string(it, "type") == "connection.ready" &&
                                BridgeProtocol.string(it, "role") != null
                        }
                        ?.let { BridgeProtocol.string(it, "role") }
                if (role != null) return role
                Thread.sleep(25)
            }
            return null
        }
    }

    private fun bridgeWith(executor: String) =
        BridgeConfig(
            websocketPort = allocatePort(),
            httpPort = allocatePort(),
            openAiApiKey = "test-key",
            connectTimeoutMs = 1_000,
            requestTimeoutMs = 5_000,
            executor = executor,
        )

    @Test
    fun `player state is relayed to a studio subscriber and exposed over http`() {
        val config = bridgeWith(BridgeConfig.EXECUTOR_CODEX)
        val bridge = UnforgeBridge(config)
        val studio = Peer("ws://127.0.0.1:${config.websocketPort}/ws")
        val client = Peer("ws://127.0.0.1:${config.websocketPort}/ws")

        try {
            bridge.start()
            assertTrue(studio.connectBlocking(5, TimeUnit.SECONDS))
            assertTrue(client.connectBlocking(5, TimeUnit.SECONDS))

            studio.send(
                BridgeProtocol.event(
                    "connection.hello",
                    fields =
                        mapOf("role" to BridgeProtocol.ROLE_STUDIO, "clientId" to "studio-test"),
                )
            )
            client.send(
                BridgeProtocol.event(
                    "connection.hello",
                    fields =
                        mapOf("role" to BridgeProtocol.ROLE_CLIENT, "clientId" to "client-test"),
                )
            )
            assertTrue(studio.awaitType("connection.roster"))
            assertTrue(client.awaitType("connection.ready"))

            client.send(
                BridgeProtocol.event(
                    "player.state",
                    fields =
                        mapOf(
                            "clientId" to "client-test",
                            "state" to mapOf("name" to "Tester", "combatLevel" to 126),
                        ),
                )
            )
            assertTrue(
                studio.awaitType("player.state"),
                "studio never received the relayed player state",
            )
            val relayed = studio.firstOfType("player.state")
            assertNotNull(relayed)
            assertEquals("Tester", relayed!!.path("state").path("name").asText())

            client.send(
                BridgeProtocol.event(
                    "player.event",
                    fields = mapOf("clientId" to "client-test", "event" to "login"),
                )
            )
            assertTrue(studio.awaitType("player.event"))

            val http = HttpClient.newHttpClient()
            val clients =
                http.send(
                    HttpRequest.newBuilder(URI("http://127.0.0.1:${config.httpPort}/v1/clients"))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals(200, clients.statusCode())
            assertTrue(clients.body().contains("client-test"))
            assertTrue(clients.body().contains("studioConnected\":true"))

            val latest =
                http.send(
                    HttpRequest.newBuilder(
                            URI(
                                "http://127.0.0.1:${config.httpPort}/v1/player/latest?clientId=client-test"
                            )
                        )
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals(200, latest.statusCode())
            assertTrue(latest.body().contains("Tester"))

            val studioStatus =
                http.send(
                    HttpRequest.newBuilder(URI("http://127.0.0.1:${config.httpPort}/v1/studio"))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals(200, studioStatus.statusCode())
            assertTrue(studioStatus.body().contains("\"studioConnected\":true"))
        } finally {
            client.closeBlocking()
            studio.closeBlocking()
            bridge.close()
        }
    }

    @Test
    fun `a studio may not publish player state`() {
        val config = bridgeWith(BridgeConfig.EXECUTOR_CODEX)
        val bridge = UnforgeBridge(config)
        val studio = Peer("ws://127.0.0.1:${config.websocketPort}/ws")

        try {
            bridge.start()
            assertTrue(studio.connectBlocking(5, TimeUnit.SECONDS))
            studio.send(
                BridgeProtocol.event(
                    "connection.hello",
                    fields =
                        mapOf("role" to BridgeProtocol.ROLE_STUDIO, "clientId" to "studio-test"),
                )
            )
            assertTrue(studio.awaitType("connection.ready"))

            studio.send(
                BridgeProtocol.event(
                    "player.state",
                    fields = mapOf("clientId" to "studio-test", "state" to mapOf("name" to "nope")),
                )
            )
            assertTrue(studio.awaitType("task.error"))
            val error = studio.firstOfType("task.error")
            assertEquals("role_not_permitted", BridgeProtocol.string(error!!, "errorCode"))
        } finally {
            studio.closeBlocking()
            bridge.close()
        }
    }

    @Test
    fun `studio executor receives the task and its result is relayed back to the client`() {
        val config = bridgeWith(BridgeConfig.EXECUTOR_STUDIO)
        val bridge = UnforgeBridge(config)
        val studio = Peer("ws://127.0.0.1:${config.websocketPort}/ws")
        val client = Peer("ws://127.0.0.1:${config.websocketPort}/ws")

        try {
            bridge.start()
            assertTrue(studio.connectBlocking(5, TimeUnit.SECONDS))
            assertTrue(client.connectBlocking(5, TimeUnit.SECONDS))
            studio.send(
                BridgeProtocol.event(
                    "connection.hello",
                    fields = mapOf("role" to BridgeProtocol.ROLE_STUDIO),
                )
            )
            client.send(
                BridgeProtocol.event(
                    "connection.hello",
                    fields = mapOf("role" to BridgeProtocol.ROLE_CLIENT),
                )
            )
            assertTrue(studio.awaitType("connection.roster"))

            client.send(
                BridgeProtocol.event(
                    "task.submit",
                    taskId = "studio-task-1",
                    requestId = "studio-request-1",
                    fields = mapOf("text" to "where is the player?"),
                )
            )

            assertTrue(studio.awaitType("task.submit"), "studio did not receive the delegated task")
            val delegated = studio.firstOfType("task.submit")
            assertEquals("studio-task-1", BridgeProtocol.string(delegated!!, "taskId"))
            assertEquals(BridgeProtocol.ROLE_STUDIO, BridgeProtocol.string(delegated, "role"))

            studio.send(
                BridgeProtocol.event(
                    "task.result",
                    taskId = "studio-task-1",
                    requestId = "studio-request-1",
                    fields = mapOf("status" to "completed", "text" to "at the Grand Exchange"),
                )
            )

            assertTrue(client.awaitType("task.result"), "client never received the studio result")
            val result = client.firstOfType("task.result")
            assertEquals("at the Grand Exchange", BridgeProtocol.string(result!!, "text"))
            assertEquals("studio", BridgeProtocol.string(result, "source"))
        } finally {
            client.closeBlocking()
            studio.closeBlocking()
            bridge.close()
        }
    }

    @Test
    fun `a peer without a role is still treated as a client`() {
        val config = bridgeWith(BridgeConfig.EXECUTOR_CODEX)
        val bridge = UnforgeBridge(config)
        val legacy = Peer("ws://127.0.0.1:${config.websocketPort}/ws")

        try {
            bridge.start()
            assertTrue(legacy.connectBlocking(5, TimeUnit.SECONDS))
            // Exactly what an older plugin build sends: no role, no clientId.
            legacy.send(
                BridgeProtocol.event(
                    "connection.hello",
                    fields = mapOf("client" to "unforge-239-client"),
                )
            )
            assertTrue(legacy.awaitType("connection.ready"))
            assertEquals(BridgeProtocol.ROLE_CLIENT, legacy.awaitRoleInReady())

            legacy.send(BridgeProtocol.event("ping"))
            assertTrue(legacy.awaitType("pong"))

            legacy.send(
                "{\"protocol\":\"unforge.bridge\",\"version\":1,\"type\":\"does.not.exist\"}"
            )
            assertTrue(legacy.awaitType("task.error"))
            val error = legacy.firstOfType("task.error")
            assertEquals("unknown_message_type", BridgeProtocol.string(error!!, "errorCode"))
            assertFalse(legacy.types().contains("player.state"))
        } finally {
            legacy.closeBlocking()
            bridge.close()
        }
    }

    @Test
    fun `configuration accepts the new executor and player state limits`() {
        val studioConfig = bridgeWith(BridgeConfig.EXECUTOR_STUDIO)
        assertEquals(BridgeConfig.EXECUTOR_STUDIO, studioConfig.executor)
        assertTrue(studioConfig.prefersStudioExecutor())
        assertEquals(BridgeConfig.DEFAULT_STUDIO_PORT, studioConfig.studioPort)

        val defaults = BridgeConfig()
        assertEquals(BridgeConfig.EXECUTOR_CODEX, defaults.executor)
        assertFalse(defaults.prefersStudioExecutor())

        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            BridgeConfig(executor = "rocket")
        }
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            BridgeConfig(maxPlayerStateBytes = 10)
        }
    }
}
