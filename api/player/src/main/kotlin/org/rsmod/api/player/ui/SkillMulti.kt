package org.rsmod.api.player.ui

import org.rsmod.game.type.obj.ObjType

/**
 * The generic "what would you like to make?" dialog, interface **270 `skillmulti`**.
 *
 * Unlike the anvil (interface 312), nothing on 270 is baked: every component is `events=0x0` with
 * no ops and no `onOp`, so the dialog is entirely server-driven. The server opens it, runs
 * `skillmulti_setup` (cs 2046), and enables the option slots with `ifSetEvents` - without that last
 * step the client refuses to report the click at all.
 *
 * #### How the click gets back
 * `skillmulti_itembutton_triggered` (cs 2052) hands the client the pair `(component, count)` and
 * only proceeds when the component's event mask permits it. The count rides in the **subcomponent**
 * field, which is why [SKILL_MULTI_SUB_RANGE] has to cover every quantity the player can pick
 * rather than just the slot ids. This is the same shape the items-kept-on-death interface uses: a
 * cs2-populated modal whose buttons arrive as a `ResumePauseButtonInput` because the server enabled
 * [org.rsmod.game.type.interf.IfEvent.PauseButton] over a subcomponent range.
 */
public object SkillMulti {
    /**
     * Interface 270's option slots, `270:15` through `270:32`.
     *
     * These are resolved by packed id rather than by name. `.data/symbols/component.sym` names
     * `270:14` as `skillmulti:a` and stops naming anything after `270:24`, but the cache's `layer`
     * field puts `270:15`..`270:32` *inside* `270:14` - the names are one slot out of step and run
     * out eight slots early.
     */
    public const val FIRST_OPTION_COMPONENT: Int = 15

    /**
     * cs 2046 writes exactly 18 option slots and stops at the first `-1`, so anything past this is
     * silently dropped by the client.
     */
    public const val MAX_OPTIONS: Int = 18

    /**
     * The client clamps the chosen quantity to `1..28` (`skillmulti_setup` on the way in,
     * `skillmulti_itembutton_triggered` on the way out). Dialogs with no quantity row report `0`
     * instead, so the enabled subcomponent range has to start there.
     */
    public const val MAX_COUNT: Int = 28

    public val SUB_RANGE: IntRange = 0..MAX_COUNT
}

/**
 * Picks the verb the client puts on each option slot's right-click menu.
 *
 * The value is a key into enum **1809**, which cs 2048 reads to build `cc_setop(1, ...)`. Keys that
 * enum does not list fall through to its default, `"Make"`.
 *
 * Only the keys whose verb is unambiguous are listed. Enum 1809 repeats several verbs across
 * multiple keys (three separate keys map to `"Cook"`, two to `"Smith"`); the entry chosen here is
 * the one that also leaves the quantity row enabled, since keys `26` and `29`..`34` are switched
 * off by enum 5178 and keys `1`, `3` and `8` lose the `X`/`All` buttons via enum 1810.
 */
public enum class SkillMultiVerb(public val id: Int) {
    Make(22),
    Cook(6),
    Fire(9),
    Smelt(13),
    Smith(25),
    Spin(16),
    Cut(12),
    Craft(15),
    Weave(35),
    Enchant(19),
}

/**
 * A single row in a [SkillMulti] dialog.
 *
 * @param obj drawn in the slot and used for the `<col=ff9040>`-highlighted op text.
 * @param label the name shown beside the model. Must not contain `|`, which cs 632 uses to split
 *   the dialog's single string argument into the title and one label per option.
 */
public data class SkillMultiOption(public val obj: ObjType, public val label: String)

/**
 * @param index the zero-based position of [option] in the list handed to the dialog.
 * @param count how many the player asked for, in `1..28`. Reported as `0` by dialogs whose verb
 *   suppresses the quantity row; those are normalised to `1` before this is returned.
 */
public data class SkillMultiSelection(
    public val index: Int,
    public val option: SkillMultiOption,
    public val count: Int,
)
