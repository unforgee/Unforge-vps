package org.rsmod.content.skills.smithing.configs

/**
 * The anvil's slot grid: which product each of interface 312's item slots makes, per bar.
 *
 * **Generated** from `smithing_setup` (cs 430), which embeds the whole table as literal operands to
 * 289 `gosub 431` calls - one block per bar, separated by a switch on `enum 1253` keyed by
 * [SmithingVarBits.bar_type]. Regenerate rather than editing by hand.
 *
 * Only the component and the product obj are stored. How many bars a product costs, how many it
 * yields, and the level it needs are **not** duplicated here: they are read at runtime from enums
 * 844/845/846, the same source the client uses, so the grid and the menu cannot disagree.
 *
 * The client hides some of these behind quest or skill gates the server does not model - claws
 * need Death Plateau (`varp 314 >= 80`), keel parts need Sailing, and dart tips are greyed until
 * Tourist Trap (`varp 197 >= 16`). The entries are kept because the level requirement in enum 846
 * is the real gate and a player cannot click a slot the client never draws.
 */
object SmithingProducts {
    /** [SmithingVarBits.bar_type] value -> the bar obj that tier smiths. */
    val barByTier: Map<Int, Int> =
        mapOf(1 to 2349, 2 to 2351, 3 to 2353, 4 to 2359, 5 to 2361, 6 to 2363, 7 to 13354)

    /** Bar obj -> (interface 312 component -> product obj). */
    val products: Map<Int, Map<Int, Int>> =
        mapOf(
            // Bronze bar
            2349 to
                mapOf(
                    9 to 1205, // Bronze dagger q=1 bars=1 lvl=0
                    10 to 1277, // Bronze sword q=1 bars=1 lvl=4
                    11 to 1321, // Bronze scimitar q=1 bars=2 lvl=5
                    12 to 1291, // Bronze longsword q=1 bars=2 lvl=6
                    13 to 1307, // Bronze 2h sword q=1 bars=3 lvl=14
                    14 to 1351, // Bronze axe q=1 bars=1 lvl=0
                    15 to 1422, // Bronze mace q=1 bars=1 lvl=2
                    16 to 1337, // Bronze warhammer q=1 bars=3 lvl=9
                    17 to 1375, // Bronze battleaxe q=1 bars=3 lvl=10
                    18 to 3095, // Bronze claws q=1 bars=2 lvl=13
                    19 to 1103, // Bronze chainbody q=1 bars=3 lvl=11
                    20 to 1075, // Bronze platelegs q=1 bars=3 lvl=16
                    21 to 1087, // Bronze plateskirt q=1 bars=3 lvl=16
                    22 to 1117, // Bronze platebody q=1 bars=5 lvl=18
                    23 to 4819, // Bronze nails q=15 bars=1 lvl=4
                    24 to 1139, // Bronze med helm q=1 bars=1 lvl=3
                    25 to 1155, // Bronze full helm q=1 bars=2 lvl=7
                    26 to 1173, // Bronze sq shield q=1 bars=2 lvl=8
                    27 to 1189, // Bronze kiteshield q=1 bars=3 lvl=12
                    29 to 819, // Bronze dart tip q=10 bars=1 lvl=4
                    30 to 39, // Bronze arrowtips q=15 bars=1 lvl=5
                    31 to 864, // Bronze knife q=5 bars=1 lvl=7
                    32 to 1794, // Bronze wire q=1 bars=1 lvl=4
                    33 to 19570, // Bronze javelin tips q=5 bars=1 lvl=6
                    34 to 9375, // Bronze bolts (unf) q=10 bars=1 lvl=3
                    35 to 9420, // Bronze limbs q=1 bars=1 lvl=6
                    36 to 31999, // Bronze keel parts q=1 bars=5 lvl=10
                ),
            // Iron bar
            2351 to
                mapOf(
                    9 to 1203, // Iron dagger q=1 bars=1 lvl=15
                    10 to 1279, // Iron sword q=1 bars=1 lvl=19
                    11 to 1323, // Iron scimitar q=1 bars=2 lvl=20
                    12 to 1293, // Iron longsword q=1 bars=2 lvl=21
                    13 to 1309, // Iron 2h sword q=1 bars=3 lvl=29
                    14 to 1349, // Iron axe q=1 bars=1 lvl=16
                    15 to 1420, // Iron mace q=1 bars=1 lvl=17
                    16 to 1335, // Iron warhammer q=1 bars=3 lvl=24
                    17 to 1363, // Iron battleaxe q=1 bars=3 lvl=25
                    18 to 3096, // Iron claws q=1 bars=2 lvl=28
                    19 to 1101, // Iron chainbody q=1 bars=3 lvl=26
                    20 to 1067, // Iron platelegs q=1 bars=3 lvl=31
                    21 to 1081, // Iron plateskirt q=1 bars=3 lvl=31
                    22 to 1115, // Iron platebody q=1 bars=5 lvl=33
                    23 to 4820, // Iron nails q=15 bars=1 lvl=19
                    24 to 1137, // Iron med helm q=1 bars=1 lvl=18
                    25 to 1153, // Iron full helm q=1 bars=2 lvl=22
                    26 to 1175, // Iron sq shield q=1 bars=2 lvl=23
                    27 to 1191, // Iron kiteshield q=1 bars=3 lvl=27
                    28 to 4540, // Oil lantern frame q=1 bars=1 lvl=26
                    29 to 820, // Iron dart tip q=10 bars=1 lvl=19
                    30 to 40, // Iron arrowtips q=15 bars=1 lvl=20
                    31 to 863, // Iron knife q=5 bars=1 lvl=22
                    32 to 7225, // Iron spit q=1 bars=1 lvl=17
                    33 to 19572, // Iron javelin tips q=5 bars=1 lvl=21
                    34 to 9377, // Iron bolts (unf) q=10 bars=1 lvl=18
                    35 to 9423, // Iron limbs q=1 bars=1 lvl=23
                    36 to 32002, // Iron keel parts q=1 bars=5 lvl=22
                ),
            // Steel bar
            2353 to
                mapOf(
                    9 to 1207, // Steel dagger q=1 bars=1 lvl=30
                    10 to 1281, // Steel sword q=1 bars=1 lvl=34
                    11 to 1325, // Steel scimitar q=1 bars=2 lvl=35
                    12 to 1295, // Steel longsword q=1 bars=2 lvl=36
                    13 to 1311, // Steel 2h sword q=1 bars=3 lvl=44
                    14 to 1353, // Steel axe q=1 bars=1 lvl=31
                    15 to 1424, // Steel mace q=1 bars=1 lvl=32
                    16 to 1339, // Steel warhammer q=1 bars=3 lvl=39
                    17 to 1365, // Steel battleaxe q=1 bars=3 lvl=40
                    18 to 3097, // Steel claws q=1 bars=2 lvl=43
                    19 to 1105, // Steel chainbody q=1 bars=3 lvl=41
                    20 to 1069, // Steel platelegs q=1 bars=3 lvl=46
                    21 to 1083, // Steel plateskirt q=1 bars=3 lvl=46
                    22 to 1119, // Steel platebody q=1 bars=5 lvl=48
                    23 to 1539, // Steel nails q=15 bars=1 lvl=34
                    24 to 1141, // Steel med helm q=1 bars=1 lvl=33
                    25 to 1157, // Steel full helm q=1 bars=2 lvl=37
                    26 to 1177, // Steel sq shield q=1 bars=2 lvl=38
                    27 to 1193, // Steel kiteshield q=1 bars=3 lvl=42
                    28 to 4544, // Bullseye lantern (unf) q=1 bars=1 lvl=49
                    29 to 821, // Steel dart tip q=10 bars=1 lvl=34
                    30 to 41, // Steel arrowtips q=15 bars=1 lvl=35
                    31 to 865, // Steel knife q=5 bars=1 lvl=37
                    32 to 2370, // Steel studs q=1 bars=1 lvl=36
                    33 to 19574, // Steel javelin tips q=5 bars=1 lvl=36
                    34 to 9378, // Steel bolts (unf) q=10 bars=1 lvl=33
                    35 to 9425, // Steel limbs q=1 bars=1 lvl=36
                    36 to 32005, // Steel keel parts q=1 bars=5 lvl=38
                    37 to 32886, // Chain q=1 bars=1 lvl=34
                ),
            // Mithril bar
            2359 to
                mapOf(
                    9 to 1209, // Mithril dagger q=1 bars=1 lvl=50
                    10 to 1285, // Mithril sword q=1 bars=1 lvl=54
                    11 to 1329, // Mithril scimitar q=1 bars=2 lvl=55
                    12 to 1299, // Mithril longsword q=1 bars=2 lvl=56
                    13 to 1315, // Mithril 2h sword q=1 bars=3 lvl=64
                    14 to 1355, // Mithril axe q=1 bars=1 lvl=51
                    15 to 1428, // Mithril mace q=1 bars=1 lvl=52
                    16 to 1343, // Mithril warhammer q=1 bars=3 lvl=59
                    17 to 1369, // Mithril battleaxe q=1 bars=3 lvl=60
                    18 to 3099, // Mithril claws q=1 bars=2 lvl=63
                    19 to 1109, // Mithril chainbody q=1 bars=3 lvl=61
                    20 to 1071, // Mithril platelegs q=1 bars=3 lvl=66
                    21 to 1085, // Mithril plateskirt q=1 bars=3 lvl=66
                    22 to 1121, // Mithril platebody q=1 bars=5 lvl=68
                    23 to 4822, // Mithril nails q=15 bars=1 lvl=54
                    24 to 1143, // Mithril med helm q=1 bars=1 lvl=53
                    25 to 1159, // Mithril full helm q=1 bars=2 lvl=57
                    26 to 1181, // Mithril sq shield q=1 bars=2 lvl=58
                    27 to 1197, // Mithril kiteshield q=1 bars=3 lvl=62
                    29 to 822, // Mithril dart tip q=10 bars=1 lvl=54
                    30 to 42, // Mithril arrowtips q=15 bars=1 lvl=55
                    31 to 866, // Mithril knife q=5 bars=1 lvl=57
                    32 to 9416, // Mith grapple tip q=1 bars=1 lvl=59
                    33 to 19576, // Mithril javelin tips q=5 bars=1 lvl=56
                    34 to 9379, // Mithril bolts (unf) q=10 bars=1 lvl=53
                    35 to 9427, // Mithril limbs q=1 bars=1 lvl=56
                    36 to 32008, // Mithril keel parts q=1 bars=5 lvl=56
                ),
            // Adamantite bar
            2361 to
                mapOf(
                    9 to 1211, // Adamant dagger q=1 bars=1 lvl=70
                    10 to 1287, // Adamant sword q=1 bars=1 lvl=74
                    11 to 1331, // Adamant scimitar q=1 bars=2 lvl=75
                    12 to 1301, // Adamant longsword q=1 bars=2 lvl=76
                    13 to 1317, // Adamant 2h sword q=1 bars=3 lvl=84
                    14 to 1357, // Adamant axe q=1 bars=1 lvl=71
                    15 to 1430, // Adamant mace q=1 bars=1 lvl=72
                    16 to 1345, // Adamant warhammer q=1 bars=3 lvl=79
                    17 to 1371, // Adamant battleaxe q=1 bars=3 lvl=80
                    18 to 3100, // Adamant claws q=1 bars=2 lvl=83
                    19 to 1111, // Adamant chainbody q=1 bars=3 lvl=81
                    20 to 1073, // Adamant platelegs q=1 bars=3 lvl=86
                    21 to 1091, // Adamant plateskirt q=1 bars=3 lvl=86
                    22 to 1123, // Adamant platebody q=1 bars=5 lvl=88
                    23 to 4823, // Adamantite nails q=15 bars=1 lvl=74
                    24 to 1145, // Adamant med helm q=1 bars=1 lvl=73
                    25 to 1161, // Adamant full helm q=1 bars=2 lvl=77
                    26 to 1183, // Adamant sq shield q=1 bars=2 lvl=78
                    27 to 1199, // Adamant kiteshield q=1 bars=3 lvl=82
                    29 to 823, // Adamant dart tip q=10 bars=1 lvl=74
                    30 to 43, // Adamant arrowtips q=15 bars=1 lvl=75
                    31 to 867, // Adamant knife q=5 bars=1 lvl=77
                    32 to 19578, // Adamant javelin tips q=5 bars=1 lvl=76
                    34 to 9380, // Adamant bolts(unf) q=10 bars=1 lvl=73
                    35 to 9429, // Adamantite limbs q=1 bars=1 lvl=76
                    36 to 32011, // Adamant keel parts q=1 bars=5 lvl=74
                ),
            // Runite bar
            2363 to
                mapOf(
                    9 to 1213, // Rune dagger q=1 bars=1 lvl=85
                    10 to 1289, // Rune sword q=1 bars=1 lvl=89
                    11 to 1333, // Rune scimitar q=1 bars=2 lvl=90
                    12 to 1303, // Rune longsword q=1 bars=2 lvl=91
                    13 to 1319, // Rune 2h sword q=1 bars=3 lvl=99
                    14 to 1359, // Rune axe q=1 bars=1 lvl=86
                    15 to 1432, // Rune mace q=1 bars=1 lvl=87
                    16 to 1347, // Rune warhammer q=1 bars=3 lvl=94
                    17 to 1373, // Rune battleaxe q=1 bars=3 lvl=95
                    18 to 3101, // Rune claws q=1 bars=2 lvl=98
                    19 to 1113, // Rune chainbody q=1 bars=3 lvl=96
                    20 to 1079, // Rune platelegs q=1 bars=3 lvl=99
                    21 to 1093, // Rune plateskirt q=1 bars=3 lvl=99
                    22 to 1127, // Rune platebody q=1 bars=5 lvl=99
                    23 to 4824, // Rune nails q=15 bars=1 lvl=89
                    24 to 1147, // Rune med helm q=1 bars=1 lvl=88
                    25 to 1163, // Rune full helm q=1 bars=2 lvl=92
                    26 to 1185, // Rune sq shield q=1 bars=2 lvl=93
                    27 to 1201, // Rune kiteshield q=1 bars=3 lvl=97
                    29 to 824, // Rune dart tip q=10 bars=1 lvl=89
                    30 to 44, // Rune arrowtips q=15 bars=1 lvl=90
                    31 to 868, // Rune knife q=5 bars=1 lvl=92
                    32 to 19580, // Rune javelin tips q=5 bars=1 lvl=91
                    34 to 9381, // Runite bolts (unf) q=10 bars=1 lvl=88
                    35 to 9431, // Runite limbs q=1 bars=1 lvl=91
                    36 to 32014, // Rune keel parts q=1 bars=5 lvl=86
                ),
            // Lovakite bar
            13354 to
                mapOf(
                    9 to 13357, // Shayzien gloves (1) q=1 bars=1 lvl=45
                    10 to 13358, // Shayzien boots (1) q=1 bars=1 lvl=47
                    11 to 13359, // Shayzien helm (1) q=1 bars=2 lvl=49
                    12 to 13360, // Shayzien greaves (1) q=1 bars=3 lvl=51
                    13 to 13361, // Shayzien platebody (1) q=1 bars=4 lvl=53
                    14 to 13362, // Shayzien gloves (2) q=1 bars=1 lvl=55
                    15 to 13363, // Shayzien boots (2) q=1 bars=1 lvl=57
                    16 to 13364, // Shayzien helm (2) q=1 bars=2 lvl=59
                    17 to 13365, // Shayzien greaves (2) q=1 bars=3 lvl=61
                    18 to 13366, // Shayzien platebody (2) q=1 bars=4 lvl=63
                    19 to 13367, // Shayzien gloves (3) q=1 bars=1 lvl=65
                    20 to 13368, // Shayzien boots (3) q=1 bars=1 lvl=67
                    21 to 13369, // Shayzien helm (3) q=1 bars=2 lvl=69
                    22 to 13370, // Shayzien greaves (3) q=1 bars=3 lvl=71
                    23 to 13371, // Shayzien platebody (3) q=1 bars=4 lvl=73
                    24 to 13372, // Shayzien gloves (4) q=1 bars=1 lvl=75
                    25 to 13373, // Shayzien boots (4) q=1 bars=1 lvl=77
                    26 to 13374, // Shayzien helm (4) q=1 bars=2 lvl=79
                    27 to 13375, // Shayzien greaves (4) q=1 bars=3 lvl=81
                    28 to 13376, // Shayzien platebody (4) q=1 bars=4 lvl=83
                    29 to 13377, // Shayzien gloves (5) q=1 bars=1 lvl=85
                    30 to 13378, // Shayzien boots (5) q=1 bars=1 lvl=87
                    31 to 13379, // Shayzien helm (5) q=1 bars=2 lvl=89
                    32 to 13380, // Shayzien greaves (5) q=1 bars=3 lvl=91
                    33 to 13381, // Shayzien body (5) q=1 bars=4 lvl=93
                ),
        )

    val tierByBar: Map<Int, Int> = barByTier.entries.associate { (k, v) -> v to k }
}
