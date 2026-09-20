package org.rsmod.api.npc.pet

/** Single editable server-side balance source for pet behaviour. */
public object PetRuntimeConfig {
    public val policy: PetRuntimePolicy =
        PetRuntimePolicy(
            bossPetNpcIds = setOf(),
            skillingPetNpcIds = setOf(),
            bossDamageMultiplier = 1.0,
            bossDefenceMultiplier = 1.0,
            bossHitpointsMultiplier = 1.0,
            skillingPetXpMultiplier = 1.0,
        )
}
