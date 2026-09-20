# Fragment System Master Plan

## Scope lock

This system contains only the fragment layer requested for the League-style feature:

- 250 server-defined fragments.
- 50 sets, five fragments per set, with one five-piece set bonus per set.
- Fragment acquisition, duplicate conversion to XP, and levels 1–10.
- Item-instance rarity/quality hooks and companion stat hooks.
- A data-driven proc vocabulary for area damage, bleed, freeze, forced movement, item procs, and companion procs.

It intentionally does not add League points, relic unlock trees, area unlocks, quest gates, boss locks, or a second progression currency.

## Runtime topology

`content/other/fragments` owns the catalogue, in-memory state, persistence pipeline, drop logic, player commands, and content implementations. API modules expose only generic hook contracts:

1. `CharacterDataStage.Pipeline` loads and saves one character row.
2. `NpcKilledEvent` is the first live obtain source. It is server-side and only awards on a valid attributed combat kill.
3. `EquipmentRollModifier` is called by `EquipmentInstanceService` before the deterministic rarity/quality roll.
4. `CompanionStatAugmenter` is called inside the canonical companion stat calculator and included in the cache key.

This keeps fragment state authoritative and makes the item/companion integrations optional: a server build without the content module still has the same base APIs and zero fragment modifiers.

## Progression rules

- First obtain creates the fragment at level 1.
- A duplicate gives 250 XP to that same fragment.
- XP is capped at the level-10 threshold; level is always derived from XP.
- Completing all five IDs in a set activates its set bonus. There is no unlock requirement.
- State is encoded as stable `fragment-id=xp` pairs. Unknown IDs are ignored when loading so future catalogue edits cannot crash a login.

## Obtain rules

The initial drop table is deliberately conservative while the catalogue is validated in live play:

- normal NPC: 1.25% per valid kill;
- NPC level 100+: 3.50% per valid kill;
- names containing `boss` or `guardian`: 7.50% per valid kill.

The selected fragment is uniformly sampled from all 250 IDs. This can later be replaced by weighted source tables without changing persistence or player state. Admin commands (`fragmentgive`, `fragmentxp`, `fragmentreset`) provide deterministic QA without affecting normal obtain rules.

## Balance guardrails

- Every additive combat contribution is represented in the effect enum and routed through one snapshot.
- Companion cooldown reduction still passes through the existing global cap.
- Item rarity modifiers alter roll weights through the canonical policy; they do not mutate an already-created item.
- Item quality is clamped to 100.
- Proc effects are data and do not silently execute from the catalogue. Each future combat consumer must explicitly consume the snapshot and apply its own hit/target safety rules.

## Verification gates

1. Catalogue initialization must fail if it is not exactly 50 sets, 250 fragments, or five fragments per set.
2. Persistence round-trip must preserve IDs, XP, and derived levels.
3. Duplicate XP must stop at level 10.
4. Fragment state changes must change the companion cache stamp.
5. Item rolls without fragment state must remain deterministic and unchanged.
6. A valid kill can award at most one fragment per event.
7. Invalid/unattributed kill events award nothing.

## Future extension points

The next safe additions are a fragment interface that displays `FragmentCatalog` data, skill/reward source adapters that call `FragmentService.obtain`, and combat consumers for the already-defined proc types. None of those require changing the save format or adding an unlock system.
