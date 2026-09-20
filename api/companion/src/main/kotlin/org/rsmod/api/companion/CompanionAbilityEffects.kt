package org.rsmod.api.companion

/** Concrete effect family used by the runtime adapter after server validation. */
public enum class CompanionAbilityEffectType {
    DAMAGE,
    HEAL,
    BUFF,
    DEBUFF,
    DEFENSIVE,
    MOVEMENT,
    TAUNT,
}

public data class CompanionAbilityEffect(
    public val type: CompanionAbilityEffectType,
    public val powerPercent: Int,
    public val durationTicks: Int = 0,
    public val target: CompanionAbilityTarget,
) {
    init {
        require(powerPercent in 1..500)
        require(durationTicks >= 0)
    }
}

public data class CompanionActiveAbilityEffect(
    public val companionId: Long,
    public val abilityId: String,
    public val effect: CompanionAbilityEffect,
    public val targetIds: List<Int>,
    public val expiresAtEpochMillis: Long,
)

/** Stable, deterministic effect mapping kept outside the UI and persistence layers. */
public object CompanionAbilityEffects {
    private val effects: Map<String, CompanionAbilityEffect> =
        mapOf(
            "shield-bash" to
                effect(CompanionAbilityEffectType.DAMAGE, 125, CompanionAbilityTarget.SINGLE_ENEMY),
            "challenging-shout" to
                effect(
                    CompanionAbilityEffectType.TAUNT,
                    100,
                    CompanionAbilityTarget.AREA_ENEMIES,
                    80,
                ),
            "iron-bastion" to
                effect(CompanionAbilityEffectType.DEFENSIVE, 35, CompanionAbilityTarget.SELF, 100),
            "unyielding-fortress" to
                effect(
                    CompanionAbilityEffectType.DEFENSIVE,
                    50,
                    CompanionAbilityTarget.OWNER_AND_COMPANION,
                    120,
                ),
            "intercept" to
                effect(
                    CompanionAbilityEffectType.DEFENSIVE,
                    25,
                    CompanionAbilityTarget.OWNER_AND_COMPANION,
                    60,
                ),
            "mending-wave" to
                effect(
                    CompanionAbilityEffectType.HEAL,
                    25,
                    CompanionAbilityTarget.OWNER_AND_COMPANION,
                ),
            "purifying-light" to
                effect(CompanionAbilityEffectType.BUFF, 100, CompanionAbilityTarget.OWNER),
            "guardian-rescue" to
                effect(CompanionAbilityEffectType.HEAL, 35, CompanionAbilityTarget.OWNER),
            "divine-intervention" to
                effect(CompanionAbilityEffectType.DEFENSIVE, 100, CompanionAbilityTarget.OWNER, 80),
            "dispel-pulse" to
                effect(
                    CompanionAbilityEffectType.BUFF,
                    100,
                    CompanionAbilityTarget.OWNER_AND_COMPANION,
                ),
            "rallying-call" to
                effect(
                    CompanionAbilityEffectType.BUFF,
                    15,
                    CompanionAbilityTarget.OWNER_AND_COMPANION,
                    100,
                ),
            "everlasting-aid" to
                effect(
                    CompanionAbilityEffectType.HEAL,
                    40,
                    CompanionAbilityTarget.OWNER_AND_COMPANION,
                    120,
                ),
            "focused-volley" to
                effect(CompanionAbilityEffectType.DAMAGE, 150, CompanionAbilityTarget.SINGLE_ENEMY),
            "crippling-strike" to
                effect(
                    CompanionAbilityEffectType.DEBUFF,
                    20,
                    CompanionAbilityTarget.SINGLE_ENEMY,
                    100,
                ),
            "death-mark" to
                effect(
                    CompanionAbilityEffectType.DEBUFF,
                    25,
                    CompanionAbilityTarget.SINGLE_ENEMY,
                    160,
                ),
            "perfect-execution" to
                effect(CompanionAbilityEffectType.DAMAGE, 300, CompanionAbilityTarget.SINGLE_ENEMY),
            "shadow-step" to
                effect(
                    CompanionAbilityEffectType.MOVEMENT,
                    125,
                    CompanionAbilityTarget.SINGLE_ENEMY,
                ),
            "frenzy" to
                effect(CompanionAbilityEffectType.BUFF, 20, CompanionAbilityTarget.SELF, 100),
            "apex-predator" to
                effect(CompanionAbilityEffectType.DAMAGE, 350, CompanionAbilityTarget.SINGLE_ENEMY),
        )

    public fun effect(abilityId: String): CompanionAbilityEffect =
        effects[abilityId] ?: error("No effect mapping for companion ability: $abilityId")

    public fun validateCatalog() {
        CompanionAbilityCatalog.definitions
            .filter { it.active }
            .forEach { definition ->
                val effect = effect(definition.id)
                require(effect.target == definition.target) {
                    "Effect target for ${definition.id} does not match its ability definition."
                }
            }
    }

    private fun effect(
        type: CompanionAbilityEffectType,
        powerPercent: Int,
        target: CompanionAbilityTarget,
        durationTicks: Int = 0,
    ): CompanionAbilityEffect = CompanionAbilityEffect(type, powerPercent, durationTicks, target)
}
