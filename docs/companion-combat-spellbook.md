# Companion Combat Positioning & Spellbook System

## Overview

Companions are virtual `Player` bots. Their combat uses the **same interaction, pathing and
spell pipelines as real players** — there is no parallel companion combat engine. The previous
implementation called `target.queueHit(...)` directly inside `CompanionRuntimeScript.sync`, which
let companions damage NPCs from unlimited distance. That call path has been removed.

## Movement & attack range rules

- Engagement is issued through `NpcInteractions.interact(bot, npc, npc.attackOp())` for weapon
  attacks, or `NpcTInteractions.interact(bot, npc, spell.component, -1, null)` for spells —
  the identical entry points a real player uses.
- `PlayerInteractionProcessor` then owns the bot's approach: it routes to a legal attack tile,
  respects target size, collision, clipping and line of sight, and waits (keeps routing) when no
  legal tile exists. It never teleports the companion onto the target.
- **Melee**: `apRange(-1)` — the companion must be adjacent (touch range, bounds-aware) before the
  attack op fires.
- **Ranged**: `apRange(attackRange)` — the weapon's `attackrange` param, capped at 10.
- **Magic (spell-on-target)**: `InteractionNpcT` uses `DEFAULT_AP_RANGE = 10` — the companion
  stops at any legal tile within 10 tiles, exactly like a player casting on an NPC.
- While out of combat the companion follows the owner with a 12-tile leash (telejump catch-up is
  a follower feature, never used to reach combat position).

## Combat styles (capability gate)

`CompanionAttackStyle` (`MELEE` | `RANGED` | `MAGIC`) is an explicit capability stored per
companion. Only `MAGIC` companions may autocast — arbitrary companions cannot cast accidentally.
The starter-picker assigns it from the chosen kit (Mage → MAGIC, Range → RANGED, Melee → MELEE).

## Spellbook & autocast

- Per-companion `CompanionSpellbook` (`STANDARD` | `ANCIENTS`) — persisted, server-authoritative,
  and **completely separate from the owner's personal spellbook varbit**.
- `autocastSpellId` stores the cache-backed autocast registry id; `0` = no autocast.
- Selection UI: right-click the companion → **Companion** → *Spellbook (Autocast & Combat Style)*
  (also `::petspellbook` / `::petmagic`). The menu lists the real registered autocast spells of
  the companion's active book, sorted by level; locked spells display `(requires Lv N)` in red.
- Switching spellbooks clears the stored autocast if the spell is not a member of the new book —
  a Standard spell can never remain selected on Ancients and vice versa.

## Server-side validation (every combat cycle, and on selection)

1. `CompanionService.setAutocastSpell` re-checks: spell belongs to the companion's active book,
   `companion.level >= spell.levelReq` (exact-level unlock works), and `autocastId > 0`.
2. `resolveCompanionAutocast` additionally requires `attackStyle == MAGIC`, re-verifies book and
   level, then calls `MagicRuneManager.canCastSpell(bot, spell, book)`.
3. Failing rune checks silently fall back to the standard weapon-attack interaction (with a
   rate-limited warning message to the owner) — never a free cast.

## Resource convention

`bot.inv` aliases the owner's `companion_storage` inventory (the documented Beast-of-Burden
pack). `MagicRuneManager` validates and consumes real stored runes; the worn staff/equipment
provides the same substitutes as a real player. No infinite casting exists.

## Bot state mirroring

`CompanionPlayerManager.applyCombatState` runs at spawn and every combat cycle:

- Combat stats (attack/strength/defence/ranged/magic/prayer) = `companion.level` — the owner's
  levels can never substitute.
- `stats.hitpoints` base = gear-derived `maximumHitpoints`; current = `Companion.hitpoints`.
- Engine `spellbook` varbit = companion's own book (owner varbit untouched).
- Bot combat XP is mirrored back into `Companion.experience`; bot hitpoints losses are mirrored
  into `Companion.hitpoints` via `CompanionService.damage` (0 hp → INCAPACITATED → despawn).

## Persistence & migration

`V22__companion_spellbook_autocast.sql` adds `attack_style`, `spellbook`, `autocast_spell_id`
with `NOT NULL DEFAULT` backfill — pre-feature companions load as MELEE / STANDARD / no autocast
with no migration errors and no loss of existing data.

## Death / despawn

Incapacitation sets `active=false` → the sync loop despawns the bot and clears its engagement,
rune-warning and XP-baseline state. Resummon restores the persisted spellbook/autocast via
`applyCombatState`.
