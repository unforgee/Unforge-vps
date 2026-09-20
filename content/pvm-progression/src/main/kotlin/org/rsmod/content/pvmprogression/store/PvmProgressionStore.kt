package org.rsmod.content.pvmprogression.store

/**
 * The persistence boundary for PvM Progression 2.0.
 *
 * Everything the feature remembers about a player - bounty boards, boss records, mutation stats,
 * challenge stats, streak state - lives behind this interface. The live server binds a SQL store
 * (written through the character save transaction); the integration test harness, which replaces
 * the database with `ThrowDatabase`, binds [InMemoryPvmProgressionStore] instead, so the whole
 * feature can be exercised end-to-end without a real DB.
 *
 * The interface is deliberately coarser than "one method per field": each subsystem exposes a typed
 * data class and the store loads/saves those as units. This keeps the SQL surface tiny and avoids
 * the N+1 query pattern a per-field API would invite.
 */
interface PvmProgressionStore {
    /** Loads the full progression state for [characterId], or `null` if none exists yet. */
    fun load(characterId: Int): PvmProgressionData?

    /** Saves [data] for [characterId], replacing any prior state. */
    fun save(characterId: Int, data: PvmProgressionData)

    /** Removes all progression state for [characterId] (used by `::resetpvmprogression`). */
    fun delete(characterId: Int)
}
