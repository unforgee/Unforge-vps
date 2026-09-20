package net.runelite.client.plugins.unforgeadmin;

import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@PluginDescriptor(
	name = "Unforge NPC Area Builder",
	description = "Add an NPC to an exact map coordinate for area setup",
	tags = {"unforge", "npc", "area", "spawn", "admin", "panel"},
	enabledByDefault = true
)
public class UnforgeNpcAreaPlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private UnforgeNpcAreaPanel panel;

	private NavigationButton navigationButton;

	@Override
	protected void startUp()
	{
		BufferedImage icon = UnforgePluginIcon.create(new Color(255, 180, 70), false);
		navigationButton = NavigationButton.builder()
			.tooltip("Unforge NPC Area Builder")
			.icon(icon)
			.priority(3)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navigationButton);
	}
}
