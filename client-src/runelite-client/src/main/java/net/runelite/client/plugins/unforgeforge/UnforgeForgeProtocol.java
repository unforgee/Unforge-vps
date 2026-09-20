package net.runelite.client.plugins.unforgeforge;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Parser for the `UNFORGE_FORGE` console side channel emitted by the server-side
 * ForgeScript. The envelope is `|`-delimited; free-text fields are base64-url
 * encoded so markup and separators can never corrupt a frame.
 *
 * <pre>
 * UNFORGE_FORGE|BEGIN|instanceId|rev|objId|nameB64|rarity|tier|ilvl|quality|upgrade|scope|slot
 * UNFORGE_FORGE|AFF|slot|defIdB64|familyB64|stat|unit|polarity|mag|rerollable|min|max|qualityBps
 * UNFORGE_FORGE|SKA|skillB64|effectB64|unit|mag
 * UNFORGE_FORGE|COST|upT|upG|allT|allG|selBaseT|selPerT|selGoldPer|mysteryT|warnHighRoll
 * UNFORGE_FORGE|ENCHANT|id|tier|remaining|max|active
 * UNFORGE_FORGE|BAL|tickets|gold
 * UNFORGE_FORGE|END|instanceId
 * UNFORGE_FORGE|RESULT|op|instanceId|newRev|newUpgradeLevel
 * UNFORGE_FORGE|DIFF|slot|stat|unit|oldMag|newMag
 * UNFORGE_FORGE|ERR|code|messageB64
 * </pre>
 */
final class UnforgeForgeProtocol
{
	static final String PREFIX = "UNFORGE_FORGE";

	private UnforgeForgeProtocol()
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
			switch (parts[1])
			{
				case "BEGIN":
					return parts.length == 13
						? ParsedMessage.begin(new ForgeItem(
							Long.parseLong(parts[2]),
							Long.parseLong(parts[3]),
							Integer.parseInt(parts[4]),
							decode(parts[5]),
							parts[6],
							parts[7],
							Integer.parseInt(parts[8]),
							Integer.parseInt(parts[9]),
							Integer.parseInt(parts[10]),
							parts[11],
							Integer.parseInt(parts[12])))
						: null;
				case "AFF":
					return parts.length == 13
						? ParsedMessage.affix(new ForgeAffix(
							Integer.parseInt(parts[2]),
							decode(parts[3]),
							decode(parts[4]),
							parts[5],
							parts[6],
							parts[7],
							Integer.parseInt(parts[8]),
							"1".equals(parts[9]),
							"-".equals(parts[10]) ? null : Integer.parseInt(parts[10]),
							"-".equals(parts[11]) ? null : Integer.parseInt(parts[11]),
							Integer.parseInt(parts[12])))
						: null;
				case "SKA":
					return parts.length == 6
						? ParsedMessage.skillAffix(new ForgeSkillAffix(
							decode(parts[2]), decode(parts[3]), parts[4],
							Integer.parseInt(parts[5])))
						: null;
				case "COST":
					return parts.length == 10 || parts.length == 11
						? ParsedMessage.cost(new ForgeCosts(
							Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
							Integer.parseInt(parts[4]), Integer.parseInt(parts[5]),
							Integer.parseInt(parts[6]), Integer.parseInt(parts[7]),
							Integer.parseInt(parts[8]),
							parts.length == 11 ? Integer.parseInt(parts[9]) : 0,
							"1".equals(parts[parts.length == 11 ? 10 : 9])))
						: null;
				case "ENCHANT":
					return parts.length == 7
						? ParsedMessage.enchant(new ForgeEnchant(
							parts[2], parts[3], Integer.parseInt(parts[4]),
							Integer.parseInt(parts[5]), "1".equals(parts[6])))
						: null;
				case "BAL":
					return parts.length == 4
						? ParsedMessage.balance(
							Integer.parseInt(parts[2]), Integer.parseInt(parts[3]))
						: null;
				case "END":
					return parts.length == 3 ? ParsedMessage.end() : null;
				case "RESULT":
					return parts.length == 6
						? ParsedMessage.result(
							parts[2], Long.parseLong(parts[3]),
							Long.parseLong(parts[4]), Integer.parseInt(parts[5]))
						: null;
				case "DIFF":
					return parts.length == 7
						? ParsedMessage.diff(new ForgeDiff(
							Integer.parseInt(parts[2]), parts[3], parts[4],
							Integer.parseInt(parts[5]), Integer.parseInt(parts[6])))
						: null;
				case "ERR":
					return parts.length == 4
						? ParsedMessage.error(parts[2], decode(parts[3]))
						: null;
				default:
					return null;
			}
		}
		catch (RuntimeException ignored)
		{
			// A malformed frame stays a normal console line rather than being swallowed.
			return null;
		}
	}

	private static String decode(String value)
	{
		return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
	}

	/** One complete Forge item snapshot assembled between BEGIN and END. */
	static final class ForgeItem
	{
		final long instanceId;
		long revision;
		final int objectId;
		final String name;
		final String rarity;
		final String tier;
		final int itemLevel;
		final int quality;
		final int upgradeLevel;
		final String scope;
		final int slot;
		final List<ForgeAffix> affixes = new ArrayList<>();
		final List<ForgeSkillAffix> skillAffixes = new ArrayList<>();
		ForgeCosts costs;
		ForgeEnchant enchant;
		int tickets;
		int gold;

		ForgeItem(long instanceId, long revision, int objectId, String name, String rarity,
			String tier, int itemLevel, int quality, int upgradeLevel, String scope, int slot)
		{
			this.instanceId = instanceId;
			this.revision = revision;
			this.objectId = objectId;
			this.name = name;
			this.rarity = rarity;
			this.tier = tier;
			this.itemLevel = itemLevel;
			this.quality = quality;
			this.upgradeLevel = upgradeLevel;
			this.scope = scope;
			this.slot = slot;
		}
	}

	static final class ForgeEnchant
	{
		final String id;
		final String tier;
		final int remaining;
		final int max;
		final boolean active;

		ForgeEnchant(String id, String tier, int remaining, int max, boolean active)
		{
			this.id = id;
			this.tier = tier;
			this.remaining = remaining;
			this.max = max;
			this.active = active;
		}
	}

	static final class ForgeAffix
	{
		final int slot;
		final String definitionId;
		final String family;
		final String stat;
		final String unit;
		final String polarity;
		final int magnitude;
		final boolean rerollable;
		final Integer rangeMin;
		final Integer rangeMax;
		final int qualityBps;

		ForgeAffix(int slot, String definitionId, String family, String stat, String unit,
			String polarity, int magnitude, boolean rerollable, Integer rangeMin,
			Integer rangeMax, int qualityBps)
		{
			this.slot = slot;
			this.definitionId = definitionId;
			this.family = family;
			this.stat = stat;
			this.unit = unit;
			this.polarity = polarity;
			this.magnitude = magnitude;
			this.rerollable = rerollable;
			this.rangeMin = rangeMin;
			this.rangeMax = rangeMax;
			this.qualityBps = qualityBps;
		}
	}

	static final class ForgeSkillAffix
	{
		final String skill;
		final String effect;
		final String unit;
		final int magnitude;

		ForgeSkillAffix(String skill, String effect, String unit, int magnitude)
		{
			this.skill = skill;
			this.effect = effect;
			this.unit = unit;
			this.magnitude = magnitude;
		}
	}

	static final class ForgeCosts
	{
		final int upgradeTickets;
		final int upgradeGold;
		final int reforgeAllTickets;
		final int reforgeAllGold;
		final int selectedBaseTickets;
		final int selectedPerAffixTickets;
		final int selectedGoldPerAffix;
		final int mysteryTickets;
		final boolean warnsHighRoll;

		ForgeCosts(int upgradeTickets, int upgradeGold, int reforgeAllTickets, int reforgeAllGold,
			int selectedBaseTickets, int selectedPerAffixTickets, int selectedGoldPerAffix,
			int mysteryTickets, boolean warnsHighRoll)
		{
			this.upgradeTickets = upgradeTickets;
			this.upgradeGold = upgradeGold;
			this.reforgeAllTickets = reforgeAllTickets;
			this.reforgeAllGold = reforgeAllGold;
			this.selectedBaseTickets = selectedBaseTickets;
			this.selectedPerAffixTickets = selectedPerAffixTickets;
			this.selectedGoldPerAffix = selectedGoldPerAffix;
			this.mysteryTickets = mysteryTickets;
			this.warnsHighRoll = warnsHighRoll;
		}

		int selectedTickets(int count)
		{
			return selectedBaseTickets + count * selectedPerAffixTickets;
		}

		int selectedGold(int count)
		{
			return count * selectedGoldPerAffix;
		}
	}

	static final class ForgeDiff
	{
		final int slot;
		final String stat;
		final String unit;
		final int oldMagnitude;
		final int newMagnitude;

		ForgeDiff(int slot, String stat, String unit, int oldMagnitude, int newMagnitude)
		{
			this.slot = slot;
			this.stat = stat;
			this.unit = unit;
			this.oldMagnitude = oldMagnitude;
			this.newMagnitude = newMagnitude;
		}
	}

	static final class ParsedMessage
	{
		enum Kind
		{
			BEGIN, AFFIX, SKILL_AFFIX, COST, ENCHANT, BALANCE, END, RESULT, DIFF, ERROR
		}

		final Kind kind;
		final ForgeItem item;
		final ForgeAffix affix;
		final ForgeSkillAffix skillAffix;
		final ForgeCosts costs;
		final ForgeEnchant enchant;
		final ForgeDiff diff;
		final String operation;
		final long instanceId;
		final long revision;
		final int upgradeLevel;
		final int tickets;
		final int gold;
		final String code;
		final String text;

		private ParsedMessage(Kind kind, ForgeItem item, ForgeAffix affix,
			ForgeSkillAffix skillAffix, ForgeCosts costs, ForgeEnchant enchant, ForgeDiff diff, String operation,
			long instanceId, long revision, int upgradeLevel, int tickets, int gold,
			String code, String text)
		{
			this.kind = kind;
			this.item = item;
			this.affix = affix;
			this.skillAffix = skillAffix;
			this.costs = costs;
			this.enchant = enchant;
			this.diff = diff;
			this.operation = operation;
			this.instanceId = instanceId;
			this.revision = revision;
			this.upgradeLevel = upgradeLevel;
			this.tickets = tickets;
			this.gold = gold;
			this.code = code;
			this.text = text;
		}

		static ParsedMessage begin(ForgeItem item)
		{
			return new ParsedMessage(Kind.BEGIN, item, null, null, null, null, null, null,
				item.instanceId, item.revision, item.upgradeLevel, 0, 0, null, null);
		}

		static ParsedMessage affix(ForgeAffix affix)
		{
			return new ParsedMessage(Kind.AFFIX, null, affix, null, null, null, null, null,
				-1, -1, -1, 0, 0, null, null);
		}

		static ParsedMessage skillAffix(ForgeSkillAffix skillAffix)
		{
			return new ParsedMessage(Kind.SKILL_AFFIX, null, null, skillAffix, null, null, null, null,
				-1, -1, -1, 0, 0, null, null);
		}

		static ParsedMessage cost(ForgeCosts costs)
		{
			return new ParsedMessage(Kind.COST, null, null, null, costs, null, null, null,
				-1, -1, -1, 0, 0, null, null);
		}

		static ParsedMessage enchant(ForgeEnchant enchant)
		{
			return new ParsedMessage(Kind.ENCHANT, null, null, null, null, enchant, null, null,
				-1, -1, -1, 0, 0, null, null);
		}

		static ParsedMessage balance(int tickets, int gold)
		{
			return new ParsedMessage(Kind.BALANCE, null, null, null, null, null, null, null,
				-1, -1, -1, tickets, gold, null, null);
		}

		static ParsedMessage end()
		{
			return new ParsedMessage(Kind.END, null, null, null, null, null, null, null,
				-1, -1, -1, 0, 0, null, null);
		}

		static ParsedMessage result(String operation, long instanceId, long revision, int upgradeLevel)
		{
			return new ParsedMessage(Kind.RESULT, null, null, null, null, null, null, operation,
				instanceId, revision, upgradeLevel, 0, 0, null, null);
		}

		static ParsedMessage diff(ForgeDiff diff)
		{
			return new ParsedMessage(Kind.DIFF, null, null, null, null, null, diff, null,
				-1, -1, -1, 0, 0, null, null);
		}

		static ParsedMessage error(String code, String text)
		{
			return new ParsedMessage(Kind.ERROR, null, null, null, null, null, null, null,
				-1, -1, -1, 0, 0, code, text);
		}
	}
}
