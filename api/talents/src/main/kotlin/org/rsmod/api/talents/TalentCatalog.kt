package org.rsmod.api.talents

/**
 * The data-driven talent catalogs for both trees.
 *
 * Talents are deliberately plain data: the UI grid, the validation rules and the persistence layer
 * all iterate this catalog, so adding a talent is a one-line change here - never a UI or schema
 * change. Names and effects are Unforge originals; the reference layout only informed the visual
 * structure (tiers, grid, rank text, points header).
 *
 * Balance contract:
 * - `pointsPerRank = tier` mirrors the reference's rising per-rank cost (1/2/3/4 points).
 * - [TalentRules.tierGate] controls when a tier unlocks based on cumulative spent points.
 * - `requires`/`requiresRank` create the branching dependencies.
 */
public object TalentCatalog {
    /** Every player-tree definition, in grid order (tier, then slot). */
    public val player: List<TalentDefinition> =
        listOf(
            // -------------------- Tier 1 (1 pt/rank, unlocked from the start) -------------
            TalentDefinition(
                id = "sharp-strikes",
                tree = TalentTree.PLAYER,
                tier = 1,
                slot = 0,
                name = "Sharpened Strikes",
                description = "+2% damage against monsters per rank.",
                iconObj = 1333, // rune scimitar
                maxRank = 5,
                pointsPerRank = 1,
                effectKey = TalentEffectKey.NPC_DAMAGE_BPS,
                effectBpsPerRank = 200,
            ),
            TalentDefinition(
                id = "point-runner",
                tree = TalentTree.PLAYER,
                tier = 1,
                slot = 1,
                name = "Point Runner",
                description = "+5% PvM points per kill per rank.",
                iconObj = 995, // coins
                maxRank = 5,
                pointsPerRank = 1,
                effectKey = TalentEffectKey.PVM_POINTS_BPS,
                effectBpsPerRank = 500,
            ),
            TalentDefinition(
                id = "keen-eye",
                tree = TalentTree.PLAYER,
                tier = 1,
                slot = 2,
                name = "Keen Eye",
                description = "+3% chance of an extra drop roll per rank.",
                iconObj = 1601, // diamond
                maxRank = 5,
                pointsPerRank = 1,
                effectKey = TalentEffectKey.DROP_ROLL_BPS,
                effectBpsPerRank = 300,
            ),
            TalentDefinition(
                id = "skilled-hands",
                tree = TalentTree.PLAYER,
                tier = 1,
                slot = 3,
                name = "Skilled Hands",
                description = "+1% non-combat experience per rank.",
                iconObj = 2347, // hammer
                maxRank = 5,
                pointsPerRank = 1,
                effectKey = TalentEffectKey.SKILL_XP_BPS,
                effectBpsPerRank = 100,
            ),
            TalentDefinition(
                id = "longshot",
                tree = TalentTree.PLAYER,
                tier = 1,
                slot = 4,
                name = "Longshot",
                description = "+1% damage against monsters per rank.",
                iconObj = 861, // magic shortbow
                maxRank = 3,
                pointsPerRank = 1,
                effectKey = TalentEffectKey.NPC_DAMAGE_BPS,
                effectBpsPerRank = 100,
            ),
            // -------------------- Tier 2 (2 pts/rank, gate 5 spent) ----------------------
            TalentDefinition(
                id = "battle-focus",
                tree = TalentTree.PLAYER,
                tier = 2,
                slot = 0,
                name = "Battle Focus",
                description = "+3% damage against monsters per rank.",
                iconObj = 4587, // dragon scimitar
                maxRank = 3,
                pointsPerRank = 2,
                effectKey = TalentEffectKey.NPC_DAMAGE_BPS,
                effectBpsPerRank = 300,
            ),
            TalentDefinition(
                id = "fortune-finder",
                tree = TalentTree.PLAYER,
                tier = 2,
                slot = 1,
                name = "Fortune Finder",
                description = "+7.5% PvM points per kill per rank.",
                iconObj = 2572, // ring of wealth
                maxRank = 3,
                pointsPerRank = 2,
                effectKey = TalentEffectKey.PVM_POINTS_BPS,
                effectBpsPerRank = 750,
            ),
            TalentDefinition(
                id = "slayer-instinct",
                tree = TalentTree.PLAYER,
                tier = 2,
                slot = 2,
                name = "Slayer Instinct",
                description = "+5% slayer points per task per rank.",
                iconObj = 4151, // abyssal whip
                maxRank = 3,
                pointsPerRank = 2,
                effectKey = TalentEffectKey.SLAYER_POINTS_BPS,
                effectBpsPerRank = 500,
            ),
            TalentDefinition(
                id = "momentum",
                tree = TalentTree.PLAYER,
                tier = 2,
                slot = 3,
                name = "Momentum",
                description = "+4% damage against monsters per rank.",
                iconObj = 563, // law rune
                maxRank = 2,
                pointsPerRank = 2,
                effectKey = TalentEffectKey.NPC_DAMAGE_BPS,
                effectBpsPerRank = 400,
                requires = "sharp-strikes",
                requiresRank = 3,
            ),
            TalentDefinition(
                id = "thick-skin",
                tree = TalentTree.PLAYER,
                tier = 2,
                slot = 4,
                name = "Thick Skin",
                description = "Reserve rank: defensive training, future armour effect.",
                iconObj = 1201, // rune kiteshield
                maxRank = 5,
                pointsPerRank = 2,
                effectKey = TalentEffectKey.SKILL_XP_BPS,
                effectBpsPerRank = 50,
            ),
            // -------------------- Tier 3 (3 pts/rank, gate 12 spent) ---------------------
            TalentDefinition(
                id = "executioners-eye",
                tree = TalentTree.PLAYER,
                tier = 3,
                slot = 0,
                name = "Executioner's Eye",
                description = "+5% damage against monsters per rank.",
                iconObj = 560, // death rune
                maxRank = 2,
                pointsPerRank = 3,
                effectKey = TalentEffectKey.NPC_DAMAGE_BPS,
                effectBpsPerRank = 500,
                requires = "momentum",
                requiresRank = 1,
            ),
            TalentDefinition(
                id = "hoarder",
                tree = TalentTree.PLAYER,
                tier = 3,
                slot = 1,
                name = "Hoarder",
                description = "+10% PvM points per kill.",
                iconObj = 536, // dragon bones
                maxRank = 1,
                pointsPerRank = 3,
                effectKey = TalentEffectKey.PVM_POINTS_BPS,
                effectBpsPerRank = 1000,
            ),
            TalentDefinition(
                id = "scholar",
                tree = TalentTree.PLAYER,
                tier = 3,
                slot = 2,
                name = "Scholar",
                description = "+3% non-combat experience per rank.",
                iconObj = 2434, // prayer potion
                maxRank = 3,
                pointsPerRank = 3,
                effectKey = TalentEffectKey.SKILL_XP_BPS,
                effectBpsPerRank = 300,
            ),
            TalentDefinition(
                id = "ruthless",
                tree = TalentTree.PLAYER,
                tier = 3,
                slot = 3,
                name = "Ruthless",
                description = "+6% chance of an extra drop roll per rank.",
                iconObj = 565, // blood rune
                maxRank = 2,
                pointsPerRank = 3,
                effectKey = TalentEffectKey.DROP_ROLL_BPS,
                effectBpsPerRank = 600,
            ),
            // -------------------- Tier 4 (4 pts/rank, gate 20 spent) ---------------------
            TalentDefinition(
                id = "apex-training",
                tree = TalentTree.PLAYER,
                tier = 4,
                slot = 0,
                name = "Apex Training",
                description = "+8% damage against monsters.",
                iconObj = 1163, // rune full helm
                maxRank = 1,
                pointsPerRank = 4,
                effectKey = TalentEffectKey.NPC_DAMAGE_BPS,
                effectBpsPerRank = 800,
                requires = "executioners-eye",
                requiresRank = 2,
            ),
            TalentDefinition(
                id = "war-ledger",
                tree = TalentTree.PLAYER,
                tier = 4,
                slot = 1,
                name = "War Ledger",
                description = "+15% PvM points per kill.",
                iconObj = 1127, // rune platebody
                maxRank = 1,
                pointsPerRank = 4,
                effectKey = TalentEffectKey.PVM_POINTS_BPS,
                effectBpsPerRank = 1500,
            ),
            TalentDefinition(
                id = "survivor",
                tree = TalentTree.PLAYER,
                tier = 4,
                slot = 2,
                name = "Survivor",
                description = "+5% non-combat experience.",
                iconObj = 385, // shark
                maxRank = 1,
                pointsPerRank = 4,
                effectKey = TalentEffectKey.SKILL_XP_BPS,
                effectBpsPerRank = 500,
            ),
        )

    /** Every company-tree definition, in grid order. */
    public val company: List<TalentDefinition> =
        listOf(
            // -------------------- Tier 1 (1 pt/rank, unlocked from the start) ------------
            TalentDefinition(
                id = "shared-knowledge",
                tree = TalentTree.COMPANY,
                tier = 1,
                slot = 0,
                name = "Shared Knowledge",
                description = "Members: +2% non-combat experience per rank.",
                iconObj = 2434, // prayer potion
                maxRank = 5,
                pointsPerRank = 1,
                effectKey = TalentEffectKey.COMPANY_XP_BPS,
                effectBpsPerRank = 200,
            ),
            TalentDefinition(
                id = "war-chest-fund",
                tree = TalentTree.COMPANY,
                tier = 1,
                slot = 1,
                name = "War Chest Fund",
                description = "Members: +2% PvM points per kill per rank.",
                iconObj = 995, // coins
                maxRank = 5,
                pointsPerRank = 1,
                effectKey = TalentEffectKey.COMPANY_PVM_POINTS_BPS,
                effectBpsPerRank = 200,
            ),
            TalentDefinition(
                id = "banner-of-unity",
                tree = TalentTree.COMPANY,
                tier = 1,
                slot = 2,
                name = "Banner of Unity",
                description = "Members: +1% damage against monsters per rank.",
                iconObj = 1201, // rune kiteshield
                maxRank = 5,
                pointsPerRank = 1,
                effectKey = TalentEffectKey.COMPANY_DAMAGE_BPS,
                effectBpsPerRank = 100,
            ),
            TalentDefinition(
                id = "field-rations",
                tree = TalentTree.COMPANY,
                tier = 1,
                slot = 3,
                name = "Field Rations",
                description = "Members: +1% non-combat experience per rank.",
                iconObj = 385, // shark
                maxRank = 3,
                pointsPerRank = 1,
                effectKey = TalentEffectKey.COMPANY_XP_BPS,
                effectBpsPerRank = 100,
            ),
            // -------------------- Tier 2 (2 pts/rank, gate 4 spent) ----------------------
            TalentDefinition(
                id = "supply-lines",
                tree = TalentTree.COMPANY,
                tier = 2,
                slot = 0,
                name = "Supply Lines",
                description = "Members: +3% non-combat experience per rank.",
                iconObj = 2347, // hammer
                maxRank = 3,
                pointsPerRank = 2,
                effectKey = TalentEffectKey.COMPANY_XP_BPS,
                effectBpsPerRank = 300,
            ),
            TalentDefinition(
                id = "vanguard-drills",
                tree = TalentTree.COMPANY,
                tier = 2,
                slot = 1,
                name = "Vanguard Drills",
                description = "Members: +3% damage against monsters per rank.",
                iconObj = 4587, // dragon scimitar
                maxRank = 2,
                pointsPerRank = 2,
                effectKey = TalentEffectKey.COMPANY_DAMAGE_BPS,
                effectBpsPerRank = 300,
                requires = "shared-knowledge",
                requiresRank = 2,
            ),
            TalentDefinition(
                id = "treasury",
                tree = TalentTree.COMPANY,
                tier = 2,
                slot = 2,
                name = "Treasury",
                description = "Members: +4% PvM points per kill per rank.",
                iconObj = 2572, // ring of wealth
                maxRank = 3,
                pointsPerRank = 2,
                effectKey = TalentEffectKey.COMPANY_PVM_POINTS_BPS,
                effectBpsPerRank = 400,
            ),
            TalentDefinition(
                id = "rallying-standard",
                tree = TalentTree.COMPANY,
                tier = 2,
                slot = 3,
                name = "Rallying Standard",
                description = "Members: +2% damage against monsters per rank.",
                iconObj = 1333, // rune scimitar
                maxRank = 2,
                pointsPerRank = 2,
                effectKey = TalentEffectKey.COMPANY_DAMAGE_BPS,
                effectBpsPerRank = 200,
            ),
            // -------------------- Tier 3 (3 pts/rank, gate 10 spent) ---------------------
            TalentDefinition(
                id = "war-council",
                tree = TalentTree.COMPANY,
                tier = 3,
                slot = 0,
                name = "War Council",
                description = "Leader only. Members: +5% damage vs monsters per rank.",
                iconObj = 560, // death rune
                maxRank = 2,
                pointsPerRank = 3,
                effectKey = TalentEffectKey.COMPANY_DAMAGE_BPS,
                effectBpsPerRank = 500,
                leaderOnly = true,
            ),
            TalentDefinition(
                id = "quartermaster",
                tree = TalentTree.COMPANY,
                tier = 3,
                slot = 1,
                name = "Quartermaster",
                description = "Members: +6% PvM points per kill per rank.",
                iconObj = 536, // dragon bones
                maxRank = 3,
                pointsPerRank = 3,
                effectKey = TalentEffectKey.COMPANY_PVM_POINTS_BPS,
                effectBpsPerRank = 600,
            ),
            TalentDefinition(
                id = "deep-reserves",
                tree = TalentTree.COMPANY,
                tier = 3,
                slot = 2,
                name = "Deep Reserves",
                description = "Members: +5% non-combat experience per rank.",
                iconObj = 3024, // super restore
                maxRank = 2,
                pointsPerRank = 3,
                effectKey = TalentEffectKey.COMPANY_XP_BPS,
                effectBpsPerRank = 500,
            ),
            // -------------------- Tier 4 (4 pts/rank, gate 16 spent) ---------------------
            TalentDefinition(
                id = "citadel-might",
                tree = TalentTree.COMPANY,
                tier = 4,
                slot = 0,
                name = "Citadel Might",
                description = "Leader only. Members: +10% damage vs monsters.",
                iconObj = 1163, // rune full helm
                maxRank = 1,
                pointsPerRank = 4,
                effectKey = TalentEffectKey.COMPANY_DAMAGE_BPS,
                effectBpsPerRank = 1000,
                requires = "war-council",
                requiresRank = 1,
                leaderOnly = true,
            ),
            TalentDefinition(
                id = "golden-era",
                tree = TalentTree.COMPANY,
                tier = 4,
                slot = 1,
                name = "Golden Era",
                description = "Members: +10% PvM points per kill.",
                iconObj = 1601, // diamond
                maxRank = 1,
                pointsPerRank = 4,
                effectKey = TalentEffectKey.COMPANY_PVM_POINTS_BPS,
                effectBpsPerRank = 1000,
            ),
        )

    private val byId: Map<String, TalentDefinition> =
        (player + company).associateBy(TalentDefinition::id)

    public fun definition(id: String): TalentDefinition =
        requireNotNull(byId[id]) { "Unknown talent id: $id" }

    public fun definitions(tree: TalentTree): List<TalentDefinition> =
        if (tree == TalentTree.PLAYER) player else company

    init {
        // Catalog invariants checked once at class-load: every prereq resolves, lives in the
        // same tree, and sits on a strictly earlier tier; slots never collide inside a tier.
        for (def in byId.values) {
            if (def.requires != null) {
                val req = definition(def.requires)
                require(req.tree == def.tree) { "Cross-tree prereq ${def.requires} on ${def.id}" }
                require(req.tier < def.tier) {
                    "Prereq ${def.requires} is not earlier than ${def.id}"
                }
            }
        }
        for (tree in TalentTree.entries) {
            val slots = definitions(tree).map { it.tier to it.slot }
            require(slots.distinct().size == slots.size) { "Duplicate grid slot in $tree catalog" }
        }
    }
}
