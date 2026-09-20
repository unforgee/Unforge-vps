package net.runelite.client.plugins.unforgedev;

import com.google.inject.Provides;
import java.awt.Color;
import lombok.extern.slf4j.Slf4j;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.ScriptID;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.unforgecommon.UnforgeNpcEditEvent;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

@PluginDescriptor(
	name = "Unforge Dev Tools",
	description = "Instant in-game Entity Inspector, Shop/Combat/Quest Editor, and AI Assistant",
	tags = {"unforge", "dev", "inspector", "shop", "combat", "ai", "tools"},
	enabledByDefault = true
)
@Slf4j
public class UnforgeDevToolsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private UnforgeDevConfig config;

	@Inject
	private UnforgeEntityOverlay entityOverlay;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private EventBus eventBus;

	private UnforgeDevPanel panel;
	private NavigationButton navButton;
	private UnforgeAiDispatcher aiDispatcher;

	@Provides
	UnforgeDevConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UnforgeDevConfig.class);
	}

	@Override
	protected void startUp()
	{
		aiDispatcher = new UnforgeAiDispatcher(okHttpClient);
		panel = new UnforgeDevPanel(this, client, clientThread, config, aiDispatcher);

		BufferedImage icon;
		try
		{
			icon = ImageUtil.loadImageResource(getClass(), "/net/runelite/client/plugins/devtools/devtools_icon.png");
		}
		catch (IllegalArgumentException e)
		{
			icon = createFallbackIcon();
		}

		navButton = NavigationButton.builder()
			.tooltip("Unforge Dev Tools")
			.icon(icon)
			.priority(1)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(entityOverlay);
	}

	@Override
	protected void shutDown()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		overlayManager.remove(entityOverlay);
	}

	private BufferedImage createFallbackIcon()
	{
		BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = img.createGraphics();
		g.setColor(new Color(0, 230, 230));
		g.fillRect(3, 3, 18, 18);
		g.setColor(Color.BLACK);
		g.drawString("DEV", 4, 16);
		g.dispose();
		return img;
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.enableDevRightClick())
		{
			return;
		}

		MenuEntry entry = event.getMenuEntry();
		MenuAction type = entry.getType();
		int identifier = event.getIdentifier();

		// NPC Right-click — add once per menu build, on the examine entry
		if (type == MenuAction.EXAMINE_NPC)
		{
			NPC npc = entry.getNpc();
			if (npc == null)
			{
				WorldView wv = client.getTopLevelWorldView();
				if (wv != null)
				{
					for (NPC n : wv.npcs())
					{
						if (n.getIndex() == identifier)
						{
							npc = n;
							break;
						}
					}
				}
			}

			if (npc != null)
			{
				final NPC targetNpc = npc;
				client.createMenuEntry(-1)
					.setOption("<col=00ffff>[Unforge Dev]</col> Edit NPC")
					.setTarget(event.getTarget())
					.setType(MenuAction.RUNELITE)
					.onClick(e -> openNpcEditor(targetNpc));
			}
		}
		// Object Right-click — add once per menu build, on the examine entry
		else if (type == MenuAction.EXAMINE_OBJECT)
		{
			WorldPoint wp = WorldPoint.fromScene(client, entry.getParam0(), entry.getParam1(), client.getPlane());
			client.createMenuEntry(-1)
				.setOption("<col=ff9800>[Unforge Dev]</col> Edit Object")
				.setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(e -> openObjectEditor(identifier, event.getTarget(), wp));
		}
	}

	private int bankDumpTicks = -1;

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == 12)
		{
			bankDumpTicks = 0;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (bankDumpTicks < 0)
		{
			return;
		}
		if (bankDumpTicks > 3)
		{
			bankDumpTicks = -1;
			return;
		}
		dumpBankWidgets(bankDumpTicks);
		bankDumpTicks++;
	}

	private void dumpBankWidgets(int tick)
	{
		dumpWidget(tick, "items(12:13)", 12, 13);
		dumpWidget(tick, "tabs(12:11)", 12, 11);
		dumpWidget(tick, "bankside(15:3)", 15, 3);
		dumpWidget(tick, "inv(149:0)", 149, 0);
	}

	private void dumpWidget(int tick, String label, int group, int child)
	{
		Widget w = client.getWidget(group, child);
		if (w == null)
		{
			log.info("[bankdump t{}] {} = NULL", tick, label);
			return;
		}
		Widget[] children = w.getChildren();
		int n = children == null ? -1 : children.length;
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("[bankdump t%d] %s type=%d mask=%08x children=%d",
			tick, label, w.getType(), w.getClickMask(), n));
		int shown = 0;
		if (children != null)
		{
			for (int i = 0; i < children.length && shown < 4; i++)
			{
				Widget c = children[i];
				if (c == null)
				{
					continue;
				}
				String[] actions = c.getActions();
				String first = actions != null && actions.length > 0 ? actions[0] : null;
				sb.append(String.format(" | c%d:type=%d mask=%08x op0='%s' item=%d",
					i, c.getType(), c.getClickMask(), first, c.getItemId()));
				shown++;
			}
		}
		log.info(sb.toString());
	}

	public void openNpcEditor(NPC npc)
	{
		// Read game state on the client thread, then hand the npc to the Drop Editor
		// via the shared event bus (it opens its own panel on the EDT).
		int npcId = npc.getId();
		String name = npc.getName();
		int combatLevel = npc.getCombatLevel();
		eventBus.post(new UnforgeNpcEditEvent(npcId, name, combatLevel));
	}

	public void openObjectEditor(int objectId, String name, WorldPoint point)
	{
		SwingUtilities.invokeLater(() ->
		{
			clientToolbar.openPanel(navButton);
			panel.loadObject(objectId, name, point);
		});
	}

	void reopenPanel()
	{
		SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
	}

	public void sendCheat(String command)
	{
		clientThread.invokeLater(() -> client.runScript(ScriptID.UNFORGE_CHEAT, command));
	}
}
