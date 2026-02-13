# Grid - Code Reference for AI Assistants

## Project Overview

**Grid** is a 4-state digital logic foundation mod for Hytale. This document provides code patterns and architecture reference for AI assistants working on Grid.

---

## Codebase Locations

- **Plugin code**: `src/main/kotlin/dev/hytalemodding/`
- **Power network system**: `src/main/kotlin/dev/hytalemodding/newnet/`
- **Decompiled server source**: `decompiled_server_src/` - Hytale API reference
- **Assets**: `mod-assets/` - Block models, textures, manifests
- **Tests**: `src/test/kotlin/dev/hytalemodding/newnet/` - Unit tests (currently broken)

---

## Architecture Overview

Grid uses Hytale's Entity Component System (ECS) architecture:

- **Components**: Data containers attached to chunks (all Grid components use `ChunkStore`)
- **Systems**: Logic processors that operate on chunks with specific components
- **Resources**: Store-level singleton state (e.g., `StateChangeEventQueue`)

### Plugin Entry Point

**File**: `src/main/kotlin/dev/hytalemodding/ExamplePlugin.kt`

Main plugin class extending `JavaPlugin`. All components and systems are registered in `setup()`.

Component types are stored in `ExamplePlugin.companion object` for global access:
```kotlin
companion object {
    lateinit var powerSourceComponentType: ComponentType<ChunkStore, PowerSource>
    lateinit var powerWireComponentType: ComponentType<ChunkStore, PowerWire>
    // ... etc
}
```

---

## 4-State Logic System

Grid implements a 4-state logic system (not binary):

```kotlin
enum class State4 {
    ZERO,     // Low/off
    ONE,      // High/on
    WEAK,     // Weak-high (pulled up, easily overridden)
    UNKNOWN_X // Conflict/undefined (destroys blocks)
}
```

### Multi-Driver Resolution

When multiple drivers target the same network:
- `ONE` beats `WEAK` beats `ZERO`
- Multiple `ONE` drivers → `ONE`
- `ONE` + `ZERO` → `UNKNOWN_X` (conflict, destroys blocks)

See `resolveNetValue()` in `TopologySystem.kt`.

---

## Core Components

All Grid components use `ChunkStore` (block-level, not entity-level).

### PowerConnectable
Declares which block faces can connect to power networks.

```kotlin
class PowerConnectable : Component<ChunkStore> {
    var facesMask: Int = 0  // Bitmask: bit N = face N is connectable
}
```

### PowerNetIds
Stores network ID for each block face (6 IDs per block).

```kotlin
class PowerNetIds : Component<ChunkStore> {
    private val netIds = IntArray(6) { UNASSIGNED }
    
    fun get(face: Int): Int
    fun set(face: Int, netId: Int)
}
```

### PowerSource
Inverting driver blocks (multi-input NOR gates).

```kotlin
class PowerSource : Component<ChunkStore> {
    var driveState: State4 = State4.ZERO
    var lastDriveState: State4 = State4.ZERO
}
```

Drives nets on all non-control faces. Drive value is NOR of all control face nets.

### PowerWire
Wire blocks that bridge all connectable faces internally.

```kotlin
class PowerWire : Component<ChunkStore> {
    // No state - just a marker component
}
```

Wires create internal connections between all faces (full bridging).

### Lamp
Power consumer blocks that light up when any connected net is HIGH.

```kotlin
class Lamp : Component<ChunkStore> {
    var lit: Boolean = false
}
```

### Relay
Controlled switches that alter network connectivity.

```kotlin
class Relay : Component<ChunkStore> {
    var enabled: Boolean = true       // Current conducting state
    var lastEnabled: Boolean = true   // Previous state (for change detection)
    var controlFaceMask: Int = 0      // Which faces are control inputs
}
```

Conducts between non-control faces when enabled. Control faces determine enabled state via InputPort probes.

### InputPort
Network probe blocks that read net state.

```kotlin
class InputPort : Component<ChunkStore> {
    var driverSideFace: Int = -1  // Which face connects to the driver (PowerSource/Relay/MUX)
}
```

InputPorts pass through power from one face to the opposite face, allowing drivers to probe network state.

### VisualState
Generic visual state component for block appearance.

```kotlin
class VisualState : Component<ChunkStore> {
    var state: String = "default"  // Interaction state name ("default", "On", etc.)
}
```

TopologySystem sets state, VisualStateSystem applies it via `setBlockInteractionState()`.

### Mux2Part
2-to-1 multiplexer component (paired blocks).

```kotlin
class Mux2Part : Component<ChunkStore> {
    var isComplete: Boolean = false
    var pairedBlockOffset: Vector3i? = null
    var selectedInput: Int = 0  // 0 or 1
}
```

Two adjacent MUX blocks form a complete multiplexer. One is "selected", the other is "non-selected".

---

## Core Systems

### TopologySystem
Main system that manages network topology, evaluation, and visual updates.

**Responsibilities:**
1. Process block placement/break events from `StateChangeEventQueue`
2. Rebuild network topology (flood fill)
3. Evaluate power sources (compute drive states)
4. Resolve network values (multi-driver resolution)
5. Update block visuals (lamps, relays, wires, etc.)
6. Detect and destroy UNKNOWN_X networks

**Key Functions:**
- `clearNetsFromSeeds()` - Clear network assignments from dirty blocks
- `rebuildPowerTopology()` - Flood fill to assign network IDs
- `evaluateSources()` - Compute PowerSource drive states
- `resolveNetValues()` - Multi-driver resolution for all networks
- `destroyXNets()` - Destroy blocks on conflicted networks
- `updateLamps()`, `updateRelays()`, etc. - Update visual states

**Algorithm:**
1. Collect topology seeds (changed blocks)
2. For each round (max 10):
   - Clear nets from dirty blocks
   - Rebuild topology via flood fill
   - For each delta cycle (max 100):
     - Evaluate sources
     - Resolve net values
     - Check stability
   - Update visuals
   - Check for relay/MUX toggles (re-seed if needed)
3. Destroy UNKNOWN_X networks
4. Clear processed events

See `TopologySystem.tick()` for full flow.

### VisualStateSystem
Applies visual state changes to blocks.

**Responsibilities:**
- Read `VisualState.state` from blocks
- Call `world.setBlockInteractionState()` to update appearance
- Handle wire shape updates (connection-based model swapping)

Runs AFTER TopologySystem via system dependencies.

### PowerBlockAddedSystem
Handles block placement events.

**Responsibilities:**
- Add Grid components to newly placed blocks
- Configure InputPort driver sides
- Validate MUX/InputPort placement rules
- Queue topology rebuild events

### PowerBlockBreakEvent
Handles block destruction events.

**Responsibilities:**
- Queue topology rebuild events
- Mark wire neighbors for visual updates

---

## Creating New Block Types

### 1. Define the Block Type
Add block definition to `mod-assets/Server/BlockType/BlockTypes.json`.

### 2. Create Component (if needed)
```kotlin
class MyComponent : Component<ChunkStore> {
    var myState: Int = 0

    companion object {
        @JvmField
        val CODEC: BuilderCodec<MyComponent> = 
            BuilderCodec.builder(MyComponent::class.java) { MyComponent() }.build()
    }

    fun getComponentType(): ComponentType<ChunkStore, MyComponent> {
        return ExamplePlugin.myComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return MyComponent().also { it.myState = this.myState }
    }
}
```

### 3. Register Component
In `ExamplePlugin.kt`:
```kotlin
// Companion object
lateinit var myComponentType: ComponentType<ChunkStore, MyComponent>

// In setup()
myComponentType = this.chunkStoreRegistry.registerComponent(
    MyComponent::class.java, "MyComponent", MyComponent.CODEC
)
```

### 4. Add to PowerBlockAddedSystem
Handle component initialization when block is placed.

### 5. Add Visual Update Function
If the block has visual states, add `updateMyBlockVisuals()` to TopologySystem.

---

## Key Hytale API Patterns

### World Access
Grid uses a `WorldAccess` interface for reading block components:

```kotlin
interface WorldAccess {
    fun <T : Component<ChunkStore>> getComponent(pos: Vector3i, type: ComponentType<ChunkStore, T>): T?
}
```

This allows mocking for unit tests (see `MockPowerWorld.kt`).

### Block Access Helpers
```kotlin
// Get component at global position
val comp = worldAccess.getComponent(pos, ExamplePlugin.powerSourceComponentType)

// Get adjacent positions
val adjacent = getAllAdjacent(pos)  // Returns 6 neighbors

// Face direction helpers
val npos = getAdjacentPos(pos, face)  // Get neighbor in direction of face
val nface = getOppositeFace(face)     // Get opposite face index
```

### System Dependencies
Control system execution order:

```kotlin
override fun getDependencies(): Set<Dependency<ChunkStore>> {
    return setOf(SystemDependency(Order.AFTER, TopologySystem::class.java))
}
```

---

## State Change Event Queue

Grid uses an event queue pattern for block changes:

```kotlin
class StateChangeEventQueue : Resource<ChunkStore> {
    val changes = mutableListOf<BlockChangeEvent>()
    val powerNetValueCache = mutableMapOf<Int, State4>()
    val netMembers = mutableMapOf<Int, MutableSet<Pair<Vector3i, Int>>>()
    // ... etc
}
```

**Flow:**
1. `PowerBlockAddedSystem` / `PowerBlockBreakEvent` queue events
2. `TopologySystem` processes events during tick
3. Topology rebuilt, networks evaluated, visuals updated
4. Queue cleared

---

## Wire Visual System

Wires use connection-based models with 64 variants (6 faces, 2^6 = 64 combinations).

**Naming Convention:**
- `Wire_{axis}_{connectionCount}{type}_{state}`
- Example: `Wire_UD_2a_Power` = vertical wire, 2 connections (adjacent), power variant

**Lookup Table:**
`WireLookupTable.kt` maps face connection bitmasks to model variants + rotations.

**Shape Update:**
1. Wire placed/broken → neighbors marked dirty
2. `VisualStateSystem` reads connection bitmask
3. Lookup table determines model + rotation
4. `world.setBlock()` swaps block type

---

## Testing

Unit tests use `MockPowerWorld` to simulate Hytale's world without a server:

```kotlin
val world = MockPowerWorld()
val queue = world.createQueue()

// Place blocks
world.placeSource(Vector3i(0, 0, 0), faceMask = 0b111111)
world.placeWire(Vector3i(1, 0, 0))

// Run topology
runTopology(setOf(Vector3i(0, 0, 0)))

// Check results
assertEquals(State4.ONE, world.getNetValue(Vector3i(1, 0, 0), face = 0))
```

**Current Status:** Tests are broken (initialization issues). See `DEBUG-LOGGING-TODO.md`.

---

## Common Pitfalls

### 1. Face Indexing
Faces are indexed 0-5: DOWN=0, UP=1, NORTH=2, EAST=3, SOUTH=4, WEST=5

Use `FACE_NAMES` array for debug logging:
```kotlin
println("Face ${FACE_NAMES[face]}")  // "DOWN", "UP", etc.
```

### 2. Component Registration Order
Components must be registered before systems that use them.

### 3. World Execute Blocks
Block modifications must happen inside `world.execute {}`:
```kotlin
world.execute {
    world.setBlock(x, y, z, "BlockType")
}
```

### 4. Cross-Store Access
Event systems on `EntityStore` must access `ChunkStore` via:
```kotlin
world.chunkStore.store.getResource(ExamplePlugin.stateChangeQueueType)
```

### 5. Delta Cycles
Power network evaluation uses iterative delta cycles (max 100). If unstable, force UNKNOWN_X.

---

## Debug Logging

Grid has extensive debug println statements (see `DEBUG-LOGGING-TODO.md`).

**Temporary Debug:**
```kotlin
println("[MySystem] Debug message")
```

**TODO:** Implement proper logging framework or DEBUG flag.

---

## Resources

- **Hytale API**: `decompiled_server_src/` - Search for Hytale classes/components
- **Release Docs**: `docs/` - RELEASE-MANAGEMENT.md, LAUNCH.md, etc.
- **Architecture**: This file (CLAUDE.md)
