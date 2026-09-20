package org.rsmod.content.other.commands

import jakarta.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.companion.Companion
import org.rsmod.api.companion.CompanionService
import org.rsmod.api.companion.CompanionSkillService
import org.rsmod.api.companion.CompanionState
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.npc.threat.ThreatService
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Feeds the companion SUPPORT/TANK progression tracks ([CompanionSkillService]) from observable
 * combat state, without touching the role-spell or hit-processing code owned by other work.
 *
 * Currently wired here (fully functional, no integration needed):
 * - **TANK / damage taken** - each cycle compares every active companion bot's hitpoints against
 *   the previous observation; a drop is hostile damage suffered and is awarded through
 *   [CompanionSkillService.awardDamageTankled], which additionally requires the companion to be in
 *   [CompanionState.COMBAT] (idle/AFK soaking earns nothing).
 * - **TANK / aggro held** - when a companion is in combat and any live npc in the owner's zone has
 *   the bot as its threat target (`ThreatService.currentTarget`) or top threat holder
 *   (`ThreatService.describe`), [CompanionSkillService.awardAggroHeld] fires; its internal interval
 *   throttle turns per-cycle reports into one payout per
 *   `CompanionSkillXp.AGGRO_HOLD_INTERVAL_MILLIS`.
 * - **Combat-window bookkeeping** - companions observed in COMBAT state call
 *   [CompanionSkillService.noteCombatActivity], which opens the grace window that lets SUPPORT
 *   awards (heals/shields/buffs/cleanses) land during and shortly after fights.
 *
 * Event-source call sites (all wired):
 * - `CompanionRoleSpells.applySupportHeal` -> `awardEffectiveHealing` (diff is already capped at
 *   missing hp by `computeSupportHealAmount`)
 * - `CompanionRoleSpells.applySupportBuff` (announce == true) -> `awardBuff`
 * - `CompanionRoleSpells.processTankSpells` taunt branch -> `awardTaunt`
 * - `CompanionRuntimeScript.syncBotVitals` -> `awardProtection` (mitigated damage)
 *
 * Future sources (cleanse/shield effects when they exist on companions):
 * - real harmful-effect removal -> `awardCleanse(ownerId, id, effectsRemoved)`
 * - shield/absorption granted -> `awardShielding(ownerId, id, appliedPoints)`
 */
public class CompanionSkillXpScript
@Inject
constructor(
    private val companions: CompanionService,
    private val skillXp: CompanionSkillService,
    private val players: PlayerList,
    private val npcRegistry: NpcRegistry,
    private val companionPlayerManager: CompanionPlayerManager,
    private val threat: ThreatService,
) : PluginScript() {

    /** companion id -> bot hitpoints observed on the previous cycle. */
    private val lastBotHitpoints = ConcurrentHashMap<Long, Int>()

    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { players.forEach(::observe) }
    }

    private fun observe(player: Player) {
        if (!player.isSlotAssigned) return
        val ownerId = runCatching { player.characterId.toLong() }.getOrNull() ?: return
        val active = companions.owned(ownerId).filter { it.active }
        if (active.isEmpty()) return
        val nearbyNpcs =
            npcRegistry
                .findAll(ZoneKey.from(player.coords))
                .filter { it.isValidTarget() && it.hitpoints > 0 }
                .toList()
        for (companion in active) {
            if (companion.state == CompanionState.COMBAT) {
                skillXp.noteCombatActivity(companion.id)
            }
            val bot = companionPlayerManager.getPlayer(companion.id)
            if (bot == null || !bot.isSlotAssigned) {
                lastBotHitpoints.remove(companion.id)
                continue
            }
            observeDamageTaken(ownerId, companion, bot)
            observeAggro(ownerId, companion, bot, nearbyNpcs)
        }
    }

    /**
     * Hitpoints lost by the bot since the last cycle count as tanked hostile damage. Bots have no
     * self-damage paths, and [CompanionSkillService.awardDamageTankled] additionally requires the
     * companion to be fighting, so ambient drains while idle cannot be farmed.
     */
    private fun observeDamageTaken(ownerId: Long, companion: Companion, bot: Player) {
        val previous = lastBotHitpoints.put(companion.id, bot.hitpoints)
        if (previous != null && bot.hitpoints < previous) {
            skillXp.awardDamageTankled(ownerId, companion.id, previous - bot.hitpoints)
        }
    }

    /**
     * The companion holds aggro when a live npc near the owner is currently targeting the bot or
     * has the bot at the top of its threat table. One qualifying npc is enough - the award is a
     * throttled flat payout, not per-npc.
     */
    private fun observeAggro(ownerId: Long, companion: Companion, bot: Player, npcs: List<Npc>) {
        if (companion.state != CompanionState.COMBAT) return
        val holdsAggro =
            npcs.any { npc ->
                threat.currentTarget(npc) === bot ||
                    threat.describe(npc).firstOrNull()?.first === bot
            }
        if (holdsAggro) {
            skillXp.awardAggroHeld(ownerId, companion.id)
        }
    }
}
