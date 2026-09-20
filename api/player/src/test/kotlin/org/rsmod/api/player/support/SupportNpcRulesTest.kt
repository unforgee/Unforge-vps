package org.rsmod.api.player.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.params
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.game.type.TypeResolver
import org.rsmod.game.type.param.ParamType
import org.rsmod.game.type.util.ParamMapBuilder

public class SupportNpcRulesTest {
    @Test
    public fun `an npc with no support params gets full support`() {
        val npc = npcTypeFactory.create { name = "bare_npc" }
        assertEquals(SupportNpcRules.FULL_MULTIPLIER, SupportNpcRules.supportMultiplier(npc))
        assertEquals(SupportNpcRules.FULL_MULTIPLIER, SupportNpcRules.healingMultiplier(npc))
        assertEquals(SupportNpcRules.FULL_MULTIPLIER, SupportNpcRules.specialMultiplier(npc))
        assertEquals(0, SupportNpcRules.tier(npc))
    }

    @Test
    public fun `a multiplier scales support proportionally`() {
        assertEquals(1_500, SupportNpcRules.scale(1_500, npc(support = 1_000)))
        assertEquals(750, SupportNpcRules.scale(1_500, npc(support = 500)))
        // A boss may allow only part of the support bonus.
        assertEquals(1_350, SupportNpcRules.scale(1_500, npc(support = 900)))
    }

    @Test
    public fun `zero multiplier blocks support entirely`() {
        assertEquals(0, SupportNpcRules.scale(1_500, npc(support = 0)))
        assertEquals(0, SupportNpcRules.healingMultiplier(npc(healing = 0)))
    }

    @Test
    public fun `beyond-full multipliers are clamped to full`() {
        // A data-entry mistake must never turn into a >100 % support multiplier.
        assertEquals(1_500, SupportNpcRules.scale(1_500, npc(support = 10_000)))
        assertEquals(1_500, SupportNpcRules.scale(1_500, npc(support = 99_999)))
    }

    @Test
    public fun `negative multipliers clamp to zero rather than inverting the effect`() {
        assertEquals(0, SupportNpcRules.scale(1_500, npc(support = -5_000)))
        assertEquals(0, SupportNpcRules.supportMultiplier(npc(support = -1)))
    }

    @Test
    public fun `scaling zero bps stays zero regardless of multiplier`() {
        for (multiplier in listOf(0, 500, 1_000, 10_000)) {
            assertEquals(0, SupportNpcRules.scale(0, npc(support = multiplier)))
        }
    }

    @Test
    public fun `tier is read back`() {
        assertEquals(2, SupportNpcRules.tier(npc(tier = 2)))
    }

    /** Builds a test npc; `null` leaves the param absent so its own default applies. */
    private fun npc(
        support: Int? = null,
        healing: Int? = null,
        special: Int? = null,
        tier: Int? = null,
    ) =
        npcTypeFactory.create {
            name = "test_npc"
            paramMap =
                ParamMapBuilder()
                    .apply {
                        support?.let { this[params.npc_support_multiplier] = it }
                        healing?.let { this[params.npc_support_healing_multiplier] = it }
                        special?.let { this[params.npc_support_special_multiplier] = it }
                        tier?.let { this[params.npc_pvm_tier] = it }
                    }
                    .toParamMap()
        }

    private companion object {
        val SUPPORT_PARAMS: List<ParamType<*>> =
            listOf(
                params.npc_support_multiplier,
                params.npc_support_healing_multiplier,
                params.npc_support_special_multiplier,
                params.npc_pvm_tier,
            )

        init {
            SUPPORT_PARAMS.forEachIndexed { index, param -> TypeResolver[param] = index + 1 }
        }
    }
}
