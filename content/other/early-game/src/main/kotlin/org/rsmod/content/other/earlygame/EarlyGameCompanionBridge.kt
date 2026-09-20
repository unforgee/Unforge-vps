package org.rsmod.content.other.earlygame

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.companion.CompanionAbilityCatalog
import org.rsmod.api.companion.CompanionPlayerRegistry
import org.rsmod.api.companion.CompanionService
import org.rsmod.api.companion.CompanionTalent
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player
import org.rsmod.game.type.npc.NpcTypeList

/**
 * The only early-game dependency on Companion Evolve 2.0. If the companion API changes, this is the
 * adapter that changes; Adventure Path state and save format stay untouched.
 */
@Singleton
public class EarlyGameCompanionBridge
@Inject
constructor(
    private val companions: CompanionService,
    private val companionRegistry: CompanionPlayerRegistry,
    private val npcTypes: NpcTypeList,
) {
    public fun isCompanionSystemAvailable(): Boolean = true

    public fun unlockCompanion(
        player: Player,
        definition: EarlyGameProgressionService.StarterCompanion,
    ): EarlyGameProgressionService.CompanionUnlockResult {
        val ownerId = player.characterId.toLong()
        check(companions.owned(ownerId).isEmpty()) { "A Companion is already owned." }
        val npcId = starterNpcId()
        val recruited =
            companions.recruit(
                ownerId,
                1,
                definition.displayName,
                definition.companionClass,
                npcId,
                50,
            )
        // The first ability is intentionally granted as a starter loadout. It still runs through
        // CompanionService.useAbility, including cooldowns and active effects; the normal talent
        // tree can replace or extend this loadout after onboarding.
        val starterAbility = definition.starterAbility
        val companion =
            recruited.copy(
                talents = listOf(CompanionTalent(definition.starterTalent, ranks = 1)),
                abilityLoadout = listOf(starterAbility),
            )
        companions.restore(ownerId, listOf(companion))
        player.mes(
            "Your ${definition.displayName} is ready with ${CompanionAbilityCatalog.definition(starterAbility).name}."
        )
        player.mes(
            "Use ::pet to summon it, then ::companionbasics ability to try the starter skill."
        )
        return EarlyGameProgressionService.CompanionUnlockResult(
            displayName = companion.name,
            role = definition.role,
            abilityName = definition.abilityName,
        )
    }

    public fun summonCompanion(player: Player): Boolean {
        val ownerId = player.characterId.toLong()
        val companion = companions.owned(ownerId).firstOrNull() ?: return false
        runCatching { companions.activate(ownerId, companion.id) }
            .onFailure { player.mes(it.message ?: "Could not summon Companion.") }
        player.mes("<col=33b532>${companion.name} is following you.</col>")
        return true
    }

    public fun openCompanionHub(player: Player): Boolean {
        if (companions.owned(player.characterId.toLong()).isEmpty()) return false
        player.mes("Companion Hub: use ::pet, ::petgear, ::pettalents or ::cstats.")
        return true
    }

    /** Uses the same server-authoritative ability validation as the live Companion action bar. */
    public fun onCompanionAbilityUsed(player: Player): Boolean {
        val ownerId = player.characterId.toLong()
        val companion = companions.owned(ownerId).firstOrNull() ?: return false
        if (!companion.active) return false
        val abilityId = companion.abilityLoadout.firstOrNull() ?: return false
        val use =
            runCatching {
                    companions.useAbility(
                        ownerCharacterId = ownerId,
                        companionId = companion.id,
                        abilityId = abilityId,
                    )
                }
                .getOrElse {
                    player.mes(it.message ?: "The Companion ability is not ready yet.")
                    return false
                }
        player.mes("<col=33b532>${use.ability.name} activated.</col>")
        return true
    }

    private fun starterNpcId(): Int =
        npcTypes.values
            .firstOrNull { it.internalName?.contains("goblin", ignoreCase = true) == true }
            ?.id ?: npcTypes.values.firstOrNull()?.id ?: 0
}
