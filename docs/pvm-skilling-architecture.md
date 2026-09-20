# PvM + Skilling Architecture — Design

Server-side design for a system where **every non-combat skill feeds PvM, but no skill is a hard
gate to starting PvM**. Written against the current codebase; nothing in this document has been
implemented yet.

Core loop being designed:

```
skilling ──► Skill Points ──► Skill Shop ──► consumables / upgrades / recipes
    │                                              │
    └────────── materials ──► crafting chains ─────┤
                                                   ▼
                                              PvM readiness
                                                   │
    perks ◄── Perk Points ◄── boss kills ◄─────────┘
    (existing PerkService)
```

Skilling funds **preparation** (consumables, gear upgrades, utility). PvM funds **permanent power**
(perk points → perks, already implemented). The two currencies are deliberately non-convertible so
neither path can be maxed in isolation.

---

## 1. Current-state observations

A surprising amount of the requested system already exists. The design below mostly **wires skills
into existing PvM infrastructure** rather than building new frameworks.

### 1.1 Permanent perks — already implemented

`api/player/src/main/kotlin/org/rsmod/api/player/perk/` (`PerkService`, `Perk`, `PerkVarps`,
`PerkLevelBoosts`) with a journal-tab UI (`content/interfaces/journal-tab/.../PerkJournalScript.kt`).

- 46 perks across four trees: **Core** (Power, Vitality, Greed, Guardian, Fortune, Swiftness),
  **PvM Offence** (Slayer, Berserker, Deadeye, Sorcerer, Executioner, Punisher, Dominion, Slaughter,
  Rend, Bloodlust, Leech), **PvM Defence** (Ironhide, Spellward, Dodging, Thorns, Laststand),
  **PvM Sustain** (Adrenaline, Regeneration, Harvest, Rejuvenation, Scavenger), plus **Skilling** XP
  perks (one per skill + Scholar) and **yield** perks (Instinct, Lumberjack, Motherlode, Trawler,
  Gourmet, Nimble, RunicMastery).
- Perk Points come from **boss kills** (`NpcDeath.onBossKill`: `1 + visLevel/100` points, ×Fortune).
  Training a perk converts 1 point → 1 perk XP; XP curve is triangular (`level*(level+1)/2`, max 10).
- Effects are read as **basis points** and applied only on NPC-originated/NPC-targeted hit paths —
  PvP is unaffected by construction.

**Implication:** the "permanent perk system" the design calls for already exists. Do not build a
second one; add perks to the existing `Perk` enum where gaps appear.

### 1.2 PvM buff/shield/cooldown layer — already implemented

`api/player/support/SupportCombatState.kt` + `content/pvm-support/`:

- Server-authoritative timed buffs: `damageBps`, `accuracyBps`, `incomingReductionBps`,
  `shieldPoints` — all **additive**, all **PvM-only**, all expiring on `currentMapClock`.
- 20 support spells (`SupportSpell`) with cooldown categories + floors, unlock flags
  (`SupportSpellUnlocks`), a scroll shop (`SupportScrollShop`), and a HUD overlay.
- `SupportNpcRules` lets a boss type scale how much support-buff reduction applies to it — bosses can
  be made partially buff-resistant without touching formulas.

**Implication:** consumable buffs (food, potions, campfires) should write into this same additive
bps/shield model — same semantics (replace-not-stack, expiry, PvM-only) — instead of a parallel buff
system.

### 1.3 Hit processors — integration points exist

- `StandardNpcHitProcessor` (player→npc): folds `perks.damageBps(...)` +
  `SupportCombatState.outgoingDamageBps/outgoingAccuracyBps` into the outgoing hit.
- `StandardPlayerHitProcessor` (npc→player): folds `perks.incomingReductionBps` +
  `styleReductionBps(hit.type)` + support reduction + Laststand into `reductionBps`, then an
  absorb-shield, then Thorns reflection.
- `Hit` carries `HitType` (Melee/Ranged/Magic/other) and `isFromNpc`. There is **no elemental
  damage-type axis today** — that is the one genuinely new combat axis this design adds.

### 1.4 Equipment instances — already implemented

`api/equipment-instance/` persists per-item `EquipmentInstance`s (rarity, affixes, sockets, unique
effects, **skill affixes** `SkillAffixRoll`, tier, itemLevel, quality, reforge) in SQLite.
`WornSkillBonuses` aggregates worn skill affixes; `SkillingRewards`
(`api/plugin-commons/skilling/SkillingRewards.kt`) consumes them for noted/bank/double/extra drops
and per-skill bonuses. `EquipmentInstanceService.rollAndPersist` is already called from boss drops
(`SoloBossArena` bonus rolls) and Smithing products.

**Implication:** "Smithing upgrades melee armor" should mean *crafting/rolling an upgraded
EquipmentInstance* (higher tier/quality or an added affix slot), not a new stat pipeline.

### 1.5 Production-skill loop — already implemented

`content/skills/skilling-core/` `MakeRecipe`/`MakeRequest`/`MakeJob`/`SkillingQueues.make` +
`SkillMakeScript`: shared weak-queue make loop for all production skills (Smithing has its own queue
only because it hands products to the instance roll). All skill modules exist: agility, construction,
cooking, crafting, farming, firemaking, fishing, fletching, herblore, hunter, magic, mining, prayer,
runecrafting, smithing, thieving, woodcutting.

**Implication:** new PvM consumables/upgrade kits are just `MakeRecipe` rows + an `onMade` hook —
no new crafting framework.

### 1.6 Shops & currencies

`api/shops/` (`Shops`, `ShopkeeperScript`, `ShopOperations`): inventory-backed shops bound to an NPC
via `npc_shop` param; prices via `ShopCostCalculations`. Currency is `CurrencyType` — a **cache type**
with only `standard_gp` defined (`currency.sym`). A second cache currency needs cache work; a
varp-based currency (like `perk_points`) needs none.

### 1.7 PvM content already live

- `SoloBossArena` (Nightmare Zone): instanced region, round-scaling boss pool (13 bosses), bonus drop
  rolls, perk-point payouts — already calls `perks.addPoints` and rolls equipment instances.
- `UnforgeSlayer`: full Kronos-ported Slayer — tasks, points, unlocks, blocks, boss tasks.
- Wilderness bosses/revenants/hotspots, `UnforgeDrops`, donator system, Dev Hub admin tooling.
- `NpcDeath`/`NpcKilledEvent`/`NpcDropTables` — the kill-reward hook everything keys off.

### 1.8 What's genuinely missing

| Gap | Consequence for this design |
|---|---|
| No elemental axis on hits/npcs | Boss "fire/water/physical" identity needs a new npc param + element-tagged hits |
| No skill-point currency | New varp (MVP) or cache currency (later) |
| No consumable→buff pipeline | Food/potion effects need a small "apply on consume" layer writing into `SupportCombatState`-style state |
| No campfire/station deployables | Firemaking/Construction roles need a "place buff loc" action (farming/hunter already place world entities) |
| No gear-upgrade recipes | "Upgrade kit" items + a make-action that re-rolls/extends an EquipmentInstance |

---

## 2. Design principles

1. **Combat stats gate PvM; skills accelerate it.** A player with combat levels and shop-bought gear
   can enter every encounter. Skilling shortens kills, widens margins, and unlocks convenience —
   it never flips "can't enter" to "can enter".
2. **Two currencies, two sinks.** Skill Points (skilling→shop) buy preparation; Perk Points
   (PvM→perks) buy permanent power. No direct conversion.
3. **Additive, capped, expiring.** All new combat effects fold into the existing bps pools
   (`outgoingDamageBps`, `incomingReductionBps`, `shieldPoints`) with hard caps — nothing new is
   multiplicative.
4. **Resistances from multiple sources.** Any elemental mitigation cap must be reachable via ≥3 of:
   food, potion, campfire, gear, perk, support spell — so no single skill is "the resistance skill".
5. **Reuse before build.** PerkService, SupportCombatState, MakeLoop, Shops, EquipmentInstanceService,
   NpcKilledEvent — the design adds data to these systems, not parallel systems.

---

## 3. Skill → PvM role map

Each skill gets **one primary contribution** (what it uniquely produces) and one **secondary**
(how its level passively helps). "Produces" means craftable consumable/upgrade; "passive" means a
small level-scaled benefit.

| Skill | Primary (produces) | Secondary (passive/utility) | Purchased alternative |
|---|---|---|---|
| **Fishing** | Raw mats for sustain food + "elemental catch" mats (water-aspect fish → resist food) | Higher tier = better food input | Cooked food sold for GP; skill shop food bundles |
| **Cooking** | PvM food: heal + minor timed buff (see §6); feasts (group food) | Higher Cooking = less burn, better feast tiers | Shop food (heals only, no buff) |
| **Firemaking** | **Campfire** deployable: timed area buff (fire resist; small regen; +fire damage vs fire-weak) | Fire resist scales with FM level when standing at own campfire | Resist potions (Herblore), resist gear |
| **Smithing** | Melee **armor upgrade kits** (roll instance to next tier / +quality), **shield** items, repair kits | — | Drops, shop gear, arena loot |
| **Mining** | Ores/bars for Smithing kits; gems → Crafting | — | Ores buyable (limited shop stock) / drops |
| **Fletching** | Ranged armor upgrade kits; **special ammunition** (elemental-tipped bolts/arrows) | — | Standard ammo from shops |
| **Woodcutting** | Logs → ammo shafts, bows; campfire fuel | — | Logs buyable / drops |
| **Runecrafting** | Magic armor upgrade kits; **rune effiency** consumable (chance to not consume runes); spell-power runes | Passive rune-save scales with RC level | Standard runes from shops |
| **Herblore** | Potions: combat boost, elemental resist, antipoison/antifire, restore | — | Basic potions in shops (weaker tiers) |
| **Crafting** | Accessories (rings/amulets) with PvM stats; gem cutting for jewelry | — | Drop accessories, donator items |
| **Agility** | — | Stamina: longer run, faster boss-room shortcuts, small flat dodge vs physical (cap) | — (utility only, never required) |
| **Construction** | **Prep station** deployable (pre-boss buff: small all-round bps + free supplies per day); repair bench | — | Same buffs obtainable via campfire/potions |
| **Farming** | Herbs → Herblore; vegetables → Cooking; **grown** special mats | — | Herbs buyable / drop |
| **Slayer** | — | Task-targeted damage bonus (extends existing Slayer perk + UnforgeSlayer tasks); boss tasks | — (already a PvM skill by nature) |
| **Hunter** | Rare mats (chinchompa→AoE weapon, furs→gear, feathers→ammo); **boss lure** consumable (reroll/pick arena boss, or summon tracked target) | Tracking: reveals boss element/weakness in arena before pull | Random boss pull (default behavior) |
| **Prayer** | (existing combat prayers) | Sustain prayers | — |
| **Magic/Thieving** | Out of scope for this pass | — | — |

Rules baked into the table:

- **Every "produces" row has a purchased/dropped alternative** — weaker or less convenient, but
  sufficient to attempt any boss. Skill-crafted versions are better *and* sellable, so skilling is an
  economy role, not a personal unlock.
- **Elemental resist never comes from only one skill** (see §9 caps: e.g. fire resist reachable via
  campfire OR potion OR resist food OR gear affix, each bounded).
- Agility/Slayer are utility-only or already-PvM; they don't produce consumables — that's fine, their
  "every skill helps" duty is met without forcing them into the economy.

---

## 4. Skilling Points — currency and earning

### 4.1 Representation (MVP)

New varp `skill_points` (mirrors `perk_points` exactly: persisted player var, server-side only, no
cache work beyond a `varp.sym` entry). Lifetime-earned counter `skill_points_total` for telemetry and
any future "earned X" unlocks. **Not tradeable** (it's a progression currency, not GP).

Later option: promote to a real `CurrencyType` if a second cache currency is wanted for the shop UI —
see risks.

### 4.2 Earning rules

Points award on **successful skilling results only** (never on failed catches/crafts — the action
that produces nothing pays nothing):

```
points += max(1, round(xpGranted / 20))        // per successful action, scaled by xp
+ milestone bonus at each level-up: level² / 10 points
+ one-time "first craft of recipe" bonus: 25 points
```

- `xpGranted` is the XP after `XpModifiers` but before perk multipliers — keeps skilling-point income
  tied to activity tier, not perk snowballing.
- Per-action award is **bounded** (`/20` with min 1): a shrimp catch pays ~1, a rune-bar smith pays
  ~6. Low-level skilling stays relevant (floors), high-level skilling isn't exponentially better.
- **No points from buying/trading** — points are minted only by doing the skill, so the shop can't be
  drained with GP alone. (GP-bought supplies are the *alternative path* for non-skillers.)
- Daily soft cap per skill (e.g. 500) to bound worst-case inflation; lifetime total uncapped.

Implementation hook: `SkillingRewards.grant`/each skill's success path calls a single
`SkillPointService.award(player, skill, xp)` — one service, one varp write.

### 4.3 What points are *not*

- Not convertible to Perk Points or GP (anti-snowball).
- Not required to craft — recipes use materials, not points. Points buy *convenience, unlocks, and
  shortcuts*; crafting stays material-driven.

---

## 5. Skill Shop structure

One new NPC ("Skill Quartermaster") at the Edgeville home area, opened via the existing `Shops`/
`ShopkeeperScript` path where possible. Because `Shops` transacts a `CurrencyType` and Skill Points
are a varp, the MVP uses a **custom shop script** (dialogue/menu-style list, `skill_points` checked
and deducted server-side) rather than the GP shop UI — same pattern the perk journal already uses for
a non-GP currency. If the client shop UI is wanted later, promote Skill Points to a cache
`CurrencyType` and reuse `Shops.open` unchanged.

Categories (each entry: cost, requirements, stock behaviour):

| Category | Contents | Requirement model |
|---|---|---|
| **Supplies** | Bait/feathers/seeds/herb secondaries/ores — limited daily stock, modest point cost | None (starter aid) |
| **Consumables** | Cooked PvM food (heal+buff), resist potions, campfire kits, special ammo packs, boss lures | Points + matching skill level for the *better* tiers (see balance §10) |
| **Upgrade kits** | Melee/ranged/magic armor upgrade kits, repair kits | Points + skill level **or** points-only at ~3× cost (buy your way without the skill) |
| **Recipe unlocks** | "Recipe scrolls" unlocking new MakeRecipe rows (feast, superior ammo, imbued kits) | Points + the skill level to *use* it — buying the scroll doesn't grant the level |
| **Utility** | Prep-station deploy kit, extra campfire duration, slayer task reroll token | Points only |
| **Cosmetics** | Skilling-outfit recolors / FashionScape-tier items (reuse `SkillingCosmeticCatalog` style) | Points only, sink |

Design intent: the shop is a **safety net and a sink**, not the only source. Everything in
Consumables/Upgrade kits is also craftable; the shop exists so a pure-PvM player can convert
*GP→points?* No — GP can't buy points. So shop tiers that need no skill level are priced in points,
which means **a non-skiller can't use this shop at all** — that's correct by design: the shop
rewards skilling, and the non-skiller's alternatives are GP shops, drops, and other players.

---

## 6. Recipes and consumables

### 6.1 Recipe layer — reuse `MakeRecipe`

New PvM recipes are ordinary `MakeRecipe`/`MakeRequest` rows on existing skills — no new crafting
system. Two small extensions:

- `recipeUnlock: VarpType?` — recipe only appears if the player bought the scroll (skill-shop
  "Recipe unlocks"). Fits the existing `valid`/`level` checks on `MakeRequest`.
- `onMade` hook already exists for side effects (equipment-instance roll for upgrade kits — same as
  Smithing's existing queue pattern).

### 6.2 Consumable layer — new, small

A `PvMConsumable` definition + one consume dispatcher (`onOpHeld1` style, matching how consumable
objs are already opped):

```kotlin
data class PvMConsumable(
    val obj: ObjType,
    val heal: Int = 0,
    val buff: ConsumableBuff?,        // writes into the PvM buff state (§7)
    val delayTicks: Int = 3,          // eat/drink delay like existing food
    val cooldownCategory: String?,    // shared cooldown group ("food", "potion", "campfire")
    val cooldownTicks: Int = 0,
)

sealed interface ConsumableBuff {
    data class Damage(val bps: Int, val ticks: Int) : ConsumableBuff          // → outgoingDamageBps
    data class Resist(val element: Element, val bps: Int, val ticks: Int)     // → element resist pool
    data class Reduction(val bps: Int, val ticks: Int) : ConsumableBuff       // → incomingReductionBps
    data class Shield(val points: Int, val ticks: Int) : ConsumableBuff       // → shieldPoints
    data class HealOverTime(val total: Int, val ticks: Int) : ConsumableBuff  // HoT (new small mechanic)
    data class RuneSave(val chanceBps: Int, val ticks: Int) : ConsumableBuff  // → rune-consume roll
    data class AmmoSave(val chanceBps: Int, val ticks: Int) : ConsumableBuff  // → ammo-consume roll
    data class Reveal(val ticks: Int) : ConsumableBuff                        // Hunter tracking
}
```

State storage: extend the `SupportCombatState` pattern — either literally add fields to it, or a
sibling `PvmBuffState` object with identical semantics (additive bps, replace-not-stack,
`currentMapClock` expiry, cleared on death/logout, PvM-only reads). **One active buff per slot**
(food/damage, resist, campfire, potion) — a new application replaces the old in the same slot, which
is also the anti-stacking balance lever.

### 6.3 The crafting chains

```
Fishing ──► raw fish ─────────────► Cooking ──► PvM food (heal + HoT / resist-food)
Farming ──► herbs/veg ────────────┤
Farming ──► herbs ────────────────► Herblore ─► resist/combat potions
Mining  ──► ores/bars ────────────► Smithing ─► melee armor/shield upgrade kits
Mining  ──► gems ─────────────────► Crafting ─► PvM accessories
Woodcutting ─► logs ──────────────► Fletching ─► ranged kits, elemental ammo
            └► (firemaking fuel) ─► Firemaking ► campfire kits
Runecrafting ─► magic kits, rune-save consumables
Hunter ──► chinchompa/furs/feathers ► weapon mats / ammo / boss lures
Construction ─► prep-station kit (deployable buff loc)
```

All craftable items also appear (weaker tier or pricier) in shops/drops — see §3.

---

## 7. PvM stat model — the minimum set

Do **not** add visible player stats. The existing model is already right: combat stats +
`HitType`-scoped bps pools. The minimal additions:

| Stat | Form | Sources | Cap |
|---|---|---|---|
| `outgoingDamageBps` | existing pool | perks, support spells, food/potion buffs | 4 000 bps (40 %) |
| `outgoingAccuracyBps` | existing pool | support spells, potions | 2 500 bps |
| `incomingReductionBps` | existing pool (all hits) | perks, spells, potions, prep station | 5 000 bps |
| `styleReductionBps` | existing pool (per HitType) | Ironhide/Spellward/Dodging, gear | 4 000 bps |
| **`element` on NPC hits** | **new**: `Element` enum {NONE, FIRE, WATER, PHYSICAL} on the npc type (param) or per-attack | boss definitions | — |
| **`elementResistBps[Element]`** | **new**: small per-element pool on player | resist food, resist potion, campfire, gear affix, perk | **3 000 bps (30 %) per element**, hard-capped |
| `shieldPoints` | existing | spells, consumables | one pool, replace-not-stack |
| `healOverTime` | new tiny ticker | cooked food | one active, ≤20 % maxHP total |
| `runeSaveBps`/`ammoSaveBps` | new tiny fields | RC/Fletching consumables + RC passive | 2 000 bps |
| `stamina`/`dodge` | reuse existing run-energy + a flat physical dodge bps | Agility level | 1 000 bps dodge |

**Element model:** an NPC's attack carries `element` (default NONE = today's behavior). In
`StandardPlayerHitProcessor`, after existing reductions: `damage *= 1 - elementResistBps[element]/10000`.
Elemental identity is *also* expressed in mechanics (burn patches, healing suppression, projectile
dodges), so resistance is mitigation, not an immunity check. `PHYSICAL` is the fallback element for
melee/ranged boss hits — its resist sources are gear + Agility dodge + generic reduction, keeping it
the "default, hardest to buy" axis.

Minimum player stats to *enter* content stay as they are today (combat stats only); recommended
level bands are communicated in dialogue, not enforced:

| Content | Entry requirement | Recommended |
|---|---|---|
| Solo Boss Arena | none (existing) | combat 60+, any food |
| Fire-family boss | none | combat 70+, ≥15 % fire resist from any source |
| Water-family boss | none | combat 75+, ≥15 % water resist, antipoison |
| Physical/elite boss | none | combat 85+, upgraded armor tier ≥ Adamant-equiv |

---

## 8. Permanent perks

Use `PerkService` as-is. The existing enum already covers most of the requested design; proposed
**additions** only where the skill→PvM map has a gap:

| New perk | Tree | Effect (per level, ×10 max) |
|---|---|---|
| `Camper` | Skilling-yield | Campfire buff +3 % duration/strength |
| `Smithwright` | Skilling-yield | Upgrade kits roll +quality floor |
| `Apprentice` | Core | +0.5 % PvM damage per fully-trained (lvl-10) skilling perk — the "skilling pays PvM" bridge, capped small |
| `Scavenger` *(exists)* | Sustain | Already gives perk points from non-boss kills — keep as the PvM→points drip |

Nothing else needed: Berserker/Deadeye/Sorcerer already are the three style damage perks;
Ironhide/Spellward/Dodging already the style defenses; Slayer the task bonus; the 15+ skilling XP
perks already reward each skill.

Perk balance rules: existing triangular XP cost (55 pts for max level) already provides diminishing
returns; keep `MAX_LEVEL = 10`; new perks stay in bps and fold into the same capped pools — a perk
can never push a pool past its cap.

---

## 9. Boss families — fire / water / physical

Bosses are defined by `BossFamily` data (new param-driven config, not new npc mechanics):

```kotlin
enum class Element { NONE, FIRE, WATER, PHYSICAL }

data class BossFamily(
    val npcs: Set<NpcType>,
    val attackElement: Element,
    val weakTo: Element?,             // amplified damage taken from element-tagged sources
    val mechanic: BossMechanic,       // see below
    val supportScale: Int = 10_000,   // feeds SupportNpcRules
    val recommendedCombat: Int,
    val recommendedResistBps: Int,
)

enum class BossMechanic { BURN_PATCHES, HEALING_TIDE, HEAVY_STRIKES, NONE }
```

| Family | Element | Mechanic | Primary counters (≥3 paths each) |
|---|---|---|---|
| **Fire** | FIRE | Burn patches on ground; periodic ignite tick | Fire resist: campfire (FM), resist potion (Herblore), resist food (Fishing→Cooking), gear affix — any mix to the 30 % cap. Safe-spotting & movement (Agility) for patches |
| **Water** | WATER | Healing tide: boss regenerates unless interrupted; slippery stuns | Water resist (same multi-source model); anti-heal: chinchompa/special ammo (Hunter→Fletching), Sorcerer burst, Punisher window |
| **Physical** | PHYSICAL | Heavy melee/ranged strikes, no element tricks | Armor tier (Smithing upgrades or drops), Def/Hitpoints + Ironhide/Dodging perks, shield-points consumables, Agility dodge, kiting |

Key: **no elemental attack is fully resistable** (cap 30 %) and no mechanic requires a specific skill —
each is beatable at 0 resist with more food/skill, and easier with preparation. Weakness
(`weakTo`) rewards elemental-tagged ammo/spellrunes — a bonus multiplier on the *attacker's* side,
not a gate.

---

## 10. Balance rules (anti-mandatory-maxing)

1. **No skill gates any boss.** Entry checks only combat stats (and even those are advisory
   dialogue, not hard checks, matching `SoloBossArena` today).
2. **Every craftable advantage has ≥2 acquisition paths**: craft it, buy a weaker tier for GP/points,
   loot it, or trade with players. Consumables are tradeable.
3. **Caps are hard, not soft**: resist 30 %/element, damage pool 40 %, reduction 50 % — enforced at
   the single aggregation point in the hit processors, so stacking sources can't overflow.
4. **Same-slot buffs replace, never stack** (existing `SupportCombatState` semantics extended):
   you can't run two resist buffs; picking your one slot is a decision, not a grind.
5. **Points mint only from doing the skill**; `/20` scaling + per-skill daily cap keeps high-level
   snowballing bounded; points can't convert to perks or GP.
6. **Skill-level requirements attach to tiers, not to the shop itself**: basic consumables always
   purchasable; "superior" tier needs the level — but crafting it yourself is always an option.
7. **Perk costs are already triangular**; new perks add nothing multiplicative — they feed the same
   capped bps pools.
8. **Economy sinks**: shop stock is finite per day; upgrade kits consume the base item + materials;
   campfires/stations expire. Materials leave the game as consumables.
9. **Diminishing verticality**: after mid-tier (≈rune-equivalent + 15–20 % resist + a few perks),
   additional skilling yields <10 % effective-HP/DPS — optimization, not obligation.

---

## 11. Build progression — melee / ranged / magic

Shared backbone: combat stats + gear tier + perks are build-agnostic; skills add style-specific
kits and universal consumables.

| Stage | Melee | Ranged | Magic |
|---|---|---|---|
| **Entry** (any skill level) | Shop/drop armor+weapon, shop food | Shop bow + standard arrows | Shop staff + standard runes |
| **Skilled** | Mining→Smithing: armor upgrade kits, shield, repair kit | Woodcutting→Fletching: ranged kit, elemental ammo | Runecrafting: magic kit, rune-save, spell-power runes |
| **Universal layer** | Fishing→Cooking food; Farming→Herblore potions; Crafting accessories; Firemaking campfire; Construction prep station; Hunter lure; Agility shortcuts; Slayer task bonus — identical for all three styles | | |
| **Perks** | Berserker + Ironhide | Deadeye + Dodging | Sorcerer + Spellward |
| **Boss tilt** | Physical bosses (armor scaling) | Fire bosses (range + movement vs patches) | Water bosses (burst vs healing tide) |

A player can run **any style at entry with zero skilling**, then specialize vertically by feeding
*one* gathering→production pair — or horizontally by buying/trading the same items.

---

## 12. Data models (concrete shapes)

All server-side, matching existing conventions (`data class` + typed refs + varp state):

```kotlin
// Skill point economy
object SkillPointService {
    fun award(access: ProtectedAccess, skill: String, xpGranted: Double)
    fun spend(access: ProtectedAccess, points: Int): Boolean
    fun balance(player: Player): Int          // reads `skill_points` varp
}

// Shop
data class SkillShopEntry(
    val id: String, val category: SkillShopCategory,
    val obj: ObjType?,              // null for non-item purchases (reroll token, unlock)
    val unlockVarp: VarpType?,      // recipe/perk unlock flag it sets
    val pointCost: Int,
    val requiredStat: StatType?, val requiredLevel: Int,
    val dailyStock: Int,            // 0 = unlimited
)
enum class SkillShopCategory { SUPPLIES, CONSUMABLES, UPGRADE_KITS, RECIPES, UTILITY, COSMETICS }

// Consumables — §6.2 PvMConsumable + ConsumableBuff

// Boss families — §9 BossFamily / Element / BossMechanic

// Buff state — extend SupportCombatState or sibling object:
//   elementResistBps[Element], healOverTime, runeSaveBps, ammoSaveBps, slot-exclusivity

// Recipe gate
val MakeRecipe.unlockVarp: VarpType?   // extension on existing model

// Player persistent state (all varps)
//   skill_points, skill_points_total, skill_points_daily_<skill>? (or one packed daily varp)
//   recipe_unlock_<id> bit-packed varps (same pattern as cw_slayer_unlocks_a/_b)
//   shop daily-stock remainder varps
```

---

## 13. MVP scope

Target: the full loop works end-to-end for **one** elemental boss family with the fewest possible new
systems. Everything else in this doc is post-MVP.

**In scope:**

1. `skill_points` varp + `SkillPointService` (award/spend/balance). ~1 file + sym entries.
2. `SkillPointService.award` calls in **two** skill chains end-to-end:
   Fishing→Cooking (food) and Mining→Smithing (melee kit). Other skills get the one-line hook later.
3. One Skill Quartermaster NPC + custom shop script (menu/dialogue, point costs, 3 categories:
   Consumables, Upgrade kits, Recipe scroll). No cache currency.
4. `PvMConsumable` + dispatcher writing into a new `elementResistBps`/`healOverTime` extension of the
   support-buff state. Implement `Resist`, `HealOverTime`, `Shield` only.
5. `Element` on npc attacks: new npc param + read in `StandardPlayerHitProcessor` (one branch).
6. **One fire boss** (`BossFamily` FIRE + burn-patch mechanic) — new custom npc reusing the
   `SoloBossArena` instancing pattern, or a new entry in the existing wilderness boss set.
7. Campfire consumable (FM): places a loc granting the fire-resist buff — smallest possible
   "deployable" to prove the Firemaking role.
8. One recipe-scroll unlock (feast recipe) to prove the unlock-varp pattern.

**Explicitly out of MVP:** water/physical boss families, prep station, boss lures/tracking, upgrade
kit → EquipmentInstance re-roll UI, most recipe scrolls, cosmetics, real cache currency, new perks,
daily caps (add when economy is observed), group feasts.

**Estimated new surface:** 1 service, 1 shop script, 1 consumable dispatcher, 1 npc param + 1
hit-processor branch, 1 deployable script, data tables. Zero changes to combat formulas, XP rates,
or existing skill logic.

---

## 14. Risks and open decisions

| Question | Options | Lean |
|---|---|---|
| Skill points as varp vs cache `CurrencyType` | Varp needs custom shop UI; currency type unlocks `Shops.open` + client shop UI but costs cache work | **Varp for MVP** — matches `perk_points` precedent; promote later |
| Where does consumable state live? | Extend `SupportCombatState` (api/player) vs new `PvmBuffState` sibling | **Sibling object** — keeps support-spell state uncluttered, same semantics |
| Element on npc type vs per-attack | Param on type = whole npc one element; per-attack needs hit-tag plumbing | **Param on type for MVP**; per-attack later if a mixed-element boss is wanted |
| Non-skillers and the point shop | Points can't be bought → shop useless to them | Intended: their paths are GP shops/drops/trade. Revisit only if consumables end up tradeable-locked |
| Upgrade kits vs instance re-rolls | Kit produces a *new* rolled instance (simple) vs modifying the worn instance (needs instance-edit service) | **New rolled item** for MVP (mirrors Smithing→instance pattern) |
| Resist cap enforcement point | Single aggregation in hit processor vs per-source clamps | **Single aggregation** — one cap check, sources stay simple |
| Do boss mechanics need real "patch" entities? | Reuse firemaking/fire locs + area damage ticks vs new entity type | **Reuse existing loc + damage script** |
| Perk additions | `Camper`/`Smithwright`/`Apprentice` add enum entries + varps | Defer — existing 46 perks cover MVP |

**Risks:** point inflation (mitigated by daily caps + bounded sinks); resist-stacking complexity
(mitigated by replace-not-stack + single cap); content-editor boundaries (spawn TOMLs for the
quartermaster/boss go through the editor pipeline); `SkillAffixRoll` vs consumable duplication
(keep gear affixes for *skilling* effects only — PvM effects come from consumables/perks, one
clear lane each).

---

## 15. Recommended implementation order

1. **`SkillPointService` + varps** — currency exists before anything spends it.
2. **Consumable buff state + `PvMConsumable` dispatcher + `Element` param + hit-processor branch** —
   the combat-facing substrate; unit-testable without content.
3. **Fishing→Cooking PvM food + first resist consumable** — first complete craft→consume→buff chain.
4. **Skill Quartermaster + shop script** — sink exists; put the resist consumable in it.
5. **Fire boss + campfire kit** — first elemental encounter and first deployable.
6. **Mining→Smithing upgrade kits** (instance-roll product) — first gear-progression chain.
7. **Recipe-scroll unlock pattern** (one scroll → one recipe) — extensibility proof.
8. Then expand: water/physical families, remaining skill hooks, prep station, hunter lures, perk
   additions, daily caps + telemetry.

Each step is independently shippable and none modifies existing gameplay logic paths — additions
only (new branch in the player hit processor being the single surgical touch-point).
