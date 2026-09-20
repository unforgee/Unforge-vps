package org.rsmod.api.npc.pet

public enum class PetKind {
    Vanilla,
    BossCombat,
    Skilling,
}

public data class PetDefinition(public val npcId: Int, public val kind: PetKind)

public data class PetCombatProfile(
    public val damageMultiplier: Double,
    public val defenceMultiplier: Double,
    public val hitpointsMultiplier: Double,
)

/** Server-only rules. The client never supplies pet kind or combat values. */
public class PetRuntimePolicy(
    public val bossPetNpcIds: Set<Int>,
    public val skillingPetNpcIds: Set<Int>,
    public val bossDamageMultiplier: Double = 1.0,
    public val bossDefenceMultiplier: Double = 1.0,
    public val bossHitpointsMultiplier: Double = 1.0,
    public val skillingPetXpMultiplier: Double = 1.0,
) {
    init {
        require(bossPetNpcIds.intersect(skillingPetNpcIds).isEmpty())
        require(
            bossDamageMultiplier >= 0.0 &&
                bossDefenceMultiplier >= 0.0 &&
                bossHitpointsMultiplier >= 0.0
        )
        require(skillingPetXpMultiplier >= 0.0)
    }

    public fun classify(npcId: Int, follower: Boolean): PetDefinition? {
        if (!follower) return null
        return PetDefinition(
            npcId,
            when {
                npcId in bossPetNpcIds -> PetKind.BossCombat
                npcId in skillingPetNpcIds -> PetKind.Skilling
                else -> PetKind.Vanilla
            },
        )
    }

    public fun combatProfile(pet: PetDefinition, inWilderness: Boolean): PetCombatProfile? =
        if (pet.kind == PetKind.BossCombat && !inWilderness)
            PetCombatProfile(bossDamageMultiplier, bossDefenceMultiplier, bossHitpointsMultiplier)
        else null

    public fun skillXp(baseXp: Int, pet: PetDefinition?): Int =
        if (pet?.kind == PetKind.Skilling) (baseXp * skillingPetXpMultiplier).toInt() else baseXp
}
