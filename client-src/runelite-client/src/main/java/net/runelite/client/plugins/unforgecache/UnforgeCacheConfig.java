package net.runelite.client.plugins.unforgecache;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("unforgecache")
public interface UnforgeCacheConfig extends Config
{
	@ConfigItem(
		keyName = "autoApply",
		name = "Re-apply replacements on startup",
		description = "Apply the replacements saved in ~/.runelite/unforge-cache when the client starts",
		position = 1
	)
	default boolean autoApply()
	{
		return true;
	}

	@ConfigItem(
		keyName = "keepOriginalOnRevert",
		name = "Keep the stored PNG when reverting",
		description = "Reverting stops the replacement but leaves the PNG on disk so it can be applied again",
		position = 2
	)
	default boolean keepOriginalOnRevert()
	{
		return true;
	}
}
