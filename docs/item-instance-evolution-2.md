# Item Instance Evolution 2.0

Server-authoritative lifecycle, identity and audit layer for `EquipmentInstance`.
This document describes the implemented slices of the Evolution 2.0
specification (canonical snapshot, uuid, revision, fingerprint, event history,
mutation gateway, optimistic locking, idempotency, invariant checker, protocol
v4 envelope, admin diagnostics, event-history restore and corruption handling).

## Goals of this slice

Every important item now has:

- a globally unique identity (`instance_uuid`) alongside the numeric primary key
- a lifecycle state (`EquipmentInstanceState`) and binding (`ItemBinding`)
- an optimistic-lock `revision` that bumps on every mutation
- a canonical SHA-256 `fingerprint` of all gameplay-relevant state
- a data-driven evolution track (`ItemEvolutionCatalog`) with stages and branches
- lineage metadata (creation source, parents, recipe, drop)
- an append-only, hash-chained `equipment_instance_events` audit log
- one mutation gateway - no raw field writes outside it
- structured `ItemMutationResult` errors instead of booleans

## Domain model (`api/equipment-instance`)

| Type | Purpose |
|---|---|
| `ItemInstanceId` / `ItemRevision` / `ItemTemplateId` | Strong id wrappers (value classes) for new APIs |
| `ItemBinding` | `UNBOUND`, `ACCOUNT_BOUND`, `CHARACTER_BOUND`, `TRADE_LOCKED`, `QUEST_BOUND`, `TEMPORARILY_BOUND`, `DESTROYED` |
| `EquipmentInstanceState` | `ACTIVE`, `BROKEN`, `REPAIRING`, `IN_UPGRADE`, `IN_TRADE`, `RETIRED`, `DESTROYED`, `CORRUPTED` |
| `ItemMutationOperation` | Every auditable mutation (`CREATED`, `MIGRATED`, `REFORGED`, `EVOLVED`, `TRADED`, `DESTROYED`, `RESTORED`, `ADMIN_CORRECTION`, ...) |
| `ItemMutationEvent` | One audit row: revisions, fingerprints, actor, source, payload, `previousEventHash` + `eventHash`, post-mutation `snapshot` |
| `ItemEventHasher` | Tamper-evident chain: `SHA-256(previousHash | canonical event fields)` |
| `ItemMutationResult` | `Success` / `RevisionConflict(current)` / `ValidationError(code)` / `CostError` / `InternalFailure(requestId)` |
| `ItemMutationRequest` | `idempotencyKey`, `expectedRevision`, actor, operation, source, payload |

`EquipmentInstance` itself gained the persisted columns as defaulted fields, so
every pre-Evolution constructor call site and legacy row stays valid:
`instanceUuid`, `state`, `binding`, `ownerAccountId`, `ownerCharacterId`,
`masteryLevel`, `experience`, `evolutionStage`, `evolutionBranch`, `revision`,
`fingerprint`, `lineageParentIds`, `lineageRecipeId`, `lineageDropId`,
`createdAtEpochMillis`.

## Fingerprint

`EquipmentInstanceFingerprint.of(instance)` = SHA-256 over a field-delimited
canonical serialization of all mutable state (template, category, rarity, tier,
state, binding, owners, seed, versions, levels, xp, quality, evolution, source,
lineage, sorted affixes/sockets/skill-affixes, uniques, locks, reforge data).

Excluded by design: `instanceId`, `instanceUuid`, `createdAtEpochMillis`
(identity, not state), `fingerprint` itself and `revision` (the lock counter,
enforced by the conditional UPDATE instead). Two snapshots with identical state
but different identities fingerprint identically.

## Evolution catalog

`ItemEvolutionCatalog` is data-driven: stages 1..5 with level gates
(10/25/50/75/100), stage 3 opens a branch choice, three branches (`reaver`,
`bulwark`, `channeler`) with category and incompatibility rules.
`canEvolve` / `branchError` are the server-side gates; nothing is hardcoded in
combat code.

## Invariant checker

`EquipmentInstanceInvariants.violations(instance)` is report-only: uuid shape,
non-negative counters, quality range, catalog-known evolution stage/branch,
rarity affix/socket/unique counts, affix family uniqueness, locked-slot range,
binding↔owner consistency, destroyed-state/binding symmetry, fingerprint
verification and lineage sanity.

`eventChainViolations(events)` validates the audit chain: per-event hash,
contiguous revisions, fingerprint links and `previousEventHash` links.

## Persistence (V32)

`V32__item_instance_evolution.sql` adds:

- `equipment_instances`: `instance_uuid` (backfilled, unique), `state`,
  `binding`, `owner_account_id`, `owner_character_id`, `mastery_level`,
  `experience`, `evolution_stage`, `evolution_branch`, `revision`,
  `fingerprint`, `lineage_parent_ids`, `lineage_recipe_id`, `lineage_drop_id`,
  `created_at`, `updated_at`
- `equipment_instance_evolution` (stage/branch/milestones mirror)
- `equipment_instance_events` (append-only audit log + `idempotency_key`)
- indexes for uuid, owners, state, binding, fingerprint, event instance/revision

`V34__item_instance_event_snapshots.sql` adds `equipment_instance_events.snapshot
TEXT` — the canonical post-mutation state (the same serialization the
fingerprint hashes). Events written before V34 carry `NULL` and are deliberately
not restorable; snapshot integrity is proven at restore time by
`fingerprint(decoded) == afterFingerprint`, so the column stays out of
`ItemEventHasher` input and old chains remain valid.

`EquipmentInstanceRepository` exposes: `create`, `upsert`, `update`,
`saveWithRevision` (conditional `WHERE revision = ?`), `load`, `loadByUuid`,
`loadOwnedByCharacter`, `validateFingerprint`, `appendEvent` (with optional
idempotency key), `latestEvent`, `findEventByIdempotencyKey`, `loadHistory`
(cursor-paginated).

## Mutation gateway

`EquipmentInstanceMutationService.mutate(current, request, transform, onResult)`:

1. in-memory idempotency replay (`instanceId:idempotencyKey` → stored result;
   same key + different operation → `idempotency-key-reuse`)
2. `expectedRevision` optimistic-lock check → `RevisionConflict`
3. lifecycle-state gate (only `ACTIVE`, bypassed by `CREATED`/`MIGRATED`/
   `RESTORED`/`REPAIRED`/`ADMIN_CORRECTION`) → `invalid-state`
4. `transform` produces the candidate; identity fields (id, uuid, template,
   seed) are sealed → `identity-*`
5. revision bumps, fingerprint recomputed, invariants enforced →
   `invariant-violation`
6. one db-gateway request persists snapshot (`WHERE revision = ?` backstop)
   + event atomically inside `SqliteDatabase.withTransaction`
7. registry update + structured result on the game thread

`EquipmentInstanceService` routes creation (`CREATED` event) and legacy
`updateInstance` calls through the gateway.

## Restore and corruption

`EquipmentInstanceService.restoreFromEvent(instanceId, eventId, actor,
expectedRevision)` → `EquipmentInstanceMutationService.restoreToEvent`:

1. the request must carry `operation = RESTORED` → `operation-mismatch`
2. idempotent replay (`restore:<instanceId>:<eventId>`) and `expectedRevision`
   are checked before any database work
3. the instance's full event chain is loaded and validated by
   `eventChainViolations` — any broken hash, revision or fingerprint link fails
   closed → `event-chain-broken` (nothing is written to a corrupt chain)
4. the target event must exist (`event-not-found`), carry a snapshot
   (`no-snapshot`), decode (`snapshot-decode-failed`) and re-fingerprint to the
   event's `afterFingerprint` (`snapshot-fingerprint-mismatch`)
5. the decoded state is routed back through `mutate`: identity fields
   (`instanceId`, `instanceUuid`, `createdAtEpochMillis`; template/seed via the
   identity seal) are taken from the live row, the revision bumps, a new
   `RESTORED` audit event is appended and snapshot+event persist atomically

`EquipmentInstanceService.corrupt` flips an `ACTIVE` item to `CORRUPTED`
(`CORRUPTED` event); the state gate then blocks every non-bypass mutation.
`repair` returns `BROKEN`/`REPAIRING`/`CORRUPTED` items to `ACTIVE` (`REPAIRED`
event; `repairable-state-required` otherwise). `DESTROYED` items can only be
recovered via `restoreFromEvent` — `RESTORED` bypasses the state gate, so a
corrupted or destroyed item can be brought back to its recorded state.

## Load pipeline

`EquipmentInstanceLoadPipeline` stamps a baseline fingerprint on first load of
a legacy instance and appends the `MIGRATED`/`v32-baseline` event when the
instance has no audit history yet (idempotent; covers the crash-between-update-
and-event edge). The baseline event carries the snapshot, making the migration
point a valid restore target. Loaded instances are registered in
`EquipmentInstanceRegistry`.

## Client protocol v4

Alongside (never instead of) the existing v3 payload, instanced slots emit:

```text
UNFORGE_ITEM_INSTANCE|4|upsert|<instanceId>|<revision>|<uuid>|<state>|<binding>|<fp8>
```

`fp8` is an 8-hex-char fingerprint prefix for cheap staleness checks. Unknown
fields degrade to `-`, so legacy instances and older clients are unaffected.

## Admin commands (`content/other/commands`)

- `::iteminspect <id|slot>` — canonical snapshot (ids, state, binding, owners,
  levels, xp, evolution, lineage)
- `::itemhistory <id|slot>` — last 15 audit events + chain-integrity flag
- `::itemvalidate <id|slot>` — full invariant report
- `::itemfingerprint <id|slot>` — stored vs recomputed fingerprint

## Tests

- `EquipmentInstanceFingerprintTest` — determinism, identity-field exclusion,
  per-field sensitivity, order-insensitive collections, verify()
- `EquipmentInstanceInvariantsTest` — valid/invalid snapshots, tampered
  fingerprints, lifecycle/binding divergence, event-chain violations
- `EquipmentInstanceMutationServiceTest` — `prepare()` contract: revision
  conflicts, state gating, identity sealing, transform errors, invariant
  rejection
- `EquipmentInstanceMutationGatewayTest` — real Flyway SQLite +
  `ResponseDbGatewayService`: atomic snapshot+event, idempotent replay,
  key-reuse rejection, stale-revision conflict, `WHERE revision = ?` race
  backstop
- `EquipmentInstanceRepositoryTest` — create/load round-trip of every V32
  column, `loadByUuid`, `loadOwnedByCharacter`, `saveWithRevision`,
  event append/history/pagination/idempotency-key, fingerprint tamper
  detection, unstamped legacy row load
- `EquipmentInstanceSnapshotCodecTest` — canonical round-trip of every mutable
  field, placeholder identity, fail-closed malformed payloads
- `EquipmentInstanceRestoreTest` — real Flyway SQLite + gateway loop: snapshot
  restore with sealed identity + `RESTORED` event, idempotent replay,
  stale-revision conflict, chain tamper rejection (hash, revision gap,
  fingerprint link), snapshot rejection (missing/undecodable/fingerprint
  mismatch), corrupt→block→repair flow, restore of a corrupted item
- `EquipmentInstanceHoverSyncTest` — v4 envelope fields and legacy degradation

## Known scope limits (later slices)

- `TRADE_LOCKED`/`IN_TRADE`/`IN_UPGRADE` states exist in the model and gate
  mutations; the actual trade-lock session management is a later phase.
- Item XP sources/gates, mastery usage and material costs are modeled
  (`experience`, `masteryLevel`, `ItemEvolutionCatalog`) but the gameplay
  producers that award them are not wired yet.
- Affix extraction/transfer, gem-instance progression, material costs and the
  gameplay producers that trigger `corrupt`/`repair` remain unimplemented by
  design.
- Snapshots are not folded into `ItemEventHasher` input (keeps pre-V34 chains
  valid); their integrity relies on the fingerprint check instead. If
  cryptographic binding is ever required, a new hasher domain + migration is
  needed.
- The idempotency map is in-memory (request-scope); the persisted
  `idempotency_key` column makes restarts detectable via
  `findEventByIdempotencyKey` for callers that opt in.
