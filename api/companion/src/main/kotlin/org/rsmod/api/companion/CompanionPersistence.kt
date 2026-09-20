package org.rsmod.api.companion

/**
 * Persistence boundary used by the live service. Implementations must use the caller's transaction.
 */
public interface CompanionPersistence {
    public fun save(companion: Companion)

    public fun load(ownerCharacterId: Long): List<Companion>
}

public object NoopCompanionPersistence : CompanionPersistence {
    override fun save(companion: Companion): Unit = Unit

    override fun load(ownerCharacterId: Long): List<Companion> = emptyList()
}
