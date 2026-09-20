# Boss implementation log

## 2026-09-19 — audit baseline

- Scope: Unforge / Unforge revision 239 repository.
- Files inspected: Unforge boss arena, Wilderness boss manager/hotspots/drops, Slayer scripts,
  raid models/service/mechanics, Unforge drop loader, cache-enricher NPC definitions and spawn
  data.
- Change made: created the initial coverage matrix in `docs/BOSS_COVERAGE.md`.
- No boss is marked COMPLETE: no interactive enter/fight/kill/loot/KC/reset run was available in
  this audit pass.
- Important finding: `WildernessBosses.kt` currently emits messages and spotanims for its named
  mechanics but does not implement the corresponding damage/projectile/phase behaviour. It must
  not be treated as complete.

## 2026-09-19 — master command and first E2E combat slice

- Files changed:
  - `content/areas/unforge/src/main/kotlin/org/rsmod/content/areas/unforge/wilderness/WildernessScript.kt`
  - `content/areas/unforge/src/main/kotlin/org/rsmod/content/areas/unforge/wilderness/WildernessBosses.kt`
- Added admin-only `::bossmaster [status|check]`. It reports live roaming registrations and NPC
  state, links the coverage sources, and prints the remaining manual E2E gates. It deliberately
  cannot promote a boss to COMPLETE.
- Wilderness named mechanics now queue a real NPC-origin hit through `ProtectedAccess.queueHit`
  with `HitType.Magic`; the existing standard player-hit modifier therefore remains responsible
  for protection-magic prayer mitigation. Damage is intentionally bounded while per-boss verified
  profiles are still absent.
- Target acquisition now faces the player and transitions to `FIGHTING` when an attack is issued.
- Tests: source audit PASS; JSON validation PASS; Gradle compile BLOCKED before task execution by
  `java.io.IOException: Unable to establish loopback connection`; interactive server E2E NOT_TESTED.
- Status remains PARTIAL. Remaining gates: verified projectile/animation timing, boss-specific
  mechanics/phases, kill/drop/KC/collection-log assertions, death/logout/teleport cleanup, and a
  second-run smoke test.

Next batch: add encounter-local kill ownership and centralized boss KC to the Wilderness path, then
run the same E2E checklist against a real server/client session before promoting any row.

## 2026-09-19 — Wilderness KC and respawn lifecycle

- Files changed:
  - `content/areas/unforge/src/main/kotlin/org/rsmod/content/areas/unforge/wilderness/WildernessBossKillCounts.kt`
  - `content/areas/unforge/src/main/kotlin/org/rsmod/content/areas/unforge/wilderness/WildernessBosses.kt`
  - `content/areas/unforge/src/main/kotlin/org/rsmod/content/areas/unforge/wilderness/WildernessScript.kt`
- `NpcKilledEvent` is now consumed only when the dead NPC is one of the manager's active boss
  instances. This prevents unrelated Wilderness kills from incrementing boss KC.
- Nine registrations use existing permanent revision-239 `total_*_kills` varps, so values are saved
  by the existing character account repository. Sotetseg remains explicitly unmapped rather than
  reusing an unrelated counter.
- The event listener clears the defeated instance and schedules the existing 100-cycle respawn path.
  Drops remain owned by the normal death pipeline and are not rolled again by the KC listener.
- Tests: static event-order/source audit PASS; persistence mapping audit PASS; interactive kill →
  loot → KC → respawn E2E NOT_TESTED because Gradle/server launch remains blocked by the JVM
  loopback failure.
- A fresh `:content:areas:unforge:spotlessApply :content:areas:unforge:compileKotlin`
  attempt after this batch failed before Gradle task execution with the same
  `Unable to establish loopback connection` error.

## 2026-09-19 — global Wilderness BH/DMM drop pool

- File changed: `content/areas/unforge/src/main/kotlin/org/rsmod/content/areas/unforge/wilderness/WildernessDrops.kt`.
- All registered Wilderness bosses now use one complete pool containing every configured Deadman
  weapon, Deadman cape/armour, Ancient Warrior item, bounty crate tier T1/T3/T5/T7/T9, supply
  crate, ornament kit, teleport tablet and ammunition reward.
- All registered revenant NPCs use that same complete pool. Revenants use supply-heavy table weights;
  regular bosses use a stronger Ancient Warrior weighting; Sotetseg uses the strongest unique
  weighting and keeps its guaranteed T9 crate.
- Ordinary Wilderness NPCs are unaffected because registration remains limited to the existing
  boss and revenant ID lists.
- Validation: source item/registration audit PASS; compile/live drop-roll E2E NOT_TESTED because
  Gradle remains blocked before task execution by the JVM loopback failure.

## 2026-09-19 — source-backed additions for non-Wilderness bosses

- Audited the generated runtime table at
  `content/areas/unforge/src/main/resources/org/rsmod/content/areas/unforge/unforge_drops.txt`
  against the repository's boss reference tables under `content/kronos-data/npcs/drops/eco`.
- The generated table already covers the core Zulrah, KBD, Corp, Chaos Elemental, Kraken, KQ and
  God Wars leader drops, so those were not duplicated.
- Added `content/areas/unforge/src/main/kotlin/org/rsmod/content/areas/unforge/drops/UnforgeBossDropAdditions.kt`.
  It registers merged, source-backed tables for:
  - Vorkath (`8059`, `8061`): bones/hide, head, necklace, wrath talisman, jar/visages, coins,
    manta rays, runes, bolts, materials and elite clue.
  - Alchemical Hydra (`8615`–`8622`): Hydra bones, Hydra unique set, dragon knife/thrownaxe,
    runes, bolts, supplies, dragon bones, crystal key and elite clue.
  - Abyssal Sire (`5886`–`5891`, `5908`): ashes, Unsired, rune/material supplies, potions, runes,
    onyx bolt tips and elite clue.
  - Cerberus (`5862`, `5863`, `5866`): ashes, four crystal/utility uniques, coins, potions, seeds,
    ores and elite clue.
  - Giant Mole (`5779`, `6499`): big bones, mole claw, mole skin and source-backed supplies.
  - Scorpia (`6615`): Odium/Malediction shards, hard/elite clues and source-backed supplies.
- The additions use item IDs from the repository's reference JSON files, reconciled against the
  active object symbols where the legacy source is stale, and do not alter the generated table.
  `NpcDropTables.register` merges the new entries if the generated source is later expanded.
- One reference-data mismatch was reconciled against the active revision-239 object symbols:
  Vorkath's head uses runtime ID `21907` (`objs.vorkath_head`), while the legacy Vorkath JSON lists
  `2425`, which is `cert_skinpaste` in this cache.
- The added tables use a supply-heavy 1/99 weighting for rare-boss pools (4/96 for Vorkath's
  head/utility pool) so a source-backed unique cannot become a de facto common drop under the
  loader's table-first roll semantics.
- Validation: source-ID audit PASS; compile/live drop-roll E2E NOT_TESTED because Gradle remains
  blocked before task execution by the JVM loopback failure.

## 2026-09-20 — custom Unforge bosses made playable

- `UnforgeCustomNpcs.kt` already defined the user's custom `Corrupted Nechryarch` and
  `Brutal lava dragon` types, but no gameplay pool referenced them.
- Added both custom NPC references to the Solo Boss Arena's all/hard pools. They can now be
  selected by the normal `::soloboss` / `::nm` flow using their existing cache-compatible models.
- Added both NPC ids (`16296`, `16299`) to the Unforge Wilderness drop registration and gave
  them a dedicated Dawnsteel starter-set table.
- Added five cache-compatible Dawnsteel starter clones (`65020`–`65024`) under
  `.data/content/items/clones` with symbol entries, preserving the existing clone/cache pipeline.
- Replaced the three-piece iron/leather combat starter selection in
  `StarterCombatSandboxScript.kt` with the five-piece Dawnsteel set; new standalone accounts now
  receive the set on first login.
- Tripo API integration is prepared in `tools/standalone/Invoke-TripoStarterArmor.ps1`. The API key
  authenticated successfully but its separate API balance is `0.0`. The user account's Tripo Studio
  balance was available, so the starter armor was generated in Studio using 55 credits and exported as
  `work/tripo/Unforge-dawnsteel-starter-armor.glb` (remaining Studio balance: 3090).
- The GLB is kept as the generated visual asset. The active game cache uses the five cache-compatible
  Dawnsteel clones until a revision-239 model-cache conversion is performed; that conversion is
  separate from Tripo generation.
