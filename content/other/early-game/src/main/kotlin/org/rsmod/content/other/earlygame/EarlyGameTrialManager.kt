package org.rsmod.content.other.earlygame

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.map.translate
import org.rsmod.game.type.npc.NpcTypeList

/**
 * Owns the short-lived First Bond encounter. The progression service owns save data; this class
 * owns only the live NPC instance so the encounter cannot be completed by an unrelated kill.
 */
@Singleton
public class EarlyGameTrialManager
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val npcRepository: NpcRepository,
    private val progression: EarlyGameProgressionService,
) {
    private val encounters = mutableMapOf<Int, Npc>()

    public fun start(player: Player): Boolean {
        val state = progression.ensure(player)
        if (!state.firstBondUnlocked) {
            player.mes("Complete the early Adventure Path before starting First Bond.")
            return false
        }
        if (state.firstBondCompleted) {
            player.mes("First Bond is already complete.")
            return false
        }
        if (encounters.containsKey(player.characterId)) {
            player.mes("Your Bond Guardian is already waiting nearby.")
            return false
        }
        val type =
            npcTypes.values.firstOrNull {
                it.internalName?.contains("bond_guardian", ignoreCase = true) == true
            }
                ?: npcTypes.values.firstOrNull {
                    it.internalName?.contains("guardian", ignoreCase = true) == true
                }
                ?: npcTypes.values.firstOrNull {
                    it.internalName?.contains("goblin", ignoreCase = true) == true
                }
                ?: npcTypes.values.firstOrNull()
        if (type == null) {
            player.mes("The Bond Guardian could not be prepared in this world.")
            return false
        }
        val guardian = Npc(type, player.coords.translate(1, 0))
        guardian.mode = NpcMode.None
        npcRepository.add(guardian, EarlyGameConfig.TRIAL_LIFETIME_CYCLES)
        encounters[player.characterId] = guardian
        progression.startTrial(player)
        player.mes("A Bond Guardian appears nearby. Defeat that guardian to complete First Bond.")
        return true
    }

    public fun onNpcKilled(event: NpcKilledEvent) {
        val ownerId = event.killer.characterId
        if (encounters[ownerId] !== event.npc) return
        encounters.remove(ownerId)
        progression.completeTrial(event.killer)
    }

    public fun cleanup(player: Player) {
        val guardian = encounters.remove(player.characterId) ?: return
        npcRepository.del(guardian, Int.MAX_VALUE)
    }
}
