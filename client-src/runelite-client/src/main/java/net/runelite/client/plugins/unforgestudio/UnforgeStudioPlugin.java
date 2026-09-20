package net.runelite.client.plugins.unforgestudio;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientUI;

/**
 * The Unforge development studio.
 *
 * <p>Docks a set of content tools below the game window so content can be built while
 * looking at the running server: live player state, spawning an NPC or object at the
 * current tile, creating interface teleports, browsing the server's Kotlin content
 * modules, and asking the studio's AI router questions without leaving the client.</p>
 *
 * <p>Nothing here writes files directly. Structural changes go through the local
 * content editor, which owns validation, the optimistic SHA-256 lock and snapshots.</p>
 */
@PluginDescriptor(
	name = "Unforge Dev Studio",
	description = "Development dock under the game: spawns, teleports, content source and AI",
	tags = {"unforge", "dev", "content", "studio", "editor"},
	enabledByDefault = true
)
public class UnforgeStudioPlugin extends Plugin implements UnforgeStudioDock.Actions
{
	private static final BufferedImage ICON = createIcon();

	@Inject
	private ClientUI clientUI;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private UnforgeStudioConfig config;

	@Inject
	private ConfigManager configManager;

	private SymbolIndex symbols;
	private StudioApiClient api;
	private UnforgeStudioDock dock;
	private LivePlayerDockPanel livePanel;

	@Provides
	UnforgeStudioConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UnforgeStudioConfig.class);
	}

	@Override
	protected void startUp()
	{
		symbols = new SymbolIndex();
		reloadSymbols();

		api = new StudioApiClient();
		applyApiConfig();

		dock = new UnforgeStudioDock(this);
		livePanel = new LivePlayerDockPanel(client, clientThread, symbols, api, dock);

		dock.addTool("Live", livePanel);
		dock.addTool("Spawn", new SpawnDockPanel(client, clientThread, symbols, api, dock));
		dock.addTool("Teleport", new TeleportDockPanel(client, clientThread, symbols, api, dock));
		dock.addTool("Interface", new InterfaceDockPanel(client, clientThread, symbols, api, dock));
		dock.addTool("AI", new AiDockPanel(client, clientThread, symbols, api, dock));

		applyLayout();
		dock.setStatus(symbols.status());
	}

	@Override
	protected void shutDown()
	{
		if (livePanel != null)
		{
			livePanel.stop();
			livePanel = null;
		}

		if (dock != null)
		{
			dock.dispose();
			dock = null;
		}

		if (clientUI != null && clientUI.getContentDock() != null)
		{
			clientUI.setContentDock(null);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!UnforgeStudioConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("labRoot".equals(event.getKey()))
		{
			reloadSymbols();
		}

		if (dock == null)
		{
			return;
		}

		applyApiConfig();
		applyLayout();
	}

	private void reloadSymbols()
	{
		symbols.reload(config.labRoot());

		if (dock != null)
		{
			dock.setStatus(symbols.status());
		}
	}

	private void applyApiConfig()
	{
		api.configure(config.studioBaseUrl(), config.contentEditorUrl(), config.contentEditorToken());
	}

	private void applyLayout()
	{
		boolean enabled = config.dockEnabled();
		boolean alreadyDocked = clientUI.getContentDock() == dock;

		if (enabled && !alreadyDocked)
		{
			clientUI.setContentDock(dock);
		}
		else if (!enabled && alreadyDocked)
		{
			clientUI.setContentDock(null);
		}

		if (enabled)
		{
			clientUI.setContentDockHeight(config.dockHeight());
		}

		// 225 is the client's own default; only override when the user widens it.
		int sidebarWidth = config.sidebarWidth();
		clientUI.setSidebarWidthOverride(sidebarWidth > 225 ? sidebarWidth : -1);
	}

	// ------------------------------------------------------ dock callbacks

	@Override
	public void onHeightChanged(int height)
	{
		clientUI.setContentDockHeight(height);
	}

	@Override
	public void onHeightCommitted(int height)
	{
		configManager.setConfiguration(UnforgeStudioConfig.GROUP, "dockHeight", String.valueOf(height));
	}

	@Override
	public void onHideRequested()
	{
		configManager.setConfiguration(UnforgeStudioConfig.GROUP, "dockEnabled", "false");
	}

	// ------------------------------------------------------------------ icon

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setColor(new Color(46, 110, 74));
			graphics.fillRoundRect(0, 0, 15, 15, 4, 4);
			graphics.setColor(Color.WHITE);
			graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
			graphics.drawString("DV", 1, 11);
		}
		finally
		{
			graphics.dispose();
		}
		return image;
	}
}
