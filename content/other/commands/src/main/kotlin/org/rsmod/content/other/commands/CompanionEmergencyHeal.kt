package org.rsmod.content.other.commands

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.companion.Companion
import org.rsmod.api.companion.CompanionClass
import org.rsmod.api.companion.CompanionService
import org.rsmod.api.config.refs.BaseInvs
import org.rsmod.api.config.refs.spotanims
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.synths
import org.rsmod.api.npc.threat.ThreatService
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statAdd
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjTypeList

/**
 * Tactical Emergency Healing & Potion Feeding system for Unforge Companions.
 *
 * Actively monitors the owner's vital signs during combat. When the player's hitpoints fall to
 * critical levels (<= 35%), the companion immediately acts:
 * 1. Checks its Beast-of-Burden pack for edible food (Sharks, Karambwans, etc.) or potions.
 * 2. If food/potion is found: consumes 1 from pack, restores player HP (+10 to +22 HP), plays heal
 *    sound & spotanim, and shouts overhead to encourage the player!
 * 3. If no consumables are stored: channels an intrinsic class triage heal (Support: Mending Light
 *    for +22 HP; Tank/DPS: Field Triage for +14 HP).
 *
 * Governed by a 30-cycle (18-second) tactical cooldown so it serves as a clutch lifesaver.
 */
@Singleton
public class CompanionEmergencyHeal
@Inject
constructor(
    private val companions: CompanionService,
    private val personality: CompanionPersonality,
    private val objTypes: ObjTypeList,
    private val mapClock: MapClock,
    private val threat: ThreatService,
) {
    /** Character ID -> Emergency heal disabled flag (defaults to true / enabled). */
    private val healDisabled = ConcurrentHashMap.newKeySet<Long>()

    /** Companion ID -> Game cycle of last emergency heal. */
    private val lastHealCycle = ConcurrentHashMap<Long, Long>()

    public fun isEnabled(characterId: Long): Boolean = !healDisabled.contains(characterId)

    public fun toggle(player: Player): Boolean {
        val cid = player.characterId.toLong()
        return setEnabled(player, !healDisabled.contains(cid))
    }

    /** Explicit set used by the companion panel - a checkbox cannot blind-toggle. */
    public fun setEnabled(player: Player, enabled: Boolean): Boolean {
        val cid = player.characterId.toLong()
        if (enabled) {
            healDisabled.remove(cid)
        } else {
            healDisabled.add(cid)
        }
        val statusText = if (enabled) "<col=006600>ENABLED</col>" else "<col=ff0000>DISABLED</col>"
        player.mes("Companion Emergency Healing is now $statusText.")
        return enabled
    }

    public fun checkEmergencyHeal(player: Player, active: Companion, bot: Player) {
        val ownerId = runCatching { player.characterId.toLong() }.getOrNull() ?: return
        if (!isEnabled(ownerId)) return

        val maxHp = player.baseHitpointsLvl
        val curHp = player.hitpoints
        if (curHp <= 0 || curHp > (maxHp * 0.35).toInt()) {
            return
        }

        val currentCycle = mapClock.cycle.toLong()
        val lastCycle = lastHealCycle[active.id] ?: 0L
        if (currentCycle - lastCycle < HEAL_COOLDOWN_CYCLES) {
            return
        }

        val storage = player.invMap.getValue(BaseInvs.companion_storage)
        var foodSlot = -1
        var foodObj: InvObj? = null

        // Search pack for food ("Eat") or potion ("Drink")
        for (slot in 0 until storage.size) {
            val item = storage[slot] ?: continue
            val type = objTypes[item.id] ?: continue
            val isConsumable =
                type.iop.any { op ->
                    op != null &&
                        (op.equals("eat", ignoreCase = true) ||
                            op.equals("drink", ignoreCase = true))
                }
            if (isConsumable) {
                foodSlot = slot
                foodObj = item
                break
            }
        }

        if (foodSlot != -1 && foodObj != null) {
            val type = objTypes[foodObj.id] ?: return
            val healAmount = calculateFoodHeal(type.name)

            // Deduct 1 count from companion storage
            if (foodObj.count > 1) {
                storage[foodSlot] = InvObj(type, foodObj.count - 1, instanceId = foodObj.instanceId)
            } else {
                storage[foodSlot] = null
            }

            val newHp = (curHp + healAmount).coerceAtMost(maxHp)
            val healDiff = newHp - curHp
            if (healDiff > 0) {
                player.statAdd(stats.hitpoints, healDiff, 0)
                threat.onHeal(bot, listOf(player), healDiff)
            }
            player.spotanim(spotanims.skillcapes_hitpoints)
            player.soundSynth(synths.heal)

            personality.sayEmergencyHealFood(bot, player, active, type.name)
            player.mes(
                "<col=00b33c>[${active.name}]: Fed you a ${type.name} (+${healAmount} HP)! Health: $newHp/$maxHp</col>"
            )
            lastHealCycle[active.id] = currentCycle
        } else {
            // No food in pack: Channel class triage heal
            val triageHeal =
                when (active.companionClass) {
                    CompanionClass.SUPPORT -> 22 + (active.level / 4)
                    CompanionClass.TANK -> 14 + (active.level / 5)
                    CompanionClass.DPS -> 14 + (active.level / 5)
                }

            val newHp = (curHp + triageHeal).coerceAtMost(maxHp)
            val healDiff = newHp - curHp
            if (healDiff > 0) {
                player.statAdd(stats.hitpoints, healDiff, 0)
                threat.onHeal(bot, listOf(player), healDiff)
            }
            player.spotanim(spotanims.skillcapes_hitpoints)
            player.soundSynth(synths.heal)

            personality.sayEmergencyHealTriage(bot, player, active)
            player.mes(
                "<col=00b33c>[${active.name}]: Emergency Triage restored +${triageHeal} HP! Health: $newHp/$maxHp</col>"
            )
            lastHealCycle[active.id] = currentCycle
        }
    }

    private fun calculateFoodHeal(itemName: String): Int {
        val lower = itemName.lowercase()
        return when {
            "manta" in lower -> 22
            "shark" in lower -> 20
            "karambwan" in lower -> 18
            "swordfish" in lower -> 14
            "lobster" in lower -> 12
            "tuna" in lower -> 10
            "salmon" in lower -> 9
            "trout" in lower -> 7
            "brew" in lower -> 16
            "potion" in lower -> 12
            else -> 16
        }
    }
}

private const val HEAL_COOLDOWN_CYCLES: Long = 30L // ~18 seconds
