package org.rsmod.api.area.checker

import org.rsmod.map.CoordGrid

public object Wilderness {
    public fun isWilderness(coords: CoordGrid): Boolean {
        // Wilderness surface bounds:
        // x in 2944..3392 && z in 3520..4000 on level 0
        if (coords.level == 0 && coords.x in 2944..3392 && coords.z in 3520..4000) {
            return true
        }
        // Revenant Caves (3136, 10048 to 3263, 10239)
        if (coords.x in 3136..3263 && coords.z in 10048..10239) {
            return true
        }
        // Wilderness God Wars Dungeon (3008, 10112 to 3071, 10175)
        if (coords.x in 3008..3071 && coords.z in 10112..10175) {
            return true
        }
        // King Black Dragon lair (2240, 4672 to 2303, 4735)
        if (coords.x in 2240..2303 && coords.z in 4672..4735) {
            return true
        }
        // Wilderness Agility Course dungeon
        if (coords.x in 2980..3020 && coords.z in 10300..10360) {
            return true
        }
        return false
    }

    public fun isSafeZone(coords: CoordGrid): Boolean {
        // Mage Bank surface (3080..3130, 3950..3970)
        if (coords.level == 0 && coords.x in 3080..3130 && coords.z in 3950..3970) {
            return true
        }
        // Mage Bank dungeon / basement (3089..3125, 9950..9985)
        if (coords.x in 3089..3125 && coords.z in 9950..9985) {
            return true
        }
        // Friendly Outpost (3225..3245, 3610..3630)
        if (coords.level == 0 && coords.x in 3225..3245 && coords.z in 3610..3630) {
            return true
        }
        // Rogue's Castle inner courtyard sanctuary (3280..3295, 3930..3945)
        if (coords.level == 0 && coords.x in 3280..3295 && coords.z in 3930..3945) {
            return true
        }
        return false
    }

    public fun getWildernessLevel(coords: CoordGrid): Int {
        if (!isWilderness(coords) || isSafeZone(coords)) return 0
        if (coords.level == 0 && coords.z in 3520..4000) {
            return ((coords.z - 3520) / 8) + 1
        }
        if (coords.x in 3136..3263 && coords.z in 10048..10239) {
            return ((coords.z - 10048) / 6) + 17
        }
        return 50
    }
}

public fun CoordGrid.isWilderness(): Boolean = Wilderness.isWilderness(this)

public fun CoordGrid.isWildernessSafeZone(): Boolean = Wilderness.isSafeZone(this)

public fun CoordGrid.wildernessLevel(): Int = Wilderness.getWildernessLevel(this)
