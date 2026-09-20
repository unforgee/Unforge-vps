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
import org.rsmod.api.equipment.instance.SkillAffixRoll
import org.rsmod.api.testing.factory.objTypeFactory
import org.rsmod.game.type.TypeResolver
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.param.ParamType
import org.rsmod.game.type.util.ParamMap
import org.rsmod.game.type.util.ParamMapBuilder

public class EquipmentInstanceHoverSyncTest {
    @Test
    public fun `small payload is emitted as a single upsert`() {
        val payload = EquipmentInstanceHoverSync.encode(93, 2, whip(), 700, smallInstance())
        val messages = EquipmentInstanceHoverSync.wireMessages(payload)
        assertEquals(1, messages.size)
        assertEquals(payload, messages.single())
        assertTrue(messages.single().toByteArray().size <= 240)
    }

    @Test
    public fun `oversized payload splits into head upsert plus section chunks`() {
        val payload = EquipmentInstanceHoverSync.encode(93, 2, whip(), 700, jackpotInstance())
        assertTrue(payload.toByteArray().size > 240, "Fixture must exceed the message budget")

        val messages = EquipmentInstanceHoverSync.wireMessages(payload)
        assertTrue(messages.size > 1)
        for (message in messages) {
            assertTrue(
                message.toByteArray().size <= 240,
                "Wire message exceeds the budget: $message",
            )
        }

        // The head upsert keeps the envelope and carries every section as EMPTY.
        val head = messages.first().split("|")
        assertEquals("UNFORGE_ITEM_INSTANCE", head[0])
        assertEquals("3", head[1])
        assertEquals("upsert", head[2])
        assertEquals("93", head[3])
        assertEquals("2", head[4])
        assertEquals(jackpotInstance().instanceId.toString(), head[5])
        assertEquals("4151", head[6])
        assertEquals("Jackpot", head[8])
        assertEquals("700", head[16])
        // The head upsert still carries the stats tail - only the four large
        // sections are chunked into `sect` messages.
        assertEquals(JACKPOT_STATS, head[17])
        assertEquals(listOf("-", "-", "-", "-"), head.subList(12, 16))

        // Every non-empty section must arrive as `sect` chunks that reassemble to
        // the original section data, in canonical order.
        val sections = mutableMapOf<String, MutableList<Pair<Int, String>>>()
        for (message in messages.drop(1)) {
            val parts = message.split("|")
            assertEquals("sect", parts[2])
            assertEquals("93", parts[3])
            assertEquals("2", parts[4])
            assertEquals("4151", parts[5])
            sections.getOrPut(parts[6]) { mutableListOf() }.add(parts[7].toInt() to parts[9])
        }
        val original = payload.split("|")
        val expected =
            mapOf(
                "affixes" to original[12],
                "sockets" to original[13],
                "abilities" to original[14],
                "skillaffixes" to original[15],
            )
        for ((name, data) in expected) {
            val chunks = sections.getValue(name).sortedBy(Pair<Int, String>::first)
            assertEquals(data, chunks.joinToString(",") { it.second }, "section=$name")
        }
    }

    @Test
    public fun `v4 evolution envelope carries revision, uuid, state, binding and fingerprint`() {
        val instance =
            smallInstance()
                .copy(
                    revision = 7L,
                    instanceUuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                    state = org.rsmod.api.equipment.instance.EquipmentInstanceState.ACTIVE,
                    binding = org.rsmod.api.equipment.instance.ItemBinding.CHARACTER_BOUND,
                    ownerCharacterId = 99L,
                    fingerprint = "0123456789abcdef0123456789abcdef",
                )
        val parts = EquipmentInstanceHoverSync.evolutionEnvelope(instance).split("|")
        assertEquals(
            listOf(
                "UNFORGE_ITEM_INSTANCE",
                "4",
                "upsert",
                instance.instanceId.toString(),
                "7",
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                "ACTIVE",
                "CHARACTER_BOUND",
                "01234567",
            ),
            parts,
        )
        // An unstamped legacy instance degrades to safe placeholders, never garbage.
        val legacy = EquipmentInstanceHoverSync.evolutionEnvelope(smallInstance()).split("|")
        assertEquals("-", legacy[5])
        assertEquals("-", legacy[8])
        assertEquals("UNBOUND", legacy[7])
    }

    @Test
    public fun `plain wearable keeps zero instance and emits no sections`() {
        val payload = EquipmentInstanceHoverSync.encode(93, 5, legs(), 100, null)
        val messages = EquipmentInstanceHoverSync.wireMessages(payload)
        assertEquals(1, messages.size)
        val parts = payload.split("|")
        assertEquals("0", parts[5])
        assertEquals("11832", parts[6])
        assertEquals("100", parts[16])
        // Base stats ride along even without an instance so the hover can show
        // the total stat block of plain equipment.
        assertEquals(LEGS_STATS, parts[17])
    }

    private fun equipment(id: Int, init: ParamMapBuilder.() -> Unit): UnpackedObjType =
        objTypeFactory.create(id = id) {
            paramMap = buildParams {
                for (param in STAT_PARAMS) {
                    this[param] = 0
                }
                init()
            }
        }

    private fun whip() = equipment(4151) { this[params.attack_stab] = 90 }

    private fun legs() =
        equipment(11832) {
            this[params.attack_stab] = 90
            this[params.melee_strength] = 82
        }

    private fun buildParams(init: ParamMapBuilder.() -> Unit): ParamMap =
        ParamMapBuilder().apply(init).toParamMap()

    private fun smallInstance() =
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

    private fun jackpotInstance() =
        EquipmentInstance(
            instanceId = 123_456L,
            templateObj = 4151,
            category = EquipmentCategory.RightHand,
            rarity = EquipmentRarity.Jackpot,
            rollSeed = 239L,
            affixes =
                List(5) { index ->
                    EquipmentAffixRoll(
                        slot = index,
                        definitionId = "jackpot-power-$index",
                        family = "jackpot-family-$index",
                        stat = EquipmentStat.AttackStab,
                        unit = ModifierUnit.BasisPoints,
                        polarity = ModifierPolarity.Boon,
                        magnitude = 250 + index,
                    )
                },
            sockets =
                listOf(
                    EquipmentSocket(0, "Universal", socketedObj = 1603, magnitude = 3),
                    EquipmentSocket(1, "Weapon", socketedObj = null, magnitude = 0),
                ),
            uniqueEffectIds = listOf("jackpot-surge", "executioner", "overdrive"),
            skillAffixes =
                listOf(
                    SkillAffixRoll(
                        slot = 0,
                        skill = "ALL",
                        effect = "NOTED_CHANCE",
                        unit = ModifierUnit.BasisPoints,
                        magnitude = 150,
                    )
                ),
            source = "test",
        )

    private companion object {
        /**
         * `whip()` base attack stab (90) after the five compounding `BasisPoints` stab affixes of
         * [jackpotInstance] (`90 -> 92 -> 94 -> 96 -> 98 -> 100`).
         */
        const val JACKPOT_STATS = "100,0,0,0,0,0,0,0,0,0,0,0,0,0"

        /** `legs()` base params: attack stab 90, melee strength 82, everything else zero. */
        const val LEGS_STATS = "90,0,0,0,0,0,0,0,0,0,82,0,0,0"

        /**
         * Every param [EquipmentInstanceDescribe.totalStats] reads. Resolved to stable test ids
         * because `ParamType.id` is only populated by the real cache at runtime.
         */
        val STAT_PARAMS: List<ParamType<Int>> =
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
            )

        init {
            STAT_PARAMS.forEachIndexed { index, param -> TypeResolver[param] = index + 1 }
        }
    }
}
