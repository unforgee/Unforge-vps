package org.rsmod.content.interfaces.iteminspect

import jakarta.inject.Inject
import java.util.Base64
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.config.refs.objs
import org.rsmod.api.equipment.instance.EquipmentAffixRoll
import org.rsmod.api.equipment.instance.EquipmentCategory
import org.rsmod.api.equipment.instance.EquipmentInstance
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.equipment.instance.EquipmentRarity
import org.rsmod.api.equipment.instance.EquipmentStat
import org.rsmod.api.equipment.instance.EquipmentTier
import org.rsmod.api.equipment.instance.ModifierPolarity
import org.rsmod.api.equipment.instance.ModifierUnit
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.inv.InvOpenScript
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.interact.WornInteractions
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.interfaces.iteminspect.configs.iteminspect_interfaces
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.Wearpos

/**
 * The examine-to-RuneLite-panel chain: a real `Examine` click on an inventory obj routes through
 * `HeldOpScript` -> `HeldInteractions.examine` -> [ObjExamineEvents.Examine] -> this script, which
 * streams `UNFORGE_INSPECT_BEGIN`/`UNFORGE_INSPECT_LINE`/`UNFORGE_INSPECT_END` console messages
 * carrying the authoritative post-affix stats plus per-ability explanations for the client's `Item
 * Instance Inspect` side panel.
 */
class ItemInspectScriptTest {
    class InspectDeps
    @Inject
    constructor(
        val instances: EquipmentInstanceRegistry,
        val heldInteractions: HeldInteractions,
        val wornInteractions: WornInteractions,
    )

    // Note: `EquipmentInstanceRegistry` is `@Singleton`-scoped, so the same just-in-time instance
    // is shared between the script under test and `InspectDeps` - instance registration is visible
    // to the inspect stream without a child module.

    /**
     * Opens the inventory interface in its real gameframe side slot so [InvOpenScript] registers
     * the runtime `IfEvent.Op10` (examine) mask on `inv_items` - dynamic sub-slot clicks are only
     * accepted when the op has been enabled via `ifSetEvents`.
     */
    private fun GameTestScope.openInventory() {
        player.ifOpenOverlay(interfaces.inventory, components.toplevel_target_side3, eventBus)
        advance()
    }

    private fun GameTestScope.examineHeld(slot: Int) {
        player.ifButton(components.inv_items, comsub = slot, op = IfButtonOp.Op10)
        advance()
    }

    private fun GameTestScope.consoleMessages(): List<String> = client.mapOf(MessageGame::message)

    /**
     * Decoded inspect lines in send order. Lines exceeding the `MessageGame` byte budget arrive as
     * `UNFORGE_INSPECT_PART` chunks terminated by `UNFORGE_INSPECT_PEND` and are reassembled
     * exactly like the client plugin does.
     */
    private fun GameTestScope.inspectLines(): List<String> {
        val lines = mutableListOf<String>()
        val pending = StringBuilder()
        for (message in consoleMessages()) {
            when {
                message.startsWith("UNFORGE_INSPECT_LINE|") ->
                    lines += dec(message.substringAfter('|'))
                message.startsWith("UNFORGE_INSPECT_PART|") ->
                    pending.append(message.substringAfter('|'))
                message == "UNFORGE_INSPECT_PEND" -> {
                    lines += dec(pending.toString())
                    pending.setLength(0)
                }
            }
        }
        return lines
    }

    private fun dec(encoded: String): String =
        String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)

    @Test
    fun GameTestState.`held weapon examine streams inspect lines without opening a modal`() =
        runGameTest(ItemInspectScript::class, HeldOpScript::class, InvOpenScript::class) {
            player.inv[0] = InvObj(objs.rune_axe)
            openInventory()
            examineHeld(0)

            assertModalNotOpen(iteminspect_interfaces.unforge_iteminspect)
            val messages = consoleMessages()
            assertTrue(messages.any { it.startsWith("UNFORGE_INSPECT_BEGIN|") }) {
                "BEGIN missing in: $messages"
            }
            assertTrue(messages.any { it == "UNFORGE_INSPECT_END" }) { "END missing in: $messages" }
            val begin = messages.first { it.startsWith("UNFORGE_INSPECT_BEGIN|") }
            val beginParts = begin.split('|')
            assertEquals(objs.rune_axe.id.toString(), beginParts[1]) { "obj id: $begin" }
            assertEquals("Rune axe", dec(beginParts[3])) { "name: $begin" }
            assertEquals("Base item", dec(beginParts[4])) { "subtitle: $begin" }

            val lines = inspectLines()
            assertTrue(lines.any { it.contains("attack:") }) { "Stat block missing: $lines" }
            assertTrue(lines.any { it.contains("no affixes") }) { "Base note missing: $lines" }
        }

    @Test
    fun GameTestState.`held armour examine streams inspect lines`() =
        runGameTest(ItemInspectScript::class, HeldOpScript::class, InvOpenScript::class) {
            player.inv[2] = InvObj(objs.rune_platebody)
            openInventory()
            examineHeld(2)

            val messages = consoleMessages()
            val begin = messages.firstOrNull { it.startsWith("UNFORGE_INSPECT_BEGIN|") }
            assertTrue(begin != null && dec(begin.split('|')[3]) == "Rune platebody") {
                "BEGIN missing: $messages"
            }
        }

    @Test
    fun GameTestState.`non-equipment examine keeps chat-only output`() =
        runGameTest(ItemInspectScript::class, HeldOpScript::class, InvOpenScript::class) {
            player.inv[0] = InvObj(objs.beer)
            openInventory()
            examineHeld(0)

            assertTrue(consoleMessages().none { it.startsWith("UNFORGE_INSPECT_") }) {
                "Inspect stream emitted for non-equipment: ${consoleMessages()}"
            }
        }

    @Test
    fun GameTestState.`worn equipment examine streams inspect lines`() =
        runInjectedGameTest(InspectDeps::class, null, ItemInspectScript::class) { deps ->
            player.worn[Wearpos.Torso.slot] = InvObj(objs.rune_platebody)
            // `examine` dispatches the event synchronously - the messages land in the capture
            // buffer immediately (an `advance` here would wipe them before the tick).
            deps.wornInteractions.examine(player, player.worn, Wearpos.Torso.slot)

            val messages = consoleMessages()
            assertTrue(messages.any { it.startsWith("UNFORGE_INSPECT_BEGIN|") }) {
                "BEGIN missing in: $messages"
            }
        }

    @Test
    fun GameTestState.`instanced item streams affix-effective stats and exact ability text`() =
        runInjectedGameTest(InspectDeps::class, null, ItemInspectScript::class) { deps ->
            val instance =
                EquipmentInstance(
                    instanceId = 42L,
                    templateObj = objs.rune_axe.id,
                    category = EquipmentCategory.RightHand,
                    rarity = EquipmentRarity.Rare,
                    rollSeed = 1L,
                    affixes =
                        listOf(
                            EquipmentAffixRoll(
                                slot = 0,
                                definitionId = "test-stab",
                                family = "keen",
                                stat = EquipmentStat.AttackStab,
                                unit = ModifierUnit.Flat,
                                polarity = ModifierPolarity.Boon,
                                magnitude = 7,
                            )
                        ),
                    sockets = emptyList(),
                    uniqueEffectIds = listOf("spec-energy-drain"),
                    source = "test",
                    tier = EquipmentTier.Rune,
                    itemLevel = 10,
                    quality = 95,
                )
            deps.instances.put(instance)

            player.inv[0] = InvObj(objs.rune_axe, instanceId = 42L)
            deps.heldInteractions.examine(player, player.inv, 0)

            val messages = consoleMessages()
            val begin = messages.firstOrNull { it.startsWith("UNFORGE_INSPECT_BEGIN|") }
            assertTrue(begin != null) { "BEGIN missing in: $messages" }
            val beginParts = begin!!.split('|')
            assertEquals("42", beginParts[2]) { "instance id: $begin" }
            assertTrue(dec(beginParts[4]).contains("Instance #42")) { "subtitle: $begin" }

            val lines = inspectLines()
            assertTrue(lines.any { it.contains("Instance #42") }) { "Header missing: $lines" }
            assertTrue(lines.any { it.contains("Attack Stab") && it.contains("+7") }) {
                "Affix roll missing: $lines"
            }
            // `spec-energy-drain` resolves to the lifesteal bucket: heal 12% of dealt damage.
            assertTrue(lines.any { it.contains("Energy Drain") && it.contains("chance") }) {
                "Ability name line missing: $lines"
            }
            assertTrue(lines.any { it.contains("heal 12.0% of damage dealt") }) {
                "Ability explanation missing: $lines"
            }
        }
}
