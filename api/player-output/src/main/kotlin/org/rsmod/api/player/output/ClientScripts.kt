package org.rsmod.api.player.output

import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.enums.EnumType
import org.rsmod.game.type.inv.InvType
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.map.CoordGrid

public fun Player.runClientScript(id: Int, vararg args: Any) {
    runClientScript(id, args.toList())
}

public fun Player.runClientScript(id: Int, args: List<Any>) {
    client.write(RunClientScript(id, args))
}

public object ClientScripts {
    /**
     * Cache onLoad for magic_spellbook (iface 218) runs CS2 2262 to unhide spells. Call this after
     * login varps are authoritative when the overlay may have loaded earlier (before VarpReset).
     *
     * Args match the packed component list on `magic_spellbook:universe` in rev 239.
     */
    public fun magicSpellbookInitialiseSpells(player: Player, magicSpellbookId: Int) {
        val base = magicSpellbookId shl 16
        player.runClientScript(
            MAGIC_SPELLBOOK_INITIALISE,
            base or 0, // universe
            base or 1, // top
            base or 3, // spelllayer
            base or 201, // infolayer
            base or 2, // glow
            base or 203, // filtermenu_container
            base or 206, // filtermenu
            base or 207, // bottom
            base or 208, // infobutton
            base or 209, // filterbutton
            base or 210, // tooltip
        )
    }

    public fun settingsInterfaceScaling(player: Player, scale: Int) {
        player.runClientScript(2358, scale)
    }

    public fun buffBarLayoutRedraw(player: Player) {
        player.runClientScript(5937)
    }

    public fun playerMember(player: Player, member: Boolean = player.members): Unit =
        player.runClientScript(828, if (member) 1 else 0)

    public fun ccDeleteAll(player: Player, component: ComponentType): Unit =
        player.runClientScript(2249, component.packed)

    public fun highlightingOff(player: Player): Unit = player.runClientScript(5485)

    public fun highlightingOn(player: Player): Unit = player.runClientScript(5487)

    public fun camForceAngle(player: Player, rate: Int, rate2: Int): Unit =
        player.runClientScript(143, rate, rate2)

    /** @param joinedChoices Menu list choices must be split by the `|` character. */
    public fun menu(player: Player, title: String, joinedChoices: String, hotkeys: Boolean): Unit =
        player.runClientScript(217, title, joinedChoices, if (hotkeys) 1 else 0)

    /**
     * Switches, or opens, the toplevel side tab. Values for [side] can be found in
     * [org.rsmod.api.config.Constants] prefixed with `toplevel_`. (i.e., `toplevel_attack`)
     */
    public fun toplevelSidebuttonSwitch(player: Player, side: Int): Unit =
        player.runClientScript(915, side)

    /** @param joinedChoices Dialogue choices must be split by the `|` character. */
    public fun chatboxMultiInit(player: Player, title: String, joinedChoices: String): Unit =
        player.runClientScript(58, title, joinedChoices)

    /**
     * `[clientscript,skillmulti_setup]` - populates interface 270.
     *
     * The script's 21 int arguments are, in order: the verb key, the "All" quantity, eighteen
     * option objs, and the initially-selected quantity. It stops reading options at the first `-1`,
     * so [objs] must be padded to eighteen entries with `-1`.
     *
     * @param verb a key into enum 1809, which supplies the op text drawn on every option slot.
     * @param maxCount the amount the `All` button offers. Clamped by the client to `1..28`.
     * @param selectedCount seeds both the highlighted quantity button and the `Other` button's
     *   remembered value.
     * @param joinedTitleAndLabels the dialog title followed by one label per option, split by the
     *   `|` character. cs 632 peels off one token per slot and hands the final slot whatever is
     *   left, so a label containing `|` would silently shift every later row.
     */
    public fun skillMultiSetup(
        player: Player,
        verb: Int,
        maxCount: Int,
        selectedCount: Int,
        objs: List<Int>,
        joinedTitleAndLabels: String,
    ) {
        require(objs.size == SKILL_MULTI_SLOTS) {
            "`objs` must hold exactly $SKILL_MULTI_SLOTS entries. (size=${objs.size})"
        }
        val args = ArrayList<Any>(SKILL_MULTI_SLOTS + 4)
        args += verb
        args += maxCount
        args.addAll(objs)
        args += selectedCount
        args += joinedTitleAndLabels
        player.runClientScript(SKILL_MULTI_SETUP, args)
    }

    /**
     * Values for [layerMode] can be found in [org.rsmod.api.config.Constants] prefixed with
     * `meslayer_mode`. (i.e., `meslayer_mode_countdialog`)
     */
    public fun mesLayerClose(player: Player, layerMode: Int): Unit =
        player.runClientScript(101, layerMode)

    public fun mesLayerMode7(player: Player, title: String): Unit =
        player.runClientScript(108, title)

    public fun mesLayerMode9(player: Player, title: String, mode: Int = 0): Unit =
        player.runClientScript(110, title, mode)

    public fun mesLayerMode14(
        player: Player,
        title: String,
        stockMarketRestriction: Boolean = true,
        enumRestriction: EnumType<ObjType, Boolean>? = null,
        showLastSearched: Boolean = false,
    ): Unit =
        player.runClientScript(
            750,
            title,
            if (stockMarketRestriction) 1 else 0,
            enumRestriction?.id ?: -1,
            if (showLastSearched) 1 else 0,
        )

    public fun chatDefaultRestoreInput(player: Player): Unit = player.runClientScript(2158)

    public fun topLevelMainModalOpen(
        player: Player,
        colour: Int = -1,
        transparency: Int = -1,
    ): Unit = player.runClientScript(2524, colour, transparency)

    public fun topLevelMainModalBackground(
        player: Player,
        colour: Int = -1,
        transparency: Int = -1,
    ): Unit = player.runClientScript(917, colour, transparency)

    public fun topLevelChatboxResetBackground(player: Player): Unit = player.runClientScript(2379)

    public fun ifSetTextAlign(
        player: Player,
        target: ComponentType,
        alignH: Int,
        alignV: Int,
        lineHeight: Int,
    ): Unit = player.runClientScript(600, alignH, alignV, lineHeight, target.packed)

    public fun objboxSetButtons(player: Player, text: String): Unit =
        player.runClientScript(2868, text)

    public fun interfaceInvInit(
        player: Player,
        inv: Inventory,
        target: ComponentType,
        objRowCount: Int,
        objColCount: Int,
        dragType: Int = 0,
        dragComponent: ComponentType? = null,
        op1: String? = null,
        op2: String? = null,
        op3: String? = null,
        op4: String? = null,
        op5: String? = null,
    ): Unit =
        player.runClientScript(
            149,
            target.packed,
            inv.type.id,
            objRowCount,
            objColCount,
            dragType,
            dragComponent?.packed ?: -1,
            op1 ?: "",
            op2 ?: "",
            op3 ?: "",
            op4 ?: "",
            op5 ?: "",
        )

    public fun shopMainInit(
        player: Player,
        shopInv: InvType,
        title: String,
        enableBuy50: Boolean = true,
        customBuyAmountObj: ObjType? = null,
        customBuyAmount: Int? = null,
    ) {
        check(customBuyAmount == null || customBuyAmountObj != null) {
            "`customBuyAmount` must be set if `customBuyAmountObj` is set."
        }
        check(customBuyAmountObj == null || customBuyAmount != null) {
            "`customBuyAmountObj` must be set if `customBuyAmount` is set."
        }
        player.runClientScript(
            1074,
            shopInv.id,
            title,
            customBuyAmountObj?.id ?: -1,
            customBuyAmount ?: 0,
            if (enableBuy50) 1 else 0,
        )
    }

    public fun examineItem(
        player: Player,
        obj: Int,
        count: Int,
        desc: String,
        market: Boolean,
        marketPrice: Int,
        alchable: Boolean,
        highAlch: Int,
        lowAlch: Int,
    ) {
        player.runClientScript(
            6003,
            obj,
            count,
            desc,
            if (market) 1 else 0,
            marketPrice,
            if (alchable) 1 else 0,
            highAlch,
            lowAlch,
        )
    }

    /**
     * @param timer the overlay timer type, see [org.rsmod.api.config.Constants] for known values.
     *   (prefixed with `overlay_timer_`)
     * @param isDespawnTimer `true` if the overlay represents a despawn timer as opposed to respawn
     *   timer. (i.e., hunter trap despawn vs tree respawn)
     */
    public fun addOverlayTimerLoc(
        player: Player,
        coords: CoordGrid,
        loc: LocType,
        shape: LocShape,
        timer: Int,
        ticks: Int,
        colour: Int,
        isDespawnTimer: Boolean = false,
    ) {
        player.runClientScript(
            5474,
            coords.packed,
            loc.id,
            shape.id,
            timer,
            ticks,
            colour,
            if (isDespawnTimer) 1 else 0,
        )
    }

    public fun confirmDestroyInit(
        player: Player,
        header: String,
        text: String,
        obj: Int,
        count: Int,
    ): Unit = player.runClientScript(814, obj, count, header, text)

    public fun pvpIconsComLevelRange(player: Player, combatLevel: Int): Unit =
        player.runClientScript(5224, combatLevel)

    public fun statGroupTooltip(
        player: Player,
        tooltip: ComponentType,
        container: ComponentType,
        text: String,
    ): Unit = player.runClientScript(7065, tooltip.packed, container.packed, text)

    public fun tooltip(
        player: Player,
        text: String,
        container: ComponentType,
        tooltip: ComponentType,
    ): Unit = player.runClientScript(1495, text, container.packed, tooltip.packed)

    public fun confirmOverlayInit(
        player: Player,
        target: ComponentType,
        title: String,
        text: String,
        cancel: String,
        confirm: String,
    ): Unit = player.runClientScript(4212, "$title|$text|$cancel|$confirm", target.packed)

    /**
     * Flashes a toplevel side-panel icon. Values match [org.rsmod.api.config.Constants]
     * `toplevel_*`.
     *
     * BROKEN: `[proc,toplevel_flashicon]` takes **3** ints per its rev-239 cache trailer, and what
     * they hold has not been recovered - the cache has no component hook that calls it. Tutorial
     * Island drives the flash through the `flashside` varbit instead. Do not use until the
     * signature is known.
     */
    public fun toplevelFlashIcon(player: Player, side: Int): Unit =
        player.runClientScript(913, side)

    public fun highlightNpcOn(player: Player, npcIndex: Int): Unit =
        player.runClientScript(4744, npcIndex)

    public fun highlightNpcOff(player: Player, npcIndex: Int): Unit =
        player.runClientScript(4745, npcIndex)

    public fun highlightNpcTypeOn(player: Player, npcTypeId: Int): Unit =
        player.runClientScript(4746, npcTypeId)

    public fun highlightNpcTypeOff(player: Player, npcTypeId: Int): Unit =
        player.runClientScript(4747, npcTypeId)

    public fun highlightLocOn(player: Player, locIndex: Int): Unit =
        player.runClientScript(4748, locIndex)

    public fun highlightLocOff(player: Player, locIndex: Int): Unit =
        player.runClientScript(4749, locIndex)

    public fun highlightLocTypeOn(player: Player, locTypeId: Int): Unit =
        player.runClientScript(4750, locTypeId)

    public fun highlightLocTypeOff(player: Player, locTypeId: Int): Unit =
        player.runClientScript(4751, locTypeId)

    public fun entityHighlightClear(player: Player): Unit = player.runClientScript(5950)

    public fun magicFlashSpell(player: Player, spellComponent: ComponentType): Unit =
        player.runClientScript(2081, spellComponent.packed)

    public fun magicFlash(player: Player): Unit = player.runClientScript(2232)

    public fun equipmentIconFlash(player: Player): Unit = player.runClientScript(2643)

    /**
     * Classic Tutorial Island clientscript helpers (rev 239 live). Prefer these over the unused
     * `tut2_*` cache scripts (3376+).
     */
    /** [clientscript,mesoverlay] — HTML guide text for `tutorial_overlay` / chat mes layer. */
    public fun mesOverlay(player: Player, text: String): Unit = player.runClientScript(1974, text)

    /** [clientscript,tutorial_overlay_hint] */
    public fun tutorialOverlayHint(player: Player, vararg args: Any): Unit =
        player.runClientScript(2584, *args)

    /**
     * [clientscript,tutorial_progressbar_init] — takes 6 ints. Normally unnecessary: `614:0` has a
     * baked onLoad hook that runs it with `[trigger, 614:2, 614:3, 614:4, 614:12, 614:15]` when
     * `tutorial_overlay` opens.
     */
    public fun tutorialProgressbarInit(player: Player, vararg args: Any): Unit =
        player.runClientScript(749, *args)

    /**
     * [clientscript,tutorial_progressbar_set] — takes **1** int per its cache trailer. Whether that
     * int is the raw step or a percentage is unconfirmed, so [max] is only used to derive it.
     */
    public fun tutorialProgressbarSet(player: Player, progress: Int, max: Int = 52): Unit =
        player.runClientScript(867, if (max <= 0) 0 else (progress * 100) / max)

    /** [clientscript,tutorial_default_settings] */
    public fun tutorialDefaultSettings(player: Player, vararg args: Any): Unit =
        player.runClientScript(2644, *args)

    /** [clientscript,tutorial_end] */
    public fun tutorialEnd(player: Player): Unit = player.runClientScript(2645)

    /** Cache onLoad script on `magic_spellbook:universe` (rev 239). */
    private const val MAGIC_SPELLBOOK_INITIALISE = 2262

    /** `[clientscript,skillmulti_setup]` - 21 int args, 1 string arg. */
    private const val SKILL_MULTI_SETUP = 2046

    /** cs 2046 always writes eighteen option slots, `270:15`..`270:32`. */
    private const val SKILL_MULTI_SLOTS = 18
}
