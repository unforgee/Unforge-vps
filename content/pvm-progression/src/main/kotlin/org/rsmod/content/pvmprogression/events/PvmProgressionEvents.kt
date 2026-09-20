package org.rsmod.content.pvmprogression.events

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * PvM Progression 2.0 event vocabulary.
 *
 * Every event here is an [UnboundEvent], which the engine's `UnboundEventMap` stores in a list - so
 * any number of subscribers can listen without colliding with each other or with the rest of the
 * server. This is the merge-safe backbone: the other agent's Last Stand / Lucky Kill / Boss
 * Modifier scripts subscribe to the same `NpcHitEvents.Impact` and `NpcKilledEvent` we do, and none
 * of us blocks the other.
 *
 * The managers publish these events so that:
 * - the achievement-hook layer can turn gameplay outcomes into achievement progress, and
 * - the hub / notification layer can react without each manager knowing about the others.
 *
 * None of these events are persisted or carry mutable state - they are thin, immutable signals.
 */

// ── Bounty ───────────────────────────────────────────────────────────────────

/** A single bounty task reached its required amount and its reward was granted. */
data class BountyCompleteEvent(
    val player: Player,
    val category: BountyCategory,
    val bountyId: String,
    val name: String,
) : UnboundEvent

/** All bounties of [category] for [player] have been completed in the current reset window. */
data class BountyCategoryCompleteEvent(val player: Player, val category: BountyCategory) :
    UnboundEvent

enum class BountyCategory {
    DAILY,
    WEEKLY,
    ELITE,
}

// ── Mutation ─────────────────────────────────────────────────────────────────

/** A mutated npc was killed by [killer]. */
data class MutationKillEvent(
    val player: Player,
    val npc: Npc,
    val mutations: List<MutationKind>,
    val rarity: MutationRarity,
) : UnboundEvent

enum class MutationKind {
    GIANT,
    ANCIENT,
    CORRUPTED,
    TREASURE,
    UNSTABLE,
    VAMPIRIC,
    BERSERK,
}

enum class MutationRarity {
    UNCOMMON,
    RARE,
    EPIC,
}

// ── Boss records ─────────────────────────────────────────────────────────────

/** A new personal best kill time was recorded for [bossId]. */
data class BossPersonalBestEvent(
    val player: Player,
    val bossId: Int,
    val bossName: String,
    val timeTenths: Int,
    val previousTenths: Int,
) : UnboundEvent

/** A new per-boss record field (highest hit, lowest HP finish, etc.) was recorded. */
data class BossRecordEvent(
    val player: Player,
    val bossId: Int,
    val bossName: String,
    val field: RecordField,
    val value: Long,
) : UnboundEvent

enum class RecordField {
    HIGHEST_HIT,
    HIGHEST_SPECIAL_HIT,
    DAMAGE_DEALT,
    DAMAGE_TAKEN,
    LOWEST_HP_BOSS_FINISH,
    LOWEST_HP_PERCENT,
    FOOD_CONSUMED,
    PRAYER_CONSUMED,
    KILLS_WITHOUT_FOOD,
    KILLS_WITHOUT_PRAYER_RESTORE,
    DEATHS,
    BEST_STREAK,
}

// ── Combat challenge ─────────────────────────────────────────────────────────

/** A combat challenge was completed successfully and its reward was granted. */
data class CombatChallengeCompleteEvent(
    val player: Player,
    val bossId: Int,
    val bossName: String,
    val type: ChallengeType,
    val tier: ChallengeTier,
) : UnboundEvent

enum class ChallengeTier {
    BRONZE,
    SILVER,
    GOLD,
    ELITE,
}

// ── Streak ───────────────────────────────────────────────────────────────────

/** The player's PvM streak crossed a milestone threshold. */
data class StreakMilestoneEvent(val player: Player, val milestone: Int, val currentStreak: Int) :
    UnboundEvent

/** The player cashed out their accumulated streak pool. */
data class StreakCashOutEvent(val player: Player, val streak: Int, val tokensAwarded: Int) :
    UnboundEvent

// ── Challenge types (declared here so the event above can reference them) ─────

enum class ChallengeType {
    NO_FOOD,
    NO_PRAYER,
    NO_POTION,
    SPEED_KILL,
    LOW_DAMAGE_TAKEN,
    STYLE_LOCK,
    FINISH_WITH_STYLE,
    NO_SPECIAL_ATTACK,
    SPECIAL_FINISH,
    NO_COMPANION,
    COMPANION_ONLY_PHASE,
    NO_MOVEMENT,
    LOW_HP_FINISH,
}

// ── Boss systems ────────────────────────────────────────────────────────────

data class LuckyKillEvent(val player: Player, val npc: Npc, val bossName: String) : UnboundEvent

data class LastStandKillEvent(
    val player: Player,
    val npc: Npc,
    val bossName: String,
    val finishingHp: Int,
    val finishingHpPercentBps: Int,
) : UnboundEvent

data class ModifiedBossKillEvent(
    val player: Player,
    val npc: Npc,
    val bossName: String,
    val modifier: BossModifier,
    val mythic: Boolean,
) : UnboundEvent

data class MythicBossSpawnEvent(val npc: Npc, val bossName: String) : UnboundEvent

data class MythicBossKillEvent(val player: Player, val npc: Npc, val bossName: String) :
    UnboundEvent

enum class BossModifier {
    ENRAGED,
    ARMORED,
    VAMPIRIC,
    QUICK,
    CURSED,
}

data class MysteryEnchantRolledEvent(
    val player: Player,
    val item: org.rsmod.api.equipment.instance.EquipmentInstance,
) : UnboundEvent

data class LegendaryEnchantRolledEvent(
    val player: Player,
    val item: org.rsmod.api.equipment.instance.EquipmentInstance,
) : UnboundEvent
