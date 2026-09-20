package org.rsmod.api.companion

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

public interface CompanionContractSource {
    public fun consume(ownerCharacterId: Long, slot: Int): Boolean
}

public object NoopCompanionContractSource : CompanionContractSource {
    override fun consume(ownerCharacterId: Long, slot: Int): Boolean = true
}

public class CompanionService(
    private val contracts: CompanionContractSource = NoopCompanionContractSource,
    private val persistence: CompanionPersistence = NoopCompanionPersistence,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private val values = ConcurrentHashMap<Long, MutableList<Companion>>()
    private val abilityCooldowns = ConcurrentHashMap<Pair<Long, String>, Long>()
    private val activeAbilityEffects =
        ConcurrentHashMap<Long, MutableList<CompanionActiveAbilityEffect>>()
    private val pendingAbilityHitmarks = ConcurrentHashMap<Long, CompanionHitmarkStyle>()

    public fun recruit(
        ownerCharacterId: Long,
        slot: Int,
        name: String,
        companionClass: CompanionClass,
        npcId: Int,
        maximumHitpoints: Int,
    ): Companion {
        validateName(name)
        require(slot in 1..CompanionRules.MAX_SLOTS)
        require(maximumHitpoints > 0)
        val owned = values.computeIfAbsent(ownerCharacterId) { mutableListOf() }
        require(owned.none { it.slot == slot }) { "Companion slot $slot is already occupied" }
        require(slot == 1 || owned.any { it.slot == slot - 1 }) {
            "Companion slots must be unlocked in order"
        }
        check(contracts.consume(ownerCharacterId, slot)) { "Companion Contract Scroll is required" }
        val companion =
            Companion(
                id = nextId(ownerCharacterId, slot),
                ownerCharacterId = ownerCharacterId,
                slot = slot,
                name = name.trim(),
                companionClass = companionClass,
                npcId = npcId,
                hitpoints = maximumHitpoints,
                maximumHitpoints = maximumHitpoints,
            )
        owned += companion
        persistence.save(companion)
        return companion
    }

    /**
     * Milliseconds left on [companion]'s incapacitation cooldown at [nowEpochMillis]. Always `0`
     * for companions that are not incapacitated or whose cooldown has already elapsed.
     */
    public fun cooldownRemainingMillis(companion: Companion, nowEpochMillis: Long): Long {
        if (companion.state != CompanionState.INCAPACITATED) return 0L
        val until = companion.incapacitatedUntilEpochMillis ?: return 0L
        return (until - nowEpochMillis).coerceAtLeast(0L)
    }

    public fun activate(ownerCharacterId: Long, companionId: Long): Companion {
        var selected = find(ownerCharacterId, companionId)
        if (selected.state == CompanionState.INCAPACITATED) {
            val remaining = cooldownRemainingMillis(selected, clockMillis())
            require(remaining <= 0L) { "Companion is still recovering for ${remaining}ms" }
            selected = resummon(ownerCharacterId, companionId, clockMillis())
        }
        require(selected.state != CompanionState.DESPAWNED)
        val owned = owned(ownerCharacterId)
        val activeCount = owned.count { it.active && it.id != companionId }
        require(activeCount < CompanionRules.ACTIVE_LIMIT) {
            "Cannot exceed active companion limit"
        }
        val updated = owned.map { if (it.id == companionId) it.copy(active = true) else it }
        replace(ownerCharacterId, updated)
        updated.forEach(persistence::save)
        return updated.first { it.id == companionId }
    }

    public fun activateAll(ownerCharacterId: Long): List<Companion> {
        val now = clockMillis()
        val owned = owned(ownerCharacterId)
        val updated =
            owned.map { companion ->
                when {
                    // Expired incapacitation cooldowns resummon automatically.
                    companion.state == CompanionState.INCAPACITATED &&
                        cooldownRemainingMillis(companion, now) <= 0L ->
                        companion.copy(
                            active = true,
                            state = CompanionState.FOLLOWING,
                            hitpoints = companion.maximumHitpoints,
                            incapacitatedUntilEpochMillis = null,
                        )
                    companion.state == CompanionState.INCAPACITATED ||
                        companion.state == CompanionState.DESPAWNED -> companion
                    else -> companion.copy(active = true)
                }
            }
        replace(ownerCharacterId, updated)
        updated.forEach(persistence::save)
        return updated.filter { it.active }
    }

    public fun morph(
        ownerCharacterId: Long,
        companionId: Long,
        npcId: Int,
        name: String,
    ): Companion {
        require(npcId >= 0)
        validateName(name)
        val current = find(ownerCharacterId, companionId)
        val updated = current.copy(npcId = npcId, name = name.trim())
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    public fun deactivate(ownerCharacterId: Long, companionId: Long): Companion {
        val owned = owned(ownerCharacterId)
        val updated = owned.map { if (it.id == companionId) it.copy(active = false) else it }
        replace(ownerCharacterId, updated)
        updated.forEach(persistence::save)
        return updated.first { it.id == companionId }
    }

    public fun setPackCapacity(
        ownerCharacterId: Long,
        companionId: Long,
        packCapacity: Int,
    ): Companion {
        require(packCapacity in CompanionRules.MIN_PACK_CAPACITY..CompanionRules.MAX_PACK_CAPACITY)
        val current = find(ownerCharacterId, companionId)
        require(packCapacity >= current.packCapacity) { "Pack capacity cannot be downgraded" }
        val updated = current.copy(packCapacity = packCapacity)
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    public fun addExperience(ownerCharacterId: Long, companionId: Long, amount: Long): Companion {
        require(amount >= 0L)
        val current = find(ownerCharacterId, companionId)
        val xp = current.experience + amount
        val level = CompanionRules.levelFromExperience(xp)
        val updated =
            current.copy(
                level = level,
                experience = xp,
                talentPoints = CompanionRules.talentPointsForLevel(level),
            )
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    /**
     * Evolves [companionId] to its next [CompanionEvolutionCatalog] stage. Requirements are
     * validated server-side; the stage may grant talent points and an optional model morph.
     */
    public fun evolve(ownerCharacterId: Long, companionId: Long): Companion {
        val current = find(ownerCharacterId, companionId)
        val next =
            CompanionEvolutionCatalog.nextStage(current.evolutionStage)
                ?: error("${current.name} has already reached its final form.")
        require(current.level >= next.requiredLevel) {
            "${current.name} must reach level ${next.requiredLevel} to evolve."
        }
        val updated =
            current.copy(
                evolutionStage = next.stage,
                npcId = next.npcId ?: current.npcId,
                talentPoints = current.talentPoints + next.talentPointsBonus,
            )
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    public fun equipGear(
        ownerCharacterId: Long,
        companionId: Long,
        instanceIds: List<Long>,
    ): Companion {
        require(instanceIds.size <= CompanionRules.MAX_GEAR_ITEMS)
        require(instanceIds.all { it > 0L })
        require(instanceIds.distinct().size == instanceIds.size)
        val current = find(ownerCharacterId, companionId)
        val updated = current.copy(gearInstanceIds = instanceIds)
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    public fun allocateTalent(
        ownerCharacterId: Long,
        companionId: Long,
        talentId: String,
    ): Companion {
        val current = find(ownerCharacterId, companionId)
        val definition = CompanionTalentCatalog.definition(talentId)
        require(definition.companionClass == current.companionClass)
        val spent =
            current.talents.sumOf { talent ->
                CompanionTalentCatalog.definition(talent.definitionId).pointsPerRank * talent.ranks
            }
        require(CompanionRules.tierUnlocked(definition.tier, spent))
        require(spent < current.talentPoints)
        val unlocked =
            current.talents.filter { it.ranks > 0 }.mapTo(mutableSetOf()) { it.definitionId }
        require(definition.prerequisites.all(unlocked::contains)) {
            "Talent $talentId requires: ${definition.prerequisites.joinToString()}."
        }
        val old = current.talents.firstOrNull { it.definitionId == talentId }
        require(old == null || old.ranks < definition.maxRanks)
        val talents = current.talents.toMutableList()
        if (old == null) talents += CompanionTalent(talentId, 1)
        else talents[talents.indexOf(old)] = old.copy(ranks = old.ranks + 1)
        val updated = current.copy(talents = talents)
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    /**
     * Validates and starts a player-requested active ability cooldown. The caller remains
     * responsible for applying the concrete combat/healing effect to game entities.
     */
    public fun useAbility(
        ownerCharacterId: Long,
        companionId: Long,
        abilityId: String,
        targetIds: List<Int> = emptyList(),
        nowEpochMillis: Long = clockMillis(),
        cooldownReductionBps: Int = 0,
    ): CompanionAbilityUse {
        val current = find(ownerCharacterId, companionId)
        require(current.active) { "The companion must be active to use an ability." }
        val ability = CompanionAbilityCatalog.definition(abilityId)
        require(ability.active) { "${ability.name} is triggered automatically." }
        val talent =
            current.talents
                .mapNotNull { trained ->
                    CompanionTalentCatalog.byId[trained.definitionId]?.let { definition ->
                        if (definition.abilityId == abilityId && trained.ranks > 0)
                            definition to trained
                        else null
                    }
                }
                .firstOrNull() ?: error("Ability ${ability.name} has not been unlocked.")
        require(abilityId in current.abilityLoadout) {
            "${ability.name} is not equipped in the companion ability loadout."
        }
        val key = current.id to abilityId
        val readyAt = abilityCooldowns[key] ?: 0L
        require(nowEpochMillis >= readyAt) {
            "${ability.name} is on cooldown for ${readyAt - nowEpochMillis} ms."
        }
        require(targetIds.distinct().size == targetIds.size) {
            "Duplicate ability targets are not allowed."
        }
        when (ability.target) {
            CompanionAbilityTarget.SELF,
            CompanionAbilityTarget.OWNER,
            CompanionAbilityTarget.OWNER_AND_COMPANION ->
                require(targetIds.isEmpty()) {
                    "${ability.name} selects its party target automatically."
                }
            CompanionAbilityTarget.SINGLE_ENEMY ->
                require(targetIds.size == 1) {
                    "${ability.name} requires exactly one enemy target."
                }
            CompanionAbilityTarget.AREA_ENEMIES ->
                require(targetIds.size <= CompanionRules.MAX_ABILITY_TARGETS) {
                    "${ability.name} accepts at most ${CompanionRules.MAX_ABILITY_TARGETS} targets."
                }
        }
        // Centralized CDR: the caller passes the companion's sheet-derived reduction so gear,
        // talents and sets genuinely shorten ability cooldowns through one rule.
        val effectiveTicks =
            CompanionStatCaps.effectiveCooldownTicks(ability.cooldownTicks, cooldownReductionBps)
        abilityCooldowns[key] = nowEpochMillis + effectiveTicks * 600L
        if (CompanionAbilityEffects.effect(abilityId).durationTicks > 0) {
            val effect = CompanionAbilityEffects.effect(abilityId)
            activeAbilityEffects.compute(current.id) { _, old ->
                val live =
                    old.orEmpty()
                        .filter { it.expiresAtEpochMillis > nowEpochMillis }
                        .toMutableList()
                live.removeIf { it.abilityId == abilityId }
                live +=
                    CompanionActiveAbilityEffect(
                        current.id,
                        abilityId,
                        effect,
                        targetIds,
                        nowEpochMillis + effect.durationTicks * 600L,
                    )
                live
            }
        }
        val hitmarkStyle = CompanionHitmarkResolver.styleForAbility(current, abilityId)
        if (ability.target == CompanionAbilityTarget.SINGLE_ENEMY) {
            pendingAbilityHitmarks[current.id] = hitmarkStyle
        }
        return CompanionAbilityUse(
            companionId = current.id,
            ability = ability,
            talentId = talent.first.id,
            rank = talent.second.ranks,
            targetIds = targetIds,
            effect = CompanionAbilityEffects.effect(abilityId),
            hitmarkStyle = hitmarkStyle,
        )
    }

    /**
     * Consumes the visual style selected by the most recent validated ability. The combat layer
     * calls this immediately before queueing the next hit, so damage remains on the shared PvN path
     * while the ability-specific visual is applied once.
     */
    public fun consumePendingAbilityHitmark(companionId: Long): CompanionHitmarkStyle? =
        pendingAbilityHitmarks.remove(companionId)

    public fun activeAbilityEffects(
        companionId: Long,
        nowEpochMillis: Long = clockMillis(),
    ): List<CompanionActiveAbilityEffect> =
        activeAbilityEffects
            .computeIfPresent(companionId) { _, old ->
                old.filter { it.expiresAtEpochMillis > nowEpochMillis }.toMutableList()
            }
            ?.toList()
            .orEmpty()

    public fun incomingDamageMultiplier(
        companionId: Long,
        nowEpochMillis: Long = clockMillis(),
    ): Double {
        val effects = activeAbilityEffects(companionId, nowEpochMillis)
        return effects.fold(1.0) { multiplier, active ->
            multiplier *
                when (active.abilityId) {
                    "iron-bastion" -> 0.65
                    "intercept" -> 0.75
                    "divine-intervention" -> 0.0
                    else -> 1.0
                }
        }
    }

    public fun setAbilityLoadout(
        ownerCharacterId: Long,
        companionId: Long,
        abilityIds: List<String>,
    ): Companion {
        require(abilityIds.size <= CompanionRules.MAX_ABILITY_SLOTS)
        require(abilityIds.distinct().size == abilityIds.size) {
            "Ability loadout entries must be unique."
        }
        val current = find(ownerCharacterId, companionId)
        abilityIds.forEach { abilityId ->
            val ability = CompanionAbilityCatalog.definition(abilityId)
            require(ability.active) {
                "${ability.name} cannot be equipped because it is triggered automatically."
            }
            require(
                current.talents.any { trained ->
                    trained.ranks > 0 &&
                        CompanionTalentCatalog.definition(trained.definitionId).abilityId ==
                            abilityId
                }
            ) {
                "${ability.name} has not been unlocked."
            }
        }
        val updated = current.copy(abilityLoadout = abilityIds)
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    public fun abilityCooldownRemainingMillis(
        companionId: Long,
        abilityId: String,
        nowEpochMillis: Long = clockMillis(),
    ): Long =
        ((abilityCooldowns[companionId to abilityId] ?: 0L) - nowEpochMillis).coerceAtLeast(0L)

    public fun resetTalents(ownerCharacterId: Long, companionId: Long): Companion {
        val current = find(ownerCharacterId, companionId)
        activeAbilityEffects.remove(companionId)
        val updated = current.copy(talents = emptyList(), abilityLoadout = emptyList())
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    public fun setClass(
        ownerCharacterId: Long,
        companionId: Long,
        companionClass: CompanionClass,
    ): Companion {
        val current = find(ownerCharacterId, companionId)
        activeAbilityEffects.remove(companionId)
        val updated =
            current.copy(
                companionClass = companionClass,
                talents = emptyList(),
                abilityLoadout = emptyList(),
            )
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    public fun equipGearItem(
        ownerCharacterId: Long,
        companionId: Long,
        instanceId: Long,
    ): Companion {
        require(instanceId > 0L)
        val current = find(ownerCharacterId, companionId)
        if (instanceId in current.gearInstanceIds) return current
        val updatedGear =
            (current.gearInstanceIds + instanceId).takeLast(CompanionRules.MAX_GEAR_ITEMS)
        val updated = current.copy(gearInstanceIds = updatedGear)
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    public fun unequipGearItem(
        ownerCharacterId: Long,
        companionId: Long,
        instanceId: Long,
    ): Companion {
        val current = find(ownerCharacterId, companionId)
        val updated = current.copy(gearInstanceIds = current.gearInstanceIds - instanceId)
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    /**
     * Changes the companion's combat capability. The style is persisted but only gates spell
     * casting: `MAGIC` companions may drive the spell pipeline, while `MELEE`/`RANGED` companions
     * always use the standard weapon interaction. The stored autocast selection is left untouched
     * so switching back to `MAGIC` restores it without data loss.
     */
    public fun setAttackStyle(
        ownerCharacterId: Long,
        companionId: Long,
        style: CompanionAttackStyle,
    ): Companion {
        val current = find(ownerCharacterId, companionId)
        val updated = current.copy(attackStyle = style)
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    public fun setCombatMode(
        ownerCharacterId: Long,
        companionId: Long,
        mode: CompanionCombatMode,
    ): Companion {
        val current = find(ownerCharacterId, companionId)
        val updated =
            current.copy(
                combatMode = mode,
                state =
                    if (
                        mode == CompanionCombatMode.PASSIVE &&
                            current.state != CompanionState.INCAPACITATED &&
                            current.state != CompanionState.DESPAWNED
                    )
                        CompanionState.FOLLOWING
                    else current.state,
            )
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    /**
     * Replaces the companion's AI behaviour settings. Applied server-side on the next tick - target
     * selection, follow spacing and role-spell gating all read this value from the persisted
     * record, so relog never loses the configuration.
     */
    public fun setBehaviour(
        ownerCharacterId: Long,
        companionId: Long,
        behaviour: CompanionBehaviour,
    ): Companion {
        val current = find(ownerCharacterId, companionId)
        val updated = current.copy(behaviour = behaviour)
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    /**
     * Switches the companion-owned spellbook. When [autocastStillValid] is `false` the stored
     * autocast selection is cleared, because a spell from the previous book must never be cast
     * while the new book is active. The caller resolves validity from the spell registry; the
     * service enforces the invariant server-side.
     */
    public fun setSpellbook(
        ownerCharacterId: Long,
        companionId: Long,
        book: CompanionSpellbook,
        autocastStillValid: Boolean,
    ): Companion {
        val current = find(ownerCharacterId, companionId)
        val updated =
            current.copy(
                spellbook = book,
                autocastSpellId = if (autocastStillValid) current.autocastSpellId else 0,
            )
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    /**
     * Selects the companion's autocast spell. [spellBook] and [levelReq] are the spell's own
     * metadata resolved from the cache-backed spell registry by the caller; the service re-checks
     * both server-side so manipulated clients cannot select out-of-book or too-high-level spells.
     */
    public fun setAutocastSpell(
        ownerCharacterId: Long,
        companionId: Long,
        autocastId: Int,
        spellBook: CompanionSpellbook,
        levelReq: Int,
    ): Companion {
        require(autocastId > 0) { "Invalid autocast spell id: $autocastId" }
        require(levelReq >= 1) { "Invalid spell level requirement: $levelReq" }
        val current = find(ownerCharacterId, companionId)
        require(spellBook == current.spellbook) {
            "Spell does not belong to the companion's active spellbook"
        }
        require(current.level >= levelReq) {
            "Companion level ${current.level} is below the spell requirement $levelReq"
        }
        val updated = current.copy(autocastSpellId = autocastId)
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    public fun clearAutocast(ownerCharacterId: Long, companionId: Long): Companion {
        val current = find(ownerCharacterId, companionId)
        val updated = current.copy(autocastSpellId = 0)
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    public fun tick(
        ownerCharacterId: Long,
        companionId: Long,
        context: CompanionCombatContext,
        nearbyTargets: List<CompanionTarget>,
    ): CompanionAction? {
        val companion = find(ownerCharacterId, companionId)
        if (
            !companion.active ||
                companion.state == CompanionState.INCAPACITATED ||
                companion.state == CompanionState.DESPAWNED
        )
            return null
        val companionTarget =
            when (companion.combatMode) {
                CompanionCombatMode.PASSIVE -> null
                CompanionCombatMode.DEFENSIVE ->
                    defensivePick(companion.behaviour.targetPriority, context, nearbyTargets)
                CompanionCombatMode.AGGRESSIVE ->
                    aggressivePick(companion.behaviour.targetPriority, context, nearbyTargets)
            }
        val validOwnerTarget =
            companionTarget != null &&
                companionTarget.alive &&
                !companionTarget.isPlayer &&
                companionTarget.attackable &&
                companionTarget.reachable &&
                companionTarget.distance <= 8
        val inCombat =
            context.ownerOnline &&
                context.ownerAlive &&
                context.sameRegion &&
                !context.inWilderness &&
                validOwnerTarget
        val state = if (inCombat) CompanionState.COMBAT else CompanionState.FOLLOWING
        val updated = companion.copy(state = state)
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        if (!inCombat) return null
        // DEFENSIVE engages the owner's target plus other live threats near the owner (up to 3,
        // for multi-target classes like DPS). AGGRESSIVE already picked the single best nearby
        // target, so its candidate list stays that single pick.
        val candidates =
            when (companion.combatMode) {
                CompanionCombatMode.AGGRESSIVE -> listOfNotNull(companionTarget)
                else -> listOfNotNull(companionTarget) + nearbyTargets
            }
        val targets =
            candidates
                .filter {
                    it.alive && !it.isPlayer && it.attackable && it.reachable && it.distance <= 8
                }
                .distinctBy { it.id }
                .take(3)
        if (targets.isEmpty()) return null
        val timedEffects = activeAbilityEffects(companion.id)
        val markedTargetIds =
            timedEffects
                .filter { it.abilityId == "death-mark" || it.abilityId == "crippling-strike" }
                .flatMapTo(mutableSetOf()) { it.targetIds }
        val damageBonus =
            timedEffects.sumOf {
                when (it.abilityId) {
                    "frenzy" -> 0.20
                    "rallying-call" -> 0.15
                    else -> 0.0
                }
            }
        return when (companion.companionClass) {
            CompanionClass.TANK ->
                CompanionAction(
                    targets.take(1).map { it.id },
                    CompanionClass.TANK,
                    "TAUNT_AND_ATTACK",
                    damageMultiplier = 0.75 + damageBonus,
                )
            CompanionClass.SUPPORT ->
                CompanionAction(
                    targets.take(1).map { it.id },
                    CompanionClass.SUPPORT,
                    "SUPPORT_THEN_ATTACK",
                    damageMultiplier = 0.60 + damageBonus,
                    supportPower = 1.0,
                )
            CompanionClass.DPS ->
                CompanionAction(
                    targets.map { it.id },
                    CompanionClass.DPS,
                    "ATTACK_OWNER_TARGET",
                    damageMultiplier =
                        1.25 +
                            damageBonus +
                            if (targets.any { it.id in markedTargetIds }) 0.25 else 0.0,
                )
        }
    }

    /**
     * DEFENSIVE-mode target pick, honouring the configured [CompanionTargetPriority]. The owner's
     * target is always a valid fallback so the companion never stalls while the owner fights.
     */
    private fun defensivePick(
        priority: CompanionTargetPriority,
        context: CompanionCombatContext,
        nearbyTargets: List<CompanionTarget>,
    ): CompanionTarget? =
        when (priority) {
            CompanionTargetPriority.OWNER_TARGET,
            CompanionTargetPriority.LOWEST_HEALTH_ALLY ->
                context.ownerTarget ?: context.ownerUnderAttackTarget
            CompanionTargetPriority.OWNER_ATTACKER ->
                context.ownerUnderAttackTarget ?: context.ownerTarget
            CompanionTargetPriority.NEAREST_HOSTILE ->
                nearbyTargets.nearestEligible(context)
                    ?: (context.ownerTarget ?: context.ownerUnderAttackTarget)
        }

    /**
     * AGGRESSIVE-mode target pick. The npc the owner is fighting remains a valid pick (companions
     * assist rather than stall when it is the only npc in range); the priority only reorders the
     * candidates.
     */
    private fun aggressivePick(
        priority: CompanionTargetPriority,
        context: CompanionCombatContext,
        nearbyTargets: List<CompanionTarget>,
    ): CompanionTarget? =
        when (priority) {
            CompanionTargetPriority.OWNER_TARGET,
            CompanionTargetPriority.LOWEST_HEALTH_ALLY ->
                context.ownerTarget?.takeIf { it.eligibleForAggressive(context) }
                    ?: nearbyTargets.nearestEligible(context)
            CompanionTargetPriority.OWNER_ATTACKER ->
                context.ownerUnderAttackTarget?.takeIf { it.eligibleForAggressive(context) }
                    ?: context.ownerTarget?.takeIf { it.eligibleForAggressive(context) }
                    ?: nearbyTargets.nearestEligible(context)
            CompanionTargetPriority.NEAREST_HOSTILE ->
                nearbyTargets.nearestEligible(context)
                    ?: context.ownerTarget?.takeIf { it.eligibleForAggressive(context) }
        }

    private fun List<CompanionTarget>.nearestEligible(
        context: CompanionCombatContext
    ): CompanionTarget? =
        filter { it.eligibleForAggressive(context) }
            .sortedWith(
                compareBy<CompanionTarget> { it.distance }
                    .thenBy { it.x }
                    .thenBy { it.y }
                    .thenBy { it.id }
            )
            .firstOrNull()

    private fun CompanionTarget.eligibleForAggressive(context: CompanionCombatContext): Boolean =
        alive &&
            !isPlayer &&
            attackable &&
            reachable &&
            plane == context.ownerPlane &&
            kotlin.math.abs(x - context.ownerX) <= 12 &&
            kotlin.math.abs(y - context.ownerY) <= 12 &&
            distance <= 8

    public fun damage(
        ownerCharacterId: Long,
        companionId: Long,
        amount: Int,
        nowEpochMillis: Long,
    ): Companion {
        require(amount >= 0)
        val current = find(ownerCharacterId, companionId)
        if (
            current.state == CompanionState.INCAPACITATED ||
                current.state == CompanionState.DESPAWNED
        )
            return current
        val hp = max(0, current.hitpoints - amount)
        val updated =
            if (hp == 0)
                current.copy(
                    hitpoints = 0,
                    active = false,
                    state = CompanionState.INCAPACITATED,
                    incapacitatedUntilEpochMillis =
                        nowEpochMillis + CompanionRules.INCAPACITATED_COOLDOWN_MILLIS,
                )
            else current.copy(hitpoints = hp)
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    /**
     * Restores [amount] hitpoints to a living companion (support-role healing). Incapacitated and
     * despawned companions are never healed through this path: they recover exclusively via
     * [resummon]/[activate], which enforce the full incapacitation rules. The domain value is
     * authoritative - the runtime mirrors it onto the bot entity.
     */
    public fun heal(ownerCharacterId: Long, companionId: Long, amount: Int): Companion {
        require(amount >= 0)
        val current = find(ownerCharacterId, companionId)
        if (
            current.state == CompanionState.INCAPACITATED ||
                current.state == CompanionState.DESPAWNED
        )
            return current
        val hp = minOf(current.hitpoints + amount, current.maximumHitpoints)
        if (hp == current.hitpoints) return current
        val updated = current.copy(hitpoints = hp)
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    public fun resummon(
        ownerCharacterId: Long,
        companionId: Long,
        nowEpochMillis: Long,
    ): Companion {
        val current = find(ownerCharacterId, companionId)
        require(current.state == CompanionState.INCAPACITATED)
        require((current.incapacitatedUntilEpochMillis ?: Long.MAX_VALUE) <= nowEpochMillis)
        val updated =
            current.copy(
                active = true,
                state = CompanionState.FOLLOWING,
                hitpoints = current.maximumHitpoints,
                incapacitatedUntilEpochMillis = null,
            )
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    /**
     * Developer/admin escape hatch: clears the incapacitated state, restores hitpoints and wipes
     * the cooldown immediately without activating the companion. Never invoked by gameplay paths -
     * normal recovery flows exclusively through [resummon] and the activation functions, which
     * enforce [CompanionRules.INCAPACITATED_COOLDOWN_MILLIS].
     */
    public fun revive(ownerCharacterId: Long, companionId: Long): Companion {
        val current = find(ownerCharacterId, companionId)
        val updated =
            current.copy(
                state = CompanionState.FOLLOWING,
                hitpoints = current.maximumHitpoints,
                incapacitatedUntilEpochMillis = null,
            )
        replace(
            ownerCharacterId,
            owned(ownerCharacterId).map { if (it.id == companionId) updated else it },
        )
        persistence.save(updated)
        return updated
    }

    public fun owned(ownerCharacterId: Long): List<Companion> =
        values[ownerCharacterId]?.toList().orEmpty()

    /** Loads persisted companions once for a logged-in owner without changing stable ids. */
    public fun load(ownerCharacterId: Long) {
        if (values.containsKey(ownerCharacterId)) return
        val loaded = persistence.load(ownerCharacterId)
        if (loaded.isNotEmpty()) values[ownerCharacterId] = loaded.toMutableList()
    }

    /** Restores a character segment produced by the account loading pipeline. */
    public fun restore(ownerCharacterId: Long, companions: List<Companion>) {
        require(companions.all { it.ownerCharacterId == ownerCharacterId })
        require(companions.map { it.slot }.distinct().size == companions.size)
        require(companions.count { it.active } <= CompanionRules.ACTIVE_LIMIT)
        values[ownerCharacterId] = companions.toMutableList()
    }

    private fun find(ownerCharacterId: Long, companionId: Long): Companion =
        owned(ownerCharacterId).first { it.id == companionId }

    private fun replace(ownerCharacterId: Long, companions: List<Companion>) {
        values[ownerCharacterId] = companions.toMutableList()
    }

    private fun nextId(owner: Long, slot: Int): Long = (owner shl 8) or slot.toLong()

    private fun validateName(name: String) {
        require(
            name.trim().length in CompanionRules.NAME_MIN_LENGTH..CompanionRules.NAME_MAX_LENGTH
        )
        require(name.trim().matches(Regex("[A-Za-z0-9 _-]+")))
    }
}
