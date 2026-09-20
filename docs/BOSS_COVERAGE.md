# Boss coverage — Unforge / Unforge revision 239

Audit baseline: 2026-09-19

This is an implementation tracking document, not a claim that cache presence equals a playable
encounter. `COMPLETE` is reserved for a boss that has passed the enter → fight → mechanic → kill →
loot → KC/reset smoke test. The initial audit found no boss that could be marked COMPLETE from the
available evidence alone.

## Runtime architecture found

| Area | Evidence | Audit result |
| --- | --- | --- |
| Unforge boss arena | `areas/unforge/wilderness/SoloBossArena.kt` | Private copied-region loop, NPC spawn, scaling, drop rolls and exit exist; it is a generic arena and not proof of authentic boss mechanics. |
| Wilderness bosses | `WildernessBosses.kt`, `WildernessHotspots.kt`, `WildernessDrops.kt`, `WildernessBossKillCounts.kt` | Runtime manager supports ten roaming registrations, bounded queued damage, persistent KC for nine existing cache varps, and respawn lifecycle; boss-specific mechanics remain incomplete. |
| Slayer | `UnforgeSlayer.kt`, `UnforgeSuperiors.kt` | Task/KC-adjacent kill handling exists; no per-boss encounter completion was verified. |
| Raids | `raids/RaidService.kt`, `RaidMechanics.kt`, `RaidModels.kt` | Theatre/Chambers framework and room progression exist; this audit did not verify every room or final reward in a live run. |
| Drops | `drops/UnforgeDrops.kt`, `WildernessDrops.kt` | Shared `NpcDropTables` is present. Collection-log integration was not found in the boss-specific paths inspected. |
| Cache | `api/cache-enricher/.../npc/npcs.toml` and `.data/cache` | Definitions for many old and modern OSRS encounters are present; definition presence is recorded as NPC_ONLY, not COMPLETE. |

## Coverage matrix

Status meanings: `COMPLETE`, `PARTIAL`, `NPC_ONLY`, `BROKEN`, `MISSING`, `BLOCKED_CACHE`.
`NOT_TESTED` is used in the test column and never promoted to PASS by compilation alone.

| Boss / encounter | NPC IDs / cache key | Region / access | Spawn | Combat / mechanics | Drops / KC / log | Status | Test |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Obor | `hill_giant_boss` | Hill Giant area; world entrance not verified | Definition present | No dedicated handler found | No boss-specific verification | NPC_ONLY | NOT_TESTED |
| Bryophyta | `moss_giant_boss` | Bryophyta area not verified | Definition present | No dedicated handler found | No boss-specific verification | NPC_ONLY | NOT_TESTED |
| Giant Mole | `mole_giant` | Falador mole lair not verified | Cache definition | No dedicated handler found | Source-backed drop registration; no boss-specific verification | NPC_ONLY | NOT_TESTED |
| King Black Dragon | `king_dragon` / 239 | Wilderness lair; roaming manager and arena pool reference it | Pool/roaming registration | Generic presentation mechanic only | Wilderness drop registration exists; KC/log not verified | PARTIAL | NOT_TESTED |
| Chaos Elemental | `chaoselemental` / 2054 | Wilderness hotspot/arena pool | Pool/roaming registration | Generic presentation mechanic only | Wilderness drop registration exists; KC/log not verified | PARTIAL | NOT_TESTED |
| Crazy Archaeologist | cache definition | Wilderness location not verified | Not found | No dedicated handler found | Not verified | NPC_ONLY | NOT_TESTED |
| Deranged Archaeologist | cache definition | Fossil Island location not verified | Not found | No dedicated handler found | Not verified | NPC_ONLY | NOT_TESTED |
| Callisto | 6503 | Wilderness hotspot | Roaming registration | Bounded queued magic hit; authentic roar push/AOE not verified | Drop registration path + persistent `total_callisto_kills` | PARTIAL | NOT_TESTED |
| Artio | cache key/ID | No dedicated registration found | Not found | No handler found | Not verified | NPC_ONLY | NOT_TESTED |
| Venenatis | 6504 | Wilderness hotspot | Roaming registration | Bounded queued magic hit; authentic venom/web behaviour unverified | Drop registration path + persistent `total_venenatis_kills` | PARTIAL | NOT_TESTED |
| Spindel | cache key/ID | No dedicated registration found | Not found | No handler found | Not verified | NPC_ONLY | NOT_TESTED |
| Vet'ion | 6611 | Wilderness hotspot | Roaming registration | Bounded queued magic hit; skeleton phase unverified | Drop registration path + persistent `total_vetion_kills` | PARTIAL | NOT_TESTED |
| Calvar'ion | cache key/ID | No dedicated registration found | Not found | No handler found | Not verified | NPC_ONLY | NOT_TESTED |
| Chaos Fanatic | cache definition | Wilderness location not verified | Not found | No dedicated handler found | Not verified | NPC_ONLY | NOT_TESTED |
| Scorpia | cache definition | Wilderness location not verified | Not found | No dedicated handler found | Source-backed drop registration; KC/E2E not verified | NPC_ONLY | NOT_TESTED |
| Abyssal Sire | 5886 | Arena pool / roaming registration | Roaming registration | Bounded queued magic hit; phases/minions absent | Source-backed drop registration; persistent `total_abyssalsire_kills`; E2E not verified | PARTIAL | NOT_TESTED |
| Cerberus | 5862 | Arena pool / roaming registration | Roaming registration | Bounded queued magic hit; ghosts/lava absent | Source-backed drop registration; persistent `total_cerberus_kills`; E2E not verified | PARTIAL | NOT_TESTED |
| Alchemical Hydra | 8615 | Arena pool / roaming registration | Roaming registration | Bounded queued magic hit; four phases absent | Source-backed drop registration; persistent `total_hydraboss_kills`; E2E not verified | PARTIAL | NOT_TESTED |
| Grotesque Guardians | `gargboss_*` | Slayer Tower location not verified | Cache phases present | No dedicated handler found | Not verified | NPC_ONLY | NOT_TESTED |
| Kraken | `slayer_kraken_boss` | Solo arena pool | Arena spawn | Generic combat; whirlpool mechanics absent | Generic arena roll only | PARTIAL | NOT_TESTED |
| Thermonuclear Smoke Devil | `smoke_devil_boss` | Solo arena pool | Arena spawn | Generic combat; special attack absent | Generic arena roll only | PARTIAL | NOT_TESTED |
| God Wars — Bandos | `godwars_bandos_avatar` | Solo arena pool | Arena spawn | Avatar proxy, not General Graardor + minions | Generic arena roll only | PARTIAL | NOT_TESTED |
| God Wars — Armadyl | `godwars_armadyl_avatar` | Solo arena pool | Arena spawn | Avatar proxy, not Kree'arra + minions | Generic arena roll only | PARTIAL | NOT_TESTED |
| God Wars — Saradomin | `godwars_saradomin_avatar` | Solo arena pool | Arena spawn | Avatar proxy, not Zilyana + minions | Generic arena roll only | PARTIAL | NOT_TESTED |
| God Wars — Zamorak | `godwars_zamorak_avatar` | Solo arena pool | Arena spawn | Avatar proxy, not K'ril + minions | Generic arena roll only | PARTIAL | NOT_TESTED |
| Nex | `nex_prison_*` cache definitions | Prison/room access not verified | Not found | No dedicated encounter path found | Not verified | NPC_ONLY | NOT_TESTED |
| Corporeal Beast | 319 / `corp_beast` | Arena pool / roaming registration | Pool/roaming registration | Bounded queued magic hit; core mechanics absent | Drop path not verified; persistent `total_corp_kills` | PARTIAL | NOT_TESTED |
| Dagannoth Kings | `dagcave_*_boss` | Solo arena pool | Arena spawn | Three proxies available; switching/prayer mechanics absent | Generic arena roll only | PARTIAL | NOT_TESTED |
| Kalphite Queen | `kalphite_queen` | Solo arena pool | Arena spawn | Generic combat; two forms/prayer absent | Generic arena roll only | PARTIAL | NOT_TESTED |
| Zulrah | cache definitions | Zul-Andra spawn data exists | World spawn/reference data | No dedicated handler found | Not verified | NPC_ONLY | NOT_TESTED |
| Vorkath | `vorkath` | Vorkath spawn data exists | World/reference spawn | No dedicated handler found | Source-backed drop registration; kill/E2E not verified | NPC_ONLY | NOT_TESTED |
| Phantom Muspah | cache definitions | Access/instance not found | Not found | No dedicated handler found | Not verified | NPC_ONLY | NOT_TESTED |
| Sarachnis | cache definition | Access not verified | Not found | No dedicated handler found | Not verified | NPC_ONLY | NOT_TESTED |
| Skotizo | cache definition | Access not verified | Not found | No dedicated handler found | Not verified | NPC_ONLY | NOT_TESTED |
| Hespori | cache definition | Farming patch/encounter not verified | Not found | No dedicated handler found | Not verified | NPC_ONLY | NOT_TESTED |
| Nightmare / Phosani | cache definitions | No dedicated instance found | Not found | No dedicated handler found | Not verified | NPC_ONLY | NOT_TESTED |
| Tempoross | `tempoross_boss_*` | Minigame architecture not verified | Cache definitions | No dedicated handler found | Not verified | NPC_ONLY | NOT_TESTED |
| DT2 bosses | Duke/Leviathan/Whisperer/Vardorvis cache search required | Quest/repeat access not found | Not found | No dedicated handler found | Not verified | BLOCKED_CACHE | NOT_TESTED |
| Theatre of Blood | Maiden/Bloat/Nylocas/Sotetseg/Xarpus/Verzik | Raid framework and command entry exist | Raid room definitions exist | Mechanics framework exists; live full-run not verified | Reward flow exists in service; log/KC not verified | PARTIAL | NOT_TESTED |
| Chambers of Xeric | room registry | Raid framework and command entry exist | Raid room definitions exist | Not proven to contain authentic full room set/Olm flow | Reward flow exists in service; log/KC not verified | PARTIAL | NOT_TESTED |
| Tombs of Amascut | `toa_*` cache definitions | No raid kind/runner found | Cache definitions only | No progression implementation found | Not verified | NPC_ONLY | NOT_TESTED |
| Corrupted Nechryarch (custom) | `cw_corrupted_nechryarch` / 16296 | Solo Boss Arena | Arena pool registration added | Uses custom cache combat definition; dedicated mechanics not verified | Dawnsteel starter-set drop table added; E2E not verified | PARTIAL | NOT_TESTED |
| Brutal Lava Dragon (custom) | `cw_brutal_lava_dragon` / 16299 | Solo Boss Arena | Arena pool registration added | Grounded custom form; flying phase not wired into arena | Dawnsteel starter-set drop table added; E2E not verified | PARTIAL | NOT_TESTED |

## Initial conclusions

1. The most dependency-appropriate first implementation batch is the existing Wilderness manager:
   replace presentation-only mechanics with a cancellable encounter state and real damage/projectile
   actions, then add kill/reward/KC assertions around it.
2. The second batch is standalone Slayer bosses already referenced by the arena pool (Kraken,
   Smoke Devil, Cerberus, Hydra), because their cache keys and common drop infrastructure are
   already available.
3. Raids remain PARTIAL until a live full progression smoke test proves every room, wipe, reward,
   and cleanup path.

## Test policy

Compilation is a build check only. Until an interactive server/client smoke test is recorded,
every row remains `NOT_TESTED`; no row is promoted to COMPLETE from static inspection.
