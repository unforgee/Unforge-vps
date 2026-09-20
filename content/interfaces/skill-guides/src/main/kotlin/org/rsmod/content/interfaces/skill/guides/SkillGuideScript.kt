package org.rsmod.content.interfaces.skill.guides

import jakarta.inject.Inject
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifCloseSub
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.interfaces.skill.guides.configs.guide_components
import org.rsmod.content.interfaces.skill.guides.configs.guide_interfaces
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * `skill_guide_v2_init`. Takes four int args and is not an `onLoad` on any component of interface
 * 860, so the server is what invokes it.
 */
private const val SKILL_GUIDE_V2_INIT = 1902

/**
 * `arg1` of [SKILL_GUIDE_V2_INIT]. Both the init script and its sibling at cs 9342 zero out args 2
 * and 3 unless the tab equals 31, which makes 31 a sentinel rather than a real tab. Opening on the
 * first tab is what the legacy module did, and it keeps args 2 and 3 irrelevant.
 */
private const val DEFAULT_TAB = 0

/**
 * Maps each stats-tab button to the guide index [SKILL_GUIDE_V2_INIT] expects in `arg0`.
 *
 * #### This is not the stat id
 * The guide is ordered by its own **1-based** list - attack, strength, ranged, magic, defence,
 * hitpoints, prayer, then the rest - which is neither the stat id order (attack 0, defence 1,
 * strength 2, ...) nor the order the buttons sit in on the tab. Stat ids are what cs 9348/9349
 * switch on for the per-stat varbits; they are the wrong thing to hand cs 1902.
 *
 * Established in-game, one click per skill, by comparing the value sent against the guide that
 * opened. All 23 entries below are observed, not inferred - and being 1-based is why sending a stat
 * id opened a broken guide for attack, whose id is 0.
 */
private val SKILL_GUIDE_BUTTONS: Map<ComponentType, Int> =
    mapOf(
        guide_components.attack to 1,
        guide_components.strength to 2,
        guide_components.ranged to 3,
        guide_components.magic to 4,
        guide_components.defence to 5,
        guide_components.hitpoints to 6,
        guide_components.prayer to 7,
        guide_components.agility to 8,
        guide_components.herblore to 9,
        guide_components.thieving to 10,
        guide_components.crafting to 11,
        guide_components.runecraft to 12,
        guide_components.mining to 13,
        guide_components.smithing to 14,
        guide_components.fishing to 15,
        guide_components.cooking to 16,
        guide_components.firemaking to 17,
        guide_components.woodcutting to 18,
        guide_components.fletching to 19,
        guide_components.slayer to 20,
        guide_components.farming to 21,
        guide_components.construction to 22,
        guide_components.hunter to 23,
    )

/**
 * Opens the rev 239 skill guide.
 *
 * #### Why this is not the old `skill_guide` (214) flow
 * The previous implementation wrote a skill id into `skill_guide_skill` (4371) and
 * `skill_guide_subsection` (4372). Those names are stale labels from an older revision: on this
 * cache 4371 is a *single bit* on `options_mobile`, packed in among vibrate / fps / buff-bar
 * toggles, and 4372 is likewise one bit. Writing 1..23 into either threw `Varbit overflow` out of
 * the button handler.
 *
 * #### How rev 239 actually works
 * The client owns the guide. `skill_guide_v2_init` (cs 1902) and the 1009-command renderer
 * `skill_guide_v2_rebuild` (cs 1904) read **no varps and no varbits at all** - they run entirely
 * off their arguments plus cache data, so the server never builds the entry list. cs 1902 writes
 * its first two args into the varcs the symbol table names `skill_guide_v2_current_skill` (1172)
 * and `skill_guide_v2_current_tab` (1173), and compares them against the current values first so
 * that re-selecting the same skill does not reset the scroll position.
 *
 * The server's entire job is therefore: open interface 860 and run cs 1902 with the guide index.
 * See [SKILL_GUIDE_BUTTONS] for why that index is not the stat id.
 *
 * #### The per-stat varbits are not ours to write
 * Varbits 20161..20184 hold seven bits of remembered guide state per stat, four to a varp, across
 * varps 965, 603, 5459, 5460, 5461 and 5462. cs 9348 and cs 9349 address them by stat id. Nothing
 * in the guide's render path reads them - the only caller of the getter is reached from
 * `stats_init` (cs 393) - so leaving them alone does not affect what the guide displays.
 */
class SkillGuideScript @Inject constructor(private val eventBus: EventBus) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((button, guide) in SKILL_GUIDE_BUTTONS) {
            onIfOverlayButton(button) { player.openGuide(guide) }
        }

        onIfOverlayButton(guide_components.close_button) { player.closeGuide() }
    }

    private fun Player.openGuide(guide: Int) {
        ifOpenOverlay(guide_interfaces.skill_guide_v2, eventBus)
        runClientScript(SKILL_GUIDE_V2_INIT, guide, DEFAULT_TAB, 0, 0)
    }

    private fun Player.closeGuide() {
        ifCloseSub(guide_interfaces.skill_guide_v2, eventBus)
    }
}
