package org.rsmod.content.skills.mining.scripts

import jakarta.inject.Inject
import org.rsmod.api.commons.skilling.SkillingRewards
import org.rsmod.api.config.Constants
import org.rsmod.api.config.locParam
import org.rsmod.api.config.locXpParam
import org.rsmod.api.config.objParam
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.perk.PerkService
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.core.SkillingPointAward
import org.rsmod.content.skills.core.SkillingPoints
import org.rsmod.content.skills.mining.configs.MiningParams
import org.rsmod.content.skills.mining.configs.mining_content
import org.rsmod.events.UnboundEvent
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The core mining loop: swing, roll, deplete, respawn.
 *
 * **Only one op is bound.** Every rock in the cache carries `Mine` on op1 and nothing else usable -
 * op3 is a hidden Prospect slot - so unlike trees, which split "Chop down" and "Chop" over two ops,
 * the loop re-enters itself through `opLoc1` until something interrupts the player.
 *
 * **The pickaxe sets the cadence, the rock sets the odds.** That is the one real difference from
 * woodcutting, where the axe picks the success rate and the cadence is fixed. See `MiningPicks` and
 * `MiningRocks` for both halves and where their numbers come from.
 *
 * **One cycle, one clock.** `actionDelay` is the tick the next roll lands on, and nothing else is
 * tracked: the tick after a roll starts a fresh cycle, replays the animation and re-arms the clock.
 * Interval is drawn once per cycle, which matters because dragon and crystal pickaxes draw a random
 * one - reading it twice in a cycle would let the animation and the roll drift apart.
 *
 * TODO:
 * - Gem drop table (1/256 per attempt, 1/64.2 with a charged glory).
 * - The Mining Guild's invisible +7 and its halved respawn times.
 * - Dragon pickaxe special attack. It is the same shape as the dragon axe's, which already works.
 */
class Mining
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val locRepo: LocRepository,
    private val playerRepo: PlayerRepository,
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
    private val perks: PerkService,
    private val mapClock: MapClock,
    private val skillingRewards: SkillingRewards,
    private val skillingPoints: SkillingPoints,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(mining_content.rock) { mine(it.loc, it.type) }
    }

    private fun ProtectedAccess.mine(rock: BoundLocInfo, type: UnpackedLocType) {
        val pickaxe = findPickaxe(player, objTypes)
        if (pickaxe == null) {
            mes("You need a pickaxe to mine this rock.")
            mes("You do not have a pickaxe which you have the Mining level to use.")
            return
        }

        if (player.miningLvl < type.rockLevelReq) {
            mes("You need a Mining level of ${type.rockLevelReq} to mine this rock.")
            return
        }

        if (inv.isFull()) {
            val ore = objTypes[type.rockOre]
            mes("Your inventory is too full to hold any more ${ore.name.lowercase()}.")
            soundSynth(synths.pillory_wrong)
            return
        }

        var minedOre = false

        if (actionDelay == mapClock) {
            minedOre = statRandom(stats.mining, type.rockRateLow, type.rockRateHigh, invisibleLvls)
        } else if (actionDelay < mapClock) {
            // Only a fresh start announces itself. Mid-run the previous cycle ended on the tick
            // just gone, so anything further back is a new one. `actionDelay` sits at -1 until the
            // player's first timed action ever, which is why it is ruled out explicitly rather
            // than left to arithmetic that happens to work once the clock is past 0.
            val resuming = actionDelay >= 0 && actionDelay == mapClock - 1
            if (!resuming) {
                spam("You swing your pick at the rock.")
            }
            startCycle(objTypes[pickaxe])
        }

        // An ore rock is spent the moment it gives up its ore: `deplete_chance` is 0 on every rock
        // this module authors, and the roll below is `> 0`. The param is read rather than assumed
        // so
        // a future rock can survive a hit without touching this script.
        val depleted = minedOre && random.of(1, 255) > type.rockDepleteChance

        if (minedOre) {
            val ore = objTypes[type.rockOre]
            val xp = type.rockXp * xpMods.get(player, stats.mining)
            spam("You manage to mine some ${ore.name.lowercase()}.")
            statAdvance(stats.mining, xp)
            skillingRewards.grant(this, "MINING", ore)
            // One skilling point per successful roll, minted only here: a missed roll, a missing
            // pickaxe, a wrong rock or an interrupted run never reaches this line, and the
            // point-to-shop chain starts from exactly this call.
            skillingPoints.grant(this, SkillingPointAward(1))
            // Motherlode perk: chance of a bonus ore.
            if (random.of(10_000) < perks.doubleOreBps(player)) {
                invAdd(inv, ore)
            }
            publish(MinedOre(player, rock, ore))
        }

        if (depleted) {
            val respawnTime = type.rockRespawnTime
            locRepo.change(rock, type.rockDepletedStage, respawnTime)
            resetAnim()
            sendLocalOverlayLoc(rock, type, respawnTime)
            return
        }

        opLoc1(rock)
    }

    /**
     * Arms the clock for the next roll and replays the swing.
     *
     * Dragon-tier and crystal pickaxes do not roll on a fractional interval; they roll every three
     * ticks with a flat chance of that becoming two. `1/6` for dragon averages the 2.83 ticks the
     * wiki publishes, and `1/4` for crystal averages its 2.75.
     */
    private fun ProtectedAccess.startCycle(pickaxe: UnpackedObjType) {
        val fastRollChance = pickaxe.pickaxeFastRollChance
        val fastRoll = fastRollChance > 0 && random.randomBoolean(fastRollChance)
        val interval = if (fastRoll) pickaxe.pickaxeRollTicks - 1 else pickaxe.pickaxeRollTicks
        actionDelay = mapClock + interval - 1
        anim(pickaxe.pickaxeMiningAnim)
    }

    private fun sendLocalOverlayLoc(rock: BoundLocInfo, type: UnpackedLocType, respawnTime: Int) {
        val players = playerRepo.findAll(ZoneKey.from(rock.coords), zoneRadius = 3)
        for (player in players) {
            ClientScripts.addOverlayTimerLoc(
                player = player,
                coords = rock.coords,
                loc = type,
                shape = rock.shape,
                timer = Constants.overlay_timer_mining,
                ticks = respawnTime,
                colour = 16765184,
            )
        }
    }

    data class MinedOre(val player: Player, val rock: BoundLocInfo, val ore: ObjType) :
        UnboundEvent

    companion object {
        val UnpackedObjType.pickaxeMiningReq: Int by objParam(params.levelrequire)
        val UnpackedObjType.pickaxeMiningAnim: SeqType by objParam(params.skill_anim)
        val UnpackedObjType.pickaxeRollTicks: Int by objParam(MiningParams.roll_ticks)
        val UnpackedObjType.pickaxeFastRollChance: Int by objParam(MiningParams.fast_roll_chance)

        val UnpackedLocType.rockLevelReq: Int by locParam(params.levelrequire)
        val UnpackedLocType.rockOre: ObjType by locParam(params.skill_productitem)
        val UnpackedLocType.rockXp: Double by locXpParam(params.skill_xp)
        val UnpackedLocType.rockDepletedStage: LocType by locParam(params.next_loc_stage)
        val UnpackedLocType.rockRespawnTime: Int by locParam(params.respawn_time)
        val UnpackedLocType.rockDepleteChance: Int by locParam(params.deplete_chance)
        val UnpackedLocType.rockRateLow: Int by locParam(MiningParams.rate_low)
        val UnpackedLocType.rockRateHigh: Int by locParam(MiningParams.rate_high)

        /**
         * The best usable pickaxe, worn or carried, exactly as woodcutting picks an axe. `>=` is
         * deliberate: bronze and iron carry `levelrequire = 0` in this cache rather than 1.
         */
        fun findPickaxe(player: Player, objTypes: ObjTypeList): InvObj? {
            // Bronze and iron share a level requirement: prefer the faster pick, regardless of
            // inventory order or whether the slower one is equipped.
            return (player.inv.filterNotNull { objTypes[it].isUsablePickaxe(player.miningLvl) } +
                    listOfNotNull(player.wornPickaxe(objTypes)))
                .minWithOrNull(
                    compareBy<InvObj> {
                            val type = objTypes[it]
                            type.pickaxeRollTicks.toDouble() -
                                if (type.pickaxeFastRollChance > 0) 1.0 / type.pickaxeFastRollChance
                                else 0.0
                        }
                        .thenByDescending { objTypes[it].pickaxeMiningReq }
                )
        }

        private fun Player.wornPickaxe(objTypes: ObjTypeList): InvObj? {
            val righthand = righthand ?: return null
            return righthand.takeIf { objTypes[it].isUsablePickaxe(miningLvl) }
        }

        private fun UnpackedObjType.isUsablePickaxe(miningLevel: Int): Boolean =
            isContentType(mining_content.pickaxe) && miningLevel >= pickaxeMiningReq
    }
}
