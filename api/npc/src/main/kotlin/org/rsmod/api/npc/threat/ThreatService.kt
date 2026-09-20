package org.rsmod.api.npc.threat

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.bonus.EquipmentCombatStats
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionPlayer
import org.rsmod.game.queue.WorldQueueList

/**
 * Per-npc PvM threat/aggro system.
 *
 * Every npc that takes player damage (or is taunted) lazily gets its own [ThreatTable] - two npcs
 * never share threat state. Damage, ally healing, shielding and taunts feed the tables; target
 * re-evaluation happens on those meaningful events only, not on a global tick scan.
 *
 * The service deliberately rides on top of the existing interaction pipeline: switching a target is
 * just [Npc.opPlayer2]/[Npc.apPlayer2] against the new holder, exactly like combat retaliation. It
 * never engages an npc that is not in combat - damage on an idle npc still flows through the normal
 * retaliation path - except for taunts, which are an explicit pull.
 *
 * Role knowledge is supplied from content modules through [registerRoleResolver] (companions map
 * their bots to `CompanionClass`, players can pick a role with `::role`); equipment threat bonuses
 * come from [WornBonuses.threatBonusBps].
 */
@Singleton
public class ThreatService
@Inject
constructor(
    private val mapClock: MapClock,
    private val aiPlayers: AiPlayerInteractions,
    private val equipmentStats: EquipmentCombatStats,
    private val worldQueues: WorldQueueList,
) {
    private val tables = WeakHashMap<Npc, ThreatTable>()

    /** player uuid -> npcs whose threat table contains that player (reverse index for heals). */
    private val engagedBy = HashMap<Long, MutableSet<Npc>>()

    /** player uuid -> last seen entity, so table keys can be resolved back to players. */
    private val knownPlayers = HashMap<Long, Player>()

    private val roleResolvers = CopyOnWriteArrayList<ThreatRoleResolver>()

    /** Session-scoped player role overrides (`::role`), keyed by character id. */
    private val roleOverrides = ConcurrentHashMap<Long, CombatRole>()

    public fun registerRoleResolver(resolver: ThreatRoleResolver) {
        roleResolvers += resolver
    }

    // --- roles ---

    public fun setRoleOverride(player: Player, role: CombatRole?) {
        val cid = player.characterId.toLong()
        if (role == null) roleOverrides.remove(cid) else roleOverrides[cid] = role
    }

    public fun roleOf(player: Player): CombatRole {
        roleOverrides[player.characterId.toLong()]?.let {
            return it
        }
        for (resolver in roleResolvers) {
            resolver.roleOf(player)?.let {
                return it
            }
        }
        return CombatRole.DAMAGE
    }

    /** Total threat multiplier for [player]: role multiplier x equipment threat bonus. */
    public fun threatMultiplier(player: Player): Double {
        val roleBps =
            when (roleOf(player)) {
                CombatRole.TANK -> ThreatConfig.TANK_THREAT_BPS
                CombatRole.HYBRID -> ThreatConfig.HYBRID_THREAT_BPS
                CombatRole.SUPPORT,
                CombatRole.DAMAGE -> ThreatConfig.NORMAL_THREAT_BPS
            }
        return roleBps / 10_000.0 * equipmentStats.stats(player).threatMultiplier
    }

    // --- threat sources ---

    /** Records [damage] dealt by [player] to [npc] and re-evaluates the npc's target. */
    public fun onDamage(npc: Npc, player: Player, damage: Int) {
        if (damage <= 0 || !npc.isValidTarget()) {
            return
        }
        if (tables[npc]?.mode == ThreatMode.DISABLED) {
            return
        }
        val uuid = playerUuid(player) ?: return
        val table = tableOf(npc)
        val amount = (damage * threatMultiplier(player)).toLong()
        val total = table.add(uuid, amount, cycle(), ThreatConfig.DECAY_BPS_PER_CYCLE)
        track(uuid, player, npc)
        debug(
            "[THREAT] NPC=${npc.visType.name} Player=${player.username} Source=DAMAGE " +
                "Amount=$amount Total=$total"
        )
        reevaluate(npc, "DAMAGE")
    }

    /**
     * Distributes healing threat to every npc currently engaged with [allies] or the [healer]
     * itself. Only **effective** healing should reach this method - callers cap overhealing before
     * calling.
     */
    public fun onHeal(healer: Player, allies: Collection<Player>, effectiveAmount: Int) {
        distributeSupportThreat(healer, allies, effectiveAmount, ThreatConfig.HEAL_THREAT_BPS)
    }

    /** Same as [onHeal] for shield/protection granted to allies. */
    public fun onShield(supporter: Player, allies: Collection<Player>, shieldAmount: Int) {
        distributeSupportThreat(supporter, allies, shieldAmount, ThreatConfig.SHIELD_THREAT_BPS)
    }

    private fun distributeSupportThreat(
        supporter: Player,
        allies: Collection<Player>,
        amount: Int,
        threatBps: Int,
    ) {
        if (amount <= 0) {
            return
        }
        val supporterUuid = playerUuid(supporter) ?: return
        val threat = amount.toLong() * threatBps / 10_000
        if (threat <= 0) {
            return
        }
        val npcs = LinkedHashSet<Npc>()
        for (ally in allies) {
            playerUuid(ally)?.let { engagedBy[it] }?.let { npcs += it }
        }
        engagedBy[supporterUuid]?.let { npcs += it }
        for (npc in npcs) {
            if (!npc.isValidTarget()) {
                continue
            }
            val table = tables[npc] ?: continue
            if (table.mode == ThreatMode.DISABLED) {
                continue
            }
            val total = table.add(supporterUuid, threat, cycle(), ThreatConfig.DECAY_BPS_PER_CYCLE)
            track(supporterUuid, supporter, npc)
            debug(
                "[THREAT] NPC=${npc.visType.name} Player=${supporter.username} Source=SUPPORT " +
                    "Amount=$threat Total=$total"
            )
            reevaluate(npc, "SUPPORT")
        }
    }

    // --- forced targeting ---

    /**
     * Taunts [npc] onto [tank]: the tank is raised to `top threat + TAUNT_THREAT_BONUS` and the npc
     * is force-targeted for [ThreatConfig.TAUNT_DURATION_CYCLES]. A recheck is scheduled at expiry
     * so normal threat targeting resumes even if nothing else touches the table.
     */
    public fun taunt(npc: Npc, tank: Player): Boolean {
        if (!npc.isValidTarget() || !tank.isValidTarget()) {
            return false
        }
        val uuid = playerUuid(tank) ?: return false
        val table = tableOf(npc)
        if (table.mode == ThreatMode.DISABLED) {
            return false
        }
        val until = cycle() + ThreatConfig.TAUNT_DURATION_CYCLES
        table.forceTarget(
            key = uuid,
            untilCycle = until,
            cycle = cycle(),
            tauntBonus = ThreatConfig.TAUNT_THREAT_BONUS,
            decayBps = ThreatConfig.DECAY_BPS_PER_CYCLE,
        )
        track(uuid, tank, npc)
        engage(npc, tank, "TAUNT")
        // When the taunt expires the npc re-evaluates immediately instead of staying glued to the
        // tank until the next threat event happens to arrive.
        worldQueues.add(ThreatConfig.TAUNT_DURATION_CYCLES + 1) { reevaluate(npc, "TAUNT_EXPIRY") }
        return true
    }

    /**
     * Boss-script escape hatch: forces [npc] onto [target] for [cycles] without touching threat
     * numbers. Works even in [ThreatMode.SCRIPTED]; ignored when [ThreatMode.DISABLED].
     */
    public fun scriptedTarget(npc: Npc, target: Player, cycles: Int) {
        if (!npc.isValidTarget() || !target.isValidTarget()) {
            return
        }
        val uuid = playerUuid(target) ?: return
        val table = tableOf(npc)
        if (table.mode == ThreatMode.DISABLED) {
            return
        }
        table.forceTarget(
            key = uuid,
            untilCycle = cycle() + cycles,
            cycle = cycle(),
            tauntBonus = 0,
            decayBps = ThreatConfig.DECAY_BPS_PER_CYCLE,
            scripted = true,
        )
        track(uuid, target, npc)
        engage(npc, target, "SCRIPTED")
        worldQueues.add(cycles + 1) { reevaluate(npc, "SCRIPTED_EXPIRY") }
    }

    public fun setMode(npc: Npc, mode: ThreatMode) {
        tableOf(npc).mode = mode
    }

    // --- target evaluation ---

    /**
     * Re-evaluates [npc]'s target against its threat table. Never engages a combat-idle npc on
     * threat alone (initial aggro stays with the normal retaliation path); forced targets (taunt,
     * scripted) always engage.
     */
    public fun reevaluate(npc: Npc, reason: String) {
        val table = tables[npc] ?: return
        if (!npc.isSlotAssigned) {
            clearNpc(npc)
            return
        }
        if (table.mode == ThreatMode.DISABLED) {
            return
        }

        val cycle = cycle()
        val currentKey = currentTarget(npc)?.let { playerUuid(it) }
        val selected =
            table.selectTarget(
                currentKey = currentKey,
                cycle = cycle,
                decayBps = ThreatConfig.DECAY_BPS_PER_CYCLE,
                valid = { key -> isEngageable(npc, key) },
                distance = { key -> distanceTo(npc, key) },
            ) ?: return

        if (selected == ThreatTable.DROP_TARGET) {
            return
        }
        val forced = table.forcedTarget(cycle) == selected
        // Threat alone never pulls a combat-idle npc into a fight - only forced targets do.
        if (!forced && table.mode != ThreatMode.NORMAL) {
            return
        }
        if (!forced && !isEngagedWithPlayer(npc)) {
            return
        }
        val target = knownPlayers[selected] ?: return
        if (target === currentTarget(npc)) {
            return
        }
        engage(npc, target, if (forced) "FORCED" else reason)
    }

    // --- cleanup ---

    /** Drops the npc's threat table and all reverse-index references to it. */
    public fun clearNpc(npc: Npc) {
        val table = tables.remove(npc) ?: return
        for (key in table.entries.keys) {
            val set = engagedBy[key]
            set?.remove(npc)
            if (set != null && set.isEmpty()) {
                engagedBy.remove(key)
            }
        }
    }

    /** Removes every threat entry and reverse-index reference held by [player]. */
    public fun removePlayer(player: Player) {
        val uuid = playerUuid(player) ?: return
        knownPlayers.remove(uuid)
        val npcs = engagedBy.remove(uuid) ?: return
        for (npc in npcs) {
            tables[npc]?.remove(uuid)
        }
    }

    // --- debug ---

    /** Current combat target of [npc], if it is interacting with a player. */
    public fun currentTarget(npc: Npc): Player? = (npc.interaction as? InteractionPlayer)?.target

    /** Snapshot of [npc]'s threat table for `::threat`: (player, threat) sorted descending. */
    public fun describe(npc: Npc): List<Pair<Player, Long>> {
        val table = tables[npc] ?: return emptyList()
        val cycle = cycle()
        return table.entries.entries
            .mapNotNull { (key, entry) ->
                val player = knownPlayers[key] ?: return@mapNotNull null
                player to table.threatOf(key, cycle, ThreatConfig.DECAY_BPS_PER_CYCLE)
            }
            .sortedByDescending { it.second }
    }

    public fun tableMode(npc: Npc): ThreatMode = tables[npc]?.mode ?: ThreatMode.NORMAL

    public fun forcedTarget(npc: Npc): Player? =
        tables[npc]?.forcedTarget(cycle())?.let { knownPlayers[it] }

    // --- internals ---

    private fun tableOf(npc: Npc): ThreatTable = tables.getOrPut(npc) { ThreatTable() }

    private fun track(uuid: Long, player: Player, npc: Npc) {
        knownPlayers[uuid] = player
        engagedBy.getOrPut(uuid) { mutableSetOf() } += npc
    }

    private fun playerUuid(player: Player): Long? = player.uuid

    private fun isEngagedWithPlayer(npc: Npc): Boolean = npc.interaction is InteractionPlayer

    private fun isEngageable(npc: Npc, key: Long): Boolean {
        val player = knownPlayers[key] ?: return false
        if (!player.isValidTarget()) {
            return false
        }
        return player.coords.level == npc.coords.level &&
            distanceTo(npc, key) <= ThreatConfig.TARGET_MAX_DISTANCE
    }

    private fun distanceTo(npc: Npc, key: Long): Int {
        val player = knownPlayers[key] ?: return Int.MAX_VALUE
        val dx = abs(player.coords.x - npc.coords.x)
        val dz = abs(player.coords.z - npc.coords.z)
        return maxOf(dx, dz)
    }

    /** Melee npcs walk adjacent; ranged/magic npcs approach at their attack range. */
    private fun engage(npc: Npc, target: Player, reason: String) {
        val old = currentTarget(npc)
        if (old !== target) {
            debug(
                "[AGGRO] NPC=${npc.visType.name} OldTarget=${old?.username} " +
                    "NewTarget=${target.username} Reason=$reason"
            )
        }
        if (usesAttackRange(npc)) {
            npc.apPlayer2(target, aiPlayers)
        } else {
            npc.opPlayer2(target, aiPlayers)
        }
    }

    /**
     * Whether the npc attacks from a distance: mirrors `NpcAttackStyle.from` - the
     * `npc_attack_style` param maps `0`=melee, `1`=ranged, `2`=magic - without depending on the
     * combat-commons enum (which lives in a module that depends on this one).
     */
    private fun usesAttackRange(npc: Npc): Boolean {
        val interaction = npc.interaction
        if (interaction is InteractionPlayer) {
            return interaction.hasApTrigger
        }
        return (npc.visType.paramOrNull(params.npc_attack_style) ?: 0) != 0
    }

    private fun cycle(): Int = mapClock.cycle

    private fun debug(message: String) {
        if (ThreatConfig.DEBUG_THREAT) {
            logger.debug { message }
        }
    }

    private companion object {
        val logger = InlineLogger()
    }
}
