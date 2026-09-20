package org.rsmod.content.pvmsupport.configs

import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.content.pvmsupport.SupportSpell
import org.rsmod.game.type.obj.ObjType

typealias support_scroll_objs = SupportScrollObjs

/**
 * One scroll per support spell.
 *
 * The scrolls are existing cache scroll objects that no other content binds to (no editor and no
 * script referenced them), renamed here into "<Spell> Scroll" with a single `Study` inventory op.
 * Using distinct cache objects means every spell gets its own scroll icon without authoring new obj
 * types.
 */
object SupportScrollObjs : ObjReferences() {
    val clueless_scroll = find("clueless_scroll")
    val bounty_teleport_scroll = find("bounty_teleport_scroll")
    val teleportscroll_nardah = find("teleportscroll_nardah")
    val teleportscroll_lunarisle = find("teleportscroll_lunarisle")
    val scroll_charge_dragonstone = find("scroll_charge_dragonstone")
    val grandtree_scroll = find("grandtree_scroll")
    val zqberviriusscroll2 = find("zqberviriusscroll2")
    val hazeel_scroll = find("hazeel_scroll")
    val handsand_scroll_magic = find("handsand_scroll_magic")
    val digexpertscroll = find("digexpertscroll")
    val burgh_efaritay_info_scroll = find("burgh_efaritay_info_scroll")
    val royal_official_scroll = find("royal_official_scroll")
    val qip_watchtower_scroll = find("qip_watchtower_scroll")
    val barbassault_scroll = find("barbassault_scroll")
    val contact_kaleef_scroll = find("contact_kaleef_scroll")
    val ii_impling_scroll = find("ii_impling_scroll")
    val godwars_knights_scroll_closed = find("godwars_knights_scroll_closed")
    val godwars_knights_scroll_open = find("godwars_knights_scroll_open")
    val veos_scroll = find("veos_scroll")
    val lovakengj_minecart_scroll = find("lovakengj_minecart_scroll")
}

/** The spell → scroll pairing, grouped as healing, offence and defence. */
public object SupportScrolls {
    public val all: List<Pair<SupportSpell, ObjType>> =
        listOf(
            SupportSpell.EmergencyMend to support_scroll_objs.clueless_scroll,
            SupportSpell.MendingLight to support_scroll_objs.bounty_teleport_scroll,
            SupportSpell.SanctuaryPulse to support_scroll_objs.teleportscroll_nardah,
            SupportSpell.GuardiansGrace to support_scroll_objs.teleportscroll_lunarisle,
            SupportSpell.RadiantRenewal to support_scroll_objs.scroll_charge_dragonstone,
            SupportSpell.NaturesGrace to support_scroll_objs.grandtree_scroll,
            SupportSpell.SpiritBond to support_scroll_objs.zqberviriusscroll2,
            SupportSpell.BattleHymn to support_scroll_objs.hazeel_scroll,
            SupportSpell.ArcaneSurge to support_scroll_objs.handsand_scroll_magic,
            SupportSpell.Stonebreaker to support_scroll_objs.digexpertscroll,
            SupportSpell.CelestialArrow to support_scroll_objs.burgh_efaritay_info_scroll,
            SupportSpell.BloodFrenzy to support_scroll_objs.qip_watchtower_scroll,
            SupportSpell.BloodSiphon to support_scroll_objs.royal_official_scroll,
            SupportSpell.IronSanctuary to support_scroll_objs.barbassault_scroll,
            SupportSpell.ArcaneBarrier to support_scroll_objs.contact_kaleef_scroll,
            SupportSpell.ShadowVeil to support_scroll_objs.ii_impling_scroll,
            SupportSpell.DemonicWard to support_scroll_objs.godwars_knights_scroll_closed,
            SupportSpell.DwarvenBulwark to support_scroll_objs.godwars_knights_scroll_open,
            SupportSpell.Juggernaut to support_scroll_objs.veos_scroll,
            SupportSpell.BoneArmor to support_scroll_objs.lovakengj_minecart_scroll,
        )

    public fun scrollFor(spell: SupportSpell): ObjType =
        all.first { (entry, _) -> entry == spell }.second
}

internal object SupportScrollObjEditor : ObjEditor() {
    init {
        for ((spell, scroll) in SupportScrolls.all) {
            edit(scroll) {
                name = "${spell.displayName} Scroll"
                desc = "Study to learn the ${spell.displayName} support spell."
                iop1 = "Study"
                stackable = false
                tradeable = true
                cost = spell.levelReq * 1_000
            }
        }
    }
}
