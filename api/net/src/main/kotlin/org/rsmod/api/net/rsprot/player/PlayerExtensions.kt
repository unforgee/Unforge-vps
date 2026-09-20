package org.rsmod.api.net.rsprot.player

import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.game.entity.Player
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

internal fun Player.protectedTelejump(collision: CollisionFlagMap, dest: CoordGrid): Boolean {
    if (isAccessProtected) {
        return false
    }
    launch {
        val context = ProtectedAccessContextFactory.empty()
        val access = ProtectedAccess(this@protectedTelejump, this, context)
        access.telejump(dest, collision)
    }
    return true
}

internal fun Player.modLevelTeleMoveSpeed(developmentMode: Boolean): MoveSpeed? =
    if (modLevel.clientCode == constants.mod_clientcode_jmod || developmentMode) {
        MoveSpeed.Stationary
    } else {
        null
    }

/**
 * Resolves the mod-icons index sent as the public-chat `modicon` byte. The protocol only carries a
 * single icon per message, so staff and secondary (e.g. donator) badges are combined into one
 * pre-rendered sprite when both are present - the client installs the combined sprites at
 * `constants.rankicon_combined_base` in the same order this function computes.
 *
 * Staff-only messages keep sending [UnpackedModLevelType.clientCode] verbatim so vanilla pmod/jmod
 * crowns are unchanged; the badge index is only ever an addition, never a replacement for staff
 * rank. Staff codes without a combined sprite installed (anything outside pmod/jmod) fall back to
 * the plain staff icon.
 */
public fun Player.chatModIcon(): Int {
    val staffIcon = if (isModLevelAssigned) modLevel.clientCode else 0
    val badge = chatBadge
    val hasBadge = badge in constants.rankicon_donator_base until constants.rankicon_combined_base
    return when {
        !hasBadge -> staffIcon
        staffIcon == constants.mod_clientcode_player -> badge
        staffIcon == constants.mod_clientcode_pmod || staffIcon == constants.mod_clientcode_jmod ->
            constants.rankicon_combined_base +
                (staffIcon - 1) * constants.rankicon_donator_count +
                (badge - constants.rankicon_donator_base)
        else -> staffIcon
    }
}
