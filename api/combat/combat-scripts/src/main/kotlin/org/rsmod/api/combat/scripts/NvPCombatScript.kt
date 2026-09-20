package org.rsmod.api.combat.scripts

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isWildernessSafeZone
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.npc.combatAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.NpcAttackStyle
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.combat.player.aggressiveNpc
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.isInPvnCombat
import org.rsmod.api.player.isInPvpCombat
import org.rsmod.api.script.advanced.onDefaultAiApPlayer2
import org.rsmod.api.script.advanced.onDefaultAiOpPlayer2
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.category.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class NvPCombatScript
@Inject
constructor(
    private val combat: NvPCombat,
    private val areaChecker: AreaChecker,
    private val maxHits: MaxHitFormulae,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onDefaultAiOpPlayer2 { attemptCombat(it.target) }
        // Npcs that attack from a distance use `ap` mode so they stop at their attack range
        // instead of walking into melee range.
        onDefaultAiApPlayer2 { attemptCombat(it.target) }
    }

    private fun StandardNpcAccess.attemptCombat(target: Player) {
        if (!canAttack(target)) {
            resetMode()
            return
        }
        val attack = npc.resolveCombatAttack(target)
        combat.attack(this, target, attack)
    }

    private fun StandardNpcAccess.canAttack(target: Player): Boolean {
        if (target.coords.isWildernessSafeZone() || npc.coords.isWildernessSafeZone()) {
            return false
        }
        val singleCombat = !mapMultiway(areaChecker)
        if (singleCombat) {
            if (target.isInPvpCombat()) {
                return false
            }

            if (target.isInPvnCombat()) {
                if (target.aggressiveNpc != null && target.aggressiveNpc != npc.uid) {
                    return false
                }
            }
        }
        return true
    }

    private fun Npc.resolveCombatAttack(target: Player): CombatAttack.NpcAttack =
        when (combatAttackStyle()) {
            NpcAttackStyle.Ranged -> CombatAttack.NpcRanged(RangedAttackType.Standard)
            NpcAttackStyle.Magic -> CombatAttack.NpcMagic(maxHits.getMagicMaxHit(this, target))
            NpcAttackStyle.Melee -> CombatAttack.NpcMelee(resolveMeleeAttackType())
        }

    private fun Npc.resolveMeleeAttackType(): MeleeAttackType {
        val category = visType.paramOrNull(params.npc_attack_type)
        return when {
            category.isType(categories.attacktype_stab) -> MeleeAttackType.Stab
            category.isType(categories.attacktype_slash) -> MeleeAttackType.Slash
            else -> MeleeAttackType.Crush
        }
    }
}
