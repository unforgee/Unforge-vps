package net.runelite.client.plugins.iteminstancehover;

import java.util.Collections;
import java.util.List;

public final class ItemInstanceMetadata
{
	private final int scope;
	private final int slot;
	private final long instanceId;
	private final int objectId;
	private final String category;
	private final String rarity;
	private final String tier;
	private final int itemLevel;
	private final int quality;
	private final List<Affix> affixes;
	private final List<Socket> sockets;
	private final List<Ability> abilities;
	private final List<SkillAffix> skillAffixes;
	private final int speedBps;
	private final List<Integer> stats;

	ItemInstanceMetadata(
		int scope,
		int slot,
		long instanceId,
		int objectId,
		String category,
		String rarity,
		String tier,
		int itemLevel,
		int quality,
		List<Affix> affixes,
		List<Socket> sockets,
		List<Ability> abilities,
		List<SkillAffix> skillAffixes,
		int speedBps,
		List<Integer> stats)
	{
		this.scope = scope;
		this.slot = slot;
		this.instanceId = instanceId;
		this.objectId = objectId;
		this.category = category;
		this.rarity = rarity;
		this.tier = tier;
		this.itemLevel = itemLevel;
		this.quality = quality;
		this.affixes = Collections.unmodifiableList(affixes);
		this.sockets = Collections.unmodifiableList(sockets);
		this.abilities = Collections.unmodifiableList(abilities);
		this.skillAffixes = Collections.unmodifiableList(skillAffixes);
		this.speedBps = speedBps;
		this.stats = Collections.unmodifiableList(stats);
	}

	public int getScope()
	{
		return scope;
	}

	public int getSlot()
	{
		return slot;
	}

	public long getInstanceId()
	{
		return instanceId;
	}

	public int getObjectId()
	{
		return objectId;
	}

	public String getCategory()
	{
		return category;
	}

	public String getRarity()
	{
		return rarity;
	}

	public String getTier()
	{
		return tier;
	}

	public int getItemLevel()
	{
		return itemLevel;
	}

	public int getQuality()
	{
		return quality;
	}

	public List<Affix> getAffixes()
	{
		return affixes;
	}

	public List<Socket> getSockets()
	{
		return sockets;
	}

	public List<Ability> getAbilities()
	{
		return abilities;
	}

	public List<SkillAffix> getSkillAffixes()
	{
		return skillAffixes;
	}

	/** Template gear attack-speed bonus in basis points; applies to plain items too. */
	public int getSpeedBps()
	{
		return speedBps;
	}

	/**
	 * The item's total (base + affix-effective) combat stats in the fixed server order:
	 * attack stab/slash/crush/magic/ranged, defence stab/slash/crush/magic/ranged,
	 * melee strength, ranged strength, magic damage (0.1% units) and prayer.
	 * Empty when the item carries no combat stats at all.
	 */
	public List<Integer> getStats()
	{
		return stats;
	}

	/**
	 * Copies for section merges. A split payload arrives as a section-less upsert
	 * followed by `sect` messages, each of which replaces exactly one list.
	 */
	ItemInstanceMetadata withAffixes(List<Affix> value)
	{
		return new ItemInstanceMetadata(scope, slot, instanceId, objectId, category, rarity,
			tier, itemLevel, quality, value, sockets, abilities, skillAffixes, speedBps, stats);
	}

	ItemInstanceMetadata withSockets(List<Socket> value)
	{
		return new ItemInstanceMetadata(scope, slot, instanceId, objectId, category, rarity,
			tier, itemLevel, quality, affixes, value, abilities, skillAffixes, speedBps, stats);
	}

	ItemInstanceMetadata withAbilities(List<Ability> value)
	{
		return new ItemInstanceMetadata(scope, slot, instanceId, objectId, category, rarity,
			tier, itemLevel, quality, affixes, sockets, value, skillAffixes, speedBps, stats);
	}

	ItemInstanceMetadata withSkillAffixes(List<SkillAffix> value)
	{
		return new ItemInstanceMetadata(scope, slot, instanceId, objectId, category, rarity,
			tier, itemLevel, quality, affixes, sockets, abilities, value, speedBps, stats);
	}

	/**
	 * A unique-effect proc. {@code name} is the server-catalog display name;
	 * {@code description} is the server-generated summary of what the proc does.
	 * Older payloads carry only a raw id in {@code name} and an empty description.
	 */
	public static final class Ability
	{
		private final String name;
		private final String description;

		Ability(String name, String description)
		{
			this.name = name;
			this.description = description;
		}

		public String getName()
		{
			return name;
		}

		public String getDescription()
		{
			return description;
		}
	}

	public static final class SkillAffix
	{
		private final String skill;
		private final String effect;
		private final String unit;
		private final int magnitude;

		SkillAffix(String skill, String effect, String unit, int magnitude)
		{
			this.skill = skill;
			this.effect = effect;
			this.unit = unit;
			this.magnitude = magnitude;
		}

		public String getSkill() { return skill; }
		public String getEffect() { return effect; }
		public String getUnit() { return unit; }
		public int getMagnitude() { return magnitude; }
	}

	public static final class Affix
	{
		private final int slot;
		private final String definitionId;
		private final String family;
		private final String stat;
		private final String unit;
		private final String polarity;
		private final int magnitude;

		Affix(int slot, String definitionId, String family, String stat, String unit, String polarity, int magnitude)
		{
			this.slot = slot;
			this.definitionId = definitionId;
			this.family = family;
			this.stat = stat;
			this.unit = unit;
			this.polarity = polarity;
			this.magnitude = magnitude;
		}

		public int getSlot()
		{
			return slot;
		}

		public String getDefinitionId()
		{
			return definitionId;
		}

		public String getFamily()
		{
			return family;
		}

		public String getStat()
		{
			return stat;
		}

		public String getUnit()
		{
			return unit;
		}

		public String getPolarity()
		{
			return polarity;
		}

		public int getMagnitude()
		{
			return magnitude;
		}
	}

	public static final class Socket
	{
		private final int slot;
		private final String type;
		private final Integer objectId;
		private final int magnitude;

		Socket(int slot, String type, Integer objectId, int magnitude)
		{
			this.slot = slot;
			this.type = type;
			this.objectId = objectId;
			this.magnitude = magnitude;
		}

		public int getSlot()
		{
			return slot;
		}

		public String getType()
		{
			return type;
		}

		public Integer getObjectId()
		{
			return objectId;
		}

		public int getMagnitude()
		{
			return magnitude;
		}
	}
}
