package net.runelite.client.plugins.iteminstancehover;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

final class ItemInstanceProtocol
{
	static final String PREFIX = "UNFORGE_ITEM_INSTANCE";
	private static final String VERSION = "3";
	private static final String EMPTY = "-";

	private ItemInstanceProtocol()
	{
	}

	static ParsedMessage parse(String message)
	{
		if (message == null || !message.startsWith(PREFIX + "|"))
		{
			return null;
		}

		try
		{
			String[] parts = message.split("\\|", -1);
			if (parts.length < 3 || !PREFIX.equals(parts[0]) || !isSupportedVersion(parts[1]))
			{
				return null;
			}

			switch (parts[2])
			{
				case "clear":
					return parts.length == 4 ? ParsedMessage.clear(parseNonNegativeInt(parts[3])) : null;
				case "remove":
					return parts.length == 5
						? ParsedMessage.remove(parseNonNegativeInt(parts[3]), parseNonNegativeInt(parts[4]))
						: null;
				case "upsert":
					return (parts.length >= 15 && parts.length <= 18)
						? ParsedMessage.upsert(parseMetadata(parts))
						: null;
				case "sect":
					return parts.length == 10
						? ParsedMessage.section(
							parseNonNegativeInt(parts[3]),
							parseNonNegativeInt(parts[4]),
							parseNonNegativeInt(parts[5]),
							parts[6],
							parseNonNegativeInt(parts[7]),
							parseNonNegativeInt(parts[8]),
							parts[9])
						: null;
				default:
					return null;
			}
		}
		catch (RuntimeException ignored)
		{
			// A malformed or newer message must remain a normal game message instead of
			// being swallowed and silently losing user-visible information.
			return null;
		}
	}

	/** Accepts the legacy v1/v2 envelopes alongside the current version. */
	private static boolean isSupportedVersion(String version)
	{
		return "1".equals(version) || "2".equals(version) || VERSION.equals(version);
	}

	private static ItemInstanceMetadata parseMetadata(String[] parts)
	{
		int scope = parseInt(parts[3]);
		int slot = parseInt(parts[4]);
		// Zero marks a plain (non-instanced) wearable; only the gear speed field applies to it.
		long instanceId = Long.parseLong(parts[5]);
		int objectId = parseInt(parts[6]);
		if (scope < 0 || slot < 0 || instanceId < 0 || objectId < 0)
		{
			throw new IllegalArgumentException("Invalid item-instance envelope");
		}

		return new ItemInstanceMetadata(
			scope,
			slot,
			instanceId,
			objectId,
			parts[7],
			parts[8],
			parts[9],
			parseInt(parts[10]),
			parseInt(parts[11]),
				parseAffixes(parts[12]),
				parseSockets(parts[13]),
				parseAbilities(parts[14]),
				parts.length >= 16 ? parseSkillAffixes(parts[15]) : Collections.emptyList(),
				parts.length >= 17 ? parseInt(parts[16]) : 0,
				parts.length >= 18 ? parseStats(parts[17]) : Collections.emptyList());
	}

	private static List<ItemInstanceMetadata.SkillAffix> parseSkillAffixes(String value)
	{
		if (EMPTY.equals(value)) return Collections.emptyList();
		List<ItemInstanceMetadata.SkillAffix> result = new ArrayList<>();
		for (String encoded : value.split(",", -1))
		{
			String[] fields = encoded.split("~", -1);
			if (fields.length != 4) throw new IllegalArgumentException("Invalid skill affix");
			result.add(new ItemInstanceMetadata.SkillAffix(
				decodeText(fields[0]), decodeText(fields[1]), fields[2], parseInt(fields[3])));
		}
		return result;
	}

	private static List<ItemInstanceMetadata.Affix> parseAffixes(String value)
	{
		if (EMPTY.equals(value))
		{
			return Collections.emptyList();
		}

		List<ItemInstanceMetadata.Affix> affixes = new ArrayList<>();
		for (String encoded : value.split(",", -1))
		{
			String[] fields = encoded.split("~", -1);
			if (fields.length != 7)
			{
				throw new IllegalArgumentException("Invalid item-instance affix");
			}

			affixes.add(new ItemInstanceMetadata.Affix(
				parseInt(fields[0]),
				decodeText(fields[1]),
				decodeText(fields[2]),
				fields[3],
				fields[4],
				fields[5],
				parseInt(fields[6])));
		}
		return affixes;
	}

	private static List<ItemInstanceMetadata.Socket> parseSockets(String value)
	{
		if (EMPTY.equals(value))
		{
			return Collections.emptyList();
		}

		List<ItemInstanceMetadata.Socket> sockets = new ArrayList<>();
		for (String encoded : value.split(",", -1))
		{
			String[] fields = encoded.split("~", -1);
			if (fields.length != 4)
			{
				throw new IllegalArgumentException("Invalid item-instance socket");
			}

			Integer objectId = EMPTY.equals(fields[2]) ? null : parseInt(fields[2]);
			sockets.add(new ItemInstanceMetadata.Socket(
				parseInt(fields[0]),
				decodeText(fields[1]),
				objectId,
				parseInt(fields[3])));
		}
		return sockets;
	}

	/**
	 * Each entry is `b64(name)~b64(description)`. Older payloads send only the
	 * ability id - those decode into a name with an empty description.
	 */
	private static List<ItemInstanceMetadata.Ability> parseAbilities(String value)
	{
		if (EMPTY.equals(value))
		{
			return Collections.emptyList();
		}

		List<ItemInstanceMetadata.Ability> abilities = new ArrayList<>();
		for (String encoded : value.split(",", -1))
		{
			String[] fields = encoded.split("~", -1);
			if (fields.length > 2)
			{
				throw new IllegalArgumentException("Invalid item-instance ability");
			}
			String description = fields.length == 2 && !fields[1].isEmpty()
				? decodeText(fields[1])
				: "";
			abilities.add(new ItemInstanceMetadata.Ability(decodeText(fields[0]), description));
		}
		return abilities;
	}

	private static List<Integer> parseStats(String value)
	{
		if (EMPTY.equals(value))
		{
			return Collections.emptyList();
		}

		List<Integer> stats = new ArrayList<>();
		for (String entry : value.split(",", -1))
		{
			stats.add(parseInt(entry));
		}
		return stats;
	}

	private static int parseInt(String value)
	{
		return Integer.parseInt(value);
	}

	private static int parseNonNegativeInt(String value)
	{
		int parsed = parseInt(value);
		if (parsed < 0)
		{
			throw new IllegalArgumentException("Expected a non-negative value");
		}
		return parsed;
	}

	private static String decodeText(String value)
	{
		return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
	}

	/**
	 * Replaces one section list on [base] with the parsed form of [data]. Unknown
	 * section names are ignored so future server sections cannot corrupt stored
	 * metadata.
	 */
	static ItemInstanceMetadata withSection(ItemInstanceMetadata base, String name, String data)
	{
		switch (name)
		{
			case "affixes":
				return base.withAffixes(parseAffixes(data));
			case "sockets":
				return base.withSockets(parseSockets(data));
			case "abilities":
				return base.withAbilities(parseAbilities(data));
			case "skillaffixes":
				return base.withSkillAffixes(parseSkillAffixes(data));
			default:
				return base;
		}
	}

	static final class ParsedMessage
	{
		enum Action
		{
			CLEAR,
			REMOVE,
			UPSERT,
			SECTION
		}

		private final Action action;
		private final int scope;
		private final int slot;
		private final int objectId;
		private final String section;
		private final int sequence;
		private final int chunkCount;
		private final String data;
		private final ItemInstanceMetadata metadata;

		private ParsedMessage(Action action, int scope, int slot, ItemInstanceMetadata metadata)
		{
			this(action, scope, slot, -1, null, -1, -1, null, metadata);
		}

		private ParsedMessage(
			Action action,
			int scope,
			int slot,
			int objectId,
			String section,
			int sequence,
			int chunkCount,
			String data,
			ItemInstanceMetadata metadata)
		{
			this.action = action;
			this.scope = scope;
			this.slot = slot;
			this.objectId = objectId;
			this.section = section;
			this.sequence = sequence;
			this.chunkCount = chunkCount;
			this.data = data;
			this.metadata = metadata;
		}

		static ParsedMessage clear(int scope)
		{
			return new ParsedMessage(Action.CLEAR, scope, -1, null);
		}

		static ParsedMessage remove(int scope, int slot)
		{
			return new ParsedMessage(Action.REMOVE, scope, slot, null);
		}

		static ParsedMessage upsert(ItemInstanceMetadata metadata)
		{
			return new ParsedMessage(Action.UPSERT, metadata.getScope(), metadata.getSlot(), metadata);
		}

		static ParsedMessage section(
			int scope,
			int slot,
			int objectId,
			String section,
			int sequence,
			int chunkCount,
			String data)
		{
			return new ParsedMessage(
				Action.SECTION, scope, slot, objectId, section, sequence, chunkCount, data, null);
		}

		void apply(ItemInstanceMetadataStore store)
		{
			switch (action)
			{
				case CLEAR:
					store.clear(scope);
					break;
				case REMOVE:
					store.remove(scope, slot);
					break;
				case UPSERT:
					store.upsert(metadata);
					break;
				case SECTION:
					store.applySection(scope, slot, objectId, section, sequence, chunkCount, data);
					break;
				default:
					throw new IllegalStateException("Unknown item-instance action");
			}
		}
	}
}
