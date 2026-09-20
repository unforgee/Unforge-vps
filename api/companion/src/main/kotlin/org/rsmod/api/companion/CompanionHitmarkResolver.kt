package org.rsmod.api.companion

import org.rsmod.api.config.refs.hitmark_groups
import org.rsmod.game.type.hitmark.HitmarkTypeGroup

public enum class CompanionHitmarkStyle {
    TANK,
    SUPPORT,
    DAMAGE,
    MAGIC,
    BOSS,
}

/**
 * Selects the visual hitmark for a companion attack without changing how the hit is calculated or
 * applied. The returned group is passed to the regular PvN hit builder by the combat adapter.
 * Unknown and legacy data deliberately falls back to the normal damage group.
 */
public object CompanionHitmarkResolver {
    public fun styleForCompanion(companion: Companion): CompanionHitmarkStyle =
        when (companion.companionClass) {
            CompanionClass.TANK -> CompanionHitmarkStyle.TANK
            CompanionClass.SUPPORT -> CompanionHitmarkStyle.SUPPORT
            CompanionClass.DPS -> CompanionHitmarkStyle.DAMAGE
        }.let { roleStyle ->
            if (companion.attackStyle == CompanionAttackStyle.MAGIC) CompanionHitmarkStyle.MAGIC
            else roleStyle
        }

    public fun forCompanion(companion: Companion): HitmarkTypeGroup =
        group(styleForCompanion(companion))

    public fun styleForAbility(companion: Companion, abilityId: String): CompanionHitmarkStyle {
        val definition =
            CompanionAbilityCatalog.byId[abilityId] ?: return styleForCompanion(companion)
        val effect =
            runCatching { CompanionAbilityEffects.effect(abilityId) }.getOrNull()
                ?: return styleForCompanion(companion)

        if (effect.type == CompanionAbilityEffectType.HEAL) return CompanionHitmarkStyle.SUPPORT
        if (definition.ultimate) return CompanionHitmarkStyle.BOSS
        return when (effect.type) {
            CompanionAbilityEffectType.DEBUFF -> CompanionHitmarkStyle.MAGIC
            CompanionAbilityEffectType.DAMAGE,
            CompanionAbilityEffectType.MOVEMENT -> styleForCompanion(companion)
            CompanionAbilityEffectType.BUFF,
            CompanionAbilityEffectType.DEFENSIVE,
            CompanionAbilityEffectType.TAUNT,
            CompanionAbilityEffectType.HEAL -> CompanionHitmarkStyle.SUPPORT
        }
    }

    public fun forAbility(companion: Companion, abilityId: String): HitmarkTypeGroup =
        group(styleForAbility(companion, abilityId))

    public fun group(style: CompanionHitmarkStyle): HitmarkTypeGroup =
        when (style) {
            CompanionHitmarkStyle.TANK -> hitmark_groups.shield_damage
            CompanionHitmarkStyle.SUPPORT -> hitmark_groups.heal
            CompanionHitmarkStyle.DAMAGE -> hitmark_groups.regular_damage
            CompanionHitmarkStyle.MAGIC -> hitmark_groups.venom
            CompanionHitmarkStyle.BOSS -> hitmark_groups.doom
        }
}
