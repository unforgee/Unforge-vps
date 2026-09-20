# Project Gielinor

[![revision][rev-badge]][patch] [![license][license-badge]][isc] [![upstream][upstream-badge]][rsmod]

## Welcome to Project Gielinor!

This is a passion project with a clear goal: Bring the modern Old School RuneScape experience to everyone. My hope is that the private server community shares my ambition and love for the game. RuneScape is a game that has been carefully curated over a quarter century! No single person could ever faithfully recreate a game of this scale alone. It requires a community, modern tooling, and collaboration—including AI. My personal experience with private servers has taught me many things but one thing stands out above all others: Every creator wants to be a part of something bigger than themselves.

### My Hope: This is it.

Jagex has spent years protecting its intellectual property, and many ambitious private server projects have disappeared as a result. My hope is that by building this as a completely non-commercial, community-driven project, we can create something that lasts. This isn't about making money. It's about preserving a game we all love.

### This project succeeds only if the community builds it together.

If you've ever wanted to contribute to something larger than your own server, this is your opportunity. Fork the repository, submit a pull request, report issues, or help document the project. Every contribution matters, no matter how big or small. Every skillset is valuable. Every ounce of effort moves this project forward.

Join the community. Get involved. Let's do something monumental!

### Let's FREE GIELINOR!

---

## Credits & attribution

Project Gielinor stands on the work of people who built the foundations we rely on. We give them full credit — and we expect contributors to do the same.

| Project | Role | Link |
|--------|------|------|
| **[RS Mod][rsmod]** | Upstream game-server emulator this repository is forked from | [github.com/rsmod/rsmod][rsmod] |
| **[RSProt][rsprot]** | OSRS networking / protocol library | [github.com/blurite/rsprot][rsprot] |
| **[RSProx][rsprox]** | Local proxy & recommended client bridge for private targets | [github.com/blurite/rsprox][rsprox] |
| **[OpenRS2][openrs2]** | Cache archive & tooling ecosystem | [github.com/openrs2/openrs2][openrs2] |

Special thanks to the **RS Mod** maintainers and contributors for the modular Kotlin server architecture, plugin model, and educational focus that made this fork possible; to **Blurite** for RSProt and RSProx; and to everyone in the wider OSRS research community who documents revisions, dumps, and client behaviour so others can build honestly.

This project retains the upstream **ISC** license and copyright notices. See [LICENSE.md](LICENSE.md).

**Upstream:** [rsmod/rsmod][rsmod] · **This fork:** [Slashx124/UnForge][gielinor]

---

## Current focus

- Tracking **revision 239** (and the matching cache / protocol stack)
- Mechanical accuracy over shortcuts
- Content and systems that the community can verify, extend, and own

## Requirements

This project requires **[Java 21][java] or later**.

_See [Installing Java](#installing-java)._

## Installation

### IntelliJ

1. **File → New → Project from Version Control**
2. Clone URL: `https://github.com/Slashx124/UnForge.git`
3. Open the project, then **Load Gradle Project**
4. Run the **GameServer** configuration

The first boot downloads required game files. Later runs are much faster.

### CLI

```sh
git clone https://github.com/Slashx124/UnForge.git
cd UnForge
gradlew install --console=plain && gradlew run --console=plain
```

After editing map NPC/loc spawn TOML (e.g. Tutorial Island `npcs.toml`), run `gradlew packCache` before `run`. A normal server start does not repack mapsquare NPC lists.

To track upstream RS Mod as well:

```sh
git remote add upstream https://github.com/rsmod/rsmod.git
git fetch upstream
```

Project Gielinor is compatible with **[RSProx][rsprox]** — the recommended client path for private targets and packet inspection.

### RSProx localhost target (no YAML writing required)

Import the ready-made localhost target in RSProx (**+** next to the target selector → **From URL…**) using:

**https://raw.githubusercontent.com/Slashx124/UnForge/main/proxy-targets.yaml**

That file points at `jav_local_239.ws` and game port **43594**. Keep the committed `.data/game.key` so the published modulus matches; if you regenerate RSA, update the YAML modulus from `.data/client.key`.

## Installing Java

#### Where to download Java 21

- **[Adoptium OpenJDK 21 LTS][adoptium-download]** _(recommended — free & open-source)_
- **[OpenJDK 21][openjdk-download]**
- **[Oracle JDK 21][oracle-download]** _(requires login)_

#### Package managers

- **Linux/macOS (SDKMAN!):** `sdk install java 21.0.7-tem`
- **macOS (Homebrew):** `brew install openjdk@21`
- **Windows (WinGet):** `winget install --id=EclipseAdoptium.Temurin.21.JDK -e`

## Compatible clients

We recommend **[RSProx][rsprox]**:

- Free and open-source
- Actively maintained
- Packet inspection for verifying mechanics
- Private-server target support

**Ready-made localhost target:** [proxy-targets.yaml](proxy-targets.yaml)  
**Raw import URL:** https://raw.githubusercontent.com/Slashx124/UnForge/main/proxy-targets.yaml

## Local Unforge AI text bridge

The standalone build includes a text-only, localhost-only bridge between the bundled RuneLite-style client and the local AI4FUN Studio Master Agent. It uses WebSocket for live task events and HTTP for health/configuration. The default endpoints are `127.0.0.1:18901` (WebSocket) and `127.0.0.1:18900` (HTTP); AI4FUN Studio is expected at `127.0.0.1:18902`. V1 bridge tasks use OpenRouter's `openrouter/free` route; Ollama is intentionally disabled for this path until enabled separately.

Start the bridge with `tools\standalone\Start-UnforgeAiBridge.bat`, then start the normal local r239 server/config/client flow. Enable the `Unforge AI` plugin and open its toolbar panel. The bridge does not proxy game traffic, open VPS ports, or expose the client to the internet. Full protocol and startup details are in [docs/unforge-ai-bridge.md](docs/unforge-ai-bridge.md).

## Contributing

Contributions are welcome. Prefer small, verifiable changes: one mechanic, one interface, one clear bugfix.

1. Fork **Project Gielinor**
2. Create a branch for your change
3. Keep fidelity and security in mind — no shortcuts that break correctness
4. Open a pull request that explains *what* changed and *how you tested it*

If you are new, good first areas include interface wiring, content configs, skill scripts, and revision/cache verification.

## License

Project Gielinor is based on RS Mod and remains available under the **ISC** license. The full copyright notice and terms are in [LICENSE.md](LICENSE.md). You must retain the upstream copyright and permission notice in all copies.

[isc]: https://opensource.org/licenses/ISC
[license]: LICENSE.md
[license-badge]: https://img.shields.io/badge/license-ISC-informational
[patch]: https://oldschool.runescape.wiki/w/Update:Doom_Combat_Achievements
[rev-badge]: https://img.shields.io/badge/revision-239-important
[upstream-badge]: https://img.shields.io/badge/forked%20from-rsmod%2Frsmod-blue
[java]: https://openjdk.java.net/projects/jdk/21/
[adoptium-download]: https://adoptium.net/temurin/releases/?version=21
[openjdk-download]: https://jdk.java.net/archive/
[oracle-download]: https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html
[rsmod]: https://github.com/rsmod/rsmod
[gielinor]: https://github.com/Slashx124/UnForge
[rsprox]: https://github.com/blurite/rsprox
[rsprot]: https://github.com/blurite/rsprot
[openrs2]: https://github.com/openrs2/openrs2
