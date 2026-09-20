# AGENTS.md — UNFORGE-239-STANDALONE-LAB

## Tools: Content Studio (tools/content-editor)

Paikallinen loopback-editori kolmelle sisältölähteelle — **jokaisella oma sivu**
(`?source=<id>`-välilehdet, eristetty tila): `unforge` (tämä repo, `content/areas`
spawn-TOML editoitavissa + `content/kronos-data` reference), `crownwield` (auto-detektoitu
`C:\CrownWield\server\Kronos-master\kronos-server\data`, kronos-profiili) ja `ruse`
(SSH-mirroitu `devin@87.58.145.127:/home/devin/rsps-server/data`, ruse-profiili —
auto-detektoitu kun `~/.ssh/id_ed25519` löytyy). Devin CLI integroitu proposal-polkuun.

### Käynnistys

- `.\tools\standalone\Start-UnforgeContentStudio.ps1` — Gradle `:tools:content-editor:run`,
  avaa selaimen automaattisesti kun `work/content-editor/server.url` ilmestyy.
- `.\tools\standalone\Install-ContentStudioShortcut.ps1` — luo/päivittää työpöytäkuvakkeen.
- Portti 18930 (oletus). Kaikki `/api/**` vaatii `X-Editor-Token`-headerin (token on URL:ssa).
- `--source name=polku[|kronos|ruse|ro]` tai `name=ssh://user@host/path|...` lisää sourcen
  (toistettava); `|ro` = read-only, `|key=polku` = SSH-avain. ssh://-lähteet mirroroituvat
  `work/content-editor/mirrors/<id>`-kansioon ja pushataan takaisin applyssa.
- `GET /api/v1/sources` listaa lähteet (ml. `remote`/`syncState`/`syncedAt`);
  `POST /api/v1/sources/{id}/refresh` re-pullaa mirrorin; `catalog`/`names` ottavat
  `?source=`-parametrin, ChangeSetissä on `source`-kenttä (default `unforge`).

### Build & test

```powershell
.\gradlew.bat :tools:content-editor:spotlessApply :tools:content-editor:build --console=plain
```

Koko repon build on raskas — aja moduulikohtaisesti. Spotless (ktfmt) pakotetaan
`check`-vaiheessa; aja aina `spotlessApply` ennen buildia.

### Tärkeät säännöt

- **unforge**: kirjoitus vain `content/areas/**/src/main/resources/**/npcs.toml|objs.toml`
  `[[spawn]]`-riveille; validointi: `kind-mismatch`, `field-not-found`, `coords-required`,
  `row-not-found`, `source-conflict` (SHA-256 optimistic lock), `duplicate-coords` (WARN).
- **kronos**: kirjoitus vain `npcs/spawns` + `npcs/combat` + `objects/spawns` +
  `items/spawns` `.json`-taulukoiden alkioihin — `KronosJson.kt` on span-patcheri joka
  säilyttää kommentit ja layoutin; validointi: `kind-mismatch` (kind↔hakemisto),
  `value-not-int`, `ids-invalid`, `field-added` (WARN kun kenttä lisätään),
  `id/x/y/z-required` insertille, `op-conflict` (update+delete samalle riville).
- Kronos ESTETYT polut (ei koskaan lueta/kirjoiteta): `logins/`, `owner-content/`,
  `region_keys.json`, `items/manifests`, `items/models_source`.
- **ruse**: kirjoitus vain `map/npcs` + `map/items` + `def/object_spawns.json` +
  `def/shops.json` + `combat/npc` `.json`-taulukoiden alkioihin; nested `tile.x`/`stats.*`
  pistekentät toimivat (`KronosJson`-patcher); validointi `value-not-int` (leaf-pohjainen),
  `value-not-json` (tile/items/stats/bonuses/animations/tables/tiles), insert-pakolliset
  (`id,x,y` / `id,tile.x…z` tai raw `tile` / `shopId,name,items` / `ids`).
- Ruse ESTETYT polut (ei mirroroidukaan): `saves`, `logs`, `tradingpost`, `clipping`,
  `filestore`, `examine`, `doorpairs.bin`.
- Remote-apply: remote-sha256 vs mirror ennen kirjoitusta (`remote-conflict`), temp+mv-push,
  osittaisvirheessä lokaalit palautetaan backupeista; rollback tarkistaa saman driftin.
- `content/kronos-data` unforge-sourcessa on edelleen REFERENCE_ONLY.
- `work/` on gitignorettu (snapshotit, audit.jsonl, devin-lokit, mirrors/) — snapshotit
  toimivat myös crownwield/ruse-sourceille (backupit aina repo-workiin, manifestissa
  `source`-kenttä).
- Devin CLI: `devin auth login` vaaditaan; propose ajaa `devin --print --prompt-file`
  repo-juuressa ja tulos menee samaan preview→apply-putkeen (Devin ei kirjoita suoraan).
- Suorita PowerShell-kutsut `-Command '...'` single-quoteilla bashin kautta — `$_`/`$var`
  muutoin syödään. TAI kirjoita .ps1-tiedosto ja aja `-File`.

## Pelipalvelin (rsmod) — ympäristö & cache

- `java`/`git` ei ole PATHissa. Aseta ennen gradle-ajoja:
  `$env:JAVA_HOME='C:\Users\Administrator\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'`
  ja lisää `$env:JAVA_HOME\bin` PATHiin.
- Plugin-skriptit ladataan ClassGraph-skannauksella paketeista `org.rsmod.api` +
  `org.rsmod.content`; `server:shared` api-dependoi kaikki `:content:**`-moduulit
  joilla on `build.gradle.kts` — uusi hakemisto + build-tiedosto riittää kytkemään.
- Uudet nimetetyt tyypit (VarpBuilder, TimerReferences/VarpReferences `find` jne.)
  vaativat `id<TAB>nimi`-rivin `.data/symbols/<type>.sym`-tiedostoon (gitissä).
  Varp-id:t < 65535; vapaa lohko 5582+ (65504+ on varattu). Aja sen jälkeen
  `.\gradlew.bat packCache` — muuten `TypeVerifier` kaataa palvelimen/testit
  ("names that are not defined in a .sym file").
- `.data/cache/game|js5` voi jäädä "torn write" -tilaan keskeytyneestä ajosta
  (StoreCorruptException "out of date") — korjaus on `packCache`, joka rakentaa
  cachet uudestaan vanillasta.
- Integraatiotestit: `:content:skills:<x>:integration` (jvm-test-suite, käynnistää
  pelipalvelimen oikealla cachella — hidas).
- Jos Kotlin-daemon palauttaa phantom-käännösvirheitä: `.\gradlew.bat --stop`,
  poista `*/build/kotlin/compileKotlin`-hakemistot, aja `"-Pkotlin.incremental=false"
  --no-build-cache` (lainausmerkit — PowerShell muuten pilkkoo `-P`-argun).
