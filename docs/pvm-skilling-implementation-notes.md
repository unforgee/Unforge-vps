# PvM + skilling — vertical slice: toteutusmuistiinpanot

Päivitetty 2026-09-17. Täydentää `docs/pvm-skilling-progress.md`:ää ja
`docs/pvm-skilling-architecture.md`:ää.

## Mitä toteutettiin

Yksi end-to-end-ketju:

```
mining success → XP ennallaan → +1 skill point (varp)
  → skill shop (unforge_skillshop) → reward inventoryyn
  → Perk.Smithwright (PerkService) → −0.5%/lvl incoming melee dmg from npcs (max 5%)
  → PvP, NvP, NvN ja kaikki muu ennallaan
```

### Skill Points = varp-saldo (ei item-token)

`skill_points` (varp 5684) on `permanent` + `transmitNever` — identtinen malli
`perk_points`-varpin kanssa. Alkuperäinen `star_fragment`-item-token hylättiin,
koska shop käytti varpia jo UI-tasolla, token ei selviäisi restartista ja
ato?minen vähennys on varpilla triviaali. Skill Points ja Perk Points ovat edelleen
eri valuuttoja ilman konversiota.

### Ansainta

`Mining.kt`: onnistunut heitto (`minedOre`-haara, heti `statAdvance`:n jälkeen)
kutsuu `skillingPoints.grant(this, SkillingPointAward(1))`. Keskeytetty,
epäonnistunut tai virheellinen action ei koskaan saavuta riviä — myöntö on
rakenteellisesti kerran per action.

### Shop

`SkillShopScript.buy`: level-gate → owned-check → `skillingPoints.balance` →
item (`invAddOrDrop`) tai unlock-bit → `skillingPoints.spend` → viesti → `refresh()`.
Ostojärjestys säilyttää alkuperäisen invariantin: saldo tarkistetaan ennen
myöntöä, ja `objTypes[entry.obj] ?: return` ei voi kuluttaa pisteitä ilman itemiä.

### Perk

`Perk.Smithwright` (Combat-osio, xp-varp `perk_xp_smithwright` = 5685) lisättiin
`PerkService.styleReductionBps`:n melee-haaraan: `Ironhide*200 + Smithwright*50`
bps. Koska `StandardPlayerHitProcessor` soveltaa `styleReductionBps` vain
`hit.isFromNpc`-haarassa, perkki ei vaikuta PvP-, NvP- tai NvN-polkuun ollenkaan.
Level 10 = 500 bps = 5%, POC-katon sisällä. `PerkVarpBuilder` generoi xp-varpin
automaattisesti enum-arvosta — vain `.data/symbols/varp.sym`-merkintä tarvittiin.
Perk journal renderöi Smithwrightin ilman lisätöitä (komponentit perk0–perk49
oli allokoitu ennalta).

## Käytetyt olemassa olevat järjestelmät

| Tarve | Järjestelmä |
|---|---|
| Persistent server-side saldo | `VarpType` + `VarPlayerIntMap` + `VarPlayerIntMapSetter` (sama kuin `perk_points`) |
| Perk-malli + journal + xp-varpit | `Perk` enum, `PerkVarpBuilder`, `PerkService`, `PerkJournalScript` |
| PvM-vahinkovähennys | `StandardPlayerHitProcessor` `hit.isFromNpc`-haara → `styleReductionBps` |
| Shop-UI | `unforge_skillshop`-interface + komponentit (aiemmin rakennettu) |
| Item-myöntö | `invAddOrDrop` (nykyinen shop-konventio) |
| Skill XP | `statAdvance` + `xpMods` + `PerkService.skillXpBps` — koskematon |
| Admin-avaus | `onCommand("skillshop")` / `::skillpoints <n>` cheatit |

## Muutetut tiedostot

| Tiedosto | Muutos |
|---|---|
| `content/skills/skilling-core/.../SkillingPoints.kt` | Refaktoroitu varp-taloudeksi: `SkillingPointVarps`, `SkillingPointVarpBuilder`, `SkillingPointAward`, puhtaat `applySkillingPointAward/Spend`, `@Singleton SkillingPoints` (`balance`/`grant`/`spend`). Item-token poistettu. |
| `content/skills/skilling-core/.../SkillingPointsTest.kt` | 7 yksikkötestiä: validointi, kerrytys, dedup-sopimus, spend-sopimus. |
| `content/skills/skilling-core/.../SkillingPointConfigTest.kt` | Uusi: varp resolvoituu + tuore pelaaja lukee 0. |
| `content/skills/mining/.../Mining.kt` | `SkillingPoints` injektio + yksi grant-rivi `minedOre`-haarassa. |
| `content/skills/mining/.../MiningScriptTest.kt` | 3 uutta testiä: success→1pt, invalid→0pt+0xp, failed roll→0pt; ei duplikaattia. |
| `content/skills/mining/build.gradle.kts` | `implementation` + `integrationImplementation` skilling-coreen. |
| `content/interfaces/skill-shop/.../SkillShopVarps.kt` | `skill_points`-builder siirretty skilling-coreen; `recipeUnlocks` jäi; `points` ref resolvoituu nimellä. |
| `content/interfaces/skill-shop/.../SkillShopScript.kt` | `SkillingPoints` injektio; `buy` käyttää `balance`/`spend`. |
| `content/interfaces/skill-shop/build.gradle.kts` | `implementation` skilling-core + `integrationImplementation` api:player + `integration-test-suite`-plugin. |
| `content/interfaces/skill-shop/.../SkillShopScriptTest.kt` | Uusi: 4 integraatiotestiä (osto, insufficient funds, unlock-bit, level-gate). |
| `api/player/.../perk/Perk.kt` | `Perk.Smithwright` + `PerkVarps.xp_smithwright`. |
| `api/player/.../perk/PerkService.kt` | `styleReductionBps` melee-haara: `+ Smithwright*50`. |
| `api/player/.../StandardPlayerHitProcessorTest.kt` | 3 uutta testiä: locked/unlocked melee, magic-tyyli, PvP. |
| `.data/symbols/varp.sym` | `5684 skill_points`, `5683 skill_recipe_unlocks`, `5685 perk_xp_smithwright`. |
| `content/areas/unforge/.../db/migration/V21__unforge_eco_server_identity.sql` | Uudelleennimetty V18:sta (Flyway-duplikaatti api/db:n V18:n kanssa). |

## Ajatut testit ja build

- `spotlessApply` kaikille muutetuille moduuleille — OK.
- `:content:skills:skilling-core:build` `:mining:build` `:interfaces:skill-shop:build`
  `:api:player:build` — BUILD SUCCESSFUL.
- `:content:skills:skilling-core:integration` — 1/1 OK.
- `:content:skills:mining:integration` — 15/15 OK (9 `MiningScriptTest` + 6 `MiningConfigTest`).
- `:content:interfaces:skill-shop:integration` — 4/4 OK.
- `:api:player:integration` — 60/60 OK (mukaan 4 `StandardPlayerHitProcessorTest`-tapausta).
- `packCache` ei vaadittu: varpit ovat koodityyppejä (VarpBuilder), eivät cache-tyyppejä.
  Testipalvelin kirjoittaa varpit cacheen bootissa normaalisti.

## Kompromissit

- **`invAddOrDrop` pudottaa maahan** jos inventaario on täysi — nykyinen shop-konventio,
  sama kuin `api:shops`-engine. "Item inventoryyn" -vaatimus täyttyy testissä;
  maahan pudotus on hyväksytty fallback.
- **`skill_points` ei transmit**: saldo renderöidään `ifSetText`:llä shop-headeriin —
  ei client-paketteja, sama malli kuin `perk_points`.
- **Smithwright-avaus**: perkki on `Perk.entries`:ssä → journalin Train-nappi toimii,
  mutta POC ei toteuta "osta skill pointeilla" -polkua perkille; xp-varpia voi kasvattaa
  journalin train-toiminnolla tai tulevalla ansaintalogiikalla.
- **Shop-avaus vain `::skillshop`**: NPC-integraatio (quartermaster `Trade`-op) on
  jonossa — interface on jo npc-avauskelpoinen.
- **`SkillingPointAward` ei deduplokoi**: kutsujan (skill-skriptin) vastuulla, koska
  idempotenssi vaatisi per-action-tilaa jota POC:ssa ei ole. Testi lukitsee sopimuksen.

## Ristiriidat architecture-doc ↔ koodi

| Asia | Dokumentti | Koodi | Ratkaisu |
|---|---|---|---|
| Skill point -malli | "varp tai character-balance" / progress-docin item-token | `skill_points` varp | Varp voitti — token poistettu; dokumentoitu progress.md:ssä |
| Shop-engine | "yksi shop entry nykyisellä infralla" | Custom `unforge_skillshop` modal, ei `Shops`-engine | Koodi voitti — engine vaatisi `CurrencyType`-laajennuksen, joka lykättiin |
| Perk-vaikutus | "fire damage tai melee armor" | `Smithwright` = incoming melee reduction | Koodi voitti — pienin muutos olemassa olevaan `styleReductionBps`-polkuun |
| Varpujen id:t | progress-doc: `skill_points` 5682 | 5684 (5682 on `gathering_farm_8557_remaining`) | Koodi voitti — id:t ovat vain nimiavaruuden avaimia |

## Item-inspect-sivupaneeli (2026-09-17, erillinen tehtävä)

Examine → `unforge_iteminspect` side-paneeli aseille ja armoureille. Ei osa vertical
sliceä, mutta jakaa saman `EquipmentInstance`-ekosysteemin.

### Rakenne

- `ObjExamineEvents.Examine` (unbound, `api/player/events/interact`): julkaistaan
  `HeldInteractions.examine` + `WornInteractions.objExamine` -poluilta normaalin
  chat-examinen ja `mesInstanceData`-lähetyksen jälkeen. Sivupaneeli-plugini tilaa
  eventin — examine-koodiin ei tartte kytkeä paneelia.
- `EquipmentInstanceDescribe`: `describe(type, instance)`:n stat-block irrotettu
  `statLines(type, affixes)`-funktioon (affix-listapohjainen, sama WornBonuses-semantiikka:
  Flat = additiivinen, BasisPoints = skaalaa basea) ja `describeBase(type)` lisätty
  base-näkymälle. Wire-kontrakti client-hover-pluginin kanssa säilyi koskemattomana.
- `ItemInspectScript` (`content/interfaces/item-inspect`): `onEvent<Examine>` →
  equipment-gate → `ifOpenSide` → render (title/subtitle/obj-model/26-rivin pooli) +
  `abilityLines` = `EquipmentAbilityCatalog.displayName` + `EquipmentAbilityProcs.procFor`
  -kentät selkokielellä (proc-chance `chanceBps(rarity, ilvl)`, on-hit/on-kill/strike/passive).
- Equipment-gate: `type.isEquipable` + `Wearpos[wearpos1]` ei ole Ring/Quiver/
  client-only (Arms/Head/Jaw). Instansoitu item avaa aina.
- UI: `unforge_iteminspect` (interface 1008, 243x334), scrollattava content-layer +
  `ifSetScrollPos`-napit; scroll-tila `WeakHashMap<Player, Int>`:ssa, `onIfClose` nollaa.

### Testin ympäristöhuomiot

- `If3ButtonHandler` vaatii `ui.containsOverlay/containsModal` + IfEvent-maskin:
  held-examine-testi avaa `interfaces.inventory` → `toplevel_target_side3` oikeana
  overlayna, jolloin `InvOpenScript.onIfOpen` rekisteröi `Op10`-maskin kuten loginilla.
- Staattisten komponenttien (comsub=-1) baked `events` riittää ilman `ifSetEvents`:ää
  → paneelin close/scroll-napit toimivat suoraan.
- `EquipmentInstanceRegistry` sai `@Singleton`-annotaation: ilman sitä testi-injektorin
  JIT-sidonta antoi skriptille ja depsille eri instanssit (child-module-sidonta kaatui
  `JitBindingAlreadySet`-virheeseen, koska parent oli jo realisoinut JIT-bindingin).
- `advance()` tyhjentää capture-clientin ennen tickiä → suoraan
  `Interactions.examine`-kutsun jälkeiset `IfSetText`-paketit luetaan ilman advancea.

## Ympäristökorjaukset kierroksen aikana (eivät osa slicen toiminnallisuutta)

- **Flyway V18-duplikaatti** — Unforgein `V18__unforge_eco_server_identity.sql`
  törmäsi api/db:n aiempaan `V18__equipment_instance_skilling_affixes.sql`:iin.
  Uudelleennimetty `V21` (uusin vapaa). Koskee kaikkia integraatiotestejä, ei vain
  tätä slicettä — korjaus dokumentoitava ylös, jos tuotanto-DB on jo ajanut V18:n
  eri nimellä.
- **Tyhjä `combat-commons-0.0.1.jar` (261 B)** — keskeytetyn Gradle-ajon jäännös;
  paketti puuttui kokonaan → `combat-weapon`/`spells-runes` "unresolved reference"
  -virheet. Korjattu jar:n uudelleenajolla.
- **Stale Kotlin classpath-snapshotit** — `gradlew --stop` + snapshot-hakemistojen
  siivous (sama oire kuin aiemmassa sessiossa).

## Seuraavan skillin lisääminen samaan malliin

1. **Ansainta**: injektoi `SkillingPoints` skill-skriptiin ja kutsu
   `skillingPoints.grant(this, SkillingPointAward(1))` onnistumishaarassa
   (sama kohta kuin `statAdvance` — ei validointi-, start- eikä fail-polkuihin).
2. **Testi**: kopioi `MiningScriptTest`-malli — keskeytys/fail/invalid → 0,
   yksi success → 1, toinen action → 2.
3. **Shop-rivi**: lisää `SkillShopEntry`-arvo (obj, cost, mahd. level-gate/unlockBit)
   — rivi renderöityy ja toimii automaattisesti ilman muita muutoksia.
4. **Uusi perk**: lisää `Perk`-enum-arvo + sym-merkintä `perk_xp_<nimi>` (seuraava
   vapaa id) + vaikutus `PerkServiceen` — npc-scope tulee ilmaiseksi
   `hit.isFromNpc`-haarasta.
5. **Ei tarvita**: uutta varp-builderia (varp on jo), uutta shopia, uutta UI:a,
   DB-migraatiota, cache-pakkausta (varpit ovat koodityyppejä).
