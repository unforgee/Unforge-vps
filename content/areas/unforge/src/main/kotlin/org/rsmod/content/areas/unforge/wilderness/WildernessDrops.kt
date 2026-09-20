package org.rsmod.content.areas.unforge.wilderness

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcDropTables
import org.rsmod.api.death.NpcDropTables.DropItem
import org.rsmod.api.death.NpcDropTables.DropTable

/**
 * Shared Deadman/Bounty Hunter rewards for Unforge Wilderness bosses and revenant patrols. Every
 * registered target can roll every item in the master pool. Recipient class only changes table
 * weights: low revenants are supply/crate heavy, bosses are ancient-gear heavy, and the apex
 * encounter has the strongest unique rates.
 */
@Singleton
public class WildernessDrops @Inject constructor(private val dropTables: NpcDropTables) {

    public object Items {
        // Deadman uniques
        public const val AGS_DEADMAN: Int = 29605
        public const val VOIDWAKER_DEADMAN: Int = 29607
        public const val VOLATILE_STAFF_DEADMAN: Int = 29609
        public const val DARK_BOW_DEADMAN: Int = 29611
        public const val TOXIC_STAFF_DEADMAN: Int = 33036
        public const val IMBUED_ZAMORAK_CAPE_DEADMAN: Int = 29613
        public const val IMBUED_GUTHIX_CAPE_DEADMAN: Int = 29615
        public const val IMBUED_SARADOMIN_CAPE_DEADMAN: Int = 29617
        public const val DEADMAN_CHEST: Int = 13317
        public const val DEADMAN_LEGS: Int = 13318
        public const val DEADMAN_CAPE: Int = 13319
        public const val DEADMAN_SKULL: Int = 33065
        public const val DEADMAN_TELEPORT_TABLET: Int = 13666

        // Ancient Warrior / Bounty Hunter
        public const val VESTA_SPEAR: Int = 22610
        public const val VESTA_LONGSWORD: Int = 22613
        public const val VESTA_CHAINBODY: Int = 22616
        public const val VESTA_PLATESKIRT: Int = 22619
        public const val STATIUS_WARHAMMER: Int = 22622
        public const val STATIUS_FULL_HELM: Int = 22625
        public const val STATIUS_PLATEBODY: Int = 22628
        public const val STATIUS_PLATELEGS: Int = 22631
        public const val MORRIGAN_COIF: Int = 22634
        public const val MORRIGAN_LEATHER_BODY: Int = 22636
        public const val MORRIGAN_LEATHER_CHAPS: Int = 22638
        public const val MORRIGAN_JAVELIN: Int = 22641
        public const val MORRIGAN_THROWING_AXE: Int = 22644
        public const val ZURIEL_HOOD: Int = 22647
        public const val ZURIEL_ROBE_TOP: Int = 22650
        public const val ZURIEL_ROBE_BOTTOM: Int = 22653
        public const val ZURIEL_STAFF: Int = 22656
        public const val BOUNTY_CRATE_T1: Int = 28082
        public const val BOUNTY_CRATE_T3: Int = 28092
        public const val BOUNTY_CRATE_T5: Int = 28094
        public const val BOUNTY_CRATE_T7: Int = 28096
        public const val BOUNTY_CRATE_T9: Int = 28098
        public const val BOUNTY_SUPPLY_CRATE: Int = 30576
        public const val BH_ORNAMENT_KIT: Int = 28017
        public const val COINS: Int = 995
        public const val BLOOD_MONEY: Int = 13307
    }

    public fun registerAll() {
        registerBossDrops()
        registerRevenantDrops()
    }

    private fun registerBossDrops() {
        val regularBossIds =
            listOf(
                6503, // Callisto
                6504, // Venenatis
                6611, // Vet'ion
                2054, // Chaos Elemental
                239, // King Black Dragon
                5886, // Abyssal Sire
                5862, // Cerberus
                8615, // Alchemical Hydra
                319, // Corporeal Beast
                16296, // Corrupted Nechryarch
                16299, // Brutal lava dragon
            )
        val regularGuaranteed =
            listOf(
                DropItem(Items.BLOOD_MONEY, 250, 750, 1),
                DropItem(Items.COINS, 50_000, 200_000, 1),
                DropItem(Items.DEADMAN_SKULL, 1, 1, 1),
            )
        val regularTables =
            wildernessTables("boss", dmmWeight = 10, ancientWeight = 20, crateWeight = 30)
        for (id in regularBossIds) {
            dropTables.register(id, regularGuaranteed, regularTables)
        }

        // Apex boss: complete pool plus guaranteed T9 crate and stronger cash floor.
        val apexGuaranteed =
            listOf(
                DropItem(Items.BLOOD_MONEY, 1_500, 3_000, 1),
                DropItem(Items.COINS, 250_000, 1_000_000, 1),
                DropItem(Items.DEADMAN_SKULL, 2, 3, 1),
                DropItem(Items.BOUNTY_CRATE_T9, 1, 1, 1),
            )
        val apexTables =
            wildernessTables("apex", dmmWeight = 40, ancientWeight = 60, crateWeight = 80)
        dropTables.register(8388, apexGuaranteed, apexTables)
    }

    private fun registerRevenantDrops() {
        val revenantNpcIds =
            listOf(
                11246, // Revenant Maledictus
                7940, // Revenant Dragon
                7939, // Revenant Knight
                7938, // Revenant Dark Beast
                7937, // Revenant Ork
                7936, // Revenant Demon
                7935, // Revenant Hellhound
                7934, // Revenant Cyclops
            )
        val guaranteed =
            listOf(
                DropItem(Items.BLOOD_MONEY, 50, 150, 1),
                DropItem(Items.COINS, 10_000, 50_000, 1),
            )
        // Full master pool for every revenant; weights keep low revs supply-oriented.
        val tables =
            wildernessTables("revenant", dmmWeight = 5, ancientWeight = 25, crateWeight = 70)
        for (id in revenantNpcIds) {
            dropTables.register(id, guaranteed, tables)
        }
    }

    private fun deadmanTable(name: String, weight: Int): DropTable =
        DropTable(
            name = "${name}_deadman_uniques",
            weight = weight,
            items =
                listOf(
                    DropItem(Items.AGS_DEADMAN, 1, 1, 2),
                    DropItem(Items.VOIDWAKER_DEADMAN, 1, 1, 2),
                    DropItem(Items.VOLATILE_STAFF_DEADMAN, 1, 1, 2),
                    DropItem(Items.DARK_BOW_DEADMAN, 1, 1, 3),
                    DropItem(Items.TOXIC_STAFF_DEADMAN, 1, 1, 3),
                    DropItem(Items.IMBUED_ZAMORAK_CAPE_DEADMAN, 1, 1, 4),
                    DropItem(Items.IMBUED_GUTHIX_CAPE_DEADMAN, 1, 1, 4),
                    DropItem(Items.IMBUED_SARADOMIN_CAPE_DEADMAN, 1, 1, 4),
                    DropItem(Items.DEADMAN_CHEST, 1, 1, 5),
                    DropItem(Items.DEADMAN_LEGS, 1, 1, 5),
                    DropItem(Items.DEADMAN_CAPE, 1, 1, 5),
                ),
        )

    private fun ancientWarriorTable(name: String, weight: Int): DropTable =
        DropTable(
            name = "${name}_bh_ancient_gear",
            weight = weight,
            items =
                listOf(
                    DropItem(Items.VESTA_LONGSWORD, 1, 1, 1),
                    DropItem(Items.VESTA_SPEAR, 1, 1, 2),
                    DropItem(Items.VESTA_CHAINBODY, 1, 1, 4),
                    DropItem(Items.VESTA_PLATESKIRT, 1, 1, 4),
                    DropItem(Items.STATIUS_WARHAMMER, 1, 1, 1),
                    DropItem(Items.STATIUS_FULL_HELM, 1, 1, 3),
                    DropItem(Items.STATIUS_PLATEBODY, 1, 1, 4),
                    DropItem(Items.STATIUS_PLATELEGS, 1, 1, 4),
                    DropItem(Items.MORRIGAN_COIF, 1, 1, 3),
                    DropItem(Items.MORRIGAN_LEATHER_BODY, 1, 1, 4),
                    DropItem(Items.MORRIGAN_LEATHER_CHAPS, 1, 1, 4),
                    DropItem(Items.MORRIGAN_JAVELIN, 25, 75, 6),
                    DropItem(Items.MORRIGAN_THROWING_AXE, 25, 75, 6),
                    DropItem(Items.ZURIEL_HOOD, 1, 1, 3),
                    DropItem(Items.ZURIEL_ROBE_TOP, 1, 1, 4),
                    DropItem(Items.ZURIEL_ROBE_BOTTOM, 1, 1, 4),
                    DropItem(Items.ZURIEL_STAFF, 1, 1, 3),
                ),
        )

    private fun bountyRewardTable(name: String, weight: Int): DropTable =
        DropTable(
            name = "${name}_bounty_rewards",
            weight = weight,
            items =
                listOf(
                    DropItem(Items.BOUNTY_CRATE_T1, 1, 2, 6),
                    DropItem(Items.BOUNTY_CRATE_T3, 1, 2, 5),
                    DropItem(Items.BOUNTY_CRATE_T5, 1, 1, 4),
                    DropItem(Items.BOUNTY_CRATE_T7, 1, 1, 3),
                    DropItem(Items.BOUNTY_CRATE_T9, 1, 1, 2),
                    DropItem(Items.BOUNTY_SUPPLY_CRATE, 1, 3, 5),
                    DropItem(Items.BH_ORNAMENT_KIT, 1, 1, 3),
                    DropItem(Items.DEADMAN_TELEPORT_TABLET, 1, 5, 5),
                    DropItem(Items.DEADMAN_SKULL, 1, 1, 2),
                ),
        )

    private fun wildernessTables(
        name: String,
        dmmWeight: Int,
        ancientWeight: Int,
        crateWeight: Int,
    ): List<DropTable> =
        listOf(
            deadmanTable(name, dmmWeight),
            ancientWarriorTable(name, ancientWeight),
            bountyRewardTable(name, crateWeight),
        )
}
