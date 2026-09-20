package org.rsmod.content.interfaces.skillshop

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetScrollPos
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.content.interfaces.skillshop.configs.SkillShopEntry
import org.rsmod.content.interfaces.skillshop.configs.SkillShopInterfaceBuilder
import org.rsmod.content.interfaces.skillshop.configs.skillshop_components
import org.rsmod.content.interfaces.skillshop.configs.skillshop_interfaces
import org.rsmod.content.interfaces.skillshop.configs.skillshop_varps
import org.rsmod.content.skills.core.SkillingPoints
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Script half of the Skill Quartermaster shop (`unforge_skillshop` interface).
 *
 * Opened with `::skillshop` (public - purchases are server-authoritative, so opening the window
 * grants nothing); a quartermaster npc only needs `ifOpenMainModal` when one is wired.
 * `::skillpoints <n>` stays admin-only because it mints currency.
 *
 * Purchases are server-authoritative against the `skill_points` varp: the level gate and the
 * one-time unlock bit are checked again on every Buy click, so a stale rendered row can never be
 * exploited. The scroll position is ephemeral per open session, same as the perk journal.
 */
class SkillShopScript
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val objTypes: ObjTypeList,
    private val objRepo: ObjRepository,
    private val skillingPoints: SkillingPoints,
) : PluginScript() {
    private val scrollPositions = WeakHashMap<Player, Int>()

    override fun ScriptContext.startup() {
        onCommand("skillshop") {
            desc = "Open the Skill Quartermaster point shop"
            cheat(::openShop)
        }
        onCommand("skillpoints") {
            modLevel = modlevels.admin
            desc = "Grant skill points: ::skillpoints <amount>"
            cheat(::grantPoints)
        }

        onIfOpen(skillshop_interfaces.unforge_skillshop) {
            scrollPositions[player] = 0
            player.ifSetScrollPos(skillshop_components.content, 0)
            // Buttons live inside wrap holders so the client emits their clicks with
            // `comsub == 0`; `-1..-1` registers Op1 for every slot the server may see.
            player.ifSetEvents(skillshop_components.close, -1..-1, IfEvent.Op1)
            player.ifSetEvents(skillshop_components.scrollUp, -1..-1, IfEvent.Op1)
            player.ifSetEvents(skillshop_components.scrollDown, -1..-1, IfEvent.Op1)
            for (button in skillshop_components.entryBuys) {
                player.ifSetEvents(button, -1..-1, IfEvent.Op1)
            }
            player.refresh()
        }
        onIfClose(skillshop_interfaces.unforge_skillshop) { scrollPositions.remove(player) }
        onIfModalButton(skillshop_components.close) { ifClose() }
        onIfModalButton(skillshop_components.scrollUp) {
            player.scrollBy(-SkillShopInterfaceBuilder.SCROLL_STEP)
        }
        onIfModalButton(skillshop_components.scrollDown) {
            player.scrollBy(SkillShopInterfaceBuilder.SCROLL_STEP)
        }
        for ((index, button) in skillshop_components.entryBuys.withIndex()) {
            onIfModalButton(button) { buy(SkillShopEntry.entries[index]) }
        }
    }

    private fun openShop(cheat: Cheat) {
        with(cheat) {
            protectedAccess.launch(player) {
                ifOpenMainModal(skillshop_interfaces.unforge_skillshop)
            }
        }
    }

    private fun grantPoints(cheat: Cheat) {
        with(cheat) {
            val amount =
                args.firstOrNull()?.toIntOrNull()
                    ?: run {
                        player.mes("Usage: ::skillpoints <amount>")
                        return
                    }
            protectedAccess.launch(player) {
                val balance = player.vars[skillshop_varps.points] + amount
                setVarp(skillshop_varps.points, balance)
                mes("<col=ffb84d>Skill points: $balance</col>")
            }
        }
    }

    private fun Player.scrollBy(delta: Int) {
        val pos = (scrollPositions[this] ?: 0) + delta
        val clamped = pos.coerceIn(0, SkillShopInterfaceBuilder.MAX_SCROLL)
        scrollPositions[this] = clamped
        ifSetScrollPos(skillshop_components.content, clamped)
    }

    private fun ProtectedAccess.buy(entry: SkillShopEntry) {
        val stat = entry.requiredStat
        if (stat != null && player.statBase(stat) < entry.requiredLevel) {
            mes(
                "<col=ffb84d>Requires ${stat.displayName} ${entry.requiredLevel} - " +
                    "yours: ${player.statBase(stat)}.</col>"
            )
            return
        }
        if (entry.unlockBit >= 0 && player.hasUnlock(entry)) {
            return // already owned - the row renders the tag instead of the button
        }
        val balance = skillingPoints.balance(player)
        if (balance < entry.cost) {
            mes(
                "<col=ffb84d>You need ${entry.cost} skill points for that - " +
                    "you have $balance.</col>"
            )
            return
        }
        if (entry.obj >= 0) {
            val type = objTypes[entry.obj] ?: return
            invAddOrDrop(objRepo, type, entry.count)
        }
        if (entry.unlockBit >= 0) {
            setVarp(
                skillshop_varps.recipeUnlocks,
                player.vars[skillshop_varps.recipeUnlocks] or (1 shl entry.unlockBit),
            )
        }
        // Funds were verified above and the game thread cannot have changed them since, so this
        // spend cannot fail - the check documents the invariant rather than handling it.
        check(skillingPoints.spend(this, entry.cost)) { "Skill point spend failed after checks" }
        mes(
            if (entry.unlockBit >= 0) {
                "<col=ffb84d>Learned: ${entry.label}.</col>"
            } else {
                "<col=ffb84d>Bought ${entry.label} for ${entry.cost} skill points.</col>"
            }
        )
        player.refresh()
    }

    private fun ProtectedAccess.setVarp(varp: org.rsmod.game.type.varp.VarpType, value: Int) {
        player.vars.backing[varp.id] = value
        player.resyncVar(varp)
    }

    private fun Player.hasUnlock(entry: SkillShopEntry): Boolean =
        entry.unlockBit >= 0 && vars[skillshop_varps.recipeUnlocks] and (1 shl entry.unlockBit) != 0

    private fun Player.refresh() {
        ifSetText(skillshop_components.pointsValue, "${vars[skillshop_varps.points]}")
        for ((index, entry) in SkillShopEntry.entries.withIndex()) {
            val stat = entry.requiredStat
            val locked = stat != null && statBase(stat) < entry.requiredLevel
            val owned = hasUnlock(entry)
            val buyable = !locked && !owned
            ifSetHide(skillshop_components.entryBuyWraps[index], hide = !buyable)
            ifSetHide(skillshop_components.entryTags[index], hide = buyable)
            if (!buyable) {
                ifSetText(
                    skillshop_components.entryTags[index],
                    if (owned) "Owned" else "Lvl ${entry.requiredLevel}",
                )
            }
        }
    }
}
