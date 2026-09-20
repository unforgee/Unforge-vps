package org.unforge.bridge

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.slf4j.LoggerFactory

class UnforgeBridge(
    private val config: BridgeConfig,
    private val codex: CodexClient = CodexClient(config),
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(UnforgeBridge::class.java)
    private val tasks = ConcurrentHashMap<String, BridgeTask>()
    private val relay = StudioRelay(config)
    private val taskExecutor =
        Executors.newFixedThreadPool(config.maxConcurrentTasks) { runnable ->
            Thread(runnable, "unforge-bridge-task").apply { isDaemon = true }
        }
    private val scheduler =
        ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "unforge-bridge-timeout").apply { isDaemon = true }
        }
    private val httpExecutor =
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "unforge-bridge-http").apply { isDaemon = true }
        }
    private val websocket =
        object : WebSocketServer(InetSocketAddress(config.bindHost, config.websocketPort)) {
            override fun onOpen(connection: WebSocket, handshake: ClientHandshake) {
                if (handshake.resourceDescriptor != "/ws") {
                    connection.close(1008, "Use /ws")
                    return
                }
                send(
                    connection,
                    BridgeProtocol.event(
                        "connection.ready",
                        fields =
                            mapOf(
                                "status" to "ready",
                                "websocketPort" to config.websocketPort,
                                "httpPort" to config.httpPort,
                                "studioPort" to config.studioPort,
                                "executor" to config.executor,
                            ),
                    ),
                )
                logger.info("WebSocket client connected")
            }

            override fun onClose(
                connection: WebSocket,
                code: Int,
                reason: String,
                remote: Boolean,
            ) {
                tasks.values
                    .filter { it.connection == connection }
                    .forEach { cancel(it, notifyClient = false) }
                relay.unregister(connection)
                broadcastRoster()
                logger.info("WebSocket client disconnected: code={} remote={}", code, remote)
            }

            override fun onMessage(connection: WebSocket, message: String) {
                handleMessage(connection, message)
            }

            override fun onError(connection: WebSocket?, exception: Exception) {
                logger.warn("WebSocket error: {}", exception.javaClass.simpleName)
            }

            override fun onStart() {
                logger.info("WebSocket listening on {}:{}", config.bindHost, config.websocketPort)
            }
        }
    private var http: HttpServer? = null
    private var started = false

    @Synchronized
    fun start() {
        check(!started) { "Bridge is already started" }
        val server = HttpServer.create(InetSocketAddress(config.bindHost, config.httpPort), 0)
        server.createContext("/health") { exchange -> handleHealth(exchange) }
        server.createContext("/config") { exchange -> handleConfig(exchange) }
        server.createContext("/v1/dictation") { exchange -> handleDictation(exchange) }
        server.createContext("/v1/tasks/cancel") { exchange -> handleHttpCancel(exchange) }
        server.createContext("/v1/studio") { exchange -> handleStudioStatus(exchange) }
        server.createContext("/v1/clients") { exchange -> handleClients(exchange) }
        server.createContext("/v1/player/latest") { exchange -> handlePlayerLatest(exchange) }
        server.executor = httpExecutor
        server.start()
        http = server
        try {
            websocket.start()
            started = true
            logger.info(
                "Unforge bridge ready on loopback: ws=ws://{}:{}/ws http=http://{}:{} executor={} studioPort={}",
                config.bindHost,
                config.websocketPort,
                config.bindHost,
                config.httpPort,
                config.executor,
                config.studioPort,
            )
        } catch (error: Exception) {
            server.stop(0)
            http = null
            throw error
        }
    }

    override fun close() {
        if (!started) return
        tasks.values.toList().forEach { cancel(it, notifyClient = false) }
        try {
            websocket.stop(1_000)
        } catch (error: Exception) {
            logger.warn("WebSocket shutdown failed: {}", error.javaClass.simpleName)
        }
        http?.stop(0)
        http = null
        scheduler.shutdownNow()
        taskExecutor.shutdownNow()
        httpExecutor.shutdownNow()
        started = false
        logger.info("Unforge bridge stopped")
    }

    private fun handleMessage(connection: WebSocket, payload: String) {
        val node =
            try {
                BridgeProtocol.parse(payload)
            } catch (_: Exception) {
                sendError(
                    connection,
                    null,
                    null,
                    "invalid_json",
                    "Message was not valid JSON",
                    false,
                )
                return
            }

        if (
            BridgeProtocol.string(node, "protocol") != BridgeProtocol.PROTOCOL ||
                node.path("version").asInt(-1) != BridgeProtocol.VERSION
        ) {
            sendError(
                connection,
                null,
                null,
                "unsupported_protocol",
                "Unsupported protocol or version",
                false,
            )
            return
        }

        when (BridgeProtocol.string(node, "type")) {
            "connection.hello" -> onHello(connection, node)
            "ping" -> send(connection, BridgeProtocol.event("pong"))
            "player.state" -> onPlayerState(connection, node, payload)
            "player.event" -> onPlayerEvent(connection, node, payload)
            "studio.status" -> onStudioStatus(connection)
            "task.submit" -> submit(connection, node)
            "task.cancel" -> onTaskCancel(connection, node)
            "task.progress",
            "task.result",
            "task.error",
            "task.cancelled" -> onStudioTaskUpdate(connection, node)
            else ->
                sendError(
                    connection,
                    BridgeProtocol.string(node, "taskId"),
                    BridgeProtocol.string(node, "requestId"),
                    "unknown_message_type",
                    "Unsupported message type",
                    false,
                )
        }
    }

    private fun onHello(connection: WebSocket, node: JsonNode) {
        val role = BridgeProtocol.roleOf(node)
        relay.registerRole(connection, role)
        BridgeProtocol.string(node, "clientId")?.let { relay.registerClient(connection, it) }
        send(
            connection,
            BridgeProtocol.event(
                "connection.ready",
                fields =
                    mapOf(
                        "status" to "ready",
                        "role" to role,
                        "executor" to config.executor,
                        "studioPort" to config.studioPort,
                    ),
            ),
        )
        broadcastRoster()
    }

    private fun onPlayerState(connection: WebSocket, node: JsonNode, payload: String) {
        if (relay.isStudio(connection)) {
            sendError(
                connection,
                null,
                null,
                "role_not_permitted",
                "player.state is client-only",
                false,
            )
            return
        }
        val clientId = BridgeProtocol.string(node, "clientId")
        val state = node.get("state")
        if (clientId == null || state == null || !state.isObject) {
            sendError(
                connection,
                null,
                null,
                "invalid_player_state",
                "clientId and state are required",
                false,
            )
            return
        }
        if (!relay.recordPlayerState(connection, clientId, payload)) {
            sendError(
                connection,
                null,
                null,
                "player_state_too_large",
                "player.state exceeds ${config.maxPlayerStateBytes} bytes",
                false,
            )
            return
        }
        relayToStudios(payload)
        if (!relay.hasStudio()) {
            logger.debug("player.state received for {} with no studio subscriber", clientId)
        }
    }

    private fun onPlayerEvent(connection: WebSocket, node: JsonNode, payload: String) {
        if (relay.isStudio(connection)) {
            sendError(
                connection,
                null,
                null,
                "role_not_permitted",
                "player.event is client-only",
                false,
            )
            return
        }
        val clientId = BridgeProtocol.string(node, "clientId")
        val event = BridgeProtocol.string(node, "event")
        if (clientId == null || event == null) {
            sendError(
                connection,
                null,
                null,
                "invalid_player_event",
                "clientId and event are required",
                false,
            )
            return
        }
        if (!relay.isKnownConnection(connection)) relay.registerClient(connection, clientId)
        relayToStudios(payload)
    }

    private fun onStudioStatus(connection: WebSocket) {
        relay.registerRole(connection, BridgeProtocol.ROLE_STUDIO)
        send(
            connection,
            BridgeProtocol.event(
                "studio.roster",
                fields =
                    mapOf(
                        "status" to "registered",
                        "executor" to config.executor,
                        "studioPort" to config.studioPort,
                    ),
            ),
        )
        broadcastRoster()
    }

    /**
     * A studio acting as the task executor answers with the normal terminal task frames. Those are
     * relayed back to the peer that submitted the task.
     */
    private fun onStudioTaskUpdate(connection: WebSocket, node: JsonNode) {
        if (!relay.isStudio(connection)) {
            sendError(
                connection,
                BridgeProtocol.string(node, "taskId"),
                BridgeProtocol.string(node, "requestId"),
                "role_not_permitted",
                "Only a studio may emit task result frames",
                false,
            )
            return
        }
        val taskId = BridgeProtocol.string(node, "taskId")
        val task = taskId?.let { tasks[it] }
        if (task == null) {
            logger.debug("Studio task frame for unknown task: {}", taskId)
            return
        }
        send(
            task.connection,
            BridgeProtocol.copyWithSource(node, "studio", task.taskId, task.requestId),
        )
        val type = BridgeProtocol.string(node, "type")
        if (type == "task.result" || type == "task.error" || type == "task.cancelled") {
            complete(task)
        }
    }

    private fun onTaskCancel(connection: WebSocket, node: JsonNode) {
        val taskId = BridgeProtocol.string(node, "taskId")
        val task = taskId?.let { tasks[it] }
        if (task == null) {
            sendError(
                connection,
                taskId,
                BridgeProtocol.string(node, "requestId"),
                "task_not_found",
                "Task is not active",
                false,
            )
            return
        }
        task.studioConnection?.let { studio ->
            if (studio.isOpen) {
                send(
                    studio,
                    BridgeProtocol.copyWithSource(node, "bridge", task.taskId, task.requestId),
                )
            }
        }
        cancel(task, notifyClient = true)
    }

    private fun submit(connection: WebSocket, node: JsonNode) {
        val taskId = BridgeProtocol.string(node, "taskId")
        val requestId = BridgeProtocol.string(node, "requestId")
        val text = BridgeProtocol.string(node, "text")
        if (!isValidId(taskId) || !isValidId(requestId) || text == null) {
            sendError(
                connection,
                taskId,
                requestId,
                "invalid_task",
                "taskId, requestId and text are required",
                false,
            )
            return
        }
        val activeTaskId = taskId ?: return
        val activeRequestId = requestId ?: return
        val taskText = text ?: return
        if (taskText.length > config.maxTaskTextChars) {
            sendError(
                connection,
                taskId,
                requestId,
                "text_too_large",
                "Task text exceeds the local limit",
                false,
            )
            return
        }
        if (tasks.size >= config.maxConcurrentTasks || tasks.containsKey(taskId)) {
            sendError(
                connection,
                taskId,
                requestId,
                "capacity_or_duplicate",
                "Task capacity is full or taskId is already active",
                true,
            )
            return
        }

        val task = BridgeTask(activeTaskId, activeRequestId, taskText, connection)
        if (tasks.putIfAbsent(activeTaskId, task) != null) {
            sendError(
                connection,
                taskId,
                requestId,
                "duplicate_task",
                "taskId is already active",
                false,
            )
            return
        }
        send(
            connection,
            BridgeProtocol.event("task.accepted", taskId, requestId, mapOf("status" to "accepted")),
        )
        sendProgress(task, "queued", "Task accepted by the local bridge")
        task.future = taskExecutor.submit { forward(task) }
        task.timeout =
            scheduler.schedule(
                {
                    if (task.terminal.compareAndSet(false, true)) {
                        tasks.remove(task.taskId, task)
                        task.future?.cancel(true)
                        sendError(
                            task.connection,
                            task.taskId,
                            task.requestId,
                            "timeout",
                            "Task exceeded the local request timeout",
                            true,
                        )
                    }
                },
                config.requestTimeoutMs,
                java.util.concurrent.TimeUnit.MILLISECONDS,
            )
    }

    /**
     * Chooses the executor for one task.
     *
     * A task that originated from a studio is always executed locally by Codex, otherwise a studio
     * executor would relay the task straight back to itself.
     */
    private fun forward(task: BridgeTask) {
        if (task.terminal.get()) return
        val originIsStudio = relay.isStudio(task.connection)
        val studio = if (!originIsStudio && config.prefersStudioExecutor()) selectStudio() else null
        if (studio == null) {
            forwardToCodex(task)
        } else {
            forwardToStudio(task, studio)
        }
    }

    private fun selectStudio(): WebSocket? = relay.studioConnections().firstOrNull { it.isOpen }

    private fun forwardToCodex(task: BridgeTask) {
        sendProgress(task, "forwarding", "Forwarding directly to Codex")
        try {
            val terminal =
                codex.submit(task.taskId, task.requestId, task.text) { node ->
                    if (task.terminal.get()) return@submit
                    send(
                        task.connection,
                        BridgeProtocol.copyWithSource(node, "codex", task.taskId, task.requestId),
                    )
                    val type = BridgeProtocol.string(node, "type")
                    if (type == "task.result" || type == "task.error" || type == "task.cancelled") {
                        complete(task)
                    }
                }
            if (!terminal && task.terminal.compareAndSet(false, true)) {
                tasks.remove(task.taskId, task)
                sendError(
                    task.connection,
                    task.taskId,
                    task.requestId,
                    "upstream_no_terminal",
                    "Codex closed the response without a terminal task message",
                    true,
                )
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            if (task.terminal.compareAndSet(false, true)) {
                tasks.remove(task.taskId, task)
                sendCancelled(task)
            }
        } catch (error: Exception) {
            if (task.terminal.compareAndSet(false, true)) {
                tasks.remove(task.taskId, task)
                sendError(
                    task.connection,
                    task.taskId,
                    task.requestId,
                    "codex_unavailable",
                    error.message ?: "Codex request failed",
                    true,
                )
            }
            logger.warn("Codex task failed: {}", error.javaClass.simpleName)
        }
    }

    /**
     * Hands the task to a connected studio agent, which owns provider selection, free-first routing
     * and cost control. The studio answers with the standard terminal task frames, relayed by
     * [onStudioTaskUpdate].
     */
    private fun forwardToStudio(task: BridgeTask, studio: WebSocket) {
        task.studioConnection = studio
        sendProgress(task, "forwarding", "Forwarding to AI4FUN Studio agent")
        val payload =
            BridgeProtocol.event(
                "task.submit",
                task.taskId,
                task.requestId,
                mapOf(
                    "text" to task.text,
                    "source" to "bridge",
                    "role" to BridgeProtocol.ROLE_STUDIO,
                ),
            )
        send(studio, payload)
        logger.info("Task {} delegated to studio agent", task.taskId)
    }

    private fun complete(task: BridgeTask) {
        if (task.terminal.compareAndSet(false, true)) {
            tasks.remove(task.taskId, task)
            task.timeout?.cancel(false)
        }
    }

    private fun cancel(task: BridgeTask, notifyClient: Boolean) {
        if (!task.terminal.compareAndSet(false, true)) return
        tasks.remove(task.taskId, task)
        task.timeout?.cancel(false)
        task.future?.cancel(true)
        if (notifyClient) sendCancelled(task)
    }

    private fun sendProgress(task: BridgeTask, phase: String, message: String) {
        send(
            task.connection,
            BridgeProtocol.event(
                "task.progress",
                task.taskId,
                task.requestId,
                mapOf("status" to "running", "phase" to phase, "message" to message),
            ),
        )
    }

    private fun sendCancelled(task: BridgeTask) {
        send(
            task.connection,
            BridgeProtocol.event(
                "task.cancelled",
                task.taskId,
                task.requestId,
                mapOf("status" to "cancelled"),
            ),
        )
    }

    private fun sendError(
        connection: WebSocket,
        taskId: String?,
        requestId: String?,
        code: String,
        message: String,
        retryable: Boolean,
    ) {
        send(
            connection,
            BridgeProtocol.event(
                "task.error",
                taskId,
                requestId,
                mapOf(
                    "status" to "failed",
                    "errorCode" to code,
                    "message" to message,
                    "retryable" to retryable,
                ),
            ),
        )
    }

    private fun send(connection: WebSocket, payload: String) {
        try {
            if (connection.isOpen) connection.send(payload)
        } catch (error: Exception) {
            logger.debug("WebSocket send skipped: {}", error.javaClass.simpleName)
        }
    }

    /** Fans a relayable client frame out to every connected studio agent. */
    private fun relayToStudios(payload: String) {
        var delivered = 0
        for (studio in relay.studioConnections()) {
            if (studio.isOpen) {
                send(studio, payload)
                delivered++
            }
        }
        if (delivered == 0) logger.debug("No studio subscriber for relayed frame")
    }

    private fun broadcastRoster() {
        val frame = relay.rosterFrame()
        relayToStudios(frame)
    }

    private fun handleHealth(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            respond(exchange, 405, "{\"error\":\"method_not_allowed\"}")
            return
        }
        respond(
            exchange,
            200,
            BridgeProtocol.event(
                "health",
                fields =
                    mapOf(
                        "status" to "ok",
                        "bind" to config.bindHost,
                        "websocketPort" to config.websocketPort,
                        "httpPort" to config.httpPort,
                        "activeTasks" to tasks.size,
                        "codexConfigured" to config.openAiApiKey.isNotBlank(),
                        "codexModel" to config.codexModel,
                        "executor" to config.executor,
                        "studioConnected" to relay.hasStudio(),
                        "studioCount" to relay.studioCount(),
                        "clientCount" to relay.clientCount(),
                    ),
            ),
        )
    }

    private fun handleConfig(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            respond(exchange, 405, "{\"error\":\"method_not_allowed\"}")
            return
        }
        respond(
            exchange,
            200,
            BridgeProtocol.event(
                "config",
                fields =
                    mapOf(
                        "bind" to config.bindHost,
                        "websocketUrl" to "ws://${config.bindHost}:${config.websocketPort}/ws",
                        "httpBaseUrl" to "http://${config.bindHost}:${config.httpPort}",
                        "openAiBaseUrl" to config.openAiBaseUrl,
                        "codexConfigured" to config.openAiApiKey.isNotBlank(),
                        "codexModel" to config.codexModel,
                        "transcriptionModel" to config.transcriptionModel,
                        "connectTimeoutMs" to config.connectTimeoutMs,
                        "requestTimeoutMs" to config.requestTimeoutMs,
                        "maxTaskTextChars" to config.maxTaskTextChars,
                        "maxConcurrentTasks" to config.maxConcurrentTasks,
                        "maxAudioBytes" to config.maxAudioBytes,
                        "executor" to config.executor,
                        "studioPort" to config.studioPort,
                        "maxPlayerStateBytes" to config.maxPlayerStateBytes,
                    ),
            ),
        )
    }

    /** `GET /v1/studio` — is a studio agent attached, and how is it configured? */
    private fun handleStudioStatus(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            respond(exchange, 405, "{\"error\":\"method_not_allowed\"}")
            return
        }
        respondJson(exchange, 200, relay.statusNode())
    }

    /** `GET /v1/clients` — connected peers with role and snapshot freshness. */
    private fun handleClients(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            respond(exchange, 405, "{\"error\":\"method_not_allowed\"}")
            return
        }
        val root: ObjectNode = BridgeProtocol.mapper.createObjectNode()
        root.put("protocol", BridgeProtocol.PROTOCOL)
        root.put("version", BridgeProtocol.VERSION)
        root.set<JsonNode>("studio", relay.statusNode())
        val clients: ArrayNode = relay.rosterArray()
        root.set<ArrayNode>("clients", clients)
        respondJson(exchange, 200, root)
    }

    /** `GET /v1/player/latest[?clientId=]` — newest player snapshot. */
    private fun handlePlayerLatest(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            respond(exchange, 405, "{\"error\":\"method_not_allowed\"}")
            return
        }
        val clientId = queryParameter(exchange, "clientId")
        val snapshot =
            if (clientId != null) relay.latestState(clientId) else relay.allClients().firstOrNull()
        if (snapshot == null || snapshot.payload.isEmpty()) {
            respond(exchange, 404, "{\"error\":\"player_state_unavailable\"}")
            return
        }
        val root: ObjectNode = BridgeProtocol.mapper.createObjectNode()
        root.put("protocol", BridgeProtocol.PROTOCOL)
        root.put("version", BridgeProtocol.VERSION)
        root.put("type", "player.state")
        root.put("clientId", snapshot.clientId)
        root.put("updatedAt", snapshot.updatedAt)
        root.set<JsonNode>("state", BridgeProtocol.parse(snapshot.payload).path("state"))
        respondJson(exchange, 200, root)
    }

    private fun handleDictation(exchange: HttpExchange) {
        if (exchange.requestMethod != "POST") {
            respond(exchange, 405, "{\"error\":\"method_not_allowed\"}")
            return
        }
        val contentLength = exchange.requestHeaders.getFirst("Content-Length")?.toLongOrNull()
        if (contentLength != null && contentLength > config.maxAudioBytes) {
            respond(exchange, 413, "{\"error\":\"audio_too_large\"}")
            return
        }
        val audio =
            try {
                exchange.requestBody.use { stream -> stream.readNBytes(config.maxAudioBytes + 1) }
            } catch (_: Exception) {
                respond(exchange, 400, "{\"error\":\"invalid_audio\"}")
                return
            }
        if (audio.isEmpty()) {
            respond(exchange, 400, "{\"error\":\"empty_audio\"}")
            return
        }
        if (audio.size > config.maxAudioBytes) {
            respond(exchange, 413, "{\"error\":\"audio_too_large\"}")
            return
        }

        try {
            val text = codex.transcribe(audio)
            respond(
                exchange,
                200,
                BridgeProtocol.event(
                    "dictation.result",
                    fields = mapOf("text" to text, "source" to "codex"),
                ),
            )
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            respond(exchange, 503, "{\"error\":\"dictation_interrupted\"}")
        } catch (error: Exception) {
            logger.warn("Dictation request failed: {}", error.javaClass.simpleName)
            respond(
                exchange,
                502,
                BridgeProtocol.event(
                    "dictation.error",
                    fields =
                        mapOf(
                            "errorCode" to "transcription_unavailable",
                            "message" to (error.message ?: "Transcription request failed").take(300),
                        ),
                ),
            )
        }
    }

    private fun handleHttpCancel(exchange: HttpExchange) {
        if (exchange.requestMethod != "POST") {
            respond(exchange, 405, "{\"error\":\"method_not_allowed\"}")
            return
        }
        val body =
            try {
                exchange.requestBody.use { stream ->
                    val bytes = stream.readNBytes(16_384)
                    String(bytes, StandardCharsets.UTF_8)
                }
            } catch (_: Exception) {
                respond(exchange, 400, "{\"error\":\"invalid_body\"}")
                return
            }
        val node =
            try {
                BridgeProtocol.parse(body)
            } catch (_: Exception) {
                respond(exchange, 400, "{\"error\":\"invalid_json\"}")
                return
            }
        val taskId = BridgeProtocol.string(node, "taskId")
        val task = taskId?.let { tasks[it] }
        if (
            BridgeProtocol.string(node, "protocol") != BridgeProtocol.PROTOCOL ||
                node.path("version").asInt(-1) != BridgeProtocol.VERSION ||
                BridgeProtocol.string(node, "type") != "task.cancel" ||
                !isValidId(taskId)
        ) {
            respond(exchange, 400, "{\"error\":\"invalid_cancel\"}")
            return
        }
        if (task == null) {
            respond(exchange, 404, "{\"error\":\"task_not_found\"}")
            return
        }
        cancel(task, notifyClient = true)
        respond(exchange, 200, BridgeProtocol.event("task.cancelled", task.taskId, task.requestId))
    }

    private fun queryParameter(exchange: HttpExchange, name: String): String? {
        val query = exchange.requestURI.rawQuery ?: return null
        for (pair in query.split('&')) {
            val separator = pair.indexOf('=')
            if (separator <= 0) continue
            val key =
                java.net.URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8)
            if (key == name) {
                return java.net.URLDecoder.decode(
                    pair.substring(separator + 1),
                    StandardCharsets.UTF_8,
                )
            }
        }
        return null
    }

    private fun respondJson(exchange: HttpExchange, status: Int, node: JsonNode) {
        respond(exchange, status, BridgeProtocol.mapper.writeValueAsString(node))
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun isValidId(value: String?): Boolean =
        value != null && value.length in 1..96 && value.all { it.isLetterOrDigit() || it in "-_." }

    private class BridgeTask(
        val taskId: String,
        val requestId: String,
        val text: String,
        val connection: WebSocket,
    ) {
        val terminal = AtomicBoolean(false)
        @Volatile var future: Future<*>? = null
        @Volatile var timeout: ScheduledFuture<*>? = null
        @Volatile var studioConnection: WebSocket? = null
    }
}
