package net.runelite.client.plugins.unforgedrops;

import java.awt.Color;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.unforgecommon.UnforgeIcons;
import net.runelite.client.plugins.unforgecommon.UnforgeNpcEditEvent;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@PluginDescriptor(
	name = "Unforge Drop Editor",
	description = "Standalone NPC combat & drop-table editor with item picker and JSON export",
	tags = {"unforge", "drop", "npc", "dev", "tools"},
	enabledByDefault = true
)
public class UnforgeDropsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	private UnforgeDropsPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		panel = new UnforgeDropsPanel(this, client, clientThread);
		navButton = NavigationButton.builder()
			.tooltip("Unforge Drop Editor")
			.icon(UnforgeIcons.letter(new Color(220, 53, 69), "D"))
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

	/** Right-click "Edit NPC" from Unforge Dev Tools loads the npc into this editor. */
	@Subscribe
	public void onUnforgeNpcEditEvent(UnforgeNpcEditEvent event)
	{
		SwingUtilities.invokeLater(() ->
		{
			clientToolbar.openPanel(navButton);
			panel.editNpc(event.getNpcId(), event.getName(), event.getCombatLevel());
		});
	}

	void reopenPanel()
	{
		SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
	}
}
