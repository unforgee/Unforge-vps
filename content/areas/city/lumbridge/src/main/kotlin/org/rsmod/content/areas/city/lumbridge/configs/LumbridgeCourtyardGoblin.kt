package org.rsmod.content.areas.city.lumbridge.configs

import org.rsmod.api.type.builders.npc.NpcBuilder
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.game.type.util.CompactableIntArray

internal object LumbridgeCourtyardGoblinNpc : NpcBuilder() {
    init {
        build("lumbridge_courtyard_goblin") {
            name = "Goblin"
            desc = "An ugly green creature."
            size = 1
            models = CompactableIntArray(24457, 24488, 24434, 24478, 24441, 24448)
            head = CompactableIntArray(intArrayOf(24210))
            readyAnim = 6181
            walkAnim = 6180
            // 128 = 100% scale; 256 = 200%
            resizeH = 256
            resizeV = 256
        }
    }
}

internal object LumbridgeCourtyardGoblinRefs : NpcReferences() {
    val lumbridge_courtyard_goblin = find("lumbridge_courtyard_goblin")
}
