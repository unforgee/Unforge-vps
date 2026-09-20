# `::points` — Points & Progress journal page

A server-authoritative, read-only report of every player-bound point balance in the game,
rendered as a `Points & Progress` page inside the side journal (quest tab family) and opened
with the `::points` player command.

## Usage

- `::points` — selects the Points sub-tab and focuses the journal side tab
  (`toplevel_sidebutton_switch` → `toplevel_details`). No arguments, no mod level, no side
  effects.
- The same page is reachable by clicking the fifth journal tab row (the repurposed vanilla
  `league_list` slot, the same pattern `unforge_perks` uses for `adventurepath_list`).

## Point sources

Each row reads exactly one authoritative source. Nothing in `PlayerPoints` mints or mutates
balances; inactive systems are rendered as `Inactive` and never contribute to the total.

| Key                 | Display              | Category    | Source (authority)                                        | Status   |
| ------------------- | -------------------- | ----------- | --------------------------------------------------------- | -------- |
| `quest_points`      | Quests               | Quest       | `qp` varp (vanilla; no quest system writes it yet → `0`)  | Active   |
| `achievement_points`| Achievements         | Quest       | —                                                         | Inactive |
| `skill_points`      | Skill                | Progression | `skill_points` varp via `SkillingPoints.balance` (skilling-core) | Active |
| `companion_talent`  | Pet talents          | Progression | `CompanionService.owned(characterId)` → `talentPoints - spent`, clamped ≥ 0 | Active |
| `perk_points`       | Perk                 | Combat      | `perk_points` varp via `PerkService.points`               | Active   |
| `pvm_points`        | PvM                  | Combat      | `pvm_points` varp (id `5686`) via `PvmPoints.balance` — minted by `NpcKilledEvent` in `content/pvm-points`, scaled by npc combat level | Active |
| `boss_points`       | Boss                 | Combat      | —                                                         | Inactive |
| `slayer_points`     | Slayer               | Combat      | `cw_slayer_points` varp                                   | Active   |
| `wild_slayer_points`| Wildy slayer         | Combat      | `cw_slayer_wild_points` varp (separate wilderness economy)| Active   |
| `support_points`    | Support              | Activity    | —                                                         | Inactive |
| `donator_points`    | Donator              | Account     | `cw_donator_points` varp (the `::donorstatus` balance)    | Active   |

### Single-source rule

Regular and wilderness slayer points are two genuinely separate economies and are reported
as distinct rows — no currency is read from two places, so the total can never double-count.
When a designed-but-unbuilt system (boss/achievement/support) lands, its row flips to a
real source instead of duplicating an existing varp.

### Total rule

`Total points` = the exact sum of **active** entries only, in registry order. Inactive rows
are excluded by definition (they hold `0` and `active = false`).

## Presentation

Interface `unforge_points` (id `1007`), a 190×300 server-authored side panel opened as an
overlay inside `side_journal:tab_container` — same frame/palette as `unforge_perks`.

```
Points
─────────────────────────────
Quest
  Quests                7
  Achievements          Inactive
Progression
  Skill                 6
  Pet talents           3
Combat
  Perk                  4
  PvM                   9
  ...                            [scrollable]
─────────────────────────────
Total:                 43
```

- The body is a fixed pool of 24 generic row slots inside one natively scrollable `content`
  layer (`scrollHeight`), with up/down rail buttons driven by server-side `IfSetScrollPos`.
- Each slot carries three hidden children — `row{i}_head` (category header) or
  `row{i}_label` + `row{i}_value` — so any number of registry entries renders without
  interface changes; leftover slots stay hidden.
- The total is pinned outside the scroll layer so it is always visible.

## Files

- `content/interfaces/journal-tab/.../PlayerPoints.kt` — `PlayerPointEntry`, categories,
  `SOURCES` registry (the single extension point), `entries()`/`total()`.
- `.../configs/PointsVarps.kt` — varp references for externally-owned balances.
- `.../configs/UnforgePointsInterfaceBuilder.kt` — `unforge_points` (id `1007`) builder.
- `.../configs/UnforgePointsInterfaces.kt` — interface/component references.
- `.../SideJournalTab.kt` — `Points(varValue = 4)` tab entry.
- `.../PlayerExtensions.kt` — `openJournalTab`/`closeJournalTab`/`prepareJournalTab` wiring.
- `.../configs/JournalComponents.kt` — `points_list` = `side_journal:league_list` ref.
- `.../scripts/JournalTabScript.kt` — `league_list` button → `SideJournalTab.Points`.
- `.../scripts/PointsJournalScript.kt` — `::points` command, `onIfOpen` render, scroll rail.
- `content/pvm-points/.../PvmPoints.kt` — `pvm_points` varp builder, `pvmPointsForLevel`
  difficulty tiers, `PvmPoints` balance/award/spend service.
- `content/pvm-points/.../PvmPointsScript.kt` — `NpcKilledEvent` subscriber that mints the
  award to `event.killer` and messages the amount (silent on `0`-point trivial mobs).
- Symbols: `.data/symbols/interface.sym` (`1007`), `.data/symbols/component.sym` (88 rows),
  `.data/symbols/varp.sym` (`5686 pvm_points` — permanent, server-only, never transmitted).

## PvM points — earning rules

`content/pvm-points` mints `pvm_points` from attributed npc kills only. The module subscribes
to the already-published `NpcKilledEvent` (fired once per kill with a resolved player killer by
`NpcDeath.spawnDeathDrops`), so there is no second kill-attribution path and perk-point awards
are untouched. The amount scales with the killed npc's `visType.vislevel`:

| Combat level | Points |
| ------------ | ------ |
| 0–9          | 0 (trivial — no write, no message, spawn-camping cannot mint) |
| 10–49        | 1      |
| 50–99        | 2      |
| 100–199      | 5      |
| 200–349      | 10     |
| 350+         | 20     |

The `visLevel >= 100` tiers coincide with the `PerkService.isBoss` threshold, but PvM and perk
points stay separate currencies: perk points buy permanent perks, PvM points are reserved for
PvM-side rewards (a spend path exists via `PvmPoints.spend`, no shop yet). The balance is a
permanent, server-only varp — the journal reads it through `points_varps.pvmPoints` like any
other externally-owned balance.

## Tests

`content/interfaces/journal-tab/src/integration/.../PointsJournalScriptTest.kt` — 6 tests:

1. Registry returns all 13 sources in deterministic render order.
2. Total equals the exact sum of active balances (seeded varps + a recruited companion).
3. Inactive systems report `0`/`Inactive` and stay out of the total.
4. No duplicate source keys (total cannot double-count).
5. `::points` opens the overlay in the journal container and mutates nothing — every seeded
   balance is re-read identical afterwards; category/label/`Inactive`/total texts are
   pushed as `IfSetText`.
6. Entries are always read for the requesting player (two players, different varps).

`content/pvm-points` — 9 tests, all passing:

- `PvmPointsTableTest` (4): tier boundaries — `<10 → 0`, `10–49 → 1`, `50–99 → 2`,
  `100–199 → 5`, `200–349 → 10`, `350+ → 20`.
- `PvmPointsScriptTest` (5, real `NpcKilledEvent` publishes): award scales with npc level and
  reaches the varp + chatbox; trivial mobs award nothing and stay silent; awards accumulate
  across kills; only `event.killer` is credited (bystander stays `0`); `spend` is
  all-or-nothing (rejects `0`/over-balance, no partial deduction).

## Deferred / not yet possible

- Boss, achievement and support points have no earning infrastructure in the repo yet;
  they are shown as `Inactive` until their systems land.
- The fifth tab row reuses the vanilla `league_list` icon/text on the client; a bespoke
  "Points" tab icon would require client-side interface edits (post-MVP).
- `characterId` is unset in tests until a character is registered; the companion row safely
  reports `0` via a guarded read.
