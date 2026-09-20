package org.rsmod.content.other.devhub.configs

import org.rsmod.api.type.builders.inv.InvBuilder
import org.rsmod.api.type.refs.inv.InvReferences
import org.rsmod.content.other.devhub.configs.DevHubInterfaceBuilder.GRID_COLUMNS
import org.rsmod.content.other.devhub.configs.DevHubInterfaceBuilder.ITEM_GRID_ROWS
import org.rsmod.game.type.inv.InvStackType

typealias devhub_invs = DevHubInvs

object DevHubInvs : InvReferences() {
    val dev_hub_shop = find("dev_hub_shop")
}

/**
 * The per-player inv backing the dev-hub item grid: one slot per scrollable grid cell, filled at
 * runtime with the active category (`DevHubScript.refresh`). Default (`Temp`) scope - each player
 * views their own list, and nothing about it should persist.
 *
 * Deliberately no cache-baked stock: the encoder caps baked stock at 255 entries, and the grid is
 * repopulated per category.
 */
internal object DevHubInvBuilder : InvBuilder() {
    init {
        build("dev_hub_shop") {
            size = GRID_COLUMNS * ITEM_GRID_ROWS
            stack = InvStackType.Always
        }
    }
}
