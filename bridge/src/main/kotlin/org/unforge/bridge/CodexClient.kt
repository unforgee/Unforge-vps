package org.unforge.bridge

import com.fasterxml.jackson.databind.JsonNode
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

class CodexClient(private val config: BridgeConfig) {
    private val client =
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(config.connectTimeoutMs)).build()

    fun submit(
        taskId: String,
        requestId: String,
        text: String,
        onEvent: (JsonNode) -> Unit,
    ): Boolean {
        requireConfigured()
        val body =
            BridgeProtocol.mapper.createObjectNode().apply {
                put("model", config.codexModel)
                put("instructions", CODEX_INSTRUCTIONS)
                put("input", text)
                put("store", false)
            }
        val request =
            HttpRequest.newBuilder(URI("${config.openAiBaseUrl}/v1/responses"))
                .timeout(Duration.ofMillis(config.requestTimeoutMs))
                .header("Authorization", "Bearer ${config.openAiApiKey}")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        BridgeProtocol.mapper.writeValueAsString(body)
                    )
                )
                .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IOException(
                "Codex API returned HTTP ${response.statusCode()}: ${errorMessage(response.body())}"
            )
        }

        val responseNode =
            try {
                BridgeProtocol.parse(response.body())
            } catch (error: Exception) {
                throw IOException("Codex returned invalid JSON", error)
            }
        val output =
            extractOutputText(responseNode)
                ?: throw IOException("Codex response did not contain output text")
        onEvent(
            BridgeProtocol.parse(
                BridgeProtocol.event(
                    "task.result",
                    taskId,
                    requestId,
                    mapOf("status" to "completed", "text" to output, "source" to "codex"),
                )
            )
        )
        return true
    }

    fun transcribe(wavBytes: ByteArray): String {
        requireConfigured()
        if (wavBytes.isEmpty()) {
            throw IOException("Dictation audio was empty")
        }

        val boundary = "----unforge-${UUID.randomUUID()}"
        val request =
            HttpRequest.newBuilder(URI("${config.openAiBaseUrl}/v1/audio/transcriptions"))
                .timeout(Duration.ofMillis(config.requestTimeoutMs))
                .header("Authorization", "Bearer ${config.openAiApiKey}")
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(wavBytes, boundary)))
                .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IOException(
                "Transcription API returned HTTP ${response.statusCode()}: ${errorMessage(response.body())}"
            )
        }
        val responseNode =
            try {
                BridgeProtocol.parse(response.body())
            } catch (error: Exception) {
                throw IOException("Transcription API returned invalid JSON", error)
            }
        return responseNode.path("text").asText("").trim().takeUnless { it.isEmpty() }
            ?: throw IOException("Transcription API returned no text")
    }

    private fun requireConfigured() {
        if (config.openAiApiKey.isBlank()) {
            throw IOException("OpenAI API key is not configured")
        }
    }

    private fun multipartBody(wavBytes: ByteArray, boundary: String): ByteArray {
        val output = ByteArrayOutputStream(wavBytes.size + 1_024)
        fun text(value: String) = output.write(value.toByteArray(StandardCharsets.UTF_8))

        text("--$boundary\r\n")
        text("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
        text(config.transcriptionModel)
        text("\r\n")

        text("--$boundary\r\n")
        text("Content-Disposition: form-data; name=\"language\"\r\n\r\nfi\r\n")

        text("--$boundary\r\n")
        text("Content-Disposition: form-data; name=\"prompt\"\r\n\r\n")
        text(TRANSCRIPTION_PROMPT)
        text("\r\n")

        text("--$boundary\r\n")
        text("Content-Disposition: form-data; name=\"file\"; filename=\"dictation.wav\"\r\n")
        text("Content-Type: audio/wav\r\n\r\n")
        output.write(wavBytes)
        text("\r\n--$boundary--\r\n")
        return output.toByteArray()
    }

    private fun extractOutputText(node: JsonNode): String? {
        node
            .path("output_text")
            .asText("")
            .trim()
            .takeUnless { it.isEmpty() }
            ?.let {
                return it
            }
        node.path("output").forEach { item ->
            item.path("content").forEach { content ->
                content
                    .path("text")
                    .asText("")
                    .trim()
                    .takeUnless { it.isEmpty() }
                    ?.let {
                        return it
                    }
            }
        }
        return null
    }

    private fun errorMessage(body: String): String {
        return try {
            BridgeProtocol.parse(body)
                .path("error")
                .path("message")
                .asText("")
                .trim()
                .take(300)
                .takeUnless { it.isEmpty() } ?: "request failed"
        } catch (_: Exception) {
            "request failed"
        }
    }

    companion object {
        private val CODEX_INSTRUCTIONS =
            """
            You are the Codex coding assistant for the local Unforge r239 project.
            Analyze the user's request against the existing project context when it is provided.
            Return a concise, actionable answer in Finnish or in the user's language.
            For code changes, explain the affected files, show the proposed change or patch,
            and list the validation to run. Do not claim that files were changed or commands ran.
            Do not invent RuneLite, Kronos, revision, cache, protocol, or gameplay facts.
        """
                .trimIndent()

        private const val TRANSCRIPTION_PROMPT =
            "Unforge, RuneLite, Kronos, RSPS, Java, Kotlin, Gradle, NPC, spawn, client, server, bridge, Codex"
    }
}
