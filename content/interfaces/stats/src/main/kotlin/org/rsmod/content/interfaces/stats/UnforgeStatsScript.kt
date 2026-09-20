package org.rsmod.content.interfaces.stats

import jakarta.inject.Inject
import java.util.Locale
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.weapon.WeaponSpeeds
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.output.GearSpeedBonus
import org.rsmod.api.player.perk.Perk
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.script.onIfOpen
import org.rsmod.content.interfaces.stats.configs.stats_components
import org.rsmod.content.interfaces.stats.configs.stats_interfaces
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Fills the `::stats` panel (interface 1003) with every stat the player has, from every source.
 *
 * The panel is four fixed text columns rebuilt on open ([onIfOpen]); each column is one component
 * whose lines are separated by `<br>`. Sources:
 * - base levels/xp from the player's [org.rsmod.game.stat.PlayerStatMap],
 * - combat level and maximum hitpoints,
 * - summed equipment bonuses (worn item params *and* equipment-instance affixes) from
 *   [WornBonuses],
 * - the gear attack-speed bonus and the resulting attack rate from [WeaponSpeeds],
 * - perk points and levels from [PerkService],
 * - each worn item's instance data from [EquipmentInstanceRegistry].
 */
class UnforgeStatsScript
@Inject
constructor(
    private val statTypes: StatTypeList,
    private val objTypes: ObjTypeList,
    private val wornBonuses: WornBonuses,
    private val weaponSpeeds: WeaponSpeeds,
    private val perks: PerkService,
    private val instances: EquipmentInstanceRegistry,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOpen(stats_interfaces.unforge_stats) { player.refresh() }
    }

    private fun Player.refresh() {
        ifSetText(stats_components.skills, skillsText())
        ifSetText(stats_components.bonuses, bonusesText())
        ifSetText(stats_components.gear, gearText())
        ifSetText(stats_components.perks, perksText())
    }

    @OptIn(InternalApi::class)
    private fun Player.skillsText(): String {
        val lines = ArrayList<String>(statTypes.values.size + 1)
        lines += "<col=ff981f>Skills</col>"
        for (stat in statTypes.values) {
            val level = statMap.getBaseLevel(stat)
            val xp = statMap.getXP(stat)
            lines += stat.displayName.padEnd(12) + level.toString().padStart(3) + " " + xp.short()
        }
        return lines.joinAsBr()
    }

    private fun Player.bonusesText(): String {
        val bonuses = wornBonuses.calculate(this)
        val lines = ArrayList<String>(21)
        lines += "<col=ff981f>Character</col>"
        lines += "Combat level".padEnd(13) + PlayerSkillXP.calculateCombatLevel(this).toString()
        lines += "Maximum HP".padEnd(13) + wornBonuses.maximumHitpoints(this).toString()

        lines += "<col=ff981f>Offence</col>"
        lines += "Stab".padEnd(13) + bonuses.offStab.signed()
        lines += "Slash".padEnd(13) + bonuses.offSlash.signed()
        lines += "Crush".padEnd(13) + bonuses.offCrush.signed()
        lines += "Magic".padEnd(13) + bonuses.offMagic.signed()
        lines += "Ranged".padEnd(13) + bonuses.offRange.signed()

        lines += "<col=ff981f>Defence</col>"
        lines += "Stab".padEnd(13) + bonuses.defStab.signed()
        lines += "Slash".padEnd(13) + bonuses.defSlash.signed()
        lines += "Crush".padEnd(13) + bonuses.defCrush.signed()
        lines += "Magic".padEnd(13) + bonuses.defMagic.signed()
        lines += "Ranged".padEnd(13) + bonuses.defRange.signed()

        lines += "<col=ff981f>Strength</col>"
        lines += "Melee".padEnd(13) + bonuses.meleeStr.signed()
        lines += "Ranged".padEnd(13) + bonuses.rangedStr.signed()
        lines += "Magic DMG".padEnd(13) + bonuses.multipliedMagicDmg.tenths()
        lines += "Prayer".padEnd(13) + bonuses.prayer.signed()
        return lines.joinAsBr()
    }

    private fun Player.gearText(): String {
        val lines = ArrayList<String>(16)
        lines += "<col=ff981f>Speed</col>"
        lines += "Gear bonus".padEnd(11) + gearSpeedPercent()
        lines += "Base rate".padEnd(11) + weaponSpeeds.baseCenti(this).seconds()
        lines += "Attack rate".padEnd(11) + weaponSpeeds.actualCenti(this).seconds()

        lines += "<col=ff981f>Worn</col>"
        for (wearpos in Wearpos.entries) {
            val obj = worn[wearpos.slot] ?: continue
            val type = objTypes.getOrNull(obj) ?: continue
            val speed = GearSpeedBonus.bps(type).percent()
            val instance = instances[obj.instanceId]
            val tag =
                if (instance == null) {
                    ""
                } else {
                    " <col=aaaaaa>[${instance.rarity.name} ${instance.tier.name} " +
                        "${instance.itemLevel}]</col>"
                }
            lines += "<col=ffffff>${type.name}</col>$tag <col=30d830>$speed</col>"
        }
        return lines.joinAsBr()
    }

    private fun Player.perksText(): String {
        val lines = ArrayList<String>(Perk.entries.size / 2 + 2)
        lines += "<col=ff981f>Perks (points: ${perks.points(this)})</col>"
        val entries = Perk.entries.map { "${it.displayName} ${perks.level(this, it)}" }
        var index = 0
        while (index < entries.size) {
            lines += entries[index].padEnd(13) + entries.getOrElse(index + 1) { "" }
            index += 2
        }
        return lines.joinAsBr()
    }

    /** Total gear attack-speed bonus across every worn item, e.g. `+35.0%`. */
    private fun Player.gearSpeedPercent(): String =
        wornBonuses.attackSpeedModifierBps(this).percent()

    private fun List<String>.joinAsBr(): String = joinToString("<br>")

    private fun Int.signed(): String = if (this < 0) toString() else "+$this"

    private fun Int.tenths(): String = String.format(Locale.ROOT, "%+.1f%%", this / 10.0)

    private fun Int.percent(): String = String.format(Locale.ROOT, "%+.1f%%", this / 100.0)

    /** Attack rate from centi-ticks, e.g. `238 -> "2.38s"`. */
    private fun Int.seconds(): String = String.format(Locale.ROOT, "%.2fs", this * 0.006)

    /** Abbreviated xp for narrow columns: `13034431 -> "13.0M"`. */
    private fun Int.short(): String =
        when {
            this >= 1_000_000_000 -> String.format(Locale.ROOT, "%.1fB", this / 1_000_000_000.0)
            this >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", this / 1_000_000.0)
            this >= 1_000 -> String.format(Locale.ROOT, "%.1fK", this / 1_000.0)
            else -> toString()
        }
}
