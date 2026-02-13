package dev.hytalemodding.wire

import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.universe.world.World
import dev.hytalemodding.ExamplePlugin
import dev.hytalemodding.newnet.*

/**
 * Checks if a block at a position can connect to a wire face.
 * 
 * This function checks the *opposite* face of the neighbor block, because if we're
 * looking from face N, the neighbor needs connectivity on OPPOSITE_FACE[N] to connect back.
 * 
 * For wire-to-wire connections, also checks that channels match (colored wire isolation).
 * Non-wire components (sources, relays, lamps, etc.) are channel-agnostic and connect to any wire.
 * 
 * Example:
 * - Wire has UP face (face 1) → checks neighbor's DOWN face (face 0)
 * - Wire has NORTH face (face 2) → checks neighbor's SOUTH face (face 3)
 * 
 * Connectable blocks include:
 * - PowerConnectable blocks (wires, lamps, relays, power sources, MUXes)
 * - InputPort blocks (always connectable - they probe networks)
 * 
 * @param world The game world
 * @param pos Position of the neighbor block to check
 * @param fromFace The face index we're checking from (on the querying block)
 * @param sourceChannel The wire channel of the querying block (for wire-to-wire isolation)
 * @return true if neighbor is connectable
 */
fun isConnectableAt(world: World, pos: Vector3i, fromFace: Int, sourceChannel: Int): Boolean {
    // Check if it's an InputPort (always connectable)
    val inputPort = getComponentForGlobalXyz(world, pos, ExamplePlugin.inputPortComponentType)
    if (inputPort != null) {
        println("  [Connection] $pos: InputPort (always connectable)")
        return true
    }
    
    // Check if it's a PowerConnectable with the required face enabled
    val conn = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerConnectableComponentType) ?: return false
    val neededFace = OPPOSITE_FACE[fromFace]
    val faceConnectable = conn.facesMask and (1 shl neededFace) != 0
    
    if (!faceConnectable) return false
    
    // If neighbor is a wire, check channel match
    val neighborWire = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerWireComponentType)
    if (neighborWire != null) {
        val channelMatch = neighborWire.channel == sourceChannel
        println("  [Connection] $pos: Wire ch=${neighborWire.channel}, source ch=$sourceChannel, match=$channelMatch")
        return channelMatch
    }
    
    // Non-wire components are channel-agnostic
    println("  [Connection] $pos: PowerConnectable (non-wire), connectable=true")
    return true
}

/**
 * Computes wire connection state by checking all 6 neighbor positions.
 * 
 * Returns a WireConnections data class with boolean flags for each direction.
 * Used by WireLookupTable to determine the correct wire variant and rotation.
 * 
 * For wire-to-wire connections, only connects to wires with matching channels.
 * Non-wire components are channel-agnostic.
 * 
 * @param world The game world
 * @param pos Position of the wire block
 * @param sourceChannel The wire channel (for colored wire isolation)
 * @return WireConnections indicating which directions have connectable neighbors
 */
fun getConnections(world: World, pos: Vector3i, sourceChannel: Int): WireConnections {
    return WireConnections(
        up    = isConnectableAt(world, Vector3i(pos.x, pos.y + 1, pos.z), 1, sourceChannel),   // face 1 = UP
        down  = isConnectableAt(world, Vector3i(pos.x, pos.y - 1, pos.z), 0, sourceChannel),   // face 0 = DOWN
        north = isConnectableAt(world, Vector3i(pos.x, pos.y, pos.z - 1), 2, sourceChannel),   // face 2 = NORTH
        south = isConnectableAt(world, Vector3i(pos.x, pos.y, pos.z + 1), 3, sourceChannel),   // face 3 = SOUTH
        west  = isConnectableAt(world, Vector3i(pos.x - 1, pos.y, pos.z), 4, sourceChannel),   // face 4 = WEST
        east  = isConnectableAt(world, Vector3i(pos.x + 1, pos.y, pos.z), 5, sourceChannel)    // face 5 = EAST
    )
}

/**
 * Checks if a block type ID represents a wire block.
 * 
 * Matches:
 * - "Wire" (base variant)
 * - "*Wire" (base variant with state marker)
 * - "Wire_XX_1", "Wire_UX_2a", etc. (all 24 variants)
 * - "*Wire_XX_1", etc. (variants with state marker)
 * 
 * @param blockTypeId The block type identifier
 * @return true if the block is any wire variant
 */
fun isWireBlock(blockTypeId: String): Boolean {
    val normalized = blockTypeId.removePrefix("*")
    return normalized == "Wire" || normalized.startsWith("Wire_")
}
