package org.rsmod.content.other.earlygame

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.interf.InterfaceType

public typealias starterrelic_interfaces = StarterRelicInterfaces

public typealias starterrelic_components = StarterRelicComponents

public object StarterRelicInterfaces : InterfaceReferences() {
    public val starterRelic: InterfaceType = find("starter_relic")
}

public object StarterRelicComponents : ComponentReferences() {
    public val root: ComponentType = find("starter_relic:root")
    public val title: ComponentType = find("starter_relic:title")
    public val subtitle: ComponentType = find("starter_relic:subtitle")
    public val accentRule: ComponentType = find("starter_relic:accent_rule")

    public val detail: ComponentType = find("starter_relic:detail")
    public val status: ComponentType = find("starter_relic:status")

    public val confirmWrap: ComponentType = find("starter_relic:confirm_wrap")
    public val confirm: ComponentType = find("starter_relic:confirm")
    public val confirmBox: ComponentType = find("starter_relic:confirm_box")
    public val confirmText: ComponentType = find("starter_relic:confirm_text")

    public val cardWraps: List<ComponentType> = cardParts("wrap")
    public val cardHits: List<ComponentType> = cardParts("hit")
    public val cardBoxes: List<ComponentType> = cardParts("box")
    public val cardSels: List<ComponentType> = cardParts("sel")
    public val cardNames: List<ComponentType> = cardParts("name")
    public val cardTags: List<ComponentType> = cardParts("tag")

    private fun cardParts(part: String): List<ComponentType> =
        List(StarterRelicInterfaceBuilder.RELIC_COUNT) { find("starter_relic:c${it}_$part") }

    /** Every component keyed by its child name - used by the builder for `layer` parents. */
    public val all: Map<String, ComponentType> = buildMap {
        childNames().forEach { name -> put(name, find("starter_relic:$name")) }
    }

    private fun childNames(): List<String> = buildList {
        addAll(listOf("root", "bg", "border", "title", "subtitle", "accent_rule"))
        for (i in 0 until StarterRelicInterfaceBuilder.RELIC_COUNT) {
            add("c${i}_wrap")
            add("c${i}_hit")
            add("c${i}_box")
            add("c${i}_sel")
            add("c${i}_name")
            add("c${i}_tag")
        }
        addAll(listOf("detail", "status", "confirm_wrap", "confirm", "confirm_box", "confirm_text"))
    }
}
