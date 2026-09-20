# PvM + skilling -edistymispäiväkirja

Päivitetty 2026-09-18 (neljäs päivitys: equipment instance template mismatch -korjaus).

### Korjaus – peli kaatuu quest/perk-tabissa (equipment instance template mismatch) — valmis 2026-09-18

**Oire:** `server-public.log`: `SQLITE_CONSTRAINT_TRIGGER ... equipment instance template
mismatch` → `Reached max retry attempts` tallennuksessa; vaihto quest/perk-tabiin näytti
"kaatavan pelin".

**Juurisyy:** `V9__equipment_instances.sql` lisäsi `inventory_objs`-tauluun triggerin, joka
hylkää (`RAISE(ABORT)`) jokaisen rivin, jossa `equipment_instance_id != 0` mutta `obj !=
template_obj`. `CharacterInventoryPipeline.save()` tallensi inventory-objektin **elävän** `obj.id`-n
sellaisenaan; transformoitu/derivoitu instance-variantti (sama instance-id, eri obj) rikkoi
triggerin → koko upsert-batch kaatui → pelaajan tallennus ei mennyt läpi.

**Korjaus** (`api/account/.../CharacterInventoryPipeline.kt`): ennen upserttia esiladataan
`equipment_instances.template_obj` kaikille inventoryssa esiintyville instance-id:ille ja
tallennetaan instance-objekteille kanoninen `template_obj`-id (`persistedObjId =
templateByInstance[instanceId] ?: obj.id`), jolloin triggeri pysyy tyytyväisenä. Apufunktiot
`inventoriesWithInstances` + `selectInstanceTemplates`.

Lisäksi `UnforgePerksInterfaceBuilder` muutettiin `internal` → `public` (ClassGraph lataa
`public object`-builderit varmasti; `private` olisi kielletty).

**Testit:** uusi `CharacterInventoryPipelineTest` (3 integraatiotestiä: template id tallennus
transformoidulle instancelle, instance-id-keräys, template-lookup). `:api:account:compileKotlin`
onnistui (class-tiedostot syntyivät). `:api:account:integration`-ajoa ei saatu suoritettua tässä
istunnossa: rinnakkaiset Gradle-daemonit (8.8 + 9.2) pitivät buildin lukittuna ja katkaisivat ajot.

**Rajoitukset:** integraatiotesti on lisätty mutta ei vielä ajettu (liittyy ympäristön daemon-
päällekkäisyyksiin); aja `.\gradlew :api:account:integration --offline --no-parallel` kun
daemonit ovat vapaita. `packCache` ajettava seuraavalla kerralla (builder-näkyvyysmuutoksen
jälkeen).


Tämä dokumentti seuraa toteutusta suhteessa `docs/pvm-skilling-implementation-plan.md`-vaiheistukseen.
Yksi muutoskierros = yksi rajattu tehtävä; ei pyritä kattamaan koko vertical sliceä kerralla.

## Suoritettu

### Sivuhomma: item-inspect-sivupaneeli — valmis 2026-09-17

Examine-opillisesti avautuva equipment-inspection-paneeli (`unforge_iteminspect`, interface 1008):
aseen tai armourin examine avaa side-modal-slotin paneelin, joka näyttää item-mallin, nimen,
**post-affix-effectiiviset statit** ja tarkan ability-selityksen per unique effect.

- Uusi moduuli: `content/interfaces/item-inspect` (builder + script + integraatiotestit).
- Koukutus: uusi `ObjExamineEvents.Examine` unbound-event, julkaistaan sekä
  `HeldInteractions.examine` että `WornInteractions.examine` -poluilta → paneeli ei ole
  sidoksissa inventory-interaktiokoodiin.
- Statit eivät duplikoi laskentaa: `EquipmentInstanceDescribe`:stä irrotettiin `statLines`
  (jaettu) ja lisättiin `describeBase(type)` varustamattomille itemeille. Instansoiduilla
  itemeillä renderöidään sama `describe(type, instance)`-output kuin examine/hover-polulla.
- Ability-selitykset resolvoidaan `EquipmentAbilityCatalog.displayName` +
  `EquipmentAbilityProcs.procFor`/`chanceBps` — samat luvut joita combat rollaa
  (proc chance, on-hit heal bps, on-kill heal/prayer, extra drop rolls, bonus strike
  style/multiplier/base, outgoing dmg bps).
- Equipment-gate: avautuu vain `isEquipable` + oikea combat-wearpos (Ring, Quiver ja
  client-only kosmeettiset slotit suljettu pois); ei-instansoidut varusteet näyttävät
  base-statit; muut itemit säilyvät chat-only-examinena.
- UI: otsikko, alaotsikko (rarity/tier/ilvl tai "Base item"), obj-malli, 26 rivin
  scrollattava tekstipooli, close- ja scroll up/down -napit.
- `EquipmentInstanceRegistry` merkitty `@Singleton`:ksi (oli jo singleton-sidottu
  tuotantoinjektorissa — annotaatio tekee testi-injektorien JIT-sidonnasta
  yhdenmukaisen).

Testit `ItemInspectScriptTest` (6/6 vihreää): held weapon base, held armour,
non-equipment → ei paneelia, worn equipment (`WornInteractions.examine`-polku),
instansoitu item affix-effective-stateilla + tarkka ability-teksti
(`spec-energy-drain` → "heal 12.0% of damage dealt"), close-nappi.
Held-testit ajavat koko ketjun: `ifButton(inv_items, Op10)` → `HeldOpScript` →
`HeldInteractions.examine` → event → `ifOpenSide` — inventory avataan testissä
oikeaan gameframe-slottiin (`toplevel_target_side3`), jolloin `InvOpenScript`
rekisteröi Op10-eventit samoin kuin login-peliruudussa.
`.data/symbols`: `1008 unforge_iteminspect` + 42 komponenttia; `packCache` ajettu.

### Tehtävät 2–4/6: ensimmäinen vertical slice — valmis 2026-09-17

Ketju `mining success → XP ennallaan → 1 skill point → shop-osto → reward inventoryyn →
PerkService-perk → PvM-vaikutus → PvP ennallaan` on nyt end-to-end toiminnassa ja testattu.
Tekniset yksityiskohdat: `docs/pvm-skilling-implementation-notes.md`.

#### Valuuttamallin korjaus (tehtävä 1:n oikaisu)

Item-token (`star_fragment`) korvattiin **varp-saldolla**: `skill_points` (varp 5684) on
persistent, `transmitNever` server-side balance — sama malli kuin `perk_points`. Syy: skill
shop käyttää varpia jo UI:ssa, token ei olisi selvinnyt restartista ilman
inventaariopersistenceä, ja shop-osto vaatii ato?misen saldovähennyksen, joka varpilla on
read-modify-write yhdessä kohdassa. Token-prototyyppi poistettu; `SkillingPoints.kt`
kommentoi päätöksen.

#### Pisteiden ansainta (mining)

- `Mining.kt`: onnistunut heitto (`minedOre`-haara, `statAdvance`:n jälkeen) kutsuu
  `skillingPoints.grant(this, SkillingPointAward(1))`. XP-polku ennallaan.
- `SkillingPoints` (@Singleton, skilling-core): `balance`, `grant`, `spend` — kirjoitukset
  `VarPlayerIntMapSetter`in kautta, atomisuus game-threadin read-modify-writestä.
- `SkillingPointVarpBuilder` (skilling-core) rakentaa `skill_points`-varpin;
  `skill_recipe_unlocks` (varp 5683) jäi shop-moduuliin.

#### Shop

- `SkillShopScript.buy` reititetty `SkillingPoints`-palvelun kautta: level-gate →
  owned-check → saldotarkistus → item/unlock-myöntö → `spend` → viesti → `refresh()`.
- Item-myöntö on `invAddOrDrop` (nykyinen shop-konventio); testi inventaariossa tilaa.
- Reward: `Shark x5` (cost 25, obj 385) on POC:n valmistautumisreward; muut rivit
  (PrayerPotion, RuneArrows, SuperRestore, CampfireKit, ArmourKit, FeastScroll)
  toimivat samalla polulla.

#### Perk

- `Perk.Smithwright` (Combat-osio, ennen Scholar-rajaa): `-0.5% incoming melee damage from
  npcs per level`, xp-varp `perk_xp_smithwright` (5685).
- `PerkService.styleReductionBps`: melee-haaraan `Ironhide*200 + Smithwright*50` →
  5% maksimi level 10:llä (POC-katto ≤5%). Kytketty olemassa olevaan
  `hit.isFromNpc`-haaraan `StandardPlayerHitProcessor`issa → PvP ja muut polut koskemattomia.
- `PerkVarpBuilder` luo `perk_xp_*`-tyypit automaattisesti enum-arvoista → vain sym-merkintä.
- Perk journal renderöi Smithwrightin dynaamisesti (komponentit perk0–perk49 valmiina).

#### Testit (kaikki vihreää)

| Testi | Tyyppi | Kattaa |
|---|---|---|
| `SkillingPointsTest` (7) | unit | award-validointi, kerrytys, dedup-sopimus, spend-sopimus |
| `SkillingPointConfigTest` (1) | integration | varp resolvoituu + vanha save → 0 |
| `MiningScriptTest` (uudet 3, yht. 9) | integration | success → XP+1pt, keskeytys/fail/invalid → 0, ei duplikaattia |
| `SkillShopScriptTest` (4) | integration | osto kuluttaa tasan, insufficient funds, item inv:hen, unlock-bit, level-gate |
| `StandardPlayerHitProcessorTest` (uudet 3, yht. 4) | integration | locked=entinen tulos, lvl10=−5%, lvl5=−2.5%, magic-hitti ennallaan, PvP-hitti ennallaan |

#### Kierroksen ympäristökorjaukset (eivät osa slicen toiminnallisuutta)

- Flyway-duplikaatti: `content/areas/unforge/.../V18__unforge_eco_server_identity.sql`
  → `V21__unforge_eco_server_identity.sql` (api/db:n V18 oli aiempi).
- `combat-commons-0.0.1.jar` oli pakattu tyhjänä keskeytetyssä ajossa → uudelleenrakennettu.
- Kotlin-daemonin stale classpath-snapshotit siivottu (`combat-weapon`, `spells-runes`).
- `.data/cache/game|js5` torn-write → `packCache` ei vaadittu; testiboot kirjoitti
  cachet kuntoon (varpit ovat koodityyppejä, ei cache-tyyppejä).

### Tehtävä 1/6: skilling point -datamalli (item-valuutta) — korvattu

> **Huom**: alla oleva item-token-toteutus on korvattu varp-saldolla (ks. yllä).
> Säilytetty historiatiedoksi; `star_fragment`-tokenia ei ole enää koodissa.

Planin ensimmäinen toteuttamaton pieni tehtävä, joka ei riipu Devinin integraatiosta: skilling
point -datamalli/valuutta (suunnitelman vaihe 2:n ensimmäinen pala ja "Tiedostokohtainen lista" -
rivin `skilling-core` typed helper -kohta).

#### Päätös Vaihe 0 (kohta 1): valuuttamalli

- **Valinta: väliaikainen item-valuutta** (auditoinnin suosittelema fallback), tokenina
  `star_fragment` (obj 25547).
- **Perusteet**:
  - Forkin shop-runtime tukee vain `standard_gp`-nimettyä valuuttaa
    (`api/config/refs/BaseCurrencies.kt` + `.data/symbols/currency.sym`); kaikki muut kronos-shopit
    on portattu merkinnällä `// TODO(currency): kronos=...`
    (`content/areas/unforge/.../shops/UnforgeShops.kt`). Nimetyn `SKILLING_POINTS`-purseen
    rekisteröinti vaatisi shop-engine-muutoksen, jonka suunnitelma lykkää.
  - Tokenin on oltava yksinomainen, jotta pisteitä ei voi farmingoida muusta sisällöstä:
    `star_fragment` on ainoa tarkistettu ehdokas, johon ei ole mitään viittausta
    (`content/`, `api/`, `server/`, spawn-TOML:t; `ectotoken`-nimeä käyttää vain enricherin
    destroy-note-teksti, ei myöntöä tai kauppaa).
  - Ei tietokanta-migraatiota, ei uutta player-tilaa, ei shop-muutosta tässä kierroksessa.

#### Toteutus

| Tiedosto | Muutos |
|---|---|
| `content/skills/skilling-core/src/main/kotlin/org/rsmod/content/skills/core/SkillingPoints.kt` | Uusi: `SkillingPointObjs` (token-ref), `SkillingPointAward` (validoitu arvo, per-action-katto), `SkillingPointGrant` (Stored/Rejected), `applySkillingPointAward` (puhdas toimitussopimus), `SkillingPoints` (@Singleton: `grant`, `balance`). |
| `content/skills/skilling-core/src/test/kotlin/org/rsmod/content/skills/core/SkillingPointsTest.kt` | Uusi: planin pyytämät kolme yksikkötestiä (hylätty/tyhjä tila, onnistunut myöntö, duplikaattikutsu) + kattoraja. |
| `content/skills/skilling-core/src/integration/kotlin/org/rsmod/content/skills/core/SkillingPointConfigTest.kt` | Uusi: tokenin olemassaolo ja stackability vasten ladattua cachea. |
| `content/skills/skilling-core/build.gradle.kts` | Lisätty `integration-test-suite`-plugin (seuraa smithing/mining-mallia). |

#### Suunnittelusopimukset

- **Toimitus on atominen ja ei pudota lattialle**: strict-inventaariolisäys; täysi inventaario →
  `Rejected`, ei osittaisia myöntöjä, ei ground-pileä (ei hyödynnyspintaa eikä kadonnutta valuuttaa).
- **Idempotenssi on kutsujan vastuulla**: malli on tilaton eikä hiljaisesti deduplokoi;
  skill-skripti myöntää täsmälleen kerran onnistunutta actionia kohden. Yksikkötesti lukitsee tämän
  sopimuksen.
- **Ei geneeristä frameworkia**: pelkät "myönnä N pistettä yhdestä actionista" ja "lue saldo";
  ei event-bussia, ei ledger-historiaa, ei XP-polun muutoksia (XP tulee edelleen
  `PlayerStatMap`/`PlayerSkillXP`-polkua).

#### Testit ja build

- `:content:skills:skilling-core:spotlessApply :content:skills:skilling-core:build` →
  **BUILD SUCCESSFUL**, exit 0.
  - Yksikkötestit (Gradlen `test`-vaihe): `MakeRequestTest` 3/3, `SkillingPointsTest` 4/4 —
    ei virheitä.
- `:content:skills:skilling-core:integration` → tuloksia alla.

### Aiemmat kierrokset (konteksti)

- `skilling-core`-moduuli luotiin aiemmassa kierroksessa: jaettu make-loop + `skillmulti`-menut
  Cooking/Crafting/Fletching/Firemaking-toteutuksille (ks. aiempi raportti). Herblore ja Runecraft
  odottavat edelleen saman mallin mukaisiksi; ne ovat erillisiä tehtäviä tästä PvM+skilling
  suunnitelmasta.

## Jonossa (planin järjestyksessä)

1. ~~Mining-toiminnon kytkentä~~ — valmis (vertical slice).
2. ~~Yksi shop entry~~ — valmis (7 riviä samalla polulla).
3. ~~Yksi skill perk~~ — valmis (`Smithwright`, melee −0.5%/lvl, max 5%).
4. Yksi temporary food/potion-buff (varp/varbit + timer, ei PlayerStatMap-muutoksia).
5. Yksi armor/ammo upgrade.
6. NPC-avaus shopille: quartermaster-npc, jonka `Trade`-op kutsuu `ifOpenMainModal` — infra
   valmis, vain spawn + op-binding puuttuu (`::skillshop` toimii siihen asti).
7. Seuraavan skillin kytkentä samaan malliin (esim. Woodcutting tai Fishing): ks.
   `docs/pvm-skilling-implementation-notes.md` "Seuraavan skillin lisääminen".

## Riskit / huomiot

- ~~Item-token ei selviä restartista~~ — ratkaistu varp-saldolla (`skill_points` on persistent).
- `invAddOrDrop` pudottaa rewardin maahan, jos inventaario on täysi — nykyinen
  shop-konventio; dokumentoitu implementation-notesiin.
- `docs/pvm-skilling-architecture.md` löytyy nyt checkoutista; missä koodi ja dokumentti
  eroavat, koodi voittaa (ks. implementation-notesin "Ristiriidat").
