package org.rsmod.content.areas.unforge.wilderness

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.entity.Player
import org.rsmod.game.type.varp.VarpType

/**
 * The Wilderness boss KC bridge uses the permanent revision-239 `total_*_kills` varps already
 * present in the cache. Keeping the mapping here gives the manager one kill-count API and avoids a
 * second session-only counter. Sotetseg is intentionally absent: revision 239 has no verified
 * existing total varp for the custom apex registration, so it must not be silently mapped to an
 * unrelated counter.
 */
public object WildernessBossKillCounts : VarpReferences() {
    private val byBoss: Map<RoamingBossType, VarpType> =
        mapOf(
            RoamingBossType.CALLISTO to find("total_callisto_kills"),
            RoamingBossType.VENENATIS to find("total_venenatis_kills"),
            RoamingBossType.VETION to find("total_vetion_kills"),
            RoamingBossType.CHAOS_ELEMENTAL to find("total_chaosele_kills"),
            RoamingBossType.KING_BLACK_DRAGON to find("total_kbd_kills"),
            RoamingBossType.ABYSSAL_SIRE to find("total_abyssalsire_kills"),
            RoamingBossType.CERBERUS to find("total_cerberus_kills"),
            RoamingBossType.ALCHEMICAL_HYDRA to find("total_hydraboss_kills"),
            RoamingBossType.CORPOREAL_BEAST to find("total_corp_kills"),
        )

    public fun varp(type: RoamingBossType): VarpType? = byBoss[type]

    public fun get(player: Player, type: RoamingBossType): Int =
        byBoss[type]?.let { player.vars[it] } ?: 0

    /** Increments only the attributed killer's persistent counter. */
    public fun increment(player: Player, type: RoamingBossType): Int {
        val varp = byBoss[type] ?: return 0
        val next = (player.vars[varp] + 1).coerceAtLeast(0)
        VarPlayerIntMapSetter.set(player, varp, next)
        return next
    }
}
