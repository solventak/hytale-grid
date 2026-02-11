# Wire Type System Implementation Plan

## Overview

Support two wire types (Signal and Power) as isolated networks. Signal wires only connect to signal wires and non-wire nodes. Power wires only connect to power wires and non-wire nodes.

## Architecture

```
Wire Blocks:      ElectricalNode + Wire(type="Signal"|"Power")
Non-Wire Devices: ElectricalNode only (bridges between networks)
```

## Wire Component (Implemented)

**Location:** `PoweredComponent.kt`

```kotlin
class Wire : Component<ChunkStore> {
    var type: String = "Power"  // "Power" or "Signal"

    companion object {
        @JvmField
        val CODEC: BuilderCodec<Wire> = BuilderCodec.builder(Wire::class.java) { Wire() }
            .appendInherited(
                KeyedCodec("Type", Codec.STRING),
                { obj, value -> obj.type = value },
                { obj -> obj.type },
                { obj, parent -> obj.type = parent.type }
            )
            .add()
            .build()
    }

    override fun clone(): Component<ChunkStore> =
        Wire().also { it.type = this.type }
}
```

---

## Implementation Steps

### Step 1: Register Wire Component

**File:** `ExamplePlugin.kt`

```kotlin
// Add to companion object:
lateinit var wireComponentType: ComponentType<ChunkStore, Wire>

// Add to setup():
wireComponentType = chunkStoreRegistry.registerComponent(
    Wire::class.java,
    "Wire",
    Wire.CODEC
)
```

---

### Step 2: Update WireLookupTable

**File:** `wire/WireLookupTable.kt`

Add wire type parameter to `WireVariantResult`:

```kotlin
data class WireVariantResult(
    val variant: WireVariant,
    val yawRotation: Rotation
) {
    /** Legacy: untyped wire */
    fun getBlockTypeId(): String = variant.modelName

    /** Typed wire: Wire_XX_0_Signal or Wire_XX_0_Power */
    fun getBlockTypeId(wireType: String): String = "${variant.modelName}_$wireType"
}
```

---

### Step 3: Update WireConnectionSystem

**File:** `wire/WireConnectionSystem.kt`

#### 3a. Add helper to get wire type from component

```kotlin
fun getWireTypeFromComponent(
    blockComponentChunk: BlockComponentChunk,
    cmdBuf: CommandBuffer<ChunkStore>,
    x: Int, y: Int, z: Int
): String? {
    val blockIndex = ChunkUtil.indexBlockInColumn(x and 31, y, z and 31)
    val blockRef = blockComponentChunk.getEntityReference(blockIndex) ?: return null
    val wire = cmdBuf.getComponent(blockRef, ExamplePlugin.wireComponentType)
    return wire?.type
}
```

#### 3b. Update block name detection

```kotlin
fun getWireTypeFromBlockName(blockTypeId: String): String? {
    val normalized = blockTypeId.removePrefix("*").removeSuffix("_On")
    return when {
        normalized.endsWith("_Signal") -> "Signal"
        normalized.endsWith("_Power") -> "Power"
        else -> null
    }
}

fun isWireBlock(blockTypeId: String): Boolean {
    val normalized = blockTypeId.removePrefix("*").removeSuffix("_On")
        .removeSuffix("_Signal").removeSuffix("_Power")
    return normalized == "Wire" || normalized.startsWith("Wire_")
}

fun getBaseWireVariant(blockTypeId: String): String =
    blockTypeId.removePrefix("*").removeSuffix("_On")
        .removeSuffix("_Signal").removeSuffix("_Power")
```

#### 3c. Update isConnectableAt()

Add `wireType` parameter for type-aware connection checking:

```kotlin
fun isConnectableAt(
    blockComponentChunk: BlockComponentChunk,
    cmdBuf: CommandBuffer<ChunkStore>,
    x: Int, y: Int, z: Int,
    wireType: String? = null  // NEW PARAMETER
): Boolean {
    val blockIndex = ChunkUtil.indexBlockInColumn(x and 31, y, z and 31)
    val blockRef = blockComponentChunk.getEntityReference(blockIndex) ?: return false

    // Must have ElectricalNode to be connectable
    val node = cmdBuf.getComponent(blockRef, ExamplePlugin.electricalNodeComponentType)
    if (node == null) return false

    // Legacy mode: no type filtering
    if (wireType == null) return true

    // Check if neighbor is a wire
    val neighborWire = cmdBuf.getComponent(blockRef, ExamplePlugin.wireComponentType)
    if (neighborWire != null) {
        // Wire-to-wire: must be same type
        return neighborWire.type == wireType
    }

    // Non-wire node (bridge): always connects
    return true
}
```

#### 3d. Update getConnections()

Pass wire type through:

```kotlin
fun getConnections(
    blockComponentChunk: BlockComponentChunk,
    cmdBuf: CommandBuffer<ChunkStore>,
    x: Int, y: Int, z: Int,
    wireType: String? = null  // NEW PARAMETER
): WireConnections {
    return WireConnections(
        up    = isConnectableAt(blockComponentChunk, cmdBuf, x, y + 1, z, wireType),
        down  = isConnectableAt(blockComponentChunk, cmdBuf, x, y - 1, z, wireType),
        north = isConnectableAt(blockComponentChunk, cmdBuf, x, y, z - 1, wireType),
        east  = isConnectableAt(blockComponentChunk, cmdBuf, x + 1, y, z, wireType),
        south = isConnectableAt(blockComponentChunk, cmdBuf, x, y, z + 1, wireType),
        west  = isConnectableAt(blockComponentChunk, cmdBuf, x - 1, y, z, wireType)
    )
}
```

#### 3e. Update updateWireVisualWithCmdBuf()

Detect wire type and use typed block names:

```kotlin
fun updateWireVisualWithCmdBuf(...) {
    // Get current block's wire type from component
    val wireType = getWireTypeFromComponent(blockComponentChunk, cmdBuf, x, y, z)

    // Get connections filtered by wire type
    val connections = getConnections(blockComponentChunk, cmdBuf, x, y, z, wireType)
    val result = WireLookupTable.lookup(connections)

    // Get block type ID with wire type suffix
    val blockTypeId = if (wireType != null) {
        result.getBlockTypeId(wireType)  // e.g., "Wire_XX_0_Signal"
    } else {
        result.getBlockTypeId()  // Legacy: "Wire_XX_0"
    }

    // ... rest of function uses blockTypeId
}
```

---

### Step 4: Update Power Propagation BFS

**File:** `PoweredComponent.kt`

#### 4a. Add shared helper function

Add a `getConnectableNeighbors()` function that handles neighbor lookup and wire type compatibility in one place:

```kotlin
/**
 * Get all adjacent positions that can connect to the current node.
 * Handles wire type compatibility: wires only connect to same-type wires or non-wire nodes.
 */
fun getConnectableNeighbors(
    current: Vector3i,
    currentRef: Ref<ChunkStore>,
    blockComponentChunk: BlockComponentChunk,
    cmdBuf: CommandBuffer<ChunkStore>
): List<Pair<Vector3i, Ref<ChunkStore>>> {
    val currentWire = cmdBuf.getComponent(currentRef, ExamplePlugin.wireComponentType)

    return getAllAdjacent(current).mapNotNull { adjacent ->
        val adjBlockIndex = ChunkUtil.indexBlockInColumn(adjacent.x and 31, adjacent.y, adjacent.z and 31)
        val adjRef = blockComponentChunk.getEntityReference(adjBlockIndex) ?: return@mapNotNull null

        // Must have ElectricalNode to be part of the network
        val adjNode = cmdBuf.getComponent(adjRef, ExamplePlugin.electricalNodeComponentType)
            ?: return@mapNotNull null

        val adjWire = cmdBuf.getComponent(adjRef, ExamplePlugin.wireComponentType)

        // Check wire type compatibility
        val canConnect = when {
            currentWire == null -> true              // Non-wire connects to all
            adjWire == null -> true                  // Wire connects to non-wire
            else -> currentWire.type == adjWire.type // Wire-to-wire: same type only
        }

        if (canConnect) Pair(adjacent, adjRef) else null
    }
}
```

#### 4b. Update findSourcesAndResetFromMultiple()

Replace the neighbor traversal logic:

```kotlin
// Continue BFS through transmitting nodes
if (node?.transmits == true) {
    for ((adjacent, _) in getConnectableNeighbors(current, blockRef, blockComponentChunk, cmdBuf)) {
        if (adjacent !in visited) {
            visited.add(adjacent)
            queue.add(adjacent)
        }
    }
}
```

#### 4c. Update propagatePowerFromSource()

Same change - replace neighbor traversal:

```kotlin
if (canTransmit) {
    for ((adjacent, _) in getConnectableNeighbors(current, blockRef, blockComponentChunk, cmdBuf)) {
        if (adjacent !in visited) {
            visited.add(adjacent)
            queue.add(adjacent)
        }
    }
}
```

---

## Connection Rules

| Source | Target | Connects? |
|--------|--------|-----------|
| Signal Wire | Signal Wire | YES |
| Signal Wire | Power Wire | NO |
| Signal Wire | Non-Wire Device | YES |
| Power Wire | Power Wire | YES |
| Power Wire | Signal Wire | NO |
| Power Wire | Non-Wire Device | YES |
| Non-Wire Device | Any | YES |

---

## Block Naming Convention

```
Wire_[vertical]_[horizontal]_[Type]
Wire_[vertical]_[horizontal]_[Type]_On  (powered state)

Examples:
  Wire_XX_0_Signal
  Wire_UX_2a_Power
  Wire_UD_4_Signal_On
```

---

## Block JSON Example

```json
{
  "BlockType": {
    "BlockEntity": {
      "Components": {
        "ElectricalNode": {
          "Transmits": true,
          "HasPoweredVisualState": true
        },
        "Wire": {
          "Type": "Signal"
        }
      }
    }
  }
}
```

---

## Files Summary

| File | Status | Changes |
|------|--------|---------|
| `PoweredComponent.kt` | Wire class done | Update BFS functions |
| `ExamplePlugin.kt` | TODO | Register wireComponentType |
| `wire/WireLookupTable.kt` | TODO | Add getBlockTypeId(wireType) |
| `wire/WireConnectionSystem.kt` | TODO | Type-aware connections |
| `generate_wire_blocks.py` | TODO | Generate 48 typed blocks |
