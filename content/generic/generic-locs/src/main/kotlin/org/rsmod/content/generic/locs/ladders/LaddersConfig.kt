package org.rsmod.content.generic.locs.ladders

import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.type.editors.loc.LocEditor
import org.rsmod.api.type.refs.loc.LocReferences

internal typealias ladder_locs = LadderLocs

internal object LadderLocs : LocReferences() {
    val ladder_down = find("laddertop")
    val ladder_up = find("ladder")
    val ladder_option = find("laddermiddle")
    val lumbridge_kitchen_dungeonladder = find("qip_cook_trapdoor_open")
    val lumbridge_cellar_dungeonladder = find("ladder_from_cellar")
    val slayer_dungeon_entrance = find("slayer_dungeon_entrance")
    val slayer_dungeon_exit = find("slayer_dungeon_exit")
}

internal object LadderLocsEdits : LocEditor() {
    init {
        edit(ladder_locs.ladder_down) {
            param[params.climb_anim] = seqs.human_pickupfloor
            contentGroup = content.ladder_down
        }

        edit(ladder_locs.ladder_up) { contentGroup = content.ladder_up }

        edit(ladder_locs.ladder_option) { contentGroup = content.ladder_option }

        edit(ladder_locs.lumbridge_kitchen_dungeonladder) {
            param[params.climb_anim] = seqs.human_pickupfloor
            contentGroup = content.dungeonladder_down
        }

        edit(ladder_locs.lumbridge_cellar_dungeonladder) { contentGroup = content.dungeonladder_up }

        // These cache locs are the Slayer Tower's floor transition. Keep the
        // interaction on the same authoritative dungeon-layer path as ladders
        // so movement, animation and destination remain server-controlled.
        edit(ladder_locs.slayer_dungeon_entrance) {
            param[params.climb_anim] = seqs.human_pickupfloor
            contentGroup = content.dungeonladder_down
        }
        edit(ladder_locs.slayer_dungeon_exit) { contentGroup = content.dungeonladder_up }
    }
}
