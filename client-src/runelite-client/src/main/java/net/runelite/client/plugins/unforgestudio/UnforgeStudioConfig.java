package net.runelite.client.plugins.unforgestudio;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/**
 * Settings for the Unforge development studio dock.
 *
 * <p>The dock hosts the local development tools under the game window. Every
 * endpoint it talks to is loopback-only, and the studio and content editor are
 * separate local processes the developer starts alongside the client.</p>
 */
@ConfigGroup(UnforgeStudioConfig.GROUP)
public interface UnforgeStudioConfig extends Config
{
	String GROUP = "unforgestudio";

	@ConfigItem(
		keyName = "dockEnabled",
		name = "Show development dock",
		description = "Dock the development tools below the game window. "
			+ "Turn this off to get the plain client window back.",
		position = 1
	)
	default boolean dockEnabled()
	{
		return true;
	}

	@Range(min = 120, max = 900)
	@ConfigItem(
		keyName = "dockHeight",
		name = "Dock height",
		description = "Height of the development dock in pixels. Dragging the divider in the "
			+ "dock header updates this.",
		position = 2
	)
	default int dockHeight()
	{
		return 280;
	}

	@Range(min = 225, max = 900)
	@ConfigItem(
		keyName = "sidebarWidth",
		name = "Plugin sidebar width",
		description = "Widen the plugin sidebar, which the client otherwise pins to 225 pixels. "
			+ "Set to 225 to restore the default.",
		position = 3
	)
	default int sidebarWidth()
	{
		return 225;
	}

	@ConfigItem(
		keyName = "studioBaseUrl",
		name = "Studio URL",
		description = "Local AI4FUN Studio HTTP endpoint. Loopback only.",
		position = 4
	)
	default String studioBaseUrl()
	{
		return "http://127.0.0.1:8787";
	}

	@ConfigItem(
		keyName = "contentEditorUrl",
		name = "Content editor URL",
		description = "Local content editor endpoint. Loopback only.",
		position = 5
	)
	default String contentEditorUrl()
	{
		return "http://127.0.0.1:18930";
	}

	@ConfigItem(
		keyName = "contentEditorToken",
		name = "Content editor token",
		description = "X-Editor-Token value printed when the content editor starts. "
			+ "Required for every editor API call.",
		position = 6,
		secret = true
	)
	default String contentEditorToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "labRoot",
		name = "Lab root",
		description = "Path to the UNFORGE-239 standalone lab. Used to find .data/symbols "
			+ "for name-to-id lookup. Leave empty to auto-detect.",
		position = 7
	)
	default String labRoot()
	{
		return "";
	}
}
