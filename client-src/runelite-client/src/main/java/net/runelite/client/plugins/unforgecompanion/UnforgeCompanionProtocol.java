package net.runelite.client.plugins.unforgecompanion;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Parser for the `UNFORGE_COMPANION` console side channel streamed by the server's
 * `companionstatus` command. One chat line per record; free-text fields are
 * base64-url encoded so `|` and colour tags can never corrupt the envelope.
 *
 * UNFORGE_COMPANION|BEGIN|count|selectedCompanionId
 * UNFORGE_COMPANION|PET|id|slot|nameB64|class|level|state|hp|maxHp|active|mode|style|packCap|incapMs
 * UNFORGE_COMPANION|ABILITY|petId|abilityIdB64|nameB64|remainingMs|totalMs
 * UNFORGE_COMPANION|FLAGS|autoLoot|emergencyHeal
 * UNFORGE_COMPANION|END
 */
final class UnforgeCompanionProtocol
{
	static final String PREFIX = "UNFORGE_COMPANION|";

	enum Kind
	{
		BEGIN,
		PET,
		ABILITY,
		FLAGS,
		END,
	}

	static final class Pet
	{
		long id;
		int slot;
		String name;
		String companionClass;
		int level;
		String state;
		int hp;
		int maxHp;
		boolean active;
		String mode;
		String style;
		int packCapacity;
		long incapacitatedMs;

		@Override
		public String toString()
		{
			return name + "  Lv" + level + "  " + state;
		}
	}

	static final class Ability
	{
		long petId;
		String id;
		String name;
		long remainingMs;
		long totalMs;
	}

	static final class Parsed
	{
		Kind kind;
		int count;
		long selectedId = -1;
		Pet pet;
		Ability ability;
		boolean autoLoot;
		boolean emergencyHeal;
	}

	private UnforgeCompanionProtocol()
	{
	}

	static Parsed parse(String message)
	{
		if (message == null || !message.startsWith(PREFIX))
		{
			return null;
		}
		String[] parts = message.substring(PREFIX.length()).split("\\|", -1);
		if (parts.length == 0)
		{
			return null;
		}
		Parsed parsed = new Parsed();
		try
		{
			switch (parts[0])
			{
				case "BEGIN":
					parsed.kind = Kind.BEGIN;
					parsed.count = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
					parsed.selectedId = parts.length > 2 ? Long.parseLong(parts[2]) : -1;
					return parsed;
				case "PET":
					if (parts.length < 14)
					{
						return null;
					}
					Pet pet = new Pet();
					pet.id = Long.parseLong(parts[1]);
					pet.slot = Integer.parseInt(parts[2]);
					pet.name = dec(parts[3]);
					pet.companionClass = parts[4];
					pet.level = Integer.parseInt(parts[5]);
					pet.state = parts[6];
					pet.hp = Integer.parseInt(parts[7]);
					pet.maxHp = Integer.parseInt(parts[8]);
					pet.active = "1".equals(parts[9]);
					pet.mode = parts[10];
					pet.style = parts[11];
					pet.packCapacity = Integer.parseInt(parts[12]);
					pet.incapacitatedMs = Long.parseLong(parts[13]);
					parsed.kind = Kind.PET;
					parsed.pet = pet;
					return parsed;
				case "ABILITY":
					if (parts.length < 6)
					{
						return null;
					}
					Ability ability = new Ability();
					ability.petId = Long.parseLong(parts[1]);
					ability.id = dec(parts[2]);
					ability.name = dec(parts[3]);
					ability.remainingMs = Long.parseLong(parts[4]);
					ability.totalMs = Long.parseLong(parts[5]);
					parsed.kind = Kind.ABILITY;
					parsed.ability = ability;
					return parsed;
				case "FLAGS":
					if (parts.length < 3)
					{
						return null;
					}
					parsed.kind = Kind.FLAGS;
					parsed.autoLoot = "1".equals(parts[1]);
					parsed.emergencyHeal = "1".equals(parts[2]);
					return parsed;
				case "END":
					parsed.kind = Kind.END;
					return parsed;
				default:
					return null;
			}
		}
		catch (IllegalArgumentException ex)
		{
			return null;
		}
	}

	private static String dec(String encoded)
	{
		try
		{
			return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException ex)
		{
			return encoded;
		}
	}
}
