# Grid - 4-State Digital Logic for Hytale

**Grid** is a foundation mod for Hytale that implements a robust 4-state digital logic system. Unlike traditional binary logic, Grid uses four states (LOW, HIGH, WEAK, UNKNOWN) with multi-driver resolution, enabling complex power networks and circuitry.

> ⚠️ **Early Development**: Grid is actively developed against Hytale's current API. Expect breaking changes before 1.0.

---

## Features

### 4-State Logic System
- **LOW** - No signal
- **HIGH** - Strong signal (1)
- **WEAK** - Weak/pulled signal
- **UNKNOWN** - Conflict state (destroys blocks)

### Core Components
- **Power Source** - Multi-input NOR gates with inversion
- **Wire** - Per-face network connections with visual updates
- **Relay** - Control-signal switches that alter topology
- **Input Port** - Network state probes for logic control
- **Lamp** - Visual output blocks (light up when powered)

### Technical Architecture
- Per-face network assignment (each block face → independent network)
- Multi-driver resolution (multiple sources driving same net)
- Delta-cycle evaluation (iterative stabilization)
- Relay-controlled topology (switches alter connectivity)
- Conflict detection (UNKNOWN_X state when drivers conflict)

---

## Installation

1. **Download** the latest release from [Releases](https://github.com/solventak/hytale-grid/releases)
2. **Place** `Grid-vX.X.X.jar` in your Hytale `mods/` folder
3. **Launch** Hytale server/client

---

## For Modders

Grid is designed as a **platform/foundation mod** for other mods to build on.

### Adding Grid as a Dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation(files("libs/Grid-v0.1.0.jar"))
}
```

### API Usage

Grid exposes components and systems through Hytale's ECS architecture:

```kotlin
// Access Grid's component types
ExamplePlugin.powerSourceComponentType
ExamplePlugin.powerWireComponentType
ExamplePlugin.lampComponentType
// ... etc
```

For detailed API documentation and examples, see [CLAUDE.md](CLAUDE.md).

---

## Building from Source

```bash
git clone https://github.com/solventak/hytale-grid.git
cd hytale-grid
./gradlew build
# Output: build/libs/Grid-vX.X.X.jar
```

**Requirements:**
- Java 17+
- Kotlin 1.9+
- Gradle 8.0+

---

## Development

### Project Structure
- `src/main/kotlin/` - Plugin source code
- `mod-assets/` - Block models, textures, manifests
- `decompiled_server/` - Hytale API reference
- `docs/` - Release management and strategy docs

### Wire Generation
Grid includes Python scripts to generate wire block variants:
```bash
python generate_wire_blocks.py
python wire_lookup_table.py
```

---

## Contributing

**Status:** Private repository. Public contributions will be accepted after initial release.

For now, submit issues/PRs directly on GitHub once the repo goes public.

---

## Roadmap

- [ ] Public release v0.1.0
- [ ] Migration guides for API changes
- [ ] Example circuits and tutorials
- [ ] Integration with other tech mods
- [ ] Modpack compatibility testing

---

## License

MIT License - see [LICENSE](LICENSE) for details.

---

## Support

- **Issues:** [GitHub Issues](https://github.com/solventak/hytale-grid/issues)
- **Discussions:** [GitHub Discussions](https://github.com/solventak/hytale-grid/discussions)
- **Discord:** [Hytale Modding Discord](https://discord.gg/hytale) (community channels)

---

## Acknowledgments

Built using Hytale's server API and Entity Component System (ECS) architecture.
