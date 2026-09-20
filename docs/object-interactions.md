# Object interactions

The cache-to-map baseline is recorded in
[work/object-interaction-audit-cache.md](../work/object-interaction-audit-cache.md).

The raw cache records are in
[work/object-interaction-cache-inventory.tsv](../work/object-interaction-cache-inventory.tsv).

This document intentionally does not describe the system as OSRS 1:1 yet. The current
generic traversal and door handlers still require a source-ID/runtime-registry join and
stateful collision/reverse-path tests before gameplay changes can be made safely.

The source inventory and deterministic join are now available in
[work/object-interaction-source-inventory.tsv](../work/object-interaction-source-inventory.tsv)
and [work/object-interaction-source-joined.tsv](../work/object-interaction-source-joined.tsv).
The join contains 14,266 focused map records: 13,391 `CACHE_ONLY`, 595
`UNVERIFIED_INSTANCE`, and 280 `DUPLICATE_HANDLER`. No `VERIFIED` or `WRONG_*` result is
claimed. Generic handlers are mapped to the runtime path in `LocInteractions.opTrigger`,
but an instance record stays unverified until region remapping and stateful interaction tests
prove the dispatch, transform, destination, and collision transition. The audit test is
`api/cache/src/test/kotlin/org/rsmod/api/cache/audit/ObjectInteractionCacheAuditTest.kt`.

Runtime-path verification is recorded in
[work/object-interaction-runtime-verification.md](../work/object-interaction-runtime-verification.md)
and the full coordinate catalog is in
[work/object-interaction-region-evidence.tsv](../work/object-interaction-region-evidence.tsv).
The static audit confirms recursive active-transform selection and the exact-event →
content-group-event dispatch order. It does not execute EventBus registration, route-to-object,
instance-template remapping, destination, or collision transitions, so gameplay remains unchanged.

The first live smoke-test attempt is blocked by the Gradle loopback failure documented in
`work/gradle-runtime-verification.log`. No server was started and no object was promoted to
`VERIFIED`; OSRS 1:1 compatibility is not claimed.

The build diagnosis is recorded in
[work/gradle-runtime-blocker.md](../work/gradle-runtime-blocker.md) and the JDK matrix in
[work/java-loopback-matrix.md](../work/java-loopback-matrix.md). Java 21's direct loopback probe
passes; Gradle 9.2.0 and cached Gradle 8.8 fail when connecting to their daemon IPC channel.

## Object fix applied

`LadderScript` and `DungeonLadderScript` now derive the destination X/Z from the interacted
`BoundLocInfo`, not from the player's post-route coordinate. The route can end on an adjacent
tile, so using `player.coords` could move the player to the wrong ladder/dungeon-ladder tile.
The existing level deltas (`+1/-1` and `±6400`) and cache/content-group registrations were
kept unchanged. This is a focused source fix; it does not prove every guild, dungeon, boss-room,
or Slayer entrance until the Gradle-blocked tests/runtime smoke tests are available.

`FossilVolcanoScript.kt` adds the exact cache-symbol/op1 route for object ID 31031
(`fossil_volcano_entrance`, `Climb-down`) to object ID 31032
(`fossil_mining_start_exit`, `Climb-up`). It uses the two unique vanilla-cache map-loc
coordinates and rejects other coordinates so instance remapping is not guessed. The reverse
double-door handlers now use `params.closesound`.

`MappedTraversalScript.kt` adds source-symbol and exact-coordinate routes for the cache-backed
Slayer Tower stairs, Troll Stronghold entrance/exit and stairs, Hunter/Farming/Woodcutting Guild
ladder or rope pairs, Darkmeyer mine, Theatre of Blood crypt, Haunted Mine, Tapoyauik temple,
Colosseum outside/lobby, Monkey Madness bamboo ladder, Warrior Guild ladder, God Wars entrance
rope, TzHaar Fight Cave and Karamja dungeon wall entrances, the two Mudskipper cave wall lanes,
and Barbarian Assault recruitment entrance/exit, plus the Lunar mine slanty-ladder pair. These
routes use the cache's actual op1 endpoints and do not apply to unlisted instance coordinates.

Rope policy: an already-present cache rope is interacted with directly; no handler in this
change consumes or requires a rope inventory item. The God Wars entrance rope is therefore
usable in both directions through object IDs 26370/26422 at `(2881,5311,2)` and
`(2881,5311,3)`. This does not fabricate rope map-locs for every no-rope quest state: those
state replacements need an exact map-loc/state pair before they can be safely enabled.

Mining Guild remains explicitly unmodified for now. Cache object ID 30367 (`mguild_ladder`)
has only `Climb-down` records at four surface coordinates and no paired cache exit or
source-proven destination in the current inventory. A destination would otherwise be a guess.

This is not a claim that all guild, dungeon, boss-room or Slayer objects are complete. In
particular, cache-only guild doors and Slayer boss-room symbols still lack a source-proven state
and destination pair. No cache, generated, build, or runtime object data was changed.
