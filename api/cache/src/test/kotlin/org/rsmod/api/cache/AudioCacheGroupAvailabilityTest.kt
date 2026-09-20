package org.rsmod.api.cache

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openrs2.cache.Cache

/**
 * Verifies revision-239 audio archives are present in the served JS5 cache and that known
 * music/synth/jingle group IDs resolve. Not a packet test — cache availability only.
 */
class AudioCacheGroupAvailabilityTest {
    @Test
    fun knownAudioGroupsExistInJs5() {
        val path = Path.of("../../.data/cache/js5").normalize().toAbsolutePath()
        Cache.open(path).use { cache ->
            assertArchiveHasGroups(cache, Js5Archives.SYNTHS, 60, 62, 97)
            assertArchiveHasGroups(cache, Js5Archives.SONGS, 1, 147, 380, 884)
            assertArchiveHasGroups(cache, Js5Archives.JINGLES, 89, 90, 249)
            assertTrue(cache.exists(Js5Archives.VORBIS, 0))
            assertTrue(cache.exists(Js5Archives.MIDIPATCHES, 0))
        }
    }

    private fun assertArchiveHasGroups(cache: Cache, archive: Int, vararg groups: Int) {
        var count = 0
        for (group in cache.list(archive)) {
            count++
        }
        assertTrue(count > 0) { "archive $archive has no groups" }
        for (group in groups) {
            assertTrue(cache.exists(archive, group)) {
                "archive=$archive group=$group missing from js5"
            }
        }
    }
}
