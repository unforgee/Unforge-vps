package org.rsmod.content.areas.unforge.slayer.hub

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.death.NpcKilledEvent
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetNpcHead
import org.rsmod.api.player.ui.ifSetScrollPos
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.content.areas.unforge.slayer.UnforgeSlayerNpcs
import org.rsmod.content.areas.unforge.slayer.UnforgeSlayerVarps
import org.rsmod.content.areas.unforge.slayer.CwSlayerData
import org.rsmod.content.areas.unforge.slayer.CwSlayerTask
import org.rsmod.content.areas.unforge.slayer.CwSlayerTaskType
import org.rsmod.content.areas.unforge.slayer.CwSlayerUnlock
import org.rsmod.content.areas.unforge.slayer.cwAssignTask
import org.rsmod.content.areas.unforge.slayer.cwCurrentTask
import org.rsmod.content.areas.unforge.slayer.cwIsDangerous
import org.rsmod.content.areas.unforge.slayer.cwRemaining
import org.rsmod.content.areas.unforge.slayer.cwResetTask
import org.rsmod.content.areas.unforge.slayer.cwSetVarp
import org.rsmod.content.areas.unforge.slayer.hasSlayerUnlock
import org.rsmod.content.areas.unforge.slayer.setSlayerUnlock
import org.rsmod.game.entity.Player
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Script half of the slayer hub (`slayer_hub` interface, authored by [SlayerHubInterfaceBuilder]).
 *
 * Slayer masters' "Rewards" op opens the modal; `onIfOpen`/`onIfClose` track a per-player session
 * (active tab, rewards scroll position, wilderness preference) in a WeakHashMap so closed sessions
 * vanish with the player. The hub reuses the shared task ops in `CwSlayerShared.kt` - assignment,
 * cancel, block and unlock purchases mutate the same `cw_slayer_*` varps as the dialogue flows, and
 * the display refreshes live on task kills while the modal stays open.
 */
class SlayerHubScript @Inject constructor(private val npcTypes: NpcTypeList) : PluginScript() {
    private enum class Tab {
        ASSIGNMENT,
        REWARDS,
    }

    private class HubSession {
        var tab: Tab = Tab.ASSIGNMENT
        var scroll: Int = 0
        var preferWild: Boolean = false
    }

    private val sessions = WeakHashMap<Player, HubSession>()

    override fun ScriptContext.startup() {
        bindMasterOps()

        onIfOpen(slayer_interfaces.slayer_hub) {
            sessions[player] = HubSession()
            // Buttons live inside wrap holders so the client emits their clicks with
            // `comsub == 0`; `-1..-1` registers Op1 for every slot the server may see.
            val buttons =
                listOf(
                    slayer_components.close,
                    slayer_components.tabTask,
                    slayer_components.tabRewards,
                    slayer_components.scrollUp,
                    slayer_components.scrollDown,
                    slayer_components.teleportButton,
                    slayer_components.cancelButton,
                    slayer_components.blockButton,
                    slayer_components.getEasy,
                    slayer_components.getMedium,
                    slayer_components.getHard,
                    slayer_components.getBoss,
                    slayer_components.wildToggle,
                ) + slayer_components.rewardBuys
            for (button in buttons) {
                if (button == slayer_components.teleportButton) {
                    player.ifSetEvents(button, -1..-1, IfEvent.Op1, IfEvent.Op2)
                } else {
                    player.ifSetEvents(button, -1..-1, IfEvent.Op1)
                }
            }
            player.refresh()
        }
        onIfClose(slayer_interfaces.slayer_hub) { sessions.remove(player) }
        onIfModalButton(slayer_components.close) { ifClose() }

        onIfModalButton(slayer_components.tabTask) { selectTab(Tab.ASSIGNMENT) }
        onIfModalButton(slayer_components.tabRewards) { selectTab(Tab.REWARDS) }
        onIfModalButton(slayer_components.scrollUp) {
            player.scrollBy(-SlayerHubInterfaceBuilder.SCROLL_STEP)
        }
        onIfModalButton(slayer_components.scrollDown) {
            player.scrollBy(SlayerHubInterfaceBuilder.SCROLL_STEP)
        }

        onIfModalButton(slayer_components.teleportButton) {
            // OSRS-style: Op1 is the normal click, Op2 is the context-menu Teleport action.
            if (it.op == IfButtonOp.Op1 || it.op == IfButtonOp.Op2) {
                teleportToTask()
            }
        }
        onIfModalButton(slayer_components.cancelButton) { cancelTask() }
        onIfModalButton(slayer_components.blockButton) { blockTask() }
        onIfModalButton(slayer_components.getEasy) { assign(CwSlayerTaskType.EASY) }
        onIfModalButton(slayer_components.getMedium) { assign(CwSlayerTaskType.MEDIUM) }
        onIfModalButton(slayer_components.getHard) { assign(CwSlayerTaskType.HARD) }
        onIfModalButton(slayer_components.getBoss) { assign(CwSlayerTaskType.BOSS) }
        onIfModalButton(slayer_components.wildToggle) { toggleWild() }

        for ((index, button) in slayer_components.rewardBuys.withIndex()) {
            onIfModalButton(button) { buyUnlock(CwSlayerUnlock.entries[index]) }
        }

        // Keep the progress bar live while the modal is open during combat.
        onEvent<NpcKilledEvent> {
            if (sessions.containsKey(killer)) {
                killer.refresh()
            }
        }
    }

    private fun ScriptContext.bindMasterOps() {
        for (ref in UnforgeSlayerNpcs.masters) {
            val type = npcTypes[ref] ?: continue
            for (slot in type.op.indices) {
                if (type.op[slot]?.lowercase() == "rewards") {
                    bindOp(type, slot + 1) { ifOpenMainModal(slayer_interfaces.slayer_hub) }
                }
            }
        }
    }

    private fun ScriptContext.bindOp(
        type: UnpackedNpcType,
        slot: Int,
        action: suspend ProtectedAccess.(NpcEvents.Op) -> Unit,
    ) {
        when (slot) {
            1 -> onOpNpc1(type) { action(it) }
            2 -> onOpNpc2(type) { action(it) }
            3 -> onOpNpc3(type) { action(it) }
            4 -> onOpNpc4(type) { action(it) }
            5 -> onOpNpc5(type) { action(it) }
        }
    }

    // ---------- handlers ----------

    private fun ProtectedAccess.selectTab(tab: Tab) {
        sessions[player]?.tab = tab
        player.refresh()
    }

    private fun Player.scrollBy(delta: Int) {
        val session = sessions[this] ?: return
        session.scroll = (session.scroll + delta).coerceIn(0, SlayerHubInterfaceBuilder.MAX_SCROLL)
        ifSetScrollPos(slayer_components.rewardsScroll, session.scroll)
    }

    private fun ProtectedAccess.teleportToTask() {
        val task = player.cwCurrentTask() ?: return
        val dangerous = player.cwIsDangerous()
        val tp =
            task.teleports.firstOrNull { it.wilderness == dangerous }
                ?: task.teleports.firstOrNull()
        if (tp == null) {
            mes("No direct teleport is available for ${task.name}.")
            return
        }
        ifClose()
        telejump(CoordGrid(tp.x, tp.z, tp.level))
    }

    private fun ProtectedAccess.cancelTask() {
        val task = player.cwCurrentTask() ?: return
        val points = player.vars[UnforgeSlayerVarps.points]
        if (points < 30) {
            mes("Cancelling a task costs 30 slayer points.")
            return
        }
        cwSetVarp(player, UnforgeSlayerVarps.points, points - 30)
        player.cwResetTask()
        mes("${task.name} has been cancelled.")
        player.refresh()
    }

    private fun ProtectedAccess.blockTask() {
        val task = player.cwCurrentTask() ?: return
        val free = UnforgeSlayerVarps.blocks.firstOrNull { player.vars[it] == 0 }
        if (free == null) {
            mes("You have no free block slots.")
            return
        }
        val points = player.vars[UnforgeSlayerVarps.points]
        if (points < 100) {
            mes("Blocking a task costs 100 slayer points.")
            return
        }
        cwSetVarp(player, UnforgeSlayerVarps.points, points - 100)
        cwSetVarp(player, free, task.key + 1)
        player.cwResetTask()
        mes("${task.name} has been blocked and your task was reset.")
        player.refresh()
    }

    private fun ProtectedAccess.assign(type: CwSlayerTaskType) {
        if (type == CwSlayerTaskType.BOSS && !player.hasSlayerUnlock(CwSlayerUnlock.LIKE_A_BOSS)) {
            mes("You haven't unlocked the ability to receive boss tasks yet.")
            return
        }
        if (player.combatLevel < type.minCombat) {
            mes(
                "You must have combat level of ${type.minCombat} or higher for this " +
                    "type of task."
            )
            return
        }
        val preferWild = sessions[player]?.preferWild ?: false
        val task = cwAssignTask(player, type, preferWild)
        if (task == null) {
            mes("No suitable ${type.name.lowercase()} task is available for you right now.")
            return
        }
        mes("Your new task is to kill ${task.name}.")
        player.refresh()
    }

    private fun ProtectedAccess.toggleWild() {
        val session = sessions[player] ?: return
        session.preferWild = !session.preferWild
        player.ifSetText(slayer_components.wildToggleText, if (session.preferWild) "On" else "Off")
    }

    private fun ProtectedAccess.buyUnlock(unlock: CwSlayerUnlock) {
        if (player.hasSlayerUnlock(unlock)) {
            return
        }
        val points = player.vars[UnforgeSlayerVarps.points]
        if (points < unlock.price) {
            mes("You need ${unlock.price} slayer points to buy ${unlock.label}.")
            return
        }
        player.setSlayerUnlock(unlock, true)
        cwSetVarp(player, UnforgeSlayerVarps.points, points - unlock.price)
        mes(if (unlock.extension) "Extension purchased." else "Unlock purchased.")
        player.refresh()
    }

    // ---------- refresh ----------

    private fun Player.refresh() {
        val session = sessions[this] ?: return
        ifSetText(slayer_components.pointsValue, "${vars[UnforgeSlayerVarps.points]}")
        ifSetText(slayer_components.statPointsValue, "${vars[UnforgeSlayerVarps.points]}")
        ifSetText(slayer_components.statDoneValue, "${vars[UnforgeSlayerVarps.completed]}")
        ifSetText(slayer_components.statWildValue, "${vars[UnforgeSlayerVarps.wildCompleted]}")

        val onRewards = session.tab == Tab.REWARDS
        ifSetHide(slayer_components.tabTaskUnderline, hide = onRewards)
        ifSetHide(slayer_components.tabRewardsUnderline, hide = !onRewards)

        refreshRewards(onRewards)
        refreshAssignment(onRewards)
    }

    private fun Player.refreshAssignment(hidden: Boolean) {
        val task = cwCurrentTask()
        val hasTask = task != null

        // Card frame shows in both states; its children depend on having a task.
        show(hidden, slayer_components.taskCard, slayer_components.taskCardBorder)
        val taskComps: List<ComponentType> = buildList {
            add(slayer_components.cardLabel)
            add(slayer_components.npcHead)
            add(slayer_components.taskName)
            add(slayer_components.taskDifficulty)
            add(slayer_components.taskHint)
            add(slayer_components.taskRemaining)
            addAll(slayer_components.pipTracks)
            addAll(slayer_components.pipFills)
            add(slayer_components.teleportButtonWrap)
            add(slayer_components.cancelButtonWrap)
            add(slayer_components.blockButtonWrap)
            add(slayer_components.milestone)
        }
        show(hidden || !hasTask, *taskComps.toTypedArray())
        ifSetHide(slayer_components.wildNote, hide = hidden || !hasTask || !cwIsDangerous())

        val emptyComps: List<ComponentType> = buildList {
            add(slayer_components.emptyTitle)
            add(slayer_components.emptyHint)
            add(slayer_components.getEasyWrap)
            add(slayer_components.getMediumWrap)
            add(slayer_components.getHardWrap)
            add(slayer_components.getBossWrap)
            add(slayer_components.wildLabel)
            add(slayer_components.wildToggleWrap)
        }
        show(hidden || hasTask, *emptyComps.toTypedArray())

        val statComps =
            listOf(
                slayer_components.statPointsCard,
                slayer_components.statPointsCardBorder,
                slayer_components.statPointsLabel,
                slayer_components.statPointsValue,
                slayer_components.statDoneCard,
                slayer_components.statDoneCardBorder,
                slayer_components.statDoneLabel,
                slayer_components.statDoneValue,
                slayer_components.statWildCard,
                slayer_components.statWildCardBorder,
                slayer_components.statWildLabel,
                slayer_components.statWildValue,
            )
        show(hidden, *statComps.toTypedArray())

        if (task != null) {
            ifSetText(slayer_components.taskName, task.name)
            ifSetText(slayer_components.taskDifficulty, difficultyLabel(task))
            ifSetText(slayer_components.taskHint, hintLabel(task))
            val remaining = cwRemaining()
            val assigned = vars[UnforgeSlayerVarps.assigned]
            ifSetText(
                slayer_components.taskRemaining,
                if (remaining < 0) {
                    "Boss assignment"
                } else {
                    "$remaining of $assigned remaining"
                },
            )
            val done =
                if (assigned > 0 && remaining >= 0) {
                    (assigned - remaining) * SlayerHubInterfaceBuilder.PROGRESS_SEGMENTS / assigned
                } else {
                    0
                }
            for (seg in 0 until SlayerHubInterfaceBuilder.PROGRESS_SEGMENTS) {
                ifSetHide(slayer_components.pipFills[seg], hide = seg >= done)
            }
            val npcId = CwSlayerData.npcIdForTask(task.name)
            if (npcId != null) {
                npcTypes[npcId]?.let { ifSetNpcHead(slayer_components.npcHead, it) }
            }
            ifSetText(slayer_components.milestone, milestoneLabel())
        }
    }

    private fun Player.refreshRewards(hidden: Boolean) {
        val comps: MutableList<ComponentType> =
            mutableListOf(
                slayer_components.rewardsScroll,
                slayer_components.rewardsHeader,
                slayer_components.rewardsFooter,
                slayer_components.rewardsScrollbar,
                slayer_components.scrollUpWrap,
                slayer_components.scrollDownWrap,
            )
        for (i in CwSlayerUnlock.entries.indices) {
            comps += slayer_components.rewardBgs[i]
            comps += slayer_components.rewardNames[i]
            comps += slayer_components.rewardPrices[i]
        }
        show(hidden, *comps.toTypedArray())
        for ((i, unlock) in CwSlayerUnlock.entries.withIndex()) {
            val owned = hasSlayerUnlock(unlock)
            show(hidden || owned, slayer_components.rewardBuyWraps[i])
            ifSetHide(slayer_components.rewardOwneds[i], hide = hidden || !owned)
        }
    }

    private fun Player.show(hide: Boolean, vararg comps: ComponentType) {
        for (comp in comps) {
            ifSetHide(comp, hide = hide)
        }
    }

    private fun difficultyLabel(task: CwSlayerTask): String {
        val highest = task.highestType.name.lowercase()
        return "${highest.replaceFirstChar { it.uppercase() }} task · level ${task.level}+ slayer"
    }

    private fun hintLabel(task: CwSlayerTask): String =
        task.teleports.firstOrNull()?.let { "Teleport: ${it.display}" }
            ?: "No direct teleport available"

    private fun Player.milestoneLabel(): String {
        val completed = vars[UnforgeSlayerVarps.completed]
        val next = ((completed / 10) + 1) * 10
        val multiplier =
            when {
                next % 1000 == 0 -> 50
                next % 250 == 0 -> 35
                next % 100 == 0 -> 25
                next % 50 == 0 -> 15
                else -> 5
            }
        return "Task $next pays ${multiplier}x points (${next - completed} to go)"
    }
}
