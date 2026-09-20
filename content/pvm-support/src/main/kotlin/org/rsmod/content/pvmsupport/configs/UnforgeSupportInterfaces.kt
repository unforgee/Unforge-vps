package org.rsmod.content.pvmsupport.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.timer.TimerReferences

typealias support_interfaces = UnforgeSupportInterfaces

typealias support_components = UnforgeSupportComponents

typealias support_timers = UnforgeSupportTimers

object UnforgeSupportInterfaces : InterfaceReferences() {
    val unforge_support = find("unforge_support")
}

object UnforgeSupportComponents : ComponentReferences() {
    val root = find("unforge_support:root")
    val title = find("unforge_support:title")
    val body = find("unforge_support:body")
}

object UnforgeSupportTimers : TimerReferences() {
    /** Drives the live refresh of the support HUD while it is open. */
    val support_hud = find("support_hud")
}
