# PvM-elementit ja status-efektit

## Vaihe 1: shared combat -mallit

`api/combat/combat-commons` sisältää nyt vain tulevaa combat-laajennusta varten
purettavat datatyypit:

- `Element`: `NONE`, `PHYSICAL`, `FIRE`, `WATER`, `ICE`, `BLOOD`, `SHADOW` ja `SMOKE`.
- `StatusEffect`: `POISON`, `BLEED`, `BURN`, `CHILL`, `SLOW`, `FROZEN`, `VULNERABLE`,
  `WEAKEN` ja `BLIND`.
- `AttackElement`: hyökkäyksen elementtimetatieto, jonka oletus on `Element.NONE`.
- `StatusInstance`: status, duration tick-määränä sekä nykyinen stack-määrä ja stack-cap.

Tämä vaihe ei muuta `HitBuilder`-rajapintaa eikä nykyisiä PvP-, PvN-, NvP- tai
NvN-laskuja. `NONE` tarkoittaa, että vanha damage-polku säilyy ennallaan.

## Rajaus

NPC-resistansseja, status-tickereitä tai statusvaikutusten soveltamista ei vielä
toteuteta. Item-, shop-, perk- ja skill-koodiin ei tehdä muutoksia.

Seuraavassa vaiheessa voidaan kytkeä `AttackElement` hyökkäyskontekstiin ja
`StatusInstance` erilliseen statusjärjestelmään. Kytkentä tehdään vasta, kun
PvM-resistanssien säännöt ja tick-aikataulu on päätetty; tässä vaiheessa mallit
ovat tarkoituksella inerttejä.
