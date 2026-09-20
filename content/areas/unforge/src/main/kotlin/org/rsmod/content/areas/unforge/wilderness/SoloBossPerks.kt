package org.rsmod.content.areas.unforge.wilderness

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.perk.PerkVarps
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.pvmpoints.PvmPoints
import org.rsmod.game.entity.Player
import org.rsmod.game.type.varp.VarpType

enum class SoloBossPerk(val displayName: String, val description: String, val varp: VarpType) {
    DAMAGE("Damage Done", "+5% damage per level", PerkVarps.soloBossDamage),
    ATTACK_SPEED("Attack Speed", "-5% attack cycle per level", PerkVarps.soloBossAttackSpeed),
    MAX_HEALTH("Max Health", "+5% maximum health per level", PerkVarps.soloBossMaxHealth),
}

@Singleton
class SoloBossPerkService @Inject constructor(private val pvmPoints: PvmPoints) {
    companion object {
        const val MAX_LEVEL = 100
        const val BASE_COST = 10

        fun costForLevel(level: Int): Int = BASE_COST + level.coerceIn(0, MAX_LEVEL) * 5

        fun bonusBpsForLevel(level: Int): Int = level.coerceIn(0, MAX_LEVEL) * 500
    }

    fun level(player: Player, perk: SoloBossPerk): Int =
        player.vars[perk.varp].coerceIn(0, MAX_LEVEL)

    fun cost(player: Player, perk: SoloBossPerk): Int = costForLevel(level(player, perk))

    fun upgrade(player: Player, perk: SoloBossPerk): Boolean {
        val level = level(player, perk)
        if (level >= MAX_LEVEL) return false
        val cost = cost(player, perk)
        if (!pvmPoints.spend(player, cost)) return false
        VarPlayerIntMapSetter.set(player, perk.varp, level + 1)
        return true
    }

    fun bonusBps(player: Player, perk: SoloBossPerk): Int = bonusBpsForLevel(level(player, perk))
}
