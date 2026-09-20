package org.rsmod.content.pvmsupport

/**
 * The PvM support spellbook's spell list.
 *
 * Every spell is deliberately strong: the project's npcs are tuned above vanilla OSRS, so a 5 %
 * buff would be meaningless. Cooldowns are stored in **game ticks** (1 tick = 0.6 s) and never as a
 * client-side timer; [minCooldownTicks] is the floor that cooldown-reduction affixes can never
 * break through.
 */
public enum class SupportSpell(
    public val displayName: String,
    public val levelReq: Int,
    public val baseCooldownTicks: Int,
    public val minCooldownTicks: Int,
    public val category: SupportCooldownCategory,
    public val effect: SupportEffect,
    public val iconName: String,
) {
    /** Big self-heal on a long cooldown. 30 s base, 15 s floor. */
    MendingLight(
        displayName = "Mending Light",
        levelReq = 80,
        baseCooldownTicks = 50,
        minCooldownTicks = 25,
        category = SupportCooldownCategory.Healing,
        effect = SupportEffect.SelfHeal(percent = 35),
        iconName = "undead_support_09.png",
    ),

    /**
     * Cheap chip-damage heal. 15 s base, 8 s floor - intentionally not a replacement for Mending
     * Light.
     */
    EmergencyMend(
        displayName = "Emergency Mend",
        levelReq = 60,
        baseCooldownTicks = 25,
        minCooldownTicks = 14,
        category = SupportCooldownCategory.Healing,
        effect = SupportEffect.SelfHeal(percent = 12),
        iconName = "human_support_02.png",
    ),

    /** Group heal around the caster. 45 s base, 25 s floor. */
    SanctuaryPulse(
        displayName = "Sanctuary Pulse",
        levelReq = 85,
        baseCooldownTicks = 75,
        minCooldownTicks = 42,
        category = SupportCooldownCategory.Healing,
        effect = SupportEffect.PartyHeal(percent = 20, radius = 7, maxTargets = 8),
        iconName = "arcane_support_08.png",
    ),

    /** Emergency heal plus a short absorption shield. 90 s base, 45 s floor. */
    GuardiansGrace(
        displayName = "Guardian's Grace",
        levelReq = 90,
        baseCooldownTicks = 150,
        minCooldownTicks = 75,
        category = SupportCooldownCategory.Healing,
        effect =
            SupportEffect.HealAndShield(healPercent = 25, shieldPercent = 10, shieldTicks = 17),
        iconName = "elf_support_05.png",
    ),

    /** PvM damage + accuracy buff for the caster and nearby allies. 60 s base, 30 s floor. */
    BattleHymn(
        displayName = "Battle Hymn",
        levelReq = 92,
        baseCooldownTicks = 100,
        minCooldownTicks = 50,
        category = SupportCooldownCategory.DamageBuff,
        effect =
            SupportEffect.DamageBuff(
                selfDamageBps = 1_500,
                partyDamageBps = 800,
                selfAccuracyBps = 1_000,
                radius = 7,
                ticks = 50,
            ),
        iconName = "human_combat_01.png",
    ),

    /**
     * PvM damage reduction + defence buffs for the caster and nearby allies. 60 s base, 30 s floor.
     */
    IronSanctuary(
        displayName = "Iron Sanctuary",
        levelReq = 92,
        baseCooldownTicks = 100,
        minCooldownTicks = 50,
        category = SupportCooldownCategory.DefenceBuff,
        effect =
            SupportEffect.DefenceBuff(
                selfReductionBps = 1_200,
                partyReductionBps = 800,
                defencePercent = 20,
                magicDefencePercent = 15,
                rangedDefencePercent = 15,
                radius = 7,
                ticks = 50,
            ),
        iconName = "human_defense_07.png",
    ),

    /** Prayer restoration. 60 s base, 30 s floor. */
    RadiantRenewal(
        displayName = "Radiant Renewal",
        levelReq = 88,
        baseCooldownTicks = 100,
        minCooldownTicks = 50,
        category = SupportCooldownCategory.Support,
        effect = SupportEffect.RestorePrayer(percent = 15),
        iconName = "human_magic_03.png",
    ),

    /** Nature-powered self-heal. 40 s base, 20 s floor. */
    NaturesGrace(
        displayName = "Nature's Grace",
        levelReq = 81,
        baseCooldownTicks = 67,
        minCooldownTicks = 33,
        category = SupportCooldownCategory.Healing,
        effect = SupportEffect.SelfHeal(percent = 20),
        iconName = "elf_defense_03.png",
    ),

    /** Group heal that links the caster's vitality with nearby allies. 50 s base, 25 s floor. */
    SpiritBond(
        displayName = "Spirit Bond",
        levelReq = 86,
        baseCooldownTicks = 84,
        minCooldownTicks = 42,
        category = SupportCooldownCategory.Healing,
        effect = SupportEffect.PartyHeal(percent = 15, radius = 7, maxTargets = 8),
        iconName = "darkelf_support_03.png",
    ),

    /** Massive stoneform defence and damage mitigation. 60 s base, 30 s floor. */
    DwarvenBulwark(
        displayName = "Dwarven Bulwark",
        levelReq = 90,
        baseCooldownTicks = 100,
        minCooldownTicks = 50,
        category = SupportCooldownCategory.DefenceBuff,
        effect =
            SupportEffect.DefenceBuff(
                selfReductionBps = 1_800,
                partyReductionBps = 800,
                defencePercent = 35,
                magicDefencePercent = 10,
                rangedDefencePercent = 10,
                radius = 7,
                ticks = 50,
            ),
        iconName = "dwarf_defense_10.png",
    ),

    /** Shimmering barrier that absorbs incoming PvM damage. 55 s base, 27 s floor. */
    ArcaneBarrier(
        displayName = "Arcane Barrier",
        levelReq = 85,
        baseCooldownTicks = 92,
        minCooldownTicks = 46,
        category = SupportCooldownCategory.DefenceBuff,
        effect = SupportEffect.HealAndShield(healPercent = 0, shieldPercent = 15, shieldTicks = 30),
        iconName = "arcane_defense_01.png",
    ),

    /** Unstoppable momentum: heavy defence and damage mitigation. 50 s base, 25 s floor. */
    Juggernaut(
        displayName = "Juggernaut",
        levelReq = 84,
        baseCooldownTicks = 84,
        minCooldownTicks = 42,
        category = SupportCooldownCategory.DefenceBuff,
        effect =
            SupportEffect.DefenceBuff(
                selfReductionBps = 1_500,
                partyReductionBps = 600,
                defencePercent = 30,
                magicDefencePercent = 15,
                rangedDefencePercent = 15,
                radius = 7,
                ticks = 50,
            ),
        iconName = "orc_defense_08.png",
    ),

    /** Rotating bone shards that absorb physical strikes. 45 s base, 22 s floor. */
    BoneArmor(
        displayName = "Bone Armor",
        levelReq = 82,
        baseCooldownTicks = 75,
        minCooldownTicks = 37,
        category = SupportCooldownCategory.DefenceBuff,
        effect = SupportEffect.HealAndShield(healPercent = 0, shieldPercent = 25, shieldTicks = 25),
        iconName = "undead_defense_01.png",
    ),

    /** Arcane power surge boosting spell damage. 75 s base, 35 s floor. */
    ArcaneSurge(
        displayName = "Arcane Surge",
        levelReq = 94,
        baseCooldownTicks = 125,
        minCooldownTicks = 60,
        category = SupportCooldownCategory.DamageBuff,
        effect =
            SupportEffect.DamageBuff(
                selfDamageBps = 2_000,
                partyDamageBps = 1_000,
                selfAccuracyBps = 1_500,
                radius = 7,
                ticks = 50,
            ),
        iconName = "arcane_magic_06.png",
    ),

    /** Crushing attack that tears monster armour. 30 s base, 15 s floor. */
    Stonebreaker(
        displayName = "Stonebreaker",
        levelReq = 78,
        baseCooldownTicks = 50,
        minCooldownTicks = 25,
        category = SupportCooldownCategory.DamageBuff,
        effect =
            SupportEffect.DamageBuff(
                selfDamageBps = 1_000,
                partyDamageBps = 500,
                selfAccuracyBps = 1_500,
                radius = 7,
                ticks = 25,
            ),
        iconName = "dwarf_combat_01.png",
    ),

    /** Piercing astral arrow with high accuracy. 25 s base, 12 s floor. */
    CelestialArrow(
        displayName = "Celestial Arrow",
        levelReq = 83,
        baseCooldownTicks = 42,
        minCooldownTicks = 20,
        category = SupportCooldownCategory.DamageBuff,
        effect =
            SupportEffect.DamageBuff(
                selfDamageBps = 1_200,
                partyDamageBps = 600,
                selfAccuracyBps = 2_000,
                radius = 7,
                ticks = 30,
            ),
        iconName = "elf_combat_10.png",
    ),

    /** Life drain siphon damaging monsters and healing the caster. 50 s base, 25 s floor. */
    BloodSiphon(
        displayName = "Blood Siphon",
        levelReq = 90,
        baseCooldownTicks = 84,
        minCooldownTicks = 42,
        category = SupportCooldownCategory.DamageBuff,
        effect =
            SupportEffect.LifeSiphon(
                healPercent = 15,
                selfDamageBps = 1_200,
                selfAccuracyBps = 1_000,
                ticks = 30,
            ),
        iconName = "undead_magic_01.png",
    ),

    /** Evasive shroud granting incoming hit reduction. 45 s base, 20 s floor. */
    ShadowVeil(
        displayName = "Shadow Veil",
        levelReq = 86,
        baseCooldownTicks = 75,
        minCooldownTicks = 35,
        category = SupportCooldownCategory.DefenceBuff,
        effect =
            SupportEffect.DefenceBuff(
                selfReductionBps = 1_400,
                partyReductionBps = 600,
                defencePercent = 20,
                magicDefencePercent = 20,
                rangedDefencePercent = 20,
                radius = 7,
                ticks = 40,
            ),
        iconName = "darkelf_defense_04.png",
    ),

    /** Demonic spiked armour reflecting monster ferocity. 65 s base, 30 s floor. */
    DemonicWard(
        displayName = "Demonic Ward",
        levelReq = 89,
        baseCooldownTicks = 110,
        minCooldownTicks = 50,
        category = SupportCooldownCategory.DefenceBuff,
        effect =
            SupportEffect.DefenceBuff(
                selfReductionBps = 1_600,
                partyReductionBps = 800,
                defencePercent = 20,
                magicDefencePercent = 15,
                rangedDefencePercent = 15,
                radius = 7,
                ticks = 50,
            ),
        iconName = "demon_defense_03.png",
    ),

    /** Devastating orcish frenzy maximizing damage output. 80 s base, 40 s floor. */
    BloodFrenzy(
        displayName = "Blood Frenzy",
        levelReq = 95,
        baseCooldownTicks = 134,
        minCooldownTicks = 67,
        category = SupportCooldownCategory.DamageBuff,
        effect =
            SupportEffect.DamageBuff(
                selfDamageBps = 2_200,
                partyDamageBps = 1_000,
                selfAccuracyBps = 1_800,
                radius = 7,
                ticks = 50,
            ),
        iconName = "orc_combat_02.png",
    );

    /** Base cooldown in whole seconds, for display. */
    public val baseCooldownSeconds: Int
        get() = baseCooldownTicks * 6 / 10
}

/** What a [SupportSpell] does when it lands. Percentages are of the target's maximum hitpoints. */
public sealed interface SupportEffect {
    public data class SelfHeal(val percent: Int) : SupportEffect

    public data class PartyHeal(val percent: Int, val radius: Int, val maxTargets: Int) :
        SupportEffect

    public data class HealAndShield(
        val healPercent: Int,
        val shieldPercent: Int,
        val shieldTicks: Int,
    ) : SupportEffect

    public data class DamageBuff(
        val selfDamageBps: Int,
        val partyDamageBps: Int,
        val selfAccuracyBps: Int,
        val radius: Int,
        val ticks: Int,
    ) : SupportEffect

    public data class DefenceBuff(
        val selfReductionBps: Int,
        val partyReductionBps: Int,
        val defencePercent: Int,
        val magicDefencePercent: Int,
        val rangedDefencePercent: Int,
        val radius: Int,
        val ticks: Int,
    ) : SupportEffect

    public data class RestorePrayer(val percent: Int) : SupportEffect

    public data class LifeSiphon(
        val healPercent: Int,
        val selfDamageBps: Int,
        val selfAccuracyBps: Int,
        val ticks: Int,
    ) : SupportEffect
}
