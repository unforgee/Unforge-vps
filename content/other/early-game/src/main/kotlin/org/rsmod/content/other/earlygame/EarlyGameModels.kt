package org.rsmod.content.other.earlygame

/** The three starter choices are data, not account locks. */
public enum class StarterRelic(
    public val displayName: String,
    public val recommendedCompanion: String,
) {
    WARRIOR("Warrior Relic", "Vanguard"),
    RANGER("Ranger Relic", "Hunter"),
    MYSTIC("Mystic Relic", "Mystic"),
}

public enum class AdventurePhase(public val title: String) {
    FIRST_STEPS("First Steps"),
    ADVENTURER("Adventurer"),
    GROWING_STRONGER("Growing Stronger"),
    FIRST_REAL_CHALLENGE("First Real Challenge"),
    FIRST_BOND("First Bond"),
    COMPLETE("Complete"),
}

/** Stable ids are persisted, so display text can be changed without breaking old saves. */
public enum class AdventureMilestone(
    public val title: String,
    public val phase: AdventurePhase,
    public val requirement: String,
    public val reward: String,
) {
    CHOOSE_RELIC(
        "Choose your Starter Relic",
        AdventurePhase.FIRST_STEPS,
        "Select a relic",
        "Starter Supplies",
    ),
    EQUIP_WEAPON(
        "Equip your first weapon",
        AdventurePhase.FIRST_STEPS,
        "Equip any weapon",
        "Food and coins",
    ),
    KILL_FIRST_MONSTER(
        "Kill your first monster",
        AdventurePhase.FIRST_STEPS,
        "Defeat one NPC",
        "Combat supplies",
    ),
    EAT_FOOD("Eat food", AdventurePhase.FIRST_STEPS, "Consume food", "Food bundle"),
    USE_PRAYER("Use a Prayer", AdventurePhase.FIRST_STEPS, "Activate prayer", "Prayer supplies"),
    REACH_COMBAT_5("Reach Combat Level 5", AdventurePhase.FIRST_STEPS, "Combat level 5", "Coins"),
    KILL_25("Kill 25 monsters", AdventurePhase.ADVENTURER, "Defeat 25 NPCs", "Supplies"),
    REACH_COMBAT_10("Reach Combat Level 10", AdventurePhase.ADVENTURER, "Combat level 10", "Coins"),
    USE_BANK("Use a bank", AdventurePhase.ADVENTURER, "Open a bank", "Starter Supplies"),
    USE_SHOP("Use a shop", AdventurePhase.ADVENTURER, "Open a shop", "Coins"),
    EQUIP_FOUR(
        "Equip at least 4 equipment pieces",
        AdventurePhase.ADVENTURER,
        "Equip four pieces",
        "Uncommon cache",
    ),
    FIRST_SLAYER(
        "Complete first Slayer task",
        AdventurePhase.ADVENTURER,
        "Complete one task",
        "Slayer supplies",
    ),
    KILL_100("Kill 100 monsters", AdventurePhase.GROWING_STRONGER, "Defeat 100 NPCs", "Supplies"),
    OPEN_FORGE("Open Forge", AdventurePhase.GROWING_STRONGER, "Use Forge", "Forge materials"),
    FIRST_AFFIX(
        "Obtain an affixed item",
        AdventurePhase.GROWING_STRONGER,
        "Obtain an affixed item",
        "Discovery Points",
    ),
    FIRST_BOUNTY(
        "Complete first Bounty",
        AdventurePhase.GROWING_STRONGER,
        "Complete a bounty",
        "Coins",
    ),
    FIRST_MUTATION(
        "Kill first Mutated Monster",
        AdventurePhase.GROWING_STRONGER,
        "Defeat a mutation",
        "Discovery Points",
    ),
    REACH_COMBAT_25(
        "Reach Combat Level 25",
        AdventurePhase.GROWING_STRONGER,
        "Combat level 25",
        "Supplies",
    ),
    FIRST_BOSS(
        "Kill your first boss",
        AdventurePhase.FIRST_REAL_CHALLENGE,
        "Defeat a boss",
        "Boss supplies",
    ),
    COMBAT_CHALLENGE(
        "Complete first Combat Challenge",
        AdventurePhase.FIRST_REAL_CHALLENGE,
        "Complete a challenge",
        "Discovery Points",
    ),
    THREE_BOUNTIES(
        "Complete 3 Bounties",
        AdventurePhase.FIRST_REAL_CHALLENGE,
        "Complete three bounties",
        "Coins",
    ),
    FIVE_SLAYER(
        "Complete 5 Slayer tasks",
        AdventurePhase.FIRST_REAL_CHALLENGE,
        "Complete five tasks",
        "Supplies",
    ),
    DISCOVER_TEN(
        "Discover 10 locations",
        AdventurePhase.FIRST_REAL_CHALLENGE,
        "Unlock ten discoveries",
        "Adventure Chest",
    ),
    TRIAL(
        "Defeat the Bond Guardian",
        AdventurePhase.FIRST_BOND,
        "Complete Trial of Bonding",
        "First Companion",
    ),
    CHOOSE_COMPANION(
        "Choose your first Companion",
        AdventurePhase.FIRST_BOND,
        "Select a Companion",
        "Companion unlocked",
    ),
    SUMMON_COMPANION(
        "Summon your Companion",
        AdventurePhase.FIRST_BOND,
        "Summon once",
        "Companion XP",
    ),
    OPEN_COMPANION_HUB(
        "Open Companion Hub",
        AdventurePhase.FIRST_BOND,
        "Open the hub",
        "Starter equipment chest",
    ),
    USE_COMPANION_ABILITY(
        "Use Companion Ability",
        AdventurePhase.FIRST_BOND,
        "Use one ability",
        "Companion reward",
    ),
}

public enum class DiscoveryRarity(public val points: Int) {
    COMMON(1),
    UNCOMMON(2),
    RARE(5),
    EPIC(10),
}

public enum class DiscoveryCategory {
    WORLD,
    MONSTERS,
    BOSSES,
    ITEMS,
    COMBAT,
    SYSTEMS,
    COMPANIONS,
}

public data class DiscoveryDefinition(
    public val id: String,
    public val title: String,
    public val category: DiscoveryCategory,
    public val rarity: DiscoveryRarity = DiscoveryRarity.COMMON,
)

public data class EarlyGameState(
    val starterRelic: StarterRelic? = null,
    val relicSelected: Boolean = false,
    val lastRelicChangeEpoch: Long? = null,
    val completedMilestones: Set<String> = emptySet(),
    val claimedMilestoneRewards: Set<String> = emptySet(),
    val firstBondUnlocked: Boolean = false,
    val firstBondCompleted: Boolean = false,
    val starterCompanionSelected: Boolean = false,
    val starterCompanionId: String? = null,
    val companionTutorialCompleted: Boolean = false,
    val companionBasics: Set<String> = emptySet(),
    val discoveries: Set<String> = emptySet(),
    val discoveryPoints: Int = 0,
    val discoveryRewardMilestones: Set<String> = emptySet(),
    val worldFindStatistics: Map<String, Int> = emptyMap(),
    val worldFindAvailableAtEpoch: Long = 0L,
    val trackerMinimized: Boolean = false,
    val trackerHidden: Boolean = false,
    val legacyPlayer: Boolean = false,
    val trialActive: Boolean = false,
    val trialAttempts: Int = 0,
    val monsterKills: Int = 0,
    val bossKills: Int = 0,
    val bountyCount: Int = 0,
    val slayerTaskCount: Int = 0,
)

public object EarlyGameConfig {
    public const val FIRST_BOND_COMBAT_LEVEL: Int = 40
    public const val WORLD_FIND_COOLDOWN_MINUTES: Int = 20
    public const val WORLD_FIND_LIFETIME_MINUTES: Int = 10
    public const val TRIAL_LIFETIME_CYCLES: Int = 200
    public const val STARTER_CHEST_MAX_RARITY: String = "UNCOMMON"
    public val starterPath: List<AdventureMilestone> = AdventureMilestone.entries.toList()
    public val discoveryRewardThresholds: List<Int> = listOf(10, 25, 50, 100)
}

public object EarlyGameDiscoveries {
    public val definitions: List<DiscoveryDefinition> =
        listOf(
            DiscoveryDefinition("starter-area", "Starter Area", DiscoveryCategory.WORLD),
            DiscoveryDefinition("lumbridge", "Lumbridge", DiscoveryCategory.WORLD),
            DiscoveryDefinition("varrock", "Varrock", DiscoveryCategory.WORLD),
            DiscoveryDefinition("first-monster", "First Monster", DiscoveryCategory.MONSTERS),
            DiscoveryDefinition(
                "first-boss",
                "First Boss",
                DiscoveryCategory.BOSSES,
                DiscoveryRarity.UNCOMMON,
            ),
            DiscoveryDefinition(
                "first-rare-drop",
                "First Rare Drop",
                DiscoveryCategory.ITEMS,
                DiscoveryRarity.RARE,
            ),
            DiscoveryDefinition(
                "first-mutation",
                "First Mutation",
                DiscoveryCategory.COMBAT,
                DiscoveryRarity.UNCOMMON,
            ),
            DiscoveryDefinition(
                "first-bounty",
                "First Bounty",
                DiscoveryCategory.SYSTEMS,
                DiscoveryRarity.UNCOMMON,
            ),
            DiscoveryDefinition(
                "first-forge-upgrade",
                "First Forge Upgrade",
                DiscoveryCategory.SYSTEMS,
                DiscoveryRarity.UNCOMMON,
            ),
            DiscoveryDefinition(
                "first-companion",
                "First Companion",
                DiscoveryCategory.COMPANIONS,
                DiscoveryRarity.RARE,
            ),
            DiscoveryDefinition(
                "first-companion-ability",
                "First Companion Ability",
                DiscoveryCategory.COMPANIONS,
                DiscoveryRarity.RARE,
            ),
            DiscoveryDefinition(
                "first-companion-level-up",
                "First Companion Level-up",
                DiscoveryCategory.COMPANIONS,
            ),
            DiscoveryDefinition("world-find-backpack", "Lost Backpack", DiscoveryCategory.WORLD),
            DiscoveryDefinition("world-find-crate", "Supply Crate", DiscoveryCategory.WORLD),
            DiscoveryDefinition("world-find-shrine", "Forgotten Shrine", DiscoveryCategory.WORLD),
            DiscoveryDefinition(
                "world-find-adventurer",
                "Stranded Adventurer",
                DiscoveryCategory.WORLD,
            ),
            DiscoveryDefinition(
                "world-find-chest",
                "Hidden Chest",
                DiscoveryCategory.WORLD,
                DiscoveryRarity.UNCOMMON,
            ),
            DiscoveryDefinition(
                "world-find-treasuremap",
                "Treasure Map",
                DiscoveryCategory.WORLD,
                DiscoveryRarity.RARE,
            ),
        )
    public val byId: Map<String, DiscoveryDefinition> = definitions.associateBy { it.id }
}
