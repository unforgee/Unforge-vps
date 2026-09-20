package org.rsmod.content.pvmprogression.challenge

import org.rsmod.content.pvmprogression.config.PvmProgressionConfig
import org.rsmod.content.pvmprogression.events.ChallengeTier
import org.rsmod.content.pvmprogression.events.ChallengeType

/**
 * One offered (or accepted) combat challenge against a boss.
 *
 * Created by [CombatChallengeManager.maybeOffer] and mutated in place only by the same player's
 * game-thread work. The `config-derivatives` ([speedKillSeconds], [lowDamageCap], etc.) are
 * computed once at offer time from the live [PvmProgressionConfig] and boss combat level, so a
 * mid-fight config change does not retroactively alter an in-progress challenge.
 */
data class PendingChallenge(
    val encounterId: Long,
    val bossId: Int,
    val bossName: String,
    val type: ChallengeType,
    val tier: ChallengeTier,
    var accepted: Boolean = false,
    var failed: Boolean = false,
    var failReason: String? = null,
    /** SPEED_KILL window in seconds, scaled by boss combat level. */
    val speedKillSeconds: Int = 0,
    /** LOW_DAMAGE_TAKEN cap, scaled by boss combat level. */
    val lowDamageCap: Int = 0,
    /** STYLE_LOCK locked style id (HitType ordinal) or -1. */
    val lockedStyle: Int = -1,
    /** FINISH_WITH_STYLE target style id (HitType ordinal) or -1. */
    val finishStyle: Int = -1,
    /** NO_COMPANION / COMPANION_ONLY_PHASE companion damage fraction target (bps). */
    val companionPercentTarget: Int = 0,
)
