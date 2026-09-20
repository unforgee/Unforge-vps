package org.rsmod.server.services

import kotlinx.coroutines.runBlocking

/**
 * Service handling donor points and ranks. Currently a stub implementation that logs lifecycle
 * events.
 */
public class DonorService : Service {
    public override suspend fun startup(): Unit = runBlocking {
        println("[DonorService] startup complete")
    }

    public override suspend fun shutdown(): Unit = runBlocking {
        println("[DonorService] shutdown complete")
    }
}
