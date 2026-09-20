package org.rsmod.content.areas.unforge.configs

import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.params
import org.rsmod.api.type.builders.npc.NpcBuilder
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.game.type.util.CompactableIntArray
import org.rsmod.game.type.util.ParamMapBuilder

/**
 * Custom Kronos npc types whose ids (15001+) collided with r239 cache npcs. Re-created on free ids
 * with lookalike models/anims; combat stats come from the Kronos combat definitions.
 */
internal object UnforgeCustomNpcs : NpcBuilder() {
    init {
        // Kronos 15001 - event boss, looks like Nechryarch.
        build("cw_corrupted_nechryarch") {
            name = "Corrupted Nechryarch"
            size = 2
            models = CompactableIntArray(32932)
            readyAnim = 6371
            walkAnim = 6372
            vislevel = 300
            op[1] = "Attack"
            attack = 310
            strength = 260
            defence = 140
            hitpoints = 5000
            ranged = 1
            magic = 150
            respawnRate = 60
            paramMap =
                ParamMapBuilder()
                    .apply {
                        this[params.attackrate] = 4
                        this[params.attack_anim] = UnforgeCustomSeqs.giant_champion_attack
                        this[params.defend_anim] = UnforgeCustomSeqs.giant_champion_defend
                        this[params.death_anim] = UnforgeCustomSeqs.giant_champion_death
                        this[params.attack_stab] = 50
                        this[params.attack_slash] = 50
                        this[params.attack_crush] = 50
                        this[params.attack_magic] = 100
                        this[params.attack_ranged] = 30
                        this[params.defence_stab] = 150
                        this[params.defence_slash] = 150
                        this[params.defence_crush] = 50
                        this[params.defence_magic] = 50
                        this[params.defence_ranged] = 150
                        this[params.venom_immunity] = 1
                        this[params.slayer_levelrequire] = 1
                        this[params.npc_attack_type] = categories.attacktype_crush
                    }
                    .toParamMap()
        }

        // Kronos 15004 - Necromancer minion, looks like a skeleton.
        build("cw_unstable_corrupted_skeleton") {
            name = "Unstable Corrupted Skeleton"
            size = 1
            models = CompactableIntArray(21170, 21178, 21159, 21156, 21180, 21202, 21187)
            readyAnim = 5483
            walkAnim = 5481
            vislevel = 100
            op[1] = "Attack"
            attack = 1
            strength = 1
            defence = 1
            hitpoints = 50
            ranged = 1
            magic = 1
            paramMap =
                ParamMapBuilder()
                    .apply {
                        this[params.attackrate] = 5
                        this[params.attack_anim] =
                            UnforgeCustomSeqs.skeleton_update_attack_weapon
                        this[params.defend_anim] = UnforgeCustomSeqs.skeleton_update_defend
                        this[params.death_anim] = UnforgeCustomSeqs.skeleton_update_death
                        this[params.defence_stab] = 3
                        this[params.defence_slash] = 3
                        this[params.defence_crush] = 3
                        this[params.defence_magic] = -100
                        this[params.defence_ranged] = 3
                        this[params.poison_immunity] = 1
                        this[params.venom_immunity] = 1
                        this[params.slayer_experience] = 18
                        this[params.slayer_levelrequire] = 1
                        this[params.npc_attack_type] = categories.attacktype_crush
                    }
                    .toParamMap()
        }

        // Kronos 15016 - BrutalLavaDragon boss (flying form), lava dragon look.
        build("cw_brutal_lava_dragon_flying") {
            name = "Brutal lava dragon"
            size = 4
            models = CompactableIntArray(50164, 50163)
            readyAnim = 90
            walkAnim = 79
            resizeH = 110
            resizeV = 110
            vislevel = 318
            op[1] = "Attack"
            attack = 330
            strength = 210
            defence = 258
            hitpoints = 5000
            ranged = 0
            magic = 250
            respawnRate = 50
            paramMap =
                ParamMapBuilder()
                    .apply {
                        this[params.attackrate] = 4
                        this[params.defence_stab] = 50
                        this[params.defence_slash] = 150
                        this[params.defence_crush] = 150
                        this[params.defence_magic] = 80
                        this[params.defence_ranged] = 80
                        this[params.poison_immunity] = 1
                        this[params.venom_immunity] = 1
                        this[params.slayer_experience] = 500
                        this[params.slayer_levelrequire] = 1
                        this[params.npc_attack_type] = categories.attacktype_crush
                    }
                    .toParamMap()
        }

        // Kronos 15019 - BrutalLavaDragon boss (grounded form).
        build("cw_brutal_lava_dragon") {
            name = "Brutal lava dragon"
            size = 4
            models = CompactableIntArray(50164, 50163)
            readyAnim = 90
            walkAnim = 79
            resizeH = 110
            resizeV = 110
            vislevel = 318
            op[1] = "Attack"
            attack = 330
            strength = 210
            defence = 258
            hitpoints = 5000
            ranged = 0
            magic = 250
            respawnRate = 50
            paramMap =
                ParamMapBuilder()
                    .apply {
                        this[params.attackrate] = 4
                        this[params.defend_anim] = UnforgeCustomSeqs.dragon_block
                        this[params.death_anim] = UnforgeCustomSeqs.dragon_death
                        this[params.defence_stab] = 50
                        this[params.defence_slash] = 150
                        this[params.defence_crush] = 150
                        this[params.defence_magic] = 80
                        this[params.defence_ranged] = 80
                        this[params.poison_immunity] = 1
                        this[params.venom_immunity] = 1
                        this[params.slayer_experience] = 500
                        this[params.slayer_levelrequire] = 1
                        this[params.npc_attack_type] = categories.attacktype_crush
                    }
                    .toParamMap()
        }

        // Kronos 15024 - untargetable storm visual (no ops), tornado model.
        build("cw_tornado") {
            name = "<col=00ffff>Tornado</col>"
            size = 1
            models = CompactableIntArray(56537)
            readyAnim = 12236
            walkAnim = 12236
            alwaysOnTop = true
            minimap = false
            vislevel = 0
            attack = 500
            strength = 500
            defence = 500
            hitpoints = 200
            ranged = 500
            magic = 500
            respawnRate = 50
            paramMap =
                ParamMapBuilder()
                    .apply {
                        this[params.attackrate] = 1
                        this[params.attack_stab] = 100
                        this[params.attack_slash] = 100
                        this[params.attack_crush] = 100
                        this[params.attack_magic] = 100
                        this[params.attack_ranged] = 100
                        this[params.poison_immunity] = 1
                        this[params.venom_immunity] = 1
                        this[params.slayer_levelrequire] = 1
                        this[params.npc_attack_type] = categories.attacktype_stab
                    }
                    .toParamMap()
        }

        // Edgeville starter-shop keepers. These are separate types so shop bindings
        // do not change the vanilla shops of Gerrant, Jatix, Zaff or Brian elsewhere.
        for ((name, displayName) in
            listOf(
                "cw_edgeville_melee_keeper" to "Melee Shop Keeper",
                "cw_edgeville_magic_keeper" to "Magic Shop Keeper",
                "cw_edgeville_range_keeper" to "Range Shop Keeper",
                "cw_edgeville_supply_keeper" to "Supply Shop Keeper",
                "cw_edgeville_skilling_keeper" to "Skilling Shop Keeper",
            )) {
            build(name) {
                this.name = displayName
                size = 1
                models = CompactableIntArray(128)
                readyAnim = 808
                walkAnim = 819
                vislevel = 100
                op[1] = "Trade"
            }
        }
    }
}

internal object UnforgeCustomNpcRefs : NpcReferences() {
    val corrupted_nechryarch = find("cw_corrupted_nechryarch")
    val unstable_corrupted_skeleton = find("cw_unstable_corrupted_skeleton")
    val brutal_lava_dragon_flying = find("cw_brutal_lava_dragon_flying")
    val brutal_lava_dragon = find("cw_brutal_lava_dragon")
    val tornado = find("cw_tornado")
    val edgeville_melee_keeper = find("cw_edgeville_melee_keeper")
    val edgeville_magic_keeper = find("cw_edgeville_magic_keeper")
    val edgeville_range_keeper = find("cw_edgeville_range_keeper")
    val edgeville_supply_keeper = find("cw_edgeville_supply_keeper")
    val edgeville_skilling_keeper = find("cw_edgeville_skilling_keeper")
}

internal object UnforgeCustomSeqs : SeqReferences() {
    val giant_champion_attack = find("giant_champion_attack")
    val giant_champion_defend = find("giant_champion_defend")
    val giant_champion_death = find("giant_champion_death")
    val skeleton_update_attack_weapon = find("skeleton_update_attack_weapon")
    val skeleton_update_defend = find("skeleton_update_defend")
    val skeleton_update_death = find("skeleton_update_death")
    val dragon_block = find("dragon_block")
    val dragon_death = find("dragon_death")
}
