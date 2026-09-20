package org.rsmod.content.interfaces.talenttree

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetObj
import org.rsmod.api.player.ui.ifSetScrollPos
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.talents.CompanyRole
import org.rsmod.api.talents.CompanyService
import org.rsmod.api.talents.CompanyState
import org.rsmod.api.talents.PlayerTalentService
import org.rsmod.api.talents.TalentCatalog
import org.rsmod.api.talents.TalentDefinition
import org.rsmod.api.talents.TalentRules
import org.rsmod.api.talents.TalentTree
import org.rsmod.api.talents.TrainResult
import org.rsmod.content.interfaces.talenttree.configs.TalentTreeInterfaceBuilder
import org.rsmod.content.interfaces.talenttree.configs.talenttree_components
import org.rsmod.content.interfaces.talenttree.configs.talenttree_interfaces
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Script half of the shared talent-tree window (`unforge_talenttree`).
 *
 * One interface serves both trees: `::talents` opens the player's personal tree (varp-backed) and
 * `::companytree` opens the company tree (db-backed) when the player is a member. The script
 * records which tree each player has open and re-resolves every click against the authoritative
 * service state - a stale rendered card can never spend a point the rules would deny, because
 * [TalentRules.checkTrain] runs again inside each service's atomic write.
 *
 * Card clicks are two-action: `Train` (op1) asks for confirmation then spends one rank, `Info`
 * (op2) sends the talent's full details to chat - the accessible, text-based tooltip for a UI that
 * cannot render client-driven tooltips.
 */
class TalentTreeScript
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val objTypes: ObjTypeList,
    private val playerList: PlayerList,
    private val playerTalents: PlayerTalentService,
    private val companies: CompanyService,
) : PluginScript() {
    private val scrollPositions = WeakHashMap<Player, Int>()
    private val openTrees = WeakHashMap<Player, TalentTree>()

    /** Read-side snapshot of whichever tree [player] is viewing. */
    private class TreeView(
        val tree: TalentTree,
        val balance: Int,
        val spent: Int,
        val maxSpend: Int,
        val ranks: Map<String, Int>,
        val role: CompanyRole?,
    )

    override fun ScriptContext.startup() {
        onCommand("talents") {
            desc = "Open your personal talent tree"
            cheat(::openPlayerTree)
        }
        onCommand("companytree") {
            desc = "Open your company's talent tree"
            cheat(::openCompanyTree)
        }
        onCommand("company") {
            desc =
                "Company management: ::company [create <name>|invite <player>|kick <player>|leave]"
            cheat(::companyCommand)
        }
        onCommand("companyaccept") {
            desc = "Accept a pending company invitation"
            cheat(::acceptInvite)
        }
        onCommand("talentpoints") {
            modLevel = modlevels.admin
            desc = "Grant talent points: ::talentpoints <amount>"
            cheat(::grantTalentPoints)
        }
        onCommand("companypoints") {
            modLevel = modlevels.admin
            desc = "Grant company talent points: ::companypoints <amount>"
            cheat(::grantCompanyPoints)
        }

        onIfOpen(talenttree_interfaces.unforge_talenttree) {
            scrollPositions[player] = 0
            player.ifSetScrollPos(talenttree_components.content, 0)
            // Buttons arrive with comsub >= 0 (they sit inside per-button wrap holders), so
            // the cache-baked op mask never applies - runtime events are required for clicks
            // to pass `InterfaceEvents.isEnabled`. `-1..-1` covers every sub-slot.
            player.ifSetEvents(talenttree_components.reset, -1..-1, IfEvent.Op1)
            player.ifSetEvents(talenttree_components.close, -1..-1, IfEvent.Op1)
            player.ifSetEvents(talenttree_components.scrollUp, -1..-1, IfEvent.Op1)
            player.ifSetEvents(talenttree_components.scrollDown, -1..-1, IfEvent.Op1)
            for (hit in talenttree_components.cardHits) {
                player.ifSetEvents(hit, -1..-1, IfEvent.Op1, IfEvent.Op2)
            }
            player.refresh()
        }
        onIfClose(talenttree_interfaces.unforge_talenttree) {
            scrollPositions.remove(player)
            openTrees.remove(player)
        }
        onIfModalButton(talenttree_components.close) { ifClose() }
        onIfModalButton(talenttree_components.scrollUp) {
            player.scrollBy(-TalentTreeInterfaceBuilder.SCROLL_STEP)
        }
        onIfModalButton(talenttree_components.scrollDown) {
            player.scrollBy(TalentTreeInterfaceBuilder.SCROLL_STEP)
        }
        onIfModalButton(talenttree_components.reset) { confirmReset() }
        for ((index, hit) in talenttree_components.cardHits.withIndex()) {
            onIfModalButton(hit) { event ->
                val def = player.cardDefinition(index)
                if (def == null) {
                    return@onIfModalButton
                }
                if (event.op == IfButtonOp.Op2) {
                    player.info(def)
                } else {
                    confirmTrain(def)
                }
            }
        }
    }

    // ---- Commands -------------------------------------------------------------

    private fun openPlayerTree(cheat: Cheat) {
        val player = cheat.player
        protectedAccess.launch(player) {
            openTrees[player] = TalentTree.PLAYER
            ifOpenMainModal(talenttree_interfaces.unforge_talenttree)
        }
    }

    private fun openCompanyTree(cheat: Cheat) {
        val player = cheat.player
        if (companies.membership(player) == null) {
            player.mes("<col=c8a2ff>You are not in a company. Use ::company create <name>.</col>")
            return
        }
        protectedAccess.launch(player) {
            openTrees[player] = TalentTree.COMPANY
            ifOpenMainModal(talenttree_interfaces.unforge_talenttree)
        }
    }

    private fun companyCommand(cheat: Cheat) {
        val player = cheat.player
        val args = cheat.args
        when (args.firstOrNull()?.lowercase()) {
            "create" -> {
                val name = args.drop(1).joinToString(" ").take(24)
                companies.createCompany(player, name) { player.mes("<col=c8a2ff>$it</col>") }
            }
            "invite" -> {
                val targetName = args.getOrNull(1)
                val target =
                    playerList.firstOrNull { it.username.equals(targetName, ignoreCase = true) }
                if (target == null) {
                    player.mes("<col=c8a2ff>That player is not online.</col>")
                } else {
                    companies.invite(player, target) { player.mes("<col=c8a2ff>$it</col>") }
                }
            }
            "kick" -> {
                val targetName = args.getOrNull(1)
                val target =
                    playerList.firstOrNull { it.username.equals(targetName, ignoreCase = true) }
                if (target == null) {
                    player.mes("<col=c8a2ff>That player is not online.</col>")
                } else {
                    companies.kickMember(player, target) { player.mes("<col=c8a2ff>$it</col>") }
                }
            }
            "leave" -> companies.leaveCompany(player) { player.mes("<col=c8a2ff>$it</col>") }
            else -> {
                val membership = companies.membership(player)
                player.mes(
                    if (membership == null) {
                        "<col=c8a2ff>You are not in a company. " +
                            "Use ::company create <name> to start one.</col>"
                    } else {
                        "<col=c8a2ff>Company #${membership.companyId} - " +
                            "role ${membership.role.dbName.lowercase()}. " +
                            "::companytree opens the talent tree.</col>"
                    }
                )
            }
        }
    }

    private fun acceptInvite(cheat: Cheat) {
        companies.acceptInvite(cheat.player) { cheat.player.mes("<col=c8a2ff>$it</col>") }
    }

    private fun grantTalentPoints(cheat: Cheat) {
        val amount =
            cheat.args.firstOrNull()?.toIntOrNull()
                ?: run {
                    cheat.player.mes("Usage: ::talentpoints <amount>")
                    return
                }
        playerTalents.grant(cheat.player, amount)
        cheat.player.mes("<col=c8a2ff>Talent points: ${playerTalents.points(cheat.player)}</col>")
    }

    private fun grantCompanyPoints(cheat: Cheat) {
        val amount =
            cheat.args.firstOrNull()?.toIntOrNull()
                ?: run {
                    cheat.player.mes("Usage: ::companypoints <amount>")
                    return
                }
        companies.grantPoints(cheat.player, amount)
        cheat.player.mes("<col=c8a2ff>Granted $amount company talent point(s).</col>")
    }

    // ---- Tree view --------------------------------------------------------------

    /** Builds the authoritative snapshot for whichever tree [player] has open. */
    private fun Player.view(): TreeView? {
        val tree = openTrees[this] ?: return null
        return when (tree) {
            TalentTree.PLAYER -> {
                val ranks = playerTalents.ranks(this)
                TreeView(
                    tree = tree,
                    balance = playerTalents.points(this),
                    spent = TalentRules.spent(tree, ranks),
                    maxSpend = maxSpend(tree),
                    ranks = ranks,
                    role = null,
                )
            }
            TalentTree.COMPANY -> {
                val membership = companies.membership(this) ?: return null
                val state: CompanyState = companies.companyFor(this) ?: return null
                val ranks = synchronized(state) { state.rankSnapshot() }
                TreeView(
                    tree = tree,
                    balance = synchronized(state) { state.points },
                    spent = TalentRules.spent(tree, ranks),
                    maxSpend = maxSpend(tree),
                    ranks = ranks,
                    role = membership.role,
                )
            }
        }
    }

    private fun maxSpend(tree: TalentTree): Int =
        TalentCatalog.definitions(tree).sumOf { it.pointsPerRank * it.maxRank }

    /** Resolves the talent shown in card [index] for the player's open tree. */
    private fun Player.cardDefinition(index: Int): TalentDefinition? {
        val tree = openTrees[this] ?: return null
        val tier = index / TalentTreeInterfaceBuilder.CARDS_PER_TIER + 1
        val slot = index % TalentTreeInterfaceBuilder.CARDS_PER_TIER
        return TalentCatalog.definitions(tree).firstOrNull { it.tier == tier && it.slot == slot }
    }

    private fun Player.scrollBy(delta: Int) {
        val pos = (scrollPositions[this] ?: 0) + delta
        val clamped = pos.coerceIn(0, TalentTreeInterfaceBuilder.MAX_SCROLL)
        scrollPositions[this] = clamped
        ifSetScrollPos(talenttree_components.content, clamped)
    }

    // ---- Rendering ----------------------------------------------------------------

    /** Repaints every dynamic element from the authoritative [TreeView]. */
    private fun Player.refresh() {
        val view = view() ?: return
        ifSetText(
            talenttree_components.title,
            if (view.tree == TalentTree.PLAYER) "Player Talent Tree" else "Company Talent Tree",
        )
        ifSetText(talenttree_components.avail, "Available: ${view.balance} pts")
        ifSetText(talenttree_components.pointsValue, "${view.spent} / ${view.maxSpend}")

        for ((tierIndex, header) in talenttree_components.tierHeaders.withIndex()) {
            val tier = tierIndex + 1
            val gate = TalentRules.tierGate(view.tree, tier)
            ifSetText(
                header,
                if (gate == 0 || view.spent >= gate) {
                    "Tier $tier"
                } else {
                    "Tier $tier - spend $gate points to unlock (${view.spent}/$gate)"
                },
            )
            val tierCost =
                TalentCatalog.definitions(view.tree)
                    .filter { it.tier == tier }
                    .minOfOrNull { it.pointsPerRank }
            if (tierCost != null) {
                ifSetText(
                    talenttree_components.tierSubs[tierIndex],
                    "$tierCost points required per talent rank.",
                )
            }
        }

        for (index in 0 until TalentTreeInterfaceBuilder.CARD_COUNT) {
            val def = cardDefinition(index)
            if (def == null) {
                setCardVisible(index, visible = false)
                continue
            }
            setCardVisible(index, visible = true)
            val rank = view.ranks[def.id] ?: 0
            // The two background fills encode trained state; exactly one is ever visible.
            ifSetHide(talenttree_components.cardBgs[index], hide = rank > 0)
            ifSetHide(talenttree_components.cardBgsOn[index], hide = rank == 0)
            ifSetText(talenttree_components.cardNames[index], def.name)
            ifSetText(talenttree_components.cardRanks[index], "$rank/${def.maxRank}")
            ifSetText(
                talenttree_components.cardCosts[index],
                if (rank >= def.maxRank) "" else "Cost: ${def.pointsPerRank} pts",
            )
            ifSetText(talenttree_components.cardTags[index], tag(def, rank, view))
            val obj = objTypes[def.iconObj]
            if (obj != null) {
                ifSetObj(talenttree_components.cardIcons[index], obj, 800)
            }
        }
    }

    private fun Player.setCardVisible(index: Int, visible: Boolean) {
        ifSetHide(talenttree_components.cardBgs[index], hide = !visible)
        ifSetHide(talenttree_components.cardBgsOn[index], hide = !visible)
        ifSetHide(talenttree_components.cardBds[index], hide = !visible)
        ifSetHide(talenttree_components.cardIframes[index], hide = !visible)
        ifSetHide(talenttree_components.cardNames[index], hide = !visible)
        ifSetHide(talenttree_components.cardIcons[index], hide = !visible)
        ifSetHide(talenttree_components.cardRanks[index], hide = !visible)
        ifSetHide(talenttree_components.cardTags[index], hide = !visible)
        ifSetHide(talenttree_components.cardCosts[index], hide = !visible)
        ifSetHide(talenttree_components.cardHits[index], hide = !visible)
        ifSetHide(talenttree_components.cardHitWraps[index], hide = !visible)
    }

    /**
     * The card's text state tag - `Locked` (tier gate / prereq / leader), `Max`, `Cost N`
     * (unaffordable) or `Train`. Text, not colour, is the indicator.
     */
    private fun tag(def: TalentDefinition, rank: Int, view: TreeView): String =
        when {
            rank >= def.maxRank -> "Max"
            def.leaderOnly && view.role != CompanyRole.LEADER -> "Leader"
            !TalentRules.tierUnlocked(view.tree, def.tier, view.spent) -> "Locked"
            def.requires.let { it != null && (view.ranks[it] ?: 0) < def.requiresRank } -> "Locked"
            view.balance < def.pointsPerRank -> "Cost ${def.pointsPerRank}"
            else -> "Train"
        }

    // ---- Train / info / reset ------------------------------------------------------

    /** `Info` op: the accessible text tooltip - full details to chat. */
    private fun Player.info(def: TalentDefinition) {
        val lines = buildString {
            append("<col=c8a2ff>${def.name}</col> - ${def.description}")
            append(" Rank cost: ${def.pointsPerRank} pts. Max rank: ${def.maxRank}.")
            val prereq = def.requires
            if (prereq != null) {
                val req = TalentCatalog.definition(prereq)
                append(" Requires ${req.name} rank ${def.requiresRank}.")
            }
            if (def.leaderOnly) {
                append(" Leader only.")
            }
        }
        mes(lines)
    }

    private suspend fun ProtectedAccess.confirmTrain(def: TalentDefinition) {
        // Non-committing gate before the dialog: a dry run of the same rules so the player sees
        // the refusal up front. The only spend happens inside `trainAttempt` after Accept.
        val snapshot = player.view() ?: return
        val check =
            TalentRules.checkTrain(
                definition = def,
                tree = snapshot.tree,
                balance = snapshot.balance,
                spent = snapshot.spent,
                ranks = snapshot.ranks,
                role = snapshot.role,
            )
        if (check != TrainResult.Ok) {
            player.mes("<col=c8a2ff>${failureText(def, check)}</col>")
            player.refresh()
            return
        }
        val accepted =
            choice2(
                "Accept",
                true,
                "Cancel",
                false,
                title =
                    "Train ${def.name} rank ${(view().ranks[def.id] ?: 0) + 1} " +
                        "for ${def.pointsPerRank} point(s)?",
            )
        if (!accepted) {
            player.mes("Talent training cancelled.")
            return
        }
        // Re-validate after the dialog suspension: points and ranks could not have changed on
        // the game thread while the player was deciding, but the second check keeps the spend
        // honest even if a future system mutates them off-thread.
        when (val retry = trainAttempt(player, def)) {
            TrainResult.Ok -> {
                player.mes(
                    "<col=c8a2ff>Trained ${def.name} to rank " +
                        "${(player.view()?.ranks?.get(def.id) ?: 0)}.</col>"
                )
            }
            else -> player.mes("<col=c8a2ff>${failureText(def, retry)}</col>")
        }
        player.refresh()
    }

    private fun ProtectedAccess.view(): TreeView =
        requireNotNull(player.view()) { "No talent tree open" }

    /** Runs the tree-specific atomic train. */
    private fun trainAttempt(player: Player, def: TalentDefinition): TrainResult =
        when (openTrees[player]) {
            TalentTree.PLAYER -> playerTalents.train(player, def)
            TalentTree.COMPANY -> companies.train(player, def)
            null -> TrainResult.WrongTree
        }

    private fun failureText(def: TalentDefinition, result: TrainResult): String =
        when (result) {
            is TrainResult.InsufficientPoints ->
                "You need ${result.cost} talent point(s) for that - you have ${result.balance}."
            is TrainResult.TierLocked ->
                "Tier ${result.tier} requires ${result.requiredSpent} spent points - " +
                    "you have ${result.spent}."
            is TrainResult.PrereqMissing -> {
                val req = TalentCatalog.definition(result.required)
                "Requires ${req.name} rank ${result.requiredRank} first."
            }
            TrainResult.MaxRank -> "${def.name} is already at max rank."
            TrainResult.LeaderRequired -> "Only the company leader can train that talent."
            TrainResult.NotMember -> "You are not a member of that company."
            TrainResult.WrongTree -> "That talent does not belong to this tree."
            TrainResult.Ok -> ""
        }

    private suspend fun ProtectedAccess.confirmReset() {
        val tree = openTrees[player] ?: return
        val accepted =
            choice2(
                "Accept",
                true,
                "Cancel",
                false,
                title =
                    "Reset the " +
                        (if (tree == TalentTree.PLAYER) "player" else "company") +
                        " talent tree and refund all spent points?",
            )
        if (!accepted) {
            return
        }
        when (tree) {
            TalentTree.PLAYER -> {
                val refund = playerTalents.reset(player)
                player.mes("<col=c8a2ff>Talent tree reset - refunded $refund point(s).</col>")
            }
            TalentTree.COMPANY -> {
                val refund = companies.reset(player)
                player.mes(
                    if (refund >= 0) {
                        "<col=c8a2ff>Company tree reset - refunded $refund point(s).</col>"
                    } else {
                        "<col=c8a2ff>Only the company leader can reset the company tree.</col>"
                    }
                )
            }
        }
        player.refresh()
    }
}
