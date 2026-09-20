package net.runelite.client.plugins.unforgecompanion;

import java.awt.Color;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.MessageNode;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.WorldView;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.unforgecommon.UnforgeCheats;
import net.runelite.client.plugins.unforgecommon.UnforgeIcons;
import net.runelite.client.plugins.unforgecompanion.UnforgeCompanionProtocol.Parsed;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * Unforge Companion Plugin:
 * Adds the right-click "Attack (Companion)" menu option to all combat and attackable NPCs.
 * Selecting this option commands both the player and active companion bot to attack the NPC in unison,
 * awarding full combat XP, monster kill bonus experience, and talent points to the companion.
 *
 * Also hosts the right-sidebar companion panel: squad selector, live HP, ability
 * cooldowns, combat-mode switching, auto-loot / emergency-heal toggles and
 * shortcuts that open the in-game companion Hub screens. The panel reads the
 * `UNFORGE_COMPANION` console snapshot (polled while open) and only ever sends
 * `playerCommand` cheats back - the server owns all state.
 */
@PluginDescriptor(
	name = "Unforge Companion",
	description = "Companion squad panel + right-click Attack with companion",
	tags = {"unforge", "companion", "pet", "combat", "attack"},
	enabledByDefault = true
)
public class UnforgeCompanionPlugin extends Plugin
{
	/** Panel refresh cadence while the sidebar tab is open (game ticks). */
	private static final int POLL_TICKS = 3;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	private UnforgeCompanionPanel panel;
	private NavigationButton navButton;
	private int tickCounter;

	@Override
	protected void startUp()
	{
		panel = new UnforgeCompanionPanel(this::sendCommand);
		navButton = NavigationButton.builder()
			.tooltip("Companions")
			.icon(UnforgeIcons.letter(new Color(0xff, 0x98, 0x1f), "C"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
	}

	private void sendCommand(String command)
	{
		UnforgeCheats.send(clientThread, client, command);
	}

	// ------------------------------------------------------------------ polling

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (panel == null
			|| !panel.isPanelActive()
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		if (++tickCounter >= POLL_TICKS)
		{
			tickCounter = 0;
			sendCommand("companionstatus");
		}
	}

	// ------------------------------------------------------------------ protocol

	@Subscribe(priority = 1000)
	public void onChatMessage(ChatMessage event)
	{
		// Same Console side channel as the Forge plugin (protocol id 99).
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.CONSOLE && type != ChatMessageType.ENGINE)
		{
			return;
		}

		Parsed parsed = UnforgeCompanionProtocol.parse(event.getMessage());
		if (parsed == null)
		{
			return;
		}

		SwingUtilities.invokeLater(() -> apply(parsed));

		// Remove the node entirely so the side channel leaves no blank chat lines.
		MessageNode node = event.getMessageNode();
		if (node != null)
		{
			ChatLineBuffer buffer = client.getChatLineMap().get(node.getType().getType());
			if (buffer != null)
			{
				buffer.removeMessageNode(node);
			}
		}
		event.setMessage("");
	}

	private void apply(Parsed message)
	{
		if (panel == null)
		{
			return;
		}
		switch (message.kind)
		{
			case BEGIN:
				panel.beginSnapshot(message.count, message.selectedId);
				break;
			case PET:
				panel.addPet(message.pet);
				break;
			case ABILITY:
				panel.addAbility(message.ability);
				break;
			case FLAGS:
				panel.setFlags(message.autoLoot, message.emergencyHeal);
				break;
			case END:
				panel.endSnapshot();
				break;
			default:
				break;
		}
	}

	// ------------------------------------------------------------------ menu

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		MenuEntry entry = event.getMenuEntry();
		MenuAction type = entry.getType();
		int identifier = event.getIdentifier();

		// Add option once per menu build when examine entry is constructed
		if (type == MenuAction.EXAMINE_NPC)
		{
			NPC npc = entry.getNpc();
			if (npc == null)
			{
				WorldView wv = client.getTopLevelWorldView();
				if (wv != null)
				{
					for (NPC n : wv.npcs())
					{
						if (n.getIndex() == identifier)
						{
							npc = n;
							break;
						}
					}
				}
			}

			if (npc != null)
			{
				NPCComposition comp = npc.getComposition();
				// Only show on combat / attackable NPCs
				if (comp != null && isAttackable(comp))
				{
					final int npcIndex = npc.getIndex();
					client.createMenuEntry(-1)
						.setOption("<col=ff981f>Attack (Companion)</col>")
						.setTarget(event.getTarget())
						.setType(MenuAction.RUNELITE)
						.onClick(e -> {
							// Command server to order companion and player to attack this target
							UnforgeCheats.send(clientThread, client, "petattack " + npcIndex);
						});
				}
			}
		}
	}

	private boolean isAttackable(NPCComposition comp)
	{
		if (comp.getCombatLevel() > 0)
		{
			return true;
		}
		String[] actions = comp.getActions();
		if (actions != null)
		{
			for (String action : actions)
			{
				if (action != null && action.equalsIgnoreCase("Attack"))
				{
					return true;
				}
			}
		}
		return false;
	}
}
