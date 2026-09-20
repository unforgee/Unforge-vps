package org.rsmod.content.other.special.weapons

import org.rsmod.api.weapons.WeaponMap
import org.rsmod.content.other.special.weapons.magic.PoweredStaves
import org.rsmod.content.other.special.weapons.magic.SanguinestiWeapons
import org.rsmod.content.other.special.weapons.magic.TumekensShadowWeapons
import org.rsmod.plugin.module.PluginModule

class MagicWeaponsModule : PluginModule() {
    override fun bind() {
        addSetBinding<WeaponMap>(TumekensShadowWeapons::class.java)
        addSetBinding<WeaponMap>(SanguinestiWeapons::class.java)
        addSetBinding<WeaponMap>(PoweredStaves::class.java)
    }
}
