package org.rsmod.content.other.commands

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.content.interfaces.stats.configs.stats_interfaces
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class StatsCommands
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val objTypes: ObjTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        playerCommand("stats", "Open the all-sources stats interface", ::stats)
        onCommand("agility", "Teleport to Gnome Course & give Graceful outfit", ::agility)
        onCommand("thieving", "Teleport to Ardougne Market & give Rogue gear", ::thieving)
        onCommand(
            "con",
            "Teleport to Rimmington house portal & give building supplies",
            ::construction,
        )
        onCommand(
            "poh",
            "Teleport to Rimmington house portal & give building supplies",
            ::construction,
        )
    }

    private fun stats(cheat: Cheat) =
        with(cheat) {
            protectedAccess.launch(player) { ifOpenMainModal(stats_interfaces.unforge_stats) }
        }

    private fun agility(cheat: Cheat) =
        with(cheat) {
            protectedAccess.launch(player) {
                telejump(CoordGrid(2474, 3436, 0)) // Gnome Agility Course start
                player.runEnergy = 10_000

                // Give Graceful outfit & Marks of Grace
                val items = listOf(11850, 11854, 11856, 11858, 11860, 11852, 11849)
                for (id in items) {
                    val type = objTypes[id] ?: continue
                    val count = if (id == 11849) 25 else 1
                    player.invAdd(player.inv, type, count, strict = false)
                }
                mes("Welcome to the Agility Course! Graceful equipment and Marks of Grace added.")
            }
        }

    private fun thieving(cheat: Cheat) =
        with(cheat) {
            protectedAccess.launch(player) {
                telejump(CoordGrid(2662, 3305, 0)) // Ardougne Market square

                // Give Rogue outfit, Dodgy necklace, lockpicks
                val items = listOf(5554, 5553, 5555, 5556, 5557, 21143, 1523)
                for (id in items) {
                    val type = objTypes[id] ?: continue
                    val count = if (id == 1523) 10 else 1
                    player.invAdd(player.inv, type, count, strict = false)
                }
                mes(
                    "Welcome to Ardougne Market! Rogue outfit, Dodgy necklace, and lockpicks added."
                )
            }
        }

    private fun construction(cheat: Cheat) =
        with(cheat) {
            protectedAccess.launch(player) {
                telejump(CoordGrid(2954, 3224, 0)) // Rimmington house portal

                // Give Saw, Hammer, Planks, Nails, Coins
                val supplies =
                    listOf(
                        8794 to 1, // Saw
                        2347 to 1, // Hammer
                        960 to 50, // Regular planks
                        8778 to 50, // Oak planks
                        8782 to 50, // Mahogany planks
                        1539 to 100, // Steel nails
                        995 to 100_000, // Coins
                    )
                for ((id, count) in supplies) {
                    val type = objTypes[id] ?: continue
                    player.invAdd(player.inv, type, count, strict = false)
                }
                mes("Welcome to Rimmington POH Portal! Building tools, planks, and coins added.")
            }
        }
}
