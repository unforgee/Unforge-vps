package net.runelite.client.plugins.explorerbackpack;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

final class ExplorerBackpackOverlay extends OverlayPanel
{
	private static final int CAPACITY = 28;
	private static final int COLUMNS = 4;
	private static final BufferedImage EMPTY = new BufferedImage(
		Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT, BufferedImage.TYPE_4BYTE_ABGR);

	private final Client client;
	private final ItemManager itemManager;
	private boolean hidden = true;

	@Inject
	ExplorerBackpackOverlay(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.BOTTOM_RIGHT);
		setPreferredColor(new Color(20, 20, 20, 170));
		panelComponent.setWrap(true);
		panelComponent.setGap(new Point(3, 2));
		panelComponent.setOrientation(ComponentOrientation.HORIZONTAL);
		panelComponent.setPreferredSize(new Dimension(COLUMNS * (Constants.ITEM_SPRITE_WIDTH + 3), 0));
	}

	@Override
	public Dimension render(java.awt.Graphics2D graphics)
	{
		if (hidden || !hasBag())
		{
			return null;
		}

		ItemContainer container = client.getItemContainer(InventoryID.LOOTING_BAG);
		int occupied = 0;
		if (container != null)
		{
			Item[] items = container.getItems();
			for (int slot = 0; slot < CAPACITY; slot++)
			{
				Item item = slot < items.length ? items[slot] : null;
				if (item != null && item.getQuantity() > 0)
				{
					occupied++;
					panelComponent.getChildren().add(new ImageComponent(itemManager.getImage(
						item.getId(), item.getQuantity(), itemManager.getItemComposition(item.getId()).isStackable())));
				}
				else
				{
					panelComponent.getChildren().add(new ImageComponent(EMPTY));
				}
			}
		}
		else
		{
			for (int slot = 0; slot < CAPACITY; slot++)
			{
				panelComponent.getChildren().add(new ImageComponent(EMPTY));
			}
		}

		panelComponent.getChildren().add(0, TitleComponent.builder()
			.text("Looting Bag " + occupied + "/" + CAPACITY)
			.color(Color.WHITE)
			.build());
		return super.render(graphics);
	}

	void toggle()
	{
		hidden = !hidden;
	}

	private boolean hasBag()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null && worn.getItems().length > 1)
		{
			Item item = worn.getItems()[1];
			if (item != null && item.getId() == net.runelite.api.gameval.ItemID.EXPLORER_BACKPACK)
			{
				return true;
			}
		}
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv == null)
		{
			return false;
		}
		for (Item item : inv.getItems())
		{
			if (item != null && isBagItem(item.getId()))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isBagItem(int itemId)
	{
		return itemId == net.runelite.api.gameval.ItemID.EXPLORER_BACKPACK
			|| itemId == net.runelite.api.gameval.ItemID.LOOTING_BAG
			|| itemId == net.runelite.api.gameval.ItemID.LOOTING_BAG_OPEN;
	}
}
