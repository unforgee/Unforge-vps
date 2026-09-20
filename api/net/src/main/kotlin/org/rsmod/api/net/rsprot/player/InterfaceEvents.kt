package org.rsmod.api.net.rsprot.player

import org.rsmod.game.type.comp.UnpackedComponentType
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.ui.UserInterfaceMap

internal object InterfaceEvents {
    /**
     * Clicks on a static component arrive with `comsub == -1` and are validated against the
     * cache-baked component events, _unless_ the server has sent an `ifSetEvents` range covering
     * the static slot. A runtime range then replaces the baked events entirely, mirroring the
     * client, where `if_setevents` overwrites the component's event mask - including with an empty
     * set, which `ifSetPauseText` and `ifObjbox` use to disable a component.
     *
     * Dynamic slots (`comsub >= 0`) stay runtime-only: the server must enable them explicitly.
     */
    fun isEnabled(
        ui: UserInterfaceMap,
        component: UnpackedComponentType,
        comsub: Int,
        event: IfEvent,
    ): Boolean {
        val verifyStaticEvents = comsub == -1 && !ui.events.contains(component, comsub)
        return if (verifyStaticEvents) {
            component.hasEvent(event)
        } else {
            ui.hasEvent(component, comsub, event)
        }
    }
}
