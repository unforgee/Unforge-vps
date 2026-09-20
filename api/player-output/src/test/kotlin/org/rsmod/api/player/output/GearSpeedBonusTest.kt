package org.rsmod.api.player.output

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.api.config.refs.params
import org.rsmod.api.testing.factory.objTypeFactory
import org.rsmod.game.type.TypeResolver
import org.rsmod.game.type.param.ParamType
import org.rsmod.game.type.util.ParamMapBuilder

public class GearSpeedBonusTest {
    @Test
    public fun `rune-class gear grants the one percent floor`() {
        assertEquals(GearSpeedBonus.MIN_BPS, GearSpeedBonus.bps(type(requirement = 40)))
        assertEquals(
            GearSpeedBonus.MIN_BPS,
            GearSpeedBonus.bps(type(internalName = "green_dhide_body", requirement = 40)),
        )
        assertEquals(
            GearSpeedBonus.MIN_BPS,
            GearSpeedBonus.bps(type(internalName = "yew_longbow", requirement = 40)),
        )
    }

    @Test
    public fun `end-game gear grants the seven percent cap`() {
        assertEquals(GearSpeedBonus.MAX_BPS, GearSpeedBonus.bps(type(requirement = 75)))
        assertEquals(GearSpeedBonus.MAX_BPS, GearSpeedBonus.bps(type(requirement = 80)))
        assertEquals(GearSpeedBonus.MAX_BPS, GearSpeedBonus.bps(type(requirement = 85)))
    }

    @Test
    public fun `mid-tier gear interpolates between the anchors`() {
        val mid = GearSpeedBonus.bps(type(requirement = 60))
        assertTrue(mid > GearSpeedBonus.MIN_BPS && mid < GearSpeedBonus.MAX_BPS, "bps=$mid")
    }

    @Test
    public fun `league items grant ten percent regardless of tier`() {
        assertEquals(
            GearSpeedBonus.LEAGUE_BPS,
            GearSpeedBonus.bps(type(internalName = "league_trailblazer_axe", requirement = 1)),
        )
        assertEquals(
            GearSpeedBonus.LEAGUE_BPS,
            GearSpeedBonus.bps(type(internalName = "trailblazer_reloaded_axe", requirement = 1)),
        )
        assertEquals(
            GearSpeedBonus.LEAGUE_BPS,
            GearSpeedBonus.bps(type(internalName = "echo_godsword", requirement = 75)),
        )
        assertEquals(
            GearSpeedBonus.LEAGUE_BPS,
            GearSpeedBonus.bps(type(internalName = "dinhs_bulwark_ornament", requirement = 75)),
        )
    }

    @Test
    public fun `requirement-less end-game gear is overridden`() {
        // Ava's assembler has no level requirement, so the score curve alone would leave it at 1%.
        assertEquals(
            GearSpeedBonus.MAX_BPS,
            GearSpeedBonus.bps(type(internalName = "avas_assembler")),
        )
        assertEquals(
            GearSpeedBonus.MAX_BPS,
            GearSpeedBonus.bps(type(internalName = "avas_assembler_masori")),
        )
    }

    @Test
    public fun `requirement-less low tier gear stays on the floor`() {
        assertEquals(
            GearSpeedBonus.MIN_BPS,
            GearSpeedBonus.bps(type(internalName = "ghostly_robe")),
        )
    }

    private fun type(internalName: String = "test_item", requirement: Int = 0) =
        objTypeFactory.create {
            this.internal = internalName
            name = internalName
            paramMap =
                ParamMapBuilder()
                    .apply {
                        for (param in GEAR_PARAMS) {
                            this[param] = 0
                        }
                        this[params.levelrequire] = requirement
                    }
                    .toParamMap()
        }

    private companion object {
        /**
         * Every param [GearSpeedBonus] reads, resolved to stable test ids because `ParamType.id` is
         * only populated by the real cache at runtime.
         */
        val GEAR_PARAMS: List<ParamType<Int>> =
            listOf(
                params.levelrequire,
                params.statreq1_level,
                params.statreq2_level,
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
            )

        init {
            GEAR_PARAMS.forEachIndexed { index, param -> TypeResolver[param] = index + 1 }
        }
    }
}
