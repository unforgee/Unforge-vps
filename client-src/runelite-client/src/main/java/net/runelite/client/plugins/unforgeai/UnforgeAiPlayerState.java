package net.runelite.client.plugins.unforgeai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Locale;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Builds a compact, read-only snapshot of the local player for the local bridge.
 *
 * <p>The snapshot contains only what the AI4FUN Studio agent needs to reason about
 * the live session: identity, position, combat state, skills and (optionally)
 * inventory and equipment. It is generated exclusively from the live RuneLite
 * {@link Client} and never from disk, network or user input.</p>
 *
 * <p>This must be called on the client thread. It is deliberately cheap: no
 * allocation beyond one small JSON tree, no I/O, no logging.</p>
 */
final class UnforgeAiPlayerState
{
	private UnforgeAiPlayerState()
	{
	}

	/**
	 * @param client          live RuneLite client, read on the client thread
	 * @param includeInventory include inventory and equipment contents
	 * @return a snapshot object, or {@code null} when the player is not in game
	 */
	static JsonObject capture(Client client, boolean includeInventory)
	{
		if (client == null)
		{
			return null;
		}

		GameState gameState = client.getGameState();
		Player local = client.getLocalPlayer();

		JsonObject state = new JsonObject();
		state.addProperty("gameState", gameState == null ? "UNKNOWN" : gameState.name());
		state.addProperty("loggedIn", gameState == GameState.LOGGED_IN);
		state.addProperty("tick", client.getTickCount());

		if (local == null)
		{
			return state;
		}

		String name = local.getName();
		if (name != null)
		{
			state.addProperty("name", name);
		}
		state.addProperty("combatLevel", local.getCombatLevel());
		state.addProperty("world", client.getWorld());

		WorldPoint position = local.getWorldLocation();
		if (position != null)
		{
			JsonObject location = new JsonObject();
			location.addProperty("x", position.getX());
			location.addProperty("y", position.getY());
			location.addProperty("z", position.getPlane());
			location.addProperty("regionId", ((position.getX() >> 6) << 8) | (position.getY() >> 6));
			location.addProperty("chunkX", position.getX() >> 3);
			location.addProperty("chunkY", position.getY() >> 3);
			state.add("position", location);
		}

		state.addProperty("animation", local.getAnimation());
		state.addProperty("poseAnimation", local.getPoseAnimation());
		state.addProperty("health", client.getBoostedSkillLevel(Skill.HITPOINTS));
		state.addProperty("maxHealth", client.getRealSkillLevel(Skill.HITPOINTS));
		state.addProperty("runEnergy", client.getEnergy());
		state.addProperty("specialAttack", percent(client.getVarpValue(VarPlayerID.SA_ENERGY)));

		Actor interacting = local.getInteracting();
		if (interacting != null)
		{
			JsonObject target = new JsonObject();
			String targetName = interacting.getName();
			if (targetName != null)
			{
				target.addProperty("name", targetName);
			}
			target.addProperty("combatLevel", interacting.getCombatLevel());
			state.add("interacting", target);
		}

		state.add("skills", skills(client));

		if (includeInventory)
		{
			state.add("inventory", container(client, InventoryID.INVENTORY));
			state.add("equipment", container(client, InventoryID.EQUIPMENT));
		}

		return state;
	}

	private static JsonObject skills(Client client)
	{
		JsonObject skills = new JsonObject();
		JsonObject levels = new JsonObject();
		JsonObject base = new JsonObject();
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			String key = skill.name().toLowerCase(Locale.ROOT);
			levels.addProperty(key, client.getBoostedSkillLevel(skill));
			base.addProperty(key, client.getRealSkillLevel(skill));
		}
		skills.add("boosted", levels);
		skills.add("base", base);
		return skills;
	}

	private static JsonArray container(Client client, InventoryID inventoryID)
	{
		JsonArray slots = new JsonArray();
		ItemContainer container = client.getItemContainer(inventoryID);
		if (container == null)
		{
			return slots;
		}
		Item[] items = container.getItems();
		if (items == null)
		{
			return slots;
		}
		for (int slot = 0; slot < items.length; slot++)
		{
			Item item = items[slot];
			if (item == null || item.getId() <= 0)
			{
				continue;
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("slot", slot);
			entry.addProperty("id", item.getId());
			entry.addProperty("quantity", item.getQuantity());
			slots.add(entry);
		}
		return slots;
	}

	/** RuneScape stores special attack as 0..1000; expose it as whole percent. */
	private static int percent(int raw)
	{
		return Math.max(0, Math.min(100, raw / 10));
	}
}
