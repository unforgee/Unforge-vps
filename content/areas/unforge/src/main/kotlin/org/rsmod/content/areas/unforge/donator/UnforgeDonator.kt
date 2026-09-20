package org.rsmod.content.areas.unforge.donator

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.type.varp.VarpType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Kronos port: donator ranks were PlayerGroup flags (ids 11..17) with a double-drop
// percentage. In UnForge we persist the tier in a Perm-scope varp instead so that
// account/mod levels stay untouched.
object UnforgeDonatorVarps : VarpReferences() {
    val tier = find("cw_donator_tier")
    val points = find("cw_donator_points")
    val bossDay = find("cw_donator_boss_day")
    val bossKills = find("cw_donator_boss_kills")
}

internal object UnforgeDonatorVarpBuilder : VarpBuilder() {
    init {
        build("cw_donator_tier")
        build("cw_donator_points")
        build("cw_donator_boss_day")
        build("cw_donator_boss_kills")
    }
}

object UnforgeDonatorLocs : LocReferences() {
    val steppingStone = find("br_stepping_stone")
    val donatorPortal = find("castlewars_zamorak_exit")
    val midStairs = find("ds2_guild_mid_stairs")
    val spiralUp = find("fai_falador_spiralstairs")
    val spiralDown = find("fai_falador_spiralstairstop")
    val bossEntrance = find("golem_demon_portal")
}

enum class DonatorTier(
    val id: Int,
    val label: String,
    val doubleDropChance: Int,
    val bossKillsPerDay: Int,
) {
    NONE(0, "", 0, 0),
    SAPPHIRE(11, "Sapphire", 10, 0),
    EMERALD(12, "Emerald", 15, 0),
    RUBY(13, "Ruby", 25, 1),
    DIAMOND(14, "Diamond", 40, 2),
    DRAGONSTONE(15, "Dragonstone", 60, 3),
    ONYX(16, "Onyx", 80, 5),
    ZENYTE(17, "Zenyte", 100, 7);

    val isDonator: Boolean
        get() = this != NONE

    // Kronos "Super Donator or higher" = anything strictly above SAPPHIRE.
    val isSuperDonator: Boolean
        get() = id > SAPPHIRE.id

    companion object {
        fun of(id: Int): DonatorTier = entries.firstOrNull { it.id == id } ?: NONE
    }
}

val Player.donatorTier: DonatorTier
    get() = DonatorTier.of(vars[UnforgeDonatorVarps.tier])

val Player.isDonator: Boolean
    get() = donatorTier.isDonator

val Player.isSuperDonator: Boolean
    get() = donatorTier.isSuperDonator

private const val DONATOR_ISLAND_X = 3810
private const val DONATOR_ISLAND_Z = 2844
private const val DONATOR_ISLAND_LEVEL = 1

// Donator island occupies region 59,44 on level 0..2. All donator-zone loc handlers
// are gated to this bounding box so the reused vanilla loc types (portal, stairs,
// stepping stones, demon portal) keep working normally elsewhere in the world.
private const val ISLAND_MIN_X = 3776
private const val ISLAND_MAX_X = 3839
private const val ISLAND_MIN_Z = 2816
private const val ISLAND_MAX_Z = 2879

private const val BOSS_AREA_X = 2425
private const val BOSS_AREA_Z = 9531

private fun inDonatorIsland(coords: CoordGrid): Boolean =
    coords.x in ISLAND_MIN_X..ISLAND_MAX_X && coords.z in ISLAND_MIN_Z..ISLAND_MAX_Z

private fun todayEpoch(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()

private fun setCwVarp(player: Player, varp: VarpType, value: Int) {
    player.vars.backing[varp.id] = value
    player.resyncVar(varp)
}

class UnforgeDonator @Inject constructor(private val protectedAccess: ProtectedAccessLauncher) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onCommand("donor") {
            desc = "Set donator tier (admin): ::donor tierId"
            modLevel = modlevels.admin
            invalidArgs = "Use as ::donor tierId (0, 11-17)"
            cheat(::setDonor)
        }
        onCommand("donorstatus") {
            desc = "Show your donator tier and perks"
            cheat(::donorStatus)
        }
        onCommand("dzone") {
            desc = "Teleport to the donator island (donators only)"
            cheat(::donatorZone)
        }

        onOpLoc1(UnforgeDonatorLocs.steppingStone) { jumpStone(it) }
        onOpLoc1(UnforgeDonatorLocs.donatorPortal) { enterPortal(it) }
        onOpLoc1(UnforgeDonatorLocs.midStairs) { climbMiddle(it) }
        onOpLoc1(UnforgeDonatorLocs.spiralUp) { climbUpper(it) }
        onOpLoc1(UnforgeDonatorLocs.spiralDown) { climbDown(it) }
        onOpLoc1(UnforgeDonatorLocs.bossEntrance) { bossEntrance(it) }
    }

    private fun setDonor(cheat: Cheat) =
        with(cheat) {
            val tier = DonatorTier.of(args[0].toInt())
            setCwVarp(player, UnforgeDonatorVarps.tier, tier.id)
            RankIconRenderer.sync(player)
            player.mes(
                if (tier.isDonator) {
                    "Donator tier set to ${tier.label}."
                } else {
                    "Donator tier cleared."
                }
            )
        }

    private fun donorStatus(cheat: Cheat) =
        with(cheat) {
            val tier = player.donatorTier
            if (!tier.isDonator) {
                player.mes("You are not a donator.")
                return@with
            }
            player.mes(
                "Rank: ${tier.label} | points: ${player.vars[UnforgeDonatorVarps.points]} " +
                    "| double-drop: ${tier.doubleDropChance}% " +
                    "| boss kills left: ${bossKillsLeft(player)}"
            )
        }

    private fun donatorZone(cheat: Cheat) =
        with(cheat) {
            if (!player.isDonator && !player.modLevel.hasAccessTo(modlevels.admin)) {
                player.mes("The donator island is exclusive to donators.")
                return@with
            }
            protectedAccess.launch(player) {
                teleport(CoordGrid(DONATOR_ISLAND_X, DONATOR_ISLAND_Z, DONATOR_ISLAND_LEVEL))
            }
        }

    private fun ProtectedAccess.jumpStone(event: LocEvents.Op1) {
        if (!inDonatorIsland(event.loc.coords)) {
            return
        }
        if (!player.isSuperDonator) {
            player.mes(
                "The outer bounds of the island are only available to Super Donators or higher."
            )
            return
        }
        val dx = event.loc.coords.x - player.coords.x
        val dz = event.loc.coords.z - player.coords.z
        val hop =
            when {
                dx == -1 && dz == 0 -> player.coords.translate(-2, 0)
                dx == 1 && dz == 0 -> player.coords.translate(2, 0)
                dx == 0 && dz == 1 -> player.coords.translate(0, 2)
                dx == 0 && dz == -1 -> player.coords.translate(0, -2)
                else -> return
            }
        teleport(hop)
    }

    private fun ProtectedAccess.enterPortal(event: LocEvents.Op1) {
        if (!inDonatorIsland(event.loc.coords)) {
            return
        }
        if (!player.isDonator && !player.modLevel.hasAccessTo(modlevels.admin)) {
            player.mes("That portal is exclusive to donators!")
            return
        }
        teleport(CoordGrid(DONATOR_ISLAND_X, DONATOR_ISLAND_Z, DONATOR_ISLAND_LEVEL))
    }

    private fun ProtectedAccess.climbMiddle(event: LocEvents.Op1) {
        if (!inDonatorIsland(event.loc.coords)) {
            return
        }
        if (!player.isSuperDonator) {
            player.mes("The VIP layer is only available to Super Donators or higher.")
            return
        }
        teleport(CoordGrid(3810, 2845, 1))
    }

    private fun ProtectedAccess.climbUpper(event: LocEvents.Op1) {
        if (!inDonatorIsland(event.loc.coords)) {
            return
        }
        teleport(CoordGrid(3815, 2843, 2))
    }

    private fun ProtectedAccess.climbDown(event: LocEvents.Op1) {
        if (!inDonatorIsland(event.loc.coords)) {
            return
        }
        teleport(CoordGrid(3815, 2843, 1))
    }

    private fun ProtectedAccess.bossEntrance(event: LocEvents.Op1) {
        if (!inDonatorIsland(event.loc.coords)) {
            return
        }
        val left = bossKillsLeft(player)
        if (!player.isDonator || left <= 0) {
            player.mes("You have no donator boss kills left today.")
            return
        }
        vars[UnforgeDonatorVarps.bossKills] = vars[UnforgeDonatorVarps.bossKills] + 1
        teleport(CoordGrid(BOSS_AREA_X, BOSS_AREA_Z, 0))
    }

    private fun bossKillsLeft(player: Player): Int {
        val tier = player.donatorTier
        if (tier.bossKillsPerDay <= 0) {
            return 0
        }
        val today = todayEpoch()
        if (player.vars[UnforgeDonatorVarps.bossDay] != today) {
            setCwVarp(player, UnforgeDonatorVarps.bossDay, today)
            setCwVarp(player, UnforgeDonatorVarps.bossKills, 0)
        }
        return (tier.bossKillsPerDay - player.vars[UnforgeDonatorVarps.bossKills]).coerceAtLeast(
            0
        )
    }
}
