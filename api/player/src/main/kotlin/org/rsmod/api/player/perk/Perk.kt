package org.rsmod.api.player.perk

import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.type.varp.VarpType

public object PerkVarps : VarpReferences() {
    /** Legacy 32-bit view kept for old commands and integrations. */
    public val points: VarpType = find("perk_points")
    public val pointsLow: VarpType = find("perk_points_low")
    public val pointsHigh: VarpType = find("perk_points_high")
    public val rankFormat: VarpType = find("perk_rank_format")
    public val xp_power: VarpType = find("perk_xp_power")
    public val xp_vitality: VarpType = find("perk_xp_vitality")
    public val xp_greed: VarpType = find("perk_xp_greed")
    public val xp_guardian: VarpType = find("perk_xp_guardian")
    public val xp_fortune: VarpType = find("perk_xp_fortune")
    public val xp_swiftness: VarpType = find("perk_xp_swiftness")

    // PvM
    public val xp_slayer: VarpType = find("perk_xp_slayer")
    public val xp_berserker: VarpType = find("perk_xp_berserker")
    public val xp_deadeye: VarpType = find("perk_xp_deadeye")
    public val xp_sorcerer: VarpType = find("perk_xp_sorcerer")
    public val xp_executioner: VarpType = find("perk_xp_executioner")
    public val xp_punisher: VarpType = find("perk_xp_punisher")
    public val xp_dominion: VarpType = find("perk_xp_dominion")
    public val xp_slaughter: VarpType = find("perk_xp_slaughter")
    public val xp_rend: VarpType = find("perk_xp_rend")
    public val xp_bloodlust: VarpType = find("perk_xp_bloodlust")
    public val xp_leech: VarpType = find("perk_xp_leech")
    public val xp_ironhide: VarpType = find("perk_xp_ironhide")
    public val xp_spellward: VarpType = find("perk_xp_spellward")
    public val xp_dodging: VarpType = find("perk_xp_dodging")
    public val xp_thorns: VarpType = find("perk_xp_thorns")
    public val xp_laststand: VarpType = find("perk_xp_laststand")
    public val xp_smithwright: VarpType = find("perk_xp_smithwright")
    public val xp_adrenaline: VarpType = find("perk_xp_adrenaline")
    public val xp_regeneration: VarpType = find("perk_xp_regeneration")
    public val xp_harvest: VarpType = find("perk_xp_harvest")
    public val xp_rejuvenation: VarpType = find("perk_xp_rejuvenation")
    public val xp_scavenger: VarpType = find("perk_xp_scavenger")

    // Skilling
    public val xp_scholar: VarpType = find("perk_xp_scholar")
    public val xp_woodcutter: VarpType = find("perk_xp_woodcutter")
    public val xp_prospector: VarpType = find("perk_xp_prospector")
    public val xp_angler: VarpType = find("perk_xp_angler")
    public val xp_chef: VarpType = find("perk_xp_chef")
    public val xp_artificer: VarpType = find("perk_xp_artificer")
    public val xp_blacksmith: VarpType = find("perk_xp_blacksmith")
    public val xp_runic: VarpType = find("perk_xp_runic")
    public val xp_arsonist: VarpType = find("perk_xp_arsonist")
    public val xp_herbalist: VarpType = find("perk_xp_herbalist")
    public val xp_farmer: VarpType = find("perk_xp_farmer")
    public val xp_tracker: VarpType = find("perk_xp_tracker")
    public val xp_burglar: VarpType = find("perk_xp_burglar")
    public val xp_builder: VarpType = find("perk_xp_builder")
    public val xp_acrobat: VarpType = find("perk_xp_acrobat")
    public val xp_fletcher: VarpType = find("perk_xp_fletcher")
    public val xp_bounty: VarpType = find("perk_xp_bounty")
    public val xp_devout: VarpType = find("perk_xp_devout")
    public val xp_instinct: VarpType = find("perk_xp_instinct")
    public val xp_lumberjack: VarpType = find("perk_xp_lumberjack")
    public val xp_motherlode: VarpType = find("perk_xp_motherlode")
    public val xp_trawler: VarpType = find("perk_xp_trawler")
    public val xp_gourmet: VarpType = find("perk_xp_gourmet")
    public val xp_nimble: VarpType = find("perk_xp_nimble")
    public val xp_runicmastery: VarpType = find("perk_xp_runicmastery")

    // Solo Boss Arena progression. These are direct permanent levels rather than perk XP,
    // because the arena has its own PvM-point economy and a 100-level cap.
    public val soloBossDamage: VarpType = find("solo_boss_perk_damage")
    public val soloBossAttackSpeed: VarpType = find("solo_boss_perk_attack_speed")
    public val soloBossMaxHealth: VarpType = find("solo_boss_perk_max_health")
}

/**
 * Passive account-wide perks trained with **Perk Points**, earned from boss kills.
 *
 * Each perk has [MAX_PERK_RANK] ranks. Every button press buys exactly one next rank and the price
 * doubles for each rank already owned. Effect strengths are defined in [PerkService] as per-rank
 * values.
 *
 * PvM perks only ever apply to npc-originated or npc-targeted combat - PvP is unaffected. Skilling
 * perks either scale xp (resolved centrally in `PlayerSkillXP.internalAddXP`) or add yield/success
 * effects inside the respective skill scripts.
 */
public enum class Perk(
    public val displayName: String,
    public val description: String,
    public val xpVarp: VarpType,
    public val baseCost: Long = 1L,
) {
    // ---- Core / existing ----
    Power("Power", "+2% dmg vs npcs /lvl", PerkVarps.xp_power),
    Vitality("Vitality", "+1 max HP /lvl", PerkVarps.xp_vitality),
    Greed("Greed", "+5% bonus drop roll /lvl", PerkVarps.xp_greed),
    Guardian("Guardian", "-2% dmg taken /lvl", PerkVarps.xp_guardian),
    Fortune("Fortune", "+10% perk pts on boss /lvl", PerkVarps.xp_fortune),
    Swiftness("Swiftness", "-1% attack cycle /lvl", PerkVarps.xp_swiftness),

    // ---- PvM offence ----
    Slayer("Slayer", "+3% dmg vs bosses /lvl", PerkVarps.xp_slayer),
    Berserker("Berserker", "+2% melee dmg /lvl", PerkVarps.xp_berserker),
    Deadeye("Deadeye", "+2% ranged dmg /lvl", PerkVarps.xp_deadeye),
    Sorcerer("Sorcerer", "+2% magic dmg /lvl", PerkVarps.xp_sorcerer),
    Executioner("Executioner", "+4% dmg vs low-HP npc /lvl", PerkVarps.xp_executioner),
    Punisher("Punisher", "+3% dmg vs high-HP npc /lvl", PerkVarps.xp_punisher),
    Dominion("Dominion", "+2% dmg vs higher-lvl npc /lvl", PerkVarps.xp_dominion),
    Slaughter("Slaughter", "+2% crit +50% dmg /lvl", PerkVarps.xp_slaughter),
    Rend("Rend", "+1 dmg every 2 lvls", PerkVarps.xp_rend),
    Bloodlust("Bloodlust", "+3% dmg below half HP /lvl", PerkVarps.xp_bloodlust),
    Leech("Leech", "Heal 0.5% of dmg /lvl", PerkVarps.xp_leech),

    // ---- PvM defence ----
    Ironhide("Ironhide", "-2% melee dmg taken /lvl", PerkVarps.xp_ironhide),
    Spellward("Spellward", "-2% magic dmg taken /lvl", PerkVarps.xp_spellward),
    Dodging("Dodging", "-2% ranged dmg taken /lvl", PerkVarps.xp_dodging),
    Thorns("Thorns", "Reflect 2% dmg taken /lvl", PerkVarps.xp_thorns),
    Laststand("Last Stand", "-3% dmg below 25% HP /lvl", PerkVarps.xp_laststand),
    Smithwright("Smithwright", "-0.5% melee dmg taken /lvl", PerkVarps.xp_smithwright),

    // ---- PvM sustain / loot ----
    Adrenaline("Adrenaline", "+10% spec regen /lvl", PerkVarps.xp_adrenaline),
    Regeneration("Regeneration", "+10% +1hp regen /lvl", PerkVarps.xp_regeneration),
    Harvest("Harvest", "+1 prayer on kill /lvl", PerkVarps.xp_harvest),
    Rejuvenation("Rejuvenation", "+2 HP on kill /lvl", PerkVarps.xp_rejuvenation),
    Scavenger("Scavenger", "+5% perk pt on kill /lvl", PerkVarps.xp_scavenger),

    // ---- Skilling xp ----
    Scholar("Scholar", "+1% skilling xp /lvl", PerkVarps.xp_scholar),
    Woodcutter("XP: Woodcutting", "+2% Woodcutting xp /lvl", PerkVarps.xp_woodcutter),
    Prospector("XP: Mining", "+2% Mining xp /lvl", PerkVarps.xp_prospector),
    Angler("XP: Fishing", "+2% Fishing xp /lvl", PerkVarps.xp_angler),
    Chef("XP: Cooking", "+2% Cooking xp /lvl", PerkVarps.xp_chef),
    Artificer("XP: Crafting", "+2% Crafting xp /lvl", PerkVarps.xp_artificer),
    Blacksmith("XP: Smithing", "+2% Smithing xp /lvl", PerkVarps.xp_blacksmith),
    Runic("XP: Runecraft", "+2% Runecraft xp /lvl", PerkVarps.xp_runic),
    Arsonist("XP: Firemaking", "+2% Firemaking xp /lvl", PerkVarps.xp_arsonist),
    Herbalist("XP: Herblore", "+2% Herblore xp /lvl", PerkVarps.xp_herbalist),
    Farmer("XP: Farming", "+2% Farming xp /lvl", PerkVarps.xp_farmer),
    Tracker("XP: Hunter", "+2% Hunter xp /lvl", PerkVarps.xp_tracker),
    Burglar("XP: Thieving", "+2% Thieving xp /lvl", PerkVarps.xp_burglar),
    Builder("XP: Construction", "+2% Construction xp /lvl", PerkVarps.xp_builder),
    Acrobat("XP: Agility", "+2% Agility xp /lvl", PerkVarps.xp_acrobat),
    Fletcher("XP: Fletching", "+2% Fletching xp /lvl", PerkVarps.xp_fletcher),
    Bounty("XP: Slayer", "+2% Slayer xp /lvl", PerkVarps.xp_bounty),
    Devout("XP: Prayer", "+2% Prayer xp /lvl", PerkVarps.xp_devout),

    // ---- Skilling yield / success ----
    Instinct("Instinct", "+1 invis WC/Mining lvl /lvl", PerkVarps.xp_instinct),
    Lumberjack("Lumberjack", "+5% double logs /lvl", PerkVarps.xp_lumberjack),
    Motherlode("Motherlode", "+5% double ore /lvl", PerkVarps.xp_motherlode),
    Trawler("Trawler", "+5% double catch /lvl", PerkVarps.xp_trawler),
    Gourmet("Gourmet", "-5% food burn /lvl", PerkVarps.xp_gourmet),
    Nimble("Nimble", "+1% pickpocket /lvl", PerkVarps.xp_nimble),
    RunicMastery("Runic Mastery", "+5% double runes /lvl", PerkVarps.xp_runicmastery);

    public companion object {
        public const val MAX_PERK_RANK: Int = 50
    }
}
