package org.rsmod.content.other.league.configs

import org.rsmod.api.type.refs.seq.SeqReferences

typealias league_combat_seqs = LeagueCombatSeqs

object LeagueCombatSeqs : SeqReferences() {
    val human_sword_slash = find("human_sword_slash")
    val human_sword_stab = find("human_sword_stab")
    val human_spear_lunge = find("human_spear_lunge")
}
