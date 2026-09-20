package net.runelite.client.plugins.unforgeadventure;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("unforgeadventure")
public interface UnforgeAdventureConfig extends Config
{
	@ConfigItem(
		keyName = "showTracker",
		name = "Show tracker",
		description = "Show the Adventure Path tracker overlay. The in-game ::adventuretracker command toggles the same state per account."
	)
	default boolean showTracker()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fontSize",
		name = "Font size",
		description = "Text size inside the Adventure Path overlay - enlarge or shrink the panel."
	)
	@Range(min = 9, max = 24)
	default int fontSize()
	{
		return 13;
	}

	@ConfigItem(
		keyName = "backgroundAlpha",
		name = "Background opacity",
		description = "How opaque the overlay background is (0 = fully transparent, 255 = solid)."
	)
	@Range(min = 0, max = 255)
	default int backgroundAlpha()
	{
		return 140;
	}

	@ConfigItem(
		keyName = "expanded",
		name = "Show all steps",
		description = "Show every remaining Adventure Path step instead of only the next few."
	)
	default boolean expanded()
	{
		return false;
	}
}
