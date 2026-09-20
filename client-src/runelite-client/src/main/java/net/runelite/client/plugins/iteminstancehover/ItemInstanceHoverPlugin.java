package net.runelite.client.plugins.iteminstancehover;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Unforge Item Instance Hover",
	description = "Shows authoritative item-instance stats, sockets, and abilities on hover, plus per-instance tags",
	tags = {"item", "instance", "equipment", "stats", "abilities", "tooltip", "tag"}
)
@Singleton
public class ItemInstanceHoverPlugin extends Plugin
{
	private static final String TAG_OPTION = "Tag instance";
	private static final String UNTAG_OPTION = "Untag instance";

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ItemInstanceHoverOverlay overlay;

	@Inject
	private ItemInstanceTagOverlay tagOverlay;

	@Inject
	private ItemInstanceMetadataStore metadataStore;

	@Inject
	private ItemInstanceTagStore tagStore;

	@Override
	protected void startUp()
	{
		metadataStore.clearAll();
		tagStore.clear();
		overlayManager.add(overlay);
		overlayManager.add(tagOverlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(tagOverlay);
		metadataStore.clearAll();
		tagStore.clear();
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

		ItemInstanceProtocol.ParsedMessage parsed = ItemInstanceProtocol.parse(event.getMessage());
		if (parsed == null)
		{
			return;
		}

		parsed.apply(metadataStore);

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

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!"Examine".equals(event.getOption()))
		{
			return;
		}

		ItemInstanceMetadata metadata = resolveMetadata(event);
		if (metadata == null || metadata.getInstanceId() <= 0)
		{
			// Plain wearables are published for their speed line only - nothing to tag.
			return;
		}

		boolean tagged = tagStore.isTagged(metadata.getInstanceId());
		client.createMenuEntry(-1)
			.setParam0(event.getActionParam0())
			.setParam1(event.getActionParam1())
			.setTarget(event.getTarget())
			.setOption(tagged ? UNTAG_OPTION : TAG_OPTION)
			.setType(MenuAction.RUNELITE)
			.setIdentifier(event.getIdentifier())
			.setItemId(event.getItemId())
			.onClick(entry -> toggleTag(metadata));
	}

	private void toggleTag(ItemInstanceMetadata metadata)
	{
		tagStore.toggle(metadata.getInstanceId());
	}

	private ItemInstanceMetadata resolveMetadata(MenuEntryAdded event)
	{
		MenuEntry entry = event.getMenuEntry();
		Widget widget = entry != null ? entry.getWidget() : null;
		if (widget == null)
		{
			widget = client.getWidget(event.getActionParam1());
		}
		if (widget == null)
		{
			return null;
		}

		Widget item = itemWidgetOf(widget);
		if (item == null)
		{
			return null;
		}

		int scope = ItemInstanceScopes.scopeFor(item);
		if (scope < 0)
		{
			return null;
		}

		int slot = item.getIndex();
		if (slot < 0)
		{
			slot = event.getActionParam0();
		}

		int objectId = item.getItemId();
		if (objectId < 0)
		{
			objectId = event.getItemId();
		}

		ItemInstanceMetadata metadata = metadataStore.get(scope, slot);
		return metadata != null && metadata.getObjectId() == objectId ? metadata : null;
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

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			metadataStore.clearAll();
			tagStore.clear();
		}
	}
}
