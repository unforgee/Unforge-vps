package net.runelite.client.plugins.unforgeai;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(UnforgeAiConfig.GROUP)
public interface UnforgeAiConfig extends Config
{
	String GROUP = "unforgeai";

	@ConfigItem(
		keyName = "websocketUrl",
		name = "Bridge WebSocket URL",
		description = "Local Unforge bridge WebSocket endpoint. Loopback addresses only.",
		position = 1
	)
	default String websocketUrl()
	{
		return "ws://127.0.0.1:18901/ws";
	}

	@ConfigItem(
		keyName = "httpBaseUrl",
		name = "Gateway HTTP URL",
		description = "Local Codex Gateway HTTP endpoint used for dictation. Loopback addresses only.",
		position = 2
	)
	default String httpBaseUrl()
	{
		return "http://127.0.0.1:18900";
	}

	@ConfigItem(
		keyName = "autoConnect",
		name = "Connect on startup",
		description = "Connect to the local bridge when the client plugin starts.",
		position = 3
	)
	default boolean autoConnect()
	{
		return true;
	}

	@ConfigItem(
		keyName = "publishPlayerState",
		name = "Publish player state",
		description = "Send the local player's level, position, skills and inventory to the local bridge. "
			+ "Loopback only; nothing leaves this machine.",
		position = 4
	)
	default boolean publishPlayerState()
	{
		return true;
	}

	@Range(min = 250, max = 10000)
	@ConfigItem(
		keyName = "playerStateIntervalMs",
		name = "Player state keepalive (ms)",
		description = "How often player state is re-sent even when nothing changed. "
			+ "State is always sent immediately when a tracked value changes.",
		position = 5
	)
	default int playerStateIntervalMs()
	{
		return 1000;
	}

	@ConfigItem(
		keyName = "includeInventory",
		name = "Include inventory and equipment",
		description = "Include inventory and equipment contents in the published player state. "
			+ "Disable to reduce frame size on slow machines.",
		position = 6
	)
	default boolean includeInventory()
	{
		return true;
	}
}
