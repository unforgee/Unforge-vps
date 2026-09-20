package org.rsmod.content.interfaces.iteminspect.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.game.type.comp.ComponentType

typealias iteminspect_interfaces = ItemInspectInterfaces

typealias iteminspect_components = ItemInspectComponents

object ItemInspectInterfaces : InterfaceReferences() {
    val unforge_iteminspect = find("unforge_iteminspect")
}

object ItemInspectComponents : ComponentReferences() {
    const val LINE_POOL: Int = 26

    val root = find("unforge_iteminspect:root")
    val title = find("unforge_iteminspect:title")
    val subtitle = find("unforge_iteminspect:subtitle")
    val itemModel = find("unforge_iteminspect:item_model")
    val closeWrap = find("unforge_iteminspect:close_wrap")
    val close = find("unforge_iteminspect:close")
    val closeG = find("unforge_iteminspect:close_g")
    val accentRule = find("unforge_iteminspect:accent_rule")

    val content = find("unforge_iteminspect:content")
    val lines = List(LINE_POOL) { find("unforge_iteminspect:line$it") }

    val scrollbar = find("unforge_iteminspect:scrollbar")
    val scrollUpWrap = find("unforge_iteminspect:scroll_up_wrap")
    val scrollUp = find("unforge_iteminspect:scroll_up")
    val scrollUpBox = find("unforge_iteminspect:scroll_up_box")
    val scrollUpText = find("unforge_iteminspect:scroll_up_text")
    val scrollDownWrap = find("unforge_iteminspect:scroll_down_wrap")
    val scrollDown = find("unforge_iteminspect:scroll_down")
    val scrollDownBox = find("unforge_iteminspect:scroll_down_box")
    val scrollDownText = find("unforge_iteminspect:scroll_down_text")

    /** Canonical child-name list; source of keys for [all]. */
    private val childNames: List<String> = buildList {
        fun reg(name: String) {
            add(name)
        }
        listOf(
                "root",
                "bg",
                "border",
                "title",
                "subtitle",
                "close_wrap",
                "close",
                "close_g",
                "item_model",
                "accent_rule",
                "content",
            )
            .forEach { reg(it) }
        for (i in 0 until LINE_POOL) {
            reg("line$i")
        }
        listOf(
                "scrollbar",
                "scroll_up_wrap",
                "scroll_up",
                "scroll_up_box",
                "scroll_up_text",
                "scroll_down_wrap",
                "scroll_down",
                "scroll_down_box",
                "scroll_down_text",
            )
            .forEach { reg(it) }
    }

    /**
     * Every component keyed by its child name - used by the builder for `layer` parents and
     * hover-script args. Packed ids resolve through `component.sym`, so ordering does not matter.
     */
    val all: Map<String, ComponentType> = buildMap {
        childNames.forEach { name -> put(name, find("unforge_iteminspect:$name")) }
    }
}
