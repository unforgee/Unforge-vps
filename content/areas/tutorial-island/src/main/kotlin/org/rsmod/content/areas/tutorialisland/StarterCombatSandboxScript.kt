package org.rsmod.content.areas.tutorialisland

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.invs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.areas.tutorialisland.configs.tutorial_varps
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.game.type.inv.InvStackType
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Converts a brand-new account into the local standalone combat sandbox.
 *
 * The starter package is resolved by cache internal name instead of handwritten numeric ids. This
 * keeps the package tied to the loaded revision-239 cache while preventing League/end-game objects
 * from leaking into a fresh account's bank.
 */
@OptIn(InternalApi::class)
class StarterCombatSandboxScript
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val statTypes: StatTypeList,
    private val invTypes: InvTypeList,
) : PluginScript() {
    private var Player.newAccount by boolVarBit(varbits.new_player_account)
    private var Player.tutCompleted by boolVarp(tutorial_varps.tut2_completed)
    private var Player.specialAttackEnergy by intVarp(varps.sa_energy)

    override fun ScriptContext.startup() {
        onPlayerLogin { player.applyCombatSandbox() }
    }

    private fun Player.applyCombatSandbox() {
        if (newAccount) {
            // Must be set before EngineLoginReady, otherwise TutorialIslandBootstrap opens its
            // modal.
            tutCompleted = true
            setStartingSkills()
            specialAttackEnergy = constants.sa_max_energy
            seedStarterLoadout()

            // The account is now initialized. This also makes the grant idempotent across
            // reconnects.
            newAccount = false
        }
    }

    /**
     * Sets every skill to its starting level: all stats at base level 1, hitpoints and herblore
     * at 10.
     *
     * This mirrors the vanilla OSRS new-account experience rather than granting max levels, with a
     * small herblore head start so the banked supplies are usable immediately. The
     * `InitialStatsScript` (which fires on `onPlayerInit`) already seeds hitpoints to its
     * `minLevel` (10); this method ensures the full stat map is consistent on the standalone combat
     * sandbox login path.
     */
    private fun Player.setStartingSkills() {
        var updated = 0
        for (stat in statTypes.values) {
            if (stat.unreleased) {
                continue
            }
            val level = if (stat.isType(stats.herblore)) HERBLORE_START_LEVEL else stat.minLevel
            statMap.setFineXP(stat, PlayerSkillXPTable.getFineXPFromLevel(level))
            statMap.setCurrentLevel(stat, level.toByte())
            statMap.setBaseLevel(stat, level.toByte())
            markStatUpdate(stat)
            updated++
        }
        appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(this)
        PlayerInterfaceUpdates.updateCombatLevel(this)
        logger.info {
            "Combat sandbox stats initialized for $username: $updated stats at starting levels"
        }
    }

    private fun Player.seedStarterLoadout() {
        val inventory = inv
        inventory.fillNulls()
        val inventoryAdded = addStarterItems(inventory, STARTER_INVENTORY)

        // Lobsters are not stackable: they fill whatever inventory slots remain after the gear
        // and the rest is banked, so the account still receives the full amount.
        val freeSlots = inventory.indices.count { inventory[it] == null }
        val invLobsters = minOf(STARTER_LOBSTERS, freeSlots)
        addStarterItems(inventory, listOf(StarterItem("lobster", invLobsters)))

        val bank = invMap.getOrPut(invTypes[invs.bank])
        val bankAdded = addStarterItems(bank, STARTER_BANK)
        if (invLobsters < STARTER_LOBSTERS) {
            val banked = STARTER_LOBSTERS - invLobsters
            addStarterItems(bank, listOf(StarterItem("lobster", banked)))
        }

        logger.info {
            "Starter loadout initialized for $username: " +
                "inventory=$inventoryAdded(+${invLobsters} lobster) bank=$bankAdded"
        }
    }

    private fun addStarterItems(container: Inventory, items: List<StarterItem>): Int {
        var added = 0
        for (item in items) {
            val type = objTypes.values.firstOrNull { it.internalName == item.internalName }
            if (type == null) {
                logger.warn { "Starter item missing from cache: ${item.internalName}" }
                continue
            }

            // A stack of 10 lobsters in one slot is not a stack the client or the eat op can work
            // with. Only the container's own stack rule and the obj's stackability may merge a
            // count; everything else takes one slot per item.
            val stacks = container.type.stack == InvStackType.Always || type.isStackable
            val counts = if (stacks) listOf(item.count) else List(item.count) { 1 }
            val slots = container.indices.filter { container[it] == null }
            if (slots.size < counts.size) {
                logger.warn { "Starter item does not fit: ${item.internalName} x${item.count}" }
                continue
            }

            for ((index, count) in counts.withIndex()) {
                container[slots[index]] = InvObj(type, count)
            }
            added++
        }
        return added
    }

    private data class StarterItem(val internalName: String, val count: Int)

    private companion object {
        private val logger = InlineLogger()
        private const val HERBLORE_START_LEVEL = 10
        private const val STARTER_LOBSTERS = 50
        private val STARTER_INVENTORY =
            listOf(
                StarterItem("tripo_dawnsteel_armor", 1),
                StarterItem("tripo_melee_weapon", 1),
                StarterItem("tripo_range_set_bow", 1),
                StarterItem("bronze_arrow", 100),
                StarterItem("tripo_magic_set_staff", 1),
                StarterItem("mindrune", 100),
                StarterItem("4doseprayerrestore", 2),
                StarterItem("coins", 1_000),
            )
        private val STARTER_BANK =
            listOf(
                StarterItem("iron_axe", 1),
                StarterItem("iron_pickaxe", 1),
                StarterItem("hammer", 1),
                StarterItem("tinderbox", 1),
                StarterItem("knife", 1),
                StarterItem("net", 1),
                StarterItem("big_net", 1),
                StarterItem("fishing_rod", 1),
                StarterItem("fly_fishing_rod", 1),
                StarterItem("harpoon", 1),
                StarterItem("lobster_pot", 1),
                StarterItem("fishing_bait", 100),
                StarterItem("feather", 100),
                StarterItem("needle", 1),
                StarterItem("thread", 10),
                StarterItem("shears", 1),
                StarterItem("chisel", 1),
                StarterItem("glassblowingpipe", 1),
                StarterItem("amulet_mould", 1),
                StarterItem("necklace_mould", 1),
                StarterItem("ring_mould", 1),
                StarterItem("jewl_bracelet_mould", 1),
                StarterItem("tiara_mould", 1),
                StarterItem("poh_saw", 1),
                StarterItem("rake", 1),
                StarterItem("dibber", 1),
                StarterItem("secateurs", 1),
                StarterItem("spade", 1),
                StarterItem("watering_can_8", 1),
                StarterItem("noose_wand", 1),
                StarterItem("hunting_ojibway_bird_snare", 1),
                StarterItem("hunting_box_trap", 1),
                StarterItem("hunting_butterfly_net", 1),
                StarterItem("ii_impling_jar", 3),
                StarterItem("bucket_empty", 1),
                StarterItem("jug_empty", 1),
                StarterItem("pot_empty", 1),
                StarterItem("bowl_empty", 1),
                StarterItem("pestle_and_mortar", 1),
                StarterItem("vial_water", 25),
                StarterItem("guam_leaf", 10),
                StarterItem("marentill", 10),
                StarterItem("tarromin", 10),
                StarterItem("eye_of_newt", 10),
                StarterItem("unicorn_horn_dust", 10),
                StarterItem("limpwurt_root", 10),
                StarterItem("snape_grass", 10),
                StarterItem("red_spiders_eggs", 5),
            )
    }
}
