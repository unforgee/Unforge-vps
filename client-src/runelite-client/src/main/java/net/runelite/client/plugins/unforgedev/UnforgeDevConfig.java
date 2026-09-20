package net.runelite.client.plugins.unforgedev;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("unforgedev")
public interface UnforgeDevConfig extends Config
{
	@ConfigItem(
		keyName = "enableDevRightClick",
		name = "Right-Click Edit Entity",
		description = "Adds [Unforge Dev] Edit NPC/Object options to entity right-click menus",
		position = 1
	)
	default boolean enableDevRightClick()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showEntityOverlay",
		name = "Hover Entity Inspector",
		description = "Shows live ID, coordinates, orientation and animations under cursor",
		position = 2
	)
	default boolean showEntityOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "preferredAiProvider",
		name = "Default AI Provider",
		description = "Auto (Free pool with fallback), Groq, Cerebras, SambaNova, Mistral, Gemini, Command Code, OpenRouter, DeepSeek",
		position = 3
	)
	default String preferredAiProvider()
	{
		return "auto";
	}

	@ConfigItem(
		keyName = "autoSyncDataFolder",
		name = "Auto-save to kronos-data",
		description = "Automatically updates content/kronos-data when changes are confirmed in editor",
		position = 4
	)
	default boolean autoSyncDataFolder()
	{
		return true;
	}
}
