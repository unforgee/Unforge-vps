# Slayer-dungeoneiden inventaario

Tämä lista on muodostettu tämän UNFORGE r239 -labin aktiivisesta Unforge-
NPC-paketista (`content/areas/unforge/.../npcs.toml`) ja Kronos-referenssin
combat-määrityksistä. Dungeon lasketaan mukaan, kun sen spawn-data sisältää
Slayer-nimen tai NPC:n combat-määrityksessä on Slayer XP/level -kenttä.

NPC-nimet ovat palvelimen symbolinimiä. Samassa dungeonissa oleva `npc` on
riittävä runtime-tunniste; koordinaatit ja respawnit tulevat erillisestä
pakattavasta `npcs.toml`-datasta.

## Dungeonit, joissa on Slayer-mobeja

| Dungeon / lähdetiedosto | Spawnit | NPC-lista |
|---|---:|---|
| Brimhaven Dungeon | 26 | `black_demon`, `bronze_dragon`, `greater_demon`, `iron_dragon`, `red_dragon`, `steel_dragon` |
| Catacombs of Kourend | 5 | `rat`, `spider` |
| Chasm of Fire | 9 | `lesser_demon_slayercave_1`, `lesser_demon_slayercave_2`, `lesser_demon_slayercave_3` |
| Edgeville Dungeon | 28 | `chaos_druid`, `chronozon`, `deadly_red_spider`, `rat`, `thug` |
| Fremennik Slayer Dungeon | 72 | `slayer_basilisk`, `slayer_cave_crawler_1..4`, `slayer_cockatrice`, `slayer_jelly_1..6`, `slayer_kursk_1..2`, `slayer_pyrefiend_1..4`, `slayer_rockslug`, `slayer_turoth_baby`, `slayer_turoth_child`, `slayer_turoth_dad`, `slayer_turoth_mum` |
| Stronghold Slayer Cave (`dungeons/gnome_stronghold.json`) | 25 | `slayer_aberrantspectre_1_strongholdcave`, `slayer_aberrantspectre_2_strongholdcave`, `slayer_bloodveld_baby_strongholdcave`, `slayer_bloodveld_strongholdcave` |
| Kalphite Cave | 42 | `kalphite_soldier`, `kalphite_worker`, `slayer_cave_entomologist` |
| Kalphite Lair | 38 | `kalphite_queen`, `kalphite_soldier`, `kalphite_worker` |
| Karamja Dungeon | 11 | `deadly_red_spider`, `dragonslayer_skeleton_1_key`, `dragonslayer_skeleton_2`, `dragonslayer_skeleton_3`, `lesser_demon` |
| Karuulm Slayer Dungeon | 50 | `drake`, `greater_demon`, `hellhound`, `hydra`, `sulphur_lizard` |
| Kraken Cove | 26 | `slayer_kraken_boss_whirlpool`, `slayer_kraken_sub` |
| Lithkren Laboratory | 6 | `adamant_dragon`, `rune_dragon` |
| Lumbridge Swamp Caves | 11 | `giant_frog`, `slayer_cave_crawler_1`, `slayer_cave_crawler_3`, `slayer_cave_crawler_4`, `slayer_rockslug`, `slayer_rockslug_baby` |
| Smoke Devil Dungeon | 43 | `lesser_demon_slayercave_1..3`, `smoke_devil` |
| Smoke Dungeon | 42 | `slayer_dustdevil`, `slayer_pyrefiend_1..4` |
| Taverley Dungeon | 97 | `bat`, `black_demon`, `black_dragon`, `black_knight`, `blue_dragon`, `chaos_druid`, `hellhound`, `jailer`, `lesser_demon`, `poison_scorpion`, `rat`, `slayer_blackdragons_guardian`, `spider`, `suit_of_armour` |
| Varrock Sewers / Dungeon | 48 | `deadly_red_spider`, `rat`, `scorpion`, `spider` |
| Waterfall Dungeon | 7 | `rat`, `shadow_spider` |

**Yhteensä:** 18 dungeon-lähdettä ja 586 spawn-riviä tässä lähderyhmässä.

## Erillinen Slayer Tower

Slayer Tower on lähdedatassa oma alueensa (`slayer_tower.json`), ei
`dungeons/`-alikansiossa. Se kuuluu mukaan pelisisältöön ja sisältää seuraavat
NPC:t:

`slayer_abberant_spectre_1..4`, `slayer_abyssal`,
`slayer_abyssal_strongholdcave`, `slayer_banshee_1`, `slayer_bloodveld`,
`slayer_bloodveld_baby`, `slayer_bloodveld_baby_strongholdcave`,
`slayer_bloodveld_strongholdcave`, `slayer_cave_gargoyle`,
`slayer_crawling_hand_1`, `slayer_crawling_hand_big_1..2`,
`slayer_gargoyle_1`, `slayer_infernal_mage_1..5`, `slayer_nechryael`,
`slayer_nechryael_strongholdcave`, `slayer_tower_dungeon_ghost`.

## Runtime-toteutus

- Slayer-teleporttikirjan Slayer-sivu on jo Unforgein palvelinskriptissä:
  `content/areas/unforge/.../teleports/UnforgeTeleports.kt`.
- Jokaiselle listan pääkohteelle on suora palvelimen teleporttikoordinaatti,
  joten kohteet eivät ole pelkkiä UI-merkintöjä.
- `slayer_dungeon_entrance` ja `slayer_dungeon_exit` on nyt sidottu samaan
  server-authoritative 1-tason siirtymämalliin kuin dungeon-ladderit.
  Siirtymässä käytetään animaatiota, yhden tickin viivettä ja `telejump`-reittiä;
  client ei päätä kohdetta.
- NPC:t ovat aktiivisessa Unforge `npcs.toml` -pakkauspolussa. Muutokset
  tarvitsevat normaalin `packCache`-vaiheen ennen käynnissä olevaan serveriin
  latautumista.

## Todisteiden rajaus

Tämä tiedosto todistaa lähdeinventaarion ja runtime-kytkennät. Se ei yksin
todista kirjautunutta client-peliä, jokaisen dungeonin fyysistä sisäänkäyntiä,
collision-reittiä tai combat/drop-kierrosta. Ne on varmennettava käynnistämällä
r239-serveri ja client, kirjautumalla sisään ja smoke-testaamalla kohteet
pelissä.
