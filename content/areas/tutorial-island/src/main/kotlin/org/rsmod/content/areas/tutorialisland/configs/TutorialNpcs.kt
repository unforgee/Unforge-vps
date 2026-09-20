@file:Suppress("SpellCheckingInspection", "unused")

package org.rsmod.content.areas.tutorialisland.configs

import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.npc.NpcReferences

typealias tutorial_npcs = TutorialNpcs

object TutorialNpcs : NpcReferences() {
    val basics_tutor = find("tut2_basics_tutor")
    val survival_tutor = find("tut2_survival_tutor")
    val fishing_spot = find("0_26_95_tut2_fishing")
    val nav_tutor = find("tut2_nav_tutor")
    val quest_tutor = find("tut2_quest_tutor")
    val mining_tutor = find("tut2_mining_tutor")
    val combat_tutor = find("tut2_combat_tutor")
    val giant_rat = find("tut2_giantrat")
    val bank_tutor = find("tut2_bank_tutor")
    val prayer_tutor = find("tut2_prayer_tutor")
    val ironman_tutor = find("tut2_ironman_tutor")
    val magic_tutor = find("tut2_magic_tutor")
    val chicken = find("tut2_chicken")
    val skippy = find("skippy")
}

internal object TutorialNpcEditor : NpcEditor() {
    init {
        // The cache type ships with a wander range, so the spots drift out of the pond like any
        // other npc. Mainland fishing spots are pinned the same way in `api/config` `NpcEdits`.
        edit(tutorial_npcs.fishing_spot) {
            moveRestrict = nomove
            wanderRange = 0
        }

        // Live has him pacing the corridor between the tin and copper rocks, not standing still.
        edit(tutorial_npcs.mining_tutor) { wanderRange = 3 }
    }
}
