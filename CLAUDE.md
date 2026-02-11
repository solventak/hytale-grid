# Hytale Modding Plugin - Code Reference

## Codebase Locations

- **Plugin code**: `src/main/kotlin/dev/hytalemodding/`
- **Decompiled server source**: `/home/alex/Developer/MyFirstMod/decompiled_server_src/` - Search here for Hytale API classes, components, and systems not implemented in this project

## Architecture Overview

This plugin uses Hytale's Entity Component System (ECS) architecture:

- **Components**: Data containers attached to entities or chunks
- **Systems**: Logic processors that operate on entities/chunks with specific components
- **Stores**: `EntityStore` for entity-level systems, `ChunkStore` for chunk-level systems

## Plugin Entry Point

**Location**: `src/main/kotlin/dev/hytalemodding/ExamplePlugin.kt`

Main plugin class extending `JavaPlugin`. All components and systems are registered in `setup()`.

---

## Creating New ChunkStore Components

Use `TemplateComponent` in `src/main/kotlin/dev/hytalemodding/PoweredComponent.kt` as a reference.

### Step 1: Create the Component Class
```kotlin
class MyComponent : Component<ChunkStore> {
    var myState: Boolean = false

    companion object {
        @JvmField
        val CODEC: BuilderCodec<MyComponent> = BuilderCodec.builder(MyComponent::class.java) { MyComponent() }.build()
    }

    fun getComponentType(): ComponentType<ChunkStore, MyComponent> {
        return ExamplePlugin.myComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return MyComponent().also { it.myState = this.myState }
    }
}
```

### Step 2: Register in ExamplePlugin.kt

1. Add to companion object:
```kotlin
companion object {
    lateinit var myComponentType: ComponentType<ChunkStore, MyComponent>
}
```

2. Register in `setup()`:
```kotlin
myComponentType = this.chunkStoreRegistry.registerComponent(
    MyComponent::class.java,
    "MyComponent",
    MyComponent.CODEC
)
```

### Required Elements
- **CODEC**: `BuilderCodec` for serialization/persistence
- **getComponentType()**: Returns the registered `ComponentType` from `ExamplePlugin`
- **clone()**: Creates a copy with all state fields

---

## Key Hytale API Patterns

### System Registration
```kotlin
entityStoreRegistry.registerSystem(MySystem())
chunkStoreRegistry.registerSystem(MyChunkSystem())
```

### Queries
- `Query.any()` - Match all entities/chunks
- `Query.and(type1, type2, ...)` - Match entities with all specified components
- `Query.not(type)` - Exclude entities with component

### CommandBuffer Operations
- `commandBuf.putComponent(ref, type, instance)` - Add/update component
- `commandBuf.addComponent(ref, type, instance)` - Add new component
- `commandBuf.getComponent(ref, type)` - Read component

### World Block Operations
```kotlin
world.execute {
    world.setBlock(x, y, z, "BlockName")
}
```

---

## Reference Implementations

- **Glider System** (disabled): `src/main/kotlin/dev/hytalemodding/glider/` - Physics-based gliding with state machine
- **Wire System**: `src/main/kotlin/dev/hytalemodding/wire/` - Block connection visuals
- **Electrical Power System**: `src/main/kotlin/dev/hytalemodding/PoweredComponent.kt` - Power network with sources, transport, and sinks

---

## Electrical Power System

**Location**: `src/main/kotlin/dev/hytalemodding/PoweredComponent.kt`

### Overview

The electrical power system propagates power from source blocks through a network of connected nodes. When a block is placed or destroyed, the system recalculates power state for all affected nodes.

### Components

| Component | Properties | Purpose |
|-----------|------------|---------|
| `PowerSource` | (none) | Marker component identifying blocks that generate power |
| `Powerable` | `powered: Boolean`, `dirty: Boolean` | Tracks power state of a block |
| `ElectricalNode` | `transmits: Boolean`, `onOffVisual: Boolean` | Controls power propagation behavior |

### Node Types

All electrical nodes need both `Powerable` and `ElectricalNode` components. The node type is determined by component configuration:

| Node Type | PowerSource | ElectricalNode.transmits | Behavior |
|-----------|-------------|--------------------------|----------|
| **Source** | ✓ | `true` | Generates power, propagates to neighbors |
| **Transport** | ✗ | `true` | Passes power through to neighbors |
| **Sink** | ✗ | `false` | Receives power but doesn't propagate further |

### Event Queue System

Events are queued when blocks with `Powerable` component are placed/destroyed, then processed during tick phase.

**Components:**
- `StateChangeEvent` - Data class with `pos: Vector3i`, `changeType: StateChangeType`, `timestamp: Long`
- `StateChangeEventQueue` - Resource (store-level singleton) holding pending events
- `PlaceBlockStateChangeEvent` - EntityEventSystem that queues PLACED events
- `BreakBlockStateChangeEvent` - EntityEventSystem that queues DESTROYED events
- `StateChangeProcessor` - TickingSystem that processes queued events

**Flow:**
1. Player places/breaks block → Event fires on `EntityStore`
2. Event system checks if block has `Powerable` component
3. If yes, queues `StateChangeEvent` to `StateChangeEventQueue` resource on `ChunkStore`
4. During tick phase, `StateChangeProcessor` processes all queued events in FIFO order

**Cross-store access:** Event systems on `EntityStore` access `ChunkStore` resources via:
```kotlin
world.chunkStore.store.getResource(ExamplePlugin.stateChangeQueueType)
```

### Two-Phase Power Recalculation Algorithm

When processing a state change event, the system uses two phases:

**Phase 1: `findSourcesAndReset(start, blockComponentChunk, cmdBuf)`**
- BFS from the changed block position
- Traverses through nodes where `ElectricalNode.transmits == true`
- Sets `powered = false` on all visited `Powerable` components
- Collects all `PowerSource` block positions found
- Returns list of source positions

**Phase 2: `propagatePowerFromSource(source, blockComponentChunk, cmdBuf)`**
- Called for each source found in Phase 1
- BFS flood fill from the source position
- Traverses through nodes where `ElectricalNode.transmits == true`
- Sets `powered = true` on all visited `Powerable` components

This ensures the entire connected network is reset and then re-energized from all connected sources.

### Visual State System

**Location**: `src/main/kotlin/dev/hytalemodding/newnet/VisualStateSystem.kt`, `src/main/kotlin/dev/hytalemodding/newnet/VisualState.kt`

Generic system for updating block interaction states (visual appearance) based on power network evaluation.

#### VisualState Component

A generic component (`VisualState`) with a single `state: String` field (default `"default"`). Any block that needs visual transitions gets this component in its prefab. The component stores the desired interaction state name (e.g., `"On"`, `"default"`).

#### How It Works

1. **TopologySystem** evaluates the power network and sets `VisualState.state` for each block type:
   - **Lamp**: `"On"` when `lamp.lit == true`
   - **Relay**: `"On"` when `relay.enabled == true`
   - **Driver (PowerSource)**: `"On"` when `driveState == State4.ONE`
   - **InputPort**: `"On"` when the probed net value is `State4.ONE`
2. Changed positions are added to `StateChangeEventQueue.visualDirtyPositions`
3. **VisualStateSystem** (runs AFTER TopologySystem) reads `VisualState.state` and calls `worldChunk.setBlockInteractionState(pos, blockType, state)`

#### Adding Visual State to a New Block Type

1. Add `VisualState` component to the block's prefab
2. Add an `updateXxxVisuals()` function in `TopologySystem.kt` that reads the block's state and calls `setVisualState(pos, world, queue, newState)`
3. Call it from `TopologySystem.tick()` in Phase 10 alongside the other update functions

#### Helper

```kotlin
// Sets VisualState and marks dirty if changed. Returns true if state changed.
fun setVisualState(pos: Vector3i, world: World, queue: StateChangeEventQueue, newState: String): Boolean
```

### Utility Functions

- `getAllAdjacent(pos: Vector3i)` - Returns 6 adjacent block positions (±X, ±Y, ±Z)
- `getComponentForGlobalXyz(world, pos, type)` - Gets a component from a block at global coordinates

### System Dependencies

Hytale ECS supports system ordering via `getDependencies()`:

```kotlin
override fun getDependencies(): Set<Dependency<ChunkStore>> {
    return setOf(SystemDependency(Order.AFTER, OtherSystem::class.java))
}
```

**Important:** Dependencies only affect ordering within the same system category. Event systems process synchronously when `invoke()` is called - they don't wait for ticking systems.

### Parallel Processing

`EntityTickingSystem` supports parallel processing by overriding:

```kotlin
override fun isParallel(archetypeChunkSize: Int, taskCount: Int): Boolean {
    return useParallel(archetypeChunkSize, taskCount)
}
```

The framework automatically forks `CommandBuffer` for each parallel task and merges results.

---

## Resources (Store-Level State)

Resources are singleton state objects at the store level (vs Components which are per-entity).

### Creating a Resource

```kotlin
class MyResource : Resource<ChunkStore> {
    var myData: Int = 0

    companion object {
        @JvmField
        val CODEC: BuilderCodec<MyResource> =
            BuilderCodec.builder(MyResource::class.java) { MyResource() }.build()
    }

    override fun clone(): Resource<ChunkStore> = MyResource()
}
```

### Registering a Resource

```kotlin
// In ExamplePlugin companion object
lateinit var myResourceType: ResourceType<ChunkStore, MyResource>

// In setup()
myResourceType = chunkStoreRegistry.registerResource(
    MyResource::class.java,
    "MyResource",
    MyResource.CODEC
)
```

### Accessing a Resource

```kotlin
// From a ticking system
val resource = store.getResource(ExamplePlugin.myResourceType)

// From an event system (cross-store)
val resource = world.chunkStore.store.getResource(ExamplePlugin.myResourceType)
```
