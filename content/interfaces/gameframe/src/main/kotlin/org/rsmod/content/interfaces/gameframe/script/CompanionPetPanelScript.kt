package org.rsmod.content.interfaces.gameframe.script

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.companion.Companion
import org.rsmod.api.companion.CompanionCombatMode
import org.rsmod.api.companion.CompanionGearCalculator
import org.rsmod.api.companion.CompanionService
import org.rsmod.api.companion.CompanionSpellbook
import org.rsmod.api.companion.CompanionTalentCatalog
import org.rsmod.api.companion.companionWeaponStyle
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetScrollPos
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.interfaces.gameframe.config.CompanionPetInterfaceBuilder
import org.rsmod.content.interfaces.gameframe.config.companion_pet_components
import org.rsmod.content.interfaces.gameframe.config.companion_pet_interfaces
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Server-authoritative state and actions for the Account-tab companion/pet panel. */
class CompanionPetPanelScript
@Inject
constructor(
    private val companions: CompanionService,
    private val protectedAccess: ProtectedAccessLauncher,
    private val instances: EquipmentInstanceRegistry,
    private val objTypes: ObjTypeList,
    private val magicSpells: MagicSpellRegistry,
) : PluginScript() {
    private val selectedSlots = WeakHashMap<Player, Int>()
    private val scrollPositions = WeakHashMap<Player, Int>()

    override fun ScriptContext.startup() {
        onIfOpen(companion_pet_interfaces.management) { player.openCompanionPetPanel() }
        for (slot in 0 until 4) {
            for (mode in 0..2) onIfOverlayButton(
                companion_pet_components.mode_buttons[slot][mode]
            ) {
                player.setCompanionMode(slot + 1, mode)
            }
            onIfOverlayButton(companion_pet_components.slot_buttons[slot]) {
                player.selectCompanionSlot(slot + 1)
            }
            onIfOverlayButton(companion_pet_components.back_buttons[slot]) {
                player.showCompanionList()
            }
            onIfOverlayButton(companion_pet_components.activate_buttons[slot]) {
                player.activateCompanion(slot + 1)
            }
            onIfOverlayButton(companion_pet_components.resummon_buttons[slot]) {
                player.resummonCompanion(slot + 1)
            }
            onIfOverlayButton(companion_pet_components.talent_buttons[slot]) {
                player.chooseCompanionTalent(slot + 1)
            }
            onIfOverlayButton(companion_pet_components.spellbook_buttons[slot]) {
                player.chooseCompanionSpellbook(slot + 1)
            }
            onIfOverlayButton(companion_pet_components.autocast_buttons[slot]) {
                player.chooseCompanionAutocast(slot + 1)
            }
            onIfOverlayButton(companion_pet_components.scroll_up_buttons[slot]) {
                player.scrollBy(-CompanionPetInterfaceBuilder.SCROLL_STEP)
            }
            onIfOverlayButton(companion_pet_components.scroll_down_buttons[slot]) {
                player.scrollBy(CompanionPetInterfaceBuilder.SCROLL_STEP)
            }
        }
    }

    private fun Player.openCompanionPetPanel() {
        val ownerId = runCatching { characterId.toLong() }.getOrNull() ?: return
        companions.load(ownerId)
        selectedSlots.remove(this)
        scrollPositions.remove(this)
        refresh()
        setButtonEvents()
    }

    private fun Player.showCompanionList() {
        selectedSlots.remove(this)
        scrollPositions.remove(this)
        refresh()
    }

    private fun Player.selectCompanionSlot(slot: Int) {
        val companion = companions.owned(characterId.toLong()).firstOrNull { it.slot == slot }
        if (companion == null) {
            mes("That agent slot is empty.")
            return
        }
        selectedSlots[this] = slot
        scrollPositions[this] = 0
        refresh()
    }

    private fun Player.activateCompanion(slot: Int) {
        val companion = companionAt(slot) ?: return
        runCatching { companions.activate(characterId.toLong(), companion.id) }
            .onSuccess {
                mes("Active agent: ${it.name}.")
                selectedSlots[this] = slot
                refresh()
            }
            .onFailure { mes("This agent cannot be activated right now.") }
    }

    private fun Player.setCompanionMode(slot: Int, mode: Int) {
        val companion =
            companionAt(slot)
                ?: run {
                    mes("Select a companion first.")
                    return
                }
        val selected = CompanionCombatMode.entries.getOrNull(mode) ?: return
        runCatching { companions.setCombatMode(characterId.toLong(), companion.id, selected) }
            .onSuccess {
                selectedSlots[this] = slot
                mes("${it.name}: ${selected.name} mode.")
                refresh()
            }
            .onFailure { mes("That combat mode could not be saved.") }
    }

    private fun Player.resummonCompanion(slot: Int) {
        val companion = companionAt(slot) ?: return
        runCatching {
                companions.resummon(characterId.toLong(), companion.id, System.currentTimeMillis())
            }
            .onSuccess {
                mes("${it.name} has been summoned.")
                selectedSlots[this] = slot
                refresh()
            }
            .onFailure { mes("This agent is not ready to be summoned.") }
    }

    private fun Player.chooseCompanionTalent(slot: Int) {
        val opened = protectedAccess.launch(this) { chooseTalent(slot) }
        if (!opened) {
            mes("Finish the current action before choosing a talent.")
        }
    }

    private suspend fun ProtectedAccess.chooseTalent(slot: Int) {
        val companion = player.companionAt(slot) ?: return
        val definitions =
            CompanionTalentCatalog.definitions.filter {
                it.companionClass == companion.companionClass
            }
        val choice =
            menu(
                "Choose a ${companion.name} talent",
                hotkeys = false,
                choices = definitions.map { it.id },
            )
        val definition = definitions.getOrNull(choice) ?: return
        runCatching {
                companions.allocateTalent(player.characterId.toLong(), companion.id, definition.id)
            }
            .onSuccess {
                player.mes("${it.name}: ${definition.id} upgraded.")
                selectedSlots[player] = slot
                player.refresh()
            }
            .onFailure { player.mes("That talent is not available yet.") }
    }

    private fun Player.chooseCompanionSpellbook(slot: Int) {
        val opened = protectedAccess.launch(this) { chooseSpellbook(slot) }
        if (!opened) {
            mes("Finish the current action before changing the spellbook.")
        }
    }

    private suspend fun ProtectedAccess.chooseSpellbook(slot: Int) {
        val companion = player.companionAt(slot) ?: return
        val books = CompanionSpellbook.entries
        val choice =
            menu(
                "${companion.name} spellbook (current: ${companion.spellbook.name})",
                hotkeys = false,
                choices = books.map { "${it.name} Spellbook" } + "Cancel",
            )
        val book = books.getOrNull(choice) ?: return
        if (book == companion.spellbook) {
            player.mes("${companion.name} already uses the ${book.name} spellbook.")
            return
        }
        // The stored autocast only survives the switch when the spell belongs to the new
        // book - a Standard spell can never remain selected on Ancients and vice versa.
        val currentSpell = magicSpells.getAutocastSpell(companion.autocastSpellId)
        val stillValid = currentSpell != null && currentSpell.spellbook == book.toEngineBook()
        runCatching {
                companions.setSpellbook(player.characterId.toLong(), companion.id, book, stillValid)
            }
            .onSuccess {
                player.mes("${it.name} now uses the ${book.name} spellbook.")
                if (currentSpell != null && !stillValid) {
                    player.mes("Autocast cleared - ${currentSpell.name} is not in ${book.name}.")
                }
                player.refresh()
            }
            .onFailure { player.mes("That spellbook could not be saved.") }
    }

    private fun Player.chooseCompanionAutocast(slot: Int) {
        val opened = protectedAccess.launch(this) { chooseAutocast(slot) }
        if (!opened) {
            mes("Finish the current action before changing autocast.")
        }
    }

    private suspend fun ProtectedAccess.chooseAutocast(slot: Int) {
        val companion = player.companionAt(slot) ?: return
        val book = companion.spellbook.toEngineBook()
        // Only real registered autocast spells of the companion-owned book are offered; the
        // service re-validates the book and the companion's own level on selection.
        val options =
            magicSpells
                .autocastSpells()
                .entries
                .filter { it.value.spellbook == book }
                .sortedBy { it.value.levelReq }
                .toList()
        if (options.isEmpty()) {
            player.mes("No autocast spells exist in the ${companion.spellbook.name} spellbook.")
            return
        }
        val labels =
            options.map { (_, spell) ->
                if (companion.level >= spell.levelReq) {
                    "${spell.name} (Lv ${spell.levelReq})"
                } else {
                    "<col=ff0000>${spell.name} (requires Lv ${spell.levelReq})</col>"
                }
            }
        val selected =
            menu(
                "${companion.name} autocast (Lv ${companion.level} | ${companion.spellbook.name})",
                hotkeys = false,
                choices = labels + "Clear autocast" + "Cancel",
            )
        when {
            selected == labels.size -> {
                runCatching { companions.clearAutocast(player.characterId.toLong(), companion.id) }
                    .onSuccess {
                        player.mes("${it.name} will no longer autocast.")
                        player.refresh()
                    }
            }
            selected < labels.size -> {
                val (autocastId, spell) = options[selected]
                runCatching {
                        companions.setAutocastSpell(
                            player.characterId.toLong(),
                            companion.id,
                            autocastId,
                            companion.spellbook,
                            spell.levelReq,
                        )
                    }
                    .onSuccess {
                        player.mes("${it.name} will now autocast ${spell.name}.")
                        player.refresh()
                    }
                    .onFailure { err ->
                        player.mes(
                            "Cannot select that spell: ${err.message ?: "requirement not met"}."
                        )
                    }
            }
        }
    }

    private fun CompanionSpellbook.toEngineBook(): Spellbook =
        when (this) {
            CompanionSpellbook.STANDARD -> Spellbook.Standard
            CompanionSpellbook.ANCIENTS -> Spellbook.Ancients
        }

    private fun Player.refresh() {
        val ownerId = characterId.toLong()
        val owned = companions.owned(ownerId).associateBy { it.slot }
        val selected = selectedSlots[this]
        ifSetHide(companion_pet_components.list_page, hide = selected != null)
        for (slot in 1..4) {
            val companion = owned[slot]
            ifSetText(
                companion_pet_components.slot_texts[slot - 1],
                if (companion == null) "Slot $slot: Empty"
                else "Slot $slot: ${companion.name} Lv${companion.level}",
            )
            ifSetHide(companion_pet_components.detail_pages[slot - 1], hide = selected != slot)
            if (companion != null) {
                updateDetail(slot - 1, companion)
            }
        }
    }

    private fun Player.updateDetail(index: Int, companion: Companion) {
        ifSetText(companion_pet_components.detail_titles[index], companion.name)
        val lines = detailLines(companion)
        val pool = companion_pet_components.detail_lines[index]
        for ((i, component) in pool.withIndex()) {
            ifSetText(component, lines.getOrElse(i) { "" })
        }
        if (lines.size > pool.size) {
            ifSetText(pool.last(), "<col=9a8b76>...</col>")
        }
        val pos = (scrollPositions[this] ?: 0).coerceIn(0, CompanionPetInterfaceBuilder.MAX_SCROLL)
        scrollPositions[this] = pos
        ifSetScrollPos(companion_pet_components.detail_contents[index], pos)
    }

    private fun detailLines(companion: Companion): List<String> = buildList {
        add("<col=ff981f>${companion.companionClass.name} agent</col>")
        add("${companion.state.name} ${if (companion.active) "ACTIVE" else "inactive"}")
        add("Level ${companion.level}")
        add("")
        add("<col=ff981f>Combat & Magic:</col>")
        add("Mode <col=ffdc00>${companion.combatMode.name}</col>")
        add("Style <col=ffdc00>${companion.attackStyle.name}</col>")
        add("Weapon <col=ffdc00>${weaponStyleLabel(companion)}</col>")
        add("Spellbook <col=ffdc00>${companion.spellbook.name}</col>")
        add(
            "Autocast <col=ffdc00>" +
                (magicSpells.getAutocastSpell(companion.autocastSpellId)?.name ?: "None") +
                "</col>"
        )
        add("XP ${companion.experience}")
        add("HP ${companion.hitpoints}/${companion.maximumHitpoints}")
        add("")
        add("<col=ff981f>Combat stats:</col>")
        val combat = CompanionGearCalculator.calculate(companion, instances)
        add("Damage x${"%.2f".format(combat.damageMultiplier)}")
        add("Support x${"%.2f".format(combat.supportPower)}")
        add("Atk every ${combat.attackDelay} ticks")
        add("Eff max HP ${combat.maximumHitpoints}")
        add("")
        add("<col=ff981f>Gear (${companion.gearInstanceIds.size}/8):</col>")
        if (companion.gearInstanceIds.isEmpty()) {
            add("<col=9a8b76>  empty</col>")
        } else {
            for (instanceId in companion.gearInstanceIds) {
                val instance = instances[instanceId]
                if (instance == null) {
                    add("<col=9a8b76>  unknown item #$instanceId</col>")
                    continue
                }
                val name = objTypes[instance.templateObj]?.name ?: "item ${instance.templateObj}"
                add("<col=ffdc00>  $name</col>")
                add(
                    "<col=9a8b76>    ${instance.rarity.name} ${instance.tier.name} " +
                        "i${instance.itemLevel}</col>"
                )
                for (affix in instance.affixes) {
                    add("<col=6b6154>    +${affix.magnitude} ${affix.stat.name}</col>")
                }
                for (effectId in instance.uniqueEffectIds) {
                    add("<col=6b6154>    fx: $effectId</col>")
                }
            }
        }
        add("")
        val spent =
            companion.talents.sumOf {
                (CompanionTalentCatalog.byId[it.definitionId]?.pointsPerRank ?: 0) * it.ranks
            }
        add(
            "<col=ff981f>Talents (${(companion.talentPoints - spent).coerceAtLeast(0)}/${companion.talentPoints}):</col>"
        )
        if (companion.talents.isEmpty()) {
            add("<col=9a8b76>  none</col>")
        } else {
            for (talent in companion.talents) {
                val def = CompanionTalentCatalog.byId[talent.definitionId]
                val display =
                    talent.definitionId.split('-').joinToString(" ") {
                        it.replaceFirstChar(Char::uppercase)
                    }
                add(
                    "<col=ffdc00>  $display</col> <col=9a8b76>r${talent.ranks}/${def?.maxRanks ?: "?"}</col>"
                )
                if (def != null) {
                    add("<col=6b6154>    ${def.effectKey}</col>")
                }
            }
        }
    }

    /**
     * The combat style implied by the companion's equipped weapon - the same `WeaponCategory`
     * detection the combat runtime applies, resolved from the right-hand gear instance. `Unarmed`
     * reports the configured style so the row always reflects what the companion will actually
     * fight with.
     */
    private fun weaponStyleLabel(companion: Companion): String {
        val weaponType =
            companion.gearInstanceIds
                .asSequence()
                .mapNotNull { instances[it]?.templateObj }
                .mapNotNull { objTypes[it] }
                .firstOrNull { Wearpos[it.wearpos1] == Wearpos.RightHand }
        return weaponType?.let { companionWeaponStyle(it.weaponCategory).name } ?: "Unarmed"
    }

    private fun Player.scrollBy(delta: Int) {
        val slot = selectedSlots[this] ?: return
        val pos = (scrollPositions[this] ?: 0) + delta
        val clamped = pos.coerceIn(0, CompanionPetInterfaceBuilder.MAX_SCROLL)
        scrollPositions[this] = clamped
        ifSetScrollPos(companion_pet_components.detail_contents[slot - 1], clamped)
    }

    private fun Player.setButtonEvents() {
        val buttons =
            companion_pet_components.slot_buttons +
                companion_pet_components.back_buttons +
                companion_pet_components.activate_buttons +
                companion_pet_components.resummon_buttons +
                companion_pet_components.talent_buttons +
                companion_pet_components.scroll_up_buttons +
                companion_pet_components.scroll_down_buttons +
                companion_pet_components.mode_buttons.flatten() +
                companion_pet_components.spellbook_buttons +
                companion_pet_components.autocast_buttons
        // Clicks arrive with `comsub >= 0` (the resolved child slot), so the runtime range must
        // cover every slot - `-1..-1` is stored as the whole-component range.
        for (button in buttons) ifSetEvents(button, -1..-1, IfEvent.Op1)
    }

    private fun Player.companionAt(slot: Int): Companion? =
        companions.owned(characterId.toLong()).firstOrNull { it.slot == slot }
}
