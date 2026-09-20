package org.rsmod.api.companion

public enum class CompanionClass {
    TANK,
    SUPPORT,
    DPS,
}

public enum class CompanionState {
    FOLLOWING,
    COMBAT,
    INCAPACITATED,
    DESPAWNED,
}

public enum class CompanionCombatMode {
    DEFENSIVE,
    AGGRESSIVE,
    PASSIVE,
}

/** How a talent participates in a companion build. */
public enum class CompanionTalentType {
    PASSIVE,
    ACTIVE,
    TRIGGERED,
    ULTIMATE,
}

/**
 * The explicit combat capability of a companion. `MAGIC` is the only style that may drive the
 * server-side spell pipeline; `RANGED` keeps the standard weapon pipeline (range comes from the
 * equipped weapon's `attackrange` param) and `MELEE` is the default for companions created before
 * the spellbook feature existed.
 */
public enum class CompanionAttackStyle {
    MELEE,
    RANGED,
    MAGIC,
}

/** Combat levels projected onto the virtual player that executes the shared combat pipeline. */
public data class CompanionCombatLevels(
    public val attack: Int,
    public val strength: Int,
    public val defence: Int,
    public val ranged: Int,
    public val magic: Int,
    public val prayer: Int,
)

/**
 * Resolves the active style into player-like combat levels. The inactive offensive styles remain at
 * level one; defence stays available for every style. Gear bonuses are deliberately not part of
 * this projection because the virtual player reads them from its worn equipment.
 */
public fun Companion.combatLevels(): CompanionCombatLevels {
    val primary = level.coerceIn(1, 99)
    val defence = (primary * 0.9).toInt().coerceAtLeast(1)
    val inactive = 1

    return when (attackStyle) {
        CompanionAttackStyle.MELEE ->
            CompanionCombatLevels(primary, primary, defence, inactive, inactive, primary)
        CompanionAttackStyle.RANGED ->
            CompanionCombatLevels(inactive, inactive, defence, primary, inactive, primary)
        CompanionAttackStyle.MAGIC ->
            CompanionCombatLevels(inactive, inactive, defence, inactive, primary, primary)
    }
}

/**
 * The companion-owned spellbook. Deliberately separate from the player's `Spellbook` varbit enum so
 * the companion model stays free of combat-module dependencies; the runtime maps these values onto
 * the engine spellbook when applying them to the companion's bot entity.
 */
public enum class CompanionSpellbook {
    STANDARD,
    ANCIENTS,
}

public data class CompanionTalentDefinition(
    public val id: String,
    public val companionClass: CompanionClass,
    public val tier: Int,
    public val maxRanks: Int = 5,
    public val pointsPerRank: Int = 1,
    public val effectKey: String,
    public val type: CompanionTalentType = CompanionTalentType.PASSIVE,
    public val abilityId: String? = null,
    public val prerequisites: List<String> = emptyList(),
    public val capstone: Boolean = false,
) {
    init {
        require(id.matches(Regex("[a-z0-9-]{2,48}")))
        require(tier in 1..8)
        require(maxRanks in 1..5)
        require(pointsPerRank > 0)
        require(effectKey.isNotBlank())
        require(type == CompanionTalentType.PASSIVE || abilityId != null) {
            "Non-passive talents must provide an ability id."
        }
        require(prerequisites.distinct().size == prerequisites.size)
        require(!prerequisites.contains(id))
        require(!capstone || tier == 8)
    }
}

public data class CompanionTalent(public val definitionId: String, public val ranks: Int = 0) {
    init {
        require(definitionId.isNotBlank())
        require(ranks >= 0)
    }
}

public data class Companion(
    public val id: Long,
    public val ownerCharacterId: Long,
    public val slot: Int,
    public val name: String,
    public val companionClass: CompanionClass,
    public val npcId: Int,
    public val level: Int = 1,
    public val experience: Long = 0,
    public val talentPoints: Int = 3,
    public val talents: List<CompanionTalent> = emptyList(),
    /** Player-selected active abilities. Empty keeps legacy companions compatible. */
    public val abilityLoadout: List<String> = emptyList(),
    public val active: Boolean = false,
    public val state: CompanionState = CompanionState.FOLLOWING,
    public val hitpoints: Int = 1,
    public val maximumHitpoints: Int = 1,
    public val incapacitatedUntilEpochMillis: Long? = null,
    public val encounterId: Long? = null,
    public val gearInstanceIds: List<Long> = emptyList(),
    public val attackStyle: CompanionAttackStyle = CompanionAttackStyle.MELEE,
    public val spellbook: CompanionSpellbook = CompanionSpellbook.STANDARD,
    public val autocastSpellId: Int = 0,
    public val packCapacity: Int = 10,
    public val combatMode: CompanionCombatMode = CompanionCombatMode.DEFENSIVE,
    public val evolutionStage: Int = 0,
    /** Player-configured AI behaviour; defaults keep pre-Behaviour-tab companions unchanged. */
    public val behaviour: CompanionBehaviour = CompanionBehaviour(),
) {
    init {
        require(id >= 0L)
        require(ownerCharacterId > 0L)
        require(slot in 1..CompanionRules.MAX_SLOTS)
        require(name.length in CompanionRules.NAME_MIN_LENGTH..CompanionRules.NAME_MAX_LENGTH)
        require(npcId >= 0)
        require(level >= 1)
        require(experience >= 0L)
        require(talentPoints >= CompanionRules.BASE_TALENT_POINTS)
        require(hitpoints >= 0 && maximumHitpoints >= 1 && hitpoints <= maximumHitpoints)
        require(gearInstanceIds.distinct().size == gearInstanceIds.size)
        require(abilityLoadout.size <= CompanionRules.MAX_ABILITY_SLOTS)
        require(abilityLoadout.distinct().size == abilityLoadout.size)
        require(state != CompanionState.INCAPACITATED || hitpoints == 0)
        require(state != CompanionState.DESPAWNED || !active)
        require(autocastSpellId >= 0)
        require(packCapacity in CompanionRules.MIN_PACK_CAPACITY..CompanionRules.MAX_PACK_CAPACITY)
        require(evolutionStage in 0..CompanionEvolutionCatalog.MAX_STAGE)
    }

    public fun withHitpoints(value: Int): Companion =
        copy(
            hitpoints = value.coerceIn(0, maximumHitpoints),
            state = if (value <= 0) CompanionState.INCAPACITATED else state,
            active = if (value <= 0) false else active,
        )
}

public object CompanionRules {
    public const val MAX_SLOTS: Int = 4
    public const val ACTIVE_LIMIT: Int = 4
    /** Equippable wearpos slots (Wearpos minus the client-only appearance layers). */
    public const val MAX_GEAR_ITEMS: Int = 11
    public const val MAX_ABILITY_SLOTS: Int = 3
    public const val MAX_ABILITY_TARGETS: Int = 9
    public const val BASE_TALENT_POINTS: Int = 3
    public const val NAME_MIN_LENGTH: Int = 1
    public const val NAME_MAX_LENGTH: Int = 12
    public const val MIN_PACK_CAPACITY: Int = 10
    public const val MAX_PACK_CAPACITY: Int = 100
    /** Exact 5-minute summon lockout applied when a companion's hitpoints reach zero. */
    public const val INCAPACITATED_COOLDOWN_MILLIS: Long = 300_000L
    public val TIER_POINT_GATES: List<Int> = listOf(0, 5, 10, 15, 20, 25, 30, 35)

    public fun talentPointsForLevel(level: Int): Int {
        require(level >= 1)
        val early = if (level < 5) 0 else ((minOf(level, 50) - 5) / 5) + 1
        val mid = if (level < 51) 0 else ((minOf(level, 100) - 51) / 3) + 1
        val late = (level - 100).coerceAtLeast(0)
        return BASE_TALENT_POINTS + early + mid + late
    }

    public fun levelFromExperience(experience: Long): Int {
        require(experience >= 0L)
        var level = 1
        var remaining = experience
        while (level < 200 && remaining >= experienceToNextLevel(level)) {
            remaining -= experienceToNextLevel(level)
            level++
        }
        return level
    }

    public fun experienceToNextLevel(level: Int): Long {
        require(level >= 1)
        return 100L * level
    }

    public fun tierUnlocked(tier: Int, spentPoints: Int): Boolean {
        require(tier in 1..8 && spentPoints >= 0)
        return spentPoints >= TIER_POINT_GATES[tier - 1]
    }
}

public data class CompanionTarget(
    public val id: Int,
    public val isPlayer: Boolean,
    public val alive: Boolean,
    public val reachable: Boolean,
    public val distance: Int,
    public val x: Int = 0,
    public val y: Int = 0,
    public val plane: Int = 0,
    public val attackable: Boolean = true,
    public val attackingOwner: Boolean = false,
)

public data class CompanionCombatContext(
    public val ownerOnline: Boolean,
    public val ownerAlive: Boolean,
    public val sameRegion: Boolean,
    public val inWilderness: Boolean,
    public val ownerTarget: CompanionTarget?,
    public val ownerUnderAttackTarget: CompanionTarget? = null,
    public val ownerX: Int = 0,
    public val ownerY: Int = 0,
    public val ownerPlane: Int = 0,
)

public data class CompanionAction(
    public val targetIds: List<Int>,
    public val role: CompanionClass,
    public val action: String,
    public val damageMultiplier: Double = 0.0,
    public val supportPower: Double = 0.0,
)

/**
 * Result of a validated player-requested ability cast. Effects are executed by the runtime layer.
 */
public data class CompanionAbilityUse(
    public val companionId: Long,
    public val ability: CompanionAbilityDefinition,
    public val talentId: String,
    public val rank: Int,
    public val targetIds: List<Int> = emptyList(),
    public val effect: CompanionAbilityEffect = CompanionAbilityEffects.effect(ability.id),
    public val hitmarkStyle: CompanionHitmarkStyle = CompanionHitmarkStyle.DAMAGE,
)
