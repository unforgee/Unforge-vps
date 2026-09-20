package net.runelite.client.plugins.uibuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("rsps2UiBuilder")
public interface UiBuilderConfig extends Config
{
	@ConfigItem(keyName = "showEditor", name = "Show editor", description = "Show the RSPS2 UI Builder panel.")
	default boolean showEditor()
	{
		return true;
	}
}
