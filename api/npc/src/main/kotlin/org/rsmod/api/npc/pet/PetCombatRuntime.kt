package org.rsmod.api.npc.pet

public enum class PetState {
    FOLLOW,
    COMBAT,
}

public data class PetTarget(
    public val id: Int,
    public val isPlayer: Boolean,
    public val isAlive: Boolean,
    public val reachable: Boolean,
    public val distance: Int,
)

public data class PetCombatContext(
    public val ownerTarget: PetTarget?,
    public val inWilderness: Boolean,
    public val ownerOnline: Boolean = true,
    public val ownerAlive: Boolean = true,
    public val sameRegion: Boolean = true,
)

public data class PetAttackProfile(
    public val animationId: Int,
    public val projectileId: Int?,
    public val gfxId: Int?,
    public val damageMultiplier: Double,
    public val aoeRadius: Int,
    public val chainMaxTargets: Int,
    public val chainFalloff: Double,
)

public data class PetAttackPlan(
    public val targetIds: List<Int>,
    public val damageMultipliers: List<Double>,
    public val profile: PetAttackProfile,
)

/** Pure server-side state/target resolver; the client cannot provide any of these values. */
public class PetCombatRuntime(private val policy: PetRuntimePolicy) {
    public fun state(pet: PetDefinition, context: PetCombatContext): PetState =
        if (
            pet.kind == PetKind.BossCombat &&
                context.ownerOnline &&
                context.ownerAlive &&
                context.sameRegion &&
                !context.inWilderness &&
                validTarget(context.ownerTarget)
        )
            PetState.COMBAT
        else PetState.FOLLOW

    public fun attack(
        pet: PetDefinition,
        context: PetCombatContext,
        nearbyTargets: List<PetTarget>,
        profile: PetAttackProfile,
    ): PetAttackPlan? {
        if (state(pet, context) != PetState.COMBAT) return null
        val primary = context.ownerTarget ?: return null
        val targets =
            (listOf(primary) + nearbyTargets)
                .filter { it.isAlive && !it.isPlayer && it.reachable && it.distance <= 8 }
                .distinctBy(PetTarget::id)
                .take(profile.chainMaxTargets.coerceIn(1, 3))
        if (targets.isEmpty()) return null
        val multipliers =
            targets.indices.map {
                profile.damageMultiplier * Math.pow(profile.chainFalloff, it.toDouble())
            }
        return PetAttackPlan(
            targets.map(PetTarget::id),
            multipliers,
            profile.copy(
                aoeRadius = profile.aoeRadius.coerceIn(0, 5),
                chainMaxTargets = profile.chainMaxTargets.coerceIn(1, 3),
                chainFalloff = profile.chainFalloff.coerceIn(0.0, 1.0),
            ),
        )
    }

    private fun validTarget(target: PetTarget?): Boolean =
        target != null &&
            target.isAlive &&
            !target.isPlayer &&
            target.reachable &&
            target.distance <= 8
}
