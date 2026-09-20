# Ironman System — Testing

## Automated tests

All in `api/ironman`:

### `src/test` — unit / persistence (fast, no game server)

```powershell
.\gradlew.bat :api:ironman:test --console=plain
```

- `IronmanPolicyTest` — 9 tests: pending-selection denial, trade matrix (regular / ironman /
  group-internal / group-external), item-on-player, owned-obj take rules incl. group observer ids,
  UIM bank denial, group storage gating, GE denial, shared rewards, shop overstock cap.
- `GameModePersistenceTest` — 5 tests against a real migrated SQLite database (Flyway runs the
  real `V1`…`V19` migrations):
  - mode + hardcore + group fields round-trip through `CharacterAccountRepository`;
  - `game_mode_selected = 0` rows load as unselected;
  - legacy rows are backfilled to `REGULAR`+selected by V19;
  - group rows/audit persist.
  - Note: the fixture closes each `SqliteDatabase` in `@AfterEach` — on Windows the WAL sidecar
    files stay locked until the last connection closes, which otherwise breaks `@TempDir` cleanup.

### `src/integration` — `GameModeSelectionTest` (full `GameTestState` server)

```powershell
.\gradlew.bat :api:ironman:integration --console=plain
```

- `every game mode can be selected` — drives the real menu → mesbox → confirm-menu coroutine for
  all six `GameMode` entries and asserts `gameMode`/`gameModeSelected`/`hardcoreStatus`.
- `new account cannot reach gated gameplay before selecting` — menu opens and
  `IronmanPolicy.canUseBank` denies while unselected.
- `invalid menu subcomponent does not select a mode` — crafted out-of-range input is ignored.
- `cancelling the flow reopens selection via watchdog` — `cancelActiveCoroutine()` simulates
  escaping the dialogue; the soft-timer watchdog re-opens the menu within ~10 cycles.
- `legacy selected account skips the selection flow` — a `REGULAR`+selected account gets no modal.

Caveats discovered while writing these tests:

- The test `GameServer` binds the real game port (43594). If a dev server or a leftover test
  worker is running, the suite fails with `BindException` — stop the other process first
  (`Get-NetTCPConnection -LocalPort 43594 -State Listen`).
- `Player.resumeActiveCoroutine` resumes only `DeferredResumeCondition` suspensions and only when
  the value type matches; pause-button dialogs need `ResumePauseButtonInput` with the component
  the dialog expects (`menu` accepts any component, `mesbox` requires `messagebox_pbutton`).
- `TextAlignment` throws if a `mesbox` wraps past 8 lines — keep mode info split across two
  short mesboxes.
- `menu()`/dialog resumes inside `ProtectedAccess` validate `player.isDelayed` and the still-open
  modal; clicking through dialogs requires `advance(1)` between resumes in tests.

## Manual verification

1. `.\gradlew.bat :server:app:installDist` (or the existing install under
   `server/app/build/install/app`), start `GameServerKt` — it binds 43594 and loads
   `org.rsmod.api.ironman.IronmanModule` via the plugin scan.
2. Connect a fresh account: the "Choose your game mode" menu must appear before gameplay;
   `::gamemode` reports the choice after confirming.
3. Disconnect mid-selection and reconnect — the menu re-appears.
4. `::setgamemode <name> <MODE> reason` as admin → check `game_mode_audit` in
   `data/game.db`; normal players get no such command.
5. Group Ironman: `::groupcreate`, `::groupinvite`, `::groupaccept`, `::gdeposit`,
   `::gwithdraw`, `::gstorage`; rows land in `ironman_groups`, `ironman_group_storage`,
   `ironman_group_audit`.
6. Hardcore: die dangerously → mode demotes (`hardcore_death` audit row); die in a safe area →
   no demotion.
