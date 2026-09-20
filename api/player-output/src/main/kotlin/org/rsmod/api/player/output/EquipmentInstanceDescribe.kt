package org.rsmod.api.player.output

import org.rsmod.api.config.refs.params
import org.rsmod.api.equipment.instance.AbilityProc
import org.rsmod.api.equipment.instance.AbilityStyle
import org.rsmod.api.equipment.instance.EquipmentAbilityCatalog
import org.rsmod.api.equipment.instance.EquipmentAbilityProcs
import org.rsmod.api.equipment.instance.EquipmentAffixRoll
import org.rsmod.api.equipment.instance.EquipmentInstance
import org.rsmod.api.equipment.instance.EquipmentRarity
import org.rsmod.api.equipment.instance.EquipmentStat
import org.rsmod.api.equipment.instance.ForgeConfig
import org.rsmod.api.equipment.instance.ModifierPolarity
import org.rsmod.api.equipment.instance.ModifierUnit
import org.rsmod.api.equipment.instance.MysteryEnchantService
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.param.ParamType

/**
 * Renders the authoritative, post-instance statistics of an equipment item as text lines.
 *
 * The line format doubles as the wire contract for the client's `UnforgeItemHoverPlugin`: the first
 * line must start with `Instance #` and every following line that should appear in the Q+hover
 * "INSTANCE DATA" section must start with `Affix`, `Sockets:`, `Unique effects:` or `Source:`.
 *
 * Effective values are computed with the same rules that [org.rsmod.api.player.bonus.WornBonuses]
 * applies per affix: `Flat` magnitudes are added to the item's own base stat and `BasisPoints`
 * magnitudes scale that base.
 */
public object EquipmentInstanceDescribe {
    /**
     * The generic `ForgeInstance` registration line consumed by the client plugin. It registers
     * per-item instance stats (side panel + hover fallback) without needing an armed examine.
     */
    public fun broadcast(objId: Int, instance: EquipmentInstance): String =
        "ForgeInstance item=$objId instance=${instance.instanceId} rarity=${instance.rarity.name} " +
            "tier=${instance.tier.name} ilvl=${instance.itemLevel} quality=${instance.quality} " +
            "upgrade=${instance.upgradeLevel} " +
            "mystery=${instance.mysteryEnchantId?.name ?: "NONE"} " +
            "mysteryTier=${instance.mysteryEnchantTier?.name ?: "NONE"} " +
            "mysteryKills=${instance.mysteryEnchantKillsRemaining}/${instance.mysteryEnchantKillsMax}"

    /** Visible examine lines; every line is also captured by the client hover plugin. */
    public fun describe(type: UnpackedObjType, instance: EquipmentInstance): List<String> {
        val lines = ArrayList<String>(8 + instance.affixes.size)
        lines +=
            "Instance #${instance.instanceId} — ${type.name} " +
                "<col=${rarityColour(instance.rarity)}>[${instance.rarity.name}]</col> " +
                "<col=aaaaaa>tier ${instance.tier.name}, ilvl ${instance.itemLevel}, " +
                "quality ${instance.quality}%</col>"
        lines +=
            "<col=ffb84d>Forge:</col> upgrade +${instance.upgradeLevel}/${ForgeConfig.MAX_UPGRADE_LEVEL} " +
                "<col=aaaaaa>(+${ForgeConfig.UPGRADE_AFFIX_SCALE_BPS / 100}% per level, right-click Forge)</col>"
        val mysteryId = instance.mysteryEnchantId
        val mysteryTier = instance.mysteryEnchantTier
        if (mysteryId != null && mysteryTier != null) {
            lines +=
                "<col=9b59b6>Mystery Enchantment:</col> " +
                    "${MysteryEnchantService.displayName(mysteryId)} " +
                    "<col=aaaaaa>${mysteryTier.name.lowercase()} — " +
                    "${instance.mysteryEnchantKillsRemaining} / ${instance.mysteryEnchantKillsMax} kills remaining</col>"
        }
        lines += statLines(type, instance.affixes)
        val maxHealth =
            instance.affixes
                .filter { it.stat == EquipmentStat.MaximumHealth }
                .sumOf(EquipmentAffixRoll::magnitude)
        val gearSpeedBps = GearSpeedBonus.bps(type)
        val speedBps =
            instance.affixes
                .filter { it.stat == EquipmentStat.AttackSpeedPercent }
                .sumOf(EquipmentAffixRoll::magnitude)
        val speedTicks =
            instance.affixes
                .filter { it.stat == EquipmentStat.AttackSpeedTicks }
                .sumOf(EquipmentAffixRoll::magnitude)
        if (maxHealth != 0 || gearSpeedBps != 0 || speedBps != 0 || speedTicks != 0) {
            val extras = buildList {
                if (maxHealth != 0) add("max HP $maxHealth")
                if (gearSpeedBps != 0) add("gear speed ${"%.1f".format(gearSpeedBps / 100.0)}%")
                if (speedBps != 0) add("attack speed ${"%.1f".format(speedBps / 100.0)}%")
                if (speedTicks != 0) add("attack speed ${signed(speedTicks)} ticks")
            }
            lines += "<col=ff981f>Affix effective extra:</col> " + extras.joinToString(", ")
        }
        for (affix in instance.affixes) {
            val value =
                when (affix.unit) {
                    ModifierUnit.BasisPoints -> "${"%.1f".format(affix.magnitude / 100.0)}%"
                    ModifierUnit.Ticks -> "${signed(affix.magnitude)} ticks"
                    ModifierUnit.ProcBasisPoints ->
                        "${"%.1f".format(affix.magnitude / 100.0)}% proc"
                    ModifierUnit.Flat ->
                        // MagicDamage magnitudes are stored in 0.1% units (x10): a rolled +20
                        // displays as "+20%".
                        if (affix.stat == EquipmentStat.MagicDamage) {
                            "${signed(affix.magnitude / 10)}%"
                        } else {
                            signed(affix.magnitude)
                        }
                }
            val base = baseStat(type, affix.stat)
            val effectiveSuffix =
                if (base != null && affix.unit != ModifierUnit.Ticks) {
                    val applied =
                        if (affix.unit == ModifierUnit.BasisPoints) {
                            base + (base * affix.magnitude / 10_000)
                        } else {
                            base + affix.magnitude
                        }
                    if (affix.stat == EquipmentStat.MagicDamage) {
                        " <col=aaaaaa>(base ${signed(base / 10)}% → ${signed(applied / 10)}%)</col>"
                    } else {
                        " <col=aaaaaa>(base ${signed(base)} → ${signed(applied)})</col>"
                    }
                } else {
                    ""
                }
            lines +=
                "<col=00b4ff>Affix ${affix.family.titleCase()}:</col> ${affix.stat.readable()} " +
                    "<col=${valueColour(affix.polarity)}>$value</col>$effectiveSuffix"
        }
        if (instance.skillAffixes.isNotEmpty()) {
            lines += "<col=4dc3ff>Skilling stats:</col>"
            for (affix in instance.skillAffixes) {
                val value =
                    if (affix.unit == ModifierUnit.BasisPoints)
                        "+${"%.2f".format(affix.magnitude / 100.0)}%"
                    else "+${affix.magnitude}"
                lines +=
                    "<col=4dc3ff>${affix.skill.titleCase()} ${affix.effect.titleCase()}:</col> " +
                        "<col=33cc33>$value</col>"
            }
        }
        val sockets =
            instance.sockets.joinToString(", ") { it.socketedObj?.let { o -> "obj#$o" } ?: it.type }
        lines += "<col=9090ff>Sockets:</col> ${sockets.ifBlank { "none" }}"
        val chanceBps = EquipmentAbilityProcs.chanceBps(instance.rarity, instance.itemLevel)
        val effects =
            instance.uniqueEffectIds.joinToString(", ") { id ->
                val proc = EquipmentAbilityProcs.procFor(id)
                val detail = strikeSummary(proc)
                "${EquipmentAbilityCatalog.displayName(id)}" +
                    " <col=aaaaaa>($detail, ${chanceBps / 100.0}% proc)</col>"
            }
        lines += "<col=ffdc00>Unique effects:</col> ${effects.ifBlank { "none" }}"
        lines += "<col=aaaaaa>Source: ${instance.source}</col>"
        return lines
    }

    /**
     * Stat block for an item that carries no equipment instance: the same attack/defence/misc lines
     * [describe] renders, computed from base params alone (no affixes, no sockets, no abilities).
     */
    public fun describeBase(type: UnpackedObjType): List<String> =
        statLines(type, emptyList()) + "<col=aaaaaa>Base item — no affixes or unique effects.</col>"

    /**
     * The item's total (base + affix-effective) combat stats in a fixed order, reused by the
     * hover-sync wire payload: attack stab/slash/crush/magic/ranged, defence
     * stab/slash/crush/magic/ranged, melee strength, ranged strength, magic damage (0.1% units) and
     * prayer.
     */
    public fun totalStats(type: UnpackedObjType, affixes: List<EquipmentAffixRoll>): List<Int> =
        listOf(
            effective(type, affixes, params.attack_stab, EquipmentStat.AttackStab),
            effective(type, affixes, params.attack_slash, EquipmentStat.AttackSlash),
            effective(type, affixes, params.attack_crush, EquipmentStat.AttackCrush),
            effective(type, affixes, params.attack_magic, EquipmentStat.AttackMagic),
            effective(type, affixes, params.attack_ranged, EquipmentStat.AttackRanged),
            effective(type, affixes, params.defence_stab, EquipmentStat.DefenceStab),
            effective(type, affixes, params.defence_slash, EquipmentStat.DefenceSlash),
            effective(type, affixes, params.defence_crush, EquipmentStat.DefenceCrush),
            effective(type, affixes, params.defence_magic, EquipmentStat.DefenceMagic),
            effective(type, affixes, params.defence_ranged, EquipmentStat.DefenceRanged),
            effective(type, affixes, params.melee_strength, EquipmentStat.Strength),
            effective(type, affixes, params.ranged_strength, EquipmentStat.RangedStrength),
            effective(type, affixes, params.magic_damage, EquipmentStat.MagicDamage),
            effective(type, affixes, params.item_prayer_bonus, EquipmentStat.Prayer),
        )

    /** The shared attack/defence/misc block, effective over the given [affixes]. */
    private fun statLines(type: UnpackedObjType, affixes: List<EquipmentAffixRoll>): List<String> {
        val t = totalStats(type, affixes)
        return listOf(
            "<col=ff981f>Affix effective attack:</col> " +
                "${colored(t[0])} stab, ${colored(t[1])} slash, ${colored(t[2])} crush, " +
                "${colored(t[3])} magic, ${colored(t[4])} ranged",
            "<col=ff981f>Affix effective defence:</col> " +
                "${colored(t[5])} stab, ${colored(t[6])} slash, ${colored(t[7])} crush, " +
                "${colored(t[8])} magic, ${colored(t[9])} ranged",
            "<col=ff981f>Affix effective misc:</col> " +
                "melee STR ${colored(t[10])}, ranged STR ${colored(t[11])}, " +
                "magic DMG ${coloredTenths(t[12])}%, prayer ${colored(t[13])}",
        )
    }

    /** Effective value of [param] on this item after the given [affixes] are applied. */
    private fun effective(
        type: UnpackedObjType,
        affixes: List<EquipmentAffixRoll>,
        param: ParamType<Int>,
        stat: EquipmentStat,
    ): Int {
        var value = type.param(param)
        for (affix in affixes) {
            if (affix.stat != stat) {
                continue
            }
            value =
                when (affix.unit) {
                    ModifierUnit.BasisPoints -> value + (value * affix.magnitude / 10_000)
                    ModifierUnit.Flat -> value + affix.magnitude
                    else -> value
                }
        }
        return value
    }

    /** The cache param a given [EquipmentStat] modifies, or `null` for non-param stats. */
    private fun baseStat(type: UnpackedObjType, stat: EquipmentStat): Int? =
        when (stat) {
            EquipmentStat.AttackStab -> type.param(params.attack_stab)
            EquipmentStat.AttackSlash -> type.param(params.attack_slash)
            EquipmentStat.AttackCrush -> type.param(params.attack_crush)
            EquipmentStat.AttackMagic -> type.param(params.attack_magic)
            EquipmentStat.AttackRanged -> type.param(params.attack_ranged)
            EquipmentStat.DefenceStab -> type.param(params.defence_stab)
            EquipmentStat.DefenceSlash -> type.param(params.defence_slash)
            EquipmentStat.DefenceCrush -> type.param(params.defence_crush)
            EquipmentStat.DefenceMagic -> type.param(params.defence_magic)
            EquipmentStat.DefenceRanged -> type.param(params.defence_ranged)
            EquipmentStat.Strength -> type.param(params.melee_strength)
            EquipmentStat.RangedStrength -> type.param(params.ranged_strength)
            EquipmentStat.MagicDamage -> type.param(params.magic_damage)
            EquipmentStat.Prayer -> type.param(params.item_prayer_bonus)
            else -> null
        }

    private fun rarityColour(rarity: EquipmentRarity): String =
        when (rarity) {
            EquipmentRarity.Uncommon -> "1eff00"
            EquipmentRarity.Rare -> "0070dd"
            EquipmentRarity.Epic -> "a335ee"
            EquipmentRarity.Legendary -> "ff8000"
            EquipmentRarity.Mythic -> "e6cc80"
            EquipmentRarity.Jackpot -> "ff0000"
        }

    private fun strikeSummary(proc: AbilityProc): String =
        when (proc.strikeStyle) {
            AbilityStyle.None -> "passive"
            else ->
                "${proc.strikeStyle.name.lowercase()} hit " +
                    "${proc.strikeMultiplierBps / 100.0}%+${proc.strikeBaseMaxHit}"
        }

    private fun EquipmentStat.readable(): String = name.replace(Regex("([a-z])([A-Z])"), "$1 $2")

    private fun String.titleCase(): String =
        split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

    private fun signed(value: Int): String = if (value < 0) "$value" else "+$value"

    private fun colored(value: Int): String = "<col=${signColour(value)}>${signed(value)}</col>"

    /** Sign-coloured 0.1%-unit value rendered as whole/tenth percents: 200 -> "+20.0". */
    private fun coloredTenths(value: Int): String {
        val sign = if (value < 0) "" else "+"
        return "<col=${signColour(value)}>$sign${"%.1f".format(value / 10.0)}</col>"
    }

    private fun signColour(value: Int): String =
        if (value > 0) "33cc33" else if (value < 0) "ff4444" else "aaaaaa"

    private fun valueColour(polarity: ModifierPolarity): String =
        when (polarity) {
            ModifierPolarity.Boon -> "33cc33"
            ModifierPolarity.Curse -> "ff4444"
            else -> "ffdc00"
        }
}

/**
 * Sends everything the client hover plugin needs for this instanced item: the generic
 * `ForgeInstance` registration (console channel - invisible in chat) followed by the visible
 * `Instance #` + detail lines that the plugin captures per examined slot.
 */
public fun Player.mesInstanceData(type: UnpackedObjType, instance: EquipmentInstance) {
    mes(EquipmentInstanceDescribe.broadcast(type.id, instance), ChatType.Console)
    EquipmentInstanceDescribe.describe(type, instance).forEach(::mes)
}
