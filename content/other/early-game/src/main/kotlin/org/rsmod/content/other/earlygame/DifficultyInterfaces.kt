package org.rsmod.content.other.earlygame

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.interf.InterfaceType

public typealias difficulty_interfaces = DifficultyInterfaces

public typealias difficulty_components = DifficultyComponents

public object DifficultyInterfaces : InterfaceReferences() {
    public val unforgeDifficulty: InterfaceType = find("unforge_difficulty")
}

public object DifficultyComponents : ComponentReferences() {
    public val root: ComponentType = find("unforge_difficulty:root")
    public val title: ComponentType = find("unforge_difficulty:title")
    public val subtitle: ComponentType = find("unforge_difficulty:subtitle")
    public val accentRule: ComponentType = find("unforge_difficulty:accent_rule")

    public val detail: ComponentType = find("unforge_difficulty:detail")
    public val status: ComponentType = find("unforge_difficulty:status")

    public val confirmWrap: ComponentType = find("unforge_difficulty:confirm_wrap")
    public val confirm: ComponentType = find("unforge_difficulty:confirm")
    public val confirmBox: ComponentType = find("unforge_difficulty:confirm_box")
    public val confirmText: ComponentType = find("unforge_difficulty:confirm_text")

    public val cardWraps: List<ComponentType> = cardParts("wrap")
    public val cardHits: List<ComponentType> = cardParts("hit")
    public val cardBoxes: List<ComponentType> = cardParts("box")
    public val cardSels: List<ComponentType> = cardParts("sel")
    public val cardNames: List<ComponentType> = cardParts("name")
    public val cardTags: List<ComponentType> = cardParts("tag")

    private fun cardParts(part: String): List<ComponentType> =
        List(DifficultyInterfaceBuilder.TIER_COUNT) { find("unforge_difficulty:c${it}_$part") }

    /** Every component keyed by its child name - used by the builder for `layer` parents. */
    public val all: Map<String, ComponentType> = buildMap {
        childNames().forEach { name -> put(name, find("unforge_difficulty:$name")) }
    }

    private fun childNames(): List<String> = buildList {
        addAll(listOf("root", "bg", "border", "title", "subtitle", "accent_rule"))
        for (i in 0 until DifficultyInterfaceBuilder.TIER_COUNT) {
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
