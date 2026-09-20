package org.rsmod.server.services

import kotlinx.coroutines.runBlocking

/**
 * Service handling player construction houses. Currently a stub implementation that logs lifecycle
 * events.
 */
public class ConstructionService : Service {
    public override suspend fun startup(): Unit = runBlocking {
        println("[ConstructionService] startup complete")
    }

    public override suspend fun shutdown(): Unit = runBlocking {
        println("[ConstructionService] shutdown complete")
    }
}
