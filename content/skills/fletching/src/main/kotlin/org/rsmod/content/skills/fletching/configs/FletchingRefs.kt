package org.rsmod.content.skills.fletching.configs

import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences

typealias fletching_objs = FletchingObjs

typealias fletching_seqs = FletchingSeqs

/**
 * Every obj the fletching tables name, by cache symbol.
 *
 * Unstrung bows are named `unstrung_<log>_<bow>` in this cache - not `<bow>_u`, which is what the
 * wiki calls them - so the table below is worth reading next to this list.
 */
object FletchingObjs : ObjReferences() {
    val knife = find("knife")
    val feather = find("feather")
    val arrow_shaft = find("arrow_shaft")
    val headless_arrow = find("headless_arrow")
    val bow_string = find("bow_string")

    val logs = find("logs")
    val oak_logs = find("oak_logs")
    val willow_logs = find("willow_logs")
    val maple_logs = find("maple_logs")
    val yew_logs = find("yew_logs")
    val magic_logs = find("magic_logs")

    val unstrung_shortbow = find("unstrung_shortbow")
    val unstrung_longbow = find("unstrung_longbow")
    val unstrung_oak_shortbow = find("unstrung_oak_shortbow")
    val unstrung_oak_longbow = find("unstrung_oak_longbow")
    val unstrung_willow_shortbow = find("unstrung_willow_shortbow")
    val unstrung_willow_longbow = find("unstrung_willow_longbow")
    val unstrung_maple_shortbow = find("unstrung_maple_shortbow")
    val unstrung_maple_longbow = find("unstrung_maple_longbow")
    val unstrung_yew_shortbow = find("unstrung_yew_shortbow")
    val unstrung_yew_longbow = find("unstrung_yew_longbow")
    val unstrung_magic_shortbow = find("unstrung_magic_shortbow")
    val unstrung_magic_longbow = find("unstrung_magic_longbow")

    val shortbow = find("shortbow")
    val longbow = find("longbow")
    val oak_shortbow = find("oak_shortbow")
    val oak_longbow = find("oak_longbow")
    val willow_shortbow = find("willow_shortbow")
    val willow_longbow = find("willow_longbow")
    val maple_shortbow = find("maple_shortbow")
    val maple_longbow = find("maple_longbow")
    val yew_shortbow = find("yew_shortbow")
    val yew_longbow = find("yew_longbow")
    val magic_shortbow = find("magic_shortbow")
    val magic_longbow = find("magic_longbow")

    val bronze_arrowheads = find("bronze_arrowheads")
    val iron_arrowheads = find("iron_arrowheads")
    val steel_arrowheads = find("steel_arrowheads")
    val mithril_arrowheads = find("mithril_arrowheads")
    val adamant_arrowheads = find("adamant_arrowheads")
    val rune_arrowheads = find("rune_arrowheads")
    val dragon_arrowheads = find("dragon_arrowheads")

    val bronze_arrow = find("bronze_arrow")
    val iron_arrow = find("iron_arrow")
    val steel_arrow = find("steel_arrow")
    val mithril_arrow = find("mithril_arrow")
    val adamant_arrow = find("adamant_arrow")
    val rune_arrow = find("rune_arrow")
    val dragon_arrow = find("dragon_arrow")
}

object FletchingSeqs : SeqReferences() {
    /** Carving a log and, since the cache has no separate one, making arrows as well. */
    val fletch = find("human_fletching")

    /** Adding a bow string. A distinct, shorter animation in the cache. */
    val string_bow = find("stringing_shortbow")
}
