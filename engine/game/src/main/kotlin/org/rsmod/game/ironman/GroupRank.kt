package org.rsmod.game.ironman

public enum class GroupRank(public val dbName: String) {
    LEADER("LEADER"),
    MEMBER("MEMBER");

    public companion object {
        public fun fromDbName(name: String?): GroupRank? =
            entries.firstOrNull { it.dbName.equals(name, ignoreCase = true) }
    }
}
