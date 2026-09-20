# Ironman System — Design

Server-authoritative OSRS-style game mode system. See `ironman-system-audit.md` for the codebase
survey this design was built against and `ironman-testing.md` for how to run the tests.

## Modes

`org.rsmod.game.ironman.GameMode` (engine/game):

| Mode | Trade | Drops/loot of others | Bank | Group storage | Hardcore |
|---|---|---|---|---|---|
| `REGULAR` | yes | yes | yes | no | – |
| `IRONMAN` | no | no | yes | no | – |
| `HARDCORE_IRONMAN` | no | no | yes | no | yes → demotes to `IRONMAN` |
| `ULTIMATE_IRONMAN` | no | no | no | no | – |
| `HARDCORE_ULTIMATE_IRONMAN` | no | no | no | no | yes → demotes to `ULTIMATE_IRONMAN` |
| `GROUP_IRONMAN` | group members only | group-owned objs only | yes | yes | – |

Mode properties: `isIronmanRestricted`, `isHardcore`, `isUltimate`, `isGroup`, `demotedMode`,
`dbName`/`fromDbName` for persistence. The enum also carries display metadata (`displayName`,
`difficulty`, `description`, `restrictions`, `hardcoreConsequence`) used by the selection dialogue.

## Player state

Persistent fields on `Player` (engine/game `Player.kt`), stored on the `characters` row:

- `gameMode: GameMode?` — `null` until first selection.
- `gameModeSelected: Boolean` — gate flag; `false` = account is held in the selection flow.
- `gameModeSelectedAt: LocalDateTime?`
- `hardcoreStatus: HardcoreStatus` (`DISABLED`/`ACTIVE`/`DEMOTED`/`DEAD`)
- `hardcoreDeathCount: Int`
- `groupId`, `groupRank`, `groupJoinedAt`, `groupSettingsVersion`

`Player.observerUUID` doubles as the ownership id for private ground objects. Group Ironman
members share `groupObserverId(groupId)` (a negative id assigned at character load in
`CharacterAccountApplier`), so group-owned drops and death piles are visible to the whole group.

## Persistence

`api/db` migration `V19__game_modes_and_group_ironman.sql`:

- Adds the columns above to `characters`. Column defaults leave **new** accounts unselected;
  the migration backfills all **existing** rows to `REGULAR` + `game_mode_selected = 1`, so legacy
  accounts never see the selection flow.
- `ironman_groups`, `ironman_group_storage`, `ironman_group_audit`, `game_mode_audit`.

`CharacterAccountRepository` loads/saves the columns. `AccountLoadResponseHook` treats a never-saved
character as a valid load when it is mid-selection, so disconnecting before choosing a mode does
not corrupt the account — the selection simply re-opens on the next login.

`IronmanDataPipeline` (`CharacterDataStage.Pipeline`) flushes dirty group storage rows and pending
group-audit rows inside the member's own character-save transaction.

## First-login selection

`GameModeSelectionScript` (api/ironman) runs on `SessionStateEvent.EngineLoginReady`:

1. If `gameModeSelected`, do nothing (legacy accounts skip the flow entirely).
2. Otherwise open a `menu` with the six modes, then one or two `mesbox` info pages, then a
   confirmation menu ("This choice is permanent...").
3. `GameModeService.selectGameMode` validates the enum, applies the mode in memory and queues a
   character save; the coroutine waits for the save future before letting the player continue.
4. A repeating soft timer (`ironman_timers.game_mode_select`, 10 cycles) re-opens the flow if the
   dialogue is escaped (walk-away cancels the coroutine) and re-arms on every login, so a
   disconnect mid-selection resumes the flow next login.
5. `IronmanPolicy` denies every gated action while `gameModeSelected == false`, so a crafted or
   macro client cannot bypass the gate.

Note: `TextAlignment` supports at most two pages (8 wrapped lines) per `mesbox`; the mode info is
therefore split into two short mesboxes.

## Policy

`org.rsmod.game.ironman.IronmanPolicy` — pure functions over server state, every gate returns
`PolicyResult.Allow`/`Deny(message)`:

- `canTradeWith` / `canUseItemOn` — player-to-player transfer (regular only; GIM inside group).
- `canTakeObj` — ground obj ownership via `Obj.ownerId` vs `player.observerUUID` (group id shared).
- `canUseBank` — denied for ultimate modes. Wired into `Banker`, `BankBooth`, `BankOpenScript`.
- `canUseGroupStorage` — group members only.
- `canUseGrandExchange` — denied for restricted modes (no GE exists yet; gate for the future impl).
- `canReceiveSharedReward` — denied for restricted modes.
- `canJoinGroup`, `shopBuyCap` (restricted modes can only buy a shop's initial stock, never
  player-sold overstock).

Drop ownership: `HeldInteractions.invDropSlot` now stamps `ownerId` on player-originated drops;
`ObjTakePlugin` enforces `canTakeObj`.

There is no trade interface or Grand Exchange in this codebase — item transfer between players
happens through ground drops, item-on-player, and shop overstock, all of which are gated.

## Hardcore

`PlayerDeath` calls `GameModeService.demoteHardcore` on dangerous deaths:

- `HARDCORE_IRONMAN` → `IRONMAN`, `HARDCORE_ULTIMATE_IRONMAN` → `ULTIMATE_IRONMAN`.
- Sets `hardcoreStatus = DEMOTED`, increments `hardcoreDeathCount`, writes a `game_mode_audit`
  row (`reason = "hardcore_death"`) and persists.
- Safe deaths (where the death pipeline keeps items) do not demote.

## Group Ironman

`GroupIronmanService` (api/ironman):

- `createGroup`/`invite`/`acceptInvite`/`leaveGroup`/`kickMember` — membership lives on the
  `characters` row; structural changes go through `GameDbManager` on the db thread and write
  `ironman_group_audit` rows. Leaving leader dissolves the group. `MAX_GROUP_SIZE` enforced.
- Shared storage: per-group in-memory `Inventory`, lazy-loaded from `ironman_group_storage`,
  mutations recorded in `pendingAudit` and flushed by `IronmanDataPipeline` inside the member's
  save transaction (also forced via `GameModeService.persist` after each mutation).

## Commands

`IronmanCommandScript`:

- `::gamemode` — read-only status report for everyone.
- `::setgamemode <player> <mode> [reason]` — admin-only (`modlevels.admin`), writes
  `game_mode_audit` (character, actor, old mode, new mode, reason, timestamp) and persists.
- `::groupcreate`, `::groupinvite`, `::groupaccept`, `::groupleave`, `::groupkick`,
  `::gstorage`, `::gdeposit`, `::gwithdraw` — Group Ironman membership/storage; the service
  validates modes and ranks.

## Module wiring

`IronmanModule : PluginModule` binds `GameModeService`, `GroupIronmanService` and registers
`IronmanDataPipeline` into the `CharacterDataStage.Pipeline` set. `server/shared` already exposes
every `api/*` module, and the plugin loader scans `org.rsmod.api.*`, so no extra server wiring is
needed.

## Known limitations / deviations

- No trade window or GE exists in this codebase; the policy gates exist for when they arrive.
- Offline admin mode changes are not supported (`::setgamemode` requires the target online).
- Group storage is exposed through chat commands, not a dedicated interface.
- `gameModeSelectedAt`/`group_joined_at` are stored as `LocalDateTime.toString()` text.
