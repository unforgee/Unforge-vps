package org.rsmod.content.areas.unforge.raids

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.plugin.module.PluginModule

/**
 * Raid death lifecycle: deaths inside an active raid are consumed so the raid engine decides
 * between a party revive (member waits in the lobby pad) and a wipe (run fails). The standard death
 * sequence - respawn teleport, item loss - never runs inside a raid.
 */
@Singleton
public class RaidDeathHook @Inject constructor(private val service: RaidService) : PlayerDeathHook {
    override suspend fun death(access: ProtectedAccess): Boolean = service.handleDeath(access)
}

/** Binds the raid engine's death hook into the shared death pipeline. */
public class RaidModule : PluginModule() {
    override fun bind() {
        bindInstance<RaidService>()
        addSetBinding<PlayerDeathHook>(RaidDeathHook::class.java)
    }
}
