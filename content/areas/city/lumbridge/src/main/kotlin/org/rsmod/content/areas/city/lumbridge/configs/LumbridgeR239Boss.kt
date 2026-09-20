package org.rsmod.content.areas.city.lumbridge.configs

import org.rsmod.api.type.builders.npc.NpcBuilder
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.game.type.util.CompactableIntArray

internal object LumbridgeR239BossNpc : NpcBuilder() {
    init {
        build("r239_test_boss") {
            name = "Lumbridge Goblin Boss"
            desc = "A hulking goblin boss guarding Lumbridge."
            size = 2
            models = CompactableIntArray(24457, 24488, 24434, 24478, 24441, 24448)
            head = CompactableIntArray(intArrayOf(24210))
            readyAnim = 6181
            walkAnim = 6180
            resizeH = 256
            resizeV = 256
            // Boss perks and PvM classification use vislevel >= 100.
            vislevel = 120
            op[1] = "Attack"
            attack = 85
            strength = 95
            defence = 80
            hitpoints = 1_000
            attackRange = 1
            huntRange = 12
            respawnRate = 100
        }
    }
}

internal object LumbridgeR239BossRefs : NpcReferences() {
    val r239_test_boss = find("r239_test_boss")
}
