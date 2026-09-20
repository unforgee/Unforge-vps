package org.rsmod.content.pvmpoints

import jakarta.inject.Inject
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onEvent
import org.rsmod.api.talents.CompanyService
import org.rsmod.api.talents.PlayerTalentService
import org.rsmod.api.talents.TalentEffectKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Mints `pvm_points` for attributed npc kills.
 *
 * [NpcKilledEvent] is published once per kill that has a resolved player killer, so subscribing
 * here is the whole awarding path - no combat hooks, no duplicated kill attribution. The amount
 * scales with the npc's combat level via [pvmPointsForLevel]; trivial mobs award `0` and stay
 * silent rather than spamming the chatbox on every critter.
 *
 * The talent trees stack on top: the killer's personal `PVM_POINTS_BPS` and their company's
 * `COMPANY_PVM_POINTS_BPS` are summed into one basis-point bonus applied to the base award, so a
 * player with no talents and no company sees identical payouts to before.
 */
class PvmPointsScript
@Inject
constructor(
    private val pvm: PvmPoints,
    private val playerTalents: PlayerTalentService,
    private val companies: CompanyService,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcKilledEvent> {
            val bonusBps =
                playerTalents.effectBps(killer, TalentEffectKey.PVM_POINTS_BPS) +
                    companies.effectBps(killer, TalentEffectKey.COMPANY_PVM_POINTS_BPS)
            val awarded = pvm.award(killer, npc.visType.vislevel, bonusBps)
            if (awarded > 0) {
                killer.mes("<col=ffb84d>You earn $awarded PvM point(s).</col>")
            }
        }
    }
}
