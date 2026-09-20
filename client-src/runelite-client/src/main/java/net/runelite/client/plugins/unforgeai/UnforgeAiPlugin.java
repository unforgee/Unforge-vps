package net.runelite.client.plugins.unforgeai;

import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import okhttp3.OkHttpClient;

@PluginDescriptor(
	name = "Unforge AI",
	configName = "UnforgeAiPlugin",
	description = "Local Codex coding assistant with dictation and live player state",
	tags = {"ai", "unforge", "assistant", "local"},
	enabledByDefault = true
)
public class UnforgeAiPlugin extends Plugin
{
	private static final BufferedImage ICON = createIcon();
	private static final long STATE_TICK_MS = 250L;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private UnforgeAiConfig config;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	private UnforgeAiPanel panel;
	private UnforgeAiClient bridgeClient;
	private NavigationButton navigationButton;

	private ScheduledExecutorService stateScheduler;
	private final AtomicBoolean publishScheduled = new AtomicBoolean();
	private volatile boolean stateDirty;
	private volatile String lastPayload;
	private volatile long lastSentAt;

	@Provides
	UnforgeAiConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UnforgeAiConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = injector.getInstance(UnforgeAiPanel.class);
		bridgeClient = new UnforgeAiClient(okHttpClient, config, panel);
		panel.init(bridgeClient);

		navigationButton = NavigationButton.builder()
			.icon(ICON)
			.tooltip("Unforge AI")
			.priority(11)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);

		if (config.autoConnect())
		{
			bridgeClient.connect();
		}

		startStatePublisher();
	}

	@Override
	protected void shutDown()
	{
		stopStatePublisher();
		if (bridgeClient != null && bridgeClient.isConnected())
		{
			bridgeClient.sendPlayerEvent("shutdown", null);
		}

		if (panel != null)
		{
			panel.close();
		}
		if (bridgeClient != null)
		{
			bridgeClient.close();
			bridgeClient = null;
		}
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}
		panel = null;
		lastPayload = null;
	}

	/**
	 * Player state is captured at most on change or on the configured keepalive
	 * interval, so the client thread never runs a serialization it does not need.
	 */
	private void startStatePublisher()
	{
		stateScheduler = Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread thread = new Thread(r, "unforge-ai-player-state");
			thread.setDaemon(true);
			return thread;
		});
		stateScheduler.scheduleWithFixedDelay(this::requestPublish, STATE_TICK_MS, STATE_TICK_MS, TimeUnit.MILLISECONDS);
	}

	private void stopStatePublisher()
	{
		if (stateScheduler != null)
		{
			stateScheduler.shutdownNow();
			stateScheduler = null;
		}
		publishScheduled.set(false);
	}

	private void requestPublish()
	{
		if (!config.publishPlayerState())
		{
			return;
		}
		if (!publishScheduled.compareAndSet(false, true))
		{
			return;
		}
		clientThread.invokeLater(this::publishPlayerState);
	}

	private void publishPlayerState()
	{
		publishScheduled.set(false);

		UnforgeAiClient bridge = bridgeClient;
		if (bridge == null || !bridge.isConnected())
		{
			return;
		}

		boolean changed = stateDirty;
		boolean keepaliveDue = System.currentTimeMillis() - lastSentAt >= config.playerStateIntervalMs();
		if (!changed && !keepaliveDue)
		{
			return;
		}

		JsonObject state = UnforgeAiPlayerState.capture(client, config.includeInventory());
		if (state == null)
		{
			return;
		}

		String payload = state.toString();
		stateDirty = false;
		if (!changed && payload.equals(lastPayload))
		{
			lastSentAt = System.currentTimeMillis();
			return;
		}

		lastPayload = payload;
		lastSentAt = System.currentTimeMillis();
		bridge.sendPlayerState(state);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			sendPlayerEvent("login", null);
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			sendPlayerEvent(state == GameState.HOPPING ? "hop" : "logout", null);
			lastPayload = null;
		}
		stateDirty = true;
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		stateDirty = true;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.INV || id == InventoryID.WORN)
		{
			stateDirty = true;
		}
	}

	private void sendPlayerEvent(String event, JsonObject data)
	{
		UnforgeAiClient bridge = bridgeClient;
		if (bridge != null && bridge.isConnected())
		{
			bridge.sendPlayerEvent(event, data);
		}
	}

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setColor(new Color(56, 128, 184));
			graphics.fillRoundRect(0, 0, 15, 15, 4, 4);
			graphics.setColor(Color.WHITE);
			graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
			graphics.drawString("AI", 2, 11);
		}
		finally
		{
			graphics.dispose();
		}
		return image;
	}
}
