package net.runelite.client.plugins.iteminstancehover;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Draws a marker on tagged instance items. Only the marker is painted on top of
 * the bank/inventory grid - no slot layout is altered, so every interface keeps
 * its full usable space.
 */
class ItemInstanceTagOverlay extends WidgetItemOverlay
{
	private static final Color TAG_COLOR = new Color(0xFF, 0xD1, 0x00);
	private static final Color TAG_TEXT = new Color(0x00, 0x00, 0x00);

	private final ItemInstanceMetadataStore metadataStore;
	private final ItemInstanceTagStore tagStore;

	@Inject
	ItemInstanceTagOverlay(ItemInstanceMetadataStore metadataStore, ItemInstanceTagStore tagStore)
	{
		this.metadataStore = metadataStore;
		this.tagStore = tagStore;
		showOnInventory();
		showOnBank();
		showOnEquipment();
		showOnInterfaces(
			InterfaceID.BANKMAIN,
			InterfaceID.BANKSIDE,
			InterfaceID.SHARED_BANK,
			InterfaceID.SHARED_BANK_SIDE,
			InterfaceID.EQUIPMENT_SIDE,
			InterfaceID.WORNITEMS);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem itemWidget)
	{
		Widget widget = itemWidget.getWidget();
		int scope = ItemInstanceScopes.scopeFor(widget);
		if (scope < 0)
		{
			return;
		}

		ItemInstanceMetadata metadata = metadataStore.get(scope, widget.getIndex());
		if (metadata == null
			|| metadata.getObjectId() != itemId
			|| !tagStore.isTagged(metadata.getInstanceId()))
		{
			return;
		}

		Rectangle bounds = itemWidget.getCanvasBounds();
		graphics.setStroke(new BasicStroke(2f));
		graphics.setColor(TAG_COLOR);
		graphics.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3);

		int size = 9;
		int[] xs = {bounds.x, bounds.x + size, bounds.x};
		int[] ys = {bounds.y, bounds.y, bounds.y + size};
		graphics.fillPolygon(xs, ys, 3);
	}
}
