package org.rsmod.content.skills.magic.spell.attacks

import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.content.skills.magic.spell.attacks.ancient.AncientSpells
import org.rsmod.content.skills.magic.spell.attacks.arceuus.ArceuusSpells
import org.rsmod.content.skills.magic.spell.attacks.lunar.LunarSpells
import org.rsmod.content.skills.magic.spell.attacks.standard.ElementalSpells
import org.rsmod.content.skills.magic.spell.attacks.standard.StandardUtilitySpells
import org.rsmod.plugin.module.PluginModule

class SpellAttacksModule : PluginModule() {
    override fun bind() {
        addSetBinding<SpellAttackMap>(ElementalSpells::class.java)
        addSetBinding<SpellAttackMap>(StandardUtilitySpells::class.java)
        addSetBinding<SpellAttackMap>(AncientSpells::class.java)
        addSetBinding<SpellAttackMap>(ArceuusSpells::class.java)
        addSetBinding<SpellAttackMap>(LunarSpells::class.java)
    }
}
