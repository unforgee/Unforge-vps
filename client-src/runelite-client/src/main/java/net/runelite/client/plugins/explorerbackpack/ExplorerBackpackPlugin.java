package net.runelite.client.plugins.explorerbackpack;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.ColorUtil;
import java.awt.Color;

@PluginDescriptor(
	name = "Explorer Backpack",
	description = "Adds a 28-slot server-backed looting bag overlay",
	tags = {"inventory", "backpack", "overlay"},
	enabledByDefault = true
)
public class ExplorerBackpackPlugin extends Plugin
{
	private static final String PUT_IN_BACKPACK = "Put in backpack";
	private static final String TOGGLE_BACKPACK = "Toggle";

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ExplorerBackpackOverlay overlay;

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		MenuAction action = MenuAction.of(event.getType());
		if (action == MenuAction.CC_OP
			&& event.getActionParam1() == InterfaceID.Inventory.ITEMS
			&& !isBagItem(event.getItemId())
			&& hasBag())
		{
			client.createMenuEntry(-1)
				.setOption(PUT_IN_BACKPACK)
				.setTarget(event.getTarget())
				// IfButton Op6 maps to server-side HeldOp.Op4. The item id is
				// carried separately from the operation id in CC_OP entries.
				.setIdentifier(6)
				.setItemId(event.getItemId())
				.setParam0(event.getActionParam0())
				.setParam1(event.getActionParam1())
				.setType(MenuAction.CC_OP);
		}

		if (action == MenuAction.CC_OP
			&& ((event.getActionParam1() == InterfaceID.Equipment.SLOT1
				&& event.getIdentifier() == ItemID.EXPLORER_BACKPACK)
			|| (event.getActionParam1() == InterfaceID.Inventory.ITEMS
				&& isBagItem(event.getItemId()))))
		{
			client.createMenuEntry(-1)
				.setOption(TOGGLE_BACKPACK)
				.setTarget("<col=ff9040>Looting bag</col>")
				.setIdentifier(event.getIdentifier())
				.setParam0(event.getActionParam0())
				.setParam1(event.getActionParam1())
				.setType(MenuAction.RUNELITE);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuAction() == MenuAction.RUNELITE && TOGGLE_BACKPACK.equals(event.getMenuOption()))
		{
			overlay.toggle();
			event.consume();
		}
	}

	private static boolean isBagItem(int itemId)
	{
		return itemId == ItemID.EXPLORER_BACKPACK
			|| itemId == ItemID.LOOTING_BAG
			|| itemId == ItemID.LOOTING_BAG_OPEN;
	}

	private boolean hasBag()
	{
		final net.runelite.api.ItemContainer worn =
			client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN);
		if (worn != null)
		{
			final net.runelite.api.Item[] wornItems = worn.getItems();
			if (wornItems.length > 1 && wornItems[1] != null
				&& wornItems[1].getId() == ItemID.EXPLORER_BACKPACK)
			{
				return true;
			}
		}
		final net.runelite.api.ItemContainer inv =
			client.getItemContainer(net.runelite.api.gameval.InventoryID.INV);
		if (inv == null)
		{
			return false;
		}
		for (final net.runelite.api.Item item : inv.getItems())
		{
			if (item != null && isBagItem(item.getId()))
			{
				return true;
			}
		}
		return false;
	}
}
