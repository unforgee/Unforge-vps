@file:Suppress("SpellCheckingInspection", "unused")

package org.rsmod.content.areas.tutorialisland.configs

import org.rsmod.api.type.refs.dbcol.DbColumnReferences
import org.rsmod.api.type.refs.dbtable.DbTableReferences
import org.rsmod.game.dbtable.DbColumnCodec
import org.rsmod.game.type.TypeListMap
import org.rsmod.game.type.literal.CacheVarLiteral

typealias design_tables = DesignDbTables

typealias design_columns = DesignDbColumns

object DesignDbTables : DbTableReferences() {
    val hair_styles = find("hair_styles")
    val facial_hair_styles = find("facial_hair_styles")
    val torso_styles = find("torso_styles")
    val sleeve_styles = find("sleeve_styles")
    val legging_styles = find("legging_styles")
    val shoe_styles = find("shoe_styles")
    val hand_styles = find("hand_styles")
}

object DesignDbColumns : DbColumnReferences() {
    val hair_kit_a = value("hair_styles:player_kit_id_type_a", IdkitCodec)
    val hair_kit_b = value("hair_styles:player_kit_id_type_b", IdkitCodec)

    val facial_kit_a = value("facial_hair_styles:player_kit_id_type_a", IdkitCodec)
    val facial_kit_b = value("facial_hair_styles:player_kit_id_type_b", IdkitCodec)

    val torso_kit_a = value("torso_styles:player_kit_id_type_a", IdkitCodec)
    val torso_kit_b = value("torso_styles:player_kit_id_type_b", IdkitCodec)

    val sleeve_kit_a = value("sleeve_styles:player_kit_id_type_a", IdkitCodec)
    val sleeve_kit_b = value("sleeve_styles:player_kit_id_type_b", IdkitCodec)

    val legging_kit_a = value("legging_styles:player_kit_id_type_a", IdkitCodec)
    val legging_kit_b = value("legging_styles:player_kit_id_type_b", IdkitCodec)

    val shoe_kit_a = value("shoe_styles:player_kit_id_type_a", IdkitCodec)
    val shoe_kit_b = value("shoe_styles:player_kit_id_type_b", IdkitCodec)

    val hand_kit_a = value("hand_styles:player_kit_id_type_a", IdkitCodec)
    val hand_kit_b = value("hand_styles:player_kit_id_type_b", IdkitCodec)
}

/** Cache `IDKIT` columns store raw identkit ids as ints. */
private object IdkitCodec : DbColumnCodec.BaseIntCodec<Int> {
    override val types: List<CacheVarLiteral> = listOf(CacheVarLiteral.IDKIT)

    override fun decode(iterator: DbColumnCodec.Iterator<Int, Int>, types: TypeListMap): Int =
        iterator.next()

    override fun encode(value: Int): List<Int> = listOf(value)
}
