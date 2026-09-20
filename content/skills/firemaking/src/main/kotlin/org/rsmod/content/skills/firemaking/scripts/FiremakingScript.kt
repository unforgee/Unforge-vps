package org.rsmod.content.skills.firemaking.scripts

import jakarta.inject.Inject
import org.rsmod.api.commons.skilling.SkillingRewards
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.skills.core.MakeRequest
import org.rsmod.content.skills.core.SkillMakeActions
import org.rsmod.content.skills.firemaking.configs.BurnableLog
import org.rsmod.content.skills.firemaking.configs.FIREMAKING_CYCLES
import org.rsmod.content.skills.firemaking.configs.FiremakingRecipes
import org.rsmod.content.skills.firemaking.configs.firemaking_locs
import org.rsmod.content.skills.firemaking.configs.firemaking_objs
import org.rsmod.content.skills.firemaking.configs.firemaking_seqs
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.synth.SynthTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Firemaking: tinderbox on logs, one fire per click.
 *
 * Firemaking has no make menu - the log in hand is the whole choice - so the only interface is the
 * tinderbox-on-logs interaction, and the whole of its "recipe" data is the log table.
 *
 * The lighter is placed through the shared weak queue even though it makes exactly one fire: the
 * log is consumed when the queue fires, not when the tinderbox is used, so walking or clicking away
 * during the two-tick window cancels the attempt and leaves the log in the inventory. That is the
 * same interruption model as every other production skill, and it is why this is not a plain
 * `delay()` call.
 */
class FiremakingScript
@Inject
constructor(
    private val synthTypes: SynthTypeList,
    private val locRepo: LocRepository,
    private val skillingRewards: SkillingRewards,
    private val make: SkillMakeActions,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (recipe in FiremakingRecipes.all) {
            onOpHeldU(firemaking_objs.tinderbox, recipe.log) { lightLog(this, recipe) }
        }
    }

    private fun lightLog(access: ProtectedAccess, recipe: BurnableLog) {
        val level = access.stat(stats.firemaking)
        if (level < recipe.level) {
            access.mes("You need a Firemaking level of ${recipe.level} to light these logs.")
            return
        }
        val fireCoords = access.coords
        val fireLoc = firemaking_locs.fire

        val request =
            MakeRequest(
                // Nothing is granted: the fire is a loc, placed by `onMade`.
                product = null,
                consumed = mapOf(recipe.log to 1),
                skill = FIREMAKING,
                stat = stats.firemaking,
                xp = recipe.xp,
                anim = firemaking_seqs.create_fire,
                sound = synthTypes[FIRE_SYNTH],
                cycles = FIREMAKING_CYCLES,
                successMessage = "The fire catches and the logs begin to burn.",
                onMade = {
                    skillingRewards.bonusDrop(this, FIREMAKING)
                    // The fire is left on the tile the player was standing on, so they step aside
                    // to stand next to it - the same shape as the game, where the fire is created
                    // under the player and they are pushed off it.
                    walk(fireCoords.translateX(-1))
                    locRepo.add(
                        fireCoords,
                        fireLoc,
                        duration = recipe.burnTicks,
                        angle = LocAngle.West,
                        shape = LocShape.CentrepieceStraight,
                    )
                },
            )
        make.start(access, request, 1)
    }

    private companion object {
        private const val FIREMAKING = "FIREMAKING"

        /**
         * The lighter's synth, authored as an id: `synth.sym` carries no names in this revision.
         */
        private const val FIRE_SYNTH = 2584
    }
}
