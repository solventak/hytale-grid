package dev.hytalemodding.wire

import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.universe.world.World
import dev.hytalemodding.ExamplePlugin
import dev.hytalemodding.newnet.*

/**
 * Check if the block at [pos] has a PowerConnectable face facing toward [fromFace].
 * [fromFace] is the face index of the *querying* block (the direction we looked),
 * so the neighbor needs connectivity on OPPOSITE_FACE[fromFace].
 */
fun isConnectableAt(world: World, pos: Vector3i, fromFace: Int): Boolean {
    val conn = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerConnectableComponentType) ?: return false
    val neededFace = OPPOSITE_FACE[fromFace]
    return conn.facesMask and (1 shl neededFace) != 0
}

/**
 * Get wire connections for a position by checking all 6 neighbors.
 * Face ordering: DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5
 */
fun getConnections(world: World, pos: Vector3i): WireConnections {
    return WireConnections(
        up    = isConnectableAt(world, Vector3i(pos.x, pos.y + 1, pos.z), 1),   // face 1 = UP
        down  = isConnectableAt(world, Vector3i(pos.x, pos.y - 1, pos.z), 0),   // face 0 = DOWN
        north = isConnectableAt(world, Vector3i(pos.x, pos.y, pos.z - 1), 2),   // face 2 = NORTH
        south = isConnectableAt(world, Vector3i(pos.x, pos.y, pos.z + 1), 3),   // face 3 = SOUTH
        west  = isConnectableAt(world, Vector3i(pos.x - 1, pos.y, pos.z), 4),   // face 4 = WEST
        east  = isConnectableAt(world, Vector3i(pos.x + 1, pos.y, pos.z), 5)    // face 5 = EAST
    )
}

fun isWireBlock(blockTypeId: String): Boolean {
    val normalized = blockTypeId.removePrefix("*")
    return normalized == "Wire" || normalized.startsWith("Wire_")
}
