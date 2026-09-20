package net.runelite.client.plugins.unforgestudio;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Loopback HTTP client for the two local services the dock talks to.
 *
 * <ul>
 *   <li><b>Studio</b> (default {@code http://127.0.0.1:8787}) - owns AI routing and the
 *       bridge read model. No token.</li>
 *   <li><b>Content editor</b> (default {@code http://127.0.0.1:18930}) - the only
 *       sanctioned writer for spawn content. Every call needs {@code X-Editor-Token}.</li>
 * </ul>
 *
 * <p>Uses the JDK HTTP client, so no new dependency and no change to the build's
 * dependency verification metadata. Every URL is re-checked to be loopback: the dock
 * must never be able to reach off-box.</p>
 */
public class StudioApiClient
{
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration LONG_TIMEOUT = Duration.ofSeconds(180);

	private final Gson gson = new Gson();

	private volatile String studioBaseUrl = "http://127.0.0.1:8787";
	private volatile String editorBaseUrl = "http://127.0.0.1:18930";
	private volatile String editorToken = "";

	public void configure(String studioBaseUrl, String editorBaseUrl, String editorToken)
	{
		this.studioBaseUrl = trimTrailingSlash(studioBaseUrl);
		this.editorBaseUrl = trimTrailingSlash(editorBaseUrl);
		this.editorToken = editorToken == null ? "" : editorToken.trim();
	}

	public boolean hasEditorToken()
	{
		return !editorToken.isEmpty();
	}

	public String studioBaseUrl()
	{
		return studioBaseUrl;
	}

	public String editorBaseUrl()
	{
		return editorBaseUrl;
	}

	// ------------------------------------------------------------------ studio

	public ApiResult studioGet(String path)
	{
		return request(studioBaseUrl + path, "GET", null, null, DEFAULT_TIMEOUT);
	}

	public ApiResult studioPost(String path, JsonObject body)
	{
		return request(studioBaseUrl + path, "POST", body, null, DEFAULT_TIMEOUT);
	}

	/** For endpoints that call an AI provider and therefore take much longer. */
	public ApiResult studioPostLong(String path, JsonObject body)
	{
		return request(studioBaseUrl + path, "POST", body, null, LONG_TIMEOUT);
	}

	// ----------------------------------------------------------- content editor

	public ApiResult editorGet(String path)
	{
		return request(editorBaseUrl + path, "GET", null, editorToken, DEFAULT_TIMEOUT);
	}

	public ApiResult editorPost(String path, JsonObject body)
	{
		return request(editorBaseUrl + path, "POST", body, editorToken, DEFAULT_TIMEOUT);
	}

	// ------------------------------------------------------------------ request

	private ApiResult request(String url, String method, JsonObject body, String token, Duration timeout)
	{
		if (!isLoopback(url))
		{
			return ApiResult.failure(0, "Refused: only loopback URLs are allowed (" + url + ")");
		}

		HttpURLConnection connection = null;
		try
		{
			URL target = URI.create(url).toURL();
			connection = (HttpURLConnection) target.openConnection();
			connection.setInstanceFollowRedirects(false);
			connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
			connection.setReadTimeout((int) timeout.toMillis());
			connection.setRequestMethod(method);
			connection.setRequestProperty("Accept", "application/json");
			connection.setRequestProperty("User-Agent", "Unforge-Studio-Dock");
			if (token != null && !token.isEmpty())
			{
				connection.setRequestProperty("X-Editor-Token", token);
			}
			if (body != null)
			{
				connection.setDoOutput(true);
				connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
				try (OutputStream output = connection.getOutputStream())
				{
					output.write(gson.toJson(body).getBytes(StandardCharsets.UTF_8));
				}
			}

			int status = connection.getResponseCode();
			InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
			String text = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);

			JsonObject json = null;
			if (!text.isEmpty())
			{
				try
				{
					JsonElement parsed = new JsonParser().parse(text);
					if (parsed.isJsonObject())
					{
						json = parsed.getAsJsonObject();
					}
				}
				catch (RuntimeException ignored)
				{
					json = null;
				}
			}

			if (status >= 200 && status < 300)
			{
				return ApiResult.success(status, json);
			}

			return ApiResult.failure(status, describeFailure(status, json, text));
		}
		catch (Exception ex)
		{
			return ApiResult.failure(0, "Request failed: " + ex.getClass().getSimpleName()
				+ (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
		}
		finally
		{
			if (connection != null)
			{
				connection.disconnect();
			}
		}
	}

	private static String describeFailure(int status, JsonObject json, String text)
	{
		if (json != null)
		{
			JsonElement error = json.get("error");
			if (error != null && !error.isJsonNull())
			{
				JsonElement message = json.get("message");
				String detail = message != null && !message.isJsonNull() ? message.getAsString() : null;
				return "HTTP " + status + ": " + error.getAsString() + (detail == null ? "" : " - " + detail);
			}
		}

		String trimmed = text == null ? "" : text.trim();
		if (trimmed.length() > 200)
		{
			trimmed = trimmed.substring(0, 200) + "...";
		}

		return "HTTP " + status + (trimmed.isEmpty() ? "" : ": " + trimmed);
	}

	/**
	 * True when the URL is plain HTTP and points at the loopback interface. Mirrors
	 * the check the studio applies to inbound requests, so both ends agree.
	 */
	public static boolean isLoopback(String url)
	{
		try
		{
			URI uri = URI.create(url);
			if (!"http".equalsIgnoreCase(uri.getScheme()))
			{
				return false;
			}

			String host = uri.getHost();
			if (host == null)
			{
				return false;
			}

			String normalized = host.toLowerCase(Locale.ROOT);
			return "127.0.0.1".equals(normalized) || "localhost".equals(normalized) || "::1".equals(normalized);
		}
		catch (IllegalArgumentException ex)
		{
			return false;
		}
	}

	private static String trimTrailingSlash(String value)
	{
		if (value == null)
		{
			return "";
		}

		String trimmed = value.trim();
		while (trimmed.endsWith("/"))
		{
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}

	/** Outcome of one call: either a parsed body or an explainable error. */
	public static final class ApiResult
	{
		private final boolean ok;
		private final int status;
		private final JsonObject json;
		private final String error;

		private ApiResult(boolean ok, int status, JsonObject json, String error)
		{
			this.ok = ok;
			this.status = status;
			this.json = json;
			this.error = error;
		}

		static ApiResult success(int status, JsonObject json)
		{
			return new ApiResult(true, status, json, null);
		}

		static ApiResult failure(int status, String error)
		{
			return new ApiResult(false, status, null, error);
		}

		public boolean ok()
		{
			return ok;
		}

		public int status()
		{
			return status;
		}

		/** Parsed body, or null when the response was empty or not an object. */
		public JsonObject json()
		{
			return json;
		}

		public String error()
		{
			return error;
		}

		public String string(String name)
		{
			if (json == null || !json.has(name) || json.get(name).isJsonNull())
			{
				return null;
			}
			return json.get(name).getAsString();
		}

		public boolean bool(String name, boolean fallback)
		{
			if (json == null || !json.has(name) || json.get(name).isJsonNull())
			{
				return fallback;
			}
			try
			{
				return json.get(name).getAsBoolean();
			}
			catch (RuntimeException ex)
			{
				return fallback;
			}
		}
	}
}
