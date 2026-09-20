package net.runelite.client.plugins.unforgeforge;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.iteminstancehover.ItemInstanceMetadata;
import net.runelite.client.plugins.iteminstancehover.ItemInstanceMetadataStore;
import net.runelite.client.plugins.iteminstancehover.ItemInstanceScopes;
import net.runelite.client.plugins.unforgecommon.UnforgeCheats;
import net.runelite.client.plugins.unforgeforge.UnforgeForgeProtocol.ParsedMessage;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * Forge 2.0 client: a "Forge" shift+right-click option on equipment items
 * (player inventory, worn equipment, companion pack) plus a right-sidebar panel that
 * renders the authoritative `UNFORGE_FORGE` snapshot. The companion-gear entry
 * point is the native Companion Hub equipment op, which streams the same
 * snapshot - the panel only ever displays server-owned state and sends back
 * `forgeop` requests carrying instance id + revision + selected affix slots.
 */
@PluginDescriptor(
	name = "Unforge Forge",
	description = "Upgrade and reforge equipment item instances via the Forge panel",
	tags = {"item", "instance", "equipment", "forge", "upgrade", "reforge"}
)
@Singleton
public class UnforgeForgePlugin extends Plugin
{
	private static final String FORGE_OPTION = "Forge";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ItemInstanceMetadataStore metadataStore;

	private UnforgeForgePanel panel;
	private NavigationButton navigationButton;

	@Override
	protected void startUp()
	{
		panel = new UnforgeForgePanel(itemManager, this::sendCommand);
		panel.showEmpty();

		navigationButton = NavigationButton.builder()
			.tooltip("Forge")
			.icon(createIcon())
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navigationButton);
		navigationButton = null;
		panel = null;
	}

	private void sendCommand(String command)
	{
		UnforgeCheats.send(clientThread, client, command);
	}

	// ------------------------------------------------------------------ menu

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		// Same convention as UnforgeSpawnPlugin: custom entries live behind
		// shift+right-click so the normal item menu stays uncluttered.
		if (!client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}

		if (!"Examine".equals(event.getOption()))
		{
			return;
		}

		Widget widget = widgetOf(event);
		if (widget == null)
		{
			return;
		}

		Widget item = itemWidgetOf(widget);
		if (item == null)
		{
			return;
		}

		String scope = forgeScopeFor(item);
		if (scope == null)
		{
			return;
		}

		int slot = item.getIndex() >= 0 ? item.getIndex() : event.getActionParam0();
		int objectId = item.getItemId() >= 0 ? item.getItemId() : event.getItemId();

		if ("pack".equals(scope))
		{
			// Companion pack: the shop widget cannot express wearability, so every
			// entry gets the option - the server rejects non-forgeable items.
			addForgeEntry(event, scope, slot);
			return;
		}

		ItemInstanceMetadata metadata = metadataStore.get(scopeId(scope), slot);
		boolean instanced = metadata != null
			&& metadata.getInstanceId() > 0
			&& metadata.getObjectId() == objectId;
		if (!instanced && !hasWearOption(event))
		{
			return;
		}

		addForgeEntry(event, scope, slot);
	}

	private void addForgeEntry(MenuEntryAdded event, String scope, int slot)
	{
		client.createMenuEntry(-1)
			.setParam0(event.getActionParam0())
			.setParam1(event.getActionParam1())
			.setTarget(event.getTarget())
			.setOption(FORGE_OPTION)
			.setType(MenuAction.RUNELITE)
			.setIdentifier(event.getIdentifier())
			.setItemId(event.getItemId())
			.onClick(entry ->
			{
				sendCommand("forgeopen " + scope + " " + slot);
				SwingUtilities.invokeLater(() ->
					clientToolbar.openPanel(navigationButton));
			});
	}

	/**
	 * Scope name for [widget] as `forgeopen` expects it, or null when the widget
	 * cannot hold a forgeable item. Banked/shared-bank items are deliberately
	 * excluded - the server requires the item to be held or worn.
	 */
	private String forgeScopeFor(Widget item)
	{
		int scope = ItemInstanceScopes.scopeFor(item);
		if (scope == InventoryID.INV)
		{
			return "inv";
		}
		if (scope == InventoryID.WORN)
		{
			return "worn";
		}
		if (WidgetUtil.componentToInterface(item.getId()) == InterfaceID.SHOPMAIN
			&& isCompanionPackOpen())
		{
			return "pack";
		}
		return null;
	}

	private static int scopeId(String scope)
	{
		switch (scope)
		{
			case "inv":
				return InventoryID.INV;
			case "worn":
				return InventoryID.WORN;
			case "pack":
				return InventoryID.RAIDS_PRIVATESTORAGE;
			default:
				return -1;
		}
	}

	/** The pack is surfaced through the shop UI titled "&lt;name&gt;'s Pack". */
	private boolean isCompanionPackOpen()
	{
		Widget universe = client.getWidget(InterfaceID.Shopmain.UNIVERSE);
		return containsPackTitle(universe);
	}

	private static boolean containsPackTitle(Widget widget)
	{
		if (widget == null || widget.isHidden())
		{
			return false;
		}
		String text = widget.getText();
		if (text != null && text.endsWith(" Pack"))
		{
			return true;
		}
		Widget[][] groups = {
			widget.getChildren(),
			widget.getDynamicChildren(),
			widget.getStaticChildren(),
			widget.getNestedChildren(),
		};
		for (Widget[] children : groups)
		{
			if (children == null)
			{
				continue;
			}
			for (Widget child : children)
			{
				if (child != widget && containsPackTitle(child))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Wearables without an instance still get Forge - the server migrates them. */
	private boolean hasWearOption(MenuEntryAdded event)
	{
		MenuEntry[] entries = client.getMenuEntries();
		if (entries == null)
		{
			return false;
		}
		for (MenuEntry entry : entries)
		{
			String option = entry.getOption();
			if (("Wear".equals(option) || "Wield".equals(option) || "Equip".equals(option))
				&& entry.getParam0() == event.getActionParam0()
				&& entry.getParam1() == event.getActionParam1())
			{
				return true;
			}
		}
		return false;
	}

	private Widget widgetOf(MenuEntryAdded event)
	{
		MenuEntry entry = event.getMenuEntry();
		Widget widget = entry != null ? entry.getWidget() : null;
		if (widget == null)
		{
			widget = client.getWidget(event.getActionParam1());
		}
		return widget;
	}

	private static Widget itemWidgetOf(Widget widget)
	{
		if (widget.getItemId() >= 0)
		{
			return widget;
		}
		Widget[] children = widget.getChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				if (child.getItemId() >= 0)
				{
					return child;
				}
			}
		}
		return widget.getItemId() < 0 && widget.getIndex() >= 0 ? widget : null;
	}

	// ------------------------------------------------------------------ protocol

	@Subscribe(priority = 1000)
	public void onChatMessage(ChatMessage event)
	{
		// The server publishes the payload through `ChatType.Console` (protocol id 99),
		// which the client surfaces as CONSOLE; ENGINE is accepted for older payloads.
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.CONSOLE && type != ChatMessageType.ENGINE)
		{
			return;
		}

		ParsedMessage parsed = UnforgeForgeProtocol.parse(event.getMessage());
		if (parsed == null)
		{
			return;
		}

		SwingUtilities.invokeLater(() -> apply(parsed));

		// Remove the node entirely so the side channel leaves no blank chat lines.
		MessageNode node = event.getMessageNode();
		if (node != null)
		{
			ChatLineBuffer buffer = client.getChatLineMap().get(node.getType().getType());
			if (buffer != null)
			{
				buffer.removeMessageNode(node);
			}
		}
		event.setMessage("");
	}

	private void apply(ParsedMessage message)
	{
		if (panel == null)
		{
			return;
		}
		switch (message.kind)
		{
			case BEGIN:
				panel.beginItem(message.item);
				clientToolbar.openPanel(navigationButton);
				break;
			case AFFIX:
				panel.addAffix(message.affix);
				break;
			case SKILL_AFFIX:
				panel.addSkillAffix(message.skillAffix);
				break;
			case COST:
				panel.setCosts(message.costs);
				break;
			case ENCHANT:
				panel.setEnchant(message.enchant);
				break;
			case BALANCE:
				panel.setBalance(message.tickets, message.gold);
				break;
			case END:
				panel.endItem();
				break;
			case RESULT:
				panel.onResult(message.operation, message.instanceId,
					message.revision, message.upgradeLevel);
				break;
			case DIFF:
				panel.addDiff(message.diff);
				break;
			case ERROR:
				panel.onError(message.code, message.text);
				break;
			default:
				break;
		}
	}

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			g.setRenderingHint(
				RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setColor(new Color(0xff, 0xb8, 0x4d));
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
			g.drawString("F", 4, 13);
		}
		finally
		{
			g.dispose();
		}
		return image;
	}
}
