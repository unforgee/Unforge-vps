package org.rsmod.game.ironman

/**
 * The permanent, server-authoritative game mode selected by a player on first login.
 *
 * `null` on [org.rsmod.game.entity.Player.gameMode] means the account has not yet chosen a mode
 * (`gameModeSelected == false`) and must not be allowed into normal gameplay.
 */
public enum class GameMode(
    public val dbName: String,
    public val displayName: String,
    public val difficulty: String,
    public val description: String,
    public val restrictions: String,
    public val hardcoreConsequence: String,
) {
    REGULAR(
        dbName = "REGULAR",
        displayName = "Regular",
        difficulty = "Standard",
        description = "The standard experience with no restrictions.",
        restrictions =
            "No restrictions. Trading, banking, shops and picking up items are all allowed.",
        hardcoreConsequence = "None.",
    ),
    IRONMAN(
        dbName = "IRONMAN",
        displayName = "Ironman",
        difficulty = "Hard",
        description = "Self-sufficient: no trading or taking items from other players.",
        restrictions =
            "No trading, no picking up other players' drops or their kill loot, no shared storage.",
        hardcoreConsequence = "None.",
    ),
    HARDCORE_IRONMAN(
        dbName = "HARDCORE_IRONMAN",
        displayName = "Hardcore Ironman",
        difficulty = "Very hard",
        description = "Ironman with one life. A dangerous death demotes you to regular Ironman.",
        restrictions = "Same as Ironman, plus a hardcore status that can be lost once.",
        hardcoreConsequence = "First dangerous death demotes you to Ironman.",
    ),
    ULTIMATE_IRONMAN(
        dbName = "ULTIMATE_IRONMAN",
        displayName = "Ultimate Ironman",
        difficulty = "Very hard",
        description = "Ironman without a bank. Carry everything you own.",
        restrictions =
            "Same as Ironman, plus no bank or shared storage. Inventory and worn equipment only.",
        hardcoreConsequence = "None.",
    ),
    HARDCORE_ULTIMATE_IRONMAN(
        dbName = "HARDCORE_ULTIMATE_IRONMAN",
        displayName = "Hardcore Ultimate Ironman",
        difficulty = "Extreme",
        description = "Ultimate Ironman with one life.",
        restrictions = "Same as Ultimate Ironman, plus a hardcore status that can be lost once.",
        hardcoreConsequence = "First dangerous death demotes you to Ultimate Ironman.",
    ),
    GROUP_IRONMAN(
        dbName = "GROUP_IRONMAN",
        displayName = "Group Ironman",
        difficulty = "Hard",
        description = "Ironman, but you may share items and storage with your permanent group.",
        restrictions =
            "No trading or item sharing outside your group. Group storage is shared only between group members.",
        hardcoreConsequence = "None.",
    );

    /** Modes that may not trade, take player drops, or use shared storage. */
    public val isIronmanRestricted: Boolean
        get() = this != REGULAR

    /** Modes with the one-life hardcore status. */
    public val isHardcore: Boolean
        get() = this == HARDCORE_IRONMAN || this == HARDCORE_ULTIMATE_IRONMAN

    /** Modes that may not use the bank or shared storage. */
    public val isUltimate: Boolean
        get() = this == ULTIMATE_IRONMAN || this == HARDCORE_ULTIMATE_IRONMAN

    public val isGroup: Boolean
        get() = this == GROUP_IRONMAN

    /** The mode a hardcore account keeps after losing its hardcore status. */
    public val demotedMode: GameMode
        get() =
            when (this) {
                HARDCORE_IRONMAN -> IRONMAN
                HARDCORE_ULTIMATE_IRONMAN -> ULTIMATE_IRONMAN
                else -> this
            }

    public companion object {
        public fun fromDbName(name: String?): GameMode? =
            entries.firstOrNull { it.dbName.equals(name, ignoreCase = true) }
    }
}
