package org.rsmod.api.player.output

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.api.config.refs.params
import org.rsmod.api.equipment.instance.EquipmentAffixRoll
import org.rsmod.api.equipment.instance.EquipmentCategory
import org.rsmod.api.equipment.instance.EquipmentInstance
import org.rsmod.api.equipment.instance.EquipmentRarity
import org.rsmod.api.equipment.instance.EquipmentSocket
import org.rsmod.api.equipment.instance.EquipmentStat
import org.rsmod.api.equipment.instance.ModifierPolarity
import org.rsmod.api.equipment.instance.ModifierUnit
import org.rsmod.api.testing.factory.objTypeFactory
import org.rsmod.game.type.TypeResolver
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.game.type.param.ParamType
import org.rsmod.game.type.util.ParamMap
import org.rsmod.game.type.util.ParamMapBuilder

public class EquipmentInstanceDescribeTest {
    @Test
    public fun `broadcast line matches the client hover plugin contract`() {
        val line = EquipmentInstanceDescribe.broadcast(4151, whipInstance())
        val regex =
            Regex(
                "^ForgeInstance item=(\\d+) instance=(\\d+) rarity=([^ ]+) " +
                    "tier=([^ ]+) ilvl=(\\d+) quality=(\\d+) upgrade=(\\d+) " +
                    "mystery=([^ ]+) mysteryTier=([^ ]+) mysteryKills=(\\d+)/(\\d+)$"
            )
        val match = regex.matchEntire(line)
        requireNotNull(match) { "Broadcast line does not match client contract: $line" }
        assertEquals("4151", match.groupValues[1])
        assertEquals("42", match.groupValues[2])
        assertEquals("Rare", match.groupValues[3])
        assertEquals("Bronze", match.groupValues[4])
        assertEquals("1", match.groupValues[5])
        assertEquals("100", match.groupValues[6])
        assertEquals("0", match.groupValues[7])
        assertEquals("NONE", match.groupValues[8])
        assertEquals("NONE", match.groupValues[9])
        assertEquals("0", match.groupValues[10])
        assertEquals("0", match.groupValues[11])
    }

    @Test
    public fun `describe emits the capture protocol and effective stats`() {
        // Colour tags are display markup - the capture contract works on the plain text.
        val lines = EquipmentInstanceDescribe.describe(whip(), whipInstance()).map(::stripTags)

        // First line arms the client-side per-slot capture.
        assertTrue(lines.first().startsWith("Instance #42"), lines.first())

        // Every line the hover should display must use a captured prefix.
        val capturedPrefixes =
            listOf("Instance #", "Affix", "Sockets:", "Unique effects:", "Forge:", "Source:")
        for (line in lines) {
            assertTrue(
                capturedPrefixes.any(line::startsWith),
                "Line is invisible to the hover plugin: $line",
            )
        }

        // Flat +3 attack-stab affix on a 90 base must produce the effective +93.
        assertTrue(lines.any { it.contains("+93 stab") }, lines.joinToString("\n"))
        assertTrue(lines.any { it.startsWith("Affix Keen: Attack Stab +3") })
        assertTrue(lines.any { it.startsWith("Sockets: Universal") })
        assertTrue(lines.any { it.startsWith("Source: test") })
    }

    @Test
    public fun `basis-points affix scales the item base stat`() {
        val instance =
            whipInstance()
                .copy(
                    rarity = EquipmentRarity.Rare,
                    affixes =
                        listOf(
                            EquipmentAffixRoll(
                                slot = 0,
                                definitionId = "stab-power",
                                family = "attack-stab",
                                stat = EquipmentStat.AttackStab,
                                unit = ModifierUnit.BasisPoints,
                                polarity = ModifierPolarity.Boon,
                                magnitude = 1_000, // +10%
                            )
                        ),
                )
        val lines = EquipmentInstanceDescribe.describe(whip(), instance).map(::stripTags)
        // 90 base + 10% = 99.
        assertTrue(lines.any { it.contains("+99 stab") }, lines.joinToString("\n"))
    }

    private fun whip() =
        objTypeFactory.create(id = 4151) {
            name = "Abyssal whip"
            wearpos1 = Wearpos.RightHand.slot
            paramMap = buildParams {
                for (param in DESCRIBE_PARAMS) {
                    this[param] = 0
                }
                this[params.attack_stab] = 90
                this[params.melee_strength] = 82
            }
        }

    private fun whipInstance() =
        EquipmentInstance(
            instanceId = 42L,
            templateObj = 4151,
            category = EquipmentCategory.RightHand,
            rarity = EquipmentRarity.Rare,
            rollSeed = 239L,
            affixes =
                listOf(
                    EquipmentAffixRoll(
                        slot = 0,
                        definitionId = "keen",
                        family = "keen",
                        stat = EquipmentStat.AttackStab,
                        unit = ModifierUnit.Flat,
                        polarity = ModifierPolarity.Boon,
                        magnitude = 3,
                    )
                ),
            sockets = listOf(EquipmentSocket(0, "Universal")),
            uniqueEffectIds = emptyList(),
            source = "test",
        )

    private fun buildParams(init: ParamMapBuilder.() -> Unit): ParamMap =
        ParamMapBuilder().apply(init).toParamMap()

    private companion object {
        private val COL_TAG = Regex("</?col=[0-9a-fA-F]{6}>|</col>")

        fun stripTags(line: String): String = line.replace(COL_TAG, "")

        /**
         * Every param [EquipmentInstanceDescribe] reads. Resolved to stable test ids because
         * `ParamType.id`/`typedDefault` are only populated by the real cache at runtime.
         *
         * The three requirement params feed the gear attack-speed bonus ([GearSpeedBonus]).
         */
        val DESCRIBE_PARAMS: List<ParamType<Int>> =
            listOf(
                params.attack_stab,
                params.attack_slash,
                params.attack_crush,
                params.attack_magic,
                params.attack_ranged,
                params.defence_stab,
                params.defence_slash,
                params.defence_crush,
                params.defence_magic,
                params.defence_ranged,
                params.melee_strength,
                params.ranged_strength,
                params.magic_damage,
                params.item_prayer_bonus,
                params.levelrequire,
                params.statreq1_level,
                params.statreq2_level,
            )

        init {
            DESCRIBE_PARAMS.forEachIndexed { index, param -> TypeResolver[param] = index + 1 }
        }
    }
}
