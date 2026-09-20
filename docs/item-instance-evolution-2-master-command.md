# Item Instance Evolution 2.0 — Master Command

Tämä dokumentti on Item Instance Evolution 2.0:n täydellinen toteutusmääräys.
Sitä tulee käsitellä yhtenäisenä arkkitehtuuri-, peli-, tietokanta-, client-,
turvallisuus-, testaus- ja julkaisusuunnitelmana.

Tavoite ei ole lisätä vain uusia affixeja. Tavoite on muuttaa nykyinen item
instance -järjestelmä pitkäikäiseksi, jäljitettäväksi ja turvalliseksi
pelijärjestelmäksi, jossa jokaisella tärkeällä itemillä on:

- yksiselitteinen identiteetti
- omistajuus ja binding-tila
- versionoitu tila
- kehitys- ja mastery-polku
- pelaajan tekemät valinnat
- täydellinen muutoshistoria
- deterministinen ja auditoitava random-roll
- turvalliset trade-, upgrade-, reforge- ja socket-operaatiot
- selitettävä vaikutus pelaajan lopullisiin stateihin
- palautus- ja virheenkorjauspolku

Kaikki alla olevat säännöt ovat server-authoritative. Client ei saa koskaan
olla lopullinen auktoriteetti itemin tilasta, hinnasta, onnistumisesta,
stat-arvosta, bindingistä, XP:stä tai omistajasta.

---

## 1. Nykyinen lähtötilanne

Nykyinen järjestelmä sisältää jo seuraavat rakennuspalikat:

- `EquipmentInstance`
- `EquipmentInstanceService`
- `EquipmentInstanceRepository`
- `EquipmentInstanceRegistry`
- rarity, tier, affixit ja socketit
- unique-effect ID:t
- roll seed
- item level ja quality
- locked affix -slotit
- reforge count ja reforge history
- skill affixit
- tietokantamigraatiot equipment instance -tauluille
- template–instance-validointi inventoryssä
- RuneLite-hover ja inspect-protokolla
- companion-stat-pipeline, joka pystyy hyödyntämään equipment instance -dataa

Evolution 2.0 ei saa luoda rinnakkaista `EquipmentInstanceV2`-järjestelmää.
Nykyistä service-, repository- ja registry-rakennetta laajennetaan siten,
että vanhat itemit voidaan ladata ja käyttää ilman käsin tehtyä resetointia.

Nykyinen data säilytetään yhteensopivana:

- vanha instance ID pysyy validina
- vanha item template pysyy samana
- vanha rarity ei muutu migraatiossa ilman eksplisiittistä balance-policyä
- vanhat affixit eivät katoa
- vanha reforge-historia säilytetään
- puuttuvat uudet kentät saavat turvallisen oletusarvon
- itemin ensimmäinen Evolution 2.0 -lataus luo tarvittavat baseline-eventit

---

## 2. Pakolliset suunnitteluperiaatteet

### 2.1 Server-authoritative

Kaikki seuraavat lasketaan palvelimella:

- itemin lopulliset affix-arvot
- rarity ja tier
- XP ja mastery
- evolution stage ja branch
- socket-validointi
- upgrade success/failure
- reforge-tulos
- material- ja currency-kustannus
- binding
- trade-kelpoisuus
- itemin tuhoaminen
- itemin palauttaminen

Client saa lähettää intentin, ei lopputulosta. Esimerkiksi client lähettää
`REQUEST_REFORGE(instanceId, recipeId, expectedRevision, idempotencyKey)`,
mutta serveri päättää lopputuloksen.

### 2.2 Yksi domain-malli

Combat, inventory, trade, crafting, UI, analytics ja admin-työkalut käyttävät
samaa canonical item snapshotia. Mikään järjestelmä ei saa rakentaa omia
osittaisia item-malleja, joissa affixien tai bindingin merkitys tulkitaan
eri tavalla.

### 2.3 Snapshot + event history

Snapshot on nopeaa lukemista varten. Event history on auditointia, rollbackia,
debuggausta ja pelaajan item-historian näyttämistä varten.

Snapshotia voi päivittää vain validin mutationin kautta. Suoraa kenttien
muokkausta repositoryssä ei sallita.

### 2.4 Deterministinen random

Kaikki random-rollit perustuvat eksplisiittiseen seed-arvoon ja nimettyyn
policy-versioon. Rollin on oltava toistettavissa testissä samoilla syötteillä.

### 2.5 Atomicity

Itemin, inventaarion, materiaalien, valuutan, eventin ja ownershipin muutos
tehdään yhdessä transaktiossa tai ei lainkaan.

### 2.6 Explainability

Pelaajan pitää pystyä näkemään, mistä jokainen merkittävä stat-arvo syntyy.
Debug-työkalun pitää pystyä selittämään sama laskenta yksityiskohtaisemmin.

### 2.7 Backward compatibility

Evolution 2.0 ei saa rikkoa plain-itemeitä, stackable-itemeitä, vanhaa
inventory-dataa, vanhaa clientia tai aiempia item instance -migraatioita.

---

## 3. Canonical domain-malli

### 3.1 Vahvat ID-tyypit

Korvaa pitkällä aikavälillä paljaat `Long`-arvot vahvoilla tyypeillä:

```kotlin
@JvmInline
value class ItemInstanceId(val value: Long)

@JvmInline
value class ItemTemplateId(val value: Int)

@JvmInline
value class ItemRevision(val value: Long)
```

Tavoite on estää tilanteet, joissa template ID, instance ID, character ID ja
slot-numero siirtyvät väärään metodiin vain siksi, että kaikki ovat numeroita.

### 3.2 Instance UUID

Jokaiselle itemille lisätään UUID `instance_uuid`.

Autoincrement-ID on tehokas tietokantaindeksiä varten. UUID on ulkoinen,
globaalisti yksiselitteinen identiteetti auditointia, client-debuggausta,
shardien välistä siirtoa ja mahdollisia migraatioita varten.

UUID ei korvaa nykyistä primary keytä tässä vaiheessa.

### 3.3 Omistajuus

Omistajuus erotetaan itemin bindingistä:

```kotlin
data class ItemOwner(
    val accountId: Long?,
    val characterId: Long?,
)
```

Sallitut invarianssit:

- accountId tai characterId on asetettu binding-säännön mukaisesti
- character-bound itemillä on aina characterId
- account-bound itemillä on accountId mutta ei pakollisesti characterId
- unbound itemillä ei ole pysyvää omistajaa
- serverin inventory-paikka ei yksinään määritä omistajuutta

### 3.4 Binding-tilat

```kotlin
enum class ItemBinding {
    UNBOUND,
    ACCOUNT_BOUND,
    CHARACTER_BOUND,
    TRADE_LOCKED,
    QUEST_BOUND,
    TEMPORARILY_BOUND,
    DESTROYED,
}
```

Binding siirtyy vain keskitetyn state machine -palvelun kautta. Esimerkiksi
`TRADE_LOCKED` ei saa olla pysyvä lopputila, vaan se palautuu onnistuneen tai
perutun traden jälkeen määritellyllä tavalla.

### 3.5 Instance state

```kotlin
enum class EquipmentInstanceState {
    ACTIVE,
    BROKEN,
    REPAIRING,
    IN_UPGRADE,
    IN_TRADE,
    RETIRED,
    DESTROYED,
    CORRUPTED,
}
```

State estää samanaikaisia ristiriitaisia operaatioita. Esimerkiksi itemiä ei
saa reforgata, jos se on jo `IN_TRADE` tai `IN_UPGRADE`.

### 3.6 Canonical snapshot

Uusi snapshot sisältää vähintään:

```kotlin
data class EquipmentInstanceSnapshot(
    val instanceId: ItemInstanceId,
    val instanceUuid: UUID,
    val templateObj: ItemTemplateId,
    val category: EquipmentCategory,
    val rarity: EquipmentRarity,
    val tier: EquipmentTier,
    val state: EquipmentInstanceState,
    val binding: ItemBinding,
    val owner: ItemOwner?,
    val rollSeed: Long,
    val schemaVersion: Int,
    val balanceVersion: Int,
    val itemLevel: Int,
    val masteryLevel: Int,
    val experience: Long,
    val quality: Int,
    val affixes: List<EquipmentAffixRoll>,
    val sockets: List<EquipmentSocket>,
    val uniqueEffects: List<UniqueEffectState>,
    val lockedAffixSlots: Set<Int>,
    val reforgeCount: Int,
    val evolution: ItemEvolutionState,
    val lineage: ItemLineage,
    val revision: ItemRevision,
    val fingerprint: String,
)
```

### 3.7 Revision

Jokaisella muutoksella revision kasvaa yhdellä. Mutation hyväksytään vain, jos
clientin `expectedRevision` vastaa nykyistä revisionia.

Tämä estää kaksi selain- tai client-näkymää muokkaamasta samaa itemiä vanhan
tiedon perusteella.

---

## 4. Lineage ja auditointi

### 4.1 Lineage

```kotlin
data class ItemLineage(
    val creationSource: String,
    val parentInstanceIds: List<ItemInstanceId>,
    val recipeId: String?,
    val dropId: String?,
    val createdAt: Instant,
)
```

Lineage kertoo, syntyikö item dropista, craftauksesta, questista, rewardista,
fuusiosta vai admin-operaatiosta.

### 4.2 Mutation event

Jokainen muutos tuottaa tapahtuman:

```kotlin
data class ItemMutationEvent(
    val eventId: UUID,
    val instanceId: ItemInstanceId,
    val operation: ItemMutationOperation,
    val actorType: ActorType,
    val actorId: Long?,
    val source: String,
    val beforeRevision: ItemRevision,
    val afterRevision: ItemRevision,
    val beforeFingerprint: String,
    val afterFingerprint: String,
    val payload: String,
    val createdAt: Instant,
)
```

### 4.3 Event-tyypit

Vähintään seuraavat eventit:

- `CREATED`
- `MIGRATED`
- `BOUND`
- `UNBOUND`
- `XP_GAINED`
- `LEVEL_UP`
- `MASTERY_CHANGED`
- `EVOLVED`
- `BRANCH_CHANGED`
- `AFFIX_ROLLED`
- `AFFIX_LOCKED`
- `AFFIX_UNLOCKED`
- `AFFIX_EXTRACTED`
- `AFFIX_TRANSFERRED`
- `SOCKET_INSERTED`
- `SOCKET_REMOVED`
- `SOCKET_REROLLED`
- `GEM_UPGRADED`
- `REFORGED`
- `UPGRADED`
- `UPGRADE_FAILED`
- `CORRUPTED`
- `REPAIRED`
- `TRADE_LOCKED`
- `TRADED`
- `DESTROYED`
- `RESTORED`
- `ADMIN_CORRECTION`

### 4.4 Audit invariants

Event-lokin on oltava järjestetty revision mukaan. Tapahtuman
`beforeFingerprint` on vastattava edellisen snapshotin fingerprintiä ja
`afterFingerprint` uuden snapshotin fingerprintiä.

### 4.5 Tamper-evident hash chain

Jokainen event voi sisältää edellisen eventin hashin. Tällöin yksittäisen
eventin jälkikäteinen muuttaminen havaitaan tarkistusajolla.

---

## 5. Database-muutokset

Lisää uusi migraatioperhe nykyisten equipment instance -migraatioiden jälkeen.
Älä muokkaa vanhoja jo julkaistuja migraatioita.

### 5.1 Päätaulun uudet kentät

`equipment_instances` tarvitsee vähintään:

- `instance_uuid TEXT NOT NULL UNIQUE`
- `state TEXT NOT NULL DEFAULT 'ACTIVE'`
- `binding TEXT NOT NULL DEFAULT 'UNBOUND'`
- `owner_account_id INTEGER`
- `owner_character_id INTEGER`
- `item_level INTEGER NOT NULL DEFAULT 1`
- `mastery_level INTEGER NOT NULL DEFAULT 0`
- `experience INTEGER NOT NULL DEFAULT 0`
- `quality INTEGER NOT NULL DEFAULT 100`
- `evolution_stage INTEGER NOT NULL DEFAULT 0`
- `evolution_branch TEXT`
- `revision INTEGER NOT NULL DEFAULT 0`
- `fingerprint TEXT NOT NULL`
- `lineage_source TEXT NOT NULL DEFAULT 'legacy-migration'`
- `created_at`
- `updated_at`

### 5.2 Affixien täydentäminen

Affix-tauluun lisätään:

- `definition_id`
- `family`
- `stat`
- `unit`
- `polarity`
- `magnitude`
- `quality_percent`
- `is_locked`
- `source_event_id`

Vanhoista affixeista `quality_percent` alustetaan policyllä, joka ei muuta
niiden tehoa. Käytännössä vanha magnitude säilytetään ja quality näkyy aluksi
`LEGACY`-tilana tai lasketaan vain informatiivisesti.

### 5.3 Socketien täydentäminen

Socket-tauluun lisätään:

- `socket_type`
- `socket_state`
- `gem_instance_id`
- `quality_percent`
- `source_event_id`

### 5.4 Evolution-taulu

Luo erillinen taulu, jos evolution-historiaa halutaan kysellä tehokkaasti:

```sql
CREATE TABLE equipment_instance_evolution (
    equipment_instance_id INTEGER PRIMARY KEY,
    stage INTEGER NOT NULL DEFAULT 0,
    branch TEXT,
    unlocked_milestones TEXT NOT NULL DEFAULT '[]',
    last_evolved_at TIMESTAMP,
    FOREIGN KEY (equipment_instance_id)
        REFERENCES equipment_instances(id) ON DELETE CASCADE
);
```

### 5.5 Event-taulu

```sql
CREATE TABLE equipment_instance_events (
    event_id TEXT PRIMARY KEY,
    equipment_instance_id INTEGER NOT NULL,
    operation TEXT NOT NULL,
    actor_type TEXT NOT NULL,
    actor_id INTEGER,
    source TEXT NOT NULL,
    before_revision INTEGER NOT NULL,
    after_revision INTEGER NOT NULL,
    before_fingerprint TEXT NOT NULL,
    after_fingerprint TEXT NOT NULL,
    payload TEXT NOT NULL,
    previous_event_hash TEXT,
    event_hash TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (equipment_instance_id)
        REFERENCES equipment_instances(id) ON DELETE CASCADE
);
```

### 5.6 Indeksit

Lisää indeksit:

- instance UUID
- owner character
- owner account
- state
- binding
- fingerprint
- event instance + revision
- event created_at
- template + rarity

### 5.7 Tietokantainvariantit

Tietokanta ja repository varmistavat vähintään:

- instance ID ei voi olla negatiivinen
- template ID vastaa inventoryn templatea
- experience ei ole negatiivinen
- item level on sallitulla alueella
- quality on 0–100
- evolution stage on catalogissa tunnettu
- revision ei pienene
- destroyed item ei voi olla aktiivisessa inventoryssä
- binding ei ole ristiriidassa owner-kenttien kanssa

---

## 6. XP-, level- ja mastery-järjestelmä

### 6.1 XP-lähteet

Sallitut XP-lähteet määritellään data-driven catalogissa:

- itemin käyttö combatissa
- tehokas damage
- tehokas healing
- block/mitigation
- boss encounter
- skilling-action
- recipe completion
- quest milestone
- item-specific objective
- controlled training activity

Pelkkä itemin mukana pitäminen ei anna XP:tä.

### 6.2 XP-validointi

XP-tapahtuma hylätään, jos:

- item ei ole pelaajan omistuksessa
- item on destroyed, retired tai corrupted ilman sallittua poikkeusta
- event source ei ole sallittu
- action ei liity itemin kategoriaan
- item ei ollut käytössä tapahtuman aikana
- sama event ID on jo käsitelty
- XP ylittää operation capin
- action on self-inflicted tai botin generoima epävalidi tapahtuma

### 6.3 Item level

Item levelin pitää perustua canonical XP-käyrään. Käyrä ei saa käyttää eri
kaavaa eri palvelinpoluissa.

Määrittele:

- minimilevel
- maksimilevel
- XP per level
- carry-over XP level-upissa
- offline- tai idle-XP:n kielto
- level capin nostopolku

### 6.4 Mastery

Mastery kuvaa pelaajan suhdetta item-tyyppiin tai item-haaraan. Mastery voidaan
pitää item-kohtaisena, mutta sen lähde ja käyttö pitää erottaa item XP:stä.

Suositus:

- item XP = tämän konkreettisen itemin historia
- mastery = pelaajan pitkäaikainen osaaminen kyseisessä item familyssa

### 6.5 XP anti-abuse

Lisää:

- encounter-kohtainen cap
- target diversity -kerroin
- low-value action -rajoitus
- diminishing returns
- aikavälikohtainen cap
- bottausta havaitseva regularity-metriikka
- logitus jokaisesta poikkeavasta XP-piikistä

---

## 7. Evolution-järjestelmä

### 7.1 Evolution-stage

Stage on data-driven. Esimerkiksi:

| Stage | Gate | Päävaikutus |
|---|---:|---|
| 0 | lähtötila | base item |
| 1 | level 10 | ensimmäinen passive |
| 2 | level 25 | uusi affix- tai socket-ominaisuus |
| 3 | level 50 | branch-valinta |
| 4 | level 75 | signature effect |
| 5 | level 100 | endgame mastery |

Tarkat arvot eivät saa olla kovakoodattuna combat-kutsuihin.

### 7.2 Evolution gate

Evolve-operaatio tarkistaa:

- itemin omistajuuden
- itemin tilan
- riittävän item levelin
- riittävän mastery-tason
- quest- tai achievement-vaatimukset
- materiaalit
- valuutan
- cooldownin
- branchin yhteensopivuuden
- ettei stagea ole jo saavutettu

### 7.3 Evolution branch

Branchit ovat catalog-määritelmiä. Jokainen branch kertoo:

- sallitut equipment-kategoriat
- avautuvat statit
- avautuvat effectit
- incompatibility-säännöt
- respec-hinnan
- respecin mahdollisen rajoituksen
- client-kuvauksen
- balance-version

### 7.4 Evolutionin atomisuus

Evolution kuluttaa materiaalit ja nostaa stagen samassa transaktiossa. Jos
eventin kirjoitus epäonnistuu, myös stage ja materiaalit palautetaan.

### 7.5 Evolution rollback

Rollback ei saa olla vapaa statin uudelleenpyöritys. Se on admin- tai
erityinen palautusoperaatio, joka käyttää alkuperäisiä snapshot- ja event-tietoja.

---

## 8. Affix-järjestelmä 2.0

### 8.1 Affix-definition

Jokainen affix määrittelee:

- id
- family
- stat
- unit
- polarity
- min/max magnitude
- quality scaling
- rarity-rajoituksen
- tier-rajoituksen
- category-rajoituksen
- conflict familyt
- allowed evolution stage
- conditional triggerin
- balance-version

### 8.2 Affix quality

Quality kertoo, kuinka lähellä roll on sallittua maksimia. Se ei saa
automaattisesti muuttaa vanhan itemin käytännön voimaa, ellei balance-policy
ole eksplisiittisesti hyväksytty.

### 8.3 Affix family

Itemillä voi olla enintään yksi affix samasta family-perheestä, ellei catalog
erikseen salli stackausta.

### 8.4 Affix conflict

Konfliktit tarkistetaan ennen random-rollia. Näin rolleria ei tarvitse yrittää
korjata jälkikäteen epävalidin tuloksen syntymisen jälkeen.

### 8.5 Conditional affix

Condition määritellään rakenteellisena policy-objektina, ei vapaana tekstinä.
Esimerkiksi `BOSS_TARGET` ja `LOW_HEALTH` ovat enum-arvoja, joille combatissa
on yksi canonical toteutus.

### 8.6 Proc-affix

Proc-affix tarvitsee:

- proc chance
- internal cooldown
- charge count
- event source
- target scope
- stack policy
- combat-log message
- telemetry event

Proc ei saa suoraan muuttaa HP:tä tai statteja ohittaen keskitetyn combat API:n.

### 8.7 Affix locking

Locked slot säilyy mutationissa vain, jos upgrade policy sallii sen. Jokainen
lukitus kirjataan kustannuksella, actorilla ja eventillä.

### 8.8 Affix extraction

Extraction muodostaa erillisen affix itemin tai essence-objektin. Extractionin
pitää säilyttää alkuperäinen source instance, quality, balance-version ja
rajoitukset.

### 8.9 Affix transfer

Transfer tarkistaa:

- lähde- ja kohdeitemin categoryn
- affix familyn
- rarityn
- item levelin
- source bindingin
- target bindingin
- transfer costin
- mahdollisen quality lossin

---

## 9. Socketit ja gemit

### 9.1 Socket-tyypit

Socket-tyypit ovat data-driven:

- `OFFENSIVE`
- `DEFENSIVE`
- `UTILITY`
- `SKILL`
- `PRISMATIC`
- `CORRUPTED`

### 9.2 Gem instance

Socketed object ei saa jäädä pelkäksi `Int`-arvoksi, jos gemillä on omaa
progressiota. Gem instance tarvitsee vähintään:

- gem template
- gem instance ID
- level
- quality
- effect definition
- binding
- source
- revision

### 9.3 Socket insertion

Insertion validoi:

- socket slotin
- gemin tyypin
- itemin ownerin
- gemin ownerin
- gemin bindingin
- socketin tyypin
- mahdollisen duplicate-effectin
- materiaalikustannuksen

### 9.4 Socket removal

Removal policy kertoo, säilyykö gem, menettääkö se qualitya vai tuhoutuuko se.
Tulos ilmoitetaan pelaajalle ennen vahvistusta.

### 9.5 Socket synergy

Gem-yhdistelmä käsitellään samalla tavalla kuin set bonus: canonical resolver,
locked tiers, active tiers ja breakdown.

---

## 10. Rarity, tier ja balance

### 10.1 Erota kolme käsitettä

- tier = materiaalinen tai alkuperäinen equipment-luokka
- rarity = kuinka harvinainen itemin kokonaisroll on
- quality = kuinka hyvä yksittäinen roll on

Näitä ei saa käyttää toistensa synonyymeinä.

### 10.2 Budget

Jokaisella itemillä on kokonaisbudjetti. Affixien, socketien, unique-effectien
ja evolution-bonusten yhteisvaikutus ei saa ylittää policyssä määritettyä
budjettia ilman eksplisiittistä endgame-poikkeusta.

### 10.3 Balance-version vaikutus

Balance policy kertoo, käytetäänkö:

- snapshot-arvoa
- live-arvoa
- grandfathered-arvoa
- season-kohtaista arvoa

Kaikki muutokset kirjataan balance-eventiksi tai policy-version vaihdoksi.

### 10.4 Balance-regressiot

Tallenna representative seed -fixturet jokaiselle raritylle, tierille,
categorylle ja evolution branchille. Muutokset, jotka muuttavat jakaumia,
vaativat tarkoituksellisen hyväksynnän.

---

## 11. Crafting, upgrade ja reforge

### 11.1 Mutation contract

Jokainen mutaatio määritellään contractina:

- input state
- vaadittu omistajuus
- vaadittu item state
- recipe
- cost
- RNG policy
- success output
- failure output
- rollback behavior
- event type
- client preview

### 11.2 Idempotency

Jokainen client-operaatio sisältää idempotency keyn. Sama key palauttaa saman
lopputuloksen eikä kuluta materiaaleja uudestaan.

### 11.3 Expected revision

Vanhaan snapshotiin perustuva mutation hylätään `REVISION_CONFLICT`-virheenä.
Client hakee tuoreen snapshotin ja näyttää pelaajalle uuden tilanteen.

### 11.4 Safe upgrade

Safe upgrade maksaa enemmän mutta ei tuhoa itemin progressiota.

### 11.5 Risky upgrade

Risky upgrade voi epäonnistua, menettää materiaaleja, laskea qualitya tai
lisätä corruptionia. Kaikki seuraukset näkyvät previewssä ennen vahvistusta.

### 11.6 Pity protection

Pity counter on item- tai pelaajakohtainen policy-kenttä. Se ei saa perustua
clientin paikalliseen laskuriin.

### 11.7 Reforge

Reforge käyttää:

- seedin johdannaista
- reforge policy -versiota
- locked slot -sääntöjä
- rarity rules
- family conflict rules
- item budgetia
- pity-sääntöä
- event-logia

---

## 12. Trade, inventory ja talous

### 12.1 Trade lock

Trade-ikkunan avaaminen asettaa väliaikaisen lockin. Lockilla on owner,
trade-session ID, expires-at ja revision.

### 12.2 Atomic trade

Trade päivittää molempien pelaajien inventoryt, item ownershipin ja eventit
yhdessä transaktiossa.

### 12.3 Trade validation

Trade hylätään, jos item:

- on character-bound
- on quest-bound
- on destroyed
- on jo toisessa trade-sessionissa
- ei ole alkuperäisen omistajan inventoryssä
- revision ei vastaa trade-sessionin revisionia
- fingerprint ei vastaa snapshotia

### 12.4 Duplikaation esto

Seuraa UUID:tä, primary keytä, event chainia ja inventory-linkitystä.
Yksikään item ei saa esiintyä kahdessa aktiivisessa paikassa samanaikaisesti.

### 12.5 Economic telemetry

Seuraa:

- syntyneet itemit
- tuhoutuneet itemit
- rarity-jakaumat
- average quality
- reforge count
- upgrade success rate
- materiaalien kulutus
- itemien siirtyminen pelaajien välillä
- korruptoituneiden itemien määrä

---

## 13. Client-protokolla ja UI

### 13.1 Protokollaversio

Nykyinen delimiter-pohjainen protokolla säilytetään legacy-yhteensopivuutta
varten, mutta uusi protokolla versionoidaan ja escape-suojataan.

Suositeltu envelope:

```text
UNFORGE_ITEM_INSTANCE|4|upsert|<revision>|<payload>
```

Payloadin tulee olla rakenteellinen, pituusrajattu ja turvallisesti parsittava.

### 13.2 Viestityypit

Vähintään:

- `upsert`
- `remove`
- `clear`
- `patch`
- `revision`
- `error`
- `stale`

### 13.3 Partial update

Älä lähetä koko item-historyä jokaisessa hover-päivityksessä. Lähetä vain
muuttunut section, ellei clientin revision ole vanhentunut.

### 13.4 Inspect-paneeli

Paneelin osiot:

1. Summary
2. Rarity/tier/level
3. Item XP ja mastery
4. Affixit
5. Socketit
6. Unique effects
7. Evolution
8. Set- ja synergy-bonukset
9. Stat breakdown
10. Lineage
11. Historia
12. Warnings

### 13.5 Stat breakdown

Näytä jokaisen lopullisen statin lähteet:

- base
- template
- tier
- rarity
- affix
- socket
- unique effect
- set
- evolution
- companion/talent/progression
- cap

### 13.6 Client-safety

Client ei saa olettaa, että:

- kaikki affixit ovat vanhan pituisia
- kaikki socketit sisältävät objektin
- history on aina olemassa
- schema-versiona on tietty numero
- enum-arvo on tunnettu
- item tulee järjestyksessä

Tuntematon data ohitetaan turvallisesti ja version mismatch näytetään vain
diagnostiikassa, ei kaatumisena.

---

## 14. Stat-laskenta ja combat-integraatio

### 14.1 Yksi canonical calculator

Kaikki itemin vaikutukset kulkevat yhden calculatorin läpi. Combatissa,
inspectissä, previewssä ja debug-komennossa käytetään samaa laskentapolkua.

### 14.2 Modifikaattorin yksikkö

Jokainen modifier ilmoittaa yksikkönsä:

- flat integer
- basis points
- percentage
- multiplier
- duration ticks
- chance basis points

Yksiköitä ei saa päätellä affixin nimestä.

### 14.3 Cap-järjestelmä

Capit määritellään keskitetysti. Combat-koodi ei sisällä omia irrallisia
CDR-, DR-, crit-, lifesteal- tai attack-speed-lukuja.

### 14.4 Stacking

Jokaiselle statille määritellään stacking policy:

- additive
- multiplicative
- highest-only
- diminishing returns
- mutually exclusive

### 14.5 Explainability API

Calculator palauttaa sekä lopullisen arvon että breakdownin. Tämä vähentää
debuggausaikaa ja estää clientin ja serverin eri tulokset.

---

## 15. Turvallisuus ja väärinkäytön esto

Pakolliset tarkistukset:

1. actor omistaa itemin
2. item on oikeassa inventoryssä
3. itemin template vastaa inventoryn templatea
4. item ei ole lukittu toiseen operaatioon
5. revision on tuore
6. idempotency key ei ole käytetty eri payloadiin
7. materiaaleja on tarpeeksi
8. valuuttaa on tarpeeksi
9. itemin binding sallii operaation
10. recipe on nykyisen balance-version mukainen
11. RNG server-side
12. event kirjoitetaan ennen onnistumisen ilmoittamista
13. client ei saa valita magnitudea
14. client ei saa valita raritya
15. client ei saa valita owneria
16. client ei saa ohittaa cooldownia
17. admin-operaatio tarvitsee audit reasonin
18. epäonnistuneet yritykset kirjataan ilman arkaluonteista dataa

Rate limiting lisätään ainakin reforge-, inspect-, trade-, upgrade- ja
evolve-operaatioihin.

---

## 16. Repository- ja service-rajapinnat

`EquipmentInstanceRepository` laajennetaan seuraavilla konsepteilla:

- `loadSnapshot(instanceId)`
- `loadByUuid(uuid)`
- `loadOwnedByCharacter(characterId)`
- `appendEvent(event)`
- `saveSnapshotWithRevision(snapshot, expectedRevision)`
- `loadHistory(instanceId, cursor, limit)`
- `validateFingerprint(instanceId)`
- `restoreFromEvent(instanceId, eventId)`

`EquipmentInstanceService` saa mutation API:t:

- `gainExperience`
- `evolve`
- `changeBranch`
- `reforge`
- `upgrade`
- `insertGem`
- `removeGem`
- `rerollSocket`
- `extractAffix`
- `transferAffix`
- `bind`
- `repair`
- `retire`
- `restore`

Jokainen palauttaa rakenteisen resultin:

```kotlin
sealed interface ItemMutationResult {
    data class Success(val snapshot: EquipmentInstanceSnapshot) : ItemMutationResult
    data class RevisionConflict(val current: EquipmentInstanceSnapshot) : ItemMutationResult
    data class ValidationError(val code: String, val details: Map<String, String>) : ItemMutationResult
    data class CostError(val missing: List<CostPart>) : ItemMutationResult
    data class InternalFailure(val requestId: UUID) : ItemMutationResult
}
```

Älä käytä pelkkää `Boolean`-paluuarvoa item-mutaatioissa. Pelaajan ja adminin
on saatava eroteltu virhekoodi.

---

## 17. Invariant checker

Implementoi erillinen tarkistus, jonka voi ajaa:

- itemin luonnin jälkeen
- jokaisen mutationin jälkeen debug-tilassa
- admin-komennolla
- taustajobina
- migraation jälkeen

Checker tarkistaa:

- snapshotin schema-validiteetin
- rarityn affix/socket-countit
- duplicate familyt
- socket-tyypit
- bindingin ja ownerin
- revision sequence
- fingerprintin
- event-chainin
- inventory-linkityksen
- parent-lineagen
- destroyed/active-ristiriidan
- balance-versionin
- evolution-stage gate -säännöt
- item XP:n rajat

Checker ei saa automaattisesti korjata dataa tuotannossa. Se raportoi ensin;
korjaus on erillinen, auditoitu admin-operaatio.

---

## 18. Testausmääräys

### 18.1 Unit-testit

Testaa vähintään:

- validit ja invalidit snapshotit
- affix family -konfliktit
- rarity countit
- socket-validointi
- XP-käyrä
- evolution gate
- branch-säännöt
- binding state machine
- fingerprintin determinismi
- modifier stacking
- cap-laskenta
- reforge policy
- upgrade policy

### 18.2 Property-based-testit

Kaikilla sallitulla seed-arvoilla:

- roll tuottaa validin instance-mallin
- magnitude pysyy rajoissa
- duplicate family ei synny
- item budget ei ylity ilman policy-poikkeusta
- snapshot serialisoituu ja palautuu samana

### 18.3 Migration-testit

Luo fixturet ainakin:

- täysin vanhasta itemistä
- itemistä ilman socket-taulua
- itemistä ilman skill-affixeja
- itemistä, jossa reforge history on tyhjä
- itemistä, jossa rarityssä on vanha affix count
- virheellisestä itemistä
- jo osittain migroidusta itemistä

### 18.4 Concurrency-testit

Testaa samanaikaisesti:

- reforge + trade
- upgrade + logout
- socket insert + destroy
- XP gain + evolve
- kaksi samaa idempotency requestia
- kaksi eri requestia samalla revisionilla

### 18.5 Crash-testit

Simuloi katkos:

- ennen materiaalien kulutusta
- materiaalien kulutuksen jälkeen
- ennen snapshotia
- snapshotin jälkeen
- eventin ennen
- eventin jälkeen
- client-notifikaation ennen

Missään tapauksessa itemin tai valuutan ei pidä kadota.

### 18.6 Client-protokollatestit

Testaa:

- legacy-message
- version 4 message
- patch-message
- stale revision
- malformed payload
- liian pitkä payload
- unknown field
- unknown enum
- duplicate event
- remove ja clear

---

## 19. Observability

Lisää metriikat:

- `item_instance_load_latency`
- `item_instance_cache_hit_rate`
- `item_mutation_success_total`
- `item_mutation_failure_total`
- `item_revision_conflict_total`
- `item_duplicate_detected_total`
- `item_fingerprint_mismatch_total`
- `item_migration_failure_total`
- `item_upgrade_success_rate`
- `item_reforge_distribution`
- `item_evolution_stage_distribution`
- `item_affix_quality_distribution`
- `item_trade_failure_total`
- `item_event_append_latency`

Jokaisessa mutaatiossa on `requestId`, jotta serveriloki, tietokanta-event,
client-viesti ja admin-debug voidaan yhdistää toisiinsa.

Älä kirjaa lokiin tarpeettomasti koko payloadia tai salaisuuksia. Käytä
fingerprintiä, instance UUID:tä ja operation-koodia.

---

## 20. Admin- ja debug-komennot

Lisää vähintään:

- `::iteminspect <id>` — canonical snapshot
- `::itemhistory <id>` — event history
- `::itemvalidate <id>` — invariant report
- `::itemfingerprint <id>` — fingerprint ja mismatchit
- `::itemroll <template> <seed>` — deterministinen simulation
- `::itemstats <id>` — breakdown
- `::itemevolve <id>` — vain server-validoinnin kautta
- `::itemrestore <id> <event>` — auditoitu restore
- `::itemlineage <id>` — parentit ja creation source
- `::itembalance <id>` — balance/schema policy
- `::itemrepair <id>` — vain eksplisiittinen admin-korjaus

Jokainen admin-komento vaatii käyttöoikeuden, kirjoitetun syyn ja audit-eventin.

---

## 21. Julkaisustrategia

### Vaihe 0 — inventaario

- listaa nykyiset equipment instance -kirjoittajat
- listaa kaikki load-polut
- listaa kaikki client-viestit
- listaa kaikki inventoryn template-validoinnit
- varmista, ettei `bin/`-tiedostoja muokata lähdekoodin sijasta
- kirjoita baseline-metriikat

### Vaihe 1 — read-compatible schema

- lisää uudet nullable/default-kentät
- backfill UUID, revision ja fingerprint
- luo baseline migration eventit
- pidä vanha kirjoituspolku toimivana

### Vaihe 2 — canonical snapshot

- rakenna snapshot-malli
- rakenna fingerprint
- rakenna invariant checker
- vaihda read-polut snapshotin käyttäjiksi

### Vaihe 3 — mutation gateway

- ohjaa reforge, socket, upgrade ja evolution yhteen gatewayhin
- ota expected revision käyttöön
- ota idempotency käyttöön
- kirjoita event ja snapshot atomisesti

### Vaihe 4 — client v4

- lisää uusi protocol envelope
- pidä legacy-parseri väliaikaisesti
- lisää revision- ja stale-viestit
- lisää inspectin uudet osiot

### Vaihe 5 — progression

- item XP
- item level
- mastery
- evolution stage
- branch
- milestone unlockit

### Vaihe 6 — advanced economy

- extraction
- transfer
- gem instances
- risky/safe upgrade
- corruption
- trade locking
- admin restore

### Vaihe 7 — legacy freeze

- estä suorat vanhat mutation-polut
- lokita jokainen legacy-kutsu
- korjaa jäljellä olevat kutsujat
- poista legacy-kirjoitus vasta kun metriikat näyttävät nollaa

---

## 22. Definition of Done

Evolution 2.0 ei ole valmis ennen kuin kaikki seuraavat täyttyvät:

- kaikki item-mutaatiot käyttävät yhtä gatewayta
- kaikki mutationit ovat atomisia
- kaikki mutationit ovat idempotentteja
- revision conflict käsitellään
- fingerprint voidaan laskea ja validoida
- event history voidaan lukea
- vanha data migroituu automaattisesti
- invalidi data havaitaan invariant checkerillä
- trade ei voi duplikoida itemiä
- destroyed item ei voi palata inventoryyn ilman restore-operaatiota
- affixit, socketit ja unique-effectit näkyvät breakdownissa
- evolution on data-driven
- XP on server-authoritative
- reforge ja upgrade ovat deterministisesti testattavissa
- client pystyy käsittelemään unknown fieldit ja version mismatchit
- kaikki admin-muutokset auditoidaan
- concurrency-testit läpäisevät
- crash-recovery-testit läpäisevät
- moduulikohtainen build ja testit läpäisevät
- dokumentaatio vastaa toteutusta

---

## 23. Lopullinen toteutuskomento agentille

Kun tämä määrittely toteutetaan, agentin tulee noudattaa seuraavaa komentoa:

> Toteuta Item Instance Evolution 2.0 nykyisen equipment-instance-arkkitehtuurin
> päälle. Älä rakenna rinnakkaista V2-palvelua. Aloita kartoittamalla kaikki
> nykyiset load-, save-, mutation-, inventory-, trade-, combat- ja client-polut.
> Säilytä vanha data ja rakenna yhteensopiva migraatiopolku. Lisää canonical
> snapshot, vahvat ID-tyypit, UUID, owner/binding/state-machine, revision,
> fingerprint, lineage, append-only event history, atomic mutation gateway,
> optimistic locking, idempotency, server-authoritative XP/level/mastery,
> data-driven evolution stages ja branches, affix quality/conflict/budget-
> säännöt, socket- ja gem-validointi, upgrade/reworge-contractit, trade-lockit,
> invariant checker, structured protocol v4, täydellinen inspect breakdown,
> observability, admin-diagnostics ja palautuspolut. Jokainen muutos on tehtävä
> source-koodiin, ei build-outputiin. Käytä projektin AGENTS.md-ohjeita, pidä
> muutokset scopeen rajattuina, aja moduulikohtainen spotlessApply ennen buildia,
> kirjoita unit-, property-, migration-, concurrency-, crash- ja protocol-testit,
> tarkista vanhojen itemien backward compatibility ja raportoi kaikki jäljelle
> jäävät oletukset. Älä ilmoita tehtävää valmiiksi ennen kuin Definition of Done
> täyttyy ja testit on ajettu.

---

## 24. Suositeltu ensimmäinen toteutuspaketti

Ensimmäiseen turvalliseen implementation sliceen kuuluu:

1. canonical snapshot
2. UUID
3. revision
4. fingerprint
5. baseline migration events
6. invariant checker
7. atomic mutation gateway
8. expected revision
9. idempotency key
10. event history
11. repository transaction support
12. migration tests
13. concurrency tests
14. client protocol versioning
15. admin `inspect`, `history` ja `validate`

Vasta tämän jälkeen kannattaa ottaa käyttöön item XP, evolution branchit,
corruption, affix transfer ja laajennettu talous. Muuten uusi pelisisältö
rakennetaan liian epäluotettavan persistointi- ja auditointipohjan päälle.

