package net.runelite.client.plugins.unforgedev;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class UnforgeAiDispatcher
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final File PROVIDERS_ENV = new File("C:/CrownWield/providers.env");

	@Getter
	public static class ProviderEntry
	{
		private final String id;
		private final String displayName;
		private final String endpoint;
		private final String model;
		private final String apiKey;
		private final boolean isFree;

		public ProviderEntry(String id, String displayName, String endpoint, String model, String apiKey, boolean isFree)
		{
			this.id = id;
			this.displayName = displayName;
			this.endpoint = endpoint;
			this.model = model;
			this.apiKey = apiKey;
			this.isFree = isFree;
		}

		@Override
		public String toString()
		{
			return displayName + (isFree ? " (Free)" : " (Paid/Credit)");
		}
	}

	private final OkHttpClient httpClient;
	private final Gson gson = new Gson();
	private final Map<String, String> envKeys = new HashMap<>();
	private final List<ProviderEntry> availableProviders = new ArrayList<>();

	public UnforgeAiDispatcher(OkHttpClient httpClient)
	{
		this.httpClient = httpClient;
		reloadKeys();
	}

	public synchronized void reloadKeys()
	{
		envKeys.clear();
		availableProviders.clear();

		if (PROVIDERS_ENV.exists())
		{
			try
			{
				List<String> lines = Files.readAllLines(PROVIDERS_ENV.toPath(), StandardCharsets.UTF_8);
				for (String line : lines)
				{
					String trimmed = line.trim();
					if (trimmed.isEmpty() || trimmed.startsWith("#"))
					{
						continue;
					}
					int eq = trimmed.indexOf('=');
					if (eq > 0)
					{
						String k = trimmed.substring(0, eq).trim();
						String v = trimmed.substring(eq + 1).trim();
						envKeys.put(k.toUpperCase(), v);
						envKeys.put(k.toLowerCase(), v);
						envKeys.put(k, v);
					}
				}
			}
			catch (IOException e)
			{
				log.warn("Failed to read providers.env", e);
			}
		}

		// Configure providers based on discovered keys
		String groqKey = getKey("GROQ_API_KEY");
		if (groqKey != null && !groqKey.isEmpty())
		{
			availableProviders.add(new ProviderEntry("groq", "Groq (Llama 3.3 70B)", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile", groqKey, true));
		}

		String cerebrasKey = getKey("CEREBRAS_API_KEY");
		if (cerebrasKey != null && !cerebrasKey.isEmpty())
		{
			availableProviders.add(new ProviderEntry("cerebras", "Cerebras (Llama 3.3 70B)", "https://api.cerebras.ai/v1/chat/completions", "llama3.3-70b", cerebrasKey, true));
		}

		String sambanovaKey = getKey("SAMBANOVA_API_KEY");
		if (sambanovaKey != null && !sambanovaKey.isEmpty())
		{
			availableProviders.add(new ProviderEntry("sambanova", "SambaNova (Llama 3.3 70B)", "https://api.sambanova.ai/v1/chat/completions", "Meta-Llama-3.3-70B-Instruct", sambanovaKey, true));
		}

		String mistralKey = getKey("MISTRAL_API_KEY");
		if (mistralKey != null && !mistralKey.isEmpty())
		{
			availableProviders.add(new ProviderEntry("mistral", "Mistral (Mistral Small)", "https://api.mistral.ai/v1/chat/completions", "mistral-small-latest", mistralKey, true));
		}

		String geminiKey = getKey("GEMINI_API_KEY");
		if (geminiKey != null && !geminiKey.isEmpty())
		{
			availableProviders.add(new ProviderEntry("gemini", "Google Gemini (2.0 Flash)", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-2.0-flash", geminiKey, true));
		}

		String cmdKey = getKey("COMMAND_CODE_API_KEY");
		if (cmdKey != null && !cmdKey.isEmpty())
		{
			availableProviders.add(new ProviderEntry("command_code", "Command Code (DeepSeek v4 Flash)", "https://api.commandcode.ai/provider/v1/chat/completions", "deepseek/deepseek-v4-flash", cmdKey, false));
		}

		String openRouterKey = getKey("OPENROUTER_API_KEY");
		if (openRouterKey != null && !openRouterKey.isEmpty())
		{
			availableProviders.add(new ProviderEntry("openrouter", "OpenRouter (DeepSeek v4 Flash)", "https://openrouter.ai/api/v1/chat/completions", "deepseek/deepseek-v4-flash-0731", openRouterKey, false));
		}

		String deepseekKey = getKey("DEEPSEEK_API_KEY");
		if (deepseekKey != null && !deepseekKey.isEmpty())
		{
			availableProviders.add(new ProviderEntry("deepseek", "DeepSeek Official (Chat)", "https://api.deepseek.com/chat/completions", "deepseek-chat", deepseekKey, false));
		}
	}

	private String getKey(String name)
	{
		String val = envKeys.get(name);
		if (val == null)
		{
			val = envKeys.get(name.toUpperCase());
		}
		if (val == null)
		{
			val = envKeys.get(name.toLowerCase());
		}
		return val;
	}

	public List<ProviderEntry> getAvailableProviders()
	{
		return new ArrayList<>(availableProviders);
	}

	public CompletableFuture<String> askAi(String providerId, String systemPrompt, String userPrompt)
	{
		return CompletableFuture.supplyAsync(() ->
		{
			if ("auto".equalsIgnoreCase(providerId))
			{
				for (ProviderEntry p : availableProviders)
				{
					if (p.isFree())
					{
						try
						{
							return executeCall(p, systemPrompt, userPrompt);
						}
						catch (Exception e)
						{
							log.warn("Free provider {} failed, trying next", p.displayName, e);
						}
					}
				}
				for (ProviderEntry p : availableProviders)
				{
					if (!p.isFree())
					{
						try
						{
							return executeCall(p, systemPrompt, userPrompt);
						}
						catch (Exception e)
						{
							log.warn("Paid fallback {} failed", p.displayName, e);
						}
					}
				}
				throw new RuntimeException("All configured AI providers failed. Check C:/CrownWield/providers.env");
			}

			for (ProviderEntry p : availableProviders)
			{
				if (p.getId().equalsIgnoreCase(providerId))
				{
					return executeCall(p, systemPrompt, userPrompt);
				}
			}

			throw new RuntimeException("Selected provider " + providerId + " is not configured or missing API key.");
		});
	}

	private String executeCall(ProviderEntry provider, String systemPrompt, String userPrompt)
	{
		JsonObject root = new JsonObject();
		root.addProperty("model", provider.getModel());
		root.addProperty("temperature", 0.2);

		JsonArray messages = new JsonArray();
		if (systemPrompt != null && !systemPrompt.isEmpty())
		{
			JsonObject sys = new JsonObject();
			sys.addProperty("role", "system");
			sys.addProperty("content", systemPrompt);
			messages.add(sys);
		}

		JsonObject user = new JsonObject();
		user.addProperty("role", "user");
		user.addProperty("content", userPrompt);
		messages.add(user);

		root.add("messages", messages);

		RequestBody body = RequestBody.create(JSON, gson.toJson(root));
		Request.Builder reqBuilder = new Request.Builder()
			.url(provider.getEndpoint())
			.header("Authorization", "Bearer " + provider.getApiKey())
			.header("Content-Type", "application/json")
			.post(body);

		if ("openrouter".equalsIgnoreCase(provider.getId()))
		{
			reqBuilder.header("HTTP-Referer", "https://unforge.kronos.rsps");
			reqBuilder.header("X-Title", "Unforge Dev Tools");
		}

		try (Response response = httpClient.newCall(reqBuilder.build()).execute())
		{
			if (!response.isSuccessful() || response.body() == null)
			{
				String errBody = response.body() != null ? response.body().string() : "Empty response";
				throw new RuntimeException("HTTP " + response.code() + " from " + provider.getDisplayName() + ": " + errBody);
			}

			String respString = response.body().string();
			JsonObject respJson = new JsonParser().parse(respString).getAsJsonObject();
			JsonArray choices = respJson.getAsJsonArray("choices");
			if (choices != null && choices.size() > 0)
			{
				JsonObject first = choices.get(0).getAsJsonObject();
				JsonObject msg = first.getAsJsonObject("message");
				if (msg != null && msg.has("content"))
				{
					return msg.get("content").getAsString();
				}
			}
			return respString;
		}
		catch (IOException e)
		{
			throw new RuntimeException("Network error contacting " + provider.getDisplayName() + ": " + e.getMessage(), e);
		}
	}
}
