package org.rsmod.content.interfaces.iteminspect

import jakarta.inject.Inject
import java.util.Base64
import org.rsmod.api.equipment.instance.AbilityProc
import org.rsmod.api.equipment.instance.AbilityStyle
import org.rsmod.api.equipment.instance.EquipmentAbilityCatalog
import org.rsmod.api.equipment.instance.EquipmentAbilityProcs
import org.rsmod.api.equipment.instance.EquipmentInstance
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.player.events.interact.ObjExamineEvents
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.EquipmentInstanceDescribe
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.mesInstanceData
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Item-instance inspect via the RuneLite side-panel plugin.
 *
 * Subscribes to [ObjExamineEvents.Examine] - published by the held- and worn-inventory examine
 * paths - and streams the full inspection to the client over `UNFORGE_INSPECT_*` console messages
 * whenever the examined item is equipment (equipable, or carrying an equipment instance).
 * Non-equipment items keep the plain chat examine.
 *
 * The old in-game `unforge_iteminspect` interface is no longer opened from examine - the RuneLite
 * `Item Instance Inspect` plugin owns the presentation so the panel never covers the player's own
 * inventory/equipment windows.
 *
 * Body lines are the authoritative [EquipmentInstanceDescribe] output: effective stats after
 * affixes, per-affix rolls, sockets and unique effects, computed with the same rules as
 * `WornBonuses`. Each unique effect additionally gets an exact mechanic explanation resolved from
 * [EquipmentAbilityProcs.procFor] - the same numbers combat rolls against.
 */
class ItemInspectScript @Inject constructor(private val instances: EquipmentInstanceRegistry) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<ObjExamineEvents.Examine> { player.inspect(obj, type) }
    }

    private fun Player.inspect(obj: InvObj, type: UnpackedObjType) {
        val instance = instances[obj.instanceId]
        // Weapons and armour only: equipable items in a real combat wear slot always open the
        // panel, even when they carry no instance - the panel then shows base stats. Rings,
        // ammunition, client-only cosmetic slots and non-equipment items keep chat-only examine.
        if (instance == null && !type.isCombatEquipment) {
            return
        }
        if (!type.isCombatEquipment) {
            // Instanced accessories (rings, quiver) are out of the panel's scope - keep their
            // instance stats in game chat as the fallback surface.
            if (instance != null) {
                mesInstanceData(type, instance)
            }
            return
        }
        sendInspect(type, instance)
    }

    /**
     * Streams the inspect payload as `UNFORGE_INSPECT_BEGIN` / `UNFORGE_INSPECT_LINE` /
     * `UNFORGE_INSPECT_END` console messages. Free-text fields are base64-url encoded so `|` and
     * colour tags in names or descriptions can never corrupt the `|`-delimited envelope.
     */
    private fun Player.sendInspect(type: UnpackedObjType, instance: EquipmentInstance?) {
        mes(
            "$BEGIN|${type.id}|${instance?.instanceId ?: 0L}|${enc(type.name)}|" +
                enc(subtitle(instance)),
            ChatType.Console,
        )
        val lines =
            if (instance != null) {
                EquipmentInstanceDescribe.describe(type, instance) + abilityLines(instance)
            } else {
                EquipmentInstanceDescribe.describeBase(type)
            }
        for (line in lines) {
            sendInspectLine(line)
        }
        mes(END, ChatType.Console)
    }

    private fun subtitle(instance: EquipmentInstance?): String =
        if (instance == null) {
            "Base item"
        } else {
            "Instance #${instance.instanceId} - ${instance.rarity.name} " +
                "tier ${instance.tier.name}, ilvl ${instance.itemLevel}"
        }

    /**
     * Exact mechanic explanations for every unique effect on the instance: display name, proc
     * chance, and each concrete [AbilityProc] field in plain terms. Numbers come straight from
     * [EquipmentAbilityProcs] so the text can never drift from what combat actually rolls.
     */
    private fun abilityLines(instance: EquipmentInstance): List<String> {
        if (instance.uniqueEffectIds.isEmpty()) {
            return emptyList()
        }
        val chance = EquipmentAbilityProcs.chanceBps(instance.rarity, instance.itemLevel)
        return buildList {
            add("")
            add("<col=ff981f>Ability details:</col>")
            for (id in instance.uniqueEffectIds) {
                val ability = EquipmentAbilityCatalog.all.firstOrNull { it.id == id }
                val origin = ability?.origin?.let { ", $it" } ?: ""
                add(
                    "<col=ffdc00>${EquipmentAbilityCatalog.displayName(id)}</col> " +
                        "<col=9a8b76>(${"%.1f".format(chance / 100.0)}% chance$origin)</col>"
                )
                explainProc(EquipmentAbilityProcs.procFor(id)).forEach(::add)
            }
        }
    }

    private fun explainProc(proc: AbilityProc): List<String> = buildList {
        if (proc.onHitHealBps > 0) {
            add("<col=6b6154>  On hit: heal ${pct(proc.onHitHealBps)}% of damage dealt.</col>")
        }
        if (proc.onKillHeal > 0 || proc.onKillPrayer > 0) {
            val parts = buildList {
                if (proc.onKillHeal > 0) add("${proc.onKillHeal} Hitpoints")
                if (proc.onKillPrayer > 0) add("${proc.onKillPrayer} Prayer")
            }
            add("<col=6b6154>  On kill: restore ${parts.joinToString(" and ")}.</col>")
        }
        if (proc.extraDropRolls > 0) {
            add("<col=6b6154>  On kill: ${proc.extraDropRolls} extra drop roll(s).</col>")
        }
        if (proc.strikeStyle != AbilityStyle.None) {
            val style =
                if (proc.strikeStyle == AbilityStyle.Hybrid) {
                    "strongest style"
                } else {
                    proc.strikeStyle.name.lowercase()
                }
            add(
                "<col=6b6154>  Proc: bonus $style strike - " +
                    "${pct(proc.strikeMultiplierBps)}% of max hit +${proc.strikeBaseMaxHit}.</col>"
            )
        }
        if (proc.outgoingDamageBps > 0) {
            add("<col=6b6154>  Passive: +${pct(proc.outgoingDamageBps)}% damage vs NPCs.</col>")
        }
        if (isEmpty()) {
            add("<col=6b6154>  No combat effect.</col>")
        }
    }

    /** Basis points -> human percent string (`1200` -> "12.0"). */
    private fun pct(bps: Int): String = "%.1f".format(bps / 100.0)

    /**
     * True for items that equip into a real combat wear slot (weapon, armour, shield...).
     * Accessories (`Ring`, `Quiver`) and client-only cosmetic slots (`Arms`, `Head`, `Jaw`) are
     * excluded - they are neither weapons nor armour.
     */
    private val UnpackedObjType.isCombatEquipment: Boolean
        get() {
            if (!isEquipable) return false
            val slot = Wearpos[wearpos1] ?: return false
            return !slot.isClientOnly && slot != Wearpos.Ring && slot != Wearpos.Quiver
        }

    /**
     * `MessageGame` payloads are capped at 255 bytes; an oversized line is dropped by the protocol
     * layer and the client would never see it. Lines that fit are sent as `LINE|b64` as before;
     * longer ones stream as ordered `PART|b64-chunk` messages terminated by `PEND`, which the
     * client concatenates and decodes.
     */
    private fun Player.sendInspectLine(line: String) {
        val encoded = enc(line)
        if ("$LINE|$encoded".toByteArray(Charsets.UTF_8).size <= MAX_MESSAGE_BYTES) {
            mes("$LINE|$encoded", ChatType.Console)
            return
        }
        for (chunk in encoded.chunked(PART_CHUNK_CHARS)) {
            mes("$PART|$chunk", ChatType.Console)
        }
        mes(PART_END, ChatType.Console)
    }

    private fun enc(text: String): String =
        Base64.getUrlEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    private companion object {
        const val BEGIN: String = "UNFORGE_INSPECT_BEGIN"
        const val LINE: String = "UNFORGE_INSPECT_LINE"
        const val PART: String = "UNFORGE_INSPECT_PART"
        const val PART_END: String = "UNFORGE_INSPECT_PEND"
        const val END: String = "UNFORGE_INSPECT_END"
        const val MAX_MESSAGE_BYTES: Int = 255
        const val PART_CHUNK_CHARS: Int = 200
    }
}
