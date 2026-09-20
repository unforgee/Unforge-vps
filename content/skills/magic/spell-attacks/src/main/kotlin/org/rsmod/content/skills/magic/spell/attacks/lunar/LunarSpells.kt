package org.rsmod.content.skills.magic.spell.attacks.lunar

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType

/**
 * The castable Lunar utility spells that target another player.
 *
 * Lunar Magicks contains no direct damage spells, so everything registered here is a supportive
 * cast. Spells that require systems outside of the spell attack pipeline (such as `vengeance`,
 * which needs a player hit hook, or `magic_imbue`, which needs a rune-substitution flag) are not
 * registered.
 */
class LunarSpells @Inject constructor() : SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        register(objs.spell_heal_other, manager) { target -> healOther(target) }
        register(objs.spell_energy_transfer, manager) { target -> energyTransfer(target) }
    }

    private fun SpellAttackRepository.register(
        spell: ObjType,
        manager: SpellAttackManager,
        effect: ProtectedAccess.(Player) -> Unit,
    ) {
        register(spell = spell, attack = LunarSpellAttack(manager, effect))
    }

    private class LunarSpellAttack(
        private val manager: SpellAttackManager,
        private val effect: ProtectedAccess.(Player) -> Unit,
    ) : SpellAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
            // Lunar utility spells are cast on players only.
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
            val castResult = manager.attemptCast(this, attack)
            if (castResult.isFailure()) {
                return
            }
            effect(target)
            manager.stopCombat(this)
        }
    }

    private fun ProtectedAccess.healOther(target: Player) {
        target.statHeal(stats.hitpoints, player.magicLvl * HEAL_PER_MAGIC_LEVEL, 0)
    }

    private fun ProtectedAccess.energyTransfer(target: Player) {
        target.runEnergy = FULL_RUN_ENERGY
    }

    private companion object {
        const val HEAL_PER_MAGIC_LEVEL: Int = 3
        const val FULL_RUN_ENERGY: Int = 1000
    }
}
