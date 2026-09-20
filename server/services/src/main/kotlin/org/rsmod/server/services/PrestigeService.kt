package org.rsmod.server.services

import kotlinx.coroutines.runBlocking

/** Service handling prestige levels. */
public class PrestigeService : Service {
    public override suspend fun startup(): Unit = runBlocking {
        println("[PrestigeService] startup complete")
    }

    public override suspend fun shutdown(): Unit = runBlocking {
        println("[PrestigeService] shutdown complete")
    }
}
