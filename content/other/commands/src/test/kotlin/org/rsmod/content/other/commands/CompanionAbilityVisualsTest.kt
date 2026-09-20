package org.rsmod.content.other.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rsmod.api.combat.commons.ability.AbilityVisual
import org.rsmod.api.companion.CompanionAbilityCatalog
import org.rsmod.api.config.refs.spotanims

/**
 * Definition-level verification for the shared ability presentation layer: every *active* companion
 * ability and every role spell must resolve to a real, cache-named visual definition. This is the
 * audit's "no missing visuals / no guessed ids" guarantee - a missing entry fails the test, and
 * every referenced asset is a `find("name")` cache symbol, never a raw id.
 */
public class CompanionAbilityVisualsTest {

    @Test
    public fun `every active catalog ability has a presentation`() {
        val active = CompanionAbilityCatalog.definitions.filter { it.active }
        assertTrue(active.isNotEmpty())
        for (ability in active) {
            assertNotNull(
                CompanionAbilityVisuals.forTalentAbility(ability.id),
                "Active ability '${ability.id}' has no AbilityVisual definition",
            )
        }
    }

    @Test
    public fun `passive catalog abilities intentionally have no presentation`() {
        // Passive talents (active=false) are never cast, so they must not resolve a visual - a
        // returned value here would mean the catalog and the visual library disagree.
        for (ability in CompanionAbilityCatalog.definitions.filterNot { it.active }) {
            assertNull(CompanionAbilityVisuals.forTalentAbility(ability.id))
        }
    }

    @Test
    public fun `every role spell resolves a presentation`() {
        for ((role, spells) in RoleSpellCatalog.SPELLS_BY_ROLE) {
            assertTrue(spells.isNotEmpty(), "Role $role has no spells")
            for (spell in spells) {
                val visual = CompanionAbilityVisuals.forRoleSpell(spell)
                assertTrue(
                    visual.castSeq != null || visual.castSpot != null || visual.impactSpot != null,
                    "Spell ${spell.id} produced an empty visual",
                )
            }
        }
    }

    @Test
    public fun `spell tier mapping follows level requirements`() {
        fun spell(levelReq: Int) =
            RoleSpell(
                "t",
                "T",
                org.rsmod.api.companion.CompanionClass.DPS,
                RoleSpellType.DAMAGE_BURST,
                levelReq,
                "",
                1,
                1,
            )

        assertEquals(0, CompanionAbilityVisuals.tierOf(spell(1)))
        assertEquals(1, CompanionAbilityVisuals.tierOf(spell(20)))
        assertEquals(2, CompanionAbilityVisuals.tierOf(spell(40)))
        assertEquals(3, CompanionAbilityVisuals.tierOf(spell(60)))
        assertEquals(4, CompanionAbilityVisuals.tierOf(spell(80)))
    }

    @Test
    public fun `heal visuals carry a real projectile and escalate per tier`() {
        // Three sphere sizes cover five tiers: small -> med -> large as the spell tier climbs.
        for (tier in 0..4) {
            val visual = CompanionAbilityVisuals.healVisual(tier)
            assertTrue(visual.hasProjectile, "Heal tier $tier has no projectile")
            assertNotNull(visual.castSeq)
            assertNotNull(visual.impactSpot)
        }
        assertEquals(
            spotanims.leagues_5_gargboss_healing_sphere_large,
            CompanionAbilityVisuals.healVisual(4).travelSpot,
        )
        assertEquals(
            spotanims.gargboss_healing_sphere_small,
            CompanionAbilityVisuals.healVisual(0).travelSpot,
        )
    }

    @Test
    public fun `burst visuals use the authentic elemental spell assets`() {
        val visual = CompanionAbilityVisuals.burstVisual(4)
        assertEquals(spotanims.firesurge_casting, visual.castSpot)
        assertEquals(spotanims.firesurge_travel, visual.travelSpot)
        assertEquals(spotanims.firesurge_impact, visual.impactSpot)
        assertEquals(
            org.rsmod.api.combat.commons.ability.AbilityVisualOrigin.ORIGINAL,
            visual.origin,
        )
    }

    @Test
    public fun `projectile visuals require both model and trajectory`() {
        assertThrows<IllegalArgumentException> {
            AbilityVisual(
                travelSpot = spotanims.windstrike_travel,
                projanim = null,
                origin = org.rsmod.api.combat.commons.ability.AbilityVisualOrigin.CUSTOM,
            )
        }
        assertThrows<IllegalArgumentException> {
            AbilityVisual(
                travelSpot = null,
                projanim = org.rsmod.api.config.refs.projanims.magic_spell,
                origin = org.rsmod.api.combat.commons.ability.AbilityVisualOrigin.CUSTOM,
            )
        }
    }

    @Test
    public fun `all defined visuals are classified for the audit`() {
        val visuals =
            RoleSpellCatalog.SPELLS_BY_ROLE.values.flatten().map {
                CompanionAbilityVisuals.forRoleSpell(it)
            } +
                CompanionAbilityCatalog.definitions
                    .filter { it.active }
                    .mapNotNull { CompanionAbilityVisuals.forTalentAbility(it.id) }
        // Every visual is explicitly ORIGINAL / ADAPTED / CUSTOM - nothing silently defaults.
        for (visual in visuals) {
            assertNotNull(visual.origin)
        }
        // The library must contain at least one authentic reproduction (elemental bursts, curse
        // debuffs) so ORIGINAL is not an unused label.
        assertTrue(
            visuals.any {
                it.origin == org.rsmod.api.combat.commons.ability.AbilityVisualOrigin.ORIGINAL
            }
        )
    }
}
