package net.runelite.client.plugins.iteminstanceinspect;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.List;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.iteminstancehover.ItemInstanceMetadata;
import net.runelite.client.plugins.iteminstancehover.ItemInstanceMetadataStore;
import net.runelite.client.plugins.iteminstancehover.ItemInstanceScopes;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * Dedicated right-sidebar inspector for equipment examine. Clicking `Examine` on an
 * inventory/worn/bank item opens this panel; the server streams the full inspection
 * (`UNFORGE_INSPECT_BEGIN`/`LINE`/`END` console messages) which replaces the body with
 * the authoritative post-affix stats, ability mechanics and skilling bonuses.
 */
@PluginDescriptor(
	name = "Item Instance Inspect",
	description = "Opens a right-side panel showing full item-instance details on Examine",
	tags = {"item", "instance", "equipment", "stats", "abilities", "inspect"}
)
@Singleton
public class ItemInstanceInspectPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ItemInstanceMetadataStore metadataStore;

	private ItemInstanceInspectPanel panel;
	private NavigationButton navigationButton;
	private Timer catalogRefreshTimer;
	private final StringBuilder pendingLineParts = new StringBuilder();

	@Override
	protected void startUp()
	{
		panel = new ItemInstanceInspectPanel(itemManager);
		panel.showEmpty();

		navigationButton = NavigationButton.builder()
			.tooltip("Item Instance Inspect")
			.icon(createIcon())
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		catalogRefreshTimer = new Timer(1000, event -> panel.showCatalog(metadataStore.snapshot()));
		catalogRefreshTimer.start();
		panel.showCatalog(metadataStore.snapshot());
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navigationButton);
		if (catalogRefreshTimer != null)
		{
			catalogRefreshTimer.stop();
			catalogRefreshTimer = null;
		}
		navigationButton = null;
		panel = null;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!"Examine".equals(event.getMenuOption()))
		{
			return;
		}

		Widget widget = event.getWidget();
		if (widget == null && event.getMenuEntry() != null)
		{
			widget = event.getMenuEntry().getWidget();
		}
		if (widget == null)
		{
			return;
		}

		Widget item = itemWidgetOf(widget);
		if (item == null || ItemInstanceScopes.scopeFor(item) < 0)
		{
			// NPC/object examines have no item widget in a synced scope - leave them alone.
			return;
		}

		int slot = item.getIndex() >= 0 ? item.getIndex() : event.getParam0();
		int objectId = item.getItemId() >= 0 ? item.getItemId() : event.getItemId();
		if (objectId < 0)
		{
			return;
		}

		ItemInstanceMetadata metadata = metadataStore.get(
			ItemInstanceScopes.scopeFor(item), slot);
		if (metadata != null && metadata.getObjectId() != objectId)
		{
			metadata = null;
		}

		// Resolve the display name on the client thread before touching Swing.
		ItemComposition composition = itemManager.getItemComposition(objectId);
		String name = composition != null ? composition.getMembersName() : null;
		ItemInstanceMetadata preview = metadata;
		SwingUtilities.invokeLater(() ->
		{
			panel.showItem(objectId, name, preview);
			clientToolbar.openPanel(navigationButton);
		});
	}

	@Subscribe(priority = 1000)
	public void onChatMessage(ChatMessage event)
	{
		// The server publishes the payload through `ChatType.Console` (protocol id 99), which the
		// client surfaces as CONSOLE; ENGINE is accepted for older payloads.
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.CONSOLE && type != ChatMessageType.ENGINE)
		{
			return;
		}

		ItemInstanceInspectProtocol.ParsedMessage parsed =
			ItemInstanceInspectProtocol.parse(event.getMessage());
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

	private void apply(ItemInstanceInspectProtocol.ParsedMessage message)
	{
		switch (message.getKind())
		{
			case BEGIN:
				pendingLineParts.setLength(0);
				panel.beginInspect(
					message.getObjectId(),
					message.getInstanceId(),
					message.getName(),
					message.getSubtitle());
				clientToolbar.openPanel(navigationButton);
				break;
			case LINE:
				panel.appendLine(message.getText());
				break;
			case PART:
				pendingLineParts.append(message.getText());
				break;
			case PART_END:
				if (pendingLineParts.length() > 0)
				{
					try
					{
						panel.appendLine(
							ItemInstanceInspectProtocol.decodeJoined(pendingLineParts.toString()));
					}
					catch (RuntimeException ignored)
					{
						// A corrupt chunk stream loses one line, not the whole inspect.
					}
					pendingLineParts.setLength(0);
				}
				break;
			case END:
				panel.endInspect();
				break;
			default:
				break;
		}
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

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			g.setRenderingHint(
				RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setColor(new java.awt.Color(0xff, 0x98, 0x1f));
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
			g.drawString("i", 6, 13);
		}
		finally
		{
			g.dispose();
		}
		return image;
	}
}
