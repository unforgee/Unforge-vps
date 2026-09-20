package org.rsmod.content.interfaces.combat.tab.configs

import org.rsmod.api.type.refs.comp.ComponentReferences

typealias combat_components = CombatTabComponents

object CombatTabComponents : ComponentReferences() {
    // Rev 239: `combat_interface:0`..`:3`, `:retaliate` and `:special_attack` are layers with no
    // ops, so the client never reports a click against them. The ops live on a child of each:
    // the stance backings carry op1='*' (the client fills in the style name), and retaliate and
    // the special bar carry their own op1. These are also the only ones with Op1 baked into the
    // cache, so no `ifSetEvents` is needed - the parents could never have received the click.
    val stance1 = find("combat_interface:0_back", 2858390886771847764)
    val stance2 = find("combat_interface:1_back", 2437616626352755402)
    val stance3 = find("combat_interface:2_back", 4786378263585440965)
    val stance4 = find("combat_interface:3_back", 4365604003166348603)
    val auto_retaliate = find("combat_interface:com_32", 7472549650049137011)
    val special_attack = find("combat_interface:sp_empty", 6807823470789281920)

    // Rev 239: orbs:specbutton (35) is the graphic; the click target is orbs:36.
    val special_attack_orb = find("orbs:specenergy_text")
}
