package net.runelite.client.plugins.iteminstancehover;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;

/**
 * Maps item widgets onto the inventory-id scopes the server uses in
 * {@code UNFORGE_ITEM_INSTANCE} messages. Shared by the stats panel, the tag
 * overlay, and the tag menu entry so all of them resolve items identically.
 */
public final class ItemInstanceScopes
{
	private ItemInstanceScopes()
	{
	}

	/**
	 * The sync scope for [widget], or {@code -1} when the widget cannot hold
	 * instance-bearing items.
	 *
	 * The scope is the server's `inventory.type.id` - which is why the inventory
	 * panels shown inside the bank and shared bank map to {@link InventoryID#INV}
	 * (the items still live in the player's inventory), not to the bank.
	 */
	public static int scopeFor(Widget widget)
	{
		final int group = WidgetUtil.componentToInterface(widget.getId());
		final int parent = widget.getParentId();

		// Worn equipment: the equipment window, the bank-side worn panel, and the
		// equipment tab next to the inventory.
		if (group == InterfaceID.WORNITEMS
			|| parent == InterfaceID.Bankside.WORNOPS
			|| group == InterfaceID.EQUIPMENT_SIDE)
		{
			return InventoryID.WORN;
		}

		// Player inventory - including the side panels inside the bank interfaces.
		if (widget.getId() == InterfaceID.Inventory.ITEMS
			|| parent == InterfaceID.Inventory.ITEMS
			|| group == InterfaceID.BANKSIDE
			|| group == InterfaceID.SHARED_BANK_SIDE
			|| group == InterfaceID.BANK_DEPOSITBOX
			|| group == InterfaceID.SHOPSIDE
			|| group == InterfaceID.TRADESIDE)
		{
			return InventoryID.INV;
		}

		if (widget.getId() == InterfaceID.Bankmain.ITEMS
			|| parent == InterfaceID.Bankmain.ITEMS)
		{
			return InventoryID.BANK;
		}

		if (widget.getId() == InterfaceID.SharedBank.ITEMS
			|| parent == InterfaceID.SharedBank.ITEMS)
		{
			return InventoryID.INV_GROUP_TEMP;
		}

		return -1;
	}
}
