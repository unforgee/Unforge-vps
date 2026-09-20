package net.runelite.client.plugins.unforgepvm;

import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.unforgecommon.UnforgeIcons;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@PluginDescriptor(
	name = "Unforge PvM Spellbook",
	description = "Visual PvM Spellbook with high-resolution fantasy icons, one-click casting, and live HUD controls",
	tags = {"unforge", "pvm", "spellbook", "magic", "support", "spells", "combat"},
	enabledByDefault = true
)
public class UnforgePvmSpellbookPlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private UnforgePvmSpellbookPanel panel;

	private NavigationButton navigationButton;

	@Override
	protected void startUp()
	{
		panel.init(this::openInSidebar);

		BufferedImage icon = UnforgeIcons.letter(new Color(175, 75, 255), "S");
		navigationButton = NavigationButton.builder()
			.tooltip("Unforge PvM Spellbook")
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
		panel.onShutDown();
	}

	private void openInSidebar()
	{
		if (navigationButton != null)
		{
			clientToolbar.openPanel(navigationButton);
		}
	}
}
