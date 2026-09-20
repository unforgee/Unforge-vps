# Ironman System Audit

Audit of the UNFORGE-239 RSMod codebase prior to implementing the server-authoritative
OSRS-style Ironman system. No parallel account system will be created; the feature integrates
into the existing SQLite + `CharacterDataStage` persistence, `PluginScript`/`EventBus`
scripting, and `ProtectedAccess` dialogue infrastructure.

## 1. Account / character model & persistence

**Schema** — `api/db/src/main/resources/db/migration/V1__init.sql`:
- `accounts(id, login_username, display_name, password_hash, email, members, mod_group→modlevel, twofa_*, known_device, timestamps)`
- `characters(id, account_id, realm_id, world_id, x, z, level, varps(JSON), created_at, last_login, last_logout, muted_until, banned_until, run_energy, xp_rate_in_hundreds)` — `run_energy`/`xp_rate` added by V2/V4.
- `inventories(character_id, inv_type)` + `inventory_objs(inventories_id, slot, obj, count, vars, equipment_instance_id)` — FK cascade.
- Flyway locations: `classpath:db/migration`, `classpath:plugin/**/migration` (`FlywayMigration.kt`). Highest applied version: V18 (`api/db`) + V16 (`server/app`). Next free: **V19**.

**Load path** — `AccountLoaderService` (single `account-reader` thread) →
`CharacterAccountRepository.selectAndCreateMetadataList` (joined `accounts`+`characters` row →
`CharacterAccountData`) → each `CharacterDataStage.Pipeline.append` (inventories, stats,
equipment instances) → `CharacterMetadataList` of `Applier`s, applied to `Player` on the game
thread by `AccountLoadResponseHook.createPlayer`.

**Save path** — `AccountSavingService` (single `account-writer` thread) →
`database.withTransaction { repository.save(...) ; pipelines.forEach { save(...) } }` — all
segments in one transaction. Queued via `AccountManager.save(player, callback)`; callback runs
on the writer thread.

**Important threading note**: `SqliteDatabase` holds ONE `Connection` with `autoCommit=false`.
`database.withTransaction` is also used by `ResponseDbGatewayService` worker threads
(`GameDbManager.request`) — transaction interleaving on a single connection is a pre-existing
project characteristic, not something this feature may worsen. All writes from gameplay code
must therefore go through `AccountManager.save` (writer thread) or `GameDbManager` (gateway),
never direct JDBC on the game thread.

**New accounts**: `AccountLoaderService.createMetadataList` inserts `accounts`+`characters`
rows (column defaults apply) and returns `AccountLoadResponse.Ok.NewAccount`. Pipelines'
`append` is NOT called for new characters — new `Player` defaults apply for anything not in
the joined row.

**Critical finding — partial-save rejection**: `AccountLoadResponseHook.validateAndQueueLogin`
rejects any `LoadAccount` whose `last_logout` is `NULL` with `LoginResponse.InvalidSave`
(lines ~113-120). A new character that disconnects mid game-mode selection (never saved) has
`last_logout = NULL` → reconnect would be rejected and the selector would never reopen.
**Required change**: skip the partial-save rejection when `gameModeSelected == false`
(needs `gameModeSelected` on `CharacterAccountData`).

## 2. Player entity

`engine/game/.../entity/Player.kt` (539 lines). Relevant existing fields: `uuid`,
`observerUUID` (doc explicitly reserves it for group game modes — hidden-obj visibility),
`accountId`, `characterId`, `modLevel: UnpackedModLevelType` (`hasAccessTo` for command
gating), `invMap`, `inv`/`worn`, `dropTrigger`, `queueList`/`weakQueueList`, `isAccessProtected`,
`ui.modals`. **No game mode fields exist** — will be added here.

## 3. Login flow & first-login hook

`AccountLoadResponseHook.handleGameLogin` → `playerRegistry.add` →
`SessionStateEvent.Login` → `SessionStateEvent.EngineLogin` → `LoginScript` sends login
packets → `SessionStateEvent.EngineLoginUi` → `GameframeScript` opens toplevel →
`SessionStateEvent.EngineLoginReady`.

`EngineLoginReady` is the correct hook (gameframe already open). `TutorialIslandBootstrap`
already subscribes to it for the tutorial flow — the mode selector must run first / coexist
(subscribe order is not guaranteed; the selector keeps a modal open which blocks the tutorial
bootstrap's modals anyway — selection flow must complete before tutorial UI proceeds;
documented risk: both handlers fire same cycle, selector wins by opening its modal last /
tutorial bootstrap checks `tutCompleted` varp and only opens CharCreate for unstarted
characters — new accounts get BOTH: selector flow must be given priority; see design doc).

Coroutine/dialogue model: `ProtectedAccess` suspend functions (`menu`, `choice2..5`,
`mesbox`, `countDialog`) pause on `ResumePauseButtonInput`/`ResumePCountDialogInput`.
From a non-suspend event use `ProtectedAccessLauncher.launch(player) { ... }`.
While a modal is open, `isAccessProtected`/`isBusy` suppress queues and halt movement.
`weakQueue` re-fires only when `!isAccessProtected` → usable as a self-rearming watchdog if
the player force-closes the selection modal (protected access lost kills the coroutine).

Queue types are cache-defined (`BaseQueues` refs `find("...")`); custom queue types can't be
added without cache edits → reuse `queues.generic_queue9/10` for the selection watchdog.

## 4. Commands

`onCommand("name", desc, ::fn)` in `PluginScript.startup` (see `AdminCommands.kt`).
`CheatHandlerBuilder.modLevel` gates access server-side via `player.modLevel.hasAccessTo`
(`modlevels.*` refs). `::gamemode` (read-only, any player) and `::setgamemode` (admin,
`modLevel = modlevels.admin`-equivalent type ref) both fit this API.

## 5. Death & respawn

`api/death`: `PlayerDeath.deathSequence` (queue `queues.death` → `onPlayerQueue` → suspend
`ProtectedAccess`). Current logic: capture `deathCoords`; `inWilderness = coords.isWilderness()`;
if wilderness → drop `inv`+`worn` via `objRepo.add(item, deathCoords, 1500, player)`
(`Obj.fromOwner` → `ownerId=receiverId=player.observerUUID`); clear invs; telejump respawn;
reset state. **Dangerous-death signal = the wilderness item-drop branch** (project's existing
item-loss rule). Safe-zone helper `isWildernessSafeZone()` exists but the drop path does not
consult it — keep behaviour consistent: demote iff items are actually dropped
(`inWilderness`), i.e. the project treats those deaths as dangerous. Tutorial Island bounds
and disconnect deaths get explicit exemptions. Hardcore demotion must run inside
`deathSequence` before `telejump`, and the demotion save must complete before respawn.

`NpcDeath`/`NpcDropTables`: NPC drops → `objRepo.add(type, coords, duration, hero)` →
`Obj.fromOwner(killer)` → `ownerId = receiverId = killer.observerUUID` — killer attribution
already persisted on the obj, survives public `reveal()`. 

## 6. Ground objs

`Obj` (`engine/game`): `receiverId` (who sees it), `ownerId` (original owner — doc already
says "important for ironman restrictions"), `nullableOwnerId`, `isOriginalOwner(player)`,
`isVisibleTo(player)` via `observerUUID`. `fromServer` → unowned (world spawns: takeable by
everyone including irons). `fromOwner`/`fromPvn` → owned.

**Gap**: `HeldInteractions.invDropSlot` constructs `Obj(coords, entity, clock, observer)` —
sets `receiverId` but leaves `ownerId = NULL`. After `reveal()`, a player-dropped item is
indistinguishable from a world spawn. **Required change**: set `ownerId = observerUUID` on
player drops so "other players' dropped items" remain identifiable post-reveal.

`ObjTakePlugin.triggerTake` (op3 "Take"): `repo.del(obj)` then inv-add — **no policy check**.
Policy must run BEFORE `repo.del`.

## 7. Bank / inventories / shops

- `Banker.kt` (generic-npcs) — `openBank()` → `ifOpenMainSidePair(bank_main, bank_side)`;
  has existing TODO for ultimate ironman. `BankBooth.kt` opens same pair. Safety net:
  `BankOpenScript.onBankOpen` on `onIfOpen(bank_side)` — inject UIM denial + force-close.
- Shops (`api/shops`): `ShopkeeperScript` binds npc "Trade" ops → `Shops.open` →
  `StandardGpShopOperations.shopBuy/invSell`. Shared-scope shop invs are GLOBAL
  (`Shops.globalInvs`) → items sold by players become buyable by anyone = ironman bypass.
  OSRS-faithful fix: restricted modes may only buy within `inv.initialStock` (player-sold
  overstock is not purchasable). Selling stays allowed (items entering shared stock for
  regulars matches OSRS).
- No Grand Exchange exists (`api/market` = price lists only). `canUseGrandExchange` → deny.
- `PlayerUInteractions` (use-item-on-player) triggers scripts only; no transfer exists, but a
  policy gate will be added to forbid restricted modes (bypass prevention per spec).

## 8. Trade / friends chat / clan chat / quests / achievements

- **No trade system exists** in active source (only client op "Trade with" configured in
  `LoginScript` via `MiscOutput.setPlayerOp(slot=4)`; `PlayerTEvents.Op` has no subscriber →
  no-op). Legacy Kronos tradepost is in archived assets only — not active. Central policy
  `canTradeWith` will be implemented and documented as the mandatory gate for any future
  trade implementation.
- **No friends chat / clan chat** implementation found → nothing to restrict (GIM group comms
  out of scope; documented).
- **No quest/achievement systems** in active modules (`quest_progress` table exists but no
  quest engine) → nothing to gate.

## 9. Module / plugin discovery

- `settings.gradle.kts` auto-includes any `*/build.gradle.kts` under `api/`, `content/`,
  `engine/`, `server/`.
- `server/shared/build.gradle.kts` `api()`s EVERY `api` subproject (except `testing`) and
  every `content` subproject → a new `api/ironman` module lands on the server classpath with
  zero wiring changes.
- `PluginModule`s are classpath-scanned (`PluginModuleLoader`, packages
  `org.rsmod.api`/`org.rsmod.content`); `ExtendedModule`s are `install()`ed statically.
  `CharacterDataStage.Pipeline` impls register via `addSetBinding` in a `PluginModule`.
- `PluginScript`s are classpath-scanned and Guice-instantiated (`PluginScriptLoader`).

## 10. Existing ironman assets

- `tutorial_interfaces.ironman_setup` overlay + `IronmanTutorScript` dialogue exist on
  Tutorial Island — legacy content describes OSRS "switch during tutorial / downgrade later"
  rules which CONTRADICT this spec (permanent first-login choice). Treated as informational
  legacy dialogue; the server-authoritative system takes precedence (documented deviation).
- `Obj.ownerId` docs + `ironman_blocked` hitmark + combat TODO confirm intended design.
- No `GameMode`/`HardcoreStatus` model, no `::gamemode`, no policy service exist.

## 11. Risks

1. `last_logout IS NULL` login rejection must be exempted for unselected accounts (§1).
2. Shared SQLite connection: all feature writes via saver/gateway only (§1).
3. `invDropSlot` ownerId gap → fix or player drops bypass post-reveal (§6).
4. Shared shop stock → cap restricted-mode buys to initial stock (§7).
5. Selection-coroutine death on modal close → weak-queue watchdog re-arms (§3).
6. Ordering vs `TutorialIslandBootstrap` on `EngineLoginReady` (§3).
7. Group `observerUUID`: GIM members share a group-scoped observer id → members can see/take
   each other's drops naturally; must be set during account apply (after `CharacterAccountApplier`).
8. Bank `onIfOpen` guard is required because bank UI can be opened by booth/banker/dialogue
   paths independently (§7).
9. Save ordering: game-mode fields written by `CharacterAccountRepository.save` (joined-row
   columns) — the authoritative per-save write; audit/group writes piggyback the same
   transaction via a `CharacterDataStage.Pipeline`.

## 12. Implementation map

| Change | Location |
|---|---|
| `GameMode`, `HardcoreStatus`, `GroupRank`, `IronmanPolicy` (pure) | `engine/game` `org.rsmod.game.ironman` |
| Player fields (`gameMode`, `gameModeSelected`, timestamps, hardcore, group) | `engine/game` `Player.kt` |
| `V19__game_modes.sql` migration + legacy-account backfill | `api/db` |
| `CharacterAccountData`/Applier/Repository columns + group observerUUID | `api/account` |
| Partial-save exemption for unselected accounts | `api/net` `AccountLoadResponseHook` |
| Drop `ownerId` fix | `api/player` `HeldInteractions` |
| Ground-obj take policy check | `api/obj-plugin` `ObjTakePlugin` |
| Item-on-player gate | `api/player` `PlayerUInteractions` |
| Shop overstock cap for restricted modes | `api/shops` `StandardGpShopOperations` |
| Bank deny (UIM) + onIfOpen safety net | `content/.../Banker.kt`, `BankBooth.kt`, `BankOpenScript` |
| Hardcore demotion inside death sequence | `api/death` `PlayerDeath` + `api/ironman` service |
| Selection UI (menu/dialogue fallback), watchdog, `::gamemode`, `::setgamemode` admin+audit | `api/ironman` scripts |
| Group service (create/invite/join/leave/storage/audit) | `api/ironman` `GroupIronmanService` |
| Tests | `api/ironman/src/test`, `src/integration` |
