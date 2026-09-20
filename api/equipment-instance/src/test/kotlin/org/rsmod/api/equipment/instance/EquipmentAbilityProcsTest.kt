package org.rsmod.api.equipment.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class EquipmentAbilityProcsTest {
    private fun statusOf(proc: AbilityProc, status: AbilityStatus): AbilityStatusProc? =
        proc.statuses.firstOrNull { it.status == status }

    @Test
    public fun `venom abilities apply venom`() {
        val proc = EquipmentAbilityProcs.procFor("boss-venom-spray-venenatis")
        assertTrue(statusOf(proc, AbilityStatus.Venom) != null)
        // The original attack is a ranged spray - keep the bonus strike too.
        assertEquals(AbilityStyle.Ranged, proc.strikeStyle)
    }

    @Test
    public fun `venom cloud is both venomous and area-of-effect`() {
        val proc = EquipmentAbilityProcs.procFor("boss-venom-cloud-zulrah")
        assertTrue(statusOf(proc, AbilityStatus.Venom) != null)
        // Boss area attacks reach one tile further than regular ones.
        assertEquals(2, proc.aoeRadius)
        assertTrue(proc.aoeDamageBps > 0)
        assertTrue(proc.hasOnHitEffects)
    }

    @Test
    public fun `poison abilities apply poison`() {
        assertTrue(
            statusOf(
                EquipmentAbilityProcs.procFor("boss-poison-sac-scorpia"),
                AbilityStatus.Poison,
            ) != null
        )
        assertTrue(
            statusOf(
                EquipmentAbilityProcs.procFor("boss-acid-drip-great-olm"),
                AbilityStatus.Poison,
            ) != null
        )
    }

    @Test
    public fun `poison slam keeps its melee strike and poisons`() {
        val proc = EquipmentAbilityProcs.procFor("boss-poison-slam-kril-tsutsaroth")
        assertEquals(AbilityStyle.Melee, proc.strikeStyle)
        assertTrue(statusOf(proc, AbilityStatus.Poison) != null)
    }

    @Test
    public fun `burn abilities apply burn`() {
        assertTrue(
            statusOf(
                EquipmentAbilityProcs.procFor("boss-lava-eruption-cerberus"),
                AbilityStatus.Burn,
            ) != null
        )
        assertTrue(
            statusOf(
                EquipmentAbilityProcs.procFor("boss-dragonfire-barrage-vorkath"),
                AbilityStatus.Burn,
            ) != null
        )
        assertTrue(
            statusOf(
                EquipmentAbilityProcs.procFor("boss-flame-wall-tztok-jad"),
                AbilityStatus.Burn,
            ) != null
        )
    }

    @Test
    public fun `freeze abilities apply freeze`() {
        assertTrue(
            statusOf(EquipmentAbilityProcs.procFor("boss-ice-prison-nex"), AbilityStatus.Freeze) !=
                null
        )
        assertTrue(
            statusOf(
                EquipmentAbilityProcs.procFor("boss-web-snare-venenatis"),
                AbilityStatus.Freeze,
            ) != null
        )
        assertTrue(
            statusOf(
                EquipmentAbilityProcs.procFor("boss-frozen-wake-vorkath"),
                AbilityStatus.Freeze,
            ) != null
        )
    }

    @Test
    public fun `ice barrage snare freezes and splashes like a real barrage`() {
        val proc = EquipmentAbilityProcs.procFor("boss-ice-barrage-snare-vorkath")
        assertTrue(statusOf(proc, AbilityStatus.Freeze) != null)
        assertTrue(proc.aoeRadius > 0)
        assertEquals(AbilityStyle.Magic, proc.strikeStyle)
    }

    @Test
    public fun `aoe abilities splash onto nearby targets`() {
        val proc = EquipmentAbilityProcs.procFor("boss-feather-storm-kreearra")
        assertTrue(proc.aoeRadius > 0)
        assertTrue(proc.aoeDamageBps > 0)
    }

    @Test
    public fun `lifesteal abilities still only heal`() {
        val proc = EquipmentAbilityProcs.procFor("boss-blood-siphon-nex")
        assertTrue(proc.onHitHealBps > 0)
        assertTrue(proc.statuses.isEmpty())
    }

    @Test
    public fun `simple strike abilities have no status riders`() {
        val proc = EquipmentAbilityProcs.procFor("spec-crystal-pulse")
        assertTrue(proc.statuses.isEmpty())
        assertEquals(0, proc.aoeRadius)
    }

    @Test
    public fun `cap merges duplicate statuses keeping strongest`() {
        val proc =
            EquipmentAbilityProcs.cap(
                AbilityProc(
                    statuses =
                        listOf(
                            AbilityStatusProc(AbilityStatus.Poison, potency = 4, cycles = 100),
                            AbilityStatusProc(AbilityStatus.Poison, potency = 8, cycles = 60),
                            AbilityStatusProc(AbilityStatus.Vulnerable, stacks = 2, cycles = 30),
                            AbilityStatusProc(AbilityStatus.Vulnerable, stacks = 9, cycles = 20),
                        ),
                    aoeRadius = 9,
                    aoeDamageBps = 50_000,
                )
            )
        assertEquals(2, proc.statuses.size)
        val poison = statusOf(proc, AbilityStatus.Poison)!!
        assertEquals(8, poison.potency)
        assertEquals(100, poison.cycles)
        assertEquals(5, statusOf(proc, AbilityStatus.Vulnerable)!!.stacks)
        assertEquals(5, proc.aoeRadius)
        assertEquals(10_000, proc.aoeDamageBps)
    }

    @Test
    public fun `describe lists ailment and aoe riders`() {
        val venom = EquipmentAbilityProcs.describe("boss-venom-cloud-zulrah")
        assertTrue(venom.contains("venom", ignoreCase = true))
        assertTrue(venom.contains("tiles", ignoreCase = true))
        val freeze = EquipmentAbilityProcs.describe("boss-ice-prison-nex")
        assertTrue(freeze.contains("freeze", ignoreCase = true))
    }
}
