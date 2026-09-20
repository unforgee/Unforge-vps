package org.rsmod.content.interfaces.journal.tab.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences

typealias points_interfaces = UnforgePointsInterfaces

typealias points_components = UnforgePointsComponents

object UnforgePointsInterfaces : InterfaceReferences() {
    val unforge_points = find("unforge_points")
}

object UnforgePointsComponents : ComponentReferences() {
    val root = find("unforge_points:root")
    val title = find("unforge_points:title")
    val content = find("unforge_points:content")
    val scrollbar = find("unforge_points:scrollbar")
    val scrollUp = find("unforge_points:scroll_up")
    val scrollDown = find("unforge_points:scroll_down")

    // Each clickable layer sits alone inside a one-child wrap so its `childIndex` is `0`: the
    // client reports `children[childIndex]` clicks, so a button deep in the parent's child list
    // would resolve out of bounds and never emit a packet.
    val scrollUpWrap = find("unforge_points:scroll_up_wrap")
    val scrollDownWrap = find("unforge_points:scroll_down_wrap")
    val totalLabel = find("unforge_points:total_label")
    val totalValue = find("unforge_points:total_value")

    /** Generic row slots, written by the script: a section head or a label+value line. */
    val heads =
        List(UnforgePointsInterfaceBuilder.ROW_SLOTS) { find("unforge_points:row${it}_head") }

    val labels =
        List(UnforgePointsInterfaceBuilder.ROW_SLOTS) { find("unforge_points:row${it}_label") }

    val values =
        List(UnforgePointsInterfaceBuilder.ROW_SLOTS) { find("unforge_points:row${it}_value") }
}
