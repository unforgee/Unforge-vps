package net.runelite.client.plugins.unforgecommon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;

/**
 * Composition lookups. All search methods MUST run on the client thread
 * (the injected client asserts this); call them inside clientThread.invokeLater.
 */
public final class UnforgeSearch
{
	private static final int MAX_RESULTS = 250;
	private static final int MISS_BREAK = 2048;
	private static final int ID_SPACE = 65536;

	private UnforgeSearch()
	{
	}

	public static final class ItemResult
	{
		public final int id;
		public final String name;
		public final int storePrice;

		ItemResult(int id, String name, int storePrice)
		{
			this.id = id;
			this.name = name != null ? name.replace(" (Members)", "") : "(unnamed)";
			this.storePrice = storePrice;
		}

		@Override
		public String toString()
		{
			return name + "  (id " + id + ")";
		}
	}

	public static final class EntityResult
	{
		public final int id;
		public final String name;
		public final String actions;
		public final int combatLevel;
		public final boolean npc;

		EntityResult(int id, String name, String[] actions, int combatLevel, boolean npc)
		{
			this.id = id;
			this.name = name != null && !name.equalsIgnoreCase("null") ? name : "(unnamed)";
			this.actions = actions == null
				? ""
				: String.join(", ", Arrays.stream(actions).filter(a -> a != null && !a.equalsIgnoreCase("null")).toArray(String[]::new));
			this.combatLevel = combatLevel;
			this.npc = npc;
		}

		@Override
		public String toString()
		{
			String lvl = npc && combatLevel > 0 ? " • lvl " + combatLevel : "";
			return name + "  (id " + id + lvl + ")";
		}
	}

	/** Client thread only. */
	public static List<ItemResult> items(Client client, String query)
	{
		int bound = client.getItemCount();
		if (bound <= 0)
		{
			bound = ID_SPACE;
		}
		List<ItemResult> out = new ArrayList<>(128);
		for (int id = 0; id < bound && out.size() < MAX_RESULTS; id++)
		{
			try
			{
				ItemComposition def = client.getItemDefinition(id);
				if (def != null && nameMatches(def.getName(), query))
				{
					out.add(new ItemResult(id, def.getName(), def.getPrice()));
				}
			}
			catch (Throwable ignored)
			{
			}
		}
		return out;
	}

	/** Client thread only. */
	public static List<EntityResult> npcs(Client client, String query, boolean byAction)
	{
		List<EntityResult> out = new ArrayList<>(128);
		int misses = 0;
		for (int id = 0; id < ID_SPACE && out.size() < MAX_RESULTS; id++)
		{
			try
			{
				NPCComposition def = client.getNpcDefinition(id);
				if (def == null)
				{
					if (++misses > MISS_BREAK)
					{
						break;
					}
					continue;
				}
				misses = 0;
				if (byAction ? actionsMatch(def.getActions(), query) : nameMatches(def.getName(), query))
				{
					out.add(new EntityResult(id, def.getName(), def.getActions(), def.getCombatLevel(), true));
				}
			}
			catch (Throwable t)
			{
				if (++misses > MISS_BREAK)
				{
					break;
				}
			}
		}
		return out;
	}

	/** Client thread only. */
	public static List<EntityResult> objects(Client client, String query, boolean byAction)
	{
		List<EntityResult> out = new ArrayList<>(128);
		int misses = 0;
		for (int id = 0; id < ID_SPACE && out.size() < MAX_RESULTS; id++)
		{
			try
			{
				ObjectComposition def = client.getObjectDefinition(id);
				if (def == null)
				{
					if (++misses > MISS_BREAK)
					{
						break;
					}
					continue;
				}
				misses = 0;
				if (byAction ? actionsMatch(def.getActions(), query) : nameMatches(def.getName(), query))
				{
					out.add(new EntityResult(id, def.getName(), def.getActions(), -1, false));
				}
			}
			catch (Throwable t)
			{
				if (++misses > MISS_BREAK)
				{
					break;
				}
			}
		}
		return out;
	}

	static boolean nameMatches(String name, String query)
	{
		return name != null && !name.equalsIgnoreCase("null") && name.toLowerCase().contains(query);
	}

	static boolean actionsMatch(String[] actions, String query)
	{
		if (actions == null)
		{
			return false;
		}
		for (String action : actions)
		{
			if (action != null && !action.equalsIgnoreCase("null") && action.toLowerCase().contains(query))
			{
				return true;
			}
		}
		return false;
	}
}
