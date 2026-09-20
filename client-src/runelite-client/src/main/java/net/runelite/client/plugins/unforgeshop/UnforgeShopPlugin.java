package net.runelite.client.plugins.unforgeshop;

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
	name = "Unforge Shop Builder",
	description = "Standalone shop editor: item picker, bulk amounts, YAML export",
	tags = {"unforge", "shop", "dev", "tools"},
	enabledByDefault = true
)
public class UnforgeShopPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	private UnforgeShopPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		panel = new UnforgeShopPanel(this, client, clientThread);
		navButton = NavigationButton.builder()
			.tooltip("Unforge Shop Builder")
			.icon(UnforgeIcons.letter(new Color(40, 167, 69), "$"))
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
