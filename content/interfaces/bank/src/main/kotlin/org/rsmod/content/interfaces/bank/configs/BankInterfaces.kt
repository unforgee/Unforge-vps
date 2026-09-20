package org.rsmod.content.interfaces.bank.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences

internal typealias bank_interfaces = BankInterfaces

internal typealias bank_components = BankComponents

internal typealias bank_comsubs = BankSubComponents

object BankInterfaces : InterfaceReferences() {
    val tutorial_overlay = find("screenhighlight", 1206696351)
}

object BankComponents : ComponentReferences() {
    val tutorial_button = find("bankmain:bank_tut", 7360148623500133599)
    val capacity_container = find("bankmain:capacity_layer", 5691092119508665688)
    val capacity_text = find("bankmain:capacity", 2572400183801329159)
    // `bankmain:items` (12:13) is a narrow 16px strip beside the scrollbar, not the
    // item grid. Script 274 creates the item children, and binds the inv/varp
    // transmit hooks, on `bankmain:com_12` (12:12) - the real grid component.
    val main_inventory = find("bankmain:com_12", 7094555693885599186)
    val tabs = find("bankmain:tabs", 8738751037378873781)
    val incinerator_confirm = find("bankmain:incinerator_confirm", 5423133603673607338)
    val potionstore_items = find("bankmain:potionstore_items", 7150465929805504638)
    val worn_off_stab = find("bankmain:stabatt", 8108856059077467721)
    val worn_off_slash = find("bankmain:slashatt", 2991199742112556623)
    val worn_off_crush = find("bankmain:crushatt", 7032909925403732722)
    val worn_off_magic = find("bankmain:magicatt", 3020894025994788434)
    val worn_off_range = find("bankmain:rangeatt", 4140384425360746649)
    val worn_speed_base = find("bankmain:attackspeedbase", 8611847314471561275)
    val worn_speed = find("bankmain:attackspeedactual", 8611847314471561276)
    val worn_def_stab = find("bankmain:stabdef", 8409975257935004635)
    val worn_def_slash = find("bankmain:slashdef", 8001388659092440331)
    val worn_def_crush = find("bankmain:crushdef", 5345750290142623042)
    val worn_def_range = find("bankmain:rangedef", 4320937687314686071)
    val worn_def_magic = find("bankmain:magicdef", 7725483948440596967)
    val worn_melee_str = find("bankmain:meleestrength", 2155694780326020956)
    val worn_ranged_str = find("bankmain:rangestrength", 8540620871125441141)
    val worn_magic_dmg = find("bankmain:magicdamage", 9155088019197373261)
    val worn_prayer = find("bankmain:prayer", 1046449737896300049)
    val worn_undead = find("bankmain:typemultiplier", 3080981337663321598)
    val worn_slayer = find("bankmain:slayermultiplier", 5442093999907644374)
    val tutorial_overlay_target = find("bankmain:bank_highlight", 3536736153639776716)
    val confirmation_overlay_target = find("bankmain:popup", 6921346722269781597)
    val tooltip = find("bankmain:tooltip", 880536259715967033)

    val rearrange_mode_swap = find("bankmain:swap", 5304300737323699661)
    val rearrange_mode_insert = find("bankmain:insert", 6700405115122503327)
    // The bank button rows pair each clickable component with a dead text/graphic
    // sibling, and the symbol names are shifted one slot relative to the clickable
    // components (verified against cache events/op text). E.g. `bankmain:item` is
    // the icon inside the clickable `bankmain:withdraw_text` button, and the
    // clickable "Deposit inventory" button is named `bankmain:depositworn_graphic`.
    val withdraw_mode_item = find("bankmain:withdraw_text", 5162632247627749002)
    val withdraw_mode_note = find("bankmain:item_text", 3476422340015843093)
    val always_placehold = find("bankmain:placeholder", 1314776946635575895)
    val deposit_inventory = find("bankmain:depositworn_graphic", 4181052166315159939)
    val deposit_worn = find("bankmain:incinerator_target", 6176312401936476794)
    val quantity_1 = find("bankmain:com_29", 2028749095784448127)
    val quantity_5 = find("bankmain:quantity1_text", 5597503861291556734)
    val quantity_10 = find("bankmain:quantity5_text", 530701198982376107)
    val quantity_x = find("bankmain:quantity10_text", 4314937377474528347)
    val quantity_all = find("bankmain:quantityx_text", 7260605638689424571)

    val incinerator_toggle = find("bankmain:incinerator_toggle", 6103145201068339558)
    val tutorial_button_toggle = find("bankmain:banktut_toggle", 3757387546077422234)
    val inventory_item_options_toggle = find("bankmain:sideops_toggle", 6890510451307622412)
    val deposit_inv_toggle = find("bankmain:depositinv_toggle", 4821074214266749210)
    val deposit_worn_toggle = find("bankmain:depositworn_toggle", 1238329595721843031)
    val release_placehold = find("bankmain:release_placeholders", 3821956540182228321)
    val bank_fillers_1 = find("bankmain:bank_filler_1", 6177734711959550248)
    val bank_fillers_10 = find("bankmain:bank_filler_10", 8037619913537120194)
    val bank_fillers_50 = find("bankmain:bank_filler_50", 6149835743640693671)
    val bank_fillers_x = find("bankmain:bank_filler_x", 2558169819183245169)
    val bank_fillers_all = find("bankmain:bank_filler_all", 8189875931580572475)
    val bank_fillers_fill = find("bankmain:bank_filler_confirm", 7827232783327574019)
    val bank_tab_display = find("bankmain:dropdown_content", 3924756297932264158)

    val side_inventory = find("bankside:items", 1885880344080200061)
    val worn_inventory = find("bankside:wornops", 6203990611586493264)
    val lootingbag_inventory = find("bankside:lootingbag_items", 8800055068705330501)
    val league_inventory = find("bankside:league_secondinv_items", 81253577765913503)
    val bankside_highlight = find("bankside:bankside_highlight", 6143522231071600323)

    val tutorial_close_button = find("screenhighlight:pausebutton", 8373824249352593324)
    val tutorial_next_page = find("screenhighlight:continue", 2368578001968595651)
    val tutorial_prev_page = find("screenhighlight:previous", 7461125518300620858)
}

@Suppress("ConstPropertyName")
object BankSubComponents {
    const val main_tab = 10
    val other_tabs = 11..19

    val tab_extended_slots_offset = 19..28
}
