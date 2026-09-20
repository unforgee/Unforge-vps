# PvM + Skilling — pelaajakokemuksen UX-spesifikaatio

Päivitetty 2026-09-17. Tämä dokumentti kertoo, **miltä** `docs/pvm-skilling-architecture.md`:n
järjestelmä näyttää ja tuntuu pelaajalle — ei miten se toteutetaan serverissä. Rajaus perustuu
kolmeen olemassa olevaan server-authoroituun UI-malliin, joita slice hyödyntää:

| Malli | Interface | Mitä siitä opitaan |
|---|---|---|
| `slayer_hub` (`SlayerHubInterfaceBuilder`, 1005) | 512×334 keskitetty modaali | Rewards-sivu: scrollattava rivilista, hinta + Buy → Owned, tabit, npc-head-malli, close-sprite (539/540) |
| `unforge_perks` (`UnforgePerksInterfaceBuilder`, 1002) | 190×300 side-journal-sivu | Pysyvä progression-lista: nimi, taso, kuvaus, 10-seg pip-palkki, Train-nappi, pisteet headerissa |
| `unforge_support` (`UnforgeSupportInterfaceBuilder`, 1004) | 260×380 floater | Yksi `<br>`-tekstirunko, soft-timer-refresh; locked-rivit `<col=888888>locked</col>` |

Tekniset raamit kaikkiin alla oleviin näkymiin:

- Komponenttityypit: `LAYER(0)`, `RECT(3)`, `TEXT(4)`, `GRAPHIC(5)`, `MODEL(6)` — ei uusia spritejä
  ilman cache-työtä; item-kuvat tehdään `TYPE_MODEL` + `ifSetObj` -parina (kuten npc-head
  `slayer_hub`:ssa).
- Vuorovaikutus: `events = EVENTS_OP1` + `op = arrayOf("...")` — op-teksti näkyy right-click-
  valikossa ja hover-verbina. Hover-säätö rajautuu cs2-apureihin (`CS_SWAP_GRAPHIC 44`,
  `CS_RECOLOUR_TEXT 45`); dynaamisia tooltip-tekstejä ei voi tehdä, joten "tooltipit" toteutuvat
  **op-teksteinä + rivin desc-rivinä + chat-mes()**.
- Tekstit: yksi fontti (`FONT_B12 495`), `<br>`-rivinvaihdot, `<col=rrggbb>`-värit, `ifSetText` /
  `ifSetHide` / `ifSetScrollPos`.
- Virheet ja vahvistukset menevät chattiin `mes()`-viesteinä (`<col=ffb84d>` gold /
  `<col=ff981f>` orange), sama kaava kuin `PerkJournalScript.trainPerk`.
- Scroll: natiivirullaus + `IfSetScrollPos`-painikkeet; positio on ephemeral (resetoituu aina
  avatessa — `WeakHashMap<Player, Int>`).

---

## 1. Pelaajan progression flow

Kaksi erillistä valuuttaa pitää näyttää eri paikoissa, jotta niitä ei koskaan sekoiteta:

```
NON-COMBAT SKILLS                    PVM
───────────────────                  ─────────────────────────
tee skilling-toimintoja ──► +Skill Points ──► Skill Quartermaster (shop)
      (Fishing→Cooking,                   │   Consumables / Upgrade kits /
       Mining→Smithing, ...)              │   Recipe scrolls / Utility
                                          ▼
                              PvM-valmistautuminen (ruoka, potion,
                              ammo, armor kit, campfire)
                                          │
kill bosses ◄─────────────────────────────┘
      │
      └──► +Perk Points ──► Perks journal tab (OLEMASSA)
              (permanent power: Berserker/Ironhide/...)
```

**Pelaajan näkökulmasta kolme "missä"-kysymystä ja niiden vakiovastaakset:**

1. "Mistä saan Skill Pointeja?" → teetävällä skillingillä; jokainen onnistunut action maksaa.
   Ensimmäinen ansainta tulostaa chattiin:
   `<col=ffb84d>You earned 3 skill points. Spend them at the Skill Quartermaster.</col>`
   (näytetään vain 1×/session tai ensimmäiset N kertaa — ei spammiin, ks. §10).
2. "Mihin ne käytetään?" → Skill Quartermaster NPC (Edgeville home). Shop-otsikko näyttää aina
   saldon: `Skill Points: 480`.
3. "Mitä hyötyä PvM:ssä?" → shop-rivien desc-tekstit kertovat suoraan ("Heals + fire resist
   3 min"), boss-kortti näyttää mitä suositellaan.

Progression-tason sanoma yhdessä lauseessa, joka toistuu johdonmukaisesti kaikkialla:
**"Combat stats decide if you can enter. Skilling decides how comfortable it is."**

---

## 2. Miten pelaajalle kerrotaan, mitä kukin skilli tekee PvM:ssä

`skill_guide_v2` (interface 860) on client-omisteinen cache-interface — serveri ei voi kirjoittaa
sen sisältöä (`SkillGuideScript`-kommentti). Siksi skillien PvM-roolit kerrotaan **kolmessa
omassa paikassa**, ei muokkaamalla skill guidea:

### 2.1 "Skill Paths" -paneeli (uusi server-authoroitu sivu)

Side-journal-tabin uusi välilehti `Paths` (SideJournalTab-enum laajenee yhdellä — sama
`ifOpenOverlay(..., journal_components.tab_container)` -polku kuin Perks). 190×300-lista, yksi
rivi per skilli, sama rivipohja kuin perks:

```
Paths                                            [side journal tab]
────────────────────────────────────────────────────────
Fishing         Feeds Cooking — better PvM food.
Cooking         PvM food: heals + timed buffs.
Firemaking      Campfires: fire resist, regen.
Smithing        Melee armour kits, shields.
Mining          Ore for Smithing kits; gems.
Fletching       Ranged kits, elemental ammo.
Woodcutting     Logs for ammo & campfires.
Runecrafting    Magic kits, rune-save pouches.
Herblore        Resist & combat potions.
Crafting        Rings & amulets with PvM stats.
Agility         Stamina, shortcuts, dodge.
Construction    Prep station: pre-boss buff.
Farming         Herbs & veg for potions/food.
Hunter          Rare mats, boss lures, tracking.
Slayer          Task bonus vs bosses.
Prayer          Sustain prayers. (existing)
```

Yksi lause per skilli — ei prosentteja, ei numeroita. Numeroita (mitä buffi antaa) näytetään
vasta shopin ja boss-prepin yhteydessä, koska ne ovat kontekstiriippuvaisia.

### 2.2 Quartermasterin ensidialogi

NPC-op `Talk-to` → 2–3 lausetta dialogue-boxissa (olemassa oleva chat-dialogue-putki, ei uutta
UI:ta): "Train any non-combat skill and I'll pay you skill points. My stock turns those into
food, potions, armour kits and recipes for boss work. Nothing here is required — it just makes
the fights shorter." + op `Trade` → shop.

### 2.3 Shopin rivien desc-tekstit viittaavat aina skilliin

Jokainen shop-rivi kertoo, mistä skillistä tuote tulee: `Crafted via Cooking 60` /
`Smithing 40 to use`. Pelaaja oppii roolikartan käyttämällä shopia — ei erillistä tutorialia.

---

## 3. Skill Shop — wireframe-tason rakenne

**Valittu malli: `slayer_hub`-modaali** (512×334, keskitetty, `ifOpenMainModal`). Sama runko
(header + accent rule + scrollattava lista + close) on jo todistettu toimivaksi
Rewards-sivulla — shop on rakenteeltaan identtinen: rivi = nimi + hinta + Buy/Owned/Locked.
Perks-sivun side-panel (190×300) jätetään progression-katsaukselle; shop kaipaa leveyttä
hinta+kuvaus+nappi -kolmikolle.

```
┌──────────────────────────────────────────────────────────────┐
│ Skill Quartermaster        Skill Points          [X]         │
│                                    480                       │
│══════════════════════════════════════════════════════════════│ <- accent rule
│ [Consumables] [Kits & Recipes] [Supplies] [Utility]          │ <- tab-rivi
│┌────────────────────────────────────────────────────┐ ┬──┐
││ CONSUMABLES                                        │ │▲ │
││ ┌────────────────────────────────────────────────┐ │ │  │
││ │ Shark ×5                              25p  [Buy]│ │ │  │
││ │ PvM food: heals 20 each. Cooked via Fishing.    │ │ │  │
││ ├────────────────────────────────────────────────┤ │ │  │
││ │ Fire-resist stew                      60p  [Buy]│ │ │  │
││ │ +15% fire resist 3 min. Cooking 40 to brew.    │ │ │  │
││ ├────────────────────────────────────────────────┤ │ │  │
││ │ Rune arrows ×100                      40p  [Buy]│ │ │  │
││ │ Standard ranged ammo. Fletchable for less.     │ │ │  │
││ └────────────────────────────────────────────────┘ │ │  │
││ KITS & RECIPES                                     │ │  │
││ ┌────────────────────────────────────────────────┐ │ │  │
││ │ Melee armour kit                     250p [Lvl]│ │ │  │
││ │ Requires Smithing 40 — yours: 27. Upgrades     │ │ │  │
││ │ worn melee armour one tier.                    │ │ │  │
││ ├────────────────────────────────────────────────┤ │ │  │
││ │ Feast recipe scroll                  500p [Buy]│ │ │  │
││ │ Permanent unlock — teaches group-feast recipe. │ │ │  │
││ │ Cooking 60 to use.                             │ │ │  │
││ └────────────────────────────────────────────────┘ │ │  │
││ Points come from skilling — any non-combat skill. │ │▼ │
│└────────────────────────────────────────────────────┘ └──┘
└──────────────────────────────────────────────────────────────┘
```

**Rivin anatomia** (pitch ~40px, slayer-reward-riviä syvempi koska 2 tekstiriviä):

- `name` — entryn nimi + määrä (`Shark ×5`), väri ORANGE (0xFF981F)
- `desc` — 1–2 riviä DIM (0x9A8B76): **vaikutus ensin, hankintatapa perässä**
- `price` — GOLD (0xFFB84D), `25p` muodossa, right-aligned
- `buy` — layer+rect+text -nappi (`Buy`, GREEN-teksti) — tai korvautuva tila (§8)
- `bg` + `bg_border` — PANEL (0x1F1B15) -kortti, sama kuin slayer reward -rivit

**Kategoriat** toteutetaan tab-rivillä (slayer_hubin tab-malli: tekstivälilehti + underline).
MVP:ssa riittää yksi sivu ilman tabeja — kategoriotaotsikot scroll-listassa (kuten
`sec_combat`/`sec_skilling` perksissä); tabit lisätään kun kategoria-aktiivisuus vaatii.

---

## 4. Permanent perk vs recipe vs consumable — erottuminen

Kolmen asianlajin eron pitää näkyä **ennen ostopäätöstä**, ei vasta inventarissa:

| Asialaji | Missä myydään | Valuutta | Visuaalinen tunnus | Esimerkki |
|---|---|---|---|---|
| **Permanent perk** | Perks journal tab (ei koskaan shopissa) | Perk Points (vain boss-killeistä) | 10-seg pip-palkki + `n/10` + Train | `Berserker 4/10` |
| **Recipe / unlock** | Skill shop, `Kits & Recipes` | Skill Points, kertaosto | suffix `<col=6b6154>- Permanent</col>` nimen perässä + `Owned`-tagi oston jälkeen (slayer-malli) | `Feast recipe scroll - Permanent` |
| **Consumable** | Skill shop + GP-shopit + craftaus | Skill Points (tai GP/mats) | määrä nimessä (`×5`), desc alkaa vaikutuksella, ei kestotagia | `Shark ×5` |
| **Upgrade kit** | Skill shop | Skill Points | suffix `- Kit` + kohdeslotti descissä | `Melee armour kit - Kit` |
| **Utility** | Skill shop | Skill Points | suffix `- Service` | `Slayer task reroll - Service` |

Periaatteet:

- **Valuutta on ensisijainen erotus**: perksivu ja shop eivät koskaan näytä samaa valuuttaa.
  Headerissä aina `Skill Points: N` vs perksissä `Perk Points: N`.
- **Suffix-tagit** (`- Permanent`, `- Kit`, `- Service`) slayer_hubin `- Unlock`/`- Extension`
  -mallilla — yksi lisäteksti nimen perään, ei uusia komponentteja.
- **"Owned" korvaa Buy-napin** kokonaan (piilotetaan `ifSetHide`, ei disabloida — disabloitua
  nappia ei voi erottaa actiivisesta pienessä koossa).
- Consumableilla ei ole "owned"-tilaa — samaa tuotetta voi ostaa uudelleen; Buy-nappi pysyy.

---

## 5. Mihin pelaajan pisteet kannattaa käyttää — ohjaus

Pelaajalle ei anneta "optimipolkua", vaan **kolme itseluettavaa signaalia**:

1. **Rivien järjestys = suositusjärjestys.** Halvimmat ja universaalimmat (food, potions)
   listan kärjessä; kallimmat erikoistuotteet (kits, scrolls) alempana. Sama kuin perks- ja
   slayer-listoissa: ylhäältä alas -lukemisjärjestys on jo etabloitu.
2. **Affordability-rivitys descissä:** hinta on GOLD kun varaa on, mutta rivi ei koskaan
   piiloudu varattomuuden vuoksi — näet aina mitä tähtäät.
3. **Boss-prep-kortti (§7) kertoo *miksi* ostaa:** "Fire boss — recommended: 15% fire resist"
   linkittää mielessä stew/campfire/potion-rivit tarpeeseen.

Shopin footer-rivi tekee ohjauksen eksplisiittiseksi yhdellä lauseella:
`New here? Food and potions first — kits and scrolls can wait.`

Lisäksi `::stats`-paneelin `perks`-sarakkeen alle oma lyhyt block myöhemmin (post-MVP):
`Skill Points 480 — suggested: Fire-resist stew (60p)`.

---

## 6. Melee / Ranged / Magic -polut

Polut esitetään **yhden rivin per polku** Paths-sivun yläosassa — ei kolmea tabia (ne tekisivät
valinnan tuntumaan sitovammalta kuin se on):

```
Your path — pick any, swap anytime
───────────────────────────────────
Melee    Mining → Smithing: armour kits, shields.
         Pair: Berserker + Ironhide perks.
Ranged   Woodcutting → Fletching: kits, elemental ammo.
         Pair: Deadeye + Dodging perks.
Magic    Runecrafting: magic kits, rune-save pouches.
         Pair: Sorcerer + Spellward perks.
───────────────────────────────────
Universal: Fishing→Cooking · Farming→Herblore ·
Firemaking · Crafting · Hunter · Agility · Slayer
```

Kaksi lukusuuntaa rakennetaan tietoisesti:

- **Pystysuunta** ("pelaan meleeä") → yksi gathering→production-pari kerrallaan.
- **Vaakasuunta** ("pelaan kaikkea") → Universal-rivi kertoo että food/potion/campfire-kerros
  on sama kaikille tyyleille — ei kolminkertaista grindia.

Slayer-päätelmä (`Boss tilt` architecture §11): boss-korteissa (§7) `Best with:`-rivi nimeää
suosikkityylin ("Ranged: kite the burn patches"), ei koskaan ainoaa vaihtoehtoa.

---

## 7. Fire / Water / Physical -bossien valmistautuminen

Boss-kortti on `slayer_hub`:n task-card -mallin suora johdannainen — hero-kortti modaalissa,
joka avautuu arena-entrancessa / quartermasterin `Boss prep` -tabista / worldmap-infossa.

```
┌─ FIRE FAMILY ─────────────────────────────┐
│ [npc_head]  Emberlord (fire)              │
│             Mechanic: burning ground,     │
│             periodic ignite.              │
│                                           │
│ Recommended: combat 70+ · fire resist 15% │
│                                           │
│ Fire resist sources (any mix to 30% cap): │
│  ✓ Campfire kit        (Firemaking 30)    │
│  ✓ Fire-resist stew    (Cooking 40)       │
│  ✓ Resist potion       (Herblore 35)      │
│  · Gear affix          (drop/craft)       │
│                                           │
│ Bring: food ×5 · resist ×1 · heal-over-   │
│ time optional. You can enter at any level │
│ — prep just makes it shorter.             │
└───────────────────────────────────────────┘
```

**Perhekohtainen sisältö:**

| Perhe | Element-tag | Mekaniikka-rivi | Checklistin pääsisältö |
|---|---|---|---|
| Fire | `FIRE` punainen | "Burning ground, ignite ticks" | fire resist ≥15%, food, liikkuminen (Agility-huomio) |
| Water | `WATER` sininen | "Heals unless interrupted" | water resist ≥15%, antipoison, anti-heal-ammus/burst |
| Physical | `PHYS` harmaa | "Heavy strikes, no tricks" | armor tier ≥ Adamant, shield-points, dodge/kiting |

Tärkeää checklistin sanamuodossa: lähteet listataan `any mix to 30% cap` -otsikolla — pelaaja
näkee välittömästi että **yksikin lähde riittää** ja cap estää ylityön. Tila-merkit (✓/·)
ovat informatiivisia (mitä pelaajalla jo on inventoryssa/varoissa), ei gateja.

---

## 8. Ruoka-, potion-, armor- ja ammo-suositukset

Suositukset esitetään aina **samassa 4-slotin skeemassa** riippumatta siitä missä ne näkyvät —
yksi mentaalimalli:

```
Food   ×5+     heals — crafted or shop tier
Potion ×1–2    resist/combat — one slot only (replace-not-stack)
Armour kit     tier check vs boss family
Ammo   ×100    elemental tip = bonus vs weakness (never required)
```

Missä skeema näkyy:

1. **Boss-kortin `Bring:`-rivi** (§7) — tiivistettynä.
2. **Shopin ensimmäinen kategoria-otsikko** (`CONSUMABLES`) — rivit ovat jo skeemassa.
3. **Chat-reminder kun pelaaja astuu arenaan ilman foodia** (advisory, max 1×/trip):
   `<col=ffb84d>You're going in without food — that's allowed, just slower.</col>`

Ammo-suosituksen sanamuoto on tärkeä: elemental ammo on **weakness-bonus**
("+damage vs fire-weak"), ei elementtivaatimus — sama asia kuin `weakTo` architecturessa.

---

## 9. Pakollisuuden ja monimutkaisuuden välttäminen

Konkreettiset UX-säännöt, jotka kääntävät architecturen §10-balansointisäännöt kieleksi:

1. **"Recommended" ei koskaan "Required".** Level-bandit ja resistit renderöidään aina
   sanalla `Recommended:`; entry-ehtoja ei ole. Ainoa hard-gate-kielenkäyttö on skill-shopin
   *tier*-vaatimus, ja sekin esitetään `Requires Smithing 40 — yours: 27` (näyttää etäisyyden,
   ei pelkkää kieltä).
2. **Cap on aina näkyvissä siellä missä stackingia voisi yrittää:** "any mix to 30% cap" —
   pelaajaa ei jää arvailemaan kannattaako stäkätä neljättä lähdettä.
3. **Yksi buffi per slot -sääntö sanottuna, ei oletettuna:** potion/stew/campfire -rivien
   descissä `Resist slot — replaces, doesn't stack`.
4. **Vaihtoehtopolut aina samalla rivillä:** `Crafted via Cooking 40 · or buy weaker tier`.
   Pelaaja näkee, ettei skilliä tarvitse treenata *juuri tämän* vuoksi.
5. **Valuutat pysyvät erillään ja se sanotaan:** shopin footer `Skill points can't be bought —
   earn them by skilling. Perk points come from boss kills.` Yksi lause, ei luentosalia.
6. **Ei punaisia varoituksia puutteesta:** puuttuvat resistit/food eivät koskaan renderöidy
   `0xFF0000`-värillä eivätkä "missing"-sanalla — ne ovat `·` (ei valittu), ei `✗` (virhe).
7. **Ensikosketus on pieni:** `::skillshop` näyttää heti ostettavaa (halvin rivi 25p,
   aloituspisteet eivät vaadi tuntien grindia). Pelaaja oppii loopin ensimmäisellä ostoksella.
8. **Ei uusia pakollisia päivärituaaleja:** daily stock näkyy rivissä (`Stock: 3/day left`)
   lähinnä scarcity-signaalina — ei FOMO-tekstillä ("last chance!").

---

## 10. Korttien ja tooltipien sisältö (konkreettiset tekstit)

### 10.1 Consumable-rivi (shop)

```
name:   Fire-resist stew
desc:   +15% fire resist for 3 min. Resist slot —
        replaces, doesn't stack.
        Brewed via Cooking 40 · weaker tier in GP shop.
price:  60p
op:     "Buy"
mes:    <col=ffb84d>Bought Fire-resist stew for 60 skill points.</col>
```

### 10.2 Recipe scroll -rivi (permanent unlock)

```
name:   Feast recipe scroll <col=6b6154>- Permanent</col>
desc:   Teaches the group-feast cooking recipe.
        Cooking 60 to use — buying doesn't grant the level.
price:  500p
op:     "Buy"
owned:  Buy → "Owned" (GREEN), purchase repeatable=false
mes:    <col=ffb84d>Learned the feast recipe. Check the Cooking make menu.</col>
```

### 10.3 Upgrade kit -rivi

```
name:   Melee armour kit <col=6b6154>- Kit</col>
desc:   Upgrades worn melee armour one tier.
        Requires Smithing 40 — or 3x price without it.
price:  250p  (750p no-skill)
op:     "Buy"
```

### 10.4 Perk-rivi (Perks tab — olemassa oleva, muutokseton)

```
name:   Berserker                    4/10
desc:   +2% melee hit damage per level.
bar:    [■■■■□□□□□□]
button: Train
```

### 10.5 Boss-kortti (boss prep)

Sisältö §7:n mukaan; uusi rivi: `Bring:`-checklist + `Best with:`-suosikkityyli.

### 10.6 Op-tekstit (hover/right-click-verbina)

| Komponentti | op-teksti |
|---|---|
| Buy-nappi | `Buy` |
| Owned-tagin paikka | (ei eventtiä) |
| Locked-rivi | `Buy` (klikkaus tuottaa vaatimus-mesin — op pysyy ennustettavana) |
| Scroll-napit | `Scroll` |
| Close | `Close` |
| Tabit | `Select` |

### 10.7 Error- ja locked-tilat

| Tila | Trigger | Näyttö |
|---|---|---|
| `insufficient-points` | balance < cost | `<col=ffb84d>You need 500 skill points for Feast recipe scroll — you have 480.</col>` |
| `locked-level` | stat < requiredLevel | `<col=ffb84d>Requires Smithing 40 — yours: 27.</col>` + rivillä price-paikalla `Lvl`-tagi Buy'n sijaan |
| `owned-repeat` | unlock jo ostettu | Buy piilossa, `Owned` (GREEN) — ei mesiä |
| `stock-empty` | daily stock 0 | hinta-teksti → `<col=ff5555>Out</col>`, Buy piilossa; mes `Restocks daily.` |
| `points-earned` | skilling success | throttle-mes §1 |
| `buff-replace` | samaan slottiin uusi buff | `<col=ffb84d>Fire-resist stew replaced your resist buff.</col>` |
| `enter-no-prep` | arena entry ilman foodia | §8.3 advisory, kerran per trip |
| `scroll-reset` | if uudelleenavattu | positio 0 (perks-malli, ei virhettä) |

---

## 11. Saavutettavuus ja selkeys

- **Ei pelkkää värisignalointia:** locked näkyy `Lvl`-tagina + tekstinä, owned sanana `Owned`,
  out sanana `Out` — värit vahvistavat, eivät kanna yksin.
- **Paletti rajattu kolmeen merkitysväriin** (slayer_hub-perintö): GOLD=hinta/pisteet,
  GREEN=valmis/omistettu/positiivinen, DIM=lähdetekstit. ACCENT (crimson) varataan boss-tagille
  ja pääotsikoille — ei käytetä virheisiin (virheet ovat chat-gold).
- **Tekstikoko:** kaikki `FONT_B12`; kuvausrivit DIM-värillä eivät kilpaile nimen kanssa.
- **Numeroiden muoto:** hinnat aina `Np` (`60p`), resistit prosentteina (`+15%`), kesto minuutteina
  (`3 min`) — ei sekunteja, ei desimaaleja shopissa.
- **Scroll-affordance:** scrollbar-rect näkyy aina kun sisältö ylivuotaa + ▲▼-napit (koska
  natiivirullaus ei ole ilmeistä kaikille).
- **Yksi asia per rivi:** name+desc+buy — ei tietotulvaa; yksityiskohdat (bps-arvot, formulat)
  jätetään `::stats`-paneeliin ja wikiin, ei shopiin.
- **Chat-mesit eivät koskaan pakota toimintaan:** kaikki advisory-mesit ovat
  ohittamassa olevaa tietoa, ei keskeneräisiä tehtäviä tai quest-log-merkintöjä.
- **Ruudunlukijaa/tekstiä ei ole** — if3-client on graafinen; selkeyteen vaikuttaa tekstin
  pituus: rividesc max ~70 merkkiä, fiteröi "flavor"-tekstit ulos.

---

## 12. MVP vs myöhempi versio

### MVP — välttämätön (slice todistaa ytimen)

| # | UI-elementti | Status |
|---|---|---|
| 1 | `unforge_skillshop`-modaali: header (otsikko + Skill Points -saldo + close), scrollattava rivilista | **slice tehty** — `content/interfaces/skill-shop` |
| 2 | Rivi: name + desc + price + Buy-nappi | slice |
| 3 | `Owned`-tila unlock-riveille (Buy→Owned) | slice |
| 4 | `locked-level`-tila (level-vaatimus + mes) | slice |
| 5 | `insufficient-points` chat-virhe | slice |
| 6 | `skill_points` varp + saldon näyttö headerissä | slice |
| 7 | `::skillshop` + `::skillpoints` -komennot testaukseen | slice |
| 8 | Skill Quartermaster NPC:n `Trade`-op avaa saman modaalin | seuraava askel (npc spawn + op-binding, slayer-mastersin malli) |
| 9 | Yksi boss-prep-tekstiblokki fire-bossille — voi olla aluksi pelkkä quartermaster-dialogi/chat-checklist, ei korttia | MVP-data riittää |
| 10 | `points-earned`-viesti ensiansainnasta | SkillPointService mukana |

### Post-MVP — ei blokkia

- Kategoriatabit (Consumables/Kits/Supplies/Utility erikseen) — tarvitaan vasta kun rivimäärä
  kasvaa yli ~15:n.
- `Paths`-sivu side-journaliin (SideJournalTab-laajennus + 190×300 builder) — skilliroolikartta.
- Boss-prep-kortti `slayer_hub`-hero-mallilla (npc-head + checklist + bring-rivi).
- `stock-empty`-tilan rivinäyttö (daily stock).
- `buff-replace`-mesit consumable-dispatcherin myötä.
- `::stats`-paneelin "suggested"-rivi.
- Grafiikka: item-mallikuvat (`TYPE_MODEL`+`ifSetObj`) rivien vasempaan reunaan — mahdollista jo
  nyt, mutta slice pitää tekstiversiota riittävänä todisteena.
- Resist/buff-palkki support-HUD:iin (`elementResistBps` näkyviin).
- Cache-`CurrencyType`-promootio → oikea shop_main-UI (jos toivotaan vanilla-tuntua).
- Daily stock -indikaattori, kosmetiikka-kategoria, prep-station-UI.

---

## 13. Toteutusviittaukset

| Elementti | Precedent | Tiedosto |
|---|---|---|
| Modaali + scroll-lista + Buy→Owned | slayer rewards | `content/areas/unforge/.../slayer/hub/` |
| Progression-lista + pisteet | perks journal | `content/interfaces/journal-tab/` |
| Floater-buffpaneeli | support HUD | `content/pvm-support/` |
| 4-sarakkeen stats-modal | `::stats` | `content/interfaces/stats/` |
| GP-shop | `Shops.open` | `api/shops/` |
| Skill-shop slice | `unforge_skillshop` | `content/interfaces/skill-shop/` |
