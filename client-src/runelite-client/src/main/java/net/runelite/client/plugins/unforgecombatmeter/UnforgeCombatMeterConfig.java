package net.runelite.client.plugins.unforgecombatmeter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("unforgecombatmeter")
public interface UnforgeCombatMeterConfig extends Config
{
	@ConfigItem(keyName = "groupCompanions", name = "Group companions", description = "Show companion totals below their owner")
	default boolean groupCompanions()
	{
		return false;
	}

	@ConfigItem(keyName = "debugLogging", name = "Debug logging", description = "Log combat meter events")
	default boolean debugLogging()
	{
		return false;
	}
}
