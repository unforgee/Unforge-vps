package net.runelite.client.plugins.unforgespawn;

import java.awt.Color;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.unforgecommon.UnforgeCheats;
import net.runelite.client.plugins.unforgecommon.UnforgeIcons;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Unforge Spawn Editor",
	description = "Standalone NPC/object spawner: search, in-game spawn cheats, spawn JSON list",
	tags = {"unforge", "spawn", "npc", "dev", "tools"},
	enabledByDefault = true
)
public class UnforgeSpawnPlugin extends Plugin
{
	// Packed widget ids: shopmain:items (300:16) and shopside:items (301:0).
	private static final int SHOP_STOCK_WIDGET = (300 << 16) | 16;
	private static final int SHOP_SIDE_WIDGET = 301 << 16;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	private UnforgeSpawnPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		panel = new UnforgeSpawnPanel(this, client, clientThread);
		navButton = NavigationButton.builder()
			.tooltip("Unforge Spawn Editor")
			.icon(UnforgeIcons.letter(new Color(0, 123, 255), "S"))
			.priority(1)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		if (panel != null)
		{
			panel.closePopout();
		}
	}

	void reopenPanel()
	{
		SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}

		MenuEntry entry = event.getMenuEntry();
		MenuAction type = entry.getType();
		int identifier = event.getIdentifier();

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
			if (npc == null)
			{
				return;
			}

			final NPC target = npc;
			final int npcId = npc.getId();
			final String npcName = npc.getName();
			final WorldPoint npcLoc = npc.getWorldLocation();

			client.createMenuEntry(-1)
				.setOption("<col=00ff88>[Spawn]</col> Copy to editor")
				.setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(e -> copyToEditor(npcId, npcName, true, npcLoc));

			client.createMenuEntry(-1)
				.setOption("<col=ff5555>[Spawn]</col> Delete")
				.setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(e -> deleteEntity("npcdel", npcId, npcLoc));
		}
		else if (type == MenuAction.EXAMINE_OBJECT)
		{
			WorldPoint wp = WorldPoint.fromScene(client, entry.getParam0(), entry.getParam1(), client.getPlane());
			final int locId = identifier;
			final String locName = Text.removeTags(event.getTarget());
			final WorldPoint locLoc = wp;
			// getConfig(): bits & 31 = loc shape, bits >>> 6 & 3 = angle - keep them so a copied
			// object respawns looking identical and deletes match the exact loc.
			final int[] shapeAngle = resolveLocConfig(entry.getParam0(), entry.getParam1(), locId);
			final int shape = shapeAngle[0];
			final int angle = shapeAngle[1];

			client.createMenuEntry(-1)
				.setOption("<col=00ff88>[Spawn]</col> Copy to editor")
				.setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(e -> copyToEditor(locId, locName, false, locLoc, shape, angle));

			client.createMenuEntry(-1)
				.setOption("<col=ffaa00>[Spawn]</col> Rotate")
				.setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(e -> rotateLoc(locId, locLoc, shape));

			client.createMenuEntry(-1)
				.setOption("<col=ff5555>[Spawn]</col> Delete")
				.setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(e -> deleteEntity("locdelat", locId, locLoc, shape));
		}
		else if (type == MenuAction.CC_OP && "Examine".equals(event.getOption()))
		{
			// Shift+right-click inside a shop: stock grid (shopmain:items) offers
			// delete/add; the player inventory side (shopside:items) offers add.
			final int packedWidget = entry.getParam1();
			if (packedWidget == SHOP_STOCK_WIDGET)
			{
				final int comsub = entry.getParam0();
				client.createMenuEntry(-1)
					.setOption("<col=ff5555>[Shop]</col> Delete from shop")
					.setTarget(event.getTarget())
					.setType(MenuAction.RUNELITE)
					.onClick(e -> UnforgeCheats.send(clientThread, client, "shopdel " + comsub));

				client.createMenuEntry(-1)
					.setOption("<col=00ff88>[Shop]</col> Add new item")
					.setTarget(event.getTarget())
					.setType(MenuAction.RUNELITE)
					.onClick(e -> promptShopAdd());
			}
			else if (packedWidget == SHOP_SIDE_WIDGET)
			{
				final int itemId = entry.getItemId();
				client.createMenuEntry(-1)
					.setOption("<col=00ff88>[Shop]</col> Add to shop")
					.setTarget(event.getTarget())
					.setType(MenuAction.RUNELITE)
					.onClick(e -> UnforgeCheats.send(clientThread, client, "shopadd " + itemId));
			}
		}
	}

	private void rotateLoc(int locId, WorldPoint loc, int shape)
	{
		if (loc == null)
		{
			return;
		}
		// locrotate <id> <level> <mapX> <mapZ> <localX> <localZ> [shape]
		String cmd = String.format("locrotate %d %d %d %d %d %d",
			locId, loc.getPlane(), loc.getX() >> 6, loc.getY() >> 6, loc.getX() & 63, loc.getY() & 63);
		if (shape >= 0)
		{
			cmd += " " + shape;
		}
		UnforgeCheats.send(clientThread, client, cmd);
	}

	private void promptShopAdd()
	{
		SwingUtilities.invokeLater(() ->
		{
			String input = javax.swing.JOptionPane.showInputDialog(
				panel, "Add item to open shop - obj name or id [count]:", "Shop Add",
				javax.swing.JOptionPane.PLAIN_MESSAGE);
			if (input == null || input.trim().isEmpty())
			{
				return;
			}
			UnforgeCheats.send(clientThread, client, "shopadd " + input.trim());
		});
	}

	/** Returns {shape, angle} for the game object at scene coords, or {-1, -1} if unresolved. */
	private int[] resolveLocConfig(int sceneX, int sceneY, int locId)
	{
		try
		{
			WorldView wv = client.getTopLevelWorldView();
			if (wv == null || wv.getScene() == null)
			{
				return new int[]{-1, -1};
			}
			net.runelite.api.Tile tile = wv.getScene().getTiles()[client.getPlane()][sceneX][sceneY];
			if (tile == null || tile.getGameObjects() == null)
			{
				return new int[]{-1, -1};
			}
			for (net.runelite.api.GameObject obj : tile.getGameObjects())
			{
				if (obj != null && obj.getId() == locId)
				{
					int config = obj.getConfig();
					return new int[]{config & 31, (config >>> 6) & 3};
				}
			}
		}
		catch (Exception ignored)
		{
		}
		return new int[]{-1, -1};
	}

	private void copyToEditor(int id, String name, boolean npc, WorldPoint loc)
	{
		copyToEditor(id, name, npc, loc, -1, -1);
	}

	private void copyToEditor(int id, String name, boolean npc, WorldPoint loc, int shape, int angle)
	{
		SwingUtilities.invokeLater(() ->
		{
			clientToolbar.openPanel(navButton);
			panel.loadEntity(id, name, npc, loc, shape, angle);
		});
	}

	private void deleteEntity(String command, int id, WorldPoint loc)
	{
		deleteEntity(command, id, loc, -1);
	}

	private void deleteEntity(String command, int id, WorldPoint loc, int shape)
	{
		if (loc == null)
		{
			return;
		}
		// npcdel / locdelat <id> <level> <mapX> <mapZ> <localX> <localZ> [shape]
		String cmd = String.format("%s %d %d %d %d %d %d",
			command, id, loc.getPlane(), loc.getX() >> 6, loc.getY() >> 6, loc.getX() & 63, loc.getY() & 63);
		if (shape >= 0)
		{
			cmd += " " + shape;
		}
		UnforgeCheats.send(clientThread, client, cmd);
	}
}
