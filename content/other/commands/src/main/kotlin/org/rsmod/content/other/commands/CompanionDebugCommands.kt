package org.rsmod.content.other.commands

import jakarta.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.companion.CompanionAbilityCatalog
import org.rsmod.api.companion.CompanionClass
import org.rsmod.api.companion.CompanionEvolutionCatalog
import org.rsmod.api.companion.CompanionFrameLayout
import org.rsmod.api.companion.CompanionFrameLayoutStore
import org.rsmod.api.companion.CompanionService
import org.rsmod.api.companion.CompanionSetCatalog
import org.rsmod.api.companion.CompanionSkill
import org.rsmod.api.companion.CompanionSkillService
import org.rsmod.api.companion.CompanionStat
import org.rsmod.api.companion.CompanionStatAugmenter
import org.rsmod.api.companion.CompanionStatCalculator
import org.rsmod.api.companion.CompanionStatFormat
import org.rsmod.api.companion.CompanionTelemetryService
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.game.cheat.Cheat
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Admin-only smoke-test commands; recruitment in normal play must be wired to Contract Scrolls. */
public class CompanionDebugCommands
@Inject
constructor(
    private val companions: CompanionService,
    private val layouts: CompanionFrameLayoutStore,
    private val skillXp: CompanionSkillService,
    private val equipmentInstances: EquipmentInstanceRegistry,
    private val telemetry: CompanionTelemetryService,
    private val companionPlayerManager: CompanionPlayerManager,
    private val roleSpells: CompanionRoleSpells,
    private val npcRegistry: NpcRegistry,
    private val fragmentAugmenters: Set<CompanionStatAugmenter>,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("companioncreate", "Create a test companion", ::create) {
            invalidArgs = "Use as ::companioncreate name class npcId hp"
        }
        onCommand("companionlist", "List owned companions", ::list)
        onCommand("companionactive", "Activate an owned companion", ::activate) {
            invalidArgs = "Use as ::companionactive companionId"
        }
        onCommand("companionxp", "Add companion experience", ::addExperience) {
            invalidArgs = "Use as ::companionxp companionId amount"
        }
        onCommand("companiontalent", "Spend one companion talent point", ::talent) {
            invalidArgs = "Use as ::companiontalent companionId talentId"
        }
        onCommand("companionframe", "Set companion frame layout", ::frame) {
            invalidArgs = "Use as ::companionframe x y scale"
        }
        onCommand("cstats", "Dump the canonical companion stat sheet", ::statSheet) {
            invalidArgs = "Use as ::cstats [companionId]"
        }
        onCommand("csets", "Show active companion gear sets", ::sets)
        onCommand("cevolve", "Evolve a companion to its next stage", ::evolve) {
            invalidArgs = "Use as ::cevolve companionId"
        }
        onCommand("cmeter", "Show companion damage/healing meter", ::meter)
        onCommand("cskillxp", "Show companion progression track xp", ::skillTracks)
        onCommand(
            "abilitypreview",
            "Preview a companion ability's real visuals",
            ::abilityPreview,
        ) {
            invalidArgs = "Use as ::abilitypreview abilityId (ex: focused-volley)"
        }
    }

    private fun resolveCompanion(
        cheat: Cheat,
        argIndex: Int = 0,
    ): org.rsmod.api.companion.Companion? =
        with(cheat) {
            val ownerId = player.characterId.toLong()
            val owned = companions.owned(ownerId)
            if (owned.isEmpty()) {
                player.mes("No companions")
                return null
            }
            val id = args.getOrNull(argIndex)?.toLongOrNull()
            val companion =
                if (id == null) owned.firstOrNull { it.active } ?: owned.first()
                else owned.firstOrNull { it.id == id }
            if (companion == null) {
                player.mes("Unknown companion id")
            }
            companion
        }

    private fun statSheet(cheat: Cheat) =
        with(cheat) {
            val companion = resolveCompanion(cheat) ?: return
            val sheet =
                CompanionStatCalculator.calculate(
                    companion,
                    equipmentInstances,
                    skillXp.levelsOf(companion.id),
                    fragmentAugmenters,
                )
            player.mes(
                "<col=ff981f>${companion.name}</col> ${companion.companionClass} " +
                    "Lv${companion.level} Evo${companion.evolutionStage} " +
                    "${companion.attackStyle}"
            )
            for (stat in CompanionStat.entries) {
                val value = sheet.value(stat)
                if (value == 0) continue
                val rendered =
                    if (stat.format == CompanionStatFormat.PERCENT_BPS)
                        "%.1f%%".format(value / 100.0)
                    else "$value"
                player.mes("  ${stat.name} = $rendered")
            }
            val combat = sheet.combat
            player.mes(
                "  combat: hp=${combat.maximumHitpoints} dmg=${"%.2f".format(combat.damageMultiplier)}x " +
                    "support=${"%.2f".format(combat.supportPower)}x delay=${combat.attackDelay}"
            )
        }

    private fun sets(cheat: Cheat) =
        with(cheat) {
            val companion = resolveCompanion(cheat) ?: return
            val items = companion.gearInstanceIds.mapNotNull(equipmentInstances::get)
            val activations = CompanionSetCatalog.activations(items)
            if (activations.isEmpty()) {
                player.mes("${companion.name}: no set pieces equipped.")
                return
            }
            for (activation in activations) {
                player.mes(
                    "<col=ff981f>${activation.definition.name}</col> " +
                        "(${activation.equippedPieces}/${activation.definition.maxPieces})"
                )
                for (bonus in activation.definition.bonuses) {
                    val state = if (activation.isActive(bonus.pieces)) "ACTIVE" else "LOCKED"
                    player.mes("  (${bonus.pieces}) ${bonus.description} - $state")
                }
            }
        }

    private fun evolve(cheat: Cheat): Unit =
        with(cheat) {
            val companion = resolveCompanion(cheat) ?: return
            val next = CompanionEvolutionCatalog.nextStage(companion.evolutionStage)
            if (next == null) {
                player.mes("${companion.name} is already at its final stage.")
                return
            }
            runCatching { companions.evolve(player.characterId.toLong(), companion.id) }
                .onSuccess { updated ->
                    player.mes(
                        "<col=ff981f>${updated.name} evolved to ${next.title} " +
                            "(stage ${updated.evolutionStage})!</col>"
                    )
                }
                .onFailure { player.mes("<col=ff3333>${it.message}</col>") }
        }

    private fun meter(cheat: Cheat) =
        with(cheat) {
            val ownerId = player.characterId.toLong()
            val rows = telemetry.snapshot(ownerId)
            if (rows.isEmpty()) {
                player.mes("No companion combat data recorded yet.")
                return
            }
            for (row in rows.sortedByDescending { it.damage + it.effectiveHealing }) {
                player.mes(
                    "<col=ff981f>${row.sourceName}</col> dmg=${row.damage} " +
                        "heal=${row.effectiveHealing} taken=${row.damageTaken} " +
                        "hits=${row.hitCount} max=${row.maxHit}"
                )
                row.abilityBreakdown.entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .forEach { (ability, dmg) -> player.mes("    $ability: $dmg") }
            }
        }

    private fun skillTracks(cheat: Cheat) =
        with(cheat) {
            val companion = resolveCompanion(cheat) ?: return
            for (skill in CompanionSkill.entries) {
                val xp = skillXp.experienceOf(companion.id, skill)
                player.mes("  ${skill.name}: Lv${skillXp.levelOf(companion.id, skill)} ($xp xp)")
            }
        }

    /**
     * Safe world preview for a companion ability presentation: plays the ability's configured
     * `AbilityVisual` on the companion bot, aimed at the nearest valid npc (or the owner for
     * support visuals). Uses the exact definition the live ability casts with - no fake UI
     * animation - and never touches cooldowns, xp or combat state.
     */
    private fun abilityPreview(cheat: Cheat) =
        with(cheat) {
            val abilityId = args[0]
            val definition = CompanionAbilityCatalog.byId[abilityId]
            if (definition == null) {
                player.mes("Unknown companion ability: '$abilityId'")
                return
            }
            val companion = resolveCompanion(cheat, argIndex = 1) ?: return
            val bot = companionPlayerManager.getPlayer(companion.id)
            if (bot == null || !bot.isSlotAssigned) {
                player.mes("${companion.name} is not summoned.")
                return
            }
            val target =
                npcRegistry
                    .findAll(ZoneKey.from(player.coords))
                    .filter { it.isValidTarget() && it.hitpoints > 0 }
                    .minByOrNull {
                        max(abs(it.coords.x - player.coords.x), abs(it.coords.z - player.coords.z))
                    }
            val played = roleSpells.previewAbilityVisual(player, bot, target, abilityId)
            player.mes(
                if (played) {
                    "Previewing ${definition.name} visuals" +
                        (target?.let { " on ${it.visType.name}" }
                            ?: " (no npc nearby - self/ally cast)")
                } else {
                    "No presentation defined for ability '$abilityId'."
                }
            )
        }

    private fun create(cheat: Cheat) =
        with(cheat) {
            val name = args[0]
            val role =
                runCatching { CompanionClass.valueOf(args[1].uppercase()) }
                    .getOrElse {
                        player.mes("Class must be TANK, SUPPORT or DPS")
                        return
                    }
            val npcId =
                args[2].toIntOrNull()
                    ?: run {
                        player.mes("Invalid NPC id")
                        return
                    }
            val hp =
                args[3].toIntOrNull()
                    ?: run {
                        player.mes("Invalid hitpoints")
                        return
                    }
            val ownerId = player.characterId.toLong()
            val companion = companions.recruit(ownerId, 1, name, role, npcId, hp)
            player.mes("Created ${companion.name} #${companion.id} (${companion.companionClass})")
        }

    private fun list(cheat: Cheat) =
        with(cheat) {
            val owned = companions.owned(player.characterId.toLong())
            if (owned.isEmpty()) player.mes("No companions")
            owned.forEach {
                player.mes(
                    "#${it.id} slot ${it.slot}: ${it.name} ${it.companionClass} Lv${it.level} ${it.state}"
                )
            }
        }

    private fun activate(cheat: Cheat) =
        with(cheat) {
            val id =
                args[0].toLongOrNull()
                    ?: run {
                        player.mes("Invalid companion id")
                        return
                    }
            val companion = companions.activate(player.characterId.toLong(), id)
            player.mes("Active companion: ${companion.name}")
        }

    private fun addExperience(cheat: Cheat) =
        with(cheat) {
            val id =
                args[0].toLongOrNull()
                    ?: run {
                        player.mes("Invalid companion id")
                        return
                    }
            val amount =
                args[1].toLongOrNull()
                    ?: run {
                        player.mes("Invalid experience")
                        return
                    }
            val companion = companions.addExperience(player.characterId.toLong(), id, amount)
            player.mes(
                "${companion.name}: level ${companion.level}, ${companion.talentPoints} talent points"
            )
        }

    private fun talent(cheat: Cheat) =
        with(cheat) {
            val id =
                args[0].toLongOrNull()
                    ?: run {
                        player.mes("Invalid companion id")
                        return
                    }
            val companion = companions.allocateTalent(player.characterId.toLong(), id, args[1])
            player.mes("${companion.name}: ${args[1]} allocated")
        }

    private fun frame(cheat: Cheat) =
        with(cheat) {
            val x =
                args[0].toIntOrNull()
                    ?: run {
                        player.mes("Invalid x")
                        return
                    }
            val y =
                args[1].toIntOrNull()
                    ?: run {
                        player.mes("Invalid y")
                        return
                    }
            val scale =
                args[2].toIntOrNull()
                    ?: run {
                        player.mes("Invalid scale")
                        return
                    }
            val layout =
                layouts.set(
                    player.characterId.toLong(),
                    CompanionFrameLayout(x, y, scalePercent = scale),
                )
            player.mes(
                "Companion frame saved at ${layout.x},${layout.y} scale ${layout.scalePercent}%"
            )
        }
}
