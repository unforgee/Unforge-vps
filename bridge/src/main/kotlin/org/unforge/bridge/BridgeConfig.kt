package org.unforge.bridge

import java.net.URI

data class BridgeConfig(
    val bindHost: String = DEFAULT_BIND_HOST,
    val websocketPort: Int = DEFAULT_WEBSOCKET_PORT,
    val httpPort: Int = DEFAULT_HTTP_PORT,
    val openAiBaseUrl: String = DEFAULT_OPENAI_BASE_URL,
    val openAiApiKey: String = "",
    val codexModel: String = DEFAULT_CODEX_MODEL,
    val transcriptionModel: String = DEFAULT_TRANSCRIPTION_MODEL,
    val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    val requestTimeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS,
    val maxTaskTextChars: Int = DEFAULT_MAX_TASK_TEXT_CHARS,
    val maxConcurrentTasks: Int = DEFAULT_MAX_CONCURRENT_TASKS,
    val maxAudioBytes: Int = DEFAULT_MAX_AUDIO_BYTES,
    /**
     * Who executes a submitted task.
     * - [EXECUTOR_CODEX] (default): the bridge calls the OpenAI Codex API directly, exactly as it
     *   did before role support existed.
     * - [EXECUTOR_STUDIO]: the bridge relays the task to a connected studio agent, which owns
     *   provider selection and cost routing. If no studio is connected the bridge falls back to
     *   Codex so a task is never silently dropped.
     */
    val executor: String = DEFAULT_EXECUTOR,
    /** Loopback port the AI4FUN Studio agent is expected to listen on. */
    val studioPort: Int = DEFAULT_STUDIO_PORT,
    /** Upper bound for one `player.state` frame relayed to studio subscribers. */
    val maxPlayerStateBytes: Int = DEFAULT_MAX_PLAYER_STATE_BYTES,
) {
    init {
        require(bindHost == LOOPBACK_HOST) {
            "Unforge bridge is localhost-only; bindHost must be $LOOPBACK_HOST"
        }
        require(websocketPort in 1..65535) { "websocketPort must be between 1 and 65535" }
        require(httpPort in 1..65535) { "httpPort must be between 1 and 65535" }
        require(websocketPort != httpPort) { "websocketPort and httpPort must be different" }
        require(connectTimeoutMs in 250..120_000) {
            "connectTimeoutMs must be between 250 and 120000"
        }
        require(requestTimeoutMs in 1_000..900_000) {
            "requestTimeoutMs must be between 1000 and 900000"
        }
        require(maxTaskTextChars in 1..32_000) { "maxTaskTextChars must be between 1 and 32000" }
        require(maxConcurrentTasks in 1..16) { "maxConcurrentTasks must be between 1 and 16" }

        val uri = URI(openAiBaseUrl)
        val isLoopbackTestEndpoint =
            uri.scheme.equals("http", ignoreCase = true) &&
                (uri.host == LOOPBACK_HOST || uri.host == "localhost")
        val isOpenAiEndpoint =
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host?.equals("api.openai.com", ignoreCase = true) == true
        require(isLoopbackTestEndpoint || isOpenAiEndpoint) {
            "OpenAI endpoint must be https://api.openai.com or a loopback HTTP test endpoint"
        }
        require(codexModel.length in 1..128) { "codexModel must be between 1 and 128 characters" }
        require(transcriptionModel.length in 1..128) {
            "transcriptionModel must be between 1 and 128 characters"
        }
        require(maxAudioBytes in 64_000..20_000_000) {
            "maxAudioBytes must be between 64000 and 20000000"
        }
        require(executor == EXECUTOR_CODEX || executor == EXECUTOR_STUDIO) {
            "executor must be $EXECUTOR_CODEX or $EXECUTOR_STUDIO"
        }
        require(studioPort in 1..65535) { "studioPort must be between 1 and 65535" }
        require(maxPlayerStateBytes in 1_024..262_144) {
            "maxPlayerStateBytes must be between 1024 and 262144"
        }
    }

    /** True when tasks may be handed to a studio agent rather than Codex. */
    fun prefersStudioExecutor(): Boolean = executor == EXECUTOR_STUDIO

    companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        const val DEFAULT_BIND_HOST = LOOPBACK_HOST
        const val DEFAULT_WEBSOCKET_PORT = 18_901
        const val DEFAULT_HTTP_PORT = 18_900
        const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com"
        const val DEFAULT_CODEX_MODEL = "gpt-5-codex"
        const val DEFAULT_TRANSCRIPTION_MODEL = "gpt-transcribe"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 5_000L
        const val DEFAULT_REQUEST_TIMEOUT_MS = 180_000L
        const val DEFAULT_MAX_TASK_TEXT_CHARS = 8_000
        const val DEFAULT_MAX_CONCURRENT_TASKS = 2
        const val DEFAULT_MAX_AUDIO_BYTES = 5_000_000

        const val EXECUTOR_CODEX = "codex"
        const val EXECUTOR_STUDIO = "studio"
        const val DEFAULT_EXECUTOR = EXECUTOR_CODEX
        const val DEFAULT_STUDIO_PORT = 18_902
        const val DEFAULT_MAX_PLAYER_STATE_BYTES = 8_192

        fun fromEnvironment(environment: Map<String, String> = System.getenv()): BridgeConfig {
            fun value(name: String, default: String): String =
                environment[name]?.trim()?.takeUnless { it.isNullOrEmpty() } ?: default

            fun integer(name: String, default: Int): Int =
                value(name, default.toString()).toIntOrNull() ?: error("$name must be an integer")

            fun long(name: String, default: Long): Long =
                value(name, default.toString()).toLongOrNull() ?: error("$name must be an integer")

            val configuredBind = value("UNFORGE_BRIDGE_BIND", DEFAULT_BIND_HOST)
            val bind =
                when (configuredBind.lowercase()) {
                    "localhost",
                    LOOPBACK_HOST -> LOOPBACK_HOST
                    else -> error("UNFORGE_BRIDGE_BIND must be 127.0.0.1 or localhost")
                }

            val openAiApiKey = value("UNFORGE_OPENAI_API_KEY", value("OPENAI_API_KEY", ""))

            val executor = value("UNFORGE_BRIDGE_EXECUTOR", DEFAULT_EXECUTOR).lowercase()
            if (executor != EXECUTOR_CODEX && executor != EXECUTOR_STUDIO) {
                error("UNFORGE_BRIDGE_EXECUTOR must be $EXECUTOR_CODEX or $EXECUTOR_STUDIO")
            }

            return BridgeConfig(
                bindHost = bind,
                websocketPort = integer("UNFORGE_BRIDGE_WS_PORT", DEFAULT_WEBSOCKET_PORT),
                httpPort = integer("UNFORGE_BRIDGE_HTTP_PORT", DEFAULT_HTTP_PORT),
                openAiBaseUrl =
                    value("UNFORGE_OPENAI_BASE_URL", DEFAULT_OPENAI_BASE_URL).trimEnd('/'),
                openAiApiKey = openAiApiKey,
                codexModel = value("UNFORGE_CODEX_MODEL", DEFAULT_CODEX_MODEL),
                transcriptionModel =
                    value("UNFORGE_TRANSCRIPTION_MODEL", DEFAULT_TRANSCRIPTION_MODEL),
                connectTimeoutMs =
                    long("UNFORGE_BRIDGE_CONNECT_TIMEOUT_MS", DEFAULT_CONNECT_TIMEOUT_MS),
                requestTimeoutMs =
                    long("UNFORGE_BRIDGE_REQUEST_TIMEOUT_MS", DEFAULT_REQUEST_TIMEOUT_MS),
                maxTaskTextChars =
                    integer("UNFORGE_BRIDGE_MAX_TEXT_CHARS", DEFAULT_MAX_TASK_TEXT_CHARS),
                maxConcurrentTasks =
                    integer("UNFORGE_BRIDGE_MAX_CONCURRENT_TASKS", DEFAULT_MAX_CONCURRENT_TASKS),
                maxAudioBytes = integer("UNFORGE_BRIDGE_MAX_AUDIO_BYTES", DEFAULT_MAX_AUDIO_BYTES),
                executor = executor,
                studioPort = integer("UNFORGE_STUDIO_PORT", DEFAULT_STUDIO_PORT),
                maxPlayerStateBytes =
                    integer("UNFORGE_BRIDGE_MAX_PLAYER_STATE_BYTES", DEFAULT_MAX_PLAYER_STATE_BYTES),
            )
        }
    }
}
