# Unforge Codex bridge V1

This standalone-main project contains a localhost-only bridge between the bundled RuneLite-style client and the OpenAI Codex API. The client plugin also supports push-to-talk dictation through the bridge.

```text
Unforge client plugin
        │ WebSocket JSON messages + HTTP WAV dictation
        ▼
Unforge Codex bridge :18901 / HTTP :18900
        │ HTTPS Responses API + Audio Transcriptions API
        ▼
OpenAI Codex API (`gpt-5-codex`)
```

The game server remains separate on its existing local r239 port `43594`. The bridge never proxies game traffic and never opens a VPS port. All three local endpoints default to `127.0.0.1`.

## Protocol

The canonical schema is [protocol/unforge-ai-v1.schema.json](../protocol/unforge-ai-v1.schema.json). Every message carries `protocol: "unforge.bridge"` and `version: 1`.

The client submits a task like this:

```json
{"protocol":"unforge.bridge","version":1,"type":"task.submit","taskId":"...","requestId":"...","text":"Missä goblin-spawnit määritellään?"}
```

The bridge emits `task.accepted`, `task.progress`, and then exactly one terminal `task.result`, `task.error`, or `task.cancelled` event for an active task. `task.cancel` is supported from the client and from the bridge HTTP management endpoint. A task is sent to Codex only after the local bridge accepts it.

Progress is intentionally structured. `phase` can be `queued`, `forwarding`, `thinking`, or `output`; `message` is bounded for status display. Codex output is returned only as text. The bridge does not execute returned code and does not expose files, shell commands, credentials, or vision payloads to the client.

## Endpoints

| Endpoint | Purpose |
| --- | --- |
| `ws://127.0.0.1:18901/ws` | Client live task channel |
| `GET http://127.0.0.1:18900/health` | Bridge liveness and active-task count |
| `GET http://127.0.0.1:18900/config` | Sanitized local configuration |
| `POST http://127.0.0.1:18900/v1/dictation` | Send one local WAV recording for transcription |
| `POST http://127.0.0.1:18900/v1/tasks/cancel` | Local task cancellation |
| `POST https://api.openai.com/v1/responses` | Codex task endpoint used only by the bridge |
| `POST https://api.openai.com/v1/audio/transcriptions` | Dictation endpoint used only by the bridge |

## Start locally

1. Set `UNFORGE_OPENAI_API_KEY` or the standard `OPENAI_API_KEY` in the environment of the bridge process. The key is never sent to the client and is not written to this repository.
2. From this standalone-main root, run `tools\standalone\Start-UnforgeAiBridge.bat` or `Start-UnforgeAiBridge.ps1`.
3. Build the bundled client with `client-src\gradlew.bat :client:shadowJar --no-daemon --max-workers=1`.
4. Start local r239 server/config with the existing `tools\standalone\Start-Unforge239-Play.bat`, or start the individual server/config/client scripts.
5. Enable `Unforge AI` in the client plugin list, open its toolbar panel, and send a text message. Press `Sanele` to record a Finnish command; press `Stop dictation` to transcribe and submit it to Codex. The panel reconnects with bounded backoff if the bridge is restarted.

The example configuration is [config/unforge-bridge.env.example](../config/unforge-bridge.env.example). Environment variables only change local ports, timeouts, model selection, and the OpenAI endpoint; the bind rejects non-loopback addresses and the production endpoint is restricted to `https://api.openai.com`.

## Cancellation and timeout boundary

The bridge owns the task timeout. A cancelled or timed-out task is never presented as a completed answer. The bridge does not apply Codex output to files; approval and controlled apply remain a separate later phase.

## Validation

Run `tools\standalone\Smoke-UnforgeAiBridge.ps1`. It starts a fake loopback Codex API, connects a real WebSocket client to the bridge, submits a task, verifies progress and the final response, and checks invalid protocol handling. Client compilation is a separate Gradle check. A live Codex request additionally requires `UNFORGE_OPENAI_API_KEY`.
