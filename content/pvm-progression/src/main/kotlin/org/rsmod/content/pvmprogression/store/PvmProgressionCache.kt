package org.rsmod.content.pvmprogression.store

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player

/**
 * Process-wide in-memory store. Used by integration tests (where the DB is `ThrowDatabase`) and as
 * a no-op default when no SQL store is bound.
 *
 * It is safe for concurrent access: a [ConcurrentHashMap] guards the top-level map, and each
 * character's [PvmProgressionData] is replaced atomically (copy-on-write) rather than mutated in
 * place, so a save never observes a half-updated data object.
 */
@Singleton
class InMemoryPvmProgressionStore @Inject constructor() : PvmProgressionStore {
    private val data = ConcurrentHashMap<Int, PvmProgressionData>()

    override fun load(characterId: Int): PvmProgressionData? = data[characterId]

    override fun save(characterId: Int, state: PvmProgressionData) {
        data[characterId] = state
    }

    override fun delete(characterId: Int) {
        data.remove(characterId)
    }

    /** Test helper: snapshot the entire store. */
    internal fun snapshot(): Map<Int, PvmProgressionData> = data.toMap()
}

/**
 * The in-process cache the managers actually talk to.
 *
 * Managers never call the store directly on the game thread - that would block on DB I/O. Instead
 * they read/write this cache, which is keyed by `characterId` and holds the authoritative live
 * state. On logout the [PvmProgressionSavePipeline] flushes the cached entry to the store inside
 * the same transaction as the rest of the character save.
 *
 * The cache is a [ConcurrentHashMap] of mutable holders; each holder is only ever touched by the
 * owning player's game-thread work, so no cross-player locking is needed.
 */
@Singleton
class PvmProgressionCache @Inject constructor() {
    private val entries = ConcurrentHashMap<Int, PvmProgressionData>()

    /** Returns the live state for [characterId], creating an empty record on first access. */
    fun getOrCreate(characterId: Int): PvmProgressionData =
        entries.computeIfAbsent(characterId) { PvmProgressionData.EMPTY }

    /** Replaces the live state for [characterId]. */
    fun put(characterId: Int, data: PvmProgressionData) {
        entries[characterId] = data
    }

    /** Returns the live state without creating an entry, or `null` if none. */
    fun get(characterId: Int): PvmProgressionData? = entries[characterId]

    /** Removes the cached entry (after a save flush). */
    fun evict(characterId: Int) {
        entries.remove(characterId)
    }

    /** Snapshot for debug / `::pvmprogression` admin views. */
    fun snapshot(): Map<Int, PvmProgressionData> = entries.toMap()
}

/**
 * The [CharacterDataStage.Pipeline] that flushes the live cache to the store on save.
 *
 * Registered as a multibinding by [PvmProgressionModule], so `AccountSavingService` calls [save]
 * inside the same transaction as every other character pipeline. Loading on login is performed on
 * the game thread by `PvmProgressionLoginSync` (the metadata stage runs off-thread and cannot
 * safely hand a mutable data object to the managers).
 */
@Singleton
class PvmProgressionSavePipeline
@Inject
constructor(private val cache: PvmProgressionCache, private val store: PvmProgressionStore) :
    CharacterDataStage.Pipeline {
    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        // Loading is performed on the game thread by PvmProgressionLoginSync.
    }

    override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
        val live = cache.get(characterId) ?: return
        store.save(characterId, live)
    }
}
