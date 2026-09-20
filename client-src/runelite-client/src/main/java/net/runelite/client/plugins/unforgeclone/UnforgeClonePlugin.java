package net.runelite.client.plugins.unforgeclone;

import java.awt.Color;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.unforgecommon.UnforgeIcons;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@PluginDescriptor(
	name = "Unforge Item Clone",
	description = "Clone an item under a new id and recolour every palette slot at once",
	tags = {"unforge", "item", "clone", "dev", "tools"},
	enabledByDefault = true
)
public class UnforgeClonePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	private UnforgeClonePanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		panel = new UnforgeClonePanel(this, client, clientThread);
		navButton = NavigationButton.builder()
			.tooltip("Unforge Item Clone")
			.icon(UnforgeIcons.letter(new Color(0, 174, 204), "C"))
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

	void reopenPanel()
	{
		SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
	}
}
