@file:OptIn(InternalApi::class, UncheckedType::class)

package org.rsmod.content.other.commands

import jakarta.inject.Inject
import java.util.Base64
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.isWilderness
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.companion.CombatTelemetryEvent
import org.rsmod.api.companion.Companion
import org.rsmod.api.companion.CompanionAbilityCatalog
import org.rsmod.api.companion.CompanionAttackStyle
import org.rsmod.api.companion.CompanionBehaviour
import org.rsmod.api.companion.CompanionClass
import org.rsmod.api.companion.CompanionCombatContext
import org.rsmod.api.companion.CompanionCombatMode
import org.rsmod.api.companion.CompanionInspectPresenter
import org.rsmod.api.companion.CompanionMitigation
import org.rsmod.api.companion.CompanionPlayerRegistry
import org.rsmod.api.companion.CompanionRules
import org.rsmod.api.companion.CompanionService
import org.rsmod.api.companion.CompanionSkill
import org.rsmod.api.companion.CompanionSkillService
import org.rsmod.api.companion.CompanionSpellbook
import org.rsmod.api.companion.CompanionStatCache
import org.rsmod.api.companion.CompanionStatAugmenter
import org.rsmod.api.companion.CompanionStatSheet
import org.rsmod.api.companion.CompanionState
import org.rsmod.api.companion.CompanionTalentCatalog
import org.rsmod.api.companion.CompanionTarget
import org.rsmod.api.companion.CompanionTargetPriority
import org.rsmod.api.companion.CompanionTelemetryService
import org.rsmod.api.companion.combatLevels
import org.rsmod.api.companion.isMagicWeapon
import org.rsmod.api.config.refs.BaseInvs
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.spotanims
import org.rsmod.api.config.refs.stats
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.equipment.instance.EquipmentInstance
import org.rsmod.api.equipment.instance.EquipmentInstanceRegistry
import org.rsmod.api.equipment.instance.EquipmentInstanceService
import org.rsmod.api.equipment.instance.EquipmentStat
import org.rsmod.api.equipment.instance.EquipmentTier
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.invtx.invMoveAll
import org.rsmod.api.invtx.invTransfer
import org.rsmod.api.npc.attackOp
import org.rsmod.api.npc.isAttackable
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.events.interact.PlayerUEvents
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.interact.NpcTInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetObj
import org.rsmod.api.player.ui.ifSetScrollPos
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.random.CoreRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.script.advanced.onOpPlayer1
import org.rsmod.api.script.advanced.onOpPlayer5
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpPlayerU
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.content.other.commands.ui.CompanionEquipmentInterfaceBuilder
import org.rsmod.content.other.commands.ui.CompanionTalentsInterfaceBuilder
import org.rsmod.content.other.commands.ui.companion_behaviour_components
import org.rsmod.content.other.commands.ui.companion_behaviour_interfaces
import org.rsmod.content.other.commands.ui.companion_combat_components
import org.rsmod.content.other.commands.ui.companion_combat_interfaces
import org.rsmod.content.other.commands.ui.companion_dashboard_components
import org.rsmod.content.other.commands.ui.companion_dashboard_interfaces
import org.rsmod.content.other.commands.ui.companion_equipment_components
import org.rsmod.content.other.commands.ui.companion_equipment_interfaces
import org.rsmod.content.other.commands.ui.companion_inspect_components
import org.rsmod.content.other.commands.ui.companion_inspect_interfaces
import org.rsmod.content.other.commands.ui.companion_talents_components
import org.rsmod.content.other.commands.ui.companion_talents_interfaces
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.interact.InteractionNpc
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionNpcT
import org.rsmod.game.interact.InteractionPlayer
import org.rsmod.game.interact.InteractionPlayerOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.game.type.util.UncheckedType
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Chebyshev distance beyond which a companion catch-up teleports next to its owner. */
private const val CATCH_UP_DISTANCE = 12

/**
 * Bridges the owner-bound companion domain to the r239 NPC & Player Bot lifecycle.
 *
 * Features:
 * 1. Automatic companion adventurer / boss pet assignment on login.
 * 2. Virtual Player Bot companion with fully visible equipment rendering in 3D.
 * 3. Follower pathfinding, leashing & catch-up teleportation.
 * 4. Authentic OSRS Equipment & Stats Screen (Interface 84) via right-click "Equipment".
 * 5. WoW 3.3.5a Talent Tree system via right-click "Talents" (Specs, Tiers, Ranks & Reset).
 * 6. 30-slot Beast of Burden pack opened in OSRS Shop UI via right-click "Inventory".
 * 7. Humanoid Adventurers (visible armor, weapons, capes) and Boss Pets (transmog).
 * 8. Combat leveling system (1-80) with combat XP, HP scaling, level-up fireworks & announcements.
 * 9. Modern 512x334 Centered Modal Companion Dashboard UI.
 */
public class CompanionRuntimeScript
@Inject
constructor(
    private val companions: CompanionService,
    private val players: PlayerList,
    private val npcList: NpcList,
    private val npcTypes: NpcTypeList,
    private val objTypes: ObjTypeList,
    private val npcRegistry: NpcRegistry,
    private val npcInteractions: NpcInteractions,
    private val npcTInteractions: NpcTInteractions,
    private val spellRegistry: MagicSpellRegistry,
    private val equipmentInstances: EquipmentInstanceRegistry,
    private val collision: CollisionFlagMap,
    private val protectedAccess: ProtectedAccessLauncher,
    private val equipmentInstanceService: EquipmentInstanceService,
    private val storageShop: CompanionStorageShopScript,
    private val companionPlayerManager: CompanionPlayerManager,
    private val companionPlayerRegistry: CompanionPlayerRegistry,
    private val companionAutoLoot: CompanionAutoLoot,
    private val companionEmergencyHeal: CompanionEmergencyHeal,
    private val companionPersonality: CompanionPersonality,
    private val companionRoleSpells: CompanionRoleSpells,
    private val companionFollowMovement: CompanionFollowMovement,
    private val forgeScript: ForgeScript,
    private val mapClock: MapClock,
    private val skillXp: CompanionSkillService,
    private val telemetry: CompanionTelemetryService,
    private val fragmentAugmenters: Set<CompanionStatAugmenter>,
    @CoreRandom private val random: GameRandom,
) : PluginScript() {

    /** Per-companion derived-stat sheets; recomputed only when the companion record changes. */
    private val statCache = CompanionStatCache(fragmentAugmenters)

    /** The canonical stat sheet for [companion] - one pipeline for combat, AI and UI. */
    private fun statSheet(companion: Companion): CompanionStatSheet =
        statCache.sheet(companion, equipmentInstances, skillXp.levelsOf(companion.id))

    /** companion id -> engagement signature (target-mode) used to avoid reissuing interactions. */
    private val engagedModes = ConcurrentHashMap<Long, Int>()

    /** companion id -> bot combat-stat XP baseline for experience mirroring. */
    private val combatXpBaselines = ConcurrentHashMap<Long, Long>()

    /** companion id -> (style track -> bot stat XP baseline) for MELEE/RANGED/MAGIC awards. */
    private val styleXpBaselines = ConcurrentHashMap<Long, EnumMap<CompanionSkill, Long>>()

    /** companion id -> explicit NPC slot target commanded by player via "Attack (Companion)". */
    private val explicitTargets = ConcurrentHashMap<Long, Int>()

    /** owner id -> chosen companion id for single-target UI menus (dashboard, talents, gear). */
    private val selectedCompanionId = ConcurrentHashMap<Long, Long>()

    /** player -> talent list scroll position for the ▲/▼ steppers (server-side, no wheel hook). */
    private val talentScrollPositions = ConcurrentHashMap<Player, Int>()

    /**
     * Players viewing the companion equipment modal -> last seen gear stamp. A Forge mutation bumps
     * the equipped instance's revision, so the stamp change triggers a live refresh of the open
     * view instead of requiring a relog/reopen.
     */
    private val equipmentViewers = ConcurrentHashMap<Player, Long>()

    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.EngineLoginReady> {
            val ownerId = runCatching { player.characterId.toLong() }.getOrNull() ?: return@onEvent
            // A virtual companion Player can outlive the owner session when the client drops
            // during combat. Reusing that Player leaves it visible in the world but with a stale
            // client/movement state, so it cannot walk or attack after relog. Always rebuild the
            // runtime bot on login; the persisted Companion record remains the source of truth.
            companionPlayerManager.despawnAll(ownerId)
            companions.load(ownerId)
            sync(player)
        }

        onPlayerLogout {
            val ownerId =
                runCatching { player.characterId.toLong() }.getOrNull() ?: return@onPlayerLogout
            despawn(ownerId)
        }

        for (type in objTypes.values) {
            if (type.isEquipable) {
                onOpPlayerU(type) { equipItemOnCompanion(it) }
            }
        }

        onEvent<GameLifecycle.LateCycle> { players.forEach(::sync) }
        onEvent<NpcKilledEvent> {
            companionAutoLoot.onNpcKilled(this)
            onNpcKilledExperience(this)
        }

        // Register interaction ops for Virtual Player Companions.
        onOpPlayer1 { event ->
            val ownerId = player.characterId.toLong()
            val companionId = companionPlayerManager.getCompanionId(event.target)
            if (
                companionId != null &&
                    companionPlayerManager.getOwnerCharacterId(companionId) == ownerId
            ) {
                selectedCompanionId[ownerId] = companionId
                val active = companions.owned(ownerId).firstOrNull { it.id == companionId }
                if (active != null) {
                    openCompanionDashboard(active)
                }
            }
        }

        onOpPlayer5 { event ->
            val ownerId = player.characterId.toLong()
            val companionId = companionPlayerManager.getCompanionId(event.target)
            if (
                companionId != null &&
                    companionPlayerManager.getOwnerCharacterId(companionId) == ownerId
            ) {
                selectedCompanionId[ownerId] = companionId
                val active = companions.owned(ownerId).firstOrNull { it.id == companionId }
                if (active != null) {
                    protectedAccess.launch(player) { openCompanionCombatView(active) }
                }
            }
        }

        // Companion Dashboard Modal Handlers
        onIfOpen(companion_dashboard_interfaces.dashboard) {
            registerDashboardEvents(player)
            val active = activeCompanion(player)
            if (active != null) {
                refreshDashboard(player, active)
            }
        }
        onIfModalButton(companion_dashboard_components.close) { ifClose() }
        onIfModalButton(companion_dashboard_components.btn_equip) {
            val active = activeCompanion(player) ?: return@onIfModalButton
            ifClose()
            openCompanionEquipmentView(active)
        }
        onIfModalButton(companion_dashboard_components.btn_talents) {
            val active = activeCompanion(player) ?: return@onIfModalButton
            ifClose()
            openCompanionTalents(active)
        }
        onIfModalButton(companion_dashboard_components.btn_storage) {
            val active = activeCompanion(player) ?: return@onIfModalButton
            ifClose()
            storageShop.openStorage(player, active.name, active.packCapacity)
        }
        onIfModalButton(companion_dashboard_components.btn_upgrade) {
            val active = activeCompanion(player) ?: return@onIfModalButton
            ifClose()
            upgradePackDialog(active)
            val updated =
                companions.owned(player.characterId.toLong()).firstOrNull { it.id == active.id }
                    ?: active
            openCompanionDashboard(updated)
        }
        onIfModalButton(companion_dashboard_components.btn_style) {
            val active = activeCompanion(player) ?: return@onIfModalButton
            ifClose()
            openCompanionCombatView(active)
        }
        onIfModalButton(companion_dashboard_components.mode_def) {
            setCompanionCombatMode(player, CompanionCombatMode.DEFENSIVE)
        }
        onIfModalButton(companion_dashboard_components.mode_agg) {
            setCompanionCombatMode(player, CompanionCombatMode.AGGRESSIVE)
        }
        onIfModalButton(companion_dashboard_components.mode_pas) {
            setCompanionCombatMode(player, CompanionCombatMode.PASSIVE)
        }
        onIfModalButton(companion_dashboard_components.btn_morph) {
            val active = activeCompanion(player) ?: return@onIfModalButton
            ifClose()
            morphDialog(active)
        }
        onIfModalButton(companion_dashboard_components.btn_store_all) {
            depositAll(player)
            val active = activeCompanion(player)
            if (active != null) {
                refreshDashboard(player, active)
            }
        }
        onIfModalButton(companion_dashboard_components.btn_withdraw_all) {
            withdrawAll(player)
            val active = activeCompanion(player)
            if (active != null) {
                refreshDashboard(player, active)
            }
        }
        onIfModalButton(companion_dashboard_components.btn_summon) {
            val ownerId = player.characterId.toLong()
            val companion = selectedOrActiveCompanion(player) ?: return@onIfModalButton
            val bot = companionPlayerManager.getPlayer(companion.id)
            if (bot != null && bot.isSlotAssigned) {
                companionPersonality.sayDismiss(bot, player, companion)
                despawn(ownerId)
                player.mes("<col=ff9900>${companion.name} was dismissed.</col>")
            } else {
                if (summonCompanion(player, companion)) {
                    sync(player)
                    val summonedBot = companionPlayerManager.getPlayer(companion.id)
                    if (summonedBot != null) {
                        companionPersonality.saySummon(summonedBot, player, companion)
                    }
                    player.mes("<col=006600>${companion.name} was summoned to your side.</col>")
                }
            }
            val updated =
                companions.owned(ownerId).firstOrNull { it.id == companion.id } ?: companion
            refreshDashboard(player, updated)
        }
        onIfModalButton(companion_dashboard_components.btn_reset_talents) {
            val ownerId = player.characterId.toLong()
            val active = activeCompanion(player) ?: return@onIfModalButton
            companions.resetTalents(ownerId, active.id)
            player.mes("<col=006600>All talent points for ${active.name} have been reset!</col>")
            val updated = companions.owned(ownerId).firstOrNull { it.id == active.id } ?: active
            refreshDashboard(player, updated)
        }
        onIfModalButton(companion_dashboard_components.btn_autoloot) {
            companionAutoLoot.toggle(player)
            val active = activeCompanion(player) ?: return@onIfModalButton
            refreshDashboard(player, active)
        }
        onIfModalButton(companion_dashboard_components.btn_inspect) {
            val active = activeCompanion(player) ?: return@onIfModalButton
            ifClose()
            openCompanionInspect(active)
        }
        onIfModalButton(companion_dashboard_components.btn_behaviour) {
            val active = activeCompanion(player) ?: return@onIfModalButton
            ifClose()
            openCompanionBehaviour(active)
        }

        // Companion Talents Modal Handlers
        onIfOpen(companion_talents_interfaces.talents) {
            val active = activeCompanion(player) ?: return@onIfOpen
            talentScrollPositions[player] = 0
            player.ifSetScrollPos(companion_talents_components.talentScroll, 0)
            registerTalentEvents(player)
            refreshTalents(player, active)
        }
        onIfClose(companion_talents_interfaces.talents) { talentScrollPositions.remove(player) }
        onIfModalButton(companion_talents_components.close) { ifClose() }
        onIfModalButton(companion_talents_components.reset) { resetCompanionTalents() }
        onIfModalButton(companion_talents_components.scrollUp) {
            scrollTalentsBy(-CompanionTalentsInterfaceBuilder.SCROLL_STEP)
        }
        onIfModalButton(companion_talents_components.scrollDown) {
            scrollTalentsBy(CompanionTalentsInterfaceBuilder.SCROLL_STEP)
        }
        for ((index, slot) in companion_talents_components.abilitySlots.withIndex()) {
            onIfModalButton(slot) { toggleAbilityLoadout(index) }
        }
        for ((index, hit) in companion_talents_components.cardHits.withIndex()) {
            onIfModalButton(hit) { trainCompanionTalent(index) }
        }
        for ((index, roleHit) in companion_talents_components.roleHits.withIndex()) {
            onIfModalButton(roleHit) { switchCompanionRole(CompanionClass.entries[index]) }
        }

        // Companion Equipment & Stats Modal Handlers
        onIfOpen(companion_equipment_interfaces.equipment) {
            val active = activeCompanion(player) ?: return@onIfOpen
            equipmentViewers[player] = Long.MIN_VALUE
            registerEquipmentEvents(player)
            refreshEquipmentView(player, active)
        }
        onIfClose(companion_equipment_interfaces.equipment) { equipmentViewers.remove(player) }
        onIfModalButton(companion_equipment_components.close) { ifClose() }
        onIfModalButton(companion_equipment_components.inventory) {
            opCompanionInventory(it.comsub, it.op)
        }
        for ((index, hit) in companion_equipment_components.slotHits.withIndex()) {
            onIfModalButton(hit) { opCompanionGearSlot(index, it.op) }
        }

        // Companion Inspect Modal Handlers
        onIfOpen(companion_inspect_interfaces.inspect) {
            val active = activeCompanion(player)
            if (active == null) {
                player.ifSetText(
                    companion_inspect_components.status,
                    "<col=ff3333>No active companion - summon one first.</col>",
                )
            } else {
                refreshInspectView(player, active)
            }
        }
        onIfModalButton(companion_inspect_components.close) { ifClose() }

        // Companion Behaviour Modal Handlers
        onIfOpen(companion_behaviour_interfaces.behaviour) {
            registerBehaviourEvents(player)
            val active = activeCompanion(player)
            if (active == null) {
                player.ifSetText(
                    companion_behaviour_components.subtitle,
                    "<col=ff3333>No active companion - summon one first.</col>",
                )
            } else {
                refreshBehaviourView(player, active)
            }
        }
        onIfModalButton(companion_behaviour_components.close) { ifClose() }
        for ((index, btn) in companion_behaviour_components.modeButtons.withIndex()) {
            onIfModalButton(btn) {
                setCompanionCombatMode(player, CompanionCombatMode.entries[index])
                refreshBehaviourView(player, activeCompanion(player) ?: return@onIfModalButton)
            }
        }
        for ((index, btn) in companion_behaviour_components.targetButtons.withIndex()) {
            onIfModalButton(btn) {
                mutateBehaviour(player) {
                    it.copy(targetPriority = CompanionTargetPriority.entries[index])
                }
            }
        }
        for ((index, btn) in companion_behaviour_components.styleButtons.withIndex()) {
            onIfModalButton(btn) {
                setCompanionAttackStyle(player, CompanionAttackStyle.entries[index])
                refreshBehaviourView(player, activeCompanion(player) ?: return@onIfModalButton)
            }
        }
        onIfModalButton(companion_behaviour_components.distMinus) {
            mutateBehaviour(player) { it.copy(followDistance = it.followDistance - 1) }
        }
        onIfModalButton(companion_behaviour_components.distPlus) {
            mutateBehaviour(player) { it.copy(followDistance = it.followDistance + 1) }
        }
        onIfModalButton(companion_behaviour_components.catchToggle) {
            mutateBehaviour(player) { it.copy(catchUpTeleport = !it.catchUpTeleport) }
        }
        onIfModalButton(companion_behaviour_components.tauntToggle) {
            mutateBehaviour(player) { it.copy(autoTaunt = !it.autoTaunt) }
        }
        onIfModalButton(companion_behaviour_components.defendToggle) {
            mutateBehaviour(player) { it.copy(autoDefend = !it.autoDefend) }
        }
        onIfModalButton(companion_behaviour_components.healToggle) {
            mutateBehaviour(player) { it.copy(autoHeal = !it.autoHeal) }
        }
        onIfModalButton(companion_behaviour_components.buffToggle) {
            mutateBehaviour(player) { it.copy(autoBuff = !it.autoBuff) }
        }
        onIfModalButton(companion_behaviour_components.healBelowMinus) {
            mutateBehaviour(player) { it.copy(healBelowPercent = it.healBelowPercent - 5) }
        }
        onIfModalButton(companion_behaviour_components.healBelowPlus) {
            mutateBehaviour(player) { it.copy(healBelowPercent = it.healBelowPercent + 5) }
        }

        // Companion Combat Style & Spells Modal Handlers
        onIfOpen(companion_combat_interfaces.combat) {
            val active = activeCompanion(player) ?: return@onIfOpen
            registerCombatEvents(player)
            refreshCombatView(player, active)
        }
        onIfModalButton(companion_combat_components.close) { ifClose() }
        onIfModalButton(companion_combat_components.back) {
            val active = activeCompanion(player) ?: return@onIfModalButton
            openCompanionDashboard(active)
        }
        onIfModalButton(companion_combat_components.mode_def) {
            setCompanionCombatMode(player, CompanionCombatMode.DEFENSIVE)
        }
        onIfModalButton(companion_combat_components.mode_agg) {
            setCompanionCombatMode(player, CompanionCombatMode.AGGRESSIVE)
        }
        onIfModalButton(companion_combat_components.mode_pas) {
            setCompanionCombatMode(player, CompanionCombatMode.PASSIVE)
        }
        onIfModalButton(companion_combat_components.style_melee) {
            setCompanionAttackStyle(player, CompanionAttackStyle.MELEE)
        }
        onIfModalButton(companion_combat_components.style_ranged) {
            setCompanionAttackStyle(player, CompanionAttackStyle.RANGED)
        }
        onIfModalButton(companion_combat_components.style_magic) {
            setCompanionAttackStyle(player, CompanionAttackStyle.MAGIC)
        }
        onIfModalButton(companion_combat_components.book_standard) {
            setCompanionSpellbook(player, CompanionSpellbook.STANDARD)
        }
        onIfModalButton(companion_combat_components.book_ancients) {
            setCompanionSpellbook(player, CompanionSpellbook.ANCIENTS)
        }
        onIfModalButton(companion_combat_components.cast_choose) {
            val active = activeCompanion(player) ?: return@onIfModalButton
            ifClose()
            autocastSelectDialog(active)
            val updated =
                companions.owned(player.characterId.toLong()).firstOrNull { it.id == active.id }
                    ?: active
            openCompanionCombatView(updated)
        }
        onIfModalButton(companion_combat_components.cast_clear) { clearCompanionAutocast(player) }

        // Ancient Pack Scroll (instant 100-slot upgrade)
        onOpHeld1(objs.trouver_parchment) { useAncientPackScroll(it.slot) }

        // Quick commands - player-facing companion management (reachable on every realm).
        playerCommand("pet", "Manage pet / summon to your side", ::cmdPet)
        playerCommand("petgear", "Open companion equipment screen", ::cmdPetGear)
        playerCommand("petequip", "Open companion equipment screen", ::cmdPetGear)
        playerCommand("pettalents", "Open companion talents screen", ::cmdPetTalents)
        playerCommand("pettalent", "Open companion talents screen", ::cmdPetTalents)
        playerCommand("petinv", "Open companion pack inventory", ::cmdPetStorage)
        playerCommand("petpack", "Open companion pack inventory", ::cmdPetStorage)
        playerCommand("petstore", "Store all inventory items into pet", ::cmdPetStore)
        playerCommand("petdeposit", "Store all inventory items into pet", ::cmdPetStore)
        playerCommand("petwithdraw", "Withdraw all items from pet into inventory", ::cmdPetWithdraw)
        playerCommand("pettake", "Withdraw all items from pet into inventory", ::cmdPetWithdraw)
        playerCommand("petcheck", "Check items in pet storage", ::cmdPetCheck)
        playerCommand("petupgrade", "Upgrade companion pack storage capacity", ::cmdPetUpgrade)
        playerCommand("petpackupgrade", "Upgrade companion pack storage capacity", ::cmdPetUpgrade)
        playerCommand("petresettalents", "Reset companion talent points", ::cmdPetResetTalents)
        playerCommand("petspellbook", "Open companion spellbook / autocast menu", ::cmdPetSpellbook)
        playerCommand("petmagic", "Open companion spellbook / autocast menu", ::cmdPetSpellbook)
        playerCommand("petmorph", "Change pet appearance (e.g. ::petmorph paladin)", ::cmdPetMorph)
        playerCommand("petloot", "Toggle companion auto-looting", ::cmdPetLoot)
        playerCommand("autoloot", "Toggle companion auto-looting", ::cmdPetLoot)
        playerCommand("petheal", "Toggle companion emergency healing", ::cmdPetHeal)
        playerCommand(
            "petattack",
            "Order companion and player to attack target NPC",
            ::cmdPetAttack,
        )
        playerCommand(
            "compattack",
            "Order companion and player to attack target NPC",
            ::cmdPetAttack,
        )
        playerCommand("petspells", "Open companion PvM role spellbook", ::cmdPetSpells)
        playerCommand("companionspells", "Open companion PvM role spellbook", ::cmdPetSpells)
        playerCommand("pets", "List and manage your 4-companion squad", ::cmdPets)
        playerCommand("squad", "List and manage your 4-companion squad", ::cmdPets)
        // Companion panel (RuneLite `Unforge Companion` sidebar) protocol + entry points.
        playerCommand(
            "companionstatus",
            "Stream the companion panel snapshot to the client plugin",
            ::cmdCompanionStatus,
        )
        playerCommand(
            "petselect",
            "Select which companion the companion UIs target",
            ::cmdPetSelect,
        ) {
            invalidArgs = "Use as ::petselect <companion id|slot|name>"
        }
        playerCommand("petmode", "Set companion combat mode", ::cmdPetMode) {
            invalidArgs = "Use as ::petmode <defensive|aggressive|passive>"
        }
        playerCommand("petsummon", "Summon the selected companion to your side", ::cmdPetSummon)
        playerCommand("petdismiss", "Dismiss your active companions", ::cmdPetDismiss)
        playerCommand("petinspect", "Open companion inspect screen", ::cmdPetInspect)
        playerCommand("petbehaviour", "Open companion behaviour screen", ::cmdPetBehaviour)
        // Dev-only commands stay admin-gated.
        onCommand("petrevive", "Dev: instantly revive an incapacitated companion", ::cmdPetRevive)
        onCommand("petxp", "Award combat experience to companion", ::cmdPetXp)
    }

    private fun sync(player: Player) {
        refreshEquipmentViewIfStale(player)
        val ownerId = runCatching { player.characterId.toLong() }.getOrNull() ?: return
        if (!player.isValidTarget()) {
            despawn(ownerId)
            return
        }
        val activeList =
            companions.owned(ownerId).filter { it.active }.take(CompanionRules.ACTIVE_LIMIT)
        if (activeList.isEmpty()) {
            despawn(ownerId)
            return
        }

        // Clean up any bots that are no longer active
        val activeIds = activeList.map { it.id }.toSet()
        for (c in companions.owned(ownerId)) {
            if (c.id !in activeIds) {
                companionPlayerManager.despawn(c.id)
                engagedModes.remove(c.id)
                combatXpBaselines.remove(c.id)
                explicitTargets.remove(c.id)
            }
        }

        // Tick-scoped reservation set: tiles already assigned to a companion this cycle, so
        // two companions can never be given the same follow destination at once.
        val followReservations = mutableSetOf<CoordGrid>()

        for (active in activeList) {
            val bot =
                companionPlayerManager.getPlayer(active.id)
                    ?: companionPlayerManager.spawn(player, active)
                    ?: continue

            // Mirror any damage suffered by the bot - including hits taken while following with
            // no combat target - before any further processing. A companion that reached zero
            // hitpoints transitions to INCAPACITATED here and skips follow/combat entirely.
            val active = syncBotVitals(player, active, bot) ?: continue

            // Always mirror accumulated combat stat XP into companion experience immediately
            awardCombatExperience(player, active, bot)

            // Tactical Emergency Healing & Potion Feeding check
            companionEmergencyHeal.checkEmergencyHeal(player, active, bot)

            // Wilderness transition detection and overhead danger warning
            if (player.coords.isWilderness()) {
                companionPersonality.sayWildernessEntered(
                    bot,
                    player,
                    active,
                    mapClock.cycle.toLong(),
                )
            }

            val explicitTargetNpc =
                explicitTargets[active.id]?.let(npcList::get)?.takeIf {
                    it.isValidTarget() && it.isAttackable()
                }
            if (explicitTargetNpc == null) {
                explicitTargets.remove(active.id)
            }

            val ownerTarget =
                explicitTargetNpc?.let {
                    CompanionTarget(
                        it.slotId,
                        false,
                        it.hitpoints > 0,
                        true,
                        it.coords.chebyshevDistance(player.coords),
                        it.coords.x,
                        it.coords.z,
                        it.coords.level,
                        attackable = it.isAttackable(),
                    )
                }
                    ?: ownerTargetNpc(player)
                        ?.takeIf { it.isValidTarget() }
                        ?.let {
                            CompanionTarget(
                                it.slotId,
                                false,
                                it.hitpoints > 0,
                                true,
                                it.coords.chebyshevDistance(player.coords),
                                it.coords.x,
                                it.coords.z,
                                it.coords.level,
                                attackable = it.isAttackable(),
                            )
                        }
            val nearby =
                npcRegistry
                    .findAll(ZoneKey.from(player.coords))
                    .filter { it !== ownerTargetNpc(player) && it.isValidTarget() }
                    .map {
                        // An NPC interacting with the owner through any player op other than
                        // "follow" counts as attacking - this feeds DEFENSIVE mode's
                        // `ownerUnderAttackTarget` fallback.
                        val ownerInteraction = it.interaction as? InteractionPlayer
                        CompanionTarget(
                            it.slotId,
                            false,
                            it.hitpoints > 0,
                            true,
                            it.coords.chebyshevDistance(player.coords),
                            it.coords.x,
                            it.coords.z,
                            it.coords.level,
                            attackable = it.isAttackable(),
                            attackingOwner =
                                ownerInteraction?.target === player &&
                                    (ownerInteraction as? InteractionPlayerOp)?.isFollowOp() != true,
                        )
                    }
                    .toList()
            val action =
                companions.tick(
                    ownerId,
                    active.id,
                    CompanionCombatContext(
                        true,
                        player.isValidTarget(),
                        true,
                        false,
                        ownerTarget,
                        ownerUnderAttackTarget = nearby.firstOrNull { it.attackingOwner },
                        ownerX = player.coords.x,
                        ownerY = player.coords.z,
                        ownerPlane = player.coords.level,
                    ),
                    nearby,
                )
            // Ranged penetration is a real outgoing-damage multiplier: it feeds the shared
            // `companionAdjustedDamage` hook in the player-vs-npc pipeline, so a RANGED-style
            // companion's hits scale with its canonical sheet value instead of a display-only
            // number.
            val penetrationMult =
                if (active.attackStyle == CompanionAttackStyle.RANGED) {
                    1.0 + statSheet(active).penetrationBps / 10_000.0
                } else {
                    1.0
                }
            companionPlayerRegistry.setDamageMultiplier(
                active.id,
                (action?.damageMultiplier ?: 1.0) * penetrationMult,
            )

            val target =
                action?.targetIds?.asSequence()?.mapNotNull(npcList::get)?.firstOrNull {
                    it.isValidTarget()
                }

            // Process role-specific PvM spellbook (Support heals/buffs, Tank taunts/defensives, DPS
            // bursts)
            companionRoleSpells.processRoleSpells(player, active, bot, target)

            if (action == null || target == null) {
                followOwner(player, bot, active, followReservations)
                continue
            }

            // The leash applies even mid-combat: companions never stay behind on their own.
            val distance = bot.coords.chebyshevDistance(player.coords)
            if (bot.coords.level != player.coords.level || distance > CATCH_UP_DISTANCE) {
                val occupied =
                    companionOccupiedTiles(player, excludeId = active.id) + followReservations
                catchUpTeleport(player, bot, active, occupied, followReservations)
                continue
            }

            if (target.visType.vislevel >= 100) {
                companionPersonality.sayBossEngaged(
                    bot,
                    player,
                    active,
                    target.visType.name,
                    mapClock.cycle.toLong(),
                )
            }

            val gear = statSheet(active).combat
            companionPlayerManager.applyCombatState(player, active, bot, gear.maximumHitpoints)
            engageTarget(player, bot, active, target)
        }
    }

    private fun followOwner(
        player: Player,
        bot: Player,
        active: Companion,
        reserved: MutableSet<CoordGrid>,
    ) {
        // Reaching follow mode means this cycle produced no combat action: drop any stale
        // interaction (an auto-acquired aggressive target, an owner fight that just ended or
        // a Passive-mode switch) so the companion actually stops fighting, and clear the
        // engage dedupe so a future engage re-issues the interaction cleanly.
        bot.clearInteraction()
        engagedModes.remove(active.id)
        val occupied = companionOccupiedTiles(player, excludeId = active.id) + reserved
        val distance = bot.coords.chebyshevDistance(player.coords)
        // Behaviour tab: with catch-up teleport disabled the companion keeps walking toward the
        // owner on any plane/distance instead of telejumping.
        if (
            bot.coords.level != player.coords.level ||
                (distance > CATCH_UP_DISTANCE && active.behaviour.catchUpTeleport)
        ) {
            catchUpTeleport(player, bot, active, occupied, reserved)
            return
        }
        val tile =
            companionFollowMovement.resolveTile(
                bot = bot,
                playerCoords = player.coords,
                slot = active.slot,
                occupied = occupied,
                requireRoute = true,
                followDistance = active.behaviour.followDistance,
            )
        // `null` means every usable tile is taken, blocked or unreachable: the companion
        // waits for the next cycle instead of stacking onto someone.
        if (tile != null) {
            if (tile != bot.coords) {
                bot.routeRequest = RouteRequestCoord(tile)
                reserved += tile
            }
        }
        bot.facePlayer(player)
    }

    /**
     * Catch-up teleport for companions left beyond the leash distance or on another level. Uses the
     * same centralised tile selection as regular following; when no free tile exists the bot waits
     * for the next cycle instead of landing on top of someone.
     */
    private fun catchUpTeleport(
        player: Player,
        bot: Player,
        active: Companion,
        occupied: Set<CoordGrid>,
        reserved: MutableSet<CoordGrid>,
    ) {
        val tile =
            companionFollowMovement.resolveTile(
                bot = bot,
                playerCoords = player.coords,
                slot = active.slot,
                occupied = occupied,
                requireRoute = false,
                followDistance = active.behaviour.followDistance,
            )
        val fallbackOffset = formationOffset(active.slot)
        val fallback = player.coords.translate(fallbackOffset.first, fallbackOffset.second)
        val destination =
            tile
                ?: followCandidates(player.coords, active.slot, active.behaviour.followDistance)
                    .firstOrNull { it !in occupied }
                ?: fallback
        // A teleport can arrive before the destination region's collision zones have been
        // published to the routefinder. Do not leave the virtual player stranded in the old
        // region in that window: login/spawn does not have this problem because it starts beside
        // the owner. The fallback is deliberately adjacent to the owner and is only used for a
        // catch-up teleport, never for ordinary walking.
        bot.clearInteraction()
        bot.routeRequest = null
        PathingEntityCommon.telejump(bot, collision, destination)
        reserved += destination
        bot.facePlayer(player)
    }

    /**
     * Tiles currently stood on by the owner and every OTHER active companion bot. The companion
     * being moved ([excludeId]) is left out so its own tile never blocks it, while every other
     * companion's tile always counts as taken.
     */
    private fun companionOccupiedTiles(player: Player, excludeId: Long): Set<CoordGrid> {
        val ownerId =
            runCatching { player.characterId.toLong() }.getOrNull() ?: return setOf(player.coords)
        return buildSet {
            add(player.coords)
            for (companion in companions.owned(ownerId)) {
                if (!companion.active || companion.id == excludeId) {
                    continue
                }
                val other = companionPlayerManager.getPlayer(companion.id) ?: continue
                add(other.coords)
            }
        }
    }

    /**
     * Mirrors damage suffered by the bot back into the persisted companion record. Domain hitpoints
     * are authoritative; a bot reaching 0 hitpoints incapacitates the companion, which makes it
     * inactive and despawns it on the next sync.
     */
    private fun syncBotVitals(player: Player, companion: Companion, bot: Player): Companion? {
        // Already-dead companions never re-enter follow/combat processing. The despawn itself is
        // handled by the active-list cleanup on the next sync cycle.
        if (companion.state == CompanionState.INCAPACITATED) {
            return null
        }
        val botHp = bot.hitpoints
        if (botHp >= companion.hitpoints) {
            return companion
        }
        val rawDamage = companion.hitpoints - botHp
        val abilityMultiplier = companions.incomingDamageMultiplier(companion.id)
        var mitigatedDamage = (rawDamage * abilityMultiplier).toInt().coerceAtLeast(0)
        // Canonical mitigation pipeline: style resistance -> flat damage reduction -> block roll,
        // all resolved from the single stat sheet. The attacker's `npc_attack_style` param picks
        // the resistance row; an un-attacked hit (poison-style hp loss) applies no style
        // resistance but still benefits from DR and block.
        val sheet = statSheet(companion)
        val attackStyle = attackStyleAgainst(bot)
        mitigatedDamage =
            CompanionMitigation.resolve(mitigatedDamage, sheet, attackStyle, random.of(10_000))
        val prevented = rawDamage - mitigatedDamage
        if (prevented > 0) {
            skillXp.awardProtection(player.characterId.toLong(), companion.id, prevented)
        }
        val updated =
            companions.damage(
                player.characterId.toLong(),
                companion.id,
                mitigatedDamage,
                System.currentTimeMillis(),
            )
        return if (updated.state == CompanionState.INCAPACITATED) {
            player.mes(
                "<col=ff0000>${updated.name} has fallen! " +
                    "It can be summoned again in 5 minutes.</col>"
            )
            null
        } else {
            updated
        }
    }

    /**
     * The `npc_attack_style` param (`0`=melee, `1`=ranged, `2`=magic) of an npc currently
     * interacting with [bot], or `-1` when no attacker is found (e.g. poison-style hp loss).
     */
    private fun attackStyleAgainst(bot: Player): Int =
        npcRegistry
            .findAll(ZoneKey.from(bot.coords))
            .firstOrNull { (it.interaction as? InteractionPlayer)?.target === bot }
            ?.visType
            ?.paramOrNull(params.npc_attack_style) ?: -1

    /** Keeps the bot engaged on [target] through the shared player interaction pipeline. */
    private fun engageTarget(player: Player, bot: Player, companion: Companion, target: Npc) {
        if (!target.isValidTarget() || !target.isAttackable()) {
            return
        }
        val spell = resolveCompanionAutocast(player, bot, companion)
        val mode =
            if (spell != null) (companion.autocastSpellId.takeIf { it > 0 } ?: spell.obj.id) else 0
        val current = bot.interaction as? InteractionNpc
        // Melee must be allowed to re-issue the attack interaction. Its route can be interrupted
        // by target movement, owner movement, or a rejected step while the interaction object
        // still points at the same NPC. Treating that stale interaction as complete strands the
        // companion until a respawn/relog rebuilds its player state. Ranged and magic can keep the
        // old dedupe because their attack interaction does not require closing distance.
        if (
            spell != null &&
                current != null &&
                current.uid == target.uid &&
                engagedModes[companion.id] == mode
        ) {
            return
        }
        if (spell != null) {
            // The spell-on-target path: identical to a player casting a spell onto an npc. The
            // interaction processor walks the bot into the spell's ap range (with line-of-sight
            // checks) and PvNCombatScript runs the registered SpellAttack pipeline, which
            // validates the book and level; companion spell resources are unlimited.
            npcTInteractions.interact(bot, target, spell.component, -1, null)
        } else {
            // The standard attack op: melee weapons trigger within touch range, ranged weapons
            // within their `attackrange` param - the same positioning rules as real players.
            npcInteractions.interact(bot, target, target.attackOp())
        }
        engagedModes[companion.id] = mode
    }

    /**
     * Resolves the companion's persisted autocast selection to a castable spell. If no explicit
     * autocast spell has been configured, automatically falls back to the highest tier combat spell
     * matching the companion's active spellbook and level.
     *
     * The spell pipeline is only available to companions that either fight with the configured
     * `MAGIC` attack style or carry a spell-capable weapon detected from their own equipment
     * (powered staves, regular staves and bladed staves). When the gate fails, or when no spell in
     * the companion-owned book validates, the caller falls back to the standard weapon pipeline
     * instead of breaking combat.
     */
    private fun resolveCompanionAutocast(
        player: Player,
        bot: Player,
        companion: Companion,
    ): MagicSpell? {
        if (companion.attackStyle != CompanionAttackStyle.MAGIC && !hasMagicWeapon(bot)) {
            return null
        }
        val book = companion.spellbook.toEngineBook()
        if (companion.autocastSpellId > 0) {
            val spell = spellRegistry.getAutocastSpell(companion.autocastSpellId)
            if (spell != null && spell.spellbook == book && companion.level >= spell.levelReq) {
                return spell
            }
        }
        return spellRegistry
            .combatSpells()
            .filter { it.spellbook == book && companion.level >= it.levelReq }
            .maxByOrNull { it.levelReq }
    }

    /**
     * Detects spell-capable weapons (powered staves, regular staves, bladed staves) from the
     * companion's own worn equipment - the same `WeaponCategory` metadata the player combat
     * pipeline uses. Only the companion's own gear is inspected, never the owner's.
     */
    private fun hasMagicWeapon(bot: Player): Boolean {
        val weaponType = objTypes.getOrNull(bot.righthand) ?: return false
        return isMagicWeapon(weaponType.weaponCategory)
    }

    /** Mirrors the bot's accumulated combat stat XP into companion experience. */
    private fun awardCombatExperience(player: Player, companion: Companion, bot: Player) {
        val total = combatXpStats.sumOf { bot.statMap.getXP(it).toLong() }
        val baseline = combatXpBaselines.put(companion.id, total) ?: total
        val delta = total - baseline
        if (delta > 0L) {
            awardExperience(player, companion, delta)
        }
        awardStyleExperience(player, companion, bot)
    }

    /**
     * Splits the bot's per-stat combat XP deltas onto the MELEE/RANGED/MAGIC progression tracks.
     * Attack+Strength XP counts as melee work, Ranged XP as ranged and Magic XP as magic - matching
     * the pipeline the companion actually fought with (its weapon/spell choice decides which bot
     * stats the shared combat code trained). Defence/Hitpoints deltas only feed the overall
     * companion level above. Damage telemetry rides the same delta.
     */
    private fun awardStyleExperience(player: Player, companion: Companion, bot: Player) {
        val ownerId = player.characterId.toLong()
        val baselines =
            styleXpBaselines.computeIfAbsent(companion.id) { EnumMap(CompanionSkill::class.java) }
        for ((skill, trackStats) in styleXpStats) {
            val total = trackStats.sumOf { bot.statMap.getXP(it).toLong() }
            val baseline = baselines.put(skill, total) ?: total
            val delta = total - baseline
            if (delta <= 0L) continue
            val style =
                when (skill) {
                    CompanionSkill.MELEE -> CompanionAttackStyle.MELEE
                    CompanionSkill.RANGED -> CompanionAttackStyle.RANGED
                    else -> CompanionAttackStyle.MAGIC
                }
            skillXp.awardDamageDealt(ownerId, companion.id, style, delta.toInt().coerceAtMost(250))
            telemetry.record(
                ownerId,
                CombatTelemetryEvent(
                    encounterId = companion.encounterId ?: 0L,
                    sourceId = companion.id,
                    ownerId = ownerId,
                    sourceName = companion.name,
                    targetId = 0L,
                    ability = skill.name.lowercase(),
                    damage = delta.toInt().coerceAtMost(Int.MAX_VALUE),
                ),
            )
        }
    }

    private fun CompanionSpellbook.toEngineBook(): Spellbook =
        when (this) {
            CompanionSpellbook.STANDARD -> Spellbook.Standard
            CompanionSpellbook.ANCIENTS -> Spellbook.Ancients
        }

    private fun awardExperience(player: Player, companion: Companion, amount: Long) {
        val oldLevel = companion.level
        val updated = companions.addExperience(player.characterId.toLong(), companion.id, amount)
        if (updated.level > oldLevel) {
            val levelsGained = updated.level - oldLevel
            player.mes(
                "<col=ff9900><shad=000000>Congratulations! Your companion ${updated.name} reached Level ${updated.level}!</shad></col>"
            )
            player.mes(
                "<col=006600>+${levelsGained} Talent Point(s) awarded! (Total: ${updated.talentPoints} pts, Max HP: +${levelsGained * 5})</col>"
            )
            val bot = companionPlayerManager.getPlayer(updated.id)
            if (bot != null && bot.isSlotAssigned) {
                bot.appearance.combatLevel = updated.level
                bot.spotanim(spotanims.fx_emote_party01_active)
                bot.rebuildAppearance()
                companionPersonality.sayLevelUp(bot, player, updated)
            }
            player.spotanim(spotanims.fx_emote_party01_active)
        }
    }

    /**
     * The npc the owner is actually fighting: an "attack" op interaction or a spell cast
     * (`InteractionNpcT`). Talk-to/Trade/Pickpocket style interactions do not count, so a defensive
     * companion never retaliates against npcs the owner is merely speaking to.
     */
    private fun ownerTargetNpc(player: Player): Npc? =
        when (val interaction = player.interaction) {
            is InteractionNpcOp ->
                interaction.target.takeIf {
                    it.visType.op
                        .getOrNull(interaction.op.slot - 1)
                        ?.equals("attack", ignoreCase = true) == true
                }
            is InteractionNpcT -> interaction.target.takeIf { it.isAttackable() }
            else -> null
        }

    private fun despawn(ownerId: Long) {
        companionPlayerManager.despawnAll(ownerId)
        for (companion in companions.owned(ownerId)) {
            engagedModes.remove(companion.id)
            combatXpBaselines.remove(companion.id)
            styleXpBaselines.remove(companion.id)
            explicitTargets.remove(companion.id)
        }
    }

    /**
     * Awards bonus kill experience to the companion when monsters are slain, ensuring companions
     * level up and gain talent points through PvM combat victories.
     */
    private fun onNpcKilledExperience(event: NpcKilledEvent) {
        val killer = event.killer
        val npc = event.npc
        val killerCompanionId = companionPlayerManager.getCompanionId(killer)
        val ownerId =
            if (killerCompanionId != null) {
                companionPlayerManager.getOwnerCharacterId(killerCompanionId)
            } else {
                runCatching { killer.characterId.toLong() }.getOrNull()
            } ?: return
        val player = players.firstOrNull { it.characterId.toLong() == ownerId } ?: killer
        val activeCompanions = companions.owned(ownerId).filter { it.active }
        val npcCombatLvl = npc.visType.vislevel.coerceAtLeast(1)
        val killBonusXp = (npcCombatLvl * 15L).coerceIn(30L, 5000L)

        for (active in activeCompanions) {
            val bot = companionPlayerManager.getPlayer(active.id) ?: continue
            if (!bot.isSlotAssigned) continue
            val distance = bot.coords.chebyshevDistance(npc.coords)
            if (distance > 16) continue

            if (explicitTargets[active.id] == npc.slotId) {
                explicitTargets.remove(active.id)
            }
            awardExperience(player, active, killBonusXp)
        }
    }

    /* Beast of Burden Storage Actions */

    private fun depositAll(player: Player) {
        val ownerId = player.characterId.toLong()
        val active = companions.owned(ownerId).firstOrNull { it.active }
        val maxCapacity = active?.packCapacity ?: 10
        val storage = player.invMap.getValue(BaseInvs.companion_storage)
        val occupied = storage.count { it != null }
        if (occupied >= maxCapacity) {
            player.mes(
                "Your companion's pack is already full ($occupied/$maxCapacity slots). Upgrade your companion's pack to carry more!"
            )
            return
        }
        var movedCount = 0
        for (slot in 0 until player.inv.size) {
            val item = player.inv[slot] ?: continue
            val currentOcc = storage.count { it != null }
            val isStackable = objTypes[item.id]?.isStackable ?: false
            val canStack = isStackable && storage.any { it?.id == item.id }
            if (!canStack && currentOcc >= maxCapacity) break
            val moved =
                player.invTransfer(
                    from = player.inv,
                    fromSlot = slot,
                    count = item.count,
                    into = storage,
                    strict = false,
                )
            if (moved.completed() > 0) {
                movedCount += moved.completed()
            }
        }
        val nowOccupied = storage.count { it != null }
        if (movedCount > 0) {
            player.mes(
                "<col=006600>Stored items in your companion's pack. ($nowOccupied/$maxCapacity slots used)</col>"
            )
        } else {
            player.mes("No items could be stored from your inventory.")
        }
    }

    private fun withdrawAll(player: Player) {
        val storage = player.invMap.getValue(BaseInvs.companion_storage)
        val occupied = storage.count { it != null }
        if (occupied == 0) {
            player.mes("Your companion is not carrying any items.")
            return
        }
        player.invMoveAll(from = storage, into = player.inv)
        val remaining = storage.count { it != null }
        player.mes(
            "<col=006600>Withdrew items from your companion. ($remaining remaining in pack)</col>"
        )
    }

    private fun depositSingleItem(player: Player, invSlot: Int) {
        val item = player.inv[invSlot] ?: return
        val itemType = objTypes[item.id]
        val itemName = itemType?.name ?: "item"
        val ownerId = player.characterId.toLong()
        val active = companions.owned(ownerId).firstOrNull { it.active }
        val maxCapacity = active?.packCapacity ?: 10
        val storage = player.invMap.getValue(BaseInvs.companion_storage)
        val occupied = storage.count { it != null }
        val isStackable = itemType?.isStackable ?: false
        val canStack = isStackable && storage.any { it?.id == item.id }
        if (!canStack && occupied >= maxCapacity) {
            player.mes(
                "Your companion's pack is full ($occupied/$maxCapacity slots). Upgrade your companion's pack to carry more!"
            )
            return
        }
        val moved =
            player.invTransfer(
                from = player.inv,
                fromSlot = invSlot,
                count = item.count,
                into = storage,
                strict = false,
            )
        if (moved.success) {
            val nowOccupied = storage.count { it != null }
            player.mes(
                "Stored ${item.count}x $itemName in pack ($nowOccupied/$maxCapacity slots used)."
            )
        } else {
            player.mes("Your companion's pack is full ($occupied/$maxCapacity slots).")
        }
    }

    private fun checkStorage(player: Player) {
        val ownerId = player.characterId.toLong()
        val active = companions.owned(ownerId).firstOrNull { it.active }
        val maxCapacity = active?.packCapacity ?: 10
        val storage = player.invMap.getValue(BaseInvs.companion_storage)
        val occupied = storage.count { it != null }
        if (occupied == 0) {
            player.mes("Your companion's pack is empty (0/$maxCapacity slots used).")
            return
        }
        player.mes("<col=006600>Companion Storage ($occupied/$maxCapacity slots used):</col>")
        val grouped = storage.filterNotNull().groupBy { it.id }
        grouped.entries.take(12).forEach { entry ->
            val type = objTypes[entry.key]
            val typeName = type?.name ?: "Item"
            val total = entry.value.sumOf { it.count.toLong() }
            player.mes(" - $typeName: ${total.formatAmount}")
        }
        if (grouped.size > 12) {
            player.mes(" ... and ${grouped.size - 12} more different items.")
        }
    }

    /* Dialogue Menus */

    private suspend fun ProtectedAccess.petMenuDialog(active: Companion) {
        val storage = player.invMap.getValue(BaseInvs.companion_storage)
        val occupied = storage.count { it != null }
        val nextTier = CompanionPackUpgradeCatalog.nextTier(active.packCapacity)
        val upgradeText =
            if (nextTier != null) {
                "Upgrade Pack (${active.packCapacity} -> ${nextTier.capacity} slots)"
            } else {
                "Pack Capacity (MAX 100 slots)"
            }
        val choice =
            menu(
                "${active.name} (Lv${active.level} ${active.companionClass.name} - Pack: $occupied/${active.packCapacity})",
                "Equipment (Equipment & Stats Screen)",
                "Talents (Talent Tree & Specs)",
                "Inventory (Beast of Burden Pack)",
                upgradeText,
                "Spellbook (Autocast & Combat Style)",
                "Appearance (Adventurers & Boss Pets)",
                "Quick Store All Inventory",
                "Quick Withdraw All Pack",
                "Never mind",
            )
        when (choice) {
            0 -> openCompanionEquipmentView(active)
            1 -> openCompanionTalents(active)
            2 -> storageShop.openStorage(player, active.name, active.packCapacity)
            3 -> upgradePackDialog(active)
            4 -> openCompanionCombatView(active)
            5 -> morphDialog(active)
            6 -> depositAll(player)
            7 -> withdrawAll(player)
            else -> {
                /* Dismiss */
            }
        }
    }

    private suspend fun ProtectedAccess.upgradePackDialog(active: Companion) {
        val ownerId = player.characterId.toLong()
        val currentCapacity = active.packCapacity
        val nextTier = CompanionPackUpgradeCatalog.nextTier(currentCapacity)
        if (nextTier == null) {
            player.mes(
                "<col=ff9900>${active.name}'s pack is already at the maximum capacity (100 slots)!</col>"
            )
            return
        }
        val costDesc = CompanionPackUpgradeCatalog.formatCost(nextTier)
        val choice =
            menu(
                "Upgrade ${active.name}'s Pack: Tier ${nextTier.tier} ($currentCapacity -> ${nextTier.capacity} slots)",
                "Confirm Upgrade: $costDesc",
                "View All Upgrade Tiers",
                "Back",
            )
        when (choice) {
            0 -> {
                if (!CompanionPackUpgradeCatalog.hasRequirements(player, nextTier)) {
                    player.mes(
                        "<col=ff0000>You do not have the required gold or materials for Tier ${nextTier.tier}!</col>"
                    )
                    player.mes("<col=ff0000>Requires: $costDesc (bank notes accepted).</col>")
                    return
                }
                CompanionPackUpgradeCatalog.consumeRequirements(player, nextTier)
                val updated = companions.setPackCapacity(ownerId, active.id, nextTier.capacity)
                player.spotanim(spotanims.fx_emote_party01_active)
                player.mes(
                    "<col=006600>Congratulations! ${active.name}'s pack capacity increased to ${updated.packCapacity} slots!</col>"
                )
            }
            1 -> {
                player.mes("<col=006600>=== Companion Pack Upgrade Progression ===</col>")
                for (tier in CompanionPackUpgradeCatalog.tiers) {
                    val status =
                        if (currentCapacity >= tier.capacity) "<col=33b532>[UNLOCKED]</col>"
                        else "<col=ff9900>[LOCKED]</col>"
                    player.mes(
                        " - Tier ${tier.tier} (${tier.capacity} slots) $status: ${CompanionPackUpgradeCatalog.formatCost(tier)}"
                    )
                }
                player.mes(
                    "<col=0000ff>Tip: Ancient pack scrolls instantly unlock 100 slots!</col>"
                )
            }
            else -> {
                /* Back */
            }
        }
    }

    private fun ProtectedAccess.useAncientPackScroll(slot: Int) {
        val ownerId = player.characterId.toLong()
        val active = companions.owned(ownerId).firstOrNull { it.active }
        if (active == null) {
            player.mes("You must have an active companion summoned to use the Ancient pack scroll!")
            return
        }
        if (active.packCapacity >= CompanionPackUpgradeCatalog.MAX_CAPACITY) {
            player.mes(
                "<col=ff9900>${active.name}'s pack is already at the maximum capacity (100 slots)!</col>"
            )
            return
        }
        if (!invDel(inv, objs.trouver_parchment, count = 1, slot = slot).success) {
            return
        }
        val updated =
            companions.setPackCapacity(ownerId, active.id, CompanionPackUpgradeCatalog.MAX_CAPACITY)
        player.spotanim(spotanims.fx_emote_party01_active)
        player.mes(
            "<col=006600>The ancient magic dissolves the scroll and infuses ${active.name}'s pack!</col>"
        )
        player.mes(
            "<col=0000ff>${active.name}'s pack has been expanded to maximum 100 slots!</col>"
        )
    }

    /**
     * Companion-owned spellbook configuration. Every selection is re-validated server-side via
     * [CompanionService]: the companion's own level, the companion-owned spellbook and the explicit
     * MAGIC combat-style capability gate spell selection - the owner's spellbook and stats are
     * never consulted.
     */
    private suspend fun ProtectedAccess.autocastSelectDialog(active: Companion) {
        val ownerId = player.characterId.toLong()
        val book = active.spellbook.toEngineBook()
        // The menu lists the real registered autocast spells of the companion-owned book - no
        // arbitrary ids can ever be submitted, and locked entries display their level
        // requirement and are rejected by the service if selected anyway.
        val options =
            spellRegistry
                .autocastSpells()
                .entries
                .filter { it.value.spellbook == book }
                .sortedBy { it.value.levelReq }
                .toList()
        if (options.isEmpty()) {
            player.mes("No autocast spells exist in the ${active.spellbook.name} spellbook.")
            return
        }
        val labels =
            options.map { (_, spell) ->
                if (active.level >= spell.levelReq) {
                    "${spell.name} (Lv ${spell.levelReq})"
                } else {
                    "<col=ff0000>${spell.name} (requires Lv ${spell.levelReq})</col>"
                }
            }
        val selected =
            menu(
                "Select Autocast (${active.name} Lv ${active.level} | ${active.spellbook.name})",
                *labels.toTypedArray(),
                "Cancel",
            )
        val (autocastId, spell) = options.getOrNull(selected) ?: return
        runCatching {
                companions.setAutocastSpell(
                    ownerId,
                    active.id,
                    autocastId,
                    active.spellbook,
                    spell.levelReq,
                )
            }
            .onSuccess {
                player.mes("<col=006600>${active.name} will now autocast ${spell.name}.</col>")
            }
            .onFailure { err ->
                player.mes("Cannot select that spell: ${err.message ?: "requirement not met"}.")
            }
    }

    private suspend fun ProtectedAccess.petMenuDialog(npc: Npc, active: Companion) =
        petMenuDialog(active)

    private suspend fun ProtectedAccess.openCompanionCombatView(active: Companion) {
        ifOpenMainModal(companion_combat_interfaces.combat)
        registerCombatEvents(player)
        refreshCombatView(player, active)
    }

    private fun registerCombatEvents(player: Player) {
        val buttons =
            listOf(
                companion_combat_components.mode_def,
                companion_combat_components.mode_agg,
                companion_combat_components.mode_pas,
                companion_combat_components.style_melee,
                companion_combat_components.style_ranged,
                companion_combat_components.style_magic,
                companion_combat_components.book_standard,
                companion_combat_components.book_ancients,
                companion_combat_components.cast_choose,
                companion_combat_components.cast_clear,
                companion_combat_components.back,
            )
        for (button in buttons) {
            player.ifSetEvents(button, -1..-1, IfEvent.Op1)
        }
    }

    private fun combatSel(selected: Boolean, label: String) =
        if (selected) "<col=ffb84d>$label</col>" else "<col=9a8b76>$label</col>"

    private fun refreshCombatView(player: Player, active: Companion, status: String? = null) {
        val autocastName = spellRegistry.getAutocastSpell(active.autocastSpellId)?.name ?: "None"
        player.ifSetText(companion_combat_components.title, "Combat Style & Spells")
        player.ifSetText(
            companion_combat_components.subtitle,
            "${active.name} - ${active.companionClass.name} Lv ${active.level}",
        )
        player.ifSetText(
            companion_combat_components.mode_def_text,
            combatSel(active.combatMode == CompanionCombatMode.DEFENSIVE, "Defensive"),
        )
        player.ifSetText(
            companion_combat_components.mode_agg_text,
            combatSel(active.combatMode == CompanionCombatMode.AGGRESSIVE, "Aggressive"),
        )
        player.ifSetText(
            companion_combat_components.mode_pas_text,
            combatSel(active.combatMode == CompanionCombatMode.PASSIVE, "Passive"),
        )
        player.ifSetText(
            companion_combat_components.mode_desc,
            when (active.combatMode) {
                CompanionCombatMode.DEFENSIVE -> "Fights your target and attackers."
                CompanionCombatMode.AGGRESSIVE -> "Hunts nearby enemies on its own."
                CompanionCombatMode.PASSIVE -> "Follows without fighting."
            },
        )
        player.ifSetText(
            companion_combat_components.style_melee_text,
            combatSel(active.attackStyle == CompanionAttackStyle.MELEE, "Melee"),
        )
        player.ifSetText(
            companion_combat_components.style_ranged_text,
            combatSel(active.attackStyle == CompanionAttackStyle.RANGED, "Ranged"),
        )
        player.ifSetText(
            companion_combat_components.style_magic_text,
            combatSel(active.attackStyle == CompanionAttackStyle.MAGIC, "Magic"),
        )
        player.ifSetText(
            companion_combat_components.book_standard_text,
            combatSel(active.spellbook == CompanionSpellbook.STANDARD, "Standard"),
        )
        player.ifSetText(
            companion_combat_components.book_ancients_text,
            combatSel(active.spellbook == CompanionSpellbook.ANCIENTS, "Ancients"),
        )
        player.ifSetText(
            companion_combat_components.cast_current,
            "$autocastName (${active.spellbook.name})",
        )
        if (status != null) {
            player.ifSetText(companion_combat_components.status, status)
        }
    }

    private fun setCompanionCombatMode(player: Player, mode: CompanionCombatMode) {
        val ownerId = player.characterId.toLong()
        val active = activeCompanion(player) ?: return
        val updated = companions.setCombatMode(ownerId, active.id, mode)
        player.mes("<col=006600>${updated.name} is now ${mode.name.lowercase()}.</col>")
        refreshCombatView(player, updated)
        refreshDashboard(player, updated)
    }

    private fun setCompanionAttackStyle(player: Player, style: CompanionAttackStyle) {
        val ownerId = player.characterId.toLong()
        val active = activeCompanion(player) ?: return
        val updated = companions.setAttackStyle(ownerId, active.id, style)
        val status =
            if (style != CompanionAttackStyle.MAGIC && active.autocastSpellId > 0) {
                "<col=ff9900>Autocast stays saved but dormant unless style is MAGIC.</col>"
            } else {
                "<col=33b532>${updated.name} now fights with ${style.name} attacks.</col>"
            }
        refreshCombatView(player, updated, status)
    }

    private fun setCompanionSpellbook(player: Player, book: CompanionSpellbook) {
        val ownerId = player.characterId.toLong()
        val active = activeCompanion(player) ?: return
        if (book == active.spellbook) {
            return
        }
        // If the persisted autocast is not a member of the new book it is cleared - a Standard
        // spell can never remain selected on Ancients and vice versa.
        val currentSpell = spellRegistry.getAutocastSpell(active.autocastSpellId)
        val stillValid = currentSpell != null && currentSpell.spellbook == book.toEngineBook()
        val updated = companions.setSpellbook(ownerId, active.id, book, stillValid)
        val status =
            if (currentSpell != null && !stillValid) {
                "<col=ff9900>Autocast cleared - ${currentSpell.name} is not in the ${book.name} book.</col>"
            } else {
                "<col=33b532>${updated.name} now uses the ${book.name} spellbook.</col>"
            }
        refreshCombatView(player, updated, status)
    }

    private fun clearCompanionAutocast(player: Player) {
        val ownerId = player.characterId.toLong()
        val active = activeCompanion(player) ?: return
        companions.clearAutocast(ownerId, active.id)
        val updated = companions.owned(ownerId).firstOrNull { it.id == active.id } ?: active
        refreshCombatView(
            player,
            updated,
            "<col=33b532>${updated.name} will no longer autocast.</col>",
        )
    }

    private suspend fun ProtectedAccess.openCompanionTalents(active: Companion) {
        ifOpenMainModal(companion_talents_interfaces.talents)
        registerTalentEvents(player)
        refreshTalents(player, active)
    }

    private fun registerTalentEvents(player: Player) {
        for (hit in companion_talents_components.cardHits) {
            player.ifSetEvents(hit, -1..-1, IfEvent.Op1)
        }
        for (hit in companion_talents_components.roleHits) {
            player.ifSetEvents(hit, -1..-1, IfEvent.Op1)
        }
        player.ifSetEvents(companion_talents_components.reset, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_talents_components.close, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_talents_components.scrollUp, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_talents_components.scrollDown, -1..-1, IfEvent.Op1)
    }

    /** Steps the talent list scroll position; if3 layers have no wheel hook in this client. */
    private fun ProtectedAccess.scrollTalentsBy(delta: Int) {
        val pos =
            ((talentScrollPositions[player] ?: 0) + delta).coerceIn(
                0,
                CompanionTalentsInterfaceBuilder.MAX_SCROLL,
            )
        talentScrollPositions[player] = pos
        ifSetScrollPos(companion_talents_components.talentScroll, pos)
    }

    private fun talentDefs(active: Companion) =
        CompanionTalentCatalog.definitions
            .filter { it.companionClass == active.companionClass }
            .sortedBy { it.tier }

    private fun refreshTalents(player: Player, active: Companion) {
        val spent = active.talents.sumOf { it.ranks }
        val available = (active.talentPoints - spent).coerceAtLeast(0)
        val defs = talentDefs(active)

        player.ifSetText(companion_talents_components.title, "${active.name} - Talents")
        player.ifSetText(
            companion_talents_components.subtitle,
            "${active.companionClass.name} | Points: $available/${active.talentPoints} | Abilities: ${active.abilityLoadout.size}/${CompanionRules.MAX_ABILITY_SLOTS}",
        )

        for (i in 0 until CompanionTalentsInterfaceBuilder.TALENT_ROWS) {
            val def = defs.getOrNull(i)
            if (def == null) {
                player.ifSetHide(companion_talents_components.cardWraps[i], hide = true)
                continue
            }
            val rank = active.talents.firstOrNull { it.definitionId == def.id }?.ranks ?: 0
            val unlocked = CompanionRules.tierUnlocked(def.tier, spent)
            player.ifSetHide(companion_talents_components.cardWraps[i], hide = false)
            player.ifSetText(
                companion_talents_components.cardNames[i],
                if (def.capstone) "<col=ffb84d>${def.id}</col>" else def.id,
            )
            player.ifSetText(companion_talents_components.cardTiers[i], "Tier ${def.tier}")
            player.ifSetText(
                companion_talents_components.cardRanks[i],
                when {
                    rank >= def.maxRanks -> "<col=33b532>MAX $rank/${def.maxRanks}</col>"
                    !unlocked -> "<col=ff3333>LOCKED</col>"
                    else -> "$rank/${def.maxRanks}"
                },
            )
            player.ifSetText(
                companion_talents_components.cardEffects[i],
                CompanionTalentCatalog.description(def),
            )
            player.ifSetHide(companion_talents_components.cardSels[i], hide = rank == 0)
        }

        for (i in 0 until CompanionTalentsInterfaceBuilder.ROLE_COUNT) {
            player.ifSetHide(
                companion_talents_components.roleSels[i],
                hide = CompanionClass.entries[i] != active.companionClass,
            )
        }
        for (i in companion_talents_components.abilityTexts.indices) {
            val ability = active.abilityLoadout.getOrNull(i)
            val cooldownMillis =
                ability?.let { companions.abilityCooldownRemainingMillis(active.id, it) } ?: 0L
            val cooldownText =
                if (cooldownMillis > 0L) " (${(cooldownMillis + 999L) / 1000L}s)" else ""
            player.ifSetText(
                companion_talents_components.abilityTexts[i],
                if (ability == null) "Ability ${i + 1}: Empty"
                else "Ability ${i + 1}: $ability$cooldownText",
            )
        }
    }

    private suspend fun ProtectedAccess.trainCompanionTalent(index: Int) {
        val ownerId = player.characterId.toLong()
        val active = activeCompanion(player) ?: return
        val def = talentDefs(active).getOrNull(index) ?: return
        val trainedRank = active.talents.firstOrNull { it.definitionId == def.id }?.ranks ?: 0
        val abilityId = def.abilityId
        if (abilityId != null && trainedRank > 0) {
            val loadout = active.abilityLoadout.toMutableList()
            if (abilityId in loadout) loadout.remove(abilityId)
            else if (loadout.size < CompanionRules.MAX_ABILITY_SLOTS) loadout += abilityId
            else {
                player.ifSetText(
                    companion_talents_components.status,
                    "<col=ff9900>All three ability slots are full.</col>",
                )
                return
            }
            runCatching { companions.setAbilityLoadout(ownerId, active.id, loadout) }
                .onSuccess { updated ->
                    player.ifSetText(
                        companion_talents_components.status,
                        "<col=33b532>Ability loadout updated.</col>",
                    )
                    refreshTalents(player, updated)
                }
                .onFailure {
                    player.ifSetText(
                        companion_talents_components.status,
                        "<col=ff3333>${it.message ?: "Cannot change ability loadout."}</col>",
                    )
                }
            return
        }
        val spent = active.talents.sumOf { it.ranks }
        val status = companion_talents_components.status
        when {
            !CompanionRules.tierUnlocked(def.tier, spent) ->
                player.ifSetText(
                    status,
                    "<col=ff3333>Locked - spend more points to unlock tier ${def.tier}.</col>",
                )
            active.talentPoints - spent <= 0 ->
                player.ifSetText(status, "<col=ff9900>No talent points available.</col>")
            else ->
                runCatching { companions.allocateTalent(ownerId, active.id, def.id) }
                    .onSuccess { updated ->
                        player.ifSetText(
                            status,
                            "<col=33b532>Trained ${def.id} (${def.effectKey}).</col>",
                        )
                        refreshTalents(player, updated)
                    }
                    .onFailure {
                        player.ifSetText(
                            status,
                            "<col=ff3333>${it.message ?: "Cannot train that talent."}</col>",
                        )
                    }
        }
    }

    private suspend fun ProtectedAccess.toggleAbilityLoadout(slot: Int) {
        val ownerId = player.characterId.toLong()
        val active = activeCompanion(player) ?: return
        val current = active.abilityLoadout.toMutableList()
        if (slot >= current.size) {
            player.ifSetText(
                companion_talents_components.status,
                "<col=ff9900>Train an active talent first, then click its card to equip it.</col>",
            )
            return
        }
        val abilityId = current[slot]
        val selectedTargetIds = ownerTargetNpc(player)?.slotId?.let(::listOf) ?: emptyList()
        runCatching {
                companions.useAbility(
                    ownerId,
                    active.id,
                    abilityId,
                    selectedTargetIds,
                    cooldownReductionBps = statSheet(active).cooldownReductionBps,
                )
            }
            .onSuccess { used ->
                val bot = companionPlayerManager.getPlayer(active.id)
                if (bot != null) {
                    val target = ownerTargetNpc(player)
                    companionRoleSpells.processManualAbility(player, active, bot, target, abilityId)
                    if (
                        target != null &&
                            used.effect.target ==
                                org.rsmod.api.companion.CompanionAbilityTarget.SINGLE_ENEMY
                    ) {
                        explicitTargets[active.id] = target.slotId
                        if (
                            used.effect.type ==
                                org.rsmod.api.companion.CompanionAbilityEffectType.MOVEMENT
                        ) {
                            PathingEntityCommon.telejump(bot, collision, target.coords)
                        }
                        engageTarget(player, bot, active, target)
                    }
                }
                player.ifSetText(
                    companion_talents_components.status,
                    "<col=33b532>${used.ability.name} activated (${used.effect.type.name.lowercase()}).</col>",
                )
                refreshTalents(player, active)
            }
            .onFailure {
                player.ifSetText(
                    companion_talents_components.status,
                    "<col=ff3333>${it.message ?: "Ability is not ready."}</col>",
                )
            }
    }

    private suspend fun ProtectedAccess.switchCompanionRole(role: CompanionClass) {
        val ownerId = player.characterId.toLong()
        val active = activeCompanion(player) ?: return
        if (role == active.companionClass) {
            player.ifSetText(
                companion_talents_components.status,
                "<col=9a8b76>Already specializing in ${role.name}.</col>",
            )
            return
        }
        val updated = companions.setClass(ownerId, active.id, role)
        player.mes(
            "<col=006600>${active.name} switched specialization to ${role.name}! Talent points reset.</col>"
        )
        refreshTalents(player, updated)
    }

    private suspend fun ProtectedAccess.resetCompanionTalents() {
        val ownerId = player.characterId.toLong()
        val active = activeCompanion(player) ?: return
        companions.resetTalents(ownerId, active.id)
        player.mes("<col=006600>All talent points for ${active.name} have been reset!</col>")
        val updated = companions.owned(ownerId).firstOrNull { it.id == active.id } ?: active
        refreshTalents(player, updated)
    }

    /** (wearpos slot, label) pairs in grid order - matches CompanionEquipmentInterfaceBuilder. */
    private val equipmentSlots: List<Pair<Int, String>> =
        listOf(
            0 to "Hat",
            1 to "Cape",
            2 to "Neck",
            3 to "Weapon",
            4 to "Chest",
            5 to "Shield",
            7 to "Legs",
            9 to "Gloves",
            10 to "Boots",
            12 to "Ring",
            13 to "Quiver",
        )

    private suspend fun ProtectedAccess.openCompanionEquipmentView(active: Companion) {
        ifOpenMainModal(companion_equipment_interfaces.equipment)
        // Bind the owner's live inventory directly to this modal so gear changes stay on this page.
        invTransmit(player.inv)
        interfaceInvInit(
            inv = player.inv,
            target = companion_equipment_components.inventory,
            objRowCount = CompanionEquipmentInterfaceBuilder.INVENTORY_COLUMNS,
            objColCount = CompanionEquipmentInterfaceBuilder.INVENTORY_ROWS,
            op1 = "Equip",
        )
        registerEquipmentEvents(player)
        refreshEquipmentView(player, active)
    }

    private fun registerEquipmentEvents(player: Player) {
        for (hit in companion_equipment_components.slotHits) {
            player.ifSetEvents(hit, -1..-1, IfEvent.Op1, IfEvent.Op2, IfEvent.Op3)
        }
        player.ifSetEvents(
            companion_equipment_components.inventory,
            player.inv.indices,
            IfEvent.Op1,
        )
        player.ifSetEvents(companion_equipment_components.close, -1..-1, IfEvent.Op1)
    }

    /**
     * Resolves what each wearpos cell shows: instances from `gearInstanceIds` first (the
     * service-managed source of truth), then the bot's visual `worn` array as a fallback so legacy
     * gear without instances still renders.
     */
    private fun resolveGearSlots(
        active: Companion,
        worn: Inventory?,
    ): Map<Int, Pair<UnpackedObjType, EquipmentInstance?>> {
        val out = mutableMapOf<Int, Pair<UnpackedObjType, EquipmentInstance?>>()
        for (id in active.gearInstanceIds) {
            val inst = equipmentInstances[id] ?: continue
            val type = objTypes[inst.templateObj] ?: continue
            val slot = Wearpos[type.wearpos1]?.slot ?: continue
            out[slot] = type to inst
        }
        if (worn != null) {
            for ((wearposSlot, _) in equipmentSlots) {
                if (wearposSlot in out) continue
                val obj = worn[wearposSlot] ?: continue
                val type = objTypes.getOrNull(obj) ?: continue
                out[wearposSlot] = type to null
            }
        }
        return out
    }

    private fun refreshEquipmentView(player: Player, active: Companion) {
        val gear = statSheet(active).combat
        val companionPlayer = companionPlayerManager.getPlayer(active.id)
        val slotItems = resolveGearSlots(active, companionPlayer?.worn)
        val comps = companion_equipment_components

        player.ifSetText(comps.title, "${active.name} - Equipment & Stats")
        player.ifSetText(
            comps.subtitle,
            "${active.companionClass.name} Lv${active.level} | ${active.attackStyle.name} | ${active.state.name}",
        )
        for (i in 0 until CompanionEquipmentInterfaceBuilder.SLOT_COUNT) {
            val (_, slotName) = equipmentSlots[i]
            val entry = slotItems[equipmentSlots[i].first]
            if (entry != null) {
                player.ifSetHide(comps.slotObjs[i], hide = false)
                player.ifSetObj(comps.slotObjs[i], entry.first, 560)
                player.ifSetText(comps.slotLabels[i], entry.first.name)
            } else {
                player.ifSetHide(comps.slotObjs[i], hide = true)
                player.ifSetText(comps.slotLabels[i], "<col=6b6154>$slotName</col>")
            }
        }

        val sums = mutableMapOf<EquipmentStat, Int>()
        val affixLines = mutableListOf<String>()
        for (id in active.gearInstanceIds) {
            val inst = equipmentInstances[id] ?: continue
            val itemName = objTypes[inst.templateObj]?.name ?: "item"
            for (affix in inst.affixes) {
                sums[affix.stat] = (sums[affix.stat] ?: 0) + affix.magnitude
                affixLines.add("$itemName: ${affix.stat.name} +${affix.magnitude}")
            }
        }
        fun total(vararg stats: EquipmentStat): Int = stats.sumOf { sums[it] ?: 0 }
        fun signed(value: Int): String = if (value < 0) "$value" else "+$value"

        player.ifSetText(
            comps.offLines[0],
            "Stab ${signed(total(EquipmentStat.AttackStab))}  Slash ${signed(total(EquipmentStat.AttackSlash))}  Crush ${signed(total(EquipmentStat.AttackCrush))}",
        )
        player.ifSetText(
            comps.offLines[1],
            "Magic ${signed(total(EquipmentStat.AttackMagic))}  Ranged ${signed(total(EquipmentStat.AttackRanged))}  STR ${signed(total(EquipmentStat.Strength))}",
        )
        player.ifSetText(
            comps.defLines[0],
            "Stab ${signed(total(EquipmentStat.DefenceStab))}  Slash ${signed(total(EquipmentStat.DefenceSlash))}  Crush ${signed(total(EquipmentStat.DefenceCrush))}",
        )
        player.ifSetText(
            comps.defLines[1],
            "Magic ${signed(total(EquipmentStat.DefenceMagic))}  Ranged ${signed(total(EquipmentStat.DefenceRanged))}  HP ${signed(total(EquipmentStat.MaximumHealth, EquipmentStat.EffectiveHealth))}",
        )
        player.ifSetText(
            comps.othLines[0],
            "Lifesteal ${signed(total(EquipmentStat.LifeSteal))}  Cooldown ${signed(total(EquipmentStat.Cooldown))}  Crit ${signed(total(EquipmentStat.CriticalRate))}",
        )
        player.ifSetText(
            comps.othLines[1],
            "Prayer ${signed(total(EquipmentStat.Prayer))}  DPS ${signed(total(EquipmentStat.DamagePerSecond))}  Support +${((gear.supportPower - 1.0) * 100).toInt()}%",
        )
        val dmgPercent = "+${((gear.damageMultiplier - 1.0) * 100).toInt()}%"
        player.ifSetText(
            comps.vitLines[0],
            "Damage: x${"%.2f".format(gear.damageMultiplier)} ($dmgPercent)",
        )
        player.ifSetText(
            comps.vitLines[1],
            "HP: ${active.hitpoints}/${gear.maximumHitpoints}  Speed: ${gear.attackDelay}t (${"%.2f".format(gear.attackDelay * 0.6)}s)",
        )
        player.ifSetText(
            comps.vitLines[2],
            "Gear: ${active.gearInstanceIds.size}/${CompanionRules.MAX_GEAR_ITEMS}  XP: ${active.experience}  Lv ${active.level}",
        )
        val rows = CompanionEquipmentInterfaceBuilder.AFFIX_ROWS
        for (i in 0 until rows) {
            val line =
                when {
                    i < affixLines.size -> affixLines[i]
                    i == rows - 1 && affixLines.size > rows ->
                        "<col=6b6154>+${affixLines.size - (rows - 1)} more affixes...</col>"
                    else -> ""
                }
            player.ifSetText(comps.affixLines[i], line)
        }
        player.ifSetText(
            comps.status,
            "<col=6b6154>Click inventory gear to equip. Click worn gear to unequip.</col>",
        )
    }

    /**
     * Re-renders the open equipment modal when the equipped instances' revisions changed (Forge
     * upgrade/reforge). Runs inside the per-cycle [sync], so an in-place mutation shows up within a
     * game tick without reopening the interface.
     */
    private fun refreshEquipmentViewIfStale(player: Player) {
        if (!equipmentViewers.containsKey(player)) return
        val active = activeCompanion(player) ?: return
        val stamp =
            active.gearInstanceIds.fold(0L) { acc, id ->
                acc * 31 + (equipmentInstances[id]?.revision ?: -1L)
            }
        if (equipmentViewers[player] == stamp) return
        equipmentViewers[player] = stamp
        refreshEquipmentView(player, active)
    }

    /** Equips the clicked owner-inventory item directly from the companion equipment modal. */
    private suspend fun ProtectedAccess.opCompanionInventory(invSlot: Int, op: IfButtonOp) {
        if (op != IfButtonOp.Op1) return

        val ownerId = player.characterId.toLong()
        val active = activeCompanion(player) ?: return
        val obj = player.inv[invSlot] ?: return
        val type = objTypes[obj]
        val wearpos = Wearpos[type.wearpos1]
        if (wearpos == null || wearpos.isClientOnly || !type.isEquipable) {
            player.mes("That item cannot be equipped on a companion.")
            return
        }

        if (obj.instanceId > 0L) {
            finishCompanionEquip(
                player = player,
                ownerId = ownerId,
                companionId = active.id,
                invSlot = invSlot,
                wearpos = wearpos,
                type = type,
                instanceId = obj.instanceId,
            )
            val updated = companions.owned(ownerId).firstOrNull { it.id == active.id } ?: active
            refreshEquipmentView(player, updated)
            return
        }

        // Normal equipment has no persisted instance yet. Roll it before transferring the item so
        // the companion's gear remains valid after logout/relogin.
        equipmentInstanceService.rollAndPersist(
            type = type,
            source = "companion-equip",
            tier = EquipmentTier.Bronze,
            ownerCharacterId = ownerId,
            onFailure = { player.mes("Failed to equip ${type.name} on a companion.") },
        ) { instance ->
            finishCompanionEquip(
                player = player,
                ownerId = ownerId,
                companionId = active.id,
                invSlot = invSlot,
                wearpos = wearpos,
                type = type,
                instanceId = instance.instanceId,
            )
            val updated = companions.owned(ownerId).firstOrNull { it.id == active.id } ?: active
            refreshEquipmentView(player, updated)
        }
    }

    /**
     * Transfers the inventory item into the companion's gear slot: unequips whatever already
     * occupies the slot, moves the obj between inventories and registers the instance id on the
     * companion. Mirrors `EquipmentStats.finishCompanionEquip` for the modal's equip path.
     */
    private fun finishCompanionEquip(
        player: Player,
        ownerId: Long,
        companionId: Long,
        invSlot: Int,
        wearpos: Wearpos,
        type: UnpackedObjType,
        instanceId: Long,
    ) {
        val companion = companions.owned(ownerId).firstOrNull { it.id == companionId } ?: return
        val inv = player.inv
        val obj = inv[invSlot]
        if (obj == null || obj.id != type.id) {
            player.mes("The item is no longer in that inventory slot.")
            return
        }
        val companionPlayer = companionPlayerManager.getPlayer(companionId)

        // If companion already has an item in this slot, find and unequip it first
        val existingInst =
            companion.gearInstanceIds.firstOrNull { id ->
                val inst = equipmentInstances[id]
                if (inst != null) {
                    objTypes[inst.templateObj]?.let {
                        Wearpos[it.wearpos1]?.slot == wearpos.slot
                    } == true
                } else false
            }
        val existingWornObj =
            existingInst
                ?.let { equipmentInstances[it] }
                ?.let { InvObj(it.templateObj, 1, instanceId = it.instanceId) }

        // Swapping an occupied slot keeps the count the same - the cap only applies
        // when equipping into a previously empty slot.
        if (
            existingInst == null && companion.gearInstanceIds.size >= CompanionRules.MAX_GEAR_ITEMS
        ) {
            player.mes(
                "${companion.name} is already wearing the maximum number of gear items (${CompanionRules.MAX_GEAR_ITEMS}/${CompanionRules.MAX_GEAR_ITEMS})."
            )
            return
        }

        if (existingInst != null) {
            companions.unequipGearItem(ownerId, companionId, existingInst)
            if (companionPlayer != null) {
                companionPlayer.worn[wearpos.slot] = null
            }
        }

        // Transfer from player inventory to companion
        val returnedObj = existingWornObj
        if (obj.count > 1) {
            inv[invSlot] = InvObj(obj.id, obj.count - 1, obj.vars, obj.instanceId)
        } else {
            inv[invSlot] = returnedObj
        }
        resendSlot(inv, invSlot)

        if (returnedObj != null && obj.count > 1) {
            val emptySlot = inv.indexOfFirst { it == null }
            if (emptySlot != -1) {
                inv[emptySlot] = returnedObj
                resendSlot(inv, emptySlot)
            }
        }

        companions.equipGearItem(ownerId, companionId, instanceId)
        if (companionPlayer != null) {
            companionPlayer.worn[wearpos.slot] = InvObj(type, 1, instanceId = instanceId)
            companionPlayer.rebuildAppearance()
        }
        player.mes("<col=006600>Equipped ${type.name} to ${companion.name}!</col>")
    }

    private suspend fun ProtectedAccess.opCompanionGearSlot(index: Int, op: IfButtonOp) {
        val ownerId = player.characterId.toLong()
        val active = activeCompanion(player) ?: return
        val (wearposSlot, slotName) = equipmentSlots.getOrNull(index) ?: return
        val status = companion_equipment_components.status
        val companionPlayer = companionPlayerManager.getPlayer(active.id)
        val entry = resolveGearSlots(active, companionPlayer?.worn)[wearposSlot]
        if (entry == null) {
            player.ifSetText(
                status,
                "<col=6b6154>$slotName slot is empty - click an inventory item to equip it.</col>",
            )
            return
        }
        val (type, inst) = entry
        if (op == IfButtonOp.Op3) {
            // Forge: delegates to the Forge side-channel - the RuneLite panel opens on the
            // published snapshot and drives upgrade/reforge through `forgeop`.
            if (inst == null) {
                player.ifSetText(
                    status,
                    "<col=ff3333>${type.name} has no instance - unequip it first.</col>",
                )
                return
            }
            forgeScript.openForCompanionGear(player, inst)
            player.ifSetText(
                status,
                "<col=ffb84d>${type.name}</col> - Forge opened in the side panel.",
            )
            return
        }
        if (op == IfButtonOp.Op2) {
            val affixes =
                inst?.affixes?.joinToString(", ") { "${it.stat.name} +${it.magnitude}" }
                    ?: "no affixes"
            player.ifSetText(status, "<col=ffb84d>${type.name}</col> - $affixes")
            return
        }
        // Unequip (primary click on the item name): mirrors EquipmentStats.opWornMain - the
        // item returns to the owner's inventory.
        if (inst == null) {
            player.ifSetText(status, "<col=ff3333>${type.name} cannot be unequipped here.</col>")
            return
        }
        val emptySlot = player.inv.indexOfFirst { it == null }
        if (emptySlot == -1) {
            player.ifSetText(status, "<col=ff3333>No free inventory space.</col>")
            return
        }
        companions.unequipGearItem(ownerId, active.id, inst.instanceId)
        if (companionPlayer != null) {
            companionPlayer.worn[wearposSlot] = null
            companionPlayer.rebuildAppearance()
        }
        player.inv[emptySlot] = InvObj(type, 1, instanceId = inst.instanceId)
        resendSlot(inv, emptySlot)
        player.mes("<col=660000>Unequipped ${type.name} from ${active.name}.</col>")
        val updated = companions.owned(ownerId).firstOrNull { it.id == active.id } ?: active
        refreshEquipmentView(player, updated)
    }

    private suspend fun ProtectedAccess.morphDialog(active: Companion) {
        val category =
            menu(
                "Change Companion Appearance",
                "Humanoid Adventurers (Visible armor & weapons)",
                "Iconic Boss Pets (Jad, Graardor, Zilyana, ...)",
                "Cancel",
            )
        when (category) {
            0 -> {
                val names = PetConfig.HUMANOID_ADVENTURERS.map { it.name }
                val pick = menu("Choose Humanoid Adventurer", *names.toTypedArray(), "Cancel")
                val chosen = PetConfig.HUMANOID_ADVENTURERS.getOrNull(pick) ?: return
                morphPet(player, active, chosen.npcId, chosen.name)
            }
            1 -> {
                val names = PetConfig.BOSS_PETS.map { it.name }
                val pick = menu("Choose Boss Pet", *names.toTypedArray(), "Cancel")
                val chosen = PetConfig.BOSS_PETS.getOrNull(pick) ?: return
                morphPet(player, active, chosen.npcId, chosen.name)
            }
            else -> {
                /* Cancel */
            }
        }
    }

    private fun morphPet(player: Player, companion: Companion, npcId: Int, name: String) {
        val ownerId = player.characterId.toLong()
        val safeName = name.take(CompanionRules.NAME_MAX_LENGTH).trim()
        val updated = companions.morph(ownerId, companion.id, npcId, safeName)
        val bot = companionPlayerManager.getPlayer(companion.id)
        if (bot != null && bot.isSlotAssigned) {
            companionPlayerManager.syncEquipment(updated, bot)
        } else {
            sync(player)
        }
        player.mes("<col=006600>Your companion metamorphosed into ${name}!</col>")
    }

    /* Commands */

    private fun cmdPet(cheat: Cheat) =
        with(cheat) {
            val ownerId = player.characterId.toLong()
            val active = companions.owned(ownerId).firstOrNull { it.active }
            if (active == null) {
                val cooling =
                    companions
                        .owned(ownerId)
                        .filter { it.state == CompanionState.INCAPACITATED }
                        .maxByOrNull {
                            companions.cooldownRemainingMillis(it, System.currentTimeMillis())
                        }
                if (cooling != null) {
                    val remaining =
                        companions.cooldownRemainingMillis(cooling, System.currentTimeMillis())
                    player.mes(
                        "<col=ff9900>${cooling.name} is still recovering. " +
                            "You can summon ${cooling.name} again in ${formatCooldown(remaining)}.</col>"
                    )
                } else {
                    player.mes(
                        "You don't have an active companion. Logging out and back in will summon one!"
                    )
                }
                return
            }
            val bot = companionPlayerManager.getPlayer(active.id)
            if (bot == null || !bot.isSlotAssigned) {
                sync(player)
            } else {
                val occupied = companionOccupiedTiles(player, excludeId = active.id)
                val tile =
                    companionFollowMovement.resolveTile(
                        bot = bot,
                        playerCoords = player.coords,
                        slot = active.slot,
                        occupied = occupied,
                        requireRoute = false,
                        followDistance = active.behaviour.followDistance,
                    )
                if (tile != null) {
                    PathingEntityCommon.telejump(bot, collision, tile)
                }
                bot.facePlayer(player)
            }
            val launched = protectedAccess.launch(player) { openCompanionDashboard(active) }
            if (!launched) {
                player.mes("Companion summoned to your side.")
            }
        }

    private fun cmdPetRevive(cheat: Cheat) =
        with(cheat) {
            val ownerId = player.characterId.toLong()
            val target =
                companions.owned(ownerId).firstOrNull { it.state == CompanionState.INCAPACITATED }
            if (target == null) {
                player.mes("<col=cc0000>You have no incapacitated companions to revive.</col>")
                return
            }
            companions.revive(ownerId, target.id)
            player.mes(
                "<col=006600>${target.name} has been revived at full hitpoints (dev command).</col>"
            )
        }

    private fun cmdPetGear(cheat: Cheat) =
        with(cheat) {
            val ownerId = player.characterId.toLong()
            val active = companions.owned(ownerId).firstOrNull { it.active }
            if (active == null) {
                player.mes("You do not have an active companion.")
                return
            }
            val launched = protectedAccess.launch(player) { openCompanionEquipmentView(active) }
            if (!launched) {
                player.mes("Please finish your current action before opening equipment.")
            }
        }

    private fun cmdPetTalents(cheat: Cheat) =
        with(cheat) {
            val ownerId = player.characterId.toLong()
            val active = companions.owned(ownerId).firstOrNull { it.active }
            if (active == null) {
                player.mes("You do not have an active companion.")
                return
            }
            val launched = protectedAccess.launch(player) { openCompanionTalents(active) }
            if (!launched) {
                player.mes("Please finish your current action before opening talents.")
            }
        }

    private fun cmdPetStorage(cheat: Cheat) =
        with(cheat) {
            val ownerId = player.characterId.toLong()
            val active = companions.owned(ownerId).firstOrNull { it.active }
            val name = active?.name ?: "Companion"
            val capacity = active?.packCapacity ?: 10
            storageShop.openStorage(player, name, capacity)
        }

    private fun cmdPetSpellbook(cheat: Cheat) =
        with(cheat) {
            val ownerId = player.characterId.toLong()
            val active = companions.owned(ownerId).firstOrNull { it.active }
            if (active == null) {
                player.mes("You do not have an active companion.")
                return
            }
            val launched = protectedAccess.launch(player) { openCompanionCombatView(active) }
            if (!launched) {
                player.mes("Please finish your current action before opening the spellbook.")
            }
        }

    private fun cmdPetResetTalents(cheat: Cheat) =
        with(cheat) {
            val ownerId = player.characterId.toLong()
            val active = companions.owned(ownerId).firstOrNull { it.active }
            if (active == null) {
                player.mes("You do not have an active companion.")
                return
            }
            companions.resetTalents(ownerId, active.id)
            player.mes(
                "<col=006600>Reset all talent points for ${active.name} (${active.talentPoints} available).</col>"
            )
        }

    private fun cmdPetXp(cheat: Cheat) =
        with(cheat) {
            val ownerId = player.characterId.toLong()
            val active = companions.owned(ownerId).firstOrNull { it.active }
            if (active == null) {
                player.mes("You do not have an active companion.")
                return
            }
            val amount = args.getOrNull(0)?.toLongOrNull() ?: 100L
            awardExperience(player, active, amount)
            player.mes("Awarded $amount XP to ${active.name}.")
        }

    private fun cmdPetStore(cheat: Cheat) = with(cheat) { depositAll(player) }

    private fun cmdPetWithdraw(cheat: Cheat) = with(cheat) { withdrawAll(player) }

    private fun cmdPetCheck(cheat: Cheat) = with(cheat) { checkStorage(player) }

    private fun cmdPetUpgrade(cheat: Cheat) =
        with(cheat) {
            val ownerId = player.characterId.toLong()
            val active = companions.owned(ownerId).firstOrNull { it.active }
            if (active == null) {
                player.mes("You do not have an active companion.")
                return
            }
            val launched = protectedAccess.launch(player) { upgradePackDialog(active) }
            if (!launched) {
                player.mes("Please finish your current action before upgrading.")
            }
        }

    private fun cmdPetMorph(cheat: Cheat) =
        with(cheat) {
            val ownerId = player.characterId.toLong()
            val active = companions.owned(ownerId).firstOrNull { it.active }
            if (active == null) {
                player.mes("You do not have an active companion.")
                return
            }
            val allOptions = PetConfig.HUMANOID_ADVENTURERS + PetConfig.BOSS_PETS
            val query = args.getOrNull(0)?.lowercase()
            if (query == null) {
                player.mes("Available appearances: " + allOptions.joinToString { it.name })
                return
            }
            val matched = allOptions.firstOrNull { it.name.lowercase().contains(query) }
            if (matched == null) {
                player.mes(
                    "Unknown appearance '$query'. Available: " + allOptions.joinToString { it.name }
                )
                return
            }
            morphPet(player, active, matched.npcId, matched.name)
        }

    private fun cmdPetLoot(cheat: Cheat) =
        with(cheat) {
            when (args.getOrNull(0)?.lowercase()) {
                "on",
                "1",
                "true" -> companionAutoLoot.setEnabled(player, true)
                "off",
                "0",
                "false" -> companionAutoLoot.setEnabled(player, false)
                else -> companionAutoLoot.toggle(player)
            }
        }

    private fun cmdPetHeal(cheat: Cheat) =
        with(cheat) {
            when (args.getOrNull(0)?.lowercase()) {
                "on",
                "1",
                "true" -> companionEmergencyHeal.setEnabled(player, true)
                "off",
                "0",
                "false" -> companionEmergencyHeal.setEnabled(player, false)
                else -> companionEmergencyHeal.toggle(player)
            }
        }

    private fun cmdPetAttack(cheat: Cheat) {
        val player = cheat.player
        val npcIndex =
            cheat.args.firstOrNull()?.toIntOrNull()
                ?: run {
                    player.mes("Usage: ::petattack <npc-index>")
                    return
                }
        val targetNpc = npcList[npcIndex]
        if (targetNpc == null || !targetNpc.isValidTarget() || !targetNpc.isAttackable()) {
            player.mes("<col=cc0000>Target NPC not found or cannot be attacked.</col>")
            return
        }

        val ownerId = runCatching { player.characterId.toLong() }.getOrNull() ?: return
        val activeList =
            companions.owned(ownerId).filter { it.active }.take(CompanionRules.ACTIVE_LIMIT)
        if (activeList.isEmpty()) {
            player.mes("<col=cc0000>You do not have any active companions summoned.</col>")
            return
        }

        player.faceNpc(targetNpc)
        player.clearInteraction()
        player.abortRoute()

        // Command all active companions to attack the target NPC
        for (active in activeList) {
            val bot =
                companionPlayerManager.getPlayer(active.id)
                    ?: companionPlayerManager.spawn(player, active)
                    ?: continue
            if (!bot.isSlotAssigned) continue

            explicitTargets[active.id] = targetNpc.slotId
            val gear = statSheet(active).combat
            val updated = syncBotVitals(player, active, bot) ?: active
            companionPlayerManager.applyCombatState(player, updated, bot, gear.maximumHitpoints)
            engageTarget(player, bot, updated, targetNpc)
            bot.say("Attacking ${targetNpc.visType.name}!")
        }

        player.mes(
            "<col=ff981f>All active companions are attacking ${targetNpc.visType.name} with you!</col>"
        )
    }

    private fun resolveDefaultPetId(): Int = 0

    private object PetConfig {
        data class AppearanceOption(val name: String, val npcId: Int)

        val HUMANOID_ADVENTURERS: List<AppearanceOption> =
            listOf(
                AppearanceOption("Player Character (Pure Equipment)", 0),
                AppearanceOption("Paladin", 3293),
                AppearanceOption("Knight of Ardougne", 3297),
                AppearanceOption("Hero", 3295),
                AppearanceOption("Battle Mage", 1610),
                AppearanceOption("Ranger", 1573),
                AppearanceOption("Void Knight", 5513),
            )

        val BOSS_PETS: List<AppearanceOption> =
            listOf(
                AppearanceOption("TzRek-Jad", 5893),
                AppearanceOption("General Graardor", 6632),
                AppearanceOption("Commander Zilyana", 6633),
                AppearanceOption("K'ril Tsutsaroth", 6634),
                AppearanceOption("Chaos Elemental", 2055),
                AppearanceOption("Snakeling", 2130),
                AppearanceOption("Baby Mole", 6635),
                AppearanceOption("Dark Core", 318),
                AppearanceOption("Phoenix", 3082),
                AppearanceOption("Dagannoth Supreme", 6628),
                AppearanceOption("Dagannoth Prime", 6629),
                AppearanceOption("Dagannoth Rex", 6630),
                AppearanceOption("Kree'arra", 6631),
                AppearanceOption("Kraken", 6640),
                AppearanceOption("Abyssal Sire", 5884),
                AppearanceOption("Scorpia", 5561),
            )
    }

    private fun cmdPets(cheat: Cheat) {
        val player = cheat.player
        val ownerId = runCatching { player.characterId.toLong() }.getOrNull() ?: return
        val owned = companions.owned(ownerId)
        if (owned.isEmpty()) {
            player.mes("<col=cc0000>You have no companions in your squad.</col>")
            return
        }
        protectedAccess.launch(player) {
            val options =
                owned
                    .map {
                        val activeTag =
                            if (it.active) "<col=006600>[Active]</col>"
                            else "<col=777777>[Inactive]</col>"
                        "Slot ${it.slot}: ${it.name} (${it.companionClass.name} Lvl ${it.level}) $activeTag"
                    }
                    .toMutableList()
            options.add("Cancel")

            val choice =
                menu(
                    "Your Companion Squad (${owned.count { it.active }}/${owned.size} Active)",
                    *options.toTypedArray(),
                )
            if (choice in owned.indices) {
                val chosen = owned[choice]
                selectedCompanionId[ownerId] = chosen.id
                player.mes(
                    "<col=ff981f>Selected ${chosen.name} (Slot ${chosen.slot}) for companion UI actions.</col>"
                )
                openCompanionDashboard(chosen)
            }
        }
    }

    private fun cmdPetSpells(cheat: Cheat) {
        val player = cheat.player
        val active = activeCompanion(player)
        if (active == null) {
            player.mes("<col=cc0000>You do not have an active companion.</col>")
            return
        }
        protectedAccess.launch(player) { openCompanionSpellbookView(active) }
    }

    private suspend fun ProtectedAccess.openCompanionSpellbookView(active: Companion) {
        val spells = companionRoleSpells.getSpellsForRole(active.companionClass)
        val unlockedSpells = companionRoleSpells.getUnlockedSpells(active)

        val title =
            "${active.name} (${active.companionClass.name}) PvM Spellbook (Lvl ${active.level})"
        val options =
            listOf(
                "View All Spells & Tiers (${unlockedSpells.size}/${spells.size} Unlocked)",
                "Toggle Auto-Cast Abilities",
                "Back",
            )

        when (menu(title, *options.toTypedArray())) {
            0 -> showSpellsList(active, spells)
            1 -> {
                val newState = companionRoleSpells.toggle(player)
                val status =
                    if (newState) "<col=006600>ENABLED</col>" else "<col=ff0000>DISABLED</col>"
                player.mes("${active.name}'s PvM Role Abilities are now $status.")
            }
            else -> {}
        }
    }

    private suspend fun ProtectedAccess.showSpellsList(active: Companion, spells: List<RoleSpell>) {
        val listOptions =
            spells
                .map { spell ->
                    val status =
                        if (active.level >= spell.levelReq) "<col=006600>[UNLOCKED]</col>"
                        else "<col=ff0000>[LOCKED (Req Lvl ${spell.levelReq})]</col>"
                    "[Lvl ${spell.levelReq}] ${spell.name} - $status"
                }
                .toMutableList()
        listOptions.add("Back")

        val choice =
            menu(
                "${active.name}'s ${active.companionClass.name} Spells",
                *listOptions.toTypedArray(),
            )
        if (choice in spells.indices) {
            val spell = spells[choice]
            val statusText =
                if (active.level >= spell.levelReq) "<col=006600>UNLOCKED</col>"
                else "<col=ff0000>LOCKED (Requires Level ${spell.levelReq})</col>"
            val details =
                """
                |Spell: ${spell.name}
                |Role: ${spell.role.name}
                |Type: ${spell.type.name}
                |Power: +${spell.powerPercent}%
                |Cooldown: ${spell.cooldownTicks} cycles
                |Status: $statusText
                |Description: ${spell.description}
                """
                    .trimMargin()
            mesbox(details)
            showSpellsList(active, spells)
        }
    }

    private companion object RuntimeConstants {
        private const val STARTER_RETRY_CYCLES = 2
    }

    /** Minimum map-clocks between "missing runes" warnings sent to the owner. */

    /** Bot combat stats whose XP mirrors into companion experience after fights. */
    private val combatXpStats =
        listOf(
            stats.attack,
            stats.strength,
            stats.defence,
            stats.ranged,
            stats.magic,
            stats.hitpoints,
        )

    /** Style progression track -> the bot stats whose XP delta feeds that track. */
    private val styleXpStats =
        mapOf(
            CompanionSkill.MELEE to listOf(stats.attack, stats.strength),
            CompanionSkill.RANGED to listOf(stats.ranged),
            CompanionSkill.MAGIC to listOf(stats.magic),
        )

    private suspend fun ProtectedAccess.openCompanionDashboard(active: Companion) {
        ifOpenMainModal(companion_dashboard_interfaces.dashboard)
        registerDashboardEvents(player)
        refreshDashboard(player, active)
    }

    private suspend fun ProtectedAccess.openCompanionInspect(active: Companion) {
        ifOpenMainModal(companion_inspect_interfaces.inspect)
        player.ifSetEvents(companion_inspect_components.close, -1..-1, IfEvent.Op1)
        refreshInspectView(player, active)
    }

    private fun refreshInspectView(player: Player, active: Companion) {
        val comps = companion_inspect_components
        val now = System.currentTimeMillis()
        val cooldowns =
            active.abilityLoadout.associateWith {
                companions.abilityCooldownRemainingMillis(active.id, it, now)
            }
        val bestRarity =
            active.gearInstanceIds
                .mapNotNull { equipmentInstances[it] }
                .maxByOrNull { it.rarity.ordinal }
                ?.rarity
                ?.name
                ?.lowercase()
                ?.replaceFirstChar(Char::uppercase)
        val view =
            CompanionInspectPresenter.present(
                companion = active,
                sheet = statSheet(active),
                skillExperience = skillXp.experienceMap(active.id),
                abilityCooldownsMillis = cooldowns,
                equippedCount = active.gearInstanceIds.size,
                bestGearRarity = bestRarity,
            )
        player.ifSetText(comps.title, "${active.name} - Inspect")
        player.ifSetText(comps.subtitle, view.subtitle)
        fun fill(rows: List<ComponentType>, lines: List<String>) {
            rows.forEachIndexed { i, comp -> player.ifSetText(comp, lines.getOrElse(i) { "" }) }
        }
        fill(comps.idLines, view.identityLines)
        fill(comps.evoLines, view.evolutionLines)
        fill(comps.sklLines, view.skillLines)
        fill(comps.setLines, view.setLines)
        fill(comps.stLines, view.statLines)
        fill(comps.abLines, view.abilityLines)
        fill(comps.tlLines, view.talentLines)
        player.ifSetText(comps.status, "<col=6b6154>${view.status}</col>")
    }

    private suspend fun ProtectedAccess.openCompanionBehaviour(active: Companion) {
        ifOpenMainModal(companion_behaviour_interfaces.behaviour)
        registerBehaviourEvents(player)
        refreshBehaviourView(player, active)
    }

    private fun registerBehaviourEvents(player: Player) {
        val comps = companion_behaviour_components
        for (btn in comps.modeButtons + comps.targetButtons + comps.styleButtons) {
            player.ifSetEvents(btn, -1..-1, IfEvent.Op1)
        }
        for (btn in
            listOf(
                comps.distMinus,
                comps.distPlus,
                comps.catchToggle,
                comps.tauntToggle,
                comps.defendToggle,
                comps.healToggle,
                comps.buffToggle,
                comps.healBelowMinus,
                comps.healBelowPlus,
                comps.close,
            )) {
            player.ifSetEvents(btn, -1..-1, IfEvent.Op1)
        }
    }

    private fun mutateBehaviour(
        player: Player,
        transform: (CompanionBehaviour) -> CompanionBehaviour,
    ) {
        val ownerId = player.characterId.toLong()
        val active = activeCompanion(player) ?: return
        val updated =
            runCatching { companions.setBehaviour(ownerId, active.id, transform(active.behaviour)) }
                .getOrNull() ?: return
        refreshBehaviourView(player, updated)
    }

    private fun refreshBehaviourView(player: Player, active: Companion) {
        val comps = companion_behaviour_components
        val b = active.behaviour
        val modes =
            listOf(
                CompanionCombatMode.PASSIVE,
                CompanionCombatMode.DEFENSIVE,
                CompanionCombatMode.AGGRESSIVE,
            )
        val targets =
            listOf(
                CompanionTargetPriority.OWNER_TARGET,
                CompanionTargetPriority.OWNER_ATTACKER,
                CompanionTargetPriority.NEAREST_HOSTILE,
                CompanionTargetPriority.LOWEST_HEALTH_ALLY,
            )
        val targetLabels =
            listOf("Owner Target", "Owner Attacker", "Nearest Hostile", "Weakest Ally")
        val styles =
            listOf(
                CompanionAttackStyle.MELEE,
                CompanionAttackStyle.RANGED,
                CompanionAttackStyle.MAGIC,
            )
        fun on(enabled: Boolean) =
            if (enabled) "<col=33B532>[ON]</col>" else "<col=9a8b76>[OFF]</col>"

        player.ifSetText(comps.title, "${active.name} - Behaviour")
        player.ifSetText(
            comps.subtitle,
            "${active.companionClass.name} Lv${active.level} | Style ${active.attackStyle.name} | Mode ${active.combatMode.name}",
        )
        comps.modeTexts.forEachIndexed { i, c ->
            player.ifSetText(c, combatSel(active.combatMode == modes[i], modes[i].name))
        }
        comps.targetTexts.forEachIndexed { i, c ->
            player.ifSetText(c, combatSel(b.targetPriority == targets[i], targetLabels[i]))
        }
        comps.styleTexts.forEachIndexed { i, c ->
            player.ifSetText(c, combatSel(active.attackStyle == styles[i], styles[i].name))
        }
        player.ifSetText(comps.distValue, "Follow Distance: ${b.followDistance} tiles")
        player.ifSetText(comps.catchText, "Catch-up Teleport: ${on(b.catchUpTeleport)}")
        player.ifSetText(comps.healBelowValue, "${b.healBelowPercent}%")
        player.ifSetText(comps.healText, "Auto Heal: ${on(b.autoHeal)}")
        player.ifSetText(comps.tauntText, "Auto Taunt: ${on(b.autoTaunt)}")
        player.ifSetText(comps.defendText, "Auto Defend: ${on(b.autoDefend)}")
        player.ifSetText(comps.buffText, "Auto Buff: ${on(b.autoBuff)}")
    }

    private fun registerDashboardEvents(player: Player) {
        player.ifSetEvents(companion_dashboard_components.close, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.btn_equip, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.btn_talents, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.btn_storage, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.btn_upgrade, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.btn_style, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.btn_morph, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.btn_store_all, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.btn_withdraw_all, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.btn_summon, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.btn_reset_talents, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.btn_autoloot, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.btn_inspect, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.btn_behaviour, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.mode_def, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.mode_agg, -1..-1, IfEvent.Op1)
        player.ifSetEvents(companion_dashboard_components.mode_pas, -1..-1, IfEvent.Op1)
    }

    private fun refreshDashboard(player: Player, active: Companion) {
        val ownerId = player.characterId.toLong()
        val storage = player.invMap.getValue(BaseInvs.companion_storage)
        val occupied = storage.count { it != null }
        val combat = statSheet(active).combat
        val levels = active.combatLevels()
        val spent = active.talents.sumOf { it.ranks }
        val availablePoints = (active.talentPoints - spent).coerceAtLeast(0)
        val autocastName = spellRegistry.getAutocastSpell(active.autocastSpellId)?.name ?: "None"

        val bot = companionPlayerManager.getPlayer(active.id)
        val isSpawned = bot != null && bot.isSlotAssigned
        val statusText = if (isSpawned) "ACTIVE - FOLLOWING" else "DISMISSED / RESTING"

        player.ifSetText(
            companion_dashboard_components.title,
            "${active.name} (Level ${active.level})",
        )
        player.ifSetText(
            companion_dashboard_components.subtitle,
            "${active.companionClass.name} Companion - Level ${active.level}",
        )
        player.ifSetText(
            companion_dashboard_components.status_badge,
            if (isSpawned) "<col=33B532>$statusText</col>" else "<col=9A8B76>$statusText</col>",
        )

        player.ifSetText(
            companion_dashboard_components.hp_text,
            "Hitpoints: ${active.hitpoints} / ${combat.maximumHitpoints}",
        )
        player.ifSetText(
            companion_dashboard_components.stat_xp,
            "Combat XP: ${active.experience.formatAmount}",
        )
        player.ifSetText(
            companion_dashboard_components.stat_class,
            "Combat Role: ${active.companionClass.name} (${active.attackStyle.name}) " +
                "Atk ${levels.attack} Str ${levels.strength} Rng ${levels.ranged} " +
                "Mag ${levels.magic} Def ${levels.defence}",
        )
        player.ifSetText(
            companion_dashboard_components.stat_dmg,
            "Damage Multiplier: x${"%.2f".format(combat.damageMultiplier)}",
        )
        player.ifSetText(
            companion_dashboard_components.stat_speed,
            "Attack Speed: ${combat.attackDelay} ticks",
        )
        player.ifSetText(
            companion_dashboard_components.stat_support,
            "Support Power: x${"%.2f".format(combat.supportPower)}",
        )
        player.ifSetText(
            companion_dashboard_components.stat_pack,
            "Pack Storage: $occupied / ${active.packCapacity} slots",
        )
        player.ifSetText(
            companion_dashboard_components.stat_talents,
            "Talent Points: $availablePoints unspent ($spent allocated)",
        )
        player.ifSetText(
            companion_dashboard_components.stat_gear,
            "Equipped Gear: ${active.gearInstanceIds.size} / ${CompanionRules.MAX_GEAR_ITEMS} slots",
        )
        player.ifSetText(
            companion_dashboard_components.stat_spell,
            "Autocast: $autocastName (${active.spellbook.name})",
        )
        player.ifSetText(
            companion_dashboard_components.btn_storage_desc,
            "Open companion pack (${active.packCapacity} slots)",
        )
        val nextPackTier = CompanionPackUpgradeCatalog.nextTier(active.packCapacity)
        player.ifSetText(
            companion_dashboard_components.btn_upgrade_desc,
            if (nextPackTier != null) {
                "${active.packCapacity} -> ${nextPackTier.capacity} slots"
            } else {
                "Pack Capacity MAX"
            },
        )
        player.ifSetText(
            companion_dashboard_components.btn_autoloot_text,
            if (companionAutoLoot.isEnabled(ownerId)) "Loot: ON" else "Loot: OFF",
        )
        player.ifSetText(
            companion_dashboard_components.mode_label,
            "Combat Mode: ${active.combatMode.name}",
        )
        player.ifSetText(
            companion_dashboard_components.mode_def_text,
            combatSel(active.combatMode == CompanionCombatMode.DEFENSIVE, "Defensive"),
        )
        player.ifSetText(
            companion_dashboard_components.mode_agg_text,
            combatSel(active.combatMode == CompanionCombatMode.AGGRESSIVE, "Aggressive"),
        )
        player.ifSetText(
            companion_dashboard_components.mode_pas_text,
            combatSel(active.combatMode == CompanionCombatMode.PASSIVE, "Passive"),
        )
    }

    private suspend fun ProtectedAccess.equipItemOnCompanion(event: PlayerUEvents.Op) {
        val ownerId = runCatching { player.characterId.toLong() }.getOrNull() ?: return
        val companionId = companionPlayerManager.getCompanionId(event.target) ?: return
        if (companionPlayerManager.getOwnerCharacterId(companionId) != ownerId) return
        val invObj = player.inv[event.invSlot] ?: return
        val instance = invObj.instanceId.takeIf { it > 0L }?.let { equipmentInstances[it] }
        val type = instance?.let { objTypes[it.templateObj] }
        val wearSlot = type?.let { Wearpos[it.wearpos1]?.slot }
        if (instance == null || type == null || wearSlot == null) {
            player.mes("That item cannot be equipped on a companion.")
            return
        }

        val companion = companions.owned(ownerId).firstOrNull { it.id == companionId } ?: return
        val bot = companionPlayerManager.getPlayer(companionId)
        val old = bot?.worn?.get(wearSlot)
        val oldId =
            old?.instanceId ?: resolveGearSlots(companion, bot?.worn)[wearSlot]?.second?.instanceId
        val gear = companion.gearInstanceIds.filterNot { it == oldId } + instance.instanceId
        if (gear.size > CompanionRules.MAX_GEAR_ITEMS) {
            player.mes("The companion has no free equipment slot.")
            return
        }

        val updated = companions.equipGear(ownerId, companionId, gear)
        if (bot != null) {
            bot.worn[wearSlot] = InvObj(type, 1, instanceId = instance.instanceId)
            bot.rebuildAppearance()
        }
        // Reuse the consumed item's inventory slot for the displaced item. This is an
        // atomic-looking swap from the player's point of view even when the inventory is full.
        player.inv[event.invSlot] = old
        resendSlot(player.inv, event.invSlot)
        player.mes("Equipped ${type.name} on ${updated.name}.")
        refreshEquipmentView(player, updated)
    }

    // ------------------------------------------------------------------ companion panel
    //
    // `UNFORGE_COMPANION|...` console side channel consumed by the RuneLite `Unforge Companion`
    // sidebar panel. The panel only ever sends these commands; all state and mutations stay
    // server-side. Free-text fields are base64-url encoded so `|` and colour tags can never
    // corrupt the envelope.
    //
    // UNFORGE_COMPANION|BEGIN|<count>|<selectedCompanionId>
    // UNFORGE_COMPANION|PET|id|slot|nameB64|class|level|state|hp|maxHp|active|mode|style|packCap|incapMs
    // UNFORGE_COMPANION|ABILITY|petId|abilityIdB64|nameB64|remainingMs|totalMs
    // UNFORGE_COMPANION|FLAGS|autoLoot|emergencyHeal
    // UNFORGE_COMPANION|END

    private fun cmdCompanionStatus(cheat: Cheat) =
        with(cheat) {
            val ownerId = runCatching { player.characterId.toLong() }.getOrNull() ?: return
            val owned = companions.owned(ownerId)
            val selectedId =
                selectedCompanionId[ownerId]
                    ?: owned.firstOrNull { it.active }?.id
                    ?: owned.firstOrNull()?.id
                    ?: -1L
            player.mes("UNFORGE_COMPANION|BEGIN|${owned.size}|$selectedId", ChatType.Console)
            val now = System.currentTimeMillis()
            for (companion in owned) {
                player.mes(
                    "UNFORGE_COMPANION|PET|${companion.id}|${companion.slot}|" +
                        "${panelEnc(companion.name)}|${companion.companionClass.name}|" +
                        "${companion.level}|${companion.state.name}|${companion.hitpoints}|" +
                        "${companion.maximumHitpoints}|${if (companion.active) 1 else 0}|" +
                        "${companion.combatMode.name}|${companion.attackStyle.name}|" +
                        "${companion.packCapacity}|" +
                        companions.cooldownRemainingMillis(companion, now),
                    ChatType.Console,
                )
                for (abilityId in companion.abilityLoadout) {
                    val def = CompanionAbilityCatalog.byId[abilityId]
                    val remaining =
                        companions.abilityCooldownRemainingMillis(companion.id, abilityId, now)
                    player.mes(
                        "UNFORGE_COMPANION|ABILITY|${companion.id}|${panelEnc(abilityId)}|" +
                            "${panelEnc(def?.name ?: abilityId)}|$remaining|" +
                            "${(def?.cooldownTicks ?: 0) * 600L}",
                        ChatType.Console,
                    )
                }
            }
            player.mes(
                "UNFORGE_COMPANION|FLAGS|${if (companionAutoLoot.isEnabled(ownerId)) 1 else 0}|" +
                    (if (companionEmergencyHeal.isEnabled(ownerId)) 1 else 0),
                ChatType.Console,
            )
            player.mes("UNFORGE_COMPANION|END", ChatType.Console)
        }

    private fun cmdPetSelect(cheat: Cheat) =
        with(cheat) {
            val ownerId = player.characterId.toLong()
            val owned = companions.owned(ownerId)
            val key = args.getOrNull(0)?.trim() ?: return
            val target =
                key.toLongOrNull()?.let { id -> owned.firstOrNull { it.id == id } }
                    ?: owned.firstOrNull { it.slot == key.toIntOrNull() }
                    ?: owned.firstOrNull { it.name.equals(key, ignoreCase = true) }
            if (target == null) {
                player.mes("<col=cc0000>You have no companion matching '$key'.</col>")
                return
            }
            selectedCompanionId[ownerId] = target.id
        }

    private fun cmdPetMode(cheat: Cheat) =
        with(cheat) {
            val modeName = args.getOrNull(0)?.uppercase() ?: return
            val mode =
                runCatching { CompanionCombatMode.valueOf(modeName) }.getOrNull()
                    ?: run {
                        player.mes("<col=cc0000>Use: petmode <defensive|aggressive|passive></col>")
                        return
                    }
            val ownerId = player.characterId.toLong()
            val companion =
                selectedOrActiveCompanion(player)
                    ?: run {
                        player.mes("You do not have a companion.")
                        return
                    }
            val updated = companions.setCombatMode(ownerId, companion.id, mode)
            player.mes("<col=006600>${updated.name} is now ${mode.name.lowercase()}.</col>")
            refreshDashboard(player, updated)
        }

    private fun cmdPetSummon(cheat: Cheat) =
        with(cheat) {
            val companion =
                selectedOrActiveCompanion(player)
                    ?: run {
                        player.mes("You do not have a companion.")
                        return
                    }
            if (summonCompanion(player, companion)) {
                sync(player)
                companionPlayerManager.getPlayer(companion.id)?.let {
                    companionPersonality.saySummon(it, player, companion)
                }
                player.mes("<col=006600>${companion.name} was summoned to your side.</col>")
            }
        }

    private fun cmdPetDismiss(cheat: Cheat) =
        with(cheat) {
            val ownerId = player.characterId.toLong()
            val companion = selectedOrActiveCompanion(player) ?: return
            companionPlayerManager.getPlayer(companion.id)?.let { bot ->
                if (bot.isSlotAssigned) {
                    companionPersonality.sayDismiss(bot, player, companion)
                }
            }
            despawn(ownerId)
            player.mes("<col=ff9900>${companion.name} was dismissed.</col>")
        }

    private fun cmdPetInspect(cheat: Cheat) =
        with(cheat) {
            val active =
                selectedOrActiveCompanion(player)
                    ?: run {
                        player.mes("You do not have a companion.")
                        return
                    }
            val launched = protectedAccess.launch(player) { openCompanionInspect(active) }
            if (!launched) {
                player.mes("Please finish your current action before opening inspect.")
            }
        }

    private fun cmdPetBehaviour(cheat: Cheat) =
        with(cheat) {
            val active =
                selectedOrActiveCompanion(player)
                    ?: run {
                        player.mes("You do not have a companion.")
                        return
                    }
            val launched = protectedAccess.launch(player) { openCompanionBehaviour(active) }
            if (!launched) {
                player.mes("Please finish your current action before opening behaviour.")
            }
        }

    private fun panelEnc(text: String): String =
        Base64.getUrlEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    private fun activeCompanion(player: Player): Companion? {
        val ownerId = runCatching { player.characterId.toLong() }.getOrNull() ?: return null
        val owned = companions.owned(ownerId)
        val selectedId = selectedCompanionId[ownerId]
        if (selectedId != null) {
            val found = owned.firstOrNull { it.id == selectedId && it.active }
            if (found != null) return found
        }
        return owned.firstOrNull { it.active }
    }

    /** The dashboard-selected companion, or any owned companion - including incapacitated ones. */
    private fun selectedOrActiveCompanion(player: Player): Companion? {
        val ownerId = runCatching { player.characterId.toLong() }.getOrNull() ?: return null
        val owned = companions.owned(ownerId)
        val selectedId = selectedCompanionId[ownerId]
        if (selectedId != null) {
            val found = owned.firstOrNull { it.id == selectedId }
            if (found != null) return found
        }
        return owned.firstOrNull { it.active } ?: owned.firstOrNull()
    }

    /**
     * Attempts to (re)activate [companion] for summoning.
     *
     * Incapacitated companions are blocked with a remaining-time message while the death cooldown
     * is running; an expired cooldown resummons at full hitpoints through the normal
     * [CompanionService.resummon] path. Returns `true` when the companion may be spawned.
     */
    private fun summonCompanion(player: Player, companion: Companion): Boolean {
        val ownerId = player.characterId.toLong()
        val fresh = companions.owned(ownerId).firstOrNull { it.id == companion.id } ?: companion
        if (fresh.state == CompanionState.INCAPACITATED) {
            val remaining = companions.cooldownRemainingMillis(fresh, System.currentTimeMillis())
            if (remaining > 0L) {
                player.mes(
                    "<col=ff9900>${fresh.name} is still recovering. " +
                        "You can summon ${fresh.name} again in ${formatCooldown(remaining)}.</col>"
                )
                return false
            }
            companions.resummon(ownerId, fresh.id, System.currentTimeMillis())
            return true
        }
        if (!fresh.active) {
            runCatching { companions.activate(ownerId, fresh.id) }
            return companions.owned(ownerId).firstOrNull { it.id == fresh.id }?.active == true
        }
        return true
    }

    private fun formatCooldown(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis + 999) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }
}
