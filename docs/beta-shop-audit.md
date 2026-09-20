# Beta Shop & Currency Audit

Audit of every beta-visible shop binding, its currency, and the supporting
economy plumbing. Companion document to `docs/points-command.md` (journal
ledger) and `docs/pvm-skilling-code-audit.md`.

## Currency matrix — active systems

| Currency | Storage | Earned by | Spent at | Persistent | Insufficient-funds behaviour |
|---|---|---|---|---|---|
| Coins (gp) | Inventory (`objs.coins`) | Drops, selling, skilling | All gp shops (~200 generated `WorldShopEdits` bindings + Unforge gp shops below) | Yes (inv) | `You can't afford that.` — purchase aborted atomically |
| PvM points | `pvm_points` varp | NPC kills scaled by combat level (0/1/2/5/10/20) | `cw_pvm_point_shop` via `deal_50luke` (Trade) | Yes (`Perm`) | `You don't have enough PvM points.` |
| Wilderness slayer points | `cw_slayer_wild_points` varp | Dangerous (wilderness-preference) slayer task completion | `cw_wilderness_slayer_shop` via `slayer_master_7` (Wilderness Equipment) | Yes (`Perm`) | `You don't have enough wilderness slayer points.` |
| Slayer points | `cw_slayer_points` varp | Regular slayer task completion | Slayer hub UI — unlocks, cancels, blocks, extensions | Yes (`Perm`) | Hub options gate on balance |
| Skill points | `skill_points` varp | Skilling actions | `::skillshop` interface | Yes | Shop gates on balance |
| Perk points | PerkService state | Boss kills | Perk interface (permanent perks) | Yes | UI gates on balance |
| Companion talent pts | CompanionService (DB) | Companion XP levels | `::pettalents` interface | Yes (companion records) | UI gates on balance |
| Donator points | `cw_donator_points` varp | Admin-granted (donations) | **No spend path yet** — display only (`::donorstatus`) | Yes (`Perm`) | n/a — see limitations |

## Bound shops

### Custom-currency shops

| NPC | Shop inv | Currency | Price source | Status |
|---|---|---|---|---|
| `deal_50luke` | `cw_pvm_point_shop` | `pvm_points` | Fixed price table ported from `content/kronos-data/shops/cw_pvm_point_shop.yaml` | Enabled |
| `slayer_master_7` | `cw_wilderness_slayer_shop` | `wilderness_slayer_points` | Fixed price table (looting bag 90, rune pouch 150, imbued heart 600) | Enabled |

Currency resolution: `ShopParams.npc_shop_currency` on the NPC type →
`ShopkeeperScript` looks up `CurrencyType` in `NameMapping` →
`ShopOperationMap` routes to `PvmPointShopOperations` /
`WildSlayerPointShopOperations` (both extend `VarpShopOperations` — balance and
debit via varp, sells rejected, atomic debit-before-grant).

### Unforge gp shops (`UnforgeShopEdits`)

All verified against `kronos-data` as COINS shops — correct as gp:

| NPC | Shop | Notes |
|---|---|---|
| `gem_trader` | `gem_shop` (Herquin's Gems) | Trade op added |
| `shopkeeper_weapon_master` | `cw_Unforge_weapon_shop` | Trade op added |
| `shopkeeper_weapon_master_2` | `cw_Unforge_weapon_shop_varrock` | Trade op added |
| `fishing_shop` | `cw_fishing_shop` | Trade op added |
| `crafting_shop` | `cw_crafting_shop` | Trade op added |
| `fashion_shop` | `cw_fashion_shop` | Trade op added |
| `food_shop` | `cw_food_shop` | Trade op added |
| `sarah` | `cw_farming_shop` | Trade op added |
| `mining_shop` | `cw_mining_shop` | Trade op added |
| `ranging_shop` | `cw_ranging_shop` | Trade op added |
| `woodcutting_shop` | `cw_woodcutting_shop` | Trade op added |
| `generalstore_med/easy/hard`, donator variants | `cw_general_store_*` | Trade op added |
| `hunter_shop` | `cw_hunter_shop` | Trade op added |
| `agility_shop` | `cw_agility_shop` | Trade op added |
| `vote_shop` | `cw_Unforge_reward_shop` | kronos COINS — correct gp |
| `smelting_shop` | `cw_smelting_shop` | Trade op added |
| `slayer_master_nieve`, `slayer_master` (Tureal) | `cw_slayer_equipment` | gp, OSRS-correct |
| `diary_queen` | `cw_achievement_rewards` | kronos COINS — correct gp |
| `hatius` | `cw_gambling_shop` | kronos COINS — correct gp |

### Generated world shops (`WorldShopEdits`)

~200 NPC→shop bindings generated from kronos data; all verified COINS.
`WorldShopOpEdits` adds `op1 = "Trade"` to the 22 shopkeeper types whose
packed ops were empty (the runtime-log failures). `NpcEditor` edits merge
sparsely, so op-fixes compose safely with the generated bindings.

## Disabled / intentionally unbound shops

These shops exist in the packed cache but have **no NPC binding** — their
currencies have no earn or spend path, so binding them would silently charge
gp for reward-shop stock (economy defect). They can be re-enabled once their
currency is implemented.

| Shop / NPC | Original currency | Why disabled |
|---|---|---|
| `cw_refunded_credit_exchange` (`wise_old_man`) | Refunded credits | Would have sold infernal cape/rings for gp — most severe exploit prevented |
| `pvpa_shop_inv` (emblem traders) | Bounty emblems | No emblem economy |
| `vote_ticket_shop` | Vote tickets | No vote integration |
| `blood_money_shop` (gunnjorn/radigad/traiborn menus) | Blood money | Menu choices removed (`choice4`→`choice3`); gear was BiS-for-gp |
| `pest_control_rewards` | Pest Control pts | No Pest Control activity |
| `survival_token_shop` | Survival tokens | No survival activity |
| `golden_nugget_shop` | Golden nuggets | No Motherlode earn path |
| `marks_of_grace_shop` | Marks of grace | No rooftop course drops |
| `molch_pearl_shop` | Molch pearls | No aerial fishing |
| `mage_arena_shop` | Mage Arena pts | No Mage Arena activity |
| `appreciation_point_shop` | Appreciation pts | No earn path |
| `warrior_guild_token_shop` | Warrior Guild tokens | No guild activity |
| `wilderness_point_shop` | Wilderness points (other) | Distinct from wild slayer pts; no earn path |
| `easter_egg_shop` | Easter eggs | Seasonal |
| `tokkul` shops (TzHaar) | Tokkul | Tokkul obj not in cache |
| `unidentified_minerals_shop` | Unid. minerals | No earn path |

Six NPCs that already had ops but pointed at these shops had their `npc_shop`
param removed; five NPCs keep a packed `npc_shop` param but expose no Trade
op, so `ShopkeeperScript` never fires for them (harmless leftover).

## Fixes applied

- `ShopParams.npc_shop_currency` param + `ShopkeeperScript` currency
  resolution (`NameMapping` → `CurrencyType` → `ShopOperationMap`).
- `VarpShopOperations` (api:shops) — varp-backed balance/debit, sell
  rejection, atomic purchase.
- `pvm_points`, `wilderness_slayer_points` entries in `currency.sym` +
  `param.sym` entry for `npc_shop_currency`.
- `PvmPointShopOperations` / `WildSlayerPointShopOperations` with kronos
  price tables; registered via `UnforgePointShopScript` →
  `ShopOperationMap`.
- ~15 dead-currency shop bindings removed; Blood Money menu choices removed;
  emblem trader unbound.
- `WorldShopOpEdits` Trade ops for the 32 NPCs from the runtime error log.

## Test coverage

`api:shops` integration tests — 15/15 passing:

- `ShopkeeperScriptGpBuyTest` (7): gp regression after custom-currency
  registration — single/bulk buys, stock decrement, insufficient funds,
  out-of-stock, no-coins.
- `VarpShopOperationsBuyTest` (8): varp currency buys — debit exact amount,
  bulk capped by balance, insufficient funds leaves balance+stock untouched,
  out-of-stock, sell rejection, atomicity, no negative balance.

## Known limitations / future work

- **Donator points** have no spend path — real admin-managed balance,
  display-only until a donator shop is built.
- **Slayer points** spend only through the slayer hub (no shop inventory) —
  intentional, matches OSRS reward-menu model.
- Disabled shops above are one binding-line away from re-enablement once
  their currency earns; stock definitions are preserved.
- gp `objType.cost = 0` items price at 1 gp (engine default), never free.
