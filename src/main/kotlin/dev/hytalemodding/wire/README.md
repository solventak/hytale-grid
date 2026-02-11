# Wire Connection System

Automatically updates wire block visuals based on adjacent wire connections. When a wire is placed or broken, it and its neighbors update their models and rotations to show correct connections.

## Files

### WireLookupTable.kt
Maps all 64 possible wire connection states (6 faces: U, D, N, E, S, W) to one of 24 canonical model variants + a yaw rotation (0, 90, 180, 270).

**Key types:**
- `WireConnections` - Data class with 6 booleans (up, down, north, east, south, west)
- `WireVariant` - Enum of 24 model variants (e.g., `WIRE_XX_1`, `WIRE_UD_2a`)
- `WireVariantResult` - Contains variant + rotation, with `getEffectiveBlockTypeId()` for fallback handling
- `WireLookupTable` - Singleton with `lookup(connections)` method

**Variant naming:** `Wire_[vertical]_[horizontal]`
- Vertical: `XX` (none), `UX` (up), `XD` (down), `UD` (both)
- Horizontal: `0` (none), `1` (single/N), `2a` (adjacent/NE), `2o` (opposite/NS), `3` (three/NES), `4` (all/NESW)

**Implementation status:** Only `Wire_XX_1` is marked as implemented. All others fall back to the base `Wire` block.

### WireConnectionSystem.kt
Event-based systems that react to block placement/breaking.

**Systems:**
- `WirePlaceEventSystem` - Handles `PlaceBlockEvent`, updates placed wire + 6 neighbors
- `WireBreakEventSystem` - Handles `BreakBlockEvent`, updates 6 neighbors

**Helper functions:**
- `isWireBlock(blockTypeId)` - Checks if block ID is "Wire" or starts with "Wire_"
- `isWireAt(world, x, y, z)` - Checks if position contains a wire
- `getConnections(world, x, y, z)` - Returns `WireConnections` by checking 6 neighbors
- `updateWireVisual(world, x, y, z)` - Looks up correct variant + rotation and places block
- `updateNeighborWires(world, x, y, z)` - Updates all 6 adjacent wires

**Block placement with rotation:**
```kotlin
val chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z)) ?: return
val blockId = BlockType.getAssetMap().getIndex(blockTypeId)
val blockType = BlockType.getAssetMap().getAsset(blockId) ?: return
chunk.setBlock(x, y, z, blockId, blockType, rotation.ordinal, 0, 0)
```

## Registration

Systems are registered in `ExamplePlugin.kt`:
```kotlin
this.entityStoreRegistry.registerSystem(WirePlaceEventSystem())
this.entityStoreRegistry.registerSystem(WireBreakEventSystem())
```

## Related Files

- `generate_wire_blocks.py` (project root) - Generates 24 wire block JSON definitions
- `wire_lookup_table.py` (project root) - Python version of lookup table for reference

## TODO

1. Create the 24 `.blockymodel` files in `Common/Blocks/wire/`
2. Mark additional variants as `implemented = true` in `WireVariant` enum as models are created
3. Test wire connection visuals in-game
