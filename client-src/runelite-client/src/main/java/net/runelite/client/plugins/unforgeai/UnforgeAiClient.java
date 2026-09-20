package net.runelite.client.plugins.unforgeai;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

@Slf4j
final class UnforgeAiClient extends WebSocketListener implements AutoCloseable
{
	private static final String PROTOCOL = "unforge.bridge";
	private static final int VERSION = 1;
	private static final int MAX_TEXT_CHARS = 8000;
	private static final long MAX_RECONNECT_DELAY_MS = 10000L;

	private final okhttp3.OkHttpClient httpClient;
	private final UnforgeAiConfig config;
	private final Listener listener;
	private final Gson gson = new Gson();
	private final String clientId = "client-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r ->
	{
		Thread thread = new Thread(r, "unforge-ai-reconnect");
		thread.setDaemon(true);
		return thread;
	});
	private final AtomicBoolean reconnectScheduled = new AtomicBoolean();

	private volatile WebSocket webSocket;
	private volatile boolean stopping;
	private volatile String activeTaskId;
	private int reconnectAttempt;

	UnforgeAiClient(okhttp3.OkHttpClient httpClient, UnforgeAiConfig config, Listener listener)
	{
		this.httpClient = httpClient;
		this.config = config;
		this.listener = listener;
	}

	/** Stable id for this client process, used to key player state on the bridge. */
	String clientId()
	{
		return clientId;
	}

	void connect()
	{
		stopping = false;
		if (!isLoopbackWebSocketUrl(config.websocketUrl()))
		{
			listener.onClientError("Bridge URL must be ws://127.0.0.1 or ws://localhost.");
			return;
		}
		if (webSocket != null)
		{
			return;
		}

		try
		{
			Request request = new Request.Builder()
				.url(config.websocketUrl())
				.header("User-Agent", "Unforge-239-Client")
				.build();
			webSocket = httpClient.newWebSocket(request, this);
			listener.onConnectionState("connecting");
		}
		catch (IllegalArgumentException ex)
		{
			listener.onClientError("Invalid bridge URL: " + ex.getMessage());
		}
	}

	void reconnect()
	{
		WebSocket current = webSocket;
		webSocket = null;
		if (current != null)
		{
			current.cancel();
		}
		connect();
	}

	void submit(String text)
	{
		String normalized = text == null ? "" : text.trim();
		if (normalized.isEmpty())
		{
			return;
		}
		if (normalized.length() > MAX_TEXT_CHARS)
		{
			listener.onClientError("Message is longer than the local 8000 character limit.");
			return;
		}
		if (activeTaskId != null)
		{
			listener.onClientError("Wait for the active task to finish or cancel it first.");
			return;
		}

		WebSocket current = webSocket;
		if (current == null)
		{
			listener.onClientError("Bridge is not connected.");
			connect();
			return;
		}

		String taskId = UUID.randomUUID().toString();
		String requestId = UUID.randomUUID().toString();
		JsonObject message = event("task.submit", taskId, requestId);
		message.addProperty("text", normalized);
		message.addProperty("source", "unforge-239-client");
		activeTaskId = taskId;
		if (!current.send(gson.toJson(message)))
		{
			activeTaskId = null;
			listener.onClientError("Bridge rejected the task because the socket is closing.");
		}
	}

	void cancelActiveTask()
	{
		String taskId = activeTaskId;
		WebSocket current = webSocket;
		if (taskId == null || current == null)
		{
			return;
		}

		JsonObject message = event("task.cancel", taskId, UUID.randomUUID().toString());
		current.send(gson.toJson(message));
	}

	/**
	 * Publishes one player snapshot to the local bridge. Studio agents subscribe to
	 * these frames; the bridge also keeps the newest snapshot per client so HTTP
	 * consumers can read it without a WebSocket.
	 *
	 * @return {@code false} when the bridge is not connected, so the caller can skip
	 *   work rather than queue stale state.
	 */
	boolean sendPlayerState(JsonObject state)
	{
		return sendRelay("player.state", message ->
		{
			message.addProperty("clientId", clientId);
			message.add("state", state);
		});
	}

	/** Publishes a discrete player event such as a login, logout or level-up. */
	boolean sendPlayerEvent(String event, JsonObject data)
	{
		if (event == null || event.isEmpty())
		{
			return false;
		}
		return sendRelay("player.event", message ->
		{
			message.addProperty("clientId", clientId);
			message.addProperty("event", event);
			if (data != null)
			{
				message.add("data", data);
			}
		});
	}

	private boolean sendRelay(String type, java.util.function.Consumer<JsonObject> decorate)
	{
		WebSocket current = webSocket;
		if (current == null)
		{
			return false;
		}
		JsonObject message = event(type, null, null);
		decorate.accept(message);
		return current.send(gson.toJson(message));
	}

	void transcribeAudio(byte[] wavBytes)
	{
		if (wavBytes == null || wavBytes.length == 0)
		{
			listener.onDictationError("Dictation did not contain any audio.");
			return;
		}
		if (!isLoopbackHttpUrl(config.httpBaseUrl()))
		{
			listener.onDictationError("Gateway HTTP URL must be http://127.0.0.1 or http://localhost.");
			return;
		}

		Request request;
		try
		{
			request = new Request.Builder()
				.url(endpoint(config.httpBaseUrl(), "/v1/dictation"))
				.header("User-Agent", "Unforge-239-Client")
				.post(RequestBody.create(MediaType.get("audio/wav"), wavBytes))
				.build();
		}
		catch (IllegalArgumentException ex)
		{
			listener.onDictationError("Invalid gateway HTTP URL: " + ex.getMessage());
			return;
		}

		listener.onDictationState("transcribing");
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException ex)
			{
				listener.onDictationError("Dictation request failed: " + safeMessage(ex));
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					String body = ignored.body() == null ? "" : ignored.body().string();
					if (!ignored.isSuccessful())
					{
						listener.onDictationError("Gateway rejected dictation (HTTP " + ignored.code() + ").");
						return;
					}
					try
					{
						JsonObject message = new JsonParser().parse(body).getAsJsonObject();
						String text = getString(message, "text");
						if (text == null)
						{
							listener.onDictationError("Gateway returned no dictated text.");
							return;
						}
						listener.onDictationText(text);
					}
					catch (RuntimeException ex)
					{
						listener.onDictationError("Gateway returned invalid dictation JSON.");
					}
				}
			}
		});
	}

	boolean hasActiveTask()
	{
		return activeTaskId != null;
	}

	boolean isConnected()
	{
		return webSocket != null;
	}

	@Override
	public void onOpen(WebSocket webSocket, Response response)
	{
		this.webSocket = webSocket;
		reconnectAttempt = 0;
		listener.onConnectionState("connected");
		JsonObject hello = event("connection.hello", null, UUID.randomUUID().toString());
		hello.addProperty("client", "unforge-239-client");
		hello.addProperty("role", "client");
		hello.addProperty("clientId", clientId);
		webSocket.send(gson.toJson(hello));
	}

	@Override
	public void onMessage(WebSocket webSocket, String text)
	{
		try
		{
			JsonElement parsed = new JsonParser().parse(text);
			if (!parsed.isJsonObject())
			{
				throw new IllegalArgumentException("Bridge message was not an object");
			}
			JsonObject message = parsed.getAsJsonObject();
			if (!PROTOCOL.equals(getString(message, "protocol"))
				|| VERSION != getInt(message, "version", -1))
			{
				throw new IllegalArgumentException("Unsupported bridge protocol or version");
			}

			String type = getString(message, "type");
			if ("task.result".equals(type) || "task.error".equals(type) || "task.cancelled".equals(type))
			{
				String taskId = getString(message, "taskId");
				if (taskId == null || taskId.equals(activeTaskId))
				{
					activeTaskId = null;
				}
			}
			listener.onMessage(message);
		}
		catch (RuntimeException ex)
		{
			listener.onClientError("Invalid message from bridge: " + ex.getMessage());
		}
	}

	@Override
	public void onFailure(WebSocket webSocket, Throwable throwable, Response response)
	{
		if (this.webSocket == webSocket)
		{
			this.webSocket = null;
		}
		listener.onConnectionState("disconnected");
		listener.onClientError("Bridge connection failed: " + safeMessage(throwable));
		scheduleReconnect();
	}

	@Override
	public void onClosed(WebSocket webSocket, int code, String reason)
	{
		if (this.webSocket == webSocket)
		{
			this.webSocket = null;
		}
		listener.onConnectionState("disconnected");
		scheduleReconnect();
	}

	@Override
	public void close()
	{
		stopping = true;
		WebSocket current = webSocket;
		webSocket = null;
		if (current != null)
		{
			current.close(1000, "Plugin stopped");
		}
		scheduler.shutdownNow();
		activeTaskId = null;
	}

	static boolean isLoopbackWebSocketUrl(String value)
	{
		try
		{
			URI uri = URI.create(value);
			return "ws".equalsIgnoreCase(uri.getScheme())
				&& ("127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost()))
				&& uri.getPort() >= 1 && uri.getPort() <= 65535
				&& (uri.getRawQuery() == null || uri.getRawQuery().isEmpty())
				&& (uri.getRawFragment() == null || uri.getRawFragment().isEmpty());
		}
		catch (IllegalArgumentException ex)
		{
			return false;
		}
	}

	static boolean isLoopbackHttpUrl(String value)
	{
		try
		{
			URI uri = URI.create(value);
			return "http".equalsIgnoreCase(uri.getScheme())
				&& ("127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost()))
				&& uri.getPort() >= 1 && uri.getPort() <= 65535
				&& (uri.getRawQuery() == null || uri.getRawQuery().isEmpty())
				&& (uri.getRawFragment() == null || uri.getRawFragment().isEmpty());
		}
		catch (IllegalArgumentException ex)
		{
			return false;
		}
	}

	private void scheduleReconnect()
	{
		if (stopping || reconnectScheduled.getAndSet(true))
		{
			return;
		}
		long delay = Math.min(MAX_RECONNECT_DELAY_MS, 500L << Math.min(reconnectAttempt++, 5));
		scheduler.schedule(() ->
		{
			reconnectScheduled.set(false);
			if (!stopping)
			{
				connect();
			}
		}, delay, TimeUnit.MILLISECONDS);
	}

	private static JsonObject event(String type, String taskId, String requestId)
	{
		JsonObject message = new JsonObject();
		message.addProperty("protocol", PROTOCOL);
		message.addProperty("version", VERSION);
		message.addProperty("type", type);
		if (taskId != null)
		{
			message.addProperty("taskId", taskId);
		}
		if (requestId != null)
		{
			message.addProperty("requestId", requestId);
		}
		return message;
	}

	private static String getString(JsonObject object, String name)
	{
		if (!object.has(name) || object.get(name).isJsonNull())
		{
			return null;
		}
		String value = object.get(name).getAsString().trim();
		return value.isEmpty() ? null : value;
	}

	private static String endpoint(String baseUrl, String path)
	{
		return baseUrl.replaceAll("/+$", "") + path;
	}

	private static int getInt(JsonObject object, String name, int fallback)
	{
		try
		{
			return object.has(name) ? object.get(name).getAsInt() : fallback;
		}
		catch (RuntimeException ex)
		{
			return fallback;
		}
	}

	private static String safeMessage(Throwable throwable)
	{
		String message = throwable.getMessage();
		return message == null || message.isEmpty() ? throwable.getClass().getSimpleName() : message;
	}

	interface Listener
	{
		void onConnectionState(String state);

		void onMessage(JsonObject message);

		void onClientError(String message);

		void onDictationState(String state);

		void onDictationText(String text);

		void onDictationError(String message);
	}
}
