package net.runelite.client.plugins.unforgecombatmeter;

import java.awt.Color;
import javax.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.plugins.unforgecommon.UnforgeIcons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(name = "Unforge Combat Meter", description = "Recount-style combat meter for players and companions", tags = {"combat", "damage", "healing", "companion"}, enabledByDefault = true)
public class UnforgeCombatMeterPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(UnforgeCombatMeterPlugin.class);
	@Inject Client client; @Inject ClientToolbar toolbar; @Inject UnforgeCombatMeterConfig config;
	private final CombatMeterModel model = new CombatMeterModel();
	private UnforgeCombatMeterPanel panel; private NavigationButton button; private int idleTicks;

	@Provides
	UnforgeCombatMeterConfig provideConfig(ConfigManager manager) { return manager.getConfig(UnforgeCombatMeterConfig.class); }

	@Override protected void startUp() { panel = new UnforgeCombatMeterPanel(model, config); button = NavigationButton.builder().tooltip("Combat Meter").icon(UnforgeIcons.letter(new Color(190, 70, 40), "⚔")).priority(5).panel(panel).build(); toolbar.addNavigation(button); }
	@Override protected void shutDown() { if (button != null) toolbar.removeNavigation(button); model.reset(); panel = null; }

	@Subscribe public void onGameStateChanged(GameStateChanged event) { if (event.getGameState() != net.runelite.api.GameState.LOGGED_IN) model.reset(); }
	@Subscribe public void onGameTick(GameTick event) { if (++idleTicks > 20 && model.elapsedSeconds() > 0) { model.reset(); idleTicks = 0; } if (panel != null) panel.refresh(); }
	@Subscribe public void onHitsplatApplied(HitsplatApplied event)
	{
		Hitsplat hit = event.getHitsplat(); int amount = hit.getAmount(); if (amount <= 0) return; Actor actor = event.getActor(); String name;
		CombatMeterModel.Kind kind;
		if (hit.isMine() && actor instanceof NPC) { name = client.getLocalPlayer() == null ? "Player" : client.getLocalPlayer().getName(); kind = CombatMeterModel.Kind.DAMAGE; }
		else if (hit.isOthers() && actor instanceof NPC) { name = "Companion / party"; kind = CombatMeterModel.Kind.DAMAGE; }
		else if (actor instanceof Player) { name = actor.getName(); kind = CombatMeterModel.Kind.TAKEN; }
		else return;
		idleTicks = 0; model.actor(name).add(kind, amount, actor instanceof NPC ? ((NPC) actor).getName() : "Attack");
		if (config.debugLogging()) log.debug("combat-meter event actor={} amount={} kind={}", name, amount, kind);
	}
}
