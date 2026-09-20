package org.rsmod.api.player.perk

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.stat.StatType

/**
 * Perk Points economy + perk rank/effect resolution.
 *
 * Rank purchases are deliberately atomic: one call to [train] can only move one perk one rank
 * forward. The next-rank price is `baseCost * 2^currentRank`, calculated with a saturating Long
 * operation so malformed/configured values can never wrap negative.
 *
 * All effect accessors resolve from server-side vars and are safe to call anywhere; they return `0`
 * for untrained perks. PvM effects must only be applied on npc-originated or npc-targeted code
 * paths - callers are responsible for that scoping.
 */
@Singleton
public class PerkService @Inject constructor() {
    /** The player's unspent Perk Points, capped only for legacy Int callers. */
    public fun points(player: Player): Int =
        pointsLong(player).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** The authoritative unspent Perk Points balance. */
    public fun pointsLong(player: Player): Long {
        ensureRankFormat(player)
        val low = player.vars[PerkVarps.pointsLow]
        val high = player.vars[PerkVarps.pointsHigh]
        if (low == 0 && high == 0) {
            // Accounts created before the 64-bit balance was introduced still have the original
            // permanent varp. Do not require a database migration just to read that balance.
            return player.vars[PerkVarps.points].toLong().coerceAtLeast(0)
        }
        return decodeUnsignedLong(low, high)
    }

    /** Returns the persisted rank. The old `perk_xp_*` varps are now rank storage. */
    public fun rank(player: Player, perk: Perk): Int {
        ensureRankFormat(player)
        val stored = player.vars[perk.xpVarp]
        val clamped = stored.coerceIn(0, Perk.MAX_PERK_RANK)
        if (stored != clamped) {
            VarPlayerIntMapSetter.set(player, perk.xpVarp, clamped)
        }
        return clamped
    }

    /** Source-compatible alias for older effect code. */
    public fun level(player: Player, perk: Perk): Int = rank(player, perk)

    /** Source-compatible read alias; the value is now the stored rank, not an XP amount. */
    public fun xp(player: Player, perk: Perk): Int = rank(player, perk)

    /** Legacy triangular conversion used once when an old XP-based character first logs in. */
    public fun levelForXp(xp: Int): Int {
        var rank = 0
        while (rank < Perk.MAX_PERK_RANK && xp >= xpForLegacyRank(rank + 1)) {
            rank++
        }
        return rank
    }

    /** Legacy helper retained for migration and external diagnostics. */
    public fun xpForLevel(level: Int): Int = xpForLegacyRank(level)

    /** Next-rank cost for the selected perk. `0` means the perk is already at max rank. */
    public fun nextRankCost(player: Player, perk: Perk): Long =
        nextRankCost(rank(player, perk), perk.baseCost)

    /** Overflow-safe `baseCost * 2^currentRank` calculation. */
    public fun nextRankCost(currentRank: Int, baseCost: Long): Long {
        require(currentRank in 0..Perk.MAX_PERK_RANK) {
            "currentRank must be between 0 and ${Perk.MAX_PERK_RANK}: $currentRank"
        }
        require(baseCost > 0) { "baseCost must be positive: $baseCost" }
        if (currentRank == Perk.MAX_PERK_RANK) {
            return 0L
        }
        return if (
            currentRank >= java.lang.Long.SIZE - 1 || baseCost > (Long.MAX_VALUE ushr currentRank)
        ) {
            Long.MAX_VALUE
        } else {
            baseCost shl currentRank
        }
    }

    /** Compact, unambiguous UI representation for a Long-sized price. */
    public fun displayCost(cost: Long): String {
        if (cost < 1_000) return cost.toString()
        val (unit, divisor) =
            when {
                cost >= 1_000_000_000_000L -> "T" to 1_000_000_000_000L
                cost >= 1_000_000_000L -> "B" to 1_000_000_000L
                cost >= 1_000_000L -> "M" to 1_000_000L
                else -> "K" to 1_000L
            }
        val whole = cost / divisor
        val remainder = (cost % divisor) * 10 / divisor
        return if (remainder == 0L) "$whole$unit" else "$whole.$remainder$unit"
    }

    /** Progress within the current rank in basis points (0 or complete). */
    public fun levelProgressBps(player: Player, perk: Perk): Int =
        if (rank(player, perk) >= Perk.MAX_PERK_RANK) 10_000 else 0

    /**
     * Buys exactly one next rank. Returns the Long price paid, or `0` when the perk is maxed or the
     * player cannot afford the purchase.
     */
    public fun train(player: Player, perk: Perk): Long {
        val currentRank = rank(player, perk)
        if (currentRank >= Perk.MAX_PERK_RANK) {
            return 0
        }
        val cost = nextRankCost(currentRank, perk.baseCost)
        val available = pointsLong(player)
        if (available < cost) {
            return 0
        }
        setPoints(player, available - cost)
        VarPlayerIntMapSetter.set(player, perk.xpVarp, currentRank + 1)
        return cost
    }

    /**
     * Awards Perk Points for a boss kill. [visLevel] scales the payout: +1 point per full 100
     * combat levels, then the Fortune perk multiplier is applied.
     */
    public fun awardBossKill(player: Player, visLevel: Int): Int {
        val base = (1 + visLevel / 100).coerceAtLeast(1)
        val awarded = base + base * pointBonusBps(player) / 10_000
        addPoints(player, awarded)
        return awarded
    }

    /** Whether [visLevel] counts as a boss for perk point gains. */
    public fun isBoss(visLevel: Int): Boolean = visLevel >= 100

    /** Adds [amount] Perk Points directly (e.g. solo arena round bonuses). */
    public fun addPoints(player: Player, amount: Int) {
        if (amount > 0) {
            addPoints(player, amount.toLong())
        }
    }

    /** Adds points without overflowing the authoritative Long balance. */
    public fun addPoints(player: Player, amount: Long) {
        if (amount > 0) {
            val current = pointsLong(player)
            setPoints(
                player,
                if (Long.MAX_VALUE - current < amount) Long.MAX_VALUE else current + amount,
            )
        }
    }

    private fun ensureRankFormat(player: Player) {
        if (player.vars[PerkVarps.rankFormat] == CURRENT_RANK_FORMAT) {
            return
        }
        for (perk in Perk.entries) {
            val legacyXp = player.vars[perk.xpVarp]
            VarPlayerIntMapSetter.set(player, perk.xpVarp, levelForXp(legacyXp))
        }
        VarPlayerIntMapSetter.set(player, PerkVarps.rankFormat, CURRENT_RANK_FORMAT)
    }

    private fun setPoints(player: Player, value: Long) {
        require(value >= 0) { "Perk Points cannot be negative: $value" }
        val low = value.toInt()
        val high = (value ushr 32).toInt()
        VarPlayerIntMapSetter.set(player, PerkVarps.pointsLow, low)
        VarPlayerIntMapSetter.set(player, PerkVarps.pointsHigh, high)
        // Keep the old public/debug varp useful without allowing it to corrupt the Long balance.
        VarPlayerIntMapSetter.set(
            player,
            PerkVarps.points,
            value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
    }

    private fun decodeUnsignedLong(low: Int, high: Int): Long {
        val highUnsigned = high.toLong() and 0x7fff_ffffL
        return (highUnsigned shl 32) or (low.toLong() and 0xffff_ffffL)
    }

    private fun xpForLegacyRank(rank: Int): Int {
        val safeRank = rank.coerceIn(0, Perk.MAX_PERK_RANK)
        return safeRank * (safeRank + 1) / 2
    }

    private companion object {
        private const val CURRENT_RANK_FORMAT: Int = 1
    }

    // ---- Core perk effects -------------------------------------------------

    /** `Power`: +200 bps (+2%) outgoing npc damage per level. */
    public fun outgoingDamageBps(player: Player): Int = level(player, Perk.Power) * 200

    /** `Vitality`: +1 max hitpoints level per level. */
    public fun maxHealthBonus(player: Player): Int = level(player, Perk.Vitality)

    /** `Greed`: +500 bps (+5%) extra-drop-roll chance per level. */
    public fun extraDropChanceBps(player: Player): Int = level(player, Perk.Greed) * 500

    /** `Guardian`: -200 bps (-2%) incoming damage per level. */
    public fun incomingReductionBps(player: Player): Int = level(player, Perk.Guardian) * 200

    /** `Fortune`: +1000 bps (+10%) perk points per level. */
    public fun pointBonusBps(player: Player): Int = level(player, Perk.Fortune) * 1_000

    /** `Swiftness`: -100 bps (-1%) attack cycle per level. */
    public fun attackSpeedBps(player: Player): Int = level(player, Perk.Swiftness) * 100

    /** Solo Boss Arena: +5% outgoing NPC damage per purchased level, capped by the arena at 100. */
    public fun soloBossDamageBps(player: Player): Int =
        player.vars[PerkVarps.soloBossDamage].coerceIn(0, 100) * 500

    /** Solo Boss Arena: -5% player attack cycle per purchased level. */
    public fun soloBossAttackSpeedBps(player: Player): Int =
        player.vars[PerkVarps.soloBossAttackSpeed].coerceIn(0, 100) * 500

    /** Solo Boss Arena: +5% maximum health per purchased level. */
    public fun soloBossMaxHealthBps(player: Player): Int =
        player.vars[PerkVarps.soloBossMaxHealth].coerceIn(0, 100) * 500

    // ---- PvM offence --------------------------------------------------------

    /**
     * `Berserker`/`Deadeye`/`Sorcerer`: +200 bps (+2%) damage per level for the matching hit style.
     * [HitType.Typeless] and unknown styles get no bonus.
     */
    public fun styleDamageBps(player: Player, type: HitType): Int =
        when (type) {
            HitType.Melee -> level(player, Perk.Berserker)
            HitType.Ranged -> level(player, Perk.Deadeye)
            HitType.Magic -> level(player, Perk.Sorcerer)
            else -> 0
        } * 200

    /** `Slayer`: +300 bps (+3%) damage per level vs boss npcs. */
    public fun bossDamageBps(player: Player): Int = level(player, Perk.Slayer) * 300

    /** `Executioner`: +400 bps (+4%) damage per level vs low-health targets. */
    public fun executeDamageBps(player: Player): Int = level(player, Perk.Executioner) * 400

    /** `Punisher`: +300 bps (+3%) damage per level vs high-health targets. */
    public fun openerDamageBps(player: Player): Int = level(player, Perk.Punisher) * 300

    /** `Dominion`: +200 bps (+2%) damage per level vs higher-level npcs. */
    public fun dominionDamageBps(player: Player): Int = level(player, Perk.Dominion) * 200

    /** `Bloodlust`: +300 bps (+3%) damage per level while the player is below half health. */
    public fun bloodlustDamageBps(player: Player): Int = level(player, Perk.Bloodlust) * 300

    /** `Slaughter`: +200 bps (+2%) crit chance per level; crits deal +50% damage. */
    public fun critChanceBps(player: Player): Int = level(player, Perk.Slaughter) * 200

    /** Critical hit damage bonus applied when a `Slaughter` roll succeeds. */
    public fun critDamageBps(): Int = 5_000

    /** `Rend`: +1 flat bonus damage every 2 levels. */
    public fun flatBonusDamage(player: Player): Int = level(player, Perk.Rend) / 2

    /** `Leech`: +50 bps (0.5%) of dealt npc damage returned as healing per level. */
    public fun onHitHealBps(player: Player): Int = level(player, Perk.Leech) * 50

    // ---- PvM defence (npc sources only) --------------------------------------

    /**
     * `Ironhide`/`Spellward`/`Dodging`: -200 bps (-2%) incoming damage per level for the matching
     * hit style. `Smithwright` adds a further -50 bps (-0.5%) per level on melee only.
     */
    public fun styleReductionBps(player: Player, type: HitType): Int =
        when (type) {
            HitType.Melee ->
                level(player, Perk.Ironhide) * 200 + level(player, Perk.Smithwright) * 50
            HitType.Magic -> level(player, Perk.Spellward) * 200
            HitType.Ranged -> level(player, Perk.Dodging) * 200
            else -> 0
        }

    /** `Thorns`: +200 bps (+2%) of incoming npc damage reflected per level. */
    public fun reflectBps(player: Player): Int = level(player, Perk.Thorns) * 200

    /** `Last Stand`: -300 bps (-3%) incoming damage per level while below 25% health. */
    public fun lastStandReductionBps(player: Player): Int = level(player, Perk.Laststand) * 300

    // ---- PvM sustain / kill effects -------------------------------------------

    /** `Adrenaline`: +10 flat energy per level added to each special regen tick. */
    public fun specRegenBonus(player: Player): Int = level(player, Perk.Adrenaline) * 10

    /** `Regeneration`: +1000 bps (+10%) chance per level of +1 hp on natural regen. */
    public fun regenBonusChanceBps(player: Player): Int = level(player, Perk.Regeneration) * 1_000

    /** `Harvest`: +1 prayer point restored on npc kill per level. */
    public fun onKillPrayer(player: Player): Int = level(player, Perk.Harvest)

    /** `Rejuvenation`: +2 hitpoints restored on npc kill per level. */
    public fun onKillHeal(player: Player): Int = level(player, Perk.Rejuvenation) * 2

    /** `Scavenger`: +500 bps (+5%) chance per level of a perk point from a non-boss kill. */
    public fun pointOnKillChanceBps(player: Player): Int = level(player, Perk.Scavenger) * 500

    // ---- Skilling xp ----------------------------------------------------------

    /**
     * Total xp bonus in basis points for [stat]: `Scholar` contributes +100 bps (+1%) per level to
     * every non-combat skill, and each skill's own perk contributes a further +200 bps (+2%) per
     * level. Combat stats (attack/defence/strength/hitpoints/ranged/magic) get nothing.
     */
    public fun skillXpBps(player: Player, stat: StatType): Int {
        val perk = xpPerkFor(stat) ?: return 0
        return level(player, Perk.Scholar) * 100 + level(player, perk) * 200
    }

    /** The xp perk associated with [stat], or `null` for combat stats. */
    private fun xpPerkFor(stat: StatType): Perk? =
        when (stat.id) {
            stats.woodcutting.id -> Perk.Woodcutter
            stats.mining.id -> Perk.Prospector
            stats.fishing.id -> Perk.Angler
            stats.cooking.id -> Perk.Chef
            stats.crafting.id -> Perk.Artificer
            stats.smithing.id -> Perk.Blacksmith
            stats.runecrafting.id -> Perk.Runic
            stats.firemaking.id -> Perk.Arsonist
            stats.herblore.id -> Perk.Herbalist
            stats.farming.id -> Perk.Farmer
            stats.hunter.id -> Perk.Tracker
            stats.thieving.id -> Perk.Burglar
            stats.construction.id -> Perk.Builder
            stats.agility.id -> Perk.Acrobat
            stats.fletching.id -> Perk.Fletcher
            stats.slayer.id -> Perk.Bounty
            stats.prayer.id -> Perk.Devout
            else -> null
        }

    // ---- Skilling yield / success ----------------------------------------------

    /** `Instinct`: +1 invisible Woodcutting & Mining level per level. */
    public fun invisibleGatherBoost(player: Player): Int = level(player, Perk.Instinct)

    /** `Lumberjack`: +500 bps (+5%) chance per level of doubling a cut log. */
    public fun doubleLogsBps(player: Player): Int = level(player, Perk.Lumberjack) * 500

    /** `Motherlode`: +500 bps (+5%) chance per level of doubling mined ore. */
    public fun doubleOreBps(player: Player): Int = level(player, Perk.Motherlode) * 500

    /** `Trawler`: +500 bps (+5%) chance per level of doubling a caught fish. */
    public fun doubleFishBps(player: Player): Int = level(player, Perk.Trawler) * 500

    /** `Gourmet`: -500 bps (-5%) burn chance per level (multiplicative). */
    public fun burnReductionBps(player: Player): Int = level(player, Perk.Gourmet) * 500

    /** `Nimble`: +1 flat pickpocket success chance per level. */
    public fun pickpocketBonus(player: Player): Int = level(player, Perk.Nimble)

    /** `Runic Mastery`: +500 bps (+5%) chance per level of doubling bound runes. */
    public fun doubleRunesBps(player: Player): Int = level(player, Perk.RunicMastery) * 500
}
