package net.runelite.client.plugins.unforgeadventure;

import javax.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Adventure Path tracker: renders the quest chain pushed by the server
 * (`UNFORGE_ADV|*` console side channel) as a translucent, movable overlay panel
 * with the next step highlighted - instead of chat text.
 */
@PluginDescriptor(
	name = "Unforge Adventure Path",
	description = "Shows the Adventure Path quest chain in a resizable overlay",
	tags = {"adventure", "quest", "tracker", "unforge"},
	enabledByDefault = true
)
public class UnforgeAdventurePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private AdventureTrackerModel model;

	@Inject
	private UnforgeAdventureOverlay overlay;

	@Provides
	UnforgeAdventureConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UnforgeAdventureConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		model.reset();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			model.reset();
		}
	}

	@Subscribe(priority = 1000)
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.CONSOLE && type != ChatMessageType.ENGINE)
		{
			return;
		}

		if (!AdventureTrackerProtocol.isTrackerMessage(event.getMessage()))
		{
			return;
		}

		AdventureTrackerProtocol.apply(event.getMessage(), model);

		// Remove the node entirely so the side channel leaves no chat lines.
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
}
