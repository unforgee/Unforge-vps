package org.unforge.bridge

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class UnforgeBridgeTest {
    @Test
    fun `bridge forwards task stream over websocket and exposes health`() {
        val upstream = FakeCodexServer().also { it.start() }
        val config =
            BridgeConfig(
                websocketPort = freePort(),
                httpPort = freePort(),
                openAiBaseUrl = upstream.baseUrl,
                openAiApiKey = "test-key",
                connectTimeoutMs = 1_000,
                requestTimeoutMs = 5_000,
            )
        val bridge = UnforgeBridge(config)
        val messages = CopyOnWriteArrayList<String>()
        val resultSeen = CountDownLatch(1)
        val invalidProtocolSeen = CountDownLatch(1)
        val client =
            object : WebSocketClient(URI("ws://127.0.0.1:${config.websocketPort}/ws")) {
                override fun onOpen(handshakedata: ServerHandshake) = Unit

                override fun onMessage(message: String) {
                    messages += message
                    val node = BridgeProtocol.parse(message)
                    if (BridgeProtocol.string(node, "type") == "task.result") {
                        resultSeen.countDown()
                    }
                    if (BridgeProtocol.string(node, "errorCode") == "unsupported_protocol") {
                        invalidProtocolSeen.countDown()
                    }
                }

                override fun onClose(code: Int, reason: String, remote: Boolean) = Unit

                override fun onError(ex: Exception) {
                    messages +=
                        BridgeProtocol.event(
                            "test.client.error",
                            fields = mapOf("message" to (ex.message ?: ex.javaClass.simpleName)),
                        )
                }
            }

        try {
            bridge.start()
            assertTrue(client.connectBlocking(5, TimeUnit.SECONDS))
            client.send(
                BridgeProtocol.event("connection.hello", fields = mapOf("client" to "test"))
            )
            client.send(
                BridgeProtocol.event(
                    "task.submit",
                    taskId = "task-1",
                    requestId = "request-1",
                    fields = mapOf("text" to "hello"),
                )
            )
            assertTrue(resultSeen.await(10, TimeUnit.SECONDS), "bridge did not return task.result")

            val types =
                messages.mapNotNull { BridgeProtocol.string(BridgeProtocol.parse(it), "type") }
            assertTrue(types.contains("connection.ready"))
            assertTrue(types.contains("task.accepted"))
            assertTrue(types.contains("task.progress"))
            assertTrue(types.contains("task.result"))
            val result =
                messages
                    .map { BridgeProtocol.parse(it) }
                    .first { BridgeProtocol.string(it, "type") == "task.result" }
            assertEquals("task-1", BridgeProtocol.string(result, "taskId"))
            assertEquals("HELLO", BridgeProtocol.string(result, "text"))
            assertEquals(1, result.path("version").asInt())
            assertEquals("codex", BridgeProtocol.string(result, "source"))

            client.send("{\"protocol\":\"unforge.bridge\",\"version\":99,\"type\":\"ping\"}")
            assertTrue(
                invalidProtocolSeen.await(5, TimeUnit.SECONDS),
                "invalid protocol was not rejected",
            )

            val health =
                HttpClient.newHttpClient()
                    .send(
                        HttpRequest.newBuilder(URI("http://127.0.0.1:${config.httpPort}/health"))
                            .GET()
                            .build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )
            assertEquals(200, health.statusCode())
            assertTrue(health.body().contains("\"type\":\"health\""))
            assertTrue(health.body().contains("\"status\":\"ok\""))
        } finally {
            client.closeBlocking()
            bridge.close()
            upstream.close()
        }
    }

    @Test
    fun `configuration rejects public bind addresses`() {
        assertThrows<IllegalArgumentException> { BridgeConfig(bindHost = "0.0.0.0") }
        assertThrows<IllegalArgumentException> {
            BridgeConfig(openAiBaseUrl = "http://192.0.2.10:18902")
        }
    }

    @Test
    fun `dictation endpoint forwards local wav to transcription api`() {
        val upstream = FakeCodexServer().also { it.start() }
        val config =
            BridgeConfig(
                websocketPort = freePort(),
                httpPort = freePort(),
                openAiBaseUrl = upstream.baseUrl,
                openAiApiKey = "test-key",
                connectTimeoutMs = 1_000,
                requestTimeoutMs = 5_000,
            )
        val bridge = UnforgeBridge(config)
        try {
            bridge.start()
            val response =
                HttpClient.newHttpClient()
                    .send(
                        HttpRequest.newBuilder(
                                URI("http://127.0.0.1:${config.httpPort}/v1/dictation")
                            )
                            .header("Content-Type", "audio/wav")
                            .POST(
                                HttpRequest.BodyPublishers.ofByteArray(byteArrayOf(82, 73, 70, 70))
                            )
                            .build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )
            assertEquals(200, response.statusCode())
            val result = BridgeProtocol.parse(response.body())
            assertEquals("dictation.result", BridgeProtocol.string(result, "type"))
            assertEquals("Tee testi", BridgeProtocol.string(result, "text"))
            assertEquals("codex", BridgeProtocol.string(result, "source"))
        } finally {
            bridge.close()
            upstream.close()
        }
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private class FakeCodexServer : AutoCloseable {
        private val executor = Executors.newCachedThreadPool()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        val baseUrl: String
            get() = "http://127.0.0.1:${server.address.port}"

        init {
            server.createContext("/v1/responses") { exchange ->
                val body =
                    exchange.requestBody.use { it.readBytes().toString(StandardCharsets.UTF_8) }
                val request = BridgeProtocol.parse(body)
                assertEquals("gpt-5-codex", BridgeProtocol.string(request, "model"))
                val response = "{\"output_text\":\"HELLO\"}"
                exchange.responseHeaders.set("Content-Type", "application/json")
                val bytes = response.toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { output -> output.write(bytes) }
            }
            server.createContext("/v1/audio/transcriptions") { exchange ->
                exchange.requestBody.use { it.readBytes() }
                val response = "{\"text\":\"Tee testi\"}"
                val bytes = response.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { output -> output.write(bytes) }
            }
            server.executor = executor
        }

        fun start() = server.start()

        override fun close() {
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
