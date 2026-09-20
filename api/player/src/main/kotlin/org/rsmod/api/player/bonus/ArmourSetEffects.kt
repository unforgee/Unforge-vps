package org.rsmod.api.player.bonus

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.stats
import org.rsmod.api.equipment.instance.AbilityProc
import org.rsmod.api.equipment.instance.AbilityStyle
import org.rsmod.api.equipment.instance.ResolvedProc
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos

/**
 * Armour set bonuses resolved from the equipment actually worn by a [Player].
 *
 * Set membership is detected on the worn item's display name (e.g. `Dharok's *`), which means every
 * degradation stage and ornament variant of a Barrows piece participates automatically and no
 * per-id tables need maintaining.
 *
 * Two kinds of contribution:
 * - [outgoingDamageBps]: unconditional outgoing-damage bonus, merged into the same bps pool as
 *   equipment-instance procs and perks by the hit processors / combat scripts.
 * - [procs]: chance-gated effects expressed as [ResolvedProc]s so they flow through the exact same
 *   pipeline as equipment-instance abilities (rolled per hit in `StandardNpcHitProcessor` and
 *   `PvNCombat.rollAbilityStrikes`).
 */
@Singleton
public class ArmourSetEffects @Inject constructor(private val objTypes: ObjTypeList) {
    /** Total additive outgoing-damage bonus in basis points granted by complete worn sets. */
    public fun outgoingDamageBps(player: Player): Int =
        voidDamageBps(player) +
            dharokDamageBps(player) +
            obsidianDamageBps(player) +
            inquisitorDamageBps(player)

    /** Chance-gated set effects, resolved through the same pipeline as instance abilities. */
    public fun procs(player: Player): List<ResolvedProc> {
        val resolved = ArrayList<ResolvedProc>(5)
        if (fullBarrows(player, "guthan's")) {
            resolved += ResolvedProc("set-guthans", 2_500, AbilityProc(onHitHealBps = 10_000))
        }
        if (fullBarrows(player, "verac's")) {
            resolved +=
                ResolvedProc(
                    "set-veracs",
                    2_500,
                    AbilityProc(strikeStyle = AbilityStyle.Melee, strikeMultiplierBps = 10_000),
                )
        }
        if (fullBarrows(player, "karil's")) {
            resolved +=
                ResolvedProc(
                    "set-karils",
                    2_500,
                    AbilityProc(strikeStyle = AbilityStyle.Ranged, strikeMultiplierBps = 5_000),
                )
        }
        if (fullBarrows(player, "ahrim's")) {
            resolved +=
                ResolvedProc(
                    "set-ahrims",
                    2_500,
                    AbilityProc(
                        strikeStyle = AbilityStyle.Magic,
                        strikeMultiplierBps = 5_000,
                        strikeBaseMaxHit = 4,
                    ),
                )
        }
        if (fullBarrows(player, "torag's")) {
            resolved += ResolvedProc("set-torags", 2_000, AbilityProc(outgoingDamageBps = 1_500))
        }
        return resolved
    }

    /** Dharok's: damage scales with missing hitpoints - up to +80% at near-death. */
    private fun dharokDamageBps(player: Player): Int {
        if (!fullBarrows(player, "dharok's")) {
            return 0
        }
        val maxHp = player.statBase(stats.hitpoints).coerceAtLeast(1)
        val missing = 1.0 - player.stat(stats.hitpoints).toDouble() / maxHp
        return (missing * 8_000).toInt().coerceIn(0, 8_000)
    }

    /**
     * Void set (helm + top + robe + gloves): +10% damage, +12.5% when any piece is Elite. Detection
     * is name-based so trouver/league ornament variants also count.
     */
    private fun voidDamageBps(player: Player): Int {
        val helm = wornName(player, Wearpos.Hat)
        val body = wornName(player, Wearpos.Torso)
        val legs = wornName(player, Wearpos.Legs)
        val hands = wornName(player, Wearpos.Hands)
        val worn =
            helm.contains("void") &&
                body.contains("void") &&
                legs.contains("void") &&
                hands.contains("void")
        if (!worn) {
            return 0
        }
        val elite = listOf(helm, body, legs, hands).any { it.contains("elite") }
        return if (elite) 1_250 else 1_000
    }

    /** Obsidian plate set: +10% damage, +5% more with a Berserker necklace. */
    private fun obsidianDamageBps(player: Player): Int {
        val worn =
            wornName(player, Wearpos.Hat).contains("obsidian") &&
                wornName(player, Wearpos.Torso).contains("obsidian") &&
                wornName(player, Wearpos.Legs).contains("obsidian")
        if (!worn) {
            return 0
        }
        val berserker = wornName(player, Wearpos.Front).contains("berserker necklace")
        return if (berserker) 1_500 else 1_000
    }

    /** Inquisitor's: +0.5% per piece, +2.5% for the full set (crush-side approximation). */
    private fun inquisitorDamageBps(player: Player): Int {
        val pieces =
            listOf(Wearpos.Hat, Wearpos.Torso, Wearpos.Legs).count {
                wornName(player, it).contains("inquisitor")
            }
        return pieces * 50 + if (pieces == 3) 250 else 0
    }

    /** Barrows full set: helm, weapon, body and legs all matching `prefix` (e.g. `Dharok's`). */
    private fun fullBarrows(player: Player, prefix: String): Boolean =
        wornName(player, Wearpos.Hat).startsWith(prefix) &&
            wornName(player, Wearpos.RightHand).startsWith(prefix) &&
            wornName(player, Wearpos.Torso).startsWith(prefix) &&
            wornName(player, Wearpos.Legs).startsWith(prefix)

    private fun wornName(player: Player, slot: Wearpos): String =
        player.worn.get(slot.slot)?.let { objTypes[it]?.name?.lowercase() } ?: ""
}
