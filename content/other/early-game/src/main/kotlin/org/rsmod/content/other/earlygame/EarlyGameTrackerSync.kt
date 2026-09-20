package org.rsmod.content.other.earlygame

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Base64
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player

/**
 * Publishes the Adventure Path state to the client over the `UNFORGE_ADV|*` console side channel,
 * where the `Unforge Adventure Path` RuneLite plugin renders it as a proper overlay panel instead
 * of chat text.
 *
 * Wire format (every message stays well under the 255-byte `MessageGame` cap):
 * - `UNFORGE_ADV|C` clears the client's step table (sent before `D` frames).
 * - `UNFORGE_ADV|D|<ordinal>|<b64 title>|<b64 phase>|<b64 requirement>` defines one milestone step;
 *   sent once per login since the table is static enum data.
 * - `UNFORGE_ADV|S|<doneMask>|<relic>|<discoveries>|<points>|<companion>|<hidden>|<minimized>`
 *   carries the mutable snapshot: `doneMask` is a decimal `Long` bitmask over milestone ordinals,
 *   `relic`/`companion` are display names or `-` when unset, `hidden`/`minimized` are `0`/`1`
 *   tracker visibility preferences.
 */
@Singleton
public class EarlyGameTrackerSync @Inject constructor() {
    /** Re-sends the full step table followed by the current status snapshot. */
    public fun pushAll(player: Player, state: EarlyGameState) {
        player.mes("$PREFIX|C", ChatType.Console)
        AdventureMilestone.entries.forEachIndexed { index, milestone ->
            player.mes(
                "$PREFIX|D|$index|${enc(milestone.title)}|${enc(milestone.phase.title)}|" +
                    enc(milestone.requirement),
                ChatType.Console,
            )
        }
        pushStatus(player, state)
    }

    /** Sends only the mutable snapshot; cheap enough to emit after every state change. */
    public fun pushStatus(player: Player, state: EarlyGameState) {
        var doneMask = 0L
        AdventureMilestone.entries.forEachIndexed { index, milestone ->
            if (milestone.name in state.completedMilestones) {
                doneMask = doneMask or (1L shl index)
            }
        }
        val relic = state.starterRelic?.displayName ?: "-"
        val companion =
            when {
                state.starterCompanionSelected -> state.starterCompanionId ?: "-"
                state.firstBondCompleted || state.firstBondUnlocked -> "READY"
                else -> "LOCKED"
            }
        player.mes(
            "$PREFIX|S|$doneMask|$relic|${state.discoveries.size}|" +
                "${state.discoveryPoints}|$companion|" +
                "${if (state.trackerHidden) 1 else 0}|${if (state.trackerMinimized) 1 else 0}",
            ChatType.Console,
        )
    }

    private fun enc(text: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))

    private companion object {
        private const val PREFIX = "UNFORGE_ADV"
    }
}
