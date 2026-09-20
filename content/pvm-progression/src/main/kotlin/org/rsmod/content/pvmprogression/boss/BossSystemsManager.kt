package org.rsmod.content.pvmprogression.boss

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import kotlin.random.Random
import org.rsmod.api.config.refs.stats
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDropTables
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.equipment.instance.EquipmentInstanceMutationService
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.equipment.instance.ItemActor
import org.rsmod.api.equipment.instance.ItemActorType
import org.rsmod.api.equipment.instance.ItemMutationOperation
import org.rsmod.api.equipment.instance.ItemMutationRequest
import org.rsmod.api.equipment.instance.ItemMutationResult
import org.rsmod.api.equipment.instance.MysteryEnchantId
import org.rsmod.api.equipment.instance.MysteryEnchantService
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statBoost
import org.rsmod.content.pvmprogression.config.PvmProgressionConfigSource
import org.rsmod.content.pvmprogression.events.BossModifier
import org.rsmod.content.pvmprogression.events.LastStandKillEvent
import org.rsmod.content.pvmprogression.events.LegendaryEnchantRolledEvent
import org.rsmod.content.pvmprogression.events.LuckyKillEvent
import org.rsmod.content.pvmprogression.events.ModifiedBossKillEvent
import org.rsmod.content.pvmprogression.events.MysteryEnchantRolledEvent
import org.rsmod.content.pvmprogression.events.MythicBossKillEvent
import org.rsmod.content.pvmprogression.record.BossRecordManager
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

/** Central reward/kill seam for Lucky Kill, Last Stand and enchant kill counters. */
@Singleton
class BossSystemsManager
@Inject
constructor(
    private val configSource: PvmProgressionConfigSource,
    private val modifiers: BossModifierManager,
    private val perks: PerkService,
    private val dropTables: NpcDropTables,
    private val npcDeath: NpcDeath,
    private val records: BossRecordManager,
    private val eventBus: EventBus,
    private val players: PlayerList,
    private val registry: EquipmentInstanceRegistry,
    private val mutations: EquipmentInstanceMutationService,
) {
    private val handled = WeakHashMap<Npc, Boolean>()
    private val guardianTriggered = WeakHashMap<Player, Boolean>()
    private val forcedLucky = mutableSetOf<Int>()
    private val logger = InlineLogger()

    fun onKill(event: NpcKilledEvent) {
        if (!event.validCombatKill || handled.put(event.npc, true) != null) return
        val npc = event.npc
        val player = event.killer
        if (!isValidCombatNpc(npc)) return
        consumeMysteryEnchantKills(player, npc)
        if (!perks.isBoss(npc.visType.vislevel)) return

        val config = configSource().bossSystems
        val rewardEligible = dropTables.has(npc.type.id)
        val treasureHunter =
            activeEnchant(player)?.mysteryEnchantId == MysteryEnchantId.TREASURE_HUNTER
        val treasureBonusBps =
            if (treasureHunter) {
                val enchant = activeEnchant(player)
                MysteryEnchantService.scaleBps(
                    commonBps = 500,
                    tier = checkNotNull(enchant?.mysteryEnchantTier),
                )
            } else 0
        val baseLuckyChanceBps = (10_000 / config.luckyKillDenominator).coerceAtMost(10_000)
        val luckyChanceBps =
            (baseLuckyChanceBps + baseLuckyChanceBps * treasureBonusBps / 10_000).coerceAtMost(
                10_000
            )
        val lucky =
            forcedLucky.remove(player.characterId) || Random.nextInt(10_000) < luckyChanceBps
        val maxHp = player.baseHitpointsLvl.coerceAtLeast(1)
        val finishingHp = player.hitpoints.coerceAtLeast(0)
        val finishingPercent = (finishingHp * 10_000 / maxHp).coerceIn(0, 10_000)
        val lastStand = player.hitpoints > 0 && finishingPercent <= config.lastStandHpBps
        val modifier = modifiers.instanceOf(npc)

        if (lucky && rewardEligible) npcDeath.spawnBonusDropRoll(npc, player, source = "lucky-kill")
        if (lastStand && rewardEligible) {
            npcDeath.spawnBonusDropRoll(npc, player, source = "last-stand")
        }
        if (modifier != null && rewardEligible) {
            npcDeath.spawnBonusDropRoll(npc, player, source = "modified-boss")
        }

        when {
            lucky && lastStand -> {
                player.mes("<col=ffd700>INSANE KILL! Lucky Kill + Last Stand!</col>")
            }
            lucky -> {
                player.mes("<col=ffd700>Lucky Kill! Your reward has been improved.</col>")
            }
        }
        if (lastStand) {
            player.mes("<col=ff6b6b>LAST STAND</col>")
            player.mes("You defeated ${npc.visType.name} with only $finishingHp HP remaining!")
        }
        if (lucky) eventBus.publish(LuckyKillEvent(player, npc, npc.visType.name))
        if (lastStand) {
            eventBus.publish(
                LastStandKillEvent(player, npc, npc.visType.name, finishingHp, finishingPercent)
            )
        }
        if (modifier != null) {
            eventBus.publish(
                ModifiedBossKillEvent(
                    player,
                    npc,
                    npc.visType.name,
                    modifier.modifier,
                    modifier.mythic,
                )
            )
            if (modifier.mythic) {
                player.mes("<col=e6cc80>Mythic boss defeated!</col>")
                eventBus.publish(MythicBossKillEvent(player, npc, npc.visType.name))
            }
        }
        records.recordSpecialKill(
            player,
            npc.visType.id,
            npc.visType.name,
            lucky,
            lastStand,
            finishingHp,
            finishingPercent,
        )
        logger.info {
            "Boss systems kill player=${player.username} boss=${npc.visType.name} " +
                "lucky=$lucky lastStand=$lastStand modifier=${modifier?.modifier} mythic=${modifier?.mythic}"
        }
    }

    fun forceLucky(player: Player) {
        forcedLucky += player.characterId
    }

    /**
     * Allows a respawning NPC object to produce one new kill event without re-enabling duplicates.
     */
    fun onNpcLifecycleReset(npc: Npc) {
        handled.remove(npc)
    }

    /** Applies the outgoing part of the active Mystery Enchant before the hit is built. */
    fun onNpcHitModify(event: NpcHitEvents.GlobalModify) {
        val hit = event.hit
        if (!hit.isFromPlayer || hit.damage <= 0 || !perks.isBoss(event.npc.visType.vislevel))
            return
        val player = hit.sourceSlot?.let(players::get) ?: return
        val enchant = activeEnchant(player) ?: return
        val tier = checkNotNull(enchant.mysteryEnchantTier)
        val bonusBps =
            when (enchant.mysteryEnchantId) {
                MysteryEnchantId.GIANTSLAYER -> MysteryEnchantService.scaleBps(700, tier)
                MysteryEnchantId.EXECUTIONER ->
                    if (event.npc.hitpoints * 4 <= event.npc.baseHitpointsLvl) {
                        MysteryEnchantService.scaleBps(800, tier)
                    } else 0
                MysteryEnchantId.BERSERKER ->
                    if (player.hitpoints * 10 <= player.baseHitpointsLvl * 3) {
                        MysteryEnchantService.scaleBps(800, tier)
                    } else 0
                MysteryEnchantId.PRECISION -> MysteryEnchantService.scaleBps(700, tier)
                MysteryEnchantId.ARCANE_FLOW ->
                    if (event.hit.type == org.rsmod.game.hit.HitType.Magic)
                        MysteryEnchantService.scaleBps(500, tier)
                    else 0
                MysteryEnchantId.RAPID ->
                    if (event.hit.type == org.rsmod.game.hit.HitType.Ranged)
                        MysteryEnchantService.scaleBps(500, tier)
                    else 0
                MysteryEnchantId.DOUBLE_STRIKE -> 0
                else -> 0
            }
        if (bonusBps > 0) {
            hit.damage =
                (hit.damage.toLong() * (10_000L + bonusBps) / 10_000L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
        }
    }

    /** Applies the healing part of Bloodthirst after the server accepted the NPC hit. */
    fun onNpcHitImpact(event: NpcHitEvents.GlobalImpact) {
        val hit = event.hit
        if (hit.isFromPlayer && hit.damage > 0) {
            val player = hit.resolvePlayerSource(players)
            if (player != null) {
                val enchant = activeEnchant(player)
                if (enchant?.mysteryEnchantId == MysteryEnchantId.BLOODTHIRST) {
                    val tier = checkNotNull(enchant.mysteryEnchantTier)
                    val healBps = MysteryEnchantService.scaleBps(300, tier)
                    val heal = (hit.damage.toLong() * healBps / 10_000L).toInt()
                    if (heal > 0) player.statBoost(stats.hitpoints, constant = heal, percent = 0)
                }
            }
        }
        val modifier = modifiers.instanceOf(event.npc)
        if (
            modifier?.modifier == BossModifier.VAMPIRIC && event.npc.hitpoints > 0 && hit.damage > 0
        ) {
            val heal =
                (hit.damage.toLong() * configSource().bossSystems.vampiricHealBps / 10_000L).toInt()
            event.npc.hitpoints =
                (event.npc.hitpoints + heal).coerceAtMost(event.npc.baseHitpointsLvl)
        }
        if (hit.isFromPlayer && hit.damage > 0 && event.npc.hitpoints > 0) {
            val player = hit.resolvePlayerSource(players)
            val enchant = player?.let(::activeEnchant)
            if (
                enchant?.mysteryEnchantId == MysteryEnchantId.DOUBLE_STRIKE &&
                    Random.nextInt(10_000) <
                        MysteryEnchantService.scaleBps(
                            commonBps = 300,
                            tier = checkNotNull(enchant.mysteryEnchantTier),
                        )
            ) {
                event.secondaryDamage = (hit.damage * 35 / 100).coerceAtLeast(1)
            }
        }
    }

    /** Applies defensive Mystery Enchants at the final server-side player damage seam. */
    fun onPlayerHitImpact(event: PlayerHitEvents.GlobalImpact) {
        if (!event.hit.isFromNpc || event.damage <= 0) return
        val enchant = activeEnchant(event.player) ?: return
        val tier = checkNotNull(enchant.mysteryEnchantTier)
        val hpThreshold = event.player.baseHitpointsLvl.coerceAtLeast(1) / 5
        if (event.player.hitpoints > hpThreshold) guardianTriggered.remove(event.player)
        val reductionBps =
            when (enchant.mysteryEnchantId) {
                MysteryEnchantId.FORTIFIED -> MysteryEnchantService.scaleBps(600, tier)
                MysteryEnchantId.GUARDIAN ->
                    if (
                        event.player.hitpoints > hpThreshold &&
                            event.player.hitpoints - event.damage <= hpThreshold &&
                            guardianTriggered.put(event.player, true) == null
                    ) {
                        MysteryEnchantService.scaleBps(600, tier)
                    } else 0
                else -> 0
            }
        if (reductionBps > 0) {
            event.damage = (event.damage.toLong() * (10_000 - reductionBps) / 10_000).toInt()
        }
    }

    fun lastStandDebug(player: Player): String {
        val maxHp = player.baseHitpointsLvl.coerceAtLeast(1)
        val percent = player.hitpoints.coerceAtLeast(0) * 10_000 / maxHp
        val threshold = configSource().bossSystems.lastStandHpBps
        return "Last Stand: hp=${player.hitpoints}/$maxHp (${percent / 100.0}%) " +
            "threshold=${threshold / 100.0}% eligible=${player.hitpoints > 0 && percent <= threshold}"
    }

    private fun consumeMysteryEnchantKills(player: Player, npc: Npc) {
        val seen = mutableSetOf<Long>()
        val killKey = "${player.characterId}:${npc.uid.packed}:${npc.visType.id}"
        val objects =
            player.invMap.values.asSequence().flatMap { it.objs.asSequence() } +
                player.worn.objs.asSequence()
        for (obj in objects) {
            if (obj == null || obj.instanceId <= 0L || !seen.add(obj.instanceId)) continue
            val instance = registry[obj.instanceId] ?: continue
            val updated = MysteryEnchantService.consumeKill(instance) ?: continue
            val request =
                ItemMutationRequest(
                    idempotencyKey = "enchant-kill:$killKey:${instance.instanceId}",
                    expectedRevision = instance.revision,
                    actor = ItemActor(ItemActorType.SYSTEM, null),
                    operation = ItemMutationOperation.MYSTERY_ENCHANTED,
                    source = "combat-kill-counter",
                    payload =
                        "valid-kill=${npc.visType.id};remaining=${updated.mysteryEnchantKillsRemaining}",
                )
            mutations.mutate(instance, request, { updated }) { result ->
                if (
                    result is ItemMutationResult.Success &&
                        updated.mysteryEnchantKillsRemaining == 0
                ) {
                    player.mes("<col=9b59b6>Mystery Enchantment expired.</col>")
                }
            }
        }
    }

    private fun activeEnchant(player: Player): org.rsmod.api.equipment.instance.EquipmentInstance? {
        val held =
            player.invMap.values
                .asSequence()
                .flatMap { it.objs.asSequence() }
                .filterNotNull()
                .mapNotNull { registry[it.instanceId] }
        val worn =
            player.worn.objs.asSequence().filterNotNull().mapNotNull { registry[it.instanceId] }
        return (held + worn).firstOrNull {
            it.mysteryEnchantId != null &&
                it.mysteryEnchantTier != null &&
                it.mysteryEnchantKillsRemaining > 0
        }
    }

    private fun isValidCombatNpc(npc: Npc): Boolean {
        val name = npc.visType.name.lowercase()
        return listOf("dummy", "tutorial", "summon", "familiar", "pet").none(name::contains)
    }

    fun publishEnchantRoll(
        player: Player,
        item: org.rsmod.api.equipment.instance.EquipmentInstance,
    ) {
        eventBus.publish(MysteryEnchantRolledEvent(player, item))
        if (item.mysteryEnchantTier?.name == "LEGENDARY") {
            eventBus.publish(LegendaryEnchantRolledEvent(player, item))
        }
    }
}
