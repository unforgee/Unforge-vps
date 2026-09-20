package org.rsmod.content.pvmsupport

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.support.SupportBuffKind
import org.rsmod.api.player.support.SupportCombatState
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.shops.Shops
import org.rsmod.content.pvmsupport.configs.support_components
import org.rsmod.content.pvmsupport.configs.support_interfaces
import org.rsmod.content.pvmsupport.configs.support_shop_invs
import org.rsmod.content.pvmsupport.configs.support_timers
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Entry points for the PvM support spellbook: the HUD, the casts and the Spellbook Codex.
 *
 * The cache-side spell buttons on the magic tab are not authored yet, so for now the spells are
 * driven by server-side commands. `::support` toggles a server-owned HUD panel that refreshes from
 * a soft timer - the closest thing to a buff bar a server can drive in this project, because the
 * vanilla buff bar is a cache/CS2 interface with no server-side entry API.
 */
public class SupportScript
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val spells: SupportSpellService,
    private val shops: Shops,
    private val eventBus: EventBus,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("support") {
            modLevel = modlevels.admin
            desc = "Toggle the PvM support HUD (cooldowns, buffs, shield)"
            cheat(::toggleHud)
        }
        onCommand("cast") {
            modLevel = modlevels.admin
            desc =
                "Cast a PvM support spell (${SupportSpell.entries.joinToString("/") { it.name }})"
            cheat(::cast)
        }
        onCommand("codex") {
            modLevel = modlevels.admin
            desc = "Spellbook Codex: standard/ancient/lunar/pvm"
            cheat(::codex)
        }
        onCommand("spellunlock") {
            modLevel = modlevels.admin
            desc = "Unlock a PvM support spell (name, display name, or 'all')"
            cheat(::unlockSpell)
        }
        onCommand("spellshop") {
            modLevel = modlevels.admin
            desc = "Open the PvM spell scroll shop"
            cheat(::openScrollShop)
        }
        onCommand("supportclear") {
            modLevel = modlevels.admin
            desc = "Clear PvM support cooldowns (testing)"
            cheat(::clear)
        }

        // Refresh while the HUD is on screen so its numbers can never drift from server state.
        onIfOpen(support_interfaces.unforge_support) { player.startHud() }
        onIfClose(support_interfaces.unforge_support) {
            player.clearSoftTimer(support_timers.support_hud)
        }
        onPlayerSoftTimer(support_timers.support_hud) { player.refreshHud() }
    }

    private fun toggleHud(cheat: Cheat) {
        with(cheat) {
            if (player.ui.containsOverlay(support_interfaces.unforge_support)) {
                player.ifCloseOverlay(support_interfaces.unforge_support, eventBus)
                player.clearSoftTimer(support_timers.support_hud)
                return
            }
            player.ifOpenOverlay(support_interfaces.unforge_support, eventBus)
            player.startHud()
        }
    }

    private fun Player.startHud() {
        refreshHud()
        softTimer(support_timers.support_hud, HUD_REFRESH_TICKS)
    }

    private fun Player.refreshHud() {
        // Defensive: if the panel is gone (closed by anything other than our own toggle), stop the
        // repeating timer instead of leaving a 2-tick refresh running forever.
        if (!ui.containsOverlay(support_interfaces.unforge_support)) {
            clearSoftTimer(support_timers.support_hud)
            return
        }
        val book =
            if (spells.isSupportEnabled(this)) {
                "PvM Support"
            } else {
                spells.currentSpellbook(this)?.name ?: "unknown"
            }
        val lines = ArrayList<String>(SupportSpell.entries.size + 6)
        lines += "<col=ff981f>Spellbook:</col> $book"
        for (spell in SupportSpell.entries) {
            if (!SupportSpellUnlocks.isUnlocked(this, spell)) {
                lines +=
                    "${spell.displayName} <col=99ccff>L${spell.levelReq}</col> <col=888888>locked</col>"
                continue
            }
            val remaining = SupportCooldowns.remainingTicks(this, spell)
            val value = if (remaining == 0) "Ready" else "${remaining * 6 / 10}s"
            val colour = if (remaining == 0) "33cc33" else "ffcc00"
            lines +=
                "${spell.displayName} <col=99ccff>L${spell.levelReq}</col> <col=$colour>$value</col>"
        }
        lines += "<col=ff981f>Buffs</col>"
        lines += "Battle Hymn ${duration(SupportBuffKind.BattleHymn)}"
        lines += "Iron Sanctuary ${duration(SupportBuffKind.IronSanctuary)}"
        lines +=
            "Shield ${SupportCombatState.shieldPoints(this)} " +
                duration(SupportBuffKind.GuardiansGrace)
        ifSetText(support_components.title, "PvM Support - $book")
        ifSetText(support_components.body, lines.joinToString("<br>"))
    }

    private fun Player.duration(kind: SupportBuffKind): String {
        val ticks = SupportCombatState.remainingTicks(this, kind)
        return if (ticks == 0) "Ready" else "${ticks * 6 / 10}s"
    }

    private fun cast(cheat: Cheat) {
        with(cheat) {
            val name =
                args.firstOrNull()
                    ?: run {
                        player.mes(
                            "Usage: ::cast <${SupportSpell.entries.joinToString("|") { it.name }}>"
                        )
                        return
                    }
            val spell =
                findSpell(name)
                    ?: run {
                        player.mes("Unknown support spell: $name")
                        return
                    }
            protectedAccess.launch(player) { spells.cast(this, spell) }
        }
    }

    private fun unlockSpell(cheat: Cheat) {
        with(cheat) {
            val name =
                args.firstOrNull()
                    ?: run {
                        player.mes("Usage: ::spellunlock <spell name|all>")
                        return
                    }
            if (name.equals("all", ignoreCase = true)) {
                SupportSpellUnlocks.unlockAll(player)
                player.mes("Unlocked every PvM support spell.")
                return
            }
            val spell =
                findSpell(name)
                    ?: run {
                        player.mes("Unknown support spell: $name")
                        return
                    }
            if (SupportSpellUnlocks.unlock(player, spell)) {
                player.mes("Unlocked <col=66ccff>${spell.displayName}</col>.")
            } else {
                player.mes("<col=66ccff>${spell.displayName}</col> is already unlocked.")
            }
        }
    }

    private fun openScrollShop(cheat: Cheat) {
        with(cheat) {
            shops.open(
                player = player,
                title = "PvM Spell Scrolls",
                shopInv = support_shop_invs.spell_scrolls,
                buyPercentage = 1.0,
                sellPercentage = 0.0,
                changePercentage = 0.0,
            )
        }
    }

    /** Matches either the enum name or the display name without spaces. */
    private fun findSpell(name: String): SupportSpell? =
        SupportSpell.entries.firstOrNull {
            it.name.equals(name, ignoreCase = true) ||
                it.displayName.replace(" ", "").equals(name, ignoreCase = true)
        }

    private fun codex(cheat: Cheat) {
        with(cheat) {
            val name =
                args.firstOrNull()
                    ?: run {
                        player.mes("Usage: ::codex <standard|ancient|lunar|pvm>")
                        return
                    }
            val book =
                when (name.lowercase()) {
                    "standard" -> Spellbook.Standard
                    "ancient",
                    "ancients" -> Spellbook.Ancients
                    "lunar",
                    "lunars" -> Spellbook.Lunars
                    "pvm",
                    "support",
                    "pvmsupport" -> Spellbook.PvmSupport
                    else -> null
                }
                    ?: run {
                        player.mes("Unknown spellbook: $name")
                        return
                    }
            protectedAccess.launch(player) {
                if (player.hitpoints <= 0) {
                    player.mes("You can't swap spellbooks while dead.")
                    return@launch
                }
                if (isInCombat()) {
                    player.mes("You can't swap spellbooks in combat.")
                    return@launch
                }
                if (book == Spellbook.PvmSupport) {
                    spells.enableSupport(player)
                    player.mes(
                        "<col=ff981f>Spellbook Codex:</col> <col=66ccff>PvM Support</col> active."
                    )
                    return@launch
                }
                if (!spells.setSpellbook(player, book)) {
                    player.mes("<col=ff981f>Spellbook Codex:</col> that spellbook is unavailable.")
                    return@launch
                }
                player.mes("<col=ff981f>Spellbook Codex:</col> switched to ${book.name}.")
            }
        }
    }

    private fun clear(cheat: Cheat) {
        with(cheat) {
            SupportCooldowns.clear(player)
            SupportCombatState.clear(player)
            player.mes("PvM support cooldowns and buffs cleared.")
        }
    }

    private companion object {
        /** HUD refresh interval: 2 ticks (~1.2 s), enough for a seconds readout. */
        private const val HUD_REFRESH_TICKS = 2
    }
}
