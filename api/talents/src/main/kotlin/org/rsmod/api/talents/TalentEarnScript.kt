package org.rsmod.api.talents

import jakarta.inject.Inject
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.script.onEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Mints talent points for boss kills - the earn side of both talent trees.
 *
 * [NpcKilledEvent] fires once per attributed kill, so subscribing here is the whole awarding path.
 * The boss gate reuses `PerkService.isBoss` (combat level >= 100) so "boss" means the same thing
 * for talent points, perk points and PvM points. Each boss kill grants the killer +1 personal
 * talent point and +1 point to their company's shared pool when they belong to one - the two
 * currencies are deliberately separate, matching the two trees.
 */
public class TalentEarnScript
@Inject
constructor(
    private val playerTalents: PlayerTalentService,
    private val companies: CompanyService,
    private val perks: PerkService,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcKilledEvent> {
            if (!perks.isBoss(npc.visType.vislevel)) {
                return@onEvent
            }
            playerTalents.grant(killer, BOSS_KILL_POINTS)
            companies.awardMemberBossKill(killer)
            killer.mes("<col=c8a2ff>You earn $BOSS_KILL_POINTS talent point(s).</col>")
        }
    }

    private companion object {
        private const val BOSS_KILL_POINTS = 1
    }
}
