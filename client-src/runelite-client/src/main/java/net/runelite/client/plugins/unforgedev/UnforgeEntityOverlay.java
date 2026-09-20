package net.runelite.client.plugins.unforgedev;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public class UnforgeEntityOverlay extends Overlay
{
	private final Client client;
	private final UnforgeDevConfig config;
	private final TooltipManager tooltipManager;

	@Inject
	public UnforgeEntityOverlay(Client client, UnforgeDevConfig config, TooltipManager tooltipManager)
	{
		this.client = client;
		this.config = config;
		this.tooltipManager = tooltipManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showEntityOverlay())
		{
			return null;
		}

		MenuEntry[] menuEntries = client.getMenuEntries();
		if (menuEntries != null && menuEntries.length > 0)
		{
			MenuEntry topEntry = menuEntries[menuEntries.length - 1];
			NPC npc = topEntry.getNpc();
			if (npc != null)
			{
				WorldPoint wp = npc.getWorldLocation();
				StringBuilder sb = new StringBuilder();
				sb.append("<col=50e3c2>[NPC] ").append(npc.getName()).append("</col> (ID: <col=ffffff>").append(npc.getId()).append("</col>)</br>");
				sb.append("<col=e0e0e0>Combat Lvl: </col><col=ffffff>").append(npc.getCombatLevel()).append("</col></br>");
				sb.append("<col=e0e0e0>Coords: </col><col=ffffff>").append(wp.getX()).append(", ").append(wp.getY()).append(", ").append(wp.getPlane()).append("</col></br>");
				sb.append("<col=e0e0e0>Animation: </col><col=ffffff>").append(npc.getAnimation()).append("</col></br>");
				sb.append("<col=e0e0e0>Orientation: </col><col=ffffff>").append(npc.getOrientation()).append("</col></br>");
				sb.append("<col=ffd700>Right-click -> [Unforge Dev] Edit NPC</col>");

				tooltipManager.add(new Tooltip(sb.toString()));
				return null;
			}
		}

		// Inspect hovered tile or object
		WorldView wv = client.getTopLevelWorldView();
		if (wv != null)
		{
			Tile tile = wv.getSelectedSceneTile();
			if (tile != null)
			{
				WorldPoint wp = WorldPoint.fromLocalInstance(client, tile.getLocalLocation());
				TileObject[] objects = tile.getGameObjects();
				TileObject targetObj = null;
				if (objects != null)
				{
					for (TileObject obj : objects)
					{
						if (obj != null)
						{
							targetObj = obj;
							break;
						}
					}
				}
				if (targetObj == null)
				{
					targetObj = tile.getWallObject();
				}
				if (targetObj == null)
				{
					targetObj = tile.getDecorativeObject();
				}
				if (targetObj == null)
				{
					targetObj = tile.getGroundObject();
				}

				if (targetObj != null)
				{
					ObjectComposition def = client.getObjectDefinition(targetObj.getId());
					String objName = def != null ? def.getName() : "Object";
					if (objName != null && !objName.equalsIgnoreCase("null"))
					{
						StringBuilder sb = new StringBuilder();
						sb.append("<col=ffca28>[Object] ").append(objName).append("</col> (ID: <col=ffffff>").append(targetObj.getId()).append("</col>)</br>");
						sb.append("<col=e0e0e0>Coords: </col><col=ffffff>").append(wp.getX()).append(", ").append(wp.getY()).append(", ").append(wp.getPlane()).append("</col></br>");
						sb.append("<col=ffd700>Right-click -> [Unforge Dev] Edit Object</col>");

						tooltipManager.add(new Tooltip(sb.toString()));
					}
				}
			}
		}

		return null;
	}
}
