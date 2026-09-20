package org.rsmod.content.other.league

import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.content.other.league.melee.LeagueWeaponSpecialAttacks
import org.rsmod.content.other.league.tools.LeagueToolSpecialAttacks
import org.rsmod.content.other.league.weapons.LeagueWeapons
import org.rsmod.plugin.module.PluginModule

class LeagueItemsModule : PluginModule() {
    override fun bind() {
        addSetBinding<SpecialAttackMap>(LeagueToolSpecialAttacks::class.java)
        addSetBinding<SpecialAttackMap>(LeagueWeaponSpecialAttacks::class.java)
        addSetBinding<WeaponMap>(LeagueWeapons::class.java)
    }
}
