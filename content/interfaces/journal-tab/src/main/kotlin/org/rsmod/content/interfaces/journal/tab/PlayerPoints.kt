package org.rsmod.content.interfaces.journal.tab

import jakarta.inject.Inject
import org.rsmod.api.companion.CompanionService
import org.rsmod.api.companion.CompanionTalentCatalog
import org.rsmod.api.player.perk.PerkService
import org.rsmod.content.interfaces.journal.tab.configs.points_varps
import org.rsmod.content.skills.core.SkillingPoints
import org.rsmod.game.entity.Player

/** Display categories for the `Points & Progress` journal page, in render order. */
enum class PlayerPointCategory(val label: String) {
    Quest("Quest"),
    Progression("Progression"),
    Combat("Combat"),
    Activity("Activity"),
    Account("Account"),
}

/**
 * One row of the points ledger. [value] is the current server-authoritative balance; [active] marks
 * point systems that exist in the game - inactive systems are rendered as `0`/`Not active` and
 * never contribute to the total.
 */
data class PlayerPointEntry(
    val key: String,
    val displayName: String,
    val value: Int,
    val category: PlayerPointCategory,
    val active: Boolean = true,
    val valueLabel: String? = null,
)

/**
 * Single read-side facade over every player-bound point balance in the game.
 *
 * Each [SOURCES] row is the *only* place its balance is read for this report, so the total can
 * never double-count a currency. Sources are listed in render order - grouped by category - and the
 * list is the extension point for future point systems: append one row and the journal page, the
 * total and the `::points` command all pick it up.
 *
 * Systems that are designed but not yet implemented (boss/achievement/support points) are kept as
 * inactive rows so the page honestly reports `Not active` instead of inventing balances; see
 * `docs/points-command.md` for the full source table.
 */
class PlayerPoints
@Inject
constructor(
    private val perks: PerkService,
    private val skilling: SkillingPoints,
    private val companions: CompanionService,
) {
    /** All registered point entries in deterministic render order. */
    fun entries(player: Player): List<PlayerPointEntry> =
        SOURCES.map { source ->
            PlayerPointEntry(
                key = source.key,
                displayName = source.displayName,
                value = if (source.active) source.read(player) else 0,
                category = source.category,
                active = source.active,
                valueLabel = source.label(player),
            )
        }

    /** Sum of every *active* point balance. Inactive rows contribute nothing. */
    fun total(entries: List<PlayerPointEntry>): Int =
        entries.filter(PlayerPointEntry::active).sumOf(PlayerPointEntry::value)

    fun total(player: Player): Int = total(entries(player))

    private fun companionTalentPoints(player: Player): Int {
        val ownerId = runCatching { player.characterId.toLong() }.getOrNull() ?: return 0
        return companions.owned(ownerId).sumOf { companion ->
            val spent =
                companion.talents.sumOf { talent ->
                    CompanionTalentCatalog.definition(talent.definitionId).pointsPerRank *
                        talent.ranks
                }
            (companion.talentPoints - spent).coerceAtLeast(0)
        }
    }

    private class Source(
        val key: String,
        val displayName: String,
        val category: PlayerPointCategory,
        val active: Boolean = true,
        val label: (Player) -> String? = { null },
        val read: Player.() -> Int = { 0 },
    )

    private val SOURCES: List<Source> =
        listOf(
            Source(
                key = "quest_points",
                displayName = "Quests",
                category = PlayerPointCategory.Quest,
            ) {
                vars[points_varps.questPoints]
            },
            Source(
                key = "achievement_points",
                displayName = "Achievements",
                category = PlayerPointCategory.Quest,
                active = false,
            ),
            Source(
                key = "skill_points",
                displayName = "Skill",
                category = PlayerPointCategory.Progression,
            ) {
                skilling.balance(this)
            },
            Source(
                key = "companion_talent",
                displayName = "Pet talents",
                category = PlayerPointCategory.Progression,
            ) {
                companionTalentPoints(this)
            },
            Source(
                key = "perk_points",
                displayName = "Perk",
                category = PlayerPointCategory.Combat,
            ) {
                perks.points(this)
            },
            Source(key = "pvm_points", displayName = "PvM", category = PlayerPointCategory.Combat) {
                vars[points_varps.pvmPoints]
            },
            Source(
                key = "boss_points",
                displayName = "Boss",
                category = PlayerPointCategory.Combat,
                active = false,
            ),
            Source(
                key = "slayer_points",
                displayName = "Slayer",
                category = PlayerPointCategory.Combat,
            ) {
                vars[points_varps.slayerPoints]
            },
            Source(
                key = "wild_slayer_points",
                displayName = "Wildy slayer",
                category = PlayerPointCategory.Combat,
            ) {
                vars[points_varps.wildSlayerPoints]
            },
            Source(
                key = "slayer_task",
                displayName = "Task",
                category = PlayerPointCategory.Activity,
                label = { player ->
                    val task = player.vars[points_varps.slayerTask]
                    val remaining = player.vars[points_varps.slayerRemaining]
                    if (task == 0) "None" else "$task ($remaining)"
                },
            ),
            Source(
                key = "support_points",
                displayName = "Support",
                category = PlayerPointCategory.Activity,
                active = false,
            ),
            Source(
                key = "donator_points",
                displayName = "Donator",
                category = PlayerPointCategory.Account,
            ) {
                vars[points_varps.donatorPoints]
            },
            Source(
                key = "donator_rank",
                displayName = "Rank",
                category = PlayerPointCategory.Account,
                label = { player ->
                    when (player.vars[points_varps.donatorTier]) {
                        11 -> "Sapphire"
                        12 -> "Emerald"
                        13 -> "Ruby"
                        14 -> "Diamond"
                        15 -> "Dragonstone"
                        16 -> "Onyx"
                        17 -> "Zenyte"
                        else -> "None"
                    }
                },
            ),
        )
}
