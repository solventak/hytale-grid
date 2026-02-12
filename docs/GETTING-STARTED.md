# Getting Started with Grid

**Audience:** Modders who want to integrate Grid into their Hytale mods.

---

## What is Grid?

Grid is a foundation mod that provides a 4-state digital logic system for Hytale. Think of it like redstone, but with:
- **4 logic states** instead of binary: LOW, HIGH, WEAK, UNKNOWN
- **Per-face networks** - each block face can belong to a different network
- **Multi-driver resolution** - handles conflicts intelligently
- **Relay-controlled topology** - switches that alter connectivity

Grid is designed to be a **platform mod** - you build on top of it to create tech mods, power systems, or complex circuitry.

---

## Installation

### For Players
1. Download `Grid-vX.X.X.jar` from [Releases](https://github.com/YOUR_USERNAME/hytale-grid/releases)
2. Place in your Hytale `mods/` folder
3. Launch Hytale

### For Modders
Add Grid as a dependency in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/Grid-v0.1.0.jar"))
}
```

---

## Using Grid in Your Mod

### 1. Access Grid Components

Grid exposes component types through `ExamplePlugin` companion object:

```kotlin
import dev.hytalemodding.ExamplePlugin
import dev.hytalemodding.newnet.*

// Access Grid components
val powerSource = ExamplePlugin.powerSourceComponentType
val powerWire = ExamplePlugin.powerWireComponentType
val lamp = ExamplePlugin.lampComponentType
val relay = ExamplePlugin.relayComponentType
val inputPort = ExamplePlugin.inputPortComponentType
```

### 2. Read Network State

To check if a block face is powered:

```kotlin
import com.hypixel.hytale.math.vector.Vector3i
import dev.hytalemodding.ExamplePlugin
import dev.hytalemodding.newnet.State4

fun isBlockPowered(pos: Vector3i, face: Int, world: World): Boolean {
    val queue = world.chunkStore.store.getResource(ExamplePlugin.stateChangeQueueType)
    val netIds = world.chunkStore.store.getComponent(pos, ExamplePlugin.powerNetIdsComponentType)
    
    if (netIds == null) return false
    
    val netId = netIds.get(face)
    val netValue = queue.powerNetValueCache[netId] ?: State4.ZERO
    
    return netValue == State4.ONE
}
```

### 3. Create Powered Blocks

Add Grid components to your blocks in their prefab JSON:

```json
{
  "Prefab": [
    {
      "Component": "PowerConnectable",
      "facesMask": 63
    },
    {
      "Component": "PowerNetIds"
    },
    {
      "Component": "VisualState",
      "state": "default"
    }
  ]
}
```

**facesMask:** Bitmask for connectable faces (bit N = face N)
- `63` = `0b111111` = all 6 faces
- `3` = `0b000011` = only UP and DOWN faces

### 4. React to Power Changes

Listen for topology changes in your own system:

```kotlin
class MyPoweredSystem : ChunkTickingSystem<ChunkStore>() {
    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuf: CommandBuffer<ChunkStore>
    ) {
        val queue = store.getResource(ExamplePlugin.stateChangeQueueType)
        
        // Check if any of your blocks changed power state
        for (event in queue.changes) {
            // Handle power change at event.pos
        }
    }
    
    override fun getQuery(): Query<ChunkStore> {
        return Query.and(ExamplePlugin.powerNetIdsComponentType, MyComponentType)
    }
}
```

---

## Building Circuits

### Basic Circuit: Source → Wire → Lamp

1. Place a **PowerSource** block (multi-input NOR gate)
2. Connect with **Wire** blocks
3. Add a **Lamp** to see the output

The lamp will light up when powered.

### Using Relays (Switches)

1. Place a **Relay** block
2. Connect power nets to conduction faces
3. Connect control signal to control faces
4. The relay conducts when control signal is HIGH

### Using Input Ports (Network Probes)

1. Place an **InputPort** adjacent to a PowerSource, Relay, or MUX
2. The opposite face probes a network
3. The driver (Source/Relay) reads the probed value via the InputPort

### Logic Gates

**NOR Gate:** PowerSource (built-in)
- Drive state = NOR of all control face inputs

**NOT Gate:** PowerSource with 1 control face
- Output = NOT(input)

**AND Gate:** Combine NOR gates
- A AND B = NOT(NOT(A) OR NOT(B))

---

## 4-State Logic Rules

### States
- **ZERO** - Low / off
- **ONE** - High / on
- **WEAK** - Weak pull-up (easily overridden)
- **UNKNOWN_X** - Conflict / undefined (destroys blocks!)

### Multi-Driver Resolution

When multiple drivers target the same network:
1. `ONE` beats `WEAK` beats `ZERO`
2. Multiple `ONE` → `ONE` (no conflict)
3. `ONE` + `ZERO` → `UNKNOWN_X` (conflict!)

**UNKNOWN_X destroys blocks** on the conflicted network. Avoid driver conflicts!

---

## Examples

### Simple Toggle Switch

```
[PowerSource] → [Wire] → [Relay] → [Wire] → [Lamp]
                            ↑
                      [InputPort]
                            ↑
                    [Wire (loopback)]
```

Relay controls its own input via loopback → oscillator (may conflict!)

### SR Latch (Set-Reset)

```
[PowerSource S] → [Wire] → [Relay 1] → [Wire] → [Lamp]
                              ↑
                         [InputPort 1]
                              ↑
[PowerSource R] → [Wire] → [Relay 2] → [Wire] ────┘
                              ↑
                         [InputPort 2]
                              ↑
                         [Wire (from Relay 1 output)]
```

Two cross-coupled relays form a latch.

---

## API Reference

### Component Types

| Component | Purpose |
|-----------|---------|
| `PowerConnectable` | Declares connectable faces (facesMask) |
| `PowerNetIds` | Stores network IDs (6 per block) |
| `PowerSource` | Inverting driver (NOR gate) |
| `PowerWire` | Bridges all faces internally |
| `Lamp` | Visual output (lights when powered) |
| `Relay` | Controlled switch |
| `InputPort` | Network probe for drivers |
| `VisualState` | Generic visual state component |
| `Mux2Part` | 2-to-1 multiplexer |

### Helper Functions

```kotlin
// Face constants
const val FACE_DOWN = 0
const val FACE_UP = 1
const val FACE_NORTH = 2
const val FACE_EAST = 3
const val FACE_SOUTH = 4
const val FACE_WEST = 5

// Get adjacent position in direction of face
fun getAdjacentPos(pos: Vector3i, face: Int): Vector3i

// Get opposite face index
fun getOppositeFace(face: Int): Int

// Get all 6 adjacent positions
fun getAllAdjacent(pos: Vector3i): List<Vector3i>
```

---

## Troubleshooting

### My blocks aren't connecting
- Check that both blocks have `PowerConnectable` with matching face bits
- Verify `PowerNetIds` component is present
- Make sure blocks are adjacent (no gaps)

### Circuits oscillate/conflict
- Check for driver conflicts (multiple sources on same net)
- Use InputPorts to probe state without creating loops
- Consider adding delay via intermediate blocks

### Blocks destroyed by UNKNOWN_X
- Driver conflict detected (e.g., Source driving ZERO and ONE simultaneously)
- Review circuit topology, ensure drivers don't conflict
- Add isolation via relays

### Visual states not updating
- Ensure `VisualState` component is in block prefab
- Check that TopologySystem ran (may take 1 tick)
- Verify block type has interaction states defined

---

## Further Reading

- **CLAUDE.md** - Detailed architecture and code patterns
- **TopologySystem.kt** - Core network evaluation algorithm
- **Unit Tests** - Example usage (see `src/test/`)

---

## Support

- **GitHub Issues:** [Report bugs](https://github.com/YOUR_USERNAME/hytale-grid/issues)
- **GitHub Discussions:** [Ask questions](https://github.com/YOUR_USERNAME/hytale-grid/discussions)
- **Discord:** Hytale Modding Discord (community channels)
