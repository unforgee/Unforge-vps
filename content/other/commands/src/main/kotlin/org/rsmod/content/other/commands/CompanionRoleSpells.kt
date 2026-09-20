package org.rsmod.content.other.commands

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.combat.accuracy.player.PlayerMagicAccuracy
import org.rsmod.api.combat.formulas.accuracy.magic.MagicAccuracyOperations
import org.rsmod.api.combat.formulas.maxhit.magic.MagicMaxHitOperations
import org.rsmod.api.companion.CombatTelemetryEvent
import org.rsmod.api.companion.Companion as CompanionRecord
import org.rsmod.api.companion.CompanionAbilityCatalog
import org.rsmod.api.companion.CompanionAbilityTarget
import org.rsmod.api.companion.CompanionAttackStyle
import org.rsmod.api.companion.CompanionClass
import org.rsmod.api.companion.CompanionService
import org.rsmod.api.companion.CompanionSkillService
import org.rsmod.api.companion.CompanionState
import org.rsmod.api.companion.CompanionTelemetryService
import org.rsmod.api.config.refs.stats
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.npc.threat.ThreatConfig
import org.rsmod.api.npc.threat.ThreatService
import org.rsmod.api.player.bonus.EquipmentCombatStats
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statBoost
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

public enum class RoleSpellType {
    HEAL,
    BUFF,
    DEFENSIVE,
    TAUNT,
    DAMAGE_BURST,
    EXECUTION,
}

public data class RoleSpell(
    val id: String,
    val name: String,
    val role: CompanionClass,
    val type: RoleSpellType,
    val levelReq: Int,
    val description: String,
    val powerPercent: Int,
    val cooldownTicks: Int,
)

@Singleton
public class CompanionRoleSpells
@Inject
constructor(
    private val mapClock: MapClock,
    private val wornBonuses: WornBonuses,
    private val companions: CompanionService,
    private val companionPlayerManager: CompanionPlayerManager,
    private val threat: ThreatService,
    private val equipmentStats: EquipmentCombatStats,
    private val skillXp: CompanionSkillService,
    private val telemetry: CompanionTelemetryService,
    private val presenter: CompanionAbilityPresenter,
) {
    /** companion id -> spell type -> game cycle of last cast. */
    private val lastCastCycles = ConcurrentHashMap<Long, MutableMap<RoleSpellType, Long>>()
    private val buffExpiryCycles = ConcurrentHashMap<Long, Long>()

    /**
     * owner character id -> (game cycle -> heal target keys already served this cycle). Two support
     * companions ticking in the same cycle can never produce duplicate heal events on the same
     * target - the second one picks the next unclaimed candidate instead.
     */
    private val healClaims = ConcurrentHashMap<Long, Pair<Long, MutableSet<Long>>>()

    /** character id -> disabled role spells set. */
    private val disabledOwners = ConcurrentHashMap.newKeySet<Long>()

    public fun isEnabled(characterId: Long): Boolean = !disabledOwners.contains(characterId)

    public fun toggle(player: Player): Boolean {
        val cid = player.characterId.toLong()
        val newState =
            if (disabledOwners.contains(cid)) {
                disabledOwners.remove(cid)
                true
            } else {
                disabledOwners.add(cid)
                false
            }
        val statusText = if (newState) "<col=006600>ENABLED</col>" else "<col=ff0000>DISABLED</col>"
        player.mes("Companion PvM Role Abilities are now $statusText.")
        return newState
    }

    public fun getSpellsForRole(role: CompanionClass): List<RoleSpell> =
        RoleSpellCatalog.SPELLS_BY_ROLE[role] ?: emptyList()

    public fun getUnlockedSpells(companion: CompanionRecord): List<RoleSpell> =
        getSpellsForRole(companion.companionClass).filter { companion.level >= it.levelReq }

    public fun processRoleSpells(
        player: Player,
        companion: CompanionRecord,
        bot: Player,
        target: Npc?,
    ) {
        val ownerId = player.characterId.toLong()
        if (!isEnabled(ownerId)) return
        if (!player.isSlotAssigned || !bot.isSlotAssigned) return

        when (companion.companionClass) {
            CompanionClass.SUPPORT -> processSupportSpells(player, companion, bot)
            CompanionClass.TANK -> processTankSpells(player, companion, bot, target)
            CompanionClass.DPS -> processDpsSpells(player, companion, bot, target)
        }
    }

    /** Executes the existing role-effect pipeline for a player-triggered talent ability. */
    public fun processManualAbility(
        player: Player,
        companion: CompanionRecord,
        bot: Player,
        target: Npc?,
        abilityId: String? = null,
    ) {
        if (!player.isSlotAssigned || !bot.isSlotAssigned) return
        if (abilityId != null) {
            presentTalentAbility(player, bot, target, abilityId)
        }
        when (companion.companionClass) {
            CompanionClass.SUPPORT -> processSupportSpells(player, companion, bot)
            CompanionClass.TANK -> processTankSpells(player, companion, bot, target)
            CompanionClass.DPS -> processDpsSpells(player, companion, bot, target)
        }
    }

    /**
     * Dev/admin preview of a talent ability's configured presentation on the companion bot - the
     * exact same visual the live ability uses (previews reuse the real definition, never a UI-only
     * fake). Does not touch cooldowns, gameplay effects or xp.
     *
     * @return `true` when the ability id has a presentation that was played.
     */
    public fun previewAbilityVisual(
        player: Player,
        bot: Player,
        target: Npc?,
        abilityId: String,
    ): Boolean {
        if (!player.isSlotAssigned || !bot.isSlotAssigned) return false
        if (CompanionAbilityVisuals.forTalentAbility(abilityId) == null) return false
        presentTalentAbility(player, bot, target, abilityId)
        return true
    }

    /**
     * Plays the configured [org.rsmod.api.combat.commons.ability.AbilityVisual] for a talent
     * ability, aimed at the entity matching its catalog target shape. The visual is the real
     * presentation definition (see [CompanionAbilityVisuals]) - the same one Companion Hub previews
     * use - never a UI-only fake.
     */
    private fun presentTalentAbility(player: Player, bot: Player, target: Npc?, abilityId: String) {
        val visual = CompanionAbilityVisuals.forTalentAbility(abilityId) ?: return
        when (CompanionAbilityCatalog.byId[abilityId]?.target) {
            CompanionAbilityTarget.SINGLE_ENEMY ->
                if (target != null && target.isValidTarget()) {
                    presenter.playAtTarget(bot, target, visual)
                } else {
                    presenter.playCast(bot, visual)
                }
            CompanionAbilityTarget.OWNER -> presenter.playOnAlly(bot, player, visual)
            CompanionAbilityTarget.OWNER_AND_COMPANION ->
                presenter.playAtTargets(bot, listOf(player, bot), visual)
            CompanionAbilityTarget.AREA_ENEMIES ->
                if (target != null && target.isValidTarget()) {
                    presenter.playAtTargets(bot, listOf(target), visual)
                } else {
                    presenter.playCast(bot, visual)
                }
            else -> presenter.playCast(bot, visual)
        }
    }

    private fun processSupportSpells(player: Player, companion: CompanionRecord, bot: Player) {
        val currentCycle = mapClock.cycle.toLong()
        val cooldowns = lastCastCycles.computeIfAbsent(companion.id) { ConcurrentHashMap() }

        // 1. Healing first: a wounded ally always outranks buff upkeep. The candidate selector
        // picks the lowest-hp party member, which covers emergency (<35%), tank-priority and
        // normal heals with a single deterministic priority order.
        val lastHealCycle = cooldowns[RoleSpellType.HEAL] ?: 0L
        val activeHeal =
            RoleSpellCatalog.SUPPORT_HEALS.filter { companion.level >= it.levelReq }
                .maxByOrNull { it.levelReq }

        // Behaviour tab: `autoHeal` disables automatic heals entirely; `healBelowPercent`
        // replaces the static eligibility threshold with the configured one.
        if (
            companion.behaviour.autoHeal &&
                activeHeal != null &&
                currentCycle - lastHealCycle >= activeHeal.cooldownTicks
        ) {
            val ownerId = player.characterId.toLong()
            val claims = healClaimsFor(ownerId, currentCycle)
            val thresholdPermille = companion.behaviour.healBelowPercent * 10
            val target =
                selectSupportHealTarget(
                    supportHealCandidates(player, ownerId),
                    claims,
                    thresholdPermille,
                )
            if (target != null) {
                applySupportHeal(player, companion, bot, activeHeal, target)
                claims += target.key
                cooldowns[RoleSpellType.HEAL] = currentCycle
            }
        }

        // 2. Buff upkeep (Attack, Strength, Defence, Ranged, Magic +5% to +25%) - gated by the
        // Behaviour tab's `autoBuff` setting.
        val lastBuffCycle = cooldowns[RoleSpellType.BUFF] ?: 0L
        val activeBuff =
            RoleSpellCatalog.SUPPORT_BUFFS.filter { companion.level >= it.levelReq }
                .maxByOrNull { it.levelReq }

        if (activeBuff != null && companion.behaviour.autoBuff) {
            val expiry = buffExpiryCycles[companion.id] ?: 0L
            if (currentCycle < expiry) {
                // statBoost is capped but not persistent; refresh it while the spell is active.
                applySupportBuff(player, companion, bot, activeBuff, announce = false)
            } else if (
                currentCycle - lastBuffCycle >= activeBuff.cooldownTicks && isBuffNeeded(player)
            ) {
                applySupportBuff(player, companion, bot, activeBuff, announce = true)
                cooldowns[RoleSpellType.BUFF] = currentCycle
                buffExpiryCycles[companion.id] = currentCycle + activeBuff.cooldownTicks
            }
        }
    }

    private fun healClaimsFor(ownerId: Long, cycle: Long): MutableSet<Long> =
        healClaims
            .compute(ownerId) { _, old ->
                if (old != null && old.first == cycle) old else cycle to mutableSetOf()
            }!!
            .second

    /**
     * Every active, living member of the owner's party that a support heal may target. The owner
     * uses [OWNER_HEAL_KEY]; companions use their stable id (which sorts above `0`, so the owner
     * wins hp-ratio ties deterministically). Incapacitated and despawned companions are never
     * candidates - they recover through the resummon flow, not through heals.
     */
    private fun supportHealCandidates(player: Player, ownerId: Long): List<SupportHealCandidate> =
        buildList {
            add(SupportHealCandidate(OWNER_HEAL_KEY, player.hitpoints, player.baseHitpointsLvl))
            for (ally in companions.owned(ownerId)) {
                if (
                    !ally.active ||
                        ally.state == CompanionState.INCAPACITATED ||
                        ally.state == CompanionState.DESPAWNED
                )
                    continue
                add(SupportHealCandidate(ally.id, ally.hitpoints, ally.maximumHitpoints))
            }
        }

    private fun isBuffNeeded(player: Player): Boolean {
        val combatStats =
            listOf(stats.attack, stats.strength, stats.defence, stats.ranged, stats.magic)
        return combatStats.any { stat -> player.stat(stat) <= player.statBase(stat) }
    }

    private fun applySupportBuff(
        player: Player,
        companion: CompanionRecord,
        bot: Player,
        spell: RoleSpell,
        announce: Boolean = true,
    ) {
        val combatStats =
            listOf(stats.attack, stats.strength, stats.defence, stats.ranged, stats.magic)
        for (stat in combatStats) {
            player.statBoost(stat, 0, spell.powerPercent)
        }

        if (announce) {
            // Fresh applications play the full presentation; upkeep refreshes stay silent so the
            // buff gfx does not re-fire on every tick for the duration of the buff.
            presenter.playOnAlly(bot, player, CompanionAbilityVisuals.forRoleSpell(spell))
            // SUPPORT xp: only a fresh application pays out; refresh ticks pass announce=false.
            skillXp.awardBuff(player.characterId.toLong(), companion.id, appliedNewEffect = true)
            bot.say("${spell.name}! (+${spell.powerPercent}% Stats)")
            player.mes(
                "<col=3399ff>[${companion.name}]: Cast ${spell.name}! Granted +${spell.powerPercent}% combat stat boost!</col>"
            )
        }
    }

    private fun applySupportHeal(
        player: Player,
        companion: CompanionRecord,
        bot: Player,
        spell: RoleSpell,
        target: SupportHealCandidate,
    ) {
        // Live values are gathered on every cast, so equipment swaps, prayers, buffs and
        // debuffs are reflected immediately. The heal scales off the selected target's own
        // hitpoint pool.
        val diff =
            computeSupportHealAmount(
                maxHp = target.maxHp,
                currentHp = target.currentHp,
                powerPercent = spell.powerPercent,
                magicDmgBonusPm = magicDamageBonus(player),
                attackRoll = magicAttackRoll(player),
                baseAttackRoll = magicBaseAttackRoll(player),
                healingPowerPm = healingPowerBonus(player, companion),
            )

        // Healing threat: effective (non-overheal) hp restored pulls aggro from every npc
        // engaged with the healed ally's party. Companion bots carry their own negative uuid,
        // so this threat lands on the healer bot - not on the owner.
        if (diff > 0) {
            threat.onHeal(bot, partyPlayers(player), diff)
            val ownerId = player.characterId.toLong()
            skillXp.awardEffectiveHealing(ownerId, companion.id, diff)
            telemetry.record(
                ownerId,
                CombatTelemetryEvent(
                    encounterId = companion.encounterId ?: 0L,
                    sourceId = companion.id,
                    ownerId = ownerId,
                    sourceName = companion.name,
                    targetId = target.key,
                    ability = spell.id,
                    effectiveHealing = diff,
                    targetIsPlayer = true,
                ),
            )
        }

        if (target.key == OWNER_HEAL_KEY) {
            if (diff > 0) {
                player.statAdd(stats.hitpoints, diff, 0)
            }
            presenter.playOnAlly(bot, player, CompanionAbilityVisuals.forRoleSpell(spell))
            bot.say("${spell.name}! (+${diff} HP)")
            player.mes(
                "<col=00b33c>[${companion.name}]: Chanted ${spell.name}! Restored +${diff} HP (${spell.powerPercent}% max HP)!</col>"
            )
            return
        }

        // Companion target: the domain hitpoints value is authoritative (persisted via
        // CompanionService.heal) and the bot entity is topped up alongside it so the next
        // syncBotVitals pass sees a consistent pair.
        val ownerId = player.characterId.toLong()
        val ally = companions.owned(ownerId).firstOrNull { it.id == target.key } ?: return
        if (diff > 0) {
            companions.heal(ownerId, ally.id, diff)
            companionPlayerManager.getPlayer(ally.id)?.statAdd(stats.hitpoints, diff, 0)
        }
        companionPlayerManager.getPlayer(ally.id)?.let { allyBot ->
            presenter.playOnAlly(bot, allyBot, CompanionAbilityVisuals.forRoleSpell(spell))
        }
        bot.say("${spell.name}! (+${diff} HP to ${ally.name})")
        player.mes(
            "<col=00b33c>[${companion.name}]: Chanted ${spell.name}! Restored +${diff} HP to ${ally.name}!</col>"
        )
    }

    /**
     * The player's current total magic damage bonus in per-mille (`1000` = +100%), using the same
     * inputs as the spell max-hit formula: the worn equipment bonus
     * ([WornBonuses.magicDamageBonusBase]) plus the magic damage prayer bonus
     * ([MagicMaxHitOperations.getMagicDamagePrayerBonus]).
     */
    private fun magicDamageBonus(player: Player): Int =
        wornBonuses.magicDamageBonusBase(player) +
            MagicMaxHitOperations.getMagicDamagePrayerBonus(player)

    /**
     * The player's current magic attack roll, computed with the same pipeline as the PvN spell
     * accuracy formula: effective magic level (current level, attack style, offensive prayers and
     * void mage set) multiplied by the worn magic attack bonus.
     */
    private fun magicAttackRoll(player: Player): Int {
        val effectiveMagic = MagicAccuracyOperations.calculateEffectiveMagic(player, null)
        val magicAttackBonus = wornBonuses.offensiveMagicBonus(player)
        return PlayerMagicAccuracy.calculateBaseAttackRoll(effectiveMagic, magicAttackBonus)
    }

    /**
     * The attack roll of the same visible magic level with baseline modifiers (normal attack style,
     * no prayers, no void set, no equipment bonus). Used to normalize [magicAttackRoll] so that
     * baseline accuracy yields a `1.0` ratio at any magic level.
     */
    /**
     * The owner's party as live [Player] entities - the owner itself plus every summoned companion
     * bot - used for heal/shield threat distribution.
     */
    private fun partyPlayers(player: Player): List<Player> {
        val ownerId = player.characterId.toLong()
        val result = ArrayList<Player>(companions.owned(ownerId).size + 1)
        result += player
        for (ally in companions.owned(ownerId)) {
            if (ally.active && ally.state != CompanionState.INCAPACITATED) {
                companionPlayerManager.getPlayer(ally.id)?.let { result += it }
            }
        }
        return result
    }

    /**
     * Equipment-driven healing power in per-mille, matching how `magicDmgBonusPm` feeds the heal
     * formula: the owner's `HealingPower` affixes plus a configurable share of the companion's own
     * attack style power (melee/ranged/magic supports all scale, melee/ranged less than magic). `1`
     * power = `10` per-mille (1% heal).
     */
    private fun healingPowerBonus(player: Player, companion: CompanionRecord): Int {
        val gear = equipmentStats.stats(player)
        val (stylePower, scalingBps) =
            when (companion.attackStyle) {
                CompanionAttackStyle.MELEE ->
                    gear.meleePower to ThreatConfig.MELEE_SUPPORT_HEAL_SCALING_BPS
                CompanionAttackStyle.RANGED ->
                    gear.rangedPower to ThreatConfig.RANGED_SUPPORT_HEAL_SCALING_BPS
                CompanionAttackStyle.MAGIC ->
                    gear.magicPower to ThreatConfig.MAGIC_SUPPORT_HEAL_SCALING_BPS
            }
        return gear.effectiveHealingPower(stylePower, scalingBps) * HEALING_POWER_TO_PM
    }

    private fun magicBaseAttackRoll(player: Player): Int {
        val baseEffectiveMagic =
            PlayerMagicAccuracy.calculateEffectiveMagic(
                visibleMagicLvl = player.magicLvl,
                styleBonus = NORMAL_STYLE_BONUS,
                prayerBonus = 1.0,
                voidBonus = 1.0,
            )
        return PlayerMagicAccuracy.calculateBaseAttackRoll(baseEffectiveMagic, 0)
    }

    private fun processTankSpells(
        player: Player,
        companion: CompanionRecord,
        bot: Player,
        target: Npc?,
    ) {
        val currentCycle = mapClock.cycle.toLong()
        val cooldowns = lastCastCycles.computeIfAbsent(companion.id) { ConcurrentHashMap() }

        // Defensive ward / mitigation upkeep
        val lastDefCycle = cooldowns[RoleSpellType.DEFENSIVE] ?: 0L
        val activeDef =
            RoleSpellCatalog.TANK_DEFENSIVES.filter { companion.level >= it.levelReq }
                .maxByOrNull { it.levelReq }

        // Behaviour tab: `autoDefend` disables the automatic defensive ward upkeep.
        if (
            companion.behaviour.autoDefend &&
                activeDef != null &&
                (currentCycle - lastDefCycle >= activeDef.cooldownTicks)
        ) {
            presenter.playCast(bot, CompanionAbilityVisuals.forRoleSpell(activeDef))
            bot.say("${activeDef.name}! (-${activeDef.powerPercent}% Damage Taken)")
            cooldowns[RoleSpellType.DEFENSIVE] = currentCycle
        }

        // Taunt shout when enemy is attacking player - gated by the Behaviour tab's `autoTaunt`.
        if (companion.behaviour.autoTaunt && target != null && target.isValidTarget()) {
            val lastTauntCycle = cooldowns[RoleSpellType.TAUNT] ?: 0L
            val activeTaunt =
                RoleSpellCatalog.TANK_TAUNTS.filter { companion.level >= it.levelReq }
                    .maxByOrNull { it.levelReq }

            if (
                activeTaunt != null && (currentCycle - lastTauntCycle >= activeTaunt.cooldownTicks)
            ) {
                // Real aggro: raise the tank bot to the top of the npc's threat table and force
                // the npc onto it for ThreatConfig.TAUNT_DURATION_CYCLES.
                if (threat.taunt(target, bot)) {
                    skillXp.awardTaunt(player.characterId.toLong(), companion.id)
                    presenter.playAtTarget(
                        bot,
                        target,
                        CompanionAbilityVisuals.forRoleSpell(activeTaunt),
                    )
                    bot.say("Taunting ${target.visType.name}! Face me!")
                    player.mes(
                        "<col=ff981f>[${companion.name}]: Used ${activeTaunt.name} to pull aggro onto the Tank!</col>"
                    )
                    cooldowns[RoleSpellType.TAUNT] = currentCycle
                }
            }
        }
    }

    private fun processDpsSpells(
        player: Player,
        companion: CompanionRecord,
        bot: Player,
        target: Npc?,
    ) {
        if (target == null || !target.isValidTarget()) return
        val currentCycle = mapClock.cycle.toLong()
        val cooldowns = lastCastCycles.computeIfAbsent(companion.id) { ConcurrentHashMap() }

        val lastBurstCycle = cooldowns[RoleSpellType.DAMAGE_BURST] ?: 0L
        val activeBurst =
            RoleSpellCatalog.DPS_BURSTS.filter { companion.level >= it.levelReq }
                .maxByOrNull { it.levelReq }

        if (activeBurst != null && (currentCycle - lastBurstCycle >= activeBurst.cooldownTicks)) {
            presenter.playAtTarget(bot, target, CompanionAbilityVisuals.forRoleSpell(activeBurst))
            bot.say("${activeBurst.name}! (+${activeBurst.powerPercent}% Burst)")
            player.mes(
                "<col=ff3333>[${companion.name}]: Unleashed ${activeBurst.name} for +${activeBurst.powerPercent}% damage!</col>"
            )
            cooldowns[RoleSpellType.DAMAGE_BURST] = currentCycle
        }

        // Executions are the finisher tier: only worthwhile on wounded targets, so they get their
        // own cooldown bucket and a low-hp gate instead of competing with bursts.
        val lastExecCycle = cooldowns[RoleSpellType.EXECUTION] ?: 0L
        val activeExec =
            RoleSpellCatalog.DPS_EXECUTIONS.filter { companion.level >= it.levelReq }
                .maxByOrNull { it.levelReq }
        val woundedTarget = target.hitpoints * 10 <= target.baseHitpointsLvl * 3

        if (
            activeExec != null &&
                woundedTarget &&
                (currentCycle - lastExecCycle >= activeExec.cooldownTicks)
        ) {
            presenter.playAtTarget(bot, target, CompanionAbilityVisuals.forRoleSpell(activeExec))
            bot.say("${activeExec.name}!")
            player.mes(
                "<col=ff3333>[${companion.name}]: Executed ${activeExec.name} on ${target.visType.name}!</col>"
            )
            cooldowns[RoleSpellType.EXECUTION] = currentCycle
        }
    }
}

/** Style bonus of every non-`Accurate` magic attack style (see `MagicAttackStyle`). */
internal const val NORMAL_STYLE_BONUS: Int = 9

/** Minimum hitpoints restored by a support heal, before the missing-hp cap. */
internal const val MIN_SUPPORT_HEAL: Int = 5

/** Weight applied to the above-baseline accuracy ratio when scaling support heals. */
internal const val SUPPORT_HEAL_ACCURACY_WEIGHT: Double = 0.5

/** Maximum total multiplier applied to a support heal. */
internal const val MAX_HEAL_MULTIPLIER: Double = 1.75

/**
 * Computes the hitpoints restored by a companion support heal.
 *
 * Scaling formula (recalculated on every cast):
 * ```
 * base       = maxHp * powerPercent / 100
 * dmgPart    = magicDmgBonusPm / 1000.0          // same rate the spell-damage formula uses
 * accPart    = (attackRoll / baseAttackRoll - 1) * SUPPORT_HEAL_ACCURACY_WEIGHT
 * multiplier = (1 + dmgPart + accPart) clamped to [1.0, MAX_HEAL_MULTIPLIER]
 * heal       = max(MIN_SUPPORT_HEAL, floor(base * multiplier))
 * ```
 *
 * `attackRoll` and `baseAttackRoll` are produced by the canonical magic accuracy pipeline
 * (`calculateEffectiveMagic` + `calculateBaseAttackRoll`), where the base roll uses the same
 * visible magic level with no gear/prayer/void modifiers. Baseline stats therefore yield a `1.0`
 * multiplier (the previous static behaviour), below-baseline values never reduce the heal, and the
 * combined scaling is capped to avoid runaway healing.
 *
 * @return the applied heal amount, capped by the player's missing hitpoints.
 */
internal fun computeSupportHealAmount(
    maxHp: Int,
    currentHp: Int,
    powerPercent: Int,
    magicDmgBonusPm: Int,
    attackRoll: Int,
    baseAttackRoll: Int,
    healingPowerPm: Int = 0,
): Int {
    val base = (maxHp * powerPercent) / 100
    val accuracyRatio = if (baseAttackRoll > 0) attackRoll.toDouble() / baseAttackRoll else 1.0
    val multiplier =
        (1.0 +
                magicDmgBonusPm / 1000.0 +
                healingPowerPm / 1000.0 +
                (accuracyRatio - 1.0) * SUPPORT_HEAL_ACCURACY_WEIGHT)
            .coerceIn(1.0, MAX_HEAL_MULTIPLIER)
    val heal = (base * multiplier).toInt().coerceAtLeast(MIN_SUPPORT_HEAL)
    return minOf(heal, (maxHp - currentHp).coerceAtLeast(0))
}

/** Heal target key reserved for the owner player; companion targets use their stable id. */
internal const val OWNER_HEAL_KEY: Long = 0L

/** Per-mille heal contribution per point of effective healing power (1 power = 1% heal). */
internal const val HEALING_POWER_TO_PM: Int = 10

/** Only targets at or below this hitpoint ratio are eligible for a support heal. */
internal const val SUPPORT_HEAL_THRESHOLD_PERMILLE: Int = 800

/** A party member a support heal may be cast on - the owner or an active living companion. */
internal data class SupportHealCandidate(val key: Long, val currentHp: Int, val maxHp: Int) {
    /** Hitpoint ratio in per-mille; full or invalid pools report `1000` (never eligible). */
    val hpPermille: Int
        get() = if (maxHp > 0) currentHp * 1000 / maxHp else 1000
}

/**
 * Picks the support-heal target deterministically: the lowest hitpoint ratio wins, ties break on
 * the lowest target key (the owner key `0` precedes every companion id, and companion ids order by
 * owner then slot). Only actually-wounded members (at or below [SUPPORT_HEAL_THRESHOLD_PERMILLE])
 * qualify, and keys in [claimed] are skipped so a second support companion ticking in the same
 * cycle heals a different target instead of duplicating the first one's heal event.
 */
internal fun selectSupportHealTarget(
    candidates: List<SupportHealCandidate>,
    claimed: Set<Long>,
    thresholdPermille: Int = SUPPORT_HEAL_THRESHOLD_PERMILLE,
): SupportHealCandidate? =
    candidates
        .filter { it.currentHp > 0 && it.hpPermille <= thresholdPermille }
        .sortedWith(compareBy({ it.hpPermille }, { it.key }))
        .firstOrNull { it.key !in claimed }

public object RoleSpellCatalog {
    // --- SUPPORT SPELLS ---
    public val SUPPORT_HEALS: List<RoleSpell> =
        listOf(
            RoleSpell(
                "mend_1",
                "Lesser Mend",
                CompanionClass.SUPPORT,
                RoleSpellType.HEAL,
                1,
                "Restores 10% of player max HP",
                10,
                20,
            ),
            RoleSpell(
                "mend_2",
                "Mend",
                CompanionClass.SUPPORT,
                RoleSpellType.HEAL,
                20,
                "Restores 16% of player max HP",
                16,
                20,
            ),
            RoleSpell(
                "mend_3",
                "Greater Mend",
                CompanionClass.SUPPORT,
                RoleSpellType.HEAL,
                40,
                "Restores 22% of player max HP",
                22,
                20,
            ),
            RoleSpell(
                "mend_4",
                "Holy Light",
                CompanionClass.SUPPORT,
                RoleSpellType.HEAL,
                60,
                "Restores 28% of player max HP",
                28,
                20,
            ),
            RoleSpell(
                "mend_5",
                "Divine Sanctuary",
                CompanionClass.SUPPORT,
                RoleSpellType.HEAL,
                80,
                "Restores 35% of player max HP",
                35,
                20,
            ),
        )

    public val SUPPORT_BUFFS: List<RoleSpell> =
        listOf(
            RoleSpell(
                "empower_1",
                "Lesser Empowerment",
                CompanionClass.SUPPORT,
                RoleSpellType.BUFF,
                1,
                "Grants +5% to all combat stats",
                5,
                45,
            ),
            RoleSpell(
                "empower_2",
                "Empowerment",
                CompanionClass.SUPPORT,
                RoleSpellType.BUFF,
                20,
                "Grants +10% to all combat stats",
                10,
                45,
            ),
            RoleSpell(
                "empower_3",
                "Greater Empowerment",
                CompanionClass.SUPPORT,
                RoleSpellType.BUFF,
                40,
                "Grants +15% to all combat stats",
                15,
                45,
            ),
            RoleSpell(
                "empower_4",
                "Heroic Aura",
                CompanionClass.SUPPORT,
                RoleSpellType.BUFF,
                60,
                "Grants +20% to all combat stats",
                20,
                45,
            ),
            RoleSpell(
                "empower_5",
                "Avatar of Power",
                CompanionClass.SUPPORT,
                RoleSpellType.BUFF,
                80,
                "Grants +25% to all combat stats",
                25,
                45,
            ),
        )

    // --- TANK SPELLS ---
    public val TANK_DEFENSIVES: List<RoleSpell> =
        listOf(
            RoleSpell(
                "skin_1",
                "Iron Skin",
                CompanionClass.TANK,
                RoleSpellType.DEFENSIVE,
                1,
                "Mitigates incoming damage by 10%",
                10,
                30,
            ),
            RoleSpell(
                "skin_2",
                "Stone Wall",
                CompanionClass.TANK,
                RoleSpellType.DEFENSIVE,
                20,
                "Mitigates incoming damage by 16%",
                16,
                30,
            ),
            RoleSpell(
                "skin_3",
                "Fortress",
                CompanionClass.TANK,
                RoleSpellType.DEFENSIVE,
                40,
                "Mitigates incoming damage by 22%",
                22,
                30,
            ),
            RoleSpell(
                "skin_4",
                "Titan's Aegis",
                CompanionClass.TANK,
                RoleSpellType.DEFENSIVE,
                60,
                "Mitigates incoming damage by 28%",
                28,
                30,
            ),
            RoleSpell(
                "skin_5",
                "Invulnerable Bulwark",
                CompanionClass.TANK,
                RoleSpellType.DEFENSIVE,
                80,
                "Mitigates incoming damage by 35%",
                35,
                30,
            ),
        )

    public val TANK_TAUNTS: List<RoleSpell> =
        listOf(
            RoleSpell(
                "taunt_1",
                "Taunting Roar",
                CompanionClass.TANK,
                RoleSpellType.TAUNT,
                1,
                "Forces the monster to attack the Tank",
                10,
                25,
            ),
            RoleSpell(
                "taunt_2",
                "Shield Intercept",
                CompanionClass.TANK,
                RoleSpellType.TAUNT,
                20,
                "Intercepts damage intended for the owner",
                16,
                25,
            ),
            RoleSpell(
                "taunt_3",
                "Concussive Bash",
                CompanionClass.TANK,
                RoleSpellType.TAUNT,
                40,
                "Staggers enemy and reduces its max hit",
                22,
                25,
            ),
            RoleSpell(
                "taunt_4",
                "Bastion Guard",
                CompanionClass.TANK,
                RoleSpellType.TAUNT,
                60,
                "Provides a 20% protective shield to owner",
                28,
                25,
            ),
            RoleSpell(
                "taunt_5",
                "Immortal Protector",
                CompanionClass.TANK,
                RoleSpellType.TAUNT,
                80,
                "Provides a 35% protective shield to owner",
                35,
                25,
            ),
        )

    // --- DPS SPELLS ---
    public val DPS_BURSTS: List<RoleSpell> =
        listOf(
            RoleSpell(
                "burst_1",
                "Focused Strike",
                CompanionClass.DPS,
                RoleSpellType.DAMAGE_BURST,
                1,
                "Empowers attack with +10% burst damage",
                10,
                18,
            ),
            RoleSpell(
                "burst_2",
                "Rending Blow",
                CompanionClass.DPS,
                RoleSpellType.DAMAGE_BURST,
                20,
                "Empowers attack with +16% burst damage",
                16,
                18,
            ),
            RoleSpell(
                "burst_3",
                "Critical Surge",
                CompanionClass.DPS,
                RoleSpellType.DAMAGE_BURST,
                40,
                "Empowers attack with +22% burst damage",
                22,
                18,
            ),
            RoleSpell(
                "burst_4",
                "Berserker Frenzy",
                CompanionClass.DPS,
                RoleSpellType.DAMAGE_BURST,
                60,
                "Empowers attack with +28% burst damage",
                28,
                18,
            ),
            RoleSpell(
                "burst_5",
                "Annihilation",
                CompanionClass.DPS,
                RoleSpellType.DAMAGE_BURST,
                80,
                "Empowers attack with +35% burst damage",
                35,
                18,
            ),
        )

    public val DPS_EXECUTIONS: List<RoleSpell> =
        listOf(
            RoleSpell(
                "exec_1",
                "Swift Flurry",
                CompanionClass.DPS,
                RoleSpellType.EXECUTION,
                1,
                "Quick flurry attack",
                10,
                25,
            ),
            RoleSpell(
                "exec_2",
                "Bloodthirst",
                CompanionClass.DPS,
                RoleSpellType.EXECUTION,
                20,
                "Restores health on hit",
                16,
                25,
            ),
            RoleSpell(
                "exec_3",
                "Armor Shatter",
                CompanionClass.DPS,
                RoleSpellType.EXECUTION,
                40,
                "Sunders enemy defense",
                22,
                25,
            ),
            RoleSpell(
                "exec_4",
                "Chain Cleave",
                CompanionClass.DPS,
                RoleSpellType.EXECUTION,
                60,
                "Hits multiple adjacent foes",
                28,
                25,
            ),
            RoleSpell(
                "exec_5",
                "Fatal Execution",
                CompanionClass.DPS,
                RoleSpellType.EXECUTION,
                80,
                "Massive double damage execution on low HP foes",
                35,
                25,
            ),
        )

    public val SPELLS_BY_ROLE: Map<CompanionClass, List<RoleSpell>> =
        mapOf(
            CompanionClass.SUPPORT to (SUPPORT_HEALS + SUPPORT_BUFFS).sortedBy { it.levelReq },
            CompanionClass.TANK to (TANK_DEFENSIVES + TANK_TAUNTS).sortedBy { it.levelReq },
            CompanionClass.DPS to (DPS_BURSTS + DPS_EXECUTIONS).sortedBy { it.levelReq },
        )
}
