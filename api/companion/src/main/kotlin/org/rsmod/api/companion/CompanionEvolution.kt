package org.rsmod.api.companion

/**
 * One evolution stage of a companion. Stages are ordered; a companion at `evolutionStage = n` can
 * evolve to stage `n + 1` once [requiredLevel] is met.
 *
 * @param statBonusPercent percent added to damage, healing output and (of base) max health.
 * @param talentPointsBonus one-off talent points granted on evolving.
 * @param npcId optional model override - when non-null the companion morphs on evolving.
 */
public data class CompanionEvolutionStage(
    public val stage: Int,
    public val requiredLevel: Int,
    public val statBonusPercent: Int,
    public val talentPointsBonus: Int = 0,
    public val npcId: Int? = null,
    public val title: String,
) {
    init {
        require(stage >= 1)
        require(requiredLevel >= 1)
        require(statBonusPercent >= 0)
        require(talentPointsBonus >= 0)
        require(title.isNotBlank())
    }
}

/**
 * Data-driven evolution track shared by every companion class. Per-class or per-species branching
 * can be layered on later without changing persistence (the stage index is the only stored value).
 */
public object CompanionEvolutionCatalog {
    public const val MAX_STAGE: Int = 3

    public val stages: List<CompanionEvolutionStage> =
        listOf(
            CompanionEvolutionStage(
                stage = 1,
                requiredLevel = 25,
                statBonusPercent = 5,
                talentPointsBonus = 1,
                title = "Awakened",
            ),
            CompanionEvolutionStage(
                stage = 2,
                requiredLevel = 50,
                statBonusPercent = 10,
                talentPointsBonus = 2,
                title = "Empowered",
            ),
            CompanionEvolutionStage(
                stage = 3,
                requiredLevel = 75,
                statBonusPercent = 15,
                talentPointsBonus = 2,
                title = "Ascendant",
            ),
        )

    /** The stage definition for [stage], or `null` for stage 0 (base form). */
    public fun stage(stage: Int): CompanionEvolutionStage? =
        stages.firstOrNull { it.stage == stage }

    /** The next stage a companion at [currentStage] could evolve into, or `null` at max. */
    public fun nextStage(currentStage: Int): CompanionEvolutionStage? =
        stages.firstOrNull { it.stage == currentStage + 1 }

    /** Whether [companion] meets every requirement for its next stage. */
    public fun canEvolve(companion: Companion): Boolean {
        val next = nextStage(companion.evolutionStage) ?: return false
        return companion.level >= next.requiredLevel
    }
}
