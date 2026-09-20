# Companion Evolve 2.0

Server-authoritative companion RPG progression, equipment, affix, set, evolution and
analytics system. Built entirely on the existing companion, equipment-instance and threat
infrastructure — no parallel "v2" services.

## Architecture

```
Companion base data (level/xp/model)
  + progression (5 skill tracks, CompanionSkillService)
  + equipment affixes (EquipmentInstance via EquipmentInstanceRegistry)
  + set bonuses (CompanionSetCatalog, "set:<id>" unique-effect tags)
  + talents (CompanionTalentCatalog)
  + evolution (CompanionEvolutionCatalog, companions.evolution_stage)
  → CompanionStatCalculator  →  CompanionStatSheet (immutable, canonical)
  → CompanionGearCalculator delegates → CompanionCombatStats (legacy shape)
  → combat / AI / abilities (CompanionRuntimeScript, CompanionRoleSpells)
  → combat events → CompanionSkillService XP + CompanionTelemetryService
  → Companion Hub UI + debug commands
```

`CompanionStatCache` (api/companion) memoizes sheets keyed by `(Companion, skillLevels)`.
`Companion` is an immutable data class, so any equipment/talent/level/evolution change
produces a new key and recomputes automatically — no manual invalidation calls.

## Progression

`CompanionSkill` has five tracks: `MELEE`, `RANGED`, `MAGIC`, `SUPPORT`, `TANK`.
Each is an independent 1–99 track (`CompanionSkillXp.MAX_LEVEL`) sharing the
`100 * level` per-level XP curve; stored XP is bounded by `MAX_TRACK_EXPERIENCE`.

XP sources (all validated server-side in `CompanionSkillService`):

| Track   | Source                                                            |
|---------|-------------------------------------------------------------------|
| MELEE/RANGED/MAGIC | `awardDamageDealt` — real damage dealt, gated on COMBAT state |
| SUPPORT | `awardEffectiveHealing`, `awardShielding`, `awardBuff`, `awardCleanse` — effective amounts only, combat-window gated |
| TANK    | `awardDamageTankled`, `awardAggroHeld`, `awardTaunt`, `awardProtection` — hostile damage only, COMBAT gated |

Anti-abuse: no overheal XP (`<= 0` rejected), buff refreshes pay nothing, flat awards
throttled, every event capped, incapacitated/despawned companions earn nothing,
self-inflicted damage never counts.

Style XP flows through `CompanionRuntimeScript.awardCombatExperience`: the companion
bot's mirrored stat-XP deltas are routed to `awardDamageDealt` with the companion's
active `CompanionAttackStyle`.

## Stat pipeline

`CompanionStat` is the canonical registry (OFFENSIVE/DEFENSIVE/SUPPORT/THREAT/ABILITY/
UTILITY groups). `CompanionStatCalculator.calculate(companion, registry, skillLevels)`
produces a `CompanionStatSheet` with:

- `value(stat)` — final capped value
- `breakdown(stat)` — per-source contributions (BASE/LEVEL/PROGRESSION/EQUIPMENT/
  SET/TALENT/EVOLUTION) for UI stat explanations
- `combat` — legacy `CompanionCombatStats` projection used by the existing adapter
- `activeSets` — set activations including locked tiers
- `effectiveCooldownTicks(base)` — the single CDR rule
- `reducedDamage(raw)` — the single DR rule (apply exactly once)

Progression grants (`CompanionStatCalculator.Progression`): +1 power per style level,
+2 healing power per Support level, +1 max hp + 20 threat bps per Tank level.

## Caps

`CompanionStatCaps` owns every multiplicative cap — combat code must not carry literals:

- CDR 40% (`MAX_COOLDOWN_REDUCTION_BPS`)
- Damage reduction 50% (`MAX_DAMAGE_REDUCTION_BPS`)
- Crit chance 50%, lifesteal 25%
- Attack delay clamp 2–10 ticks; ability cooldown minimum 1 tick

## Equipment, affixes, rarity

Companion gear reuses `EquipmentInstance` (rarity/affixes/sockets/unique effects/roll
seed/tier/item level/quality/reforge are all persisted already). Rarity controls
affix count, socket count and unique-effect slots (`EquipmentRarity`). The calculator
maps `EquipmentStat` affixes onto companion stats (Strength→melee power + damage,
Cooldown→CDR bps, DamageReduction/Absorb*→DR bps, HealingPower→healing power,
ThreatGeneration→threat bps, CriticalRate/CriticalDamage, LifeSteal, BossDamage,
AttackSpeedPercent, accuracy/defence families, …).

## Set bonuses

`CompanionSetCatalog` — data-driven `CompanionSetDefinition(id, name, bonuses)` where
each `CompanionSetBonus(pieces, stat, value, description)` activates at a piece count.
Membership rides on the persisted `uniqueEffectIds` list: an instance tagged
`"set:<id>"` belongs to that set. Four sets ship: Infernal Pact (damage), Warden's
Oath (tank), Wildhunt (ranged/crit), Mender's Circle (support). `activations(items)`
returns live and locked tiers for UI rendering.

## Talents & abilities

Existing `CompanionTalentCatalog` magnitudes are preserved 1:1 — the calculator reads
`companion.talents` directly (guardian-vitality/iron-stance/bulwark/immortal-oath →
HP/DR, vicious-strikes/executioner/elemental-edge/annihilation → damage,
rapid-assault → CDR, mending-light/battle-hymn/warding-sigil/life-anchor → healing,
taunting-roar → threat, unyielding → DR). Ability cooldowns go through
`CompanionService.useAbility(..., cooldownReductionBps = sheet.cooldownReductionBps)`.

## Evolution

`CompanionEvolutionCatalog` — three data-driven stages (Awakened 25, Empowered 50,
Ascendant 75). Each grants `statBonusPercent` to damage/healing/max-health plus
one-off talent points and an optional `npcId` morph. `CompanionService.evolve`
validates the level gate server-side and persists `evolution_stage` (V31).

## Persistence

- `V30__companion_skill_xp.sql` — per-track XP store.
- `V31__companion_evolve.sql` — `companions.evolution_stage INTEGER NOT NULL DEFAULT 0`
  (old saves load at stage 0); rebuilds `companion_skill_xp` with the five-skill CHECK
  constraint (SQLite cannot ALTER CHECK); existing rows are preserved verbatim.
- `V32__item_instance_evolution.sql` — item-instance evolution lineage.
- `V33__companion_behaviour.sql` — `companions.behaviour TEXT NOT NULL` holding the
  stable single-column encoding `PRIORITY;followDistance;autoTaunt;autoDefend;
  autoHeal;healBelowPercent;autoBuff;catchUpTeleport`. Rows predating the column
  (and older short encodings) deserialize to safe defaults — a migration never loses
  a companion row.

`CompanionRepository` binds `companion.behaviour.serialize()` on upsert and reads it
back through `CompanionBehaviour.deserialize` on load. `CompanionService.setBehaviour`
is the only mutation path: it replaces the immutable record, re-keys the stat cache
and persists immediately, so Behaviour-tab changes are live on the next tick and
survive logout/relogin.

## Behaviour

`CompanionBehaviour` is the player-configured AI record on `Companion`:

- `targetPriority` — `OWNER_TARGET` (assist), `OWNER_ATTACKER` (protect),
  `NEAREST_HOSTILE` (free) or `LOWEST_HEALTH_ALLY` (heal-centric support focus).
  `CompanionService.tick` honours it in both DEFENSIVE (`defensivePick`) and
  AGGRESSIVE (`aggressivePick`) target selection; PASSIVE always returns null.
- `followDistance` (1–8, default 2) — preferred spacing while following, fed to
  `CompanionFollowMovement.resolveTile` and the catch-up teleport destination search.
- `autoTaunt`/`autoDefend` — TANK automatic taunt shout and defensive ward upkeep
  gates in `CompanionRoleSpells`.
- `autoHeal` + `healBelowPercent` (1–99, default 80) — SUPPORT automatic heal gate
  and the hp threshold allies must be at or below.
- `autoBuff` — SUPPORT automatic buff upkeep gate.
- `catchUpTeleport` — when enabled a companion left beyond `CATCH_UP_DISTANCE`
  telejumps to the owner; when disabled it keeps walking instead (cross-plane
  transitions still teleport — they cannot be walked).

## Combat integration

- **Mitigation**: `CompanionRuntimeScript.syncBotVitals` routes every bot hp loss
  through `CompanionMitigation.resolve` — fixed order style resistance → flat DR →
  block roll — all read from the canonical sheet. The attacker's
  `npc_attack_style` param (`0/1/2` = melee/ranged/magic) selects the resistance
  row; un-attacked hp loss (poison-style) skips resistance but keeps DR+block.
  The prevented amount still pays `awardProtection` TANK xp.
- **Ranged penetration**: `penetrationBps` from the sheet is applied through the
  `CompanionPlayerRegistry` transient damage multipliers consumed by the shared
  player-vs-NPC pipeline (`PlayerAttackManager.companionAdjustedDamage`) — real
  hits, not UI-only numbers.
- **CDR**: `useAbility` is invoked with the sheet's CDR; the central rule in
  `CompanionStatCaps` computes the effective cooldown.
- **Style XP**: mirrored stat-XP deltas → `awardDamageDealt` on the active style.
- **Healing**: `CompanionRoleSpells.applySupportHeal` caps at missing HP (no overheal
  XP), awards `awardEffectiveHealing`, records telemetry.
- **Threat**: taunt awards `awardTaunt`; threat generation bps from Tank progression,
  talents, affixes and sets feed `ThreatService` through the worn-bonus pipeline.
- **Target freshness**: DEFENSIVE/AGGRESSIVE picks are recomputed every tick from
  live context, so owner-target and attacker changes apply without relog.

## Companion Hub

Two server-driven screens sit behind new dashboard buttons (bottom bar shrunk to
seven entries):

- **Inspect** (`companion_inspect`, dashboard `btn_inspect`) — read-only sheet dump
  built by `CompanionInspectPresenter` (`api/companion`, pure view model):
  identity (class/style/mode/gear count + best equipped rarity), evolution stage +
  next-stage requirement/READY state/gain preview, all five skill tracks with
  level + %-to-next, active and LOCKED set tiers, twelve stat lines with
  `{src+value}` per-source breakdowns, ability loadout with READY/remaining-seconds
  cooldowns, allocated talents. Empty/loading states render explicit rows; nothing
  is computed client-side.
- **Behaviour** (`companion_behaviour`, dashboard `btn_behaviour`) — live view of
  the persisted `CompanionBehaviour` plus combat mode and attack style. Buttons:
  PASSIVE/DEFENSIVE/AGGRESSIVE, the four target priorities, MELEE/RANGED/MAGIC
  style, follow-distance steppers, catch-up teleport toggle, role gates
  (taunt/defend/heal/buff) and the heal-below-% steppers. Every click mutates via
  `CompanionService.setBehaviour`/`setCombatMode`/`setAttackStyle` and the view is
  refreshed from the updated server record — the UI can never diverge.

Relog correctness: login despawn-and-rebuild kills stale virtual players before
`companions.load`, logout despawns bots, and every Hub refresh reads the freshly
loaded persisted record — no stale companion state after relog/teleport/respawn.

## Ability presentation

One shared definition layer for player and companion abilities (see
`work/devin/ability-presentation-audit.md` for the full inventory):

- `AbilityVisual` (api/combat/combat-commons) — seq/npcSeq/castSpot/travelSpot/
  projanim/impactSpot/sounds/timing + multi-hit `AbilityVisualImpact` +
  `AbilityVisualOrigin` (ORIGINAL/ADAPTED/CUSTOM). Invariant: travelSpot and
  projanim are always a pair.
- `CompanionAbilityVisuals` — the central library; `forRoleSpell` (tier escalation
  1/20/40/60/80) and `forTalentAbility` (all 18 active abilities).
- `CompanionAbilityPresenter` — the only playback path: cast seq+gfx+`soundArea`
  → `spawnProjectile` → `durations.clientDelay` → impact gfx/sound timed to the
  projectile landing. Bot casts reuse the exact same path as player casts
  (`CompanionRoleSpells`); transmogged bots resolve `npcSeq` or the transmog
  NPC's own `attack_anim` param — a player animation is never forced onto an
  incompatible skeleton.

Debug commands: `::abilitypreview <id>` (safe world preview on the nearest npc),
`::proj <projanim> [spotanim]`, plus the pre-existing `::anim`/`::spot`/`::sound`.

## Analytics

`CompanionTelemetryService` (bound in `CompanionModule`) keeps per-owner encounter
aggregates fed by `CombatTelemetryEvent` records from the runtime/role-spell layer:
damage dealt (per style + ability breakdown), effective healing, overhealing, damage
taken/mitigated, threat, taunts. `::cmeter` prints the live meter.

## Debug commands

- `::cstats` — canonical stat sheet dump with per-source breakdown
- `::csets` — active/locked set tiers
- `::cevolve` — evolve active companion (server-validated)
- `::cmeter` — combat analytics snapshot
- `::cskillxp` — five-track XP/level dump
- `::abilitypreview <abilityId>` — play the configured visual on the nearest npc
- `::proj <projanim> [spotanim]` — fire a projectile 2 tiles north, prints clientDelay

## Tests

- `CompanionBehaviourTest` — serialize roundtrip (incl. legacy short encodings and
  out-of-range reset), constructor range guards, `setBehaviour` persistence into
  `tick`, OWNER_ATTACKER/NEAREST_HOSTILE priority picks, PASSIVE gating,
  deterministic aggressive nearest-hostile ordering.
- `CompanionInspectPresenterTest` — identity lines, per-source stat breakdown tags,
  evolution stage/next/READY/MAX, all five skill tracks with progress, active vs
  LOCKED set rows, ability READY/cooldown-seconds, explicit empty rows.
- `CompanionMitigationTest` — style-scoped resistance, resistance→DR ordering,
  strict block-roll boundary, roll range validation.
- `CompanionStatCalculatorTest` — breakdown sources, set activation/locked tiers,
  evolution contribution, stat-cache invalidation on immutable record change.
- `CompanionFollowMovementTest`, `CompanionRoleSpellsTest`,
  `CompanionAbilityVisualsTest`, `CompanionDropOwnershipTest` — movement tile
  selection, role-spell behaviour gates, ability visual coverage, drop ownership.

## Verification

- `:api:companion:test` — 90 tests green (behaviour/presenter/mitigation included).
- `:content:other:commands:test` — green.
- `spotlessApply` applied on all touched modules.
- `packCache` + `:server:app:installDist` green (component.sym newline defect at
  `companion_equipment:105`/`companion_inspect:0` repaired first).
- Server boot: 212 scripts, Flyway validated and applied up to v34,
  `globalXpRate=150.0`, bound to 43594 with no errors. Note: the dev DB already
  contained an earlier V33 checksum from the pre-`catchUpTeleport` column default;
  its `flyway_schema_history` checksum was repaired in place (equivalent to
  `flyway repair`) — no data touched.

## Limitations & manual GUI smoke

- No companion-specific rarity field exists; Inspect shows the highest equipped
  gear rarity as the derived value.
- The Behaviour tab edits the persisted record only — mid-tick combat state (an
  already-engaged interaction) settles on the following cycle.
- Catch-up teleport requires a resolvable free tile; with every ring occupied the
  companion waits for the next cycle by design.
- Manual GUI smoke (client): pending — verified at definition/server level only.
  Suggested passes: open Hub → Inspect (breakdown/set/evolution lines live),
  Behaviour (toggles persist across relog), `::abilitypreview focused-volley`,
  `::proj magic_spell windstrike_travel`.

- **New affix**: add the `EquipmentStat` (if new) to the enum, map it in
  `CompanionStatCalculator`'s equipment section, and add an
  `EquipmentAffixDefinition` to `EquipmentAffixCatalog` with tier/rarity/slot rules.
- **New set**: add a `CompanionSetDefinition` to `CompanionSetCatalog.definitions` and
  tag item instances with `"set:<id>"` in `uniqueEffectIds`.
- **New evolution**: append a `CompanionEvolutionStage` and raise `MAX_STAGE`; the
  `evolution_stage` column needs no schema change.
- **New companion ability**: add a `CompanionAbilityDefinition` to
  `CompanionAbilityCatalog`, an effect in `CompanionAbilityEffects`, and (optionally)
  a granting talent in `CompanionTalentCatalog`. Cooldowns scale with CDR
  automatically.
- **New stat cap**: change the constant in `CompanionStatCaps`; combat code reads
  caps from there only.
