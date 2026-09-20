package org.rsmod.api.db

import org.rsmod.module.RuntimePaths

public data class DatabaseConfig(
    val scheme: String,
    val path: String,
    val user: String?,
    val password: String?,
) {
    public val url: String
        get() = "$scheme$path"

    public companion object {
        public fun createSqlite(): DatabaseConfig =
            DatabaseConfig(
                "jdbc:sqlite:",
                RuntimePaths.data.resolve("saves").resolve("game.db").toString(),
                null,
                null,
            )
    }
}
