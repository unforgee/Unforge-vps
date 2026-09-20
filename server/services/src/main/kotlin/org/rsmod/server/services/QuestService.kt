package org.rsmod.server.services

import kotlinx.coroutines.runBlocking

/** Service handling player quests. Currently a stub implementation that logs lifecycle events. */
public class QuestService : Service {
    override suspend fun startup(): Unit = runBlocking {
        println("[QuestService] startup complete")
    }

    override suspend fun shutdown(): Unit = runBlocking {
        println("[QuestService] shutdown complete")
    }
}
