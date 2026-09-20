package org.rsmod.api.companion

/** Valid target shape for a companion ability. */
public enum class CompanionAbilityTarget {
    SELF,
    OWNER,
    SINGLE_ENEMY,
    AREA_ENEMIES,
    OWNER_AND_COMPANION,
}

/** A server-authoritative definition used by the talent UI and future action bar. */
public data class CompanionAbilityDefinition(
    public val id: String,
    public val name: String,
    public val description: String,
    public val cooldownTicks: Int,
    public val target: CompanionAbilityTarget,
    public val active: Boolean = true,
    public val ultimate: Boolean = false,
) {
    init {
        require(id.matches(Regex("[a-z0-9-]{2,48}")))
        require(name.isNotBlank())
        require(description.isNotBlank())
        require(cooldownTicks > 0)
        require(!ultimate || active)
    }
}

/** Ability metadata is kept separate from talent definitions so abilities can be slotted later. */
public object CompanionAbilityCatalog {
    public val definitions: List<CompanionAbilityDefinition> =
        listOf(
            CompanionAbilityDefinition(
                "shield-bash",
                "Shield Bash",
                "Stuns and damages one enemy.",
                80,
                CompanionAbilityTarget.SINGLE_ENEMY,
            ),
            CompanionAbilityDefinition(
                "challenging-shout",
                "Challenging Shout",
                "Forces nearby enemies to attack it.",
                120,
                CompanionAbilityTarget.AREA_ENEMIES,
            ),
            CompanionAbilityDefinition(
                "iron-bastion",
                "Iron Bastion",
                "Massively reduces damage taken.",
                300,
                CompanionAbilityTarget.SELF,
                ultimate = true,
            ),
            CompanionAbilityDefinition(
                "intercept",
                "Intercept",
                "Redirects the owner's next attack.",
                100,
                CompanionAbilityTarget.OWNER_AND_COMPANION,
            ),
            CompanionAbilityDefinition(
                "protective-stance",
                "Protective Stance",
                "Auto-protects the owner in danger.",
                160,
                CompanionAbilityTarget.OWNER_AND_COMPANION,
                active = false,
            ),
            CompanionAbilityDefinition(
                "last-guardian",
                "Last Guardian",
                "Triggers on owner's lethal damage.",
                600,
                CompanionAbilityTarget.OWNER,
                active = false,
            ),
            CompanionAbilityDefinition(
                "unyielding-fortress",
                "Unyielding Fortress",
                "Creates a powerful defensive zone.",
                450,
                CompanionAbilityTarget.OWNER_AND_COMPANION,
                ultimate = true,
            ),
            CompanionAbilityDefinition(
                "mending-wave",
                "Mending Wave",
                "Heals the owner and companion.",
                100,
                CompanionAbilityTarget.OWNER_AND_COMPANION,
            ),
            CompanionAbilityDefinition(
                "purifying-light",
                "Purifying Light",
                "Removes harmful effects on owner.",
                140,
                CompanionAbilityTarget.OWNER,
            ),
            CompanionAbilityDefinition(
                "guardian-rescue",
                "Guardian Rescue",
                "Auto-rescues a wounded owner.",
                240,
                CompanionAbilityTarget.OWNER,
                active = false,
            ),
            CompanionAbilityDefinition(
                "divine-intervention",
                "Divine Intervention",
                "Prevents a lethal hit; heals.",
                600,
                CompanionAbilityTarget.OWNER,
                ultimate = true,
            ),
            CompanionAbilityDefinition(
                "dispel-pulse",
                "Dispel Pulse",
                "Cleanses nearby allies.",
                140,
                CompanionAbilityTarget.OWNER_AND_COMPANION,
            ),
            CompanionAbilityDefinition(
                "rallying-call",
                "Rallying Call",
                "Temporarily boosts combat stats.",
                180,
                CompanionAbilityTarget.OWNER_AND_COMPANION,
            ),
            CompanionAbilityDefinition(
                "second-wind",
                "Second Wind",
                "Auto-heals after heavy damage.",
                240,
                CompanionAbilityTarget.SELF,
                active = false,
            ),
            CompanionAbilityDefinition(
                "everlasting-aid",
                "Everlasting Aid",
                "Strong sustained healing effect.",
                450,
                CompanionAbilityTarget.OWNER_AND_COMPANION,
                ultimate = true,
            ),
            CompanionAbilityDefinition(
                "focused-volley",
                "Focused Volley",
                "Focused attack on one enemy.",
                80,
                CompanionAbilityTarget.SINGLE_ENEMY,
            ),
            CompanionAbilityDefinition(
                "crippling-strike",
                "Crippling Strike",
                "Damages and weakens one enemy.",
                110,
                CompanionAbilityTarget.SINGLE_ENEMY,
            ),
            CompanionAbilityDefinition(
                "momentum",
                "Momentum",
                "Gains speed after consecutive hits.",
                160,
                CompanionAbilityTarget.SELF,
                active = false,
            ),
            CompanionAbilityDefinition(
                "death-mark",
                "Death Mark",
                "Marks an enemy for increased damage.",
                140,
                CompanionAbilityTarget.SINGLE_ENEMY,
            ),
            CompanionAbilityDefinition(
                "perfect-execution",
                "Perfect Execution",
                "Devastating hit on weakened enemy.",
                500,
                CompanionAbilityTarget.SINGLE_ENEMY,
                ultimate = true,
            ),
            CompanionAbilityDefinition(
                "shadow-step",
                "Shadow Step",
                "Dashes to a target and strikes.",
                120,
                CompanionAbilityTarget.SINGLE_ENEMY,
            ),
            CompanionAbilityDefinition(
                "frenzy",
                "Frenzy",
                "Boosts attack speed and damage.",
                180,
                CompanionAbilityTarget.SELF,
            ),
            CompanionAbilityDefinition(
                "execution-window",
                "Execution Window",
                "Empowers hits vs low-HP enemies.",
                180,
                CompanionAbilityTarget.SELF,
                active = false,
            ),
            CompanionAbilityDefinition(
                "apex-predator",
                "Apex Predator",
                "Massive burst on the main target.",
                500,
                CompanionAbilityTarget.SINGLE_ENEMY,
                ultimate = true,
            ),
        )

    public val byId: Map<String, CompanionAbilityDefinition> = definitions.associateBy { it.id }

    init {
        validateTalentReferences()
    }

    public fun definition(id: String): CompanionAbilityDefinition =
        byId[id] ?: error("Unknown companion ability: $id")

    /** Ensures catalog talent references cannot silently point at missing abilities. */
    public fun validateTalentReferences() {
        CompanionTalentCatalog.definitions
            .filter { it.abilityId != null }
            .forEach { talent -> definition(talent.abilityId!!) }
    }
}
