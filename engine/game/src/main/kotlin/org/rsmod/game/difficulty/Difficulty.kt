package org.rsmod.game.difficulty

/**
 * The permanent, server-authoritative difficulty tier selected by a player on first login.
 *
 * `null` on [org.rsmod.game.entity.Player.difficulty] together with `difficultySelected == false`
 * marks an account that has not yet chosen a tier. The tier owns the player's personal xp rate
 * ([org.rsmod.game.entity.Player.xpRate]) and grants the listed outgoing-damage and drop-rate
 * boosts, expressed in basis points (100 bps = 1%).
 */
public enum class Difficulty(
    public val dbName: String,
    public val displayName: String,
    public val xpRate: Double,
    public val damageBoostBps: Int,
    public val dropBoostBps: Int,
    public val tagline: String,
) {
    EASY(
        dbName = "EASY",
        displayName = "Easy",
        xpRate = 100.0,
        damageBoostBps = 0,
        dropBoostBps = 0,
        tagline = "100x XP rate. No combat or loot bonuses.",
    ),
    MEDIUM(
        dbName = "MEDIUM",
        displayName = "Medium",
        xpRate = 50.0,
        damageBoostBps = 0,
        dropBoostBps = 500,
        tagline = "50x XP rate. +5% chance of an extra drop roll.",
    ),
    HARD(
        dbName = "HARD",
        displayName = "Hard",
        xpRate = 15.0,
        damageBoostBps = 0,
        dropBoostBps = 750,
        tagline = "15x XP rate. +7.5% chance of an extra drop roll.",
    ),
    EXPERT(
        dbName = "EXPERT",
        displayName = "Expert",
        xpRate = 5.0,
        damageBoostBps = 500,
        dropBoostBps = 1000,
        tagline = "5x XP rate. +5% damage, +10% chance of an extra drop roll.",
    ),
    INSANE(
        dbName = "INSANE",
        displayName = "Insane",
        xpRate = 1.0,
        damageBoostBps = 1000,
        dropBoostBps = 1500,
        tagline = "1x XP rate. +10% damage, +15% chance of an extra drop roll.",
    );

    public companion object {
        public fun fromDbName(name: String?): Difficulty? =
            entries.firstOrNull { it.dbName.equals(name, ignoreCase = true) }
    }
}
