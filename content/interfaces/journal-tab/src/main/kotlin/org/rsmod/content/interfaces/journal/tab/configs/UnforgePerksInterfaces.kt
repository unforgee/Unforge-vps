package org.rsmod.content.interfaces.journal.tab.configs

import org.rsmod.api.player.perk.Perk
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences

typealias perks_interfaces = UnforgePerksInterfaces

typealias perks_components = UnforgePerksComponents

object UnforgePerksInterfaces : InterfaceReferences() {
    val unforge_perks = find("unforge_perks")
}

object UnforgePerksComponents : ComponentReferences() {
    val root = find("unforge_perks:root")
    val title = find("unforge_perks:title")
    val points = find("unforge_perks:points")
    val points_label = find("unforge_perks:points_label")
    val hint = find("unforge_perks:hint")
    val content = find("unforge_perks:content")
    val scrollbar = find("unforge_perks:scrollbar")
    val scrollUp = find("unforge_perks:scroll_up")
    val scrollDown = find("unforge_perks:scroll_down")

    // One-child wrap holders keep every clickable layer at `childIndex == 0`; the client reports
    // `children[childIndex]` on click, so a button deep in the parent's child list would resolve
    // out of bounds and never emit a packet.
    val scrollUpWrap = find("unforge_perks:scroll_up_wrap")
    val scrollDownWrap = find("unforge_perks:scroll_down_wrap")
    val sectionCombat = find("unforge_perks:sec_combat")
    val sectionSkilling = find("unforge_perks:sec_skilling")

    /** One entry per [Perk] enum value, in declaration order. */
    val names = List(Perk.entries.size) { find("unforge_perks:perk${it}_name") }

    val descs = List(Perk.entries.size) { find("unforge_perks:perk${it}_desc") }
    val levels = List(Perk.entries.size) { find("unforge_perks:perk${it}_level") }
    val trainButtons = List(Perk.entries.size) { find("unforge_perks:perk${it}_train") }
    val trainWraps = List(Perk.entries.size) { find("unforge_perks:perk${it}_train_wrap") }

    /** `[perkIndex][segment]` - ten visual pips representing five ranks each. */
    val segments =
        List(Perk.entries.size) { perk ->
            List(UnforgePerksInterfaceBuilder.SEGMENTS_PER_PERK) { seg ->
                find("unforge_perks:perk${perk}_seg$seg")
            }
        }
}
