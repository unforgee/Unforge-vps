package org.rsmod.content.pvmprogression.config

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.atomic.AtomicReference

/**
 * The single mutable holder for the live [PvmProgressionConfig] snapshot.
 *
 * Managers take this class rather than a raw config so tuning can be changed while the server runs
 * (`::pvmconfig ...`, `::mutationchance ...`) without a restart. The snapshot is held in an
 * [AtomicReference], so a swap is observed atomically by every manager: no manager can ever see a
 * config that is half old and half new.
 *
 * A replacement snapshot is sanitised through [sanitizeConfig] before it is published, which clamps
 * every value into a safe range. An operator (or a malformed realm config) therefore cannot push
 * the feature into a degenerate state such as `1/1` mutation chance, a negative reward, or a
 * zero-size bounty board.
 */
@Singleton
class PvmProgressionConfigSource @Inject constructor() {
    private val current = AtomicReference(sanitizeConfig(PvmProgressionConfig.DEFAULT))

    /** The live snapshot. Always sanitised - never a partially valid config. */
    val config: PvmProgressionConfig
        get() = current.get()

    /** Replaces the live snapshot with [candidate], after sanitising it. */
    fun update(candidate: PvmProgressionConfig) {
        current.set(sanitizeConfig(candidate))
    }

    /** Applies [transform] to the live snapshot and publishes the sanitised result. */
    fun update(transform: (PvmProgressionConfig) -> PvmProgressionConfig) {
        update(transform(config))
    }

    /** Convenience accessor used throughout the managers. */
    operator fun invoke(): PvmProgressionConfig = config
}
