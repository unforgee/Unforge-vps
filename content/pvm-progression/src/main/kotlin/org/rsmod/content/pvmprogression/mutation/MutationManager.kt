package org.rsmod.content.pvmprogression.mutation

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.api.npc.isAttackable
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.random.GameRandom
import org.rsmod.content.pvmprogression.bounty.BountyObjectiveMatcher
import org.rsmod.content.pvmprogression.config.PvmProgressionConfigSource
import org.rsmod.content.pvmprogression.events.MutationKillEvent
import org.rsmod.content.pvmprogression.events.MutationKind
import org.rsmod.content.pvmprogression.events.MutationRarity
import org.rsmod.content.pvmprogression.rewards.PvmReward
import org.rsmod.content.pvmprogression.rewards.PvmRewardService
import org.rsmod.content.pvmprogression.store.PvmProgressionCache
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * The monster-mutation engine.
 *
 * On every eligible npc spawn ([maybeMutate]), a 1-in-N roll decides whether the npc becomes
 * mutated; the kind is then drawn from the rarity tiers (Uncommon 70% / Rare 27% / Epic 3%), with
 * Epic granting two modifiers. The mutation is applied in place: stat boosts scale the npc's live
 * levels, and GIANT additionally transmogs the npc to a visually larger form (reusing an existing
 * npc type rather than introducing a new cache entry).
 *
 * Mutation state is stored in a [WeakHashMap] keyed by the npc - never by an id - so two spawns of
 * the same npc type never share state, and a gc'd npc cannot leak it. Kill handling publishes a
 * [MutationKillEvent] and pays out mutation tokens through the [PvmRewardService], with an
 * idempotent reward id so a repeated death callback cannot double-pay.
 */
@Singleton
class MutationManager
@Inject
constructor(
    private val configSource: PvmProgressionConfigSource,
    private val random: GameRandom,
    private val cache: PvmProgressionCache,
    private val rewards: PvmRewardService,
    private val perks: PerkService,
    private val eventBus: EventBus,
) {
    private val mutations = WeakHashMap<Npc, MutationInstance>()
    private val unstableNpcs = mutableSetOf<Npc>()
    private var unstableChainAllowed: Boolean = false

    /** Applies [kinds] of [rarity] to [npc], scaling its stats and registering the instance. */
    fun applyMutation(npc: Npc, kinds: List<MutationKind>, rarity: MutationRarity) {
        val instance = MutationInstance(kinds, rarity)
        mutations[npc] = instance
        for (kind in kinds) {
            applyStatEffects(npc, kind)
            if (kind == MutationKind.UNSTABLE) {
                unstableNpcs += npc
            }
        }
    }

    /** `true` when [npc] currently carries any mutation. */
    fun isMutated(npc: Npc): Boolean = mutations.containsKey(npc)

    /** The mutation instance on [npc], or `null` if unmutated. */
    fun mutationOf(npc: Npc): MutationInstance? = mutations[npc]

    /** The player-facing name, prefixing mutation adjectives to the npc's base name. */
    fun displayName(npc: Npc): String {
        val base = npc.visType.name
        val instance = mutations[npc] ?: return base
        val prefix =
            instance.kinds.joinToString(" ") { kind ->
                kind.name.lowercase().replaceFirstChar { it.uppercase() }
            }
        return "$prefix $base"
    }

    /** Eligibility: attackable, above the minimum combat level, not a boss / pet / summon. */
    fun isEligible(npc: Npc): Boolean {
        val config = configSource().mutation
        val visLevel = npc.visType.vislevel
        if (visLevel < config.minCombatLevel) {
            return false
        }
        if (!npc.isAttackable()) {
            return false
        }
        if (perks.isBoss(visLevel)) {
            return false
        }
        val name = npc.visType.name.lowercase()
        if (name.contains("pet") || name.contains("summon") || name.contains("familiar")) {
            return false
        }
        return true
    }

    /**
     * Called from the kill path. Publishes [MutationKillEvent] and pays mutation tokens through the
     * reward faucet with an idempotent id keyed by the npc identity, so a multi-hit death cannot
     * double-pay. Also clears the unstable registration.
     */
    fun onKill(killer: Player, npc: Npc) {
        val instance = mutations.remove(npc) ?: return
        unstableNpcs.remove(npc)
        val event = MutationKillEvent(killer, npc, instance.kinds, instance.rarity)
        eventBus.publish(event)
        val config = configSource().mutation
        val tokens = config.tokensPerKill
        if (tokens > 0) {
            val data = cache.getOrCreate(killer.characterId)
            val updated = data.copy(mutations = data.mutations.add(instance.kinds.first()))
            cache.put(killer.characterId, updated)
            val reward = PvmReward(mutationTokens = tokens)
            val id = "mutation:${killer.characterId}:${npc.visType.id}:${npc.slotId}"
            rewards.grant(killer, id, reward)
        }
        val notifyConfig = configSource().notify
        if (notifyConfig.mutationAnnounce) {
            killer.mes("<col=9b59b6>Mutation defeated: ${displayName(npc)}.</col>")
        }
    }

    /** Admin helper: forces a mutation of [kind] onto [npc]. */
    fun forceMutate(npc: Npc, kind: MutationKind) {
        if (!isEligible(npc)) {
            return
        }
        applyMutation(npc, listOf(kind), MutationRarity.RARE)
    }

    /** Admin helper: clears all mutation state from [npc]. */
    fun clearMutation(npc: Npc) {
        mutations.remove(npc)
        unstableNpcs.remove(npc)
        npc.resetTransmog()
    }

    /** Test/admin hook: override the anti-chain switch. */
    fun setUnstableChainAllowed(allowed: Boolean) {
        unstableChainAllowed = allowed
    }

    /** `true` when [npc] is flagged Unstable and therefore will detonate on death. */
    fun isUnstable(npc: Npc): Boolean = npc in unstableNpcs

    /** Whether the unstable death-explosion may itself trigger another Unstable npc. */
    fun unstableChainAllowed(): Boolean = unstableChainAllowed

    /**
     * Roll the mutation chance for [npc] on spawn/respawn. Returns `true` if a mutation applied.
     */
    fun maybeMutate(npc: Npc, isRespawn: Boolean): Boolean {
        val config = configSource().mutation
        if (!config.enabled) {
            return false
        }
        if (!isEligible(npc)) {
            return false
        }
        val denominator =
            if (isRespawn) config.respawnChanceDenominator else config.chanceDenominator
        if (denominator <= 0) {
            return false
        }
        if (random.of(denominator) != 0) {
            return false
        }
        val rarity = rollRarity(config.epicDoubleBps)
        val kinds = pickKinds(rarity)
        if (kinds.isEmpty()) {
            return false
        }
        applyMutation(npc, kinds, rarity)
        return true
    }

    /** Vampiric lifesteal fraction (bps) of damage dealt, from config. */
    fun vampiricLifestealBps(npc: Npc): Int {
        val instance = mutations[npc] ?: return 0
        if (MutationKind.VAMPIRIC !in instance.kinds) {
            return 0
        }
        return configSource().mutation.vampiricLifestealBps
    }

    /** Berserk damage bonus (bps) given the npc's current hp fraction. */
    fun berserkDamageBps(npc: Npc): Int {
        val instance = mutations[npc] ?: return 0
        if (MutationKind.BERSERK !in instance.kinds) {
            return 0
        }
        if (npc.baseHitpointsLvl <= 0) {
            return 0
        }
        val hpFractionBps = (npc.hitpoints * 10_000 / npc.baseHitpointsLvl).coerceIn(0, 10_000)
        // Lower hp => more damage: at 100% hp +0 bps, at 0% hp +5000 bps.
        return 5_000 * (10_000 - hpFractionBps) / 10_000
    }

    /** Snapshot for `::mutations`. */
    fun snapshot(): Map<Npc, MutationInstance> = mutations.toMap()

    /** Build [BountyObjectiveMatcher.KillFacts] for the matcher, sourcing mutation info. */
    fun killFacts(npc: Npc): BountyObjectiveMatcher.KillFacts? {
        val instance = mutations[npc] ?: return null
        return BountyObjectiveMatcher.KillFacts(
            npcId = npc.visType.id,
            npcName = displayName(npc),
            visLevel = npc.visType.vislevel,
            isBoss = false,
            damageDealt = 0,
            killTimeTenths = 0,
            noDeath = true,
            style = null,
            isMutated = true,
            mutationKinds = instance.kinds,
            slayerKill = false,
            differentBossesKilledThisSession = 0,
            healed = 0,
            damageTaken = 0,
            challengeCompleted = null,
        )
    }

    private fun rollRarity(epicDoubleBps: Int): MutationRarity {
        val roll = random.of(10_000)
        return when {
            roll < epicDoubleBps -> MutationRarity.EPIC
            roll < epicDoubleBps + 2_700 -> MutationRarity.RARE
            else -> MutationRarity.UNCOMMON
        }
    }

    private fun pickKinds(rarity: MutationRarity): List<MutationKind> {
        val pool = MutationKind.entries
        return when (rarity) {
            MutationRarity.UNCOMMON -> listOf(pool[random.of(pool.size)])
            MutationRarity.RARE -> listOf(pool[random.of(pool.size)])
            MutationRarity.EPIC -> {
                val first = pool[random.of(pool.size)]
                val remaining = pool.filter { it != first }
                val second = remaining[random.of(remaining.size)]
                listOf(first, second)
            }
        }
    }

    private fun applyStatEffects(npc: Npc, kind: MutationKind) {
        when (kind) {
            MutationKind.GIANT -> {
                val newHp = (npc.baseHitpointsLvl * 2).coerceAtLeast(npc.baseHitpointsLvl + 1)
                npc.baseHitpointsLvl = newHp
                npc.hitpoints = newHp
            }
            MutationKind.ANCIENT -> {
                npc.baseDefenceLvl =
                    (npc.baseDefenceLvl * 3 / 2).coerceAtLeast(npc.baseDefenceLvl + 1)
                val newHp = (npc.baseHitpointsLvl * 3 / 2).coerceAtLeast(npc.baseHitpointsLvl + 1)
                npc.baseHitpointsLvl = newHp
                npc.hitpoints = newHp
            }
            MutationKind.CORRUPTED -> {
                npc.baseStrengthLvl =
                    (npc.baseStrengthLvl * 135 / 100).coerceAtLeast(npc.baseStrengthLvl + 1)
            }
            MutationKind.TREASURE -> {
                val newHp =
                    (npc.baseHitpointsLvl * 125 / 100).coerceAtLeast(npc.baseHitpointsLvl + 1)
                npc.baseHitpointsLvl = newHp
                npc.hitpoints = newHp
            }
            MutationKind.UNSTABLE -> {
                npc.baseAttackLvl =
                    (npc.baseAttackLvl * 130 / 100).coerceAtLeast(npc.baseAttackLvl + 1)
            }
            MutationKind.VAMPIRIC -> {
                // Vampiric lifesteal is read from config at hit-time; no static stat change.
            }
            MutationKind.BERSERK -> {
                // Berserk damage scales with low hp at hit-time; no static stat change.
            }
        }
    }
}
