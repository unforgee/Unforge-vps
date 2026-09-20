@file:Suppress("SpellCheckingInspection", "unused")

package org.rsmod.content.areas.tutorialisland.configs

import org.rsmod.api.type.refs.seq.SeqReferences

typealias tutorial_seqs = TutorialSeqs

object TutorialSeqs : SeqReferences() {
    val small_net = find("human_smallnet")
    val light_fire = find("human_createfire")
    val cook_fire = find("human_firecooking")
    val cook_range = find("human_cooking")
    val mine_bronze_pickaxe = find("human_mining_bronze_pickaxe")
    val smith_anvil = find("human_smithing")
}
