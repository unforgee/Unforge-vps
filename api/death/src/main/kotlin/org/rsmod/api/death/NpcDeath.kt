package org.rsmod.api.death

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.invs
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varns
import org.rsmod.api.config.refs.varps
import org.rsmod.api.equipment.instance.AbilityProc
import org.rsmod.api.equipment.instance.EquipmentAbilityEffects
import org.rsmod.api.equipment.instance.EquipmentAbilityProcs
import org.rsmod.api.equipment.instance.EquipmentInstanceService
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.vars.typePlayerUidVarn
import org.rsmod.api.player.bonus.EquipmentTierResolver
import org.rsmod.api.player.output.AbilityProcFx
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.stat.statBoost
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.vars.typeNpcUidVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.map.CoordGrid

@Singleton
public class NpcDeath
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val seqTypes: SeqTypeList,
    private val players: PlayerList,
    private val objRepo: ObjRepository,
    private val objTypes: ObjTypeList,
    private val dropTables: NpcDropTables,
    private val equipmentInstances: EquipmentInstanceService,
    private val abilityEffects: EquipmentAbilityEffects,
    private val perks: PerkService,
    private val eventBus: EventBus,
) {
    public suspend fun deathNoDrops(access: StandardNpcAccess) {
        access.death(npcRepo, seqTypes, players)
    }

    public suspend fun deathWithDrops(
        access: StandardNpcAccess,
        dropCoords: CoordGrid = access.coords,
    ) {
        access.death(npcRepo, seqTypes, players)
        access.npc.spawnDeathDrops(dropCoords, access.random)
    }

    private fun Npc.spawnDeathDrops(dropCoords: CoordGrid, random: GameRandom) {
        val hero = findHero(players)
        if (hero != null) {
            // Boss kills always award Perk Points, scaled by npc combat level + Fortune perk.
            if (perks.isBoss(type.vislevel)) {
                val awarded = perks.awardBossKill(hero, type.vislevel)
                if (awarded > 0) {
                    hero.mes("<col=ffb84d>You earn $awarded perk point(s).</col>")
                }
            } else if (random.of(10_000) < perks.pointOnKillChanceBps(hero)) {
                // Scavenger: non-boss kills have a small chance to award a perk point.
                perks.addPoints(hero, 1)
                hero.mes("<col=ffb84d>You earn 1 perk point.</col>")
            }
            // Harvest/Rejuvenation perks restore prayer/hitpoints on every npc kill.
            if (perks.onKillPrayer(hero) > 0) {
                hero.statBoost(stats.prayer, constant = perks.onKillPrayer(hero), percent = 0)
            }
            if (perks.onKillHeal(hero) > 0) {
                hero.statBoost(stats.hitpoints, constant = perks.onKillHeal(hero), percent = 0)
            }
            // On-kill effects roll their own per-ability proc chance (rarity/item-level scaled).
            var rolledProc = AbilityProc()
            val procced = ArrayList<String>()
            for (resolved in abilityEffects.procs(hero.worn)) {
                if (random.of(10_000) < resolved.chanceBps) {
                    rolledProc += resolved.proc
                    procced += resolved.abilityId
                }
            }
            AbilityProcFx.announce(hero, procced)
            val proc = EquipmentAbilityProcs.cap(rolledProc)
            if (proc.onKillHeal > 0) {
                hero.statBoost(stats.hitpoints, constant = proc.onKillHeal, percent = 0)
            }
            if (proc.onKillPrayer > 0) {
                hero.statBoost(stats.prayer, constant = proc.onKillPrayer, percent = 0)
            }
            val duration = hero.lootDropDuration ?: constants.lootdrop_duration
            var rolledDrops = dropTables.roll(type.id)
            // Greed perk: independent chance of one additional drop-table roll.
            if (random.of(10_000) < perks.extraDropChanceBps(hero)) {
                dropTables.roll(type.id)?.let { extra ->
                    rolledDrops = (rolledDrops ?: emptyList()) + extra
                }
            }
            // Difficulty tier: independent chance of one additional drop-table roll.
            val dropBoostBps = hero.difficulty?.dropBoostBps ?: 0
            if (dropBoostBps > 0 && random.of(10_000) < dropBoostBps) {
                dropTables.roll(type.id)?.let { extra ->
                    rolledDrops = (rolledDrops ?: emptyList()) + extra
                }
            }
            if (rolledDrops != null && proc.extraDropRolls > 0) {
                val merged = rolledDrops.toMutableList()
                repeat(proc.extraDropRolls) { dropTables.roll(type.id)?.let(merged::addAll) }
                rolledDrops = merged
            }
            // Global metal-tier progression loot. The NPC family determines the equipment tier;
            // this is additive and never replaces or alters the NPC's existing drop table.
            val bonusDrops = (rolledDrops ?: emptyList()).toMutableList()
            if (random.of(10_000) < 250) {
                val npcName = type.name.lowercase()
                val tierArmour =
                    when {
                        "black dragon" in npcName -> listOf(1149, 3140, 4087, 1187) // dragon
                        "fire giant" in npcName -> listOf(1161, 1123, 1073, 1199) // adamant
                        "mossy giant" in npcName || "moss giant" in npcName ->
                            listOf(1159, 1121, 1071, 1197) // mithril
                        "hill giant" in npcName -> listOf(1165, 1125, 1077, 1195) // black
                        "cow" in npcName || "calf" in npcName ->
                            listOf(1155, 1117, 1075, 1191) // bronze
                        // Fallback for other NPCs: use combat level bands as a sensible tier.
                        type.vislevel >= 150 -> listOf(1149, 3140, 4087, 1187) // dragon
                        type.vislevel >= 80 -> listOf(1161, 1123, 1073, 1199) // adamant
                        type.vislevel >= 40 -> listOf(1159, 1121, 1071, 1197) // mithril
                        type.vislevel >= 20 -> listOf(1165, 1125, 1077, 1195) // black
                        else -> listOf(1155, 1117, 1075, 1191) // bronze
                    }
                // One random piece from the complete tier set: helm, body, legs or shield.
                bonusDrops += tierArmour[random.of(tierArmour.size)] to 1
            }
            // Small global bonus rolls; these are additive and never replace normal loot.
            if (random.of(10_000) < 35) bonusDrops += 989 to 1 // crystal key
            if (random.of(10_000) < 15) bonusDrops += 6199 to 1 // mystery box
            rolledDrops = bonusDrops
            grantDrops(this, hero, dropCoords, rolledDrops, duration, "npc-drop")
            // Every NPC pays Forge ticks. The reward follows combat level and is capped so
            // ordinary mobs remain useful while bosses cannot flood the economy.
            objTypes[FORGE_TICK_ID]?.let { forgeTick ->
                val amount = type.vislevel.coerceIn(1, FORGE_TICK_MAX_LEVEL) * FORGE_TICK_PER_LEVEL
                val count = amount.coerceIn(FORGE_TICK_MIN, FORGE_TICK_MAX)
                if (hero.vars[varps.generic_temp_state_65516] == 1) {
                    hero.invAdd(hero.invMap.getValue(invs.bank), forgeTick, count, strict = false)
                } else {
                    objRepo.add(forgeTick, dropCoords, duration, hero, count)
                }
            }
            eventBus.publish(NpcKilledEvent(npc = this, killer = hero))
        }
    }

    // Note: We may be able to have `Npc` as the arg instead of `StandardNpcAccess`, however we
    // will need to wait and see how [spawnDeathDrops] ends up once it handles everything it needs
    // to.
    public fun spawnDrops(access: StandardNpcAccess, dropCoords: CoordGrid = access.coords) {
        access.npc.spawnDeathDrops(dropCoords, access.random)
    }

    /** Grants one additive server-rolled table roll for Lucky/Last Stand/modified kills. */
    public fun spawnBonusDropRoll(
        npc: Npc,
        hero: Player,
        dropCoords: CoordGrid = npc.coords,
        source: String = "npc-bonus-drop",
    ): Boolean {
        val rolled = dropTables.roll(npc.type.id) ?: return false
        val duration = hero.lootDropDuration ?: constants.lootdrop_duration
        grantDrops(npc, hero, dropCoords, rolled, duration, source)
        return rolled.isNotEmpty()
    }

    private fun grantDrops(
        npc: Npc,
        hero: Player,
        dropCoords: CoordGrid,
        drops: List<Pair<Int, Int>>?,
        duration: Int,
        source: String,
    ) {
        if (drops.isNullOrEmpty()) {
            objRepo.add(objs.bones, dropCoords, duration, hero)
            return
        }
        for ((objId, count) in drops) {
            val type = objTypes[objId] ?: continue
            if (count == 1 && equipmentInstances.isInstanceEligible(type)) {
                equipmentInstances.rollAndPersist(
                    type,
                    source = "$source:${npc.type.id}",
                    tier = EquipmentTierResolver.resolve(type),
                    itemLevel = npc.type.vislevel.coerceAtLeast(1),
                    ownerCharacterId = hero.characterId.toLong(),
                    onFailure = { objRepo.add(type, dropCoords, duration, hero, count) },
                ) { instance ->
                    objRepo.add(
                        InvObj(type, count = 1, instanceId = instance.instanceId),
                        dropCoords,
                        duration,
                        hero,
                    )
                }
            } else {
                objRepo.add(type, dropCoords, duration, hero, count)
            }
        }
    }

    private companion object {
        const val FORGE_TICK_ID = 6306
        const val FORGE_TICK_PER_LEVEL = 10
        const val FORGE_TICK_MIN = 10
        const val FORGE_TICK_MAX = 1000
        const val FORGE_TICK_MAX_LEVEL = FORGE_TICK_MAX / FORGE_TICK_PER_LEVEL
    }
}

private var Player.lastCombat: Int by intVarp(varps.lastcombat)
private var Player.aggressiveNpc: NpcUid? by typeNpcUidVarp(varps.aggressive_npc)
private var Npc.aggressivePlayer by typePlayerUidVarn(varns.aggressive_player)

/**
 * Handles the death sequence of this [StandardNpcAccess.npc], including clearing interactions and
 * removing (or hiding, if it respawns) the npc from the world.
 *
 * **Notes:**
 * - This is **not** the way to "kill" a npc. This "death sequence" occurs after the npc has already
 *   been deemed dead and its death queue is being processed.
 * - To queue a npc's death, use [StandardNpcAccess.queueDeath] or [org.rsmod.api.npc.queueDeath]
 *   instead.
 * - This function **does not** spawn any drop table objs for the npc.
 * - Drop table spawns are handled via [NpcDeath.deathWithDrops], which is **automatically called**
 *   for queued deaths by default. However, if you override death queues for specific npc types
 *   (`onNpcQueue(npc_type, queues.death)`), you must explicitly handle drop spawns in the script by
 *   injecting `NpcDeath` and calling either [NpcDeath.deathWithDrops] or [NpcDeath.spawnDrops].
 */
public suspend fun StandardNpcAccess.death(
    npcRepo: NpcRepository,
    seqTypes: SeqTypeList,
    players: PlayerList,
) {
    walk(coords)
    noneMode()
    hideAllOps()
    arriveDelay()

    val aggressivePlayer = npc.aggressivePlayer
    if (aggressivePlayer != null) {
        val player = aggressivePlayer.resolve(players)

        val deathSound = paramOrNull(params.death_sound)
        if (deathSound != null && player != null) {
            player.soundSynth(deathSound)
        }

        // TODO(combat): Should we assert that npc.uid will always match player.aggressiveNpc at
        // this point?

        if (player != null && player.aggressiveNpc == npc.uid) {
            player.lastCombat = 0
        }
    }

    val deathAnim = param(params.death_anim)
    anim(deathAnim)
    delay(seqTypes[deathAnim])

    if (npc.respawns) {
        npcRepo.despawn(npc, npc.type.respawnRate)
        return
    }

    npcRepo.del(npc, Int.MAX_VALUE)
}
