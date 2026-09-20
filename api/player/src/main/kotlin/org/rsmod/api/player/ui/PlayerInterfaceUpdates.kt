package org.rsmod.api.player.ui

import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.WeaponCategory

private var Player.combatTabWeaponStyle: Int by intVarBit(varbits.combat_weapon_category)
private var Player.combatLvlWhole: Int by intVarBit(varbits.combatlevel_transmit)
private var Player.combatLvlDecimal: Int by intVarBit(varbits.combatlevel_decimal_transmit)

public object PlayerInterfaceUpdates {
    /**
     * The combat tab header is rendered entirely by the client: `combat_interface:universe` loads
     * clientscript 7592 with `combat_interface:category` and `:level` as arguments, and that script
     * fills both lines from the equipment inv and [varbits.combat_weapon_category]. The server only
     * has to transmit the varbit.
     *
     * Writing the text server-side does not work and must not be reintroduced. Despite the symbol
     * names, `combat_interface:title` is a *layer* (the parent of the two text lines), so setting
     * text on it does nothing, and `combat_interface:category` is the top line - the one showing
     * the weapon name - so writing a category string there clobbers the name until the client
     * redraws.
     */
    public fun updateCombatTab(player: Player, categoryId: Int) {
        player.combatTabWeaponStyle = categoryId
        ClientScripts.pvpIconsComLevelRange(player, player.combatLevel)
        ClientScripts.pvpIconsComLevelRange(player, player.combatLevel)
    }

    public fun updateCombatTab(player: Player, objTypes: ObjTypeList) {
        val righthandType = objTypes.getOrNull(player.righthand)
        val weaponCategory = WeaponCategory.getOrUnarmed(righthandType?.weaponCategory)
        updateCombatTab(player, weaponCategory.id)
    }

    public fun updateCombatLevel(player: Player) {
        player.combatLvlWhole = player.combatLevel
        player.combatLvlDecimal = player.combatLevelDecimal
    }
}
