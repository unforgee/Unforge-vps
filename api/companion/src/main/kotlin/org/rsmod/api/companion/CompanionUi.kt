package org.rsmod.api.companion

import jakarta.inject.Inject

public data class CompanionFrameLayout(
    public val x: Int,
    public val y: Int,
    public val width: Int = 260,
    public val height: Int = 92,
    public val scalePercent: Int = 100,
) {
    init {
        require(x in -4096..4096 && y in -4096..4096)
        require(width in 160..640 && height in 64..240 && scalePercent in 50..200)
    }
}

public data class CompanionFrameState(
    public val ownerName: String,
    public val ownerHitpoints: Int,
    public val ownerMaximumHitpoints: Int,
    public val companion: Companion?,
    public val layout: CompanionFrameLayout,
)

public class CompanionFrameLayoutStore @Inject constructor() {
    private val layouts = mutableMapOf<Long, CompanionFrameLayout>()

    public fun get(ownerCharacterId: Long): CompanionFrameLayout =
        layouts[ownerCharacterId] ?: CompanionFrameLayout(20, 20)

    public fun set(ownerCharacterId: Long, layout: CompanionFrameLayout): CompanionFrameLayout {
        require(ownerCharacterId > 0L)
        layouts[ownerCharacterId] = layout
        return layout
    }

    public fun restore(ownerCharacterId: Long, layout: CompanionFrameLayout) {
        require(ownerCharacterId > 0L)
        layouts[ownerCharacterId] = layout
    }

    public fun remove(ownerCharacterId: Long) {
        layouts.remove(ownerCharacterId)
    }
}
