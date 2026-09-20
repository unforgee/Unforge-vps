package org.rsmod.server.services

import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/** Simple tests to ensure service lifecycle methods execute without exceptions. */
class ServiceLifecycleTest {
    @Test
    fun testDonorServiceLifecycle() = runBlocking {
        val service = DonorService()
        service.startup()
        service.shutdown()
    }

    @Test
    fun testPrestigeServiceLifecycle() = runBlocking {
        val service = PrestigeService()
        service.startup()
        service.shutdown()
    }

    @Test
    fun testConstructionServiceLifecycle() = runBlocking {
        val service = ConstructionService()
        service.startup()
        service.shutdown()
    }

    @Test
    fun testQuestServiceLifecycle() = runBlocking {
        val service = QuestService()
        service.startup()
        service.shutdown()
    }
}
