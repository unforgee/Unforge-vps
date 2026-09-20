package org.rsmod.content.other.devhub

/** Top-level tabs across the top of the hub window, in on-screen order. */
enum class DevHubTab(val label: String) {
    Items("Items"),
    Equipment("Equipment"),
    Gear("Gear"),
    Teleports("Teleports"),
    Skills("Skills"),
    Misc("Misc");

    companion object {
        operator fun get(index: Int): DevHubTab? = entries.getOrNull(index)
    }
}

/**
 * Every obj category shown in the hub's left-hand list, keyed by `(tab, row)`.
 *
 * `ObjCategorizer` assigns every non-filtered obj in the cache to exactly one of these; anything no
 * rule claims lands in [EverythingElse], so the store stays literally complete. Two kinds of
 * category are exceptions the categorizer seeds as empty: the [DevHubTab.Gear] rows are served from
 * `CuratedGear`'s hand-authored lists, and the [DevHubTab.Teleports]/[DevHubTab.Skills] rows are
 * served from their dedicated data providers (see `DevHubTeleports.forCategory` and
 * `DevHubSkillGroups`). Keep at most 12 categories per tab - that is how many rows the authored
 * interface has.
 */
enum class DevHubCategory(val tab: DevHubTab, val label: String) {
    Potions(DevHubTab.Items, "Potions"),
    FoodAndDrink(DevHubTab.Items, "Food & drink"),
    Runes(DevHubTab.Items, "Runes"),
    Herbs(DevHubTab.Items, "Herbs"),
    Seeds(DevHubTab.Items, "Seeds & saplings"),
    OresAndBars(DevHubTab.Items, "Ores & bars"),
    LogsAndPlanks(DevHubTab.Items, "Logs & planks"),
    FishAndMeat(DevHubTab.Items, "Raw food"),
    Gems(DevHubTab.Items, "Gems"),
    Tools(DevHubTab.Items, "Tools"),
    TeleportItems(DevHubTab.Items, "Teleport items"),
    Materials(DevHubTab.Items, "Materials"),
    MeleeWeapons(DevHubTab.Equipment, "Melee weapons"),
    RangedWeapons(DevHubTab.Equipment, "Ranged weapons"),
    MagicWeapons(DevHubTab.Equipment, "Magic weapons"),
    Ammo(DevHubTab.Equipment, "Ammunition"),
    Head(DevHubTab.Equipment, "Head"),
    Cape(DevHubTab.Equipment, "Capes"),
    Neck(DevHubTab.Equipment, "Necklaces"),
    Body(DevHubTab.Equipment, "Body"),
    Legs(DevHubTab.Equipment, "Legs"),
    Shield(DevHubTab.Equipment, "Shields"),
    HandsAndFeet(DevHubTab.Equipment, "Hands & feet"),
    Rings(DevHubTab.Equipment, "Rings"),
    MeleeGear(DevHubTab.Gear, "Melee gear"),
    RangedGear(DevHubTab.Gear, "Ranged gear"),
    MagicGear(DevHubTab.Gear, "Magic gear"),
    SwitchesAndJewellery(DevHubTab.Gear, "Switches & jewellery"),
    PvmPotions(DevHubTab.Gear, "Potions"),
    PvmFood(DevHubTab.Gear, "Food"),
    RunesAndAmmo(DevHubTab.Gear, "Runes & ammo"),
    StandardSpellbook(DevHubTab.Teleports, "Standard spellbook"),
    AncientSpellbook(DevHubTab.Teleports, "Ancient spellbook"),
    LunarSpellbook(DevHubTab.Teleports, "Lunar spellbook"),
    ArceuusSpellbook(DevHubTab.Teleports, "Arceuus spellbook"),
    Cities(DevHubTab.Teleports, "Cities"),
    Bosses(DevHubTab.Teleports, "Bosses"),
    Monsters(DevHubTab.Teleports, "Monsters"),
    Minigames(DevHubTab.Teleports, "Minigames"),
    Raids(DevHubTab.Teleports, "Raids"),
    Dungeons(DevHubTab.Teleports, "Dungeons"),
    Wilderness(DevHubTab.Teleports, "Wilderness"),
    OtherTeleports(DevHubTab.Teleports, "Other"),
    AllSkills(DevHubTab.Skills, "All skills"),
    CombatSkills(DevHubTab.Skills, "Combat"),
    GatheringSkills(DevHubTab.Skills, "Gathering"),
    ArtisanSkills(DevHubTab.Skills, "Artisan"),
    SupportSkills(DevHubTab.Skills, "Support"),
    EverythingElse(DevHubTab.Misc, "Everything else");

    companion object {
        /** Categories for [tab], in declaration order - one per visible row. */
        fun forTab(tab: DevHubTab): List<DevHubCategory> = entries.filter { it.tab == tab }
    }
}
