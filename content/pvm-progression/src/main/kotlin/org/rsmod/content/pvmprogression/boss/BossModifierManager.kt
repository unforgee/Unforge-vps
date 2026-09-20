package org.rsmod.content.pvmprogression.boss

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import kotlin.random.Random
import org.rsmod.api.npc.isAttackable
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.script.onEvent
import org.rsmod.content.pvmprogression.config.PvmProgressionConfigSource
import org.rsmod.content.pvmprogression.events.BossModifier
import org.rsmod.content.pvmprogression.events.MythicBossSpawnEvent
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

data class BossModifierInstance(val modifier: BossModifier, val mythic: Boolean)

/** One spawn-time modifier roll, shared by normal and instanced boss npcs. */
@Singleton
class BossModifierManager
@Inject
constructor(
    private val configSource: PvmProgressionConfigSource,
    private val perks: PerkService,
    private val eventBus: EventBus,
) {
    private val instances = WeakHashMap<Npc, BossModifierInstance>()
    private var forcedNext: BossModifier? = null
    private var forcedMythic = false

    fun onSpawn(npc: Npc) {
        if (instances.containsKey(npc) || !isEligible(npc)) return
        val config = configSource().bossSystems
        val force = forcedNext
        val mythic = forcedMythic || Random.nextInt(config.mythicSpawnDenominator) == 0
        if (!mythic && force == null && Random.nextInt(10_000) >= config.modifierSpawnChanceBps)
            return
        forcedNext = null
        forcedMythic = false
        val modifier = force ?: BossModifier.entries.random()
        val applied = BossModifierInstance(modifier, mythic)
        instances[npc] = applied
        applyStats(npc, applied)
        if (mythic) eventBus.publish(MythicBossSpawnEvent(npc, npc.visType.name))
    }

    fun onRespawn(npc: Npc) {
        instances.remove(npc)
        onSpawn(npc)
    }

    fun onDelete(npc: Npc) {
        instances.remove(npc)
    }

    fun instanceOf(npc: Npc): BossModifierInstance? = instances[npc]

    fun displayName(npc: Npc): String {
        val instance = instances[npc] ?: return npc.visType.name
        val suffix = if (instance.mythic) "MYTHIC" else instance.modifier.name
        return "${npc.visType.name} [$suffix]"
    }

    fun forceNext(modifier: BossModifier?) {
        if (modifier == null) {
            forcedMythic = true
            forcedNext = null
        } else {
            forcedNext = modifier
            forcedMythic = false
        }
    }

    fun isEligible(npc: Npc): Boolean {
        if (!perks.isBoss(npc.visType.vislevel) || !npc.isAttackable()) return false
        val name = npc.visType.name.lowercase()
        return listOf("dummy", "tutorial", "summon", "familiar", "pet").none(name::contains)
    }

    private fun applyStats(npc: Npc, instance: BossModifierInstance) {
        val config = configSource().bossSystems
        fun scale(value: Int, bps: Int): Int = (value * (10_000 + bps) / 10_000).coerceAtLeast(1)
        when (instance.modifier) {
            BossModifier.ENRAGED -> {
                npc.baseAttackLvl = scale(npc.baseAttackLvl, 2_000)
                npc.baseStrengthLvl = scale(npc.baseStrengthLvl, 2_000)
                npc.attackLvl = npc.baseAttackLvl
                npc.strengthLvl = npc.baseStrengthLvl
            }
            BossModifier.ARMORED -> {
                npc.baseDefenceLvl = scale(npc.baseDefenceLvl, 3_500)
                npc.baseHitpointsLvl = scale(npc.baseHitpointsLvl, 2_500)
                npc.defenceLvl = npc.baseDefenceLvl
                npc.hitpoints = npc.baseHitpointsLvl
            }
            BossModifier.VAMPIRIC -> Unit
            BossModifier.QUICK -> npc.defaultMoveSpeed = MoveSpeed.Run
            BossModifier.CURSED -> {
                npc.baseStrengthLvl = scale(npc.baseStrengthLvl, config.cursedStrengthBps)
                npc.strengthLvl = npc.baseStrengthLvl
            }
        }
        if (instance.mythic) {
            npc.baseHitpointsLvl = scale(npc.baseHitpointsLvl, config.mythicHpBps)
            npc.baseAttackLvl = scale(npc.baseAttackLvl, config.mythicDamageBps)
            npc.baseStrengthLvl = scale(npc.baseStrengthLvl, config.mythicDamageBps)
            npc.baseDefenceLvl = scale(npc.baseDefenceLvl, config.mythicDefenceBps)
            npc.attackLvl = npc.baseAttackLvl
            npc.strengthLvl = npc.baseStrengthLvl
            npc.defenceLvl = npc.baseDefenceLvl
            npc.hitpoints = npc.baseHitpointsLvl
        }
    }
}

class BossModifierScript @Inject constructor(private val manager: BossModifierManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcStateEvents.Create> { manager.onSpawn(npc) }
        onEvent<NpcStateEvents.Respawn> { manager.onRespawn(npc) }
        onEvent<NpcStateEvents.Delete> { manager.onDelete(npc) }
    }
}
