package org.rsmod.content.other.league.tools

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.spotanims
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.content.other.league.configs.league_objs
import org.rsmod.content.other.league.configs.league_seqs
import org.rsmod.content.other.league.configs.league_spots

/**
 * Instant special attacks for the League tool family.
 *
 * The plain `trailblazer_*` tools are already covered by `StatBoostSpecialAttacks` through the
 * `dragon_*_or` / `infernal_*_or` aliases, so only the League and Trailblazer Reloaded ids are
 * registered here. Registering an already-registered obj throws at startup.
 */
class LeagueToolSpecialAttacks @Inject constructor(private val worldRepo: WorldRepository) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerInstant(league_objs.league_trailblazer_axe, ::lumberUp)
        registerInstant(league_objs.trailblazer_reloaded_axe, ::lumberUp)
        registerInstant(league_objs.trailblazer_reloaded_axe_empty, ::lumberUp)
        registerInstant(league_objs.trailblazer_reloaded_axe_no_infernal, ::lumberUp)

        registerInstant(league_objs.league_trailblazer_pickaxe, ::rockKnocker)
        registerInstant(league_objs.trailblazer_reloaded_pickaxe, ::rockKnocker)
        registerInstant(league_objs.trailblazer_reloaded_pickaxe_empty, ::rockKnocker)
        registerInstant(league_objs.trailblazer_reloaded_pickaxe_no_infernal, ::rockKnocker)

        registerInstant(league_objs.league_trailblazer_harpoon, ::fishstabber)
        registerInstant(league_objs.trailblazer_reloaded_harpoon, ::fishstabber)
        registerInstant(league_objs.trailblazer_reloaded_harpoon_empty, ::fishstabber)
        registerInstant(league_objs.trailblazer_reloaded_harpoon_no_infernal, ::fishstabber)
    }

    private fun lumberUp(access: ProtectedAccess): Boolean = access.applyLumberUp()

    private fun ProtectedAccess.applyLumberUp(): Boolean {
        statBoost(stats.woodcutting, constant = 3, percent = 0)
        say("Chop chop!")
        anim(league_seqs.lumber_up)
        spotanim(league_spots.lumber_up_red, height = 96, slot = constants.spotanim_slot_combat)
        soundArea(worldRepo, coords, synths.clobber, radius = 1)
        return true
    }

    private fun rockKnocker(access: ProtectedAccess): Boolean = access.applyRockKnocker()

    private fun ProtectedAccess.applyRockKnocker(): Boolean {
        statBoost(stats.mining, constant = 3, percent = 0)
        say("Smashing!")
        anim(league_seqs.rock_knocker)
        soundArea(worldRepo, coords, synths.found_gem, radius = 1)
        return true
    }

    private fun fishstabber(access: ProtectedAccess): Boolean = access.applyFishstabber()

    private fun ProtectedAccess.applyFishstabber(): Boolean {
        statBoost(stats.fishing, constant = 3, percent = 0)
        say("Here fishy fishies!")
        anim(league_seqs.fishstabber)
        spotanim(spotanims.sp_attackglow_red)
        soundArea(worldRepo, coords, synths.rampage, radius = 1)
        return true
    }
}
