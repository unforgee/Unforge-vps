# PvM + skilling -koodiauditointi

Päivitetty 2026-09-17. Auditointi perustuu nykyiseen lähdekoodiin; käännettyjä `build/` ja `bin/`-hakemistoja ei ole käytetty lähteenä.

## Rajaus ja lähtötilanne

Projektin juuren `AGENTS.md` luettiin ensin. Pyydettyä `docs/pvm-skilling-architecture.md`-tiedostoa ei tässä checkoutissa ole, joten tämän dokumentin johtopäätökset perustuvat olemassa olevaan koodiin ja konfiguraatioihin. Auditointi ei muuta gameplayta.

## Yhteenveto

Arkkitehtuuri soveltuu PvM + skilling -kokonaisuuteen ilman isoa refactoria, jos uusi ominaisuus pidetään content/API-moduuleina ja käytetään olemassa olevia rajapintoja:

- skilling-toiminnot ovat moduulikohtaisia Kotlin-scriptejä ja konfiguraatioita;
- skillien XP ja levelit ovat keskitetysti `PlayerStatMap`-tilassa, jota `PlayerSkillXP` muokkaa;
- inventoryt ja shopit ovat valmiita item-/valuuttapohjaiseen talouteen;
- combatin attack roll, max hit, attribuutit ja hit processing ovat eriytettyjä ja testattuja;
- NPC:n combat-data on cache/Kronos-lähtöistä, mutta suunnitelman omat elementti- ja resistanssimekaniikat eivät vielä näy yleisenä datamallina;
- erillinen skilling point -tili, perk-rekisteri ja yleinen määräaikainen buffi puuttuvat.

Suositus on tehdä ensin yksi pieni pystysuuntainen proof-of-concept: yksi skilling-toiminto antaa XP:n lisäksi pisteitä, yksi shop käyttää pistevaluuttaa ja yksi passiivinen perk muuttaa vain PvN-damagea. Kaikki uudet state-arvot on rajattava erilliseen namespaceen/varpiin tai tilimalliin; nykyistä stat- tai combat-kaavaa ei pidä korvata.

## 1. Missä skillit määritellään?

Skillit eivät ole yksi keskitetty feature-paketti, vaan `content/skills/<skill>`-moduuleja. Esimerkkejä:

- `content/skills/mining/.../Mining.kt`, `MiningRocks.kt`, `MiningPicks.kt`, `MiningLevelBoosts.kt`;
- `content/skills/woodcutting/.../Woodcutting.kt`, `WoodcuttingLevelBoosts.kt` ja puu-/kirveskonfiguraatiot;
- `content/skills/fishing/FishingScript.kt` ja `FishingMethod.kt`;
- valmistusketjut käyttävät yhteistä `content/skills/skilling-core`-moduulia (`SkillMakeScript`, `MakeLoop`, `RecipeSelection`).

Skillin toiminta on siis content-skriptiä, reseptit/level-vaatimukset moduulin configia ja yleiset make-loop/transaction-idiomit skilling-corena. Uusi skilling-sisältö kannattaa lisätä omana moduulina tai olemassa olevan skillin sisään, ei engineen.

## 2. Missä levelit ja XP käsitellään?

Perusmalli on engine/API:ssa:

- `engine/game/.../stat/PlayerStatMap.kt` säilyttää pelaajan statit ja fine-XP:n;
- `engine/game/.../stat/PlayerSkillXPTable.kt` muuntaa XP:n leveliksi ja sisältää OSRS-tyyppisen XP-taulukon;
- `engine/game/.../entity/Player.kt` sisältää `statMap`-tilan;
- `api/player/.../stat/PlayerSkillXP.kt` tarjoaa XP:n lisäämisen ja rate-/fine-XP-logiikan;
- `api/player/.../stat/PlayerStatExtensions.kt` tarjoaa statin käsittelyapuja;
- `api/game-process/.../player/PlayerStatUpdateProcessor.kt` ja `api/player-output/.../UpdateStat.kt` välittävät muutokset clientille.

Combat manager myöntää combat-XP:tä osuman ja attackin perusteella. Skilling-toiminnon tulee kutsua nykyistä XP-polkuja, eikä kasvattaa statMapia suoraan.

## 3. Onko skilling point -valuutta jo olemassa?

Yleistä skilling point -tiliä ei löytynyt. Projektissa on kuitenkin kaksi käyttökelpoista perustaa:

- `engine/game/.../type/currency/CurrencyType.kt` ja `CurrencyTypeBuilder.kt` määrittelevät named currency -tyypin;
- `api/type/type-references/.../currency/CurrencyReferences.kt` ratkaisee currency-tyypit;
- shop-materiaaleissa on jo useita valuuttoja, esimerkiksi `MARKS_OF_GRACE`, `MAGE_ARENA_POINTS`, `APPRECIATION_POINTS`, `MOLCH_PEARLS` ja `SURVIVAL_TOKENS`.

Tämä tarkoittaa, että point shop -käyttöliittymä ja valuutan valinta ovat olemassa, mutta pisteiden ansainta, tallennus ja skilling-kohtainen sääntö puuttuvat. POC:ssa kannattaa käyttää uutta nimettyä `SKILLING_POINTS`-currencyä vain, jos sen taustalla oleva item-/purse-mekanismi voidaan osoittaa olemassa olevaksi. Muutoin turvallisin ensimmäinen toteutus on olemassa oleva item-valuutta tai erillinen persistent player field, jonka shop-operaatiot tukevat eksplisiittisesti.

## 4. Missä shopit ja itemit määritellään?

Runtime shop on `engine/game/.../shop/Shop.kt`: se sisältää inventoryn, valuutan ja osto-/myyntiprosentit. Shop-toiminnot ovat `api/shops`-moduulissa (`Shops`, `ShopkeeperScript`, `StandardShopOperations`, `StandardGpShopOperations`, restock ja cost-laskenta). Testit löytyvät `api/shops/src/test` ja `src/integration`.

Content-dataa on `content/kronos-data/shops/*.yaml`, esimerkiksi `Pvm_Point_Shop.yaml`, `Skilling_Supply.yaml` ja `Prospector_Percy's_Nugget_Shop.yaml`. Itemien metadata on pääosin `content/kronos-data/items/item_info.json`; runtime item type/cache tulee cache-tyypeistä ja type-reference/builders -moduuleista.

Uusi reward item lisätään dataan ja tarvittaessa named type/symbol-ketjuun AGENTS-ohjeen mukaisesti. Shopin valuutan tulee olla oikeasti resolvoitava `CurrencyType`, ei pelkkä YAML-nimi.

## 5. Missä combat damage, armor ja resistance lasketaan?

Damage syntyy useassa kerroksessa:

- attack roll ja max hit: `api/combat/combat-formulas`;
- melee/ranged/magic erikoistapaukset: `.../formulas/accuracy/*` ja `.../formulas/maxhit/*`;
- attribuutit: `.../formulas/attributes/*`, erityisesti `DamageReductionAttributes` ja collectorit;
- taistelun orkestrointi ja combat-XP: `api/combat/combat-manager/.../PlayerAttackManager.kt`;
- osuman soveltaminen: `api/player/.../hit/processor/*`, `api/npc/.../hit/*` sekä `api/hit-plugin`;
- combat-effectit: `api/combat/combat-effects`.

Armor ei ole yksi erillinen armor service: defence stats, equipment bonuses, prayer/varbitit ja NPC/player-specific formulae osallistuvat attack roll- ja max-hit-laskuihin. Damage reduction käyttää collector/attribute-mallia. Nykyinen rakenne tarjoaa hyvän extension pointin perkille: lisää modifier/attribute ennen lopullista hit processingia tai formula operation -vaiheeseen sen mukaan, onko vaikutus osumatodennäköisyyteen, max hitin kattoon vai toteutuneeseen damageen.

## 6. Consumablet ja temporary buffit

Inventoryn perusoperaatiot ovat `engine/game/.../inv/Inventory.kt` ja transaction/API-käyttö `api/invtx`, `api/inv-plugin` sekä skill-skripteissä. Ruoat, potions ja herblore-sisältö ovat content-scriptejä; yleistä, dokumentoitua temporary-buff serviceä ei löytynyt. Väliaikainen tila näyttää tyypillisesti toteutuvan varp/varbit- ja timer/attribute-tyylillä moduulikohtaisesti, ei yhtenäisenä buff API:na.

Siksi ensimmäinen perk/buff kannattaa toteuttaa olemassa olevalla player var/varbit + timer -mekanismilla tai pienellä API-moduulilla, joka kapseloi sen. Älä upota buffit inventory itemeihin tai muuta `PlayerStatMap`ia: kulutettava item kulutetaan transactionilla, vaikutus kirjataan player stateen ja expiry palauttaa tilan.

## 7. Perkit ilman isoa refactoria

Suositeltu malli on additive:

1. Perkien määrittely content-moduulin immutable configissa (`id`, kuvaus, ehdot, vaikutus).
2. Pelaajan unlockit tallennetaan erilliseen player stateen (POC:ssa varp/varbit; tuotannossa account/character persistence).
3. Combat-kohteessa käytetään olemassa olevaa attribute collectoria tai pientä `PerkModifier`-rajapintaa.
4. Modifier vaikuttaa vain valittuun vaiheeseen, esimerkiksi PvN max hit tai toteutunut elemental damage.
5. Testataan default-tila ja yksi unlocked-tila erikseen.

Tällä tavalla nykyiset kaavat säilyvät oletuksena identtisinä. Perk-rekisteriä ei kannata ensin rakentaa geneerisenä event-bussina; yksi typed modifier ja pieni config ovat pienempi riski.

## 8. PvM-bossien elementit ja damage-tyypit

Magicissa on jo elementtitaso (`content/skills/magic/spell-attacks/.../ElementalSpells.kt`) ja combatissa on melee/ranged/magic attack type -erottelu sekä demonbane/dragonbane/weapon/NPC-attribuutteja. En löytänyt yleistä bossi-elementti + target resistance -datamallia, joka yhdistäisi kaikki damage-lähteet yhteen.

Bossikohtainen sisältö on tällä hetkellä luontevimmin NPC-combat-dataa (`content/kronos-data/npcs/combat/*.json`) ja combat-skriptiä. Uusi järjestelmä tarvitsee erillisen `DamageType`/`Element`-arvon, bossin phase/resistance-datan ja yhden sovelluspaikan ennen hitin lopullista käsittelyä. POC:ssa rajaa tämä vain PvN:ään ja yhteen elementtiin; älä muuta PvP:tä tai kaikkia legacy-NPC:itä.

## 9. Valmiit ja puuttuvat osat

### Valmiina

- moduulipohjaiset skillit, reseptit, level-vaatimukset ja integration-testit;
- keskitetty XP/level-laskenta sekä clientin stat update;
- inventoryt, item stackit ja inv transaction -malli;
- shop runtime, useita currency typejä, YAML-shop-data ja shop-testit;
- combatin eri attack-lajit, PvN/PvP/NvP/NvN formulae, attribuutit ja hit processors;
- NPC combat data, drop data ja elementaaliset magic-spellit;
- varp/varbit- ja timer-tyyppiset rakennuspalikat.

### Puuttuu tai vaatii varmistuksen

- skilling point -ansainta, saldo, persistence ja client-näyttö;
- varma named currency / purse -integraatio juuri skilling-pointeille;
- yleinen consumable/buff lifecycle API;
- perk-unlockien persistence ja UI;
- yleinen elemental damage/resistance -malli bossille;
- PvM reward attribution, pointin myöntämisen idempotenssi ja exploit-suojat;
- end-to-end integration-test, jossa skill -> points -> shop -> item ja boss -> elemental hit kulkevat yhdessä.

## 10. Mitä suunnitelmasta pitää muuttaa nykyisen koodin vuoksi?

- Älä lisää skillitasoja tai XP:tä uuteen omaan progression-malliin; käytä `PlayerStatMap`/`PlayerSkillXP`-polkua.
- Älä mallinna skilling pointia aluksi irrallisena shopin erikoishaarana; päätä ensin onko se `CurrencyType`/item vai persistent player balance.
- Älä kytke perkkejä suoraan kaikkiin max-hit-formuloihin; käytä rajattua PvN-modifieria ja pidä default no-op.
- Älä käsittele resistancea armor-bonuksena. Elementti/resistance on oma damage-stage, jotta melee/ranged/magic ja boss phase voidaan erottaa.
- Älä toteuta consumable-buffia muuttamalla permanent stat XP:tä tai base leveliä; käytä tilapäistä statea ja timeria.
- Älä tee suurta engine-refactoria ennen yhtä vertical slice -POC:ia ja sen testejä.

## Suositeltu POC

Yksi turvallinen POC voi olla: Mining onnistuu nykyisellä scriptillä, antaa nykyisen Mining XP:n lisäksi pienen `SKILLING_POINTS`-saldon; yksi shop ostaa yhden uuden reward itemin tällä valuutalla; unlockattu `Ore Specialist` -perk lisää vain määritellyn PvN-mining/boss-damage modifierin; locked-tila on bit-tasolla no-op. POC:n acceptance testit tarkistavat XP:n, pisteen, shop-oston, inventory-muutoksen ja damage-modifierin default/unlocked-tapaukset. Jos currency-purse ei ole vielä olemassa, POC käyttää väliaikaista item-valuuttaa eikä pura shop-arkkitehtuuria.
