package org.rsmod.api.companion

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.PlayerList

/**
 * Companion death lifecycle: when a companion bot reaches zero hitpoints, the standard player death
 * sequence (respawn teleport, stat restore, item loss) is consumed and replaced with the companion
 * domain transition.
 *
 * The hook is idempotent: [CompanionService.damage] no-ops once the companion is `INCAPACITATED`,
 * so duplicate death queues and late hits in the same tick can never restart the cooldown or
 * double-notify the owner. The bot itself is removed by the companion runtime sync on the next
 * cycle - the companion is already `active=false` there, so it cannot attack, follow or process
 * further combat in the meantime.
 */
@Singleton
public class CompanionDeathHook
@Inject
constructor(
    private val registry: CompanionPlayerRegistry,
    private val companions: CompanionService,
    private val players: PlayerList,
) : PlayerDeathHook {
    override suspend fun death(access: ProtectedAccess): Boolean {
        val bot = access.player
        val companionId = registry.getCompanionId(bot) ?: return false
        val ownerId = registry.getOwnerCharacterId(companionId) ?: return false

        // Stop the bot mid-cycle: no follow-up attack, movement or queued combat actions.
        bot.interaction = null
        access.stopAction()
        access.combatClearQueue()

        val companion = companions.owned(ownerId).firstOrNull { it.id == companionId }
        if (companion != null && companion.state != CompanionState.INCAPACITATED) {
            companions.damage(ownerId, companionId, companion.hitpoints, System.currentTimeMillis())
            access.anim(seqs.human_death)
            players
                .firstOrNull { it.characterId.toLong() == ownerId }
                ?.mes(
                    "<col=ff0000>${companion.name} has fallen! " +
                        "It can be summoned again in 5 minutes.</col>"
                )
        }
        return true
    }
}
