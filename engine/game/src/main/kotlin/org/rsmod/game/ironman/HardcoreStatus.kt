package org.rsmod.game.ironman

public enum class HardcoreStatus(public val dbName: String) {
    /** Hardcore life still intact. */
    ACTIVE("ACTIVE"),

    /** Death occurred but demotion has not been fully processed yet. */
    DEAD("DEAD"),

    /** Hardcore status consumed by a dangerous death; account keeps its demoted mode. */
    DEMOTED("DEMOTED"),

    /** Account is not hardcore (regular, ironman, ultimate, group, or never selected). */
    DISABLED("DISABLED");

    public val hasHardcoreRemaining: Boolean
        get() = this == ACTIVE

    public companion object {
        public fun fromDbName(name: String?): HardcoreStatus =
            entries.firstOrNull { it.dbName.equals(name, ignoreCase = true) } ?: DISABLED
    }
}
