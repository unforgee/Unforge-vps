package org.rsmod.content.areas.tutorialisland.progression

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.areas.tutorialisland.configs.tutorial_objs
import org.rsmod.map.CoordGrid

@Singleton
class TutorialLeave @Inject constructor(private val progression: TutorialProgression) {
    fun giveLeaveKit(access: ProtectedAccess) {
        val player = access.player

        // Wipe inventory; worn items are replaced by the leave kit gear in inv for equipping later.
        access.invClear(access.inv)

        access.invAdd(access.inv, tutorial_objs.bronze_axe, 1)
        access.invAdd(access.inv, tutorial_objs.bronze_pickaxe, 1)
        access.invAdd(access.inv, tutorial_objs.tinderbox, 1)
        access.invAdd(access.inv, tutorial_objs.net, 1)
        access.invAdd(access.inv, tutorial_objs.shrimp, 1)
        access.invAdd(access.inv, tutorial_objs.bronze_dagger, 1)
        access.invAdd(access.inv, tutorial_objs.bronze_sword, 1)
        access.invAdd(access.inv, tutorial_objs.wooden_shield, 1)
        access.invAdd(access.inv, tutorial_objs.shortbow, 1)
        access.invAdd(access.inv, tutorial_objs.bronze_arrow, 25)
        access.invAdd(access.inv, tutorial_objs.air_rune, 25)
        access.invAdd(access.inv, tutorial_objs.mind_rune, 15)
        access.invAdd(access.inv, tutorial_objs.water_rune, 6)
        access.invAdd(access.inv, tutorial_objs.earth_rune, 4)
        access.invAdd(access.inv, tutorial_objs.body_rune, 2)
        access.invAdd(access.inv, tutorial_objs.bucket_empty, 1)
        access.invAdd(access.inv, tutorial_objs.pot_empty, 1)
        access.invAdd(access.inv, tutorial_objs.bread, 1)

        ensureBankCoins(access)

        access.teleport(LUMBRIDGE_SPAWN)
        progression.finishAndLeave(player)
    }

    private fun ensureBankCoins(access: ProtectedAccess) {
        val current = access.invTotal(access.bank, tutorial_objs.coins)
        if (current < 25) {
            access.invAdd(access.bank, tutorial_objs.coins, 25 - current)
        }
    }

    companion object {
        val LUMBRIDGE_SPAWN: CoordGrid = CoordGrid(0, 50, 50, 21, 18)
    }
}
