package org.rsmod.content.areas.unforge.map

import org.rsmod.api.config.refs.params
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.game.type.npc.NpcType

// Hand-authored distance-attack style overrides.
//
// The Kronos combat source data carries an `attack_style` of `RANGED`/`MAGIC` for a small set of
// npcs, but the generated `UnforgeCombatEdits` only maps melee attack types (stab/slash/crush).
// This editor declares the ranged/magic style so `NvPCombat` rolls the matching accuracy + max hit
// and the npc stops at its attack range (`visType.attackRange`) instead of walking into melee.
//
// Values map to `NpcAttackStyle`: 1 = Ranged, 2 = Magic.
internal object UnforgeCombatStyleEdits : NpcEditor() {
    init {
        magic(
            UnforgeWanderNpcs.godwars_spiritual_armadyl_mage,
            UnforgeWanderNpcs.godwars_spiritual_bandos_mage,
            UnforgeWanderNpcs.godwars_spiritual_saradomin_mage,
            UnforgeWanderNpcs.godwars_spiritual_zamorak_mage,
            UnforgeWanderNpcs.saradomin_mage,
            UnforgeWanderNpcs.guthix_mage,
            UnforgeWanderNpcs.zamorak_mage,
            UnforgeWanderNpcs.rc_zmi_mage,
            UnforgeWanderNpcs.rc_zmi_mage2,
            UnforgeWanderNpcs.myq4_magic_mercenary_visible,
            UnforgeWanderNpcs.slayer_infernal_mage_1,
            UnforgeWanderNpcs.slayer_infernal_mage_2,
            UnforgeWanderNpcs.slayer_infernal_mage_3,
            UnforgeWanderNpcs.slayer_infernal_mage_4,
            UnforgeWanderNpcs.slayer_infernal_mage_5,
            UnforgeWanderNpcs.slayer_abberant_spectre_1,
            UnforgeWanderNpcs.slayer_abberant_spectre_2,
            UnforgeWanderNpcs.slayer_abberant_spectre_3,
            UnforgeWanderNpcs.slayer_abberant_spectre_4,
            UnforgeCombatNpcs.smoke_devil,
            UnforgeCombatNpcs.smoke_devil_boss,
            UnforgeCombatNpcs.necromancer,
            UnforgeCombatNpcs.slayer_kraken_boss,
            UnforgeCombatNpcs.slayer_kraken_sub,
        )

        ranged(
            UnforgeWanderNpcs.godwars_spiritual_armadyl_ranger,
            UnforgeWanderNpcs.godwars_spiritual_bandos_ranger,
            UnforgeWanderNpcs.godwars_spiritual_saradomin_ranger,
            UnforgeWanderNpcs.godwars_spiritual_zamorak_ranger,
            UnforgeWanderNpcs.rc_zmi_ranger,
            UnforgeWanderNpcs.myq4_ranged_mercenary_visible,
            UnforgeWanderNpcs.snakeboss_boss_ranged,
            UnforgeCombatNpcs.hydra,
            UnforgeCombatNpcs.hydraboss,
        )
    }

    private fun magic(vararg npcs: NpcType) {
        for (npc in npcs) {
            edit(npc) { param[params.npc_attack_style] = MAGIC }
        }
    }

    private fun ranged(vararg npcs: NpcType) {
        for (npc in npcs) {
            edit(npc) { param[params.npc_attack_style] = RANGED }
        }
    }
}

private const val RANGED: Int = 1
private const val MAGIC: Int = 2
