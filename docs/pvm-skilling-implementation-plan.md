# PvM + skilling -toteutussuunnitelma

Tämä on vaiheistettu suunnitelma auditin perusteella. Tavoite on säilyttää nykyinen gameplay ja minimoida muutosten blast radius.

## Periaatteet

- Aloita yhdestä vertical slicesta, älä rakenna koko perk-/bossijärjestelmää kerralla.
- Nykyinen XP, combat ja shop toimivat oletuksena muuttumattomina.
- Uusi state on namespacettu ja persistent vain sen jälkeen, kun POC:n elinkaari on todistettu.
- POC käyttää yhtä skill-toimintoa, yhtä currencyä, yhtä item-rewardia, yhtä perkkiä ja yhtä PvN-damage-modifieria.

## Vaihe 0: sopimukset ja avoimet päätökset

Päätä ennen koodausta:

1. Onko skilling point fyysinen item, olemassa oleva `CurrencyType`-purse vai uusi character-balance? Suositus: käytä nykyistä shopin tukemaa currency-polkuja; jos purse puuttuu, käytä väliaikaista item-valuuttaa.
2. Persistoidaanko perk POC:ssa varp/varbitillä vai character-tietokantaan? Suositus: varp/varbit vain kokeiluun, DB-kenttä tuotantoon.
3. Elementin vaikutus kohdistuu PvN max hit -vaiheeseen vai toteutuneeseen hit processingiin? Suositus: yksi PvN damage modifier ennen lopullista soveltamista.

## Tiedostokohtainen lista

| Tiedosto | Tarvittava muutos | Muutoksen syy | Riippuvuudet | Testit |
|---|---|---|---|---|
| `content/skills/mining/src/main/kotlin/org/rsmod/content/skills/mining/Mining.kt` | Lisää POC:n onnistuneen mining-toiminnon loppuun pisteen myöntö nykyisen XP:n jälkeen; pidä palkinto idempotenttina yhtä actionia kohden. | Todistaa skill -> XP + point -ketjun ilman uutta skill engineä. | Nykyinen mining action, inventory transaction, valittu currency/item. | Mining-script testi: onnistunut action antaa XP:n ja täsmälleen yhden pointin; epäonnistunut/puuttuva tool ei anna pointia. |
| `content/skills/mining/src/main/kotlin/org/rsmod/content/skills/mining/configs/MiningRocks.kt` | Lisää vain POC-rewardin config, jos palkinto kuuluu rock/action-määrittelyyn. | Vältetään kovakoodattua item-id:tä scriptissä. | Item reference/type resolver. | Config-validointi ja olemassa olevat mining config -testit. |
| `content/skills/skilling-core/src/main/kotlin/org/rsmod/content/skills/core/` | Lisää pieni typed helper, esimerkiksi `SkillingRewardService`, joka vastaanottaa playerin, rewardin ja source actionin. | Yhtenäistää palkinnon myönnön ilman geneeristä event-bussia. | `api/invtx`, valittu currency/persistence API. | Unit-testit: puuttuva tila, onnistunut reward, duplicate-kutsu. |
| `engine/game/src/main/kotlin/org/rsmod/game/type/currency/CurrencyType.kt` | Ei muutosta, jos olemassa oleva named currency voidaan käyttää; muussa tapauksessa lisää vain tarvittava metadata, ei shop-logiikan haaroja. | Runtime-malli on jo oikea extension point. | Currency builder/reference. | Nykyiset currency/reference-testit ja shop-testit. |
| `api/type/type-references/src/main/kotlin/org/rsmod/api/type/refs/currency/CurrencyReferences.kt` | Rekisteröi `SKILLING_POINTS` vain jos POC:n valittu purse-polku tukee sitä. | Shop tarvitsee resolvoitavan named typen. | Cache/type build pipeline, symbolit AGENTS-ohjeen mukaan. | Reference resolver -testi ja serverin type verification. |
| `content/kronos-data/shops/Skilling_Supply.yaml` tai uusi `Skilling_Point_Shop.yaml` | Lisää yksi reward item ja valittu currency; älä muuta olemassa olevia hintoja. | Todistaa point shopin ilman shop engine -muutosta. | Currency type, item id, shop loader. | Shop buy test: saldo vähenee, item tulee inventoryyn, insufficient funds estää oston. |
| `content/kronos-data/items/item_info.json` | Lisää vain uusi reward itemin metadata, jos itemiä ei jo ole. | Shop tarvitsee olemassa olevan item-definition. | Cache data / item type. | Item load/type test; ei massamuutoksia dataan. |
| `api/shops/src/main/kotlin/org/rsmod/api/shops/operation/StandardShopOperations.kt` | Ei muutosta ensivaiheessa. Muuta vasta, jos nykyinen currency ei tue valittua pursea; lisää silloin erillinen operation/testattu adapteri. | Nykyinen shop tukee named currencyä ja muutoksen riski on suuri. | `Shop`, `CurrencyType`, inventory transactions. | Existing buy/sell suite + uusi currency-specific suite. |
| `api/player/src/main/kotlin/org/rsmod/api/player/stat/PlayerSkillXP.kt` | Ei muutosta; käytä nykyistä API:a. | XP:n keskittäminen pitää progression yhtenäisenä. | `PlayerStatMap`. | Olemassa olevat XP- ja stat integration-testit. |
| `api/combat/combat-formulas/src/main/kotlin/org/rsmod/api/combat/formulas/attributes/` | Lisää yksi typed POC-attribuutti tai modifierin lukupiste, joka on default-arvoltaan no-op. | Perk pitää liittää olemassa olevaan collector-malliin, ei forkata kaavoja. | Player/NPC attributes, perk state. | Formula test: locked = nykyinen tulos, unlocked = odotettu delta. |
| `api/combat/combat-formulas/src/main/kotlin/org/rsmod/api/combat/formulas/maxhit/PvNMeleeMaxHit.kt` (tai vastaava valittu PvN-polku) | Lisää modifier vain valittuun PvN-laskennan viimeiseen vaiheeseen. Älä koske PvP/NvP/NvN-polkuun. | Rajaa blast radiusin ja todistaa perk-arkkitehtuurin. | Uusi POC-perk resolver, equipment/attack context. | PvN max-hit tests: default, unlocked, cap/rounding. |
| `api/combat/combat-effects/src/main/kotlin/org/rsmod/api/combat/effects/` | Käytä vain, jos perk vaikuttaa osuman jälkiefektiin; muuten jätä koskematta. | Elemental resistance ei kuulu consumable/effect-moduuliin automaattisesti. | Hit/effect pipeline. | Effect tests vain, jos muutos tehdään. |
| `api/player/src/main/kotlin/org/rsmod/api/player/hit/processor/StandardPlayerHitProcessor.kt` tai vastaava PvN-hit-polku | Ei ensimmäisessä POC:ssa, ellei resistance päätetä toteutuneen damage-vaiheen modifieriksi. | Vältetään duplicate-reduction ja vanhan armor-logiikan rikkominen. | Damage modifier contract. | Regression tests existing hit processor suite. |
| `content/kronos-data/npcs/combat/<boss>.json` | Lisää yhdelle POC-bossille vain uusi element/resistance-kenttä, jos loader ja schema hyväksyvät extensionin. Muussa tapauksessa pidä metadata content-scriptin mapissa. | Bossikohtainen data kuuluu NPC-combat-dataan, mutta yleinen schema puuttuu. | JSON loader, NPC combat config. | Load/validation test ja boss combat integration test. |
| `content/skills/magic/spell-attacks/src/main/kotlin/.../ElementalSpells.kt` | Ei muutosta ensimmäisessä POC:ssa; käytä olemassa olevaa spell element -tietoa vertailuna. | Elementaalinen spell-taso on jo olemassa. | Magic attack context. | Existing magic tests. |
| `api/core/src/main/kotlin/org/rsmod/api/core/module/StatModModule.kt` | Arvioi POC:n jälkeen, voiko määräaikaisen buffin rekisteröidä tähän; älä lisää geneeristä buff frameworkia ennen käyttötapausta. | State-modifierin elinkaari on parempi kapseloida kuin levittää scriptiin. | Player state, timer/event loop. | Expiry, reapply, logout/load regression tests. |
| `api/db/src/main/resources/db/migration/` | Lisää migration vasta tuotantopersistenssivaiheessa: perk unlockit, point balance ja mahdollinen buff state. | Varppi/varbit ei riitä pysyvään economy-stateen. | Character repository/loader/saver. | Migration up/down or fresh-db integration, save/load round trip. |
| `api/account/src/main/kotlin/org/rsmod/api/account/character/` | Lisää typed character model/repository fields tuotantopersistenssissä. | Economy ja unlockit pitää tallentaa server-authoritatively. | DB migration, account load/save. | Round-trip, default old-character compatibility, concurrent update test. |
| `api/player/src/main/kotlin/org/rsmod/api/player/output/` | Lisää client update vain, jos point saldo/perk UI kuuluu POC:n scopeen. | Gameplay voidaan todistaa ilman UI-muutosta. | Client varp/varbit/interface. | Output packet/client script regression. |
| `content/skills/mining/src/test` tai `src/integration` | Lisää vertical-slice testit. | Varmistaa, ettei reward myönny invalid actionista eikä duplikoidu. | Test fixture, fake player/inventory. | Unit + integration. |
| `api/shops/src/test` ja `src/integration` | Lisää valitun point-currency shopin osto- ja insufficient-funds-testit. | Shop on economy-raja, jonka pitää olla atominen. | Test item list, currency resolver. | Buy, sell restriction, restock unaffected. |
| `api/combat/combat-formulas/src/test` tai `src/integration` | Lisää perk modifierin default/unlocked ja elemental resistance -testit, jos schema toteutetaan. | Säilyttää vanhan combatin baseline-tulokset. | Formula fixture NPC/player. | PvN regression, PvP unchanged. |

## Vaiheistus

### Vaihe 1: read-only schema- ja API-varmistus

Varmista valitun currency-polun, type resolverin ja player state -persistenssin tarkat API:t. Jos `SKILLING_POINTS` ei ole oikea purse-tyyppi, valitse item-pohjainen POC eikä muuteta shop engineä.

### Vaihe 2: vertical slice

Toteuta yksi mining reward, yksi shop item ja yksi testattava saldo. Aja ensin moduulitason testit; build-sääntöjen mukaan aja `spotlessApply` ennen buildia.

### Vaihe 3: yksi perk

Lisää yksi unlockattu POC-perk, joka vaikuttaa vain PvN-polkuun. Locked/default-polun tuloksen pitää olla byte-for-byte tai vähintään numeerisesti sama kuin ennen muutosta.

### Vaihe 4: elemental boss -kokeilu

Vasta kun perk-ketju toimii, lisää yhdelle bossille yksi elementti/resistance. Valitse yksi sovellusvaihe, lisää regression-testit ja varmista, ettei PvP tai muut bossit muutu.

### Vaihe 5: tuotantopersistenssi ja UI

Siirrä point balance ja perk unlockit DB:hen, lisää migration ja old-character compatibility. Lisää client/UI vasta kun economy-sopimus on vakaa.

## Testi- ja hyväksymiskriteerit

- kaikki nykyiset `api/player`, `api/shops`, `api/combat/combat-formulas` ja muutetun skill-moduulin testit läpäisevät;
- mining action antaa XP:n täsmälleen nykyisellä tavalla;
- invalid action ei anna XP:tä, pointia tai itemiä;
- point reward ei duplikoidu yhden server-authoritative actionin uudelleenajoissa;
- shop käyttää oikeaa valuuttaa atomisesti;
- locked perk ja kaikki ei-POC-hyökkäykset tuottavat nykyisen tuloksen;
- POC-bossin element/resistance vaikuttaa vain sille määriteltyyn PvN-polkuun;
- vanhat pelaajat latautuvat puuttuvilla uusilla state-arvoilla default-arvoihin;
- ei tehdä suurta data- tai formula-migraatiota ennen näiden ehtojen täyttymistä.

## Mitä ei tehdä tässä vaiheessa

- ei uutta geneeristä progression engineä;
- ei kaikkien skillien pisteistämistä;
- ei kaikkien consumablejen tai buffien uudelleenkirjoitusta;
- ei globalia elemental resistance -refactoria;
- ei PvP-kaavojen muuttamista;
- ei client UI -uudistusta;
- ei laajaa NPC/item JSON -massamuokkausta.
