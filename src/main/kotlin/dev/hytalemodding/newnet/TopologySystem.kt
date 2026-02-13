package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin
import dev.hytalemodding.newnet.shared.State4

/**
 * Kinds of state changes that trigger topology recalculation.
 */
enum class StateChangeKind { PLACED, DESTROYED }

/**
 * Represents a block placement or destruction event that requires power network topology update.
 *
 * @property pos The global block position
 * @property kind Whether the block was placed or destroyed
 */
data class StateChangeEvent(
    val pos: Vector3i,
    val kind: StateChangeKind,
)

/**
 * Represents a single block face belonging to a power network.
 * Used for reverse indexing: which block faces belong to which net ID.
 *
 * @property pos The global block position
 * @property face The face index (0-5: DOWN, UP, NORTH, SOUTH, WEST, EAST)
 */
data class NetEntry(val pos: Vector3i, val face: Int)

/**
 * Store-level resource (singleton) for tracking power network state and pending changes.
 *
 * This resource maintains:
 * - Pending block change events to process
 * - Network ID allocation and membership tracking
 * - Cached 4-state values for each network
 * - Dirty sets for visual and wire shape updates
 */
class StateChangeEventQueue : Resource<ChunkStore> {
    /** Pending block placement/destruction events to process in TopologySystem.tick() */
    val changes: MutableList<StateChangeEvent> = mutableListOf()
    
    /** Auto-incrementing net ID allocator */
    var nextNetId: Int = 0
    
    /** Current 4-state value (ZERO, ONE, HIGH_Z, UNKNOWN_X) for each active network */
    val powerNetValueCache: MutableMap<Int, State4> = mutableMapOf()
    
    /** Reverse index: net ID -> all (pos, face) entries on that net */
    val netMembers: MutableMap<Int, MutableSet<NetEntry>> = mutableMapOf()
    
    /** Cached member count for each net (for fast empty-net checks during clearing) */
    val netMemberCount: MutableMap<Int, Int> = mutableMapOf()
    
    /** Positions whose visual state needs updating (populated by logic, consumed by VisualStateSystem) */
    val visualDirtyPositions: MutableSet<Vector3i> = mutableSetOf()
    
    /** Positions whose wire shape needs updating (populated by event systems, consumed by VisualStateSystem) */
    val wireDirtyPositions: MutableSet<Vector3i> = mutableSetOf()

    companion object {
        @JvmField
        val CODEC: BuilderCodec<StateChangeEventQueue> =
            BuilderCodec.builder(StateChangeEventQueue::class.java) { StateChangeEventQueue() }.build()
    }

    /**
     * Allocates a new unique network ID.
     * @return A new network ID
     */
    fun allocateNetId(): Int = nextNetId++

    /**
     * Registers a block face as a member of a network.
     * @param netId The network ID
     * @param pos The block position
     * @param face The face index (0-5)
     */
    fun addNetMember(netId: Int, pos: Vector3i, face: Int) {
        val added = netMembers.getOrPut(netId) { mutableSetOf() }.add(NetEntry(pos, face))
        if (added) {
            netMemberCount[netId] = (netMemberCount[netId] ?: 0) + 1
        }
    }

    /**
     * Removes all membership records for a network.
     * Called when a network is invalidated and will be rebuilt.
     * @param netId The network ID to remove
     */
    fun removeNet(netId: Int) {
        netMembers.remove(netId)
        netMemberCount.remove(netId)
    }

    override fun clone(): Resource<ChunkStore> = StateChangeEventQueue()
}

/** Sentinel value indicating a block face has no network assignment */
const val UNASSIGNED = -1

/** Human-readable face names for debugging (indexed 0-5) */
val FACE_NAMES = arrayOf("DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST")

/** X-axis offsets for each face direction (indexed by face: 0=DOWN, 1=UP, 2=NORTH, 3=SOUTH, 4=WEST, 5=EAST) */
val FACE_DX = intArrayOf(0, 0, 0, 0, -1, 1)

/** Y-axis offsets for each face direction */
val FACE_DY = intArrayOf(-1, 1, 0, 0, 0, 0)

/** Z-axis offsets for each face direction */
val FACE_DZ = intArrayOf(0, 0, -1, 1, 0, 0)

/** Maps each face to its opposite face (e.g., UP->DOWN, NORTH->SOUTH) */
val OPPOSITE_FACE = intArrayOf(1, 0, 3, 2, 5, 4)

/**
 * Computes the position of the neighbor block across a given face,
 * and the face index on that neighbor pointing back.
 *
 * @param pos The starting block position
 * @param face The face index to cross (0-5)
 * @return Pair of (neighbor position, face index on neighbor pointing back to pos)
 */
fun neighborOfFace(pos: Vector3i, face: Int): Pair<Vector3i, Int> {
    return Pair(
        Vector3i(pos.x + FACE_DX[face], pos.y + FACE_DY[face], pos.z + FACE_DZ[face]),
        OPPOSITE_FACE[face]
    )
}

/**
 * Expands a set of seed positions to include all immediately adjacent blocks (6 neighbors each).
 * Used to widen the "dirty zone" to catch blocks that might be affected by topology changes.
 *
 * @param seeds Initial set of block positions
 * @return Expanded set including seeds + all their neighbors
 */
/**
 * BFS expansion through physically connected PowerConnectable blocks from seeds.
 *
 * Unlike net-membership expansion, this follows physical adjacency through blocks
 * that have PowerConnectable components, regardless of current net assignment state.
 * This ensures that even if nets were cleared and never rebuilt, the expansion still
 * reaches all connected InputPorts and their driver blocks (relays, MUXes, sources).
 *
 * Also includes direct neighbors (1-hop) of seeds to catch non-PowerConnectable
 * blocks like InputPorts adjacent to the seed.
 *
 * @param seeds Initial positions to expand from
 * @param worldAccess The world access interface
 * @return Expanded set including all physically reachable PowerConnectable blocks
 */
fun expandThroughConnected(seeds: Set<Vector3i>, worldAccess: WorldAccess): Set<Vector3i> {
    val visited = mutableSetOf<Vector3i>()
    val queue = ArrayDeque<Vector3i>()

    // Start with seeds + their direct neighbors
    val initial = expand(seeds, 1)
    for (pos in initial) {
        visited.add(pos)
        queue.add(pos)
    }

    // BFS through PowerConnectable blocks
    while (queue.isNotEmpty()) {
        val pos = queue.removeFirst()
        val conn = worldAccess.getComponent(pos, ExamplePlugin.powerConnectableComponentType) ?: continue

        for (face in 0..5) {
            if (conn.facesMask and (1 shl face) == 0) continue
            val (npos, nface) = neighborOfFace(pos, face)
            if (npos in visited) continue

            // Check if neighbor has a connectable face pointing back
            val nconn = worldAccess.getComponent(npos, ExamplePlugin.powerConnectableComponentType)
            if (nconn != null && nconn.facesMask and (1 shl nface) != 0) {
                visited.add(npos)
                queue.add(npos)
                continue
            }

            // Also follow through InputPorts (they're not PowerConnectable but bridge connectivity)
            val ip = worldAccess.getComponent(npos, ExamplePlugin.inputPortComponentType)
            if (ip != null) {
                visited.add(npos)
                // Add the driver-side block too
                val (driverPos, _) = neighborOfFace(npos, ip.driverSideFace)
                if (driverPos !in visited) {
                    visited.add(driverPos)
                    queue.add(driverPos)
                }
                // Add the probe-side block too
                val probeFace = OPPOSITE_FACE[ip.driverSideFace]
                val (probePos, _) = neighborOfFace(npos, probeFace)
                if (probePos !in visited) {
                    visited.add(probePos)
                    queue.add(probePos)
                }
            }
        }
    }
    return visited
}

fun expand(seeds: Set<Vector3i>, hops: Int = 1): Set<Vector3i> {
    var current = seeds.toMutableSet()
    repeat(hops) {
        val next = mutableSetOf<Vector3i>()
        next.addAll(current)
        for (pos in current) {
            for (face in 0..5) {
                next.add(Vector3i(pos.x + FACE_DX[face], pos.y + FACE_DY[face], pos.z + FACE_DZ[face]))
            }
        }
        current = next
    }
    return current
}

/**
 * Extends seed blocks to include driver-side neighbors of any InputPorts in the set.
 * 
 * When an InputPort's probed net changes, the relay/source on its driver side must be 
 * re-evaluated. Without this expansion, a relay 2 hops from a changed wire 
 * (wire → InputPort → relay) would be missed during topology rebuild.
 *
 * @param seedBlocks Initial set of dirty block positions
 * @param worldAccess The world access interface
 * @return Expanded set including InputPort driver-side blocks
 */
fun expandForInputPortDrivers(seedBlocks: Set<Vector3i>, worldAccess: WorldAccess): Set<Vector3i> {
    val expanded = seedBlocks.toMutableSet()
    for (pos in seedBlocks) {
        val inputPort = worldAccess.getComponent(pos, ExamplePlugin.inputPortComponentType) ?: continue
        val (driverPos, _) = neighborOfFace(pos, inputPort.driverSideFace)
        expanded.add(driverPos)
    }
    return expanded
}

/**
 * Scans neighbors of dirty blocks for InputPorts and includes the InputPort plus its
 * driver-side block (relay/source).
 * 
 * This ensures relays are re-evaluated when a wire adjacent to their InputPort changes,
 * even if the relay block itself isn't on an affected net. The iterative expansion in
 * TopologySystem continues until no new InputPorts are discovered.
 *
 * @param dirtyBlocks Set of blocks known to need topology recalculation
 * @param worldAccess The world access interface
 * @return Expanded set including adjacent InputPorts and their driver blocks
 */
fun expandForAdjacentInputPorts(dirtyBlocks: Set<Vector3i>, worldAccess: WorldAccess): Set<Vector3i> {
    val expanded = dirtyBlocks.toMutableSet()
    for (pos in dirtyBlocks) {
        for (face in 0..5) {
            val (npos, _) = neighborOfFace(pos, face)
            if (npos in expanded) continue
            val inputPort = worldAccess.getComponent(npos, ExamplePlugin.inputPortComponentType) ?: continue
            expanded.add(npos)
            val (driverPos, _) = neighborOfFace(npos, inputPort.driverSideFace)
            expanded.add(driverPos)
        }
    }
    return expanded
}

// getComponentForGlobalXyz has been moved to WorldAccess.kt and is now a private helper
// used only by HytaleWorldAccess. All topology code uses WorldAccess.getComponent() instead.

// --- Phase 1: Clear nets from seeds ---

/**
 * Invalidates all power networks that touch any of the seed blocks.
 * 
 * For each seed block, this function:
 * 1. Collects all network IDs assigned to any face of the block
 * 2. For each affected network, resets all member faces to UNASSIGNED
 * 3. Removes the network from the membership index
 * 
 * This is the first phase of topology recalculation. After nets are cleared,
 * Phase 2 (rebuildPowerTopology) will re-assign nets via flood fill.
 *
 * @param seedBlocks Blocks that triggered topology recalculation (placed/broken/toggled)
 * @param world The game world
 * @param queue State queue holding network membership data
 * @return All block positions that had their net assignments cleared (includes all members of cleared nets)
 */
fun clearNetsFromSeeds(seedBlocks: Set<Vector3i>, worldAccess: WorldAccess, queue: StateChangeEventQueue): Set<Vector3i> {
    // Step 1: Collect all nets that touch any seed block
    val netsToInvalidate = mutableSetOf<Int>()
    for (pos in seedBlocks) {
        val ids = worldAccess.getComponent(pos, ExamplePlugin.powerNetIdsComponentType) ?: continue
        // Check all 6 faces of this block
        for (face in 0..5) {
            val netId = ids.get(face)
            if (netId != UNASSIGNED) {
                netsToInvalidate.add(netId)
            }
        }
    }

    val allDirtyPositions = mutableSetOf<Vector3i>()
    allDirtyPositions.addAll(seedBlocks)

    // Step 2: Clear all faces belonging to invalidated nets
    for (netId in netsToInvalidate) {
        // Skip empty nets (optimization: avoids iterating through empty member sets)
        val memberCount = queue.netMemberCount[netId] ?: 0
        if (memberCount == 0) {
            queue.removeNet(netId)
            continue
        }
        
        val members = queue.netMembers[netId] ?: continue
        val clearedFaces = mutableListOf<String>()
        for (entry in members) {
            allDirtyPositions.add(entry.pos)
            val ids = worldAccess.getComponent(entry.pos, ExamplePlugin.powerNetIdsComponentType) ?: continue
            val oldId = ids.get(entry.face)
            ids.set(entry.face, UNASSIGNED)  // Reset to unassigned
            if (oldId != UNASSIGNED) {
                clearedFaces.add("${entry.pos} ${FACE_NAMES[entry.face]}(was=$oldId)")
            }
        }
        if (clearedFaces.isNotEmpty()) {
        }
        // Remove from membership index (will be rebuilt in Phase 2)
        queue.removeNet(netId)
    }

    return allDirtyPositions
}

// --- Phase 2: Rebuild topology via flood fill ---

/**
 * Rebuilds power network topology for all dirty blocks via flood-fill.
 * 
 * For each connectable face on each dirty block that doesn't already have a net assignment,
 * this function allocates a new network ID and performs a breadth-first flood fill to assign
 * that ID to all reachable faces.
 * 
 * The flood fill respects:
 * - Face connectivity (PowerConnectable.facesMask)
 * - Wire internal bridging (all faces connect)
 * - Relay internal bridging (only when enabled, and only non-control faces connect)
 * - Cross-block adjacency (face-to-opposite-face)
 *
 * @param dirtyBlocks Blocks needing network assignment
 * @param worldAccess The world access interface
 * @param queue State queue for allocating IDs and tracking membership
 */
fun rebuildPowerTopology(dirtyBlocks: Set<Vector3i>, worldAccess: WorldAccess, queue: StateChangeEventQueue) {
    for (pos in dirtyBlocks) {
        val conn = worldAccess.getComponent(pos, ExamplePlugin.powerConnectableComponentType) ?: continue
        val ids = worldAccess.getComponent(pos, ExamplePlugin.powerNetIdsComponentType) ?: continue

        // For MUX blocks, only seed faces in the conduction mask (not all facesMask faces)
        val muxComp = worldAccess.getComponent(pos, ExamplePlugin.mux2PartComponentType)
        val seedMask = if (muxComp != null && muxComp.isComplete && !muxComp.isDisconnected) {
            getMuxConductionMask(pos, muxComp, worldAccess)
        } else {
            conn.facesMask
        }

        for (face in 0..5) {
            if (seedMask and (1 shl face) == 0) continue
            if (ids.get(face) != UNASSIGNED) continue

            val netId = queue.allocateNetId()
            floodFillPower(pos, face, netId, worldAccess, queue)
        }
    }
}

/**
 * Performs breadth-first flood fill to assign a network ID to all reachable block faces.
 * 
 * This function implements the core connectivity rules:
 * 1. **Wire blocks**: Star-connect all 6 faces (all faces share the same net)
 * 2. **Relay blocks (enabled)**: Star-connect all non-control faces (control faces isolated)
 * 3. **Relay blocks (disabled)**: No internal connectivity
 * 4. **Cross-block adjacency**: A face connects to the opposite face of the neighbor block
 * 
 * The BFS queue contains (position, face) pairs. Each iteration:
 * - Assigns the net ID to the current face
 * - Spreads to other faces on the same block (if wire or enabled relay)
 * - Spreads to the opposite face of the neighbor block
 *
 * @param startPos Starting block position
 * @param startFace Starting face index (0-5)
 * @param netId The network ID to assign
 * @param worldAccess The world access interface
 * @param queue State queue for membership tracking
 */
fun floodFillPower(startPos: Vector3i, startFace: Int, netId: Int, worldAccess: WorldAccess, queue: StateChangeEventQueue) {
    val bfsQueue = ArrayDeque<Pair<Vector3i, Int>>()
    bfsQueue.add(Pair(startPos, startFace))

    while (bfsQueue.isNotEmpty()) {
        val (pos, face) = bfsQueue.removeFirst()

        val conn = worldAccess.getComponent(pos, ExamplePlugin.powerConnectableComponentType) ?: continue
        val ids = worldAccess.getComponent(pos, ExamplePlugin.powerNetIdsComponentType) ?: continue

        // Skip if face isn't connectable or already assigned
        // For MUX blocks, use conduction mask instead of static facesMask
        val muxCheck = worldAccess.getComponent(pos, ExamplePlugin.mux2PartComponentType)
        val effectiveMask = if (muxCheck != null && muxCheck.isComplete && !muxCheck.isDisconnected) {
            getMuxConductionMask(pos, muxCheck, worldAccess)
        } else {
            conn.facesMask
        }
        if (effectiveMask and (1 shl face) == 0) continue
        if (ids.get(face) != UNASSIGNED) continue

        // Assign this face to the network
        ids.set(face, netId)
        queue.addNetMember(netId, pos, face)

        // Internal connectivity rule 1: PowerWire bridges all connectable faces
        val isWire = worldAccess.getComponent(pos, ExamplePlugin.powerWireComponentType) != null
        if (isWire) {
            for (face2 in 0..5) {
                if (face2 == face) continue  // Skip self
                if (conn.facesMask and (1 shl face2) != 0 && ids.get(face2) == UNASSIGNED) {
                    bfsQueue.add(Pair(pos, face2))
                }
            }
        }

        // Internal connectivity rule 2: Enabled relay star-connects all conduction (non-control) faces
        val relay = worldAccess.getComponent(pos, ExamplePlugin.relayComponentType)
        if (relay != null && relay.enabled) {
            val controlMask = getControlFaces(pos, worldAccess)  // Bitmask of faces with InputPorts
            val faceIsControl = controlMask and (1 shl face) != 0
            // Only spread from conduction faces (control faces are isolated)
            if (!faceIsControl) {
                for (face2 in 0..5) {
                    if (face2 == face) continue  // Skip self
                    val face2IsControl = controlMask and (1 shl face2) != 0
                    // Only spread to other conduction faces
                    if (!face2IsControl && conn.facesMask and (1 shl face2) != 0 && ids.get(face2) == UNASSIGNED) {
                        bfsQueue.add(Pair(pos, face2))
                    }
                }
            }
        }

        // Internal connectivity rule 3: Complete MUX conducts through selected input to output
        val mux = worldAccess.getComponent(pos, ExamplePlugin.mux2PartComponentType)
        if (mux != null && mux.isComplete && !mux.isDisconnected) {
            val conductionMask = getMuxConductionMask(pos, mux, worldAccess)
            val faceInMask = conductionMask and (1 shl face) != 0
            if (faceInMask) {
                for (face2 in 0..5) {
                    if (face2 == face) continue
                    if (conductionMask and (1 shl face2) == 0) continue
                    val existingNet = ids.get(face2)
                    if (existingNet == UNASSIGNED) {
                        bfsQueue.add(Pair(pos, face2))
                    } else if (existingNet != netId) {
                        // Merge: reassign all members of existingNet to netId
                        val members = queue.netMembers[existingNet]
                        if (members != null) {
                            for ((mPos, mFace) in members.toList()) {
                                val mIds = worldAccess.getComponent(mPos, ExamplePlugin.powerNetIdsComponentType)
                                mIds?.set(mFace, netId)
                                queue.addNetMember(netId, mPos, mFace)
                            }
                            queue.removeNet(existingNet)
                        }
                    }
                }
            }
        }

        // External adjacency: Spread to the opposite face of the neighbor block
        val (npos, nface) = neighborOfFace(pos, face)
        val nconn = worldAccess.getComponent(npos, ExamplePlugin.powerConnectableComponentType)
        val nids = worldAccess.getComponent(npos, ExamplePlugin.powerNetIdsComponentType)

        if (nconn != null && nids != null && nconn.facesMask and (1 shl nface) != 0 && nids.get(nface) == UNASSIGNED) {
            bfsQueue.add(Pair(npos, nface))
        }

        // MUX InputPort pass-through (both directions):
        // Direction 1: MUX face -> InputPort -> far wire
        if (muxCheck != null && muxCheck.isComplete && !muxCheck.isDisconnected) {
            val neighborIp = worldAccess.getComponent(npos, ExamplePlugin.inputPortComponentType)
            if (neighborIp != null && neighborIp.driverSideFace == nface) {
                val farFace = OPPOSITE_FACE[nface]
                val (farPos, farNface) = neighborOfFace(npos, farFace)
                val farConn = worldAccess.getComponent(farPos, ExamplePlugin.powerConnectableComponentType)
                val farIds = worldAccess.getComponent(farPos, ExamplePlugin.powerNetIdsComponentType)
                if (farConn != null && farIds != null && farConn.facesMask and (1 shl farNface) != 0 && farIds.get(farNface) == UNASSIGNED) {
                    bfsQueue.add(Pair(farPos, farNface))
                }
            }
        }

        // Direction 2: wire face -> InputPort -> MUX input face
        // Check if the neighbor is an InputPort whose far side touches a conducting MUX face
        if (nconn == null) {
            val neighborIp = worldAccess.getComponent(npos, ExamplePlugin.inputPortComponentType)
            if (neighborIp != null && neighborIp.driverSideFace != nface) {
                // We're on the output side of the InputPort. Check if the driver side touches a MUX.
                val driverFace = neighborIp.driverSideFace
                val (muxPos, muxNface) = neighborOfFace(npos, driverFace)
                val muxPart = worldAccess.getComponent(muxPos, ExamplePlugin.mux2PartComponentType)
                if (muxPart != null && muxPart.isComplete && !muxPart.isDisconnected) {
                    val muxMask = getMuxConductionMask(muxPos, muxPart, worldAccess)
                    if (muxMask and (1 shl muxNface) != 0) {
                        val muxIds = worldAccess.getComponent(muxPos, ExamplePlugin.powerNetIdsComponentType)
                        if (muxIds != null && muxIds.get(muxNface) == UNASSIGNED) {
                            bfsQueue.add(Pair(muxPos, muxNface))
                        }
                    }
                }
            }
        }
    }
}

// --- Phase 3: Collect dirty net IDs ---

/**
 * Collects all unique network IDs present on any face of the dirty blocks.
 * 
 * These net IDs will need their values re-evaluated in the delta-cycle loop.
 *
 * @param dirtyBlocks Blocks that were affected by topology changes
 * @param worldAccess The world access interface
 * @return Set of all network IDs that need evaluation
 */
fun collectNetIds(dirtyBlocks: Set<Vector3i>, worldAccess: WorldAccess): Set<Int> {
    val netIds = mutableSetOf<Int>()
    for (pos in dirtyBlocks) {
        val ids = worldAccess.getComponent(pos, ExamplePlugin.powerNetIdsComponentType) ?: continue
        for (face in 0..5) {
            val id = ids.get(face)
            if (id != UNASSIGNED) {
                netIds.add(id)
            }
        }
    }
    return netIds
}

// --- Phase 4: Evaluate sources (inverter-source logic) ---

/**
 * Computes the drive state for an inverting power source (NOT gate).
 * 
 * A PowerSource acts as a multi-input inverter:
 * - Scans all adjacent InputPorts whose driverSideFace points toward this source
 * - Reads the 4-state value of each InputPort's probed network
 * - Applies inversion logic:
 *   - No inputs → drive ONE (default-on)
 *   - Any input is UNKNOWN_X → drive UNKNOWN_X (propagate error)
 *   - Mix of ONE and ZERO → drive UNKNOWN_X (conflict)
 *   - All inputs ONE → drive ZERO (invert)
 *   - All inputs ZERO or HIGH_Z → drive ONE (invert)
 *
 * @param sourcePos Position of the PowerSource block
 * @param worldAccess The world access interface
 * @param queue State queue with current net values
 * @return The computed 4-state drive value (ZERO, ONE, HIGH_Z, or UNKNOWN_X)
 */
fun computeInverterDrive(sourcePos: Vector3i, worldAccess: WorldAccess, queue: StateChangeEventQueue): State4 {
    val conn = worldAccess.getComponent(sourcePos, ExamplePlugin.powerConnectableComponentType)
        ?: return State4.ONE

    val inputValues = mutableListOf<State4>()

    // Scan all 6 faces for adjacent InputPorts
    for (face in 0..5) {
        if (conn.facesMask and (1 shl face) == 0) continue  // Face not connectable
        val (npos, nface) = neighborOfFace(sourcePos, face)
        val inputPort = worldAccess.getComponent(npos, ExamplePlugin.inputPortComponentType)
            ?: continue  // No InputPort here
        // Verify the InputPort's driverSideFace points back at this source
        if (inputPort.driverSideFace != nface) continue

        // Probe the block adjacent to the InputPort's output face.
        // InputPort itself has no PowerNetIds — it's a transparent probe.
        val outputFace = OPPOSITE_FACE[inputPort.driverSideFace]
        val (probePos, probeFace) = neighborOfFace(npos, outputFace)
        val probeIds = worldAccess.getComponent(probePos, ExamplePlugin.powerNetIdsComponentType)
        val inputNetId = probeIds?.get(probeFace) ?: UNASSIGNED
        val inputNetValue = if (inputNetId != UNASSIGNED) {
            queue.powerNetValueCache[inputNetId] ?: State4.HIGH_Z
        } else {
            State4.HIGH_Z
        }
        inputValues.add(inputNetValue)
    }

    // Apply NOR-gate inversion rules (multi-input inverter)
    if (inputValues.isEmpty()) return State4.ONE  // No inputs → default-on
    if (inputValues.any { it == State4.UNKNOWN_X }) return State4.UNKNOWN_X  // Propagate error
    val hasOne = inputValues.any { it == State4.ONE }
    val hasZero = inputValues.any { it == State4.ZERO }
    if (hasOne && hasZero) return State4.UNKNOWN_X  // Conflict: both active and inactive
    if (hasOne) return State4.ZERO  // Any input is ONE → drive ZERO (invert)
    return State4.ONE               // All inputs ZERO or HIGH_Z → drive ONE (invert)
}

/**
 * Re-evaluates all PowerSource blocks in the dirty set and updates their drive state.
 * 
 * Called during each delta-cycle iteration. Sources read their InputPort values
 * and compute new drive states via multi-input inversion.
 *
 * @param dirtyBlocks Blocks affected by this topology round
 * @param worldAccess The world access interface
 * @param queue State queue with current net values and caching
 */
fun evaluateSources(dirtyBlocks: Set<Vector3i>, worldAccess: WorldAccess, queue: StateChangeEventQueue) {
    for (pos in dirtyBlocks) {
        val source = worldAccess.getComponent(pos, ExamplePlugin.powerSourceComponentType) ?: continue
        
        // Skip inverter evaluation for Lever blocks - they're manually controlled
        val lever = worldAccess.getComponent(pos, ExamplePlugin.leverComponentType)
        if (lever != null) {
            // Lever state was already set by LeverInteractionSystem, don't overwrite
            source.lastDriveState = source.driveState
            continue
        }
        
        source.lastDriveState = source.driveState
        source.driveState = computeInverterDrive(pos, worldAccess, queue)
    }
}

// --- Phase 5: Resolve nets (multi-driver 4-state) ---

/**
 * Resolves the final 4-state value for each dirty network by combining all driver contributions.
 * 
 * For each network:
 * 1. Collects the driveState of all PowerSource blocks connected to that net
 * 2. Applies 4-state resolution rules:
 *    - No drivers → HIGH_Z (floating)
 *    - Any driver is UNKNOWN_X → UNKNOWN_X (error propagates)
 *    - Multiple distinct non-Z drivers (e.g., ONE + ZERO) → UNKNOWN_X (short circuit)
 *    - All non-Z drivers agree → that value (ZERO or ONE)
 * 
 * This implements Verilog-style tri-state bus resolution, allowing multiple drivers
 * but detecting conflicts.
 *
 * @param dirtyNetIds Network IDs to resolve
 * @param dirtyBlocks Blocks in the dirty zone (used to find sources)
 * @param worldAccess The world access interface
 * @param queue State queue; updated with resolved net values
 */
fun resolveNets(dirtyNetIds: Set<Int>, dirtyBlocks: Set<Vector3i>, worldAccess: WorldAccess, queue: StateChangeEventQueue) {
    val netDrivers = mutableMapOf<Int, MutableList<State4>>()
    for (netId in dirtyNetIds) {
        netDrivers[netId] = mutableListOf()
    }

    for (pos in dirtyBlocks) {
        val source = worldAccess.getComponent(pos, ExamplePlugin.powerSourceComponentType) ?: continue
        val ids = worldAccess.getComponent(pos, ExamplePlugin.powerNetIdsComponentType) ?: continue
        val conn = worldAccess.getComponent(pos, ExamplePlugin.powerConnectableComponentType) ?: continue

        for (face in 0..5) {
            if (conn.facesMask and (1 shl face) == 0) continue
            val netId = ids.get(face)
            if (netId != UNASSIGNED && netId in dirtyNetIds) {
                netDrivers[netId]?.add(source.driveState)
            }
        }
    }

    for (netId in dirtyNetIds) {
        val drivers = netDrivers[netId] ?: emptyList()
        queue.powerNetValueCache[netId] = State4.resolve(drivers)
    }
}

// --- Phase 6: Destroy blocks on X nets ---

/**
 * Destroys all blocks connected to networks with UNKNOWN_X value (error/conflict state).
 * 
 * This implements the "magic smoke" failure mode: when a network has conflicting drivers
 * or reaches an unstable oscillating state, all blocks on that net are destroyed and
 * turned into Empty blocks.
 * 
 * After destruction, DESTROYED events are queued so topology rebuilds next tick for
 * any survivors adjacent to the destroyed blocks.
 *
 * @param dirtyNetIds Networks to check for UNKNOWN_X state
 * @param worldAccess The world access interface
 * @param queue State queue; DESTROYED events are added for each destroyed block
 */
fun destroyXNets(dirtyNetIds: Set<Int>, world: World, queue: StateChangeEventQueue) {
    val positionsToDestroy = mutableSetOf<Vector3i>()
    for (netId in dirtyNetIds) {
        val value = queue.powerNetValueCache[netId] ?: continue
        if (value != State4.UNKNOWN_X) continue
        val members = queue.netMembers[netId] ?: continue
        for (entry in members) {
            positionsToDestroy.add(entry.pos)
        }
    }
    if (positionsToDestroy.isEmpty()) return

    world.execute {
        for (pos in positionsToDestroy) {
            world.setBlock(pos.x, pos.y, pos.z, "Empty")
        }
    }
    // Queue DESTROYED events so topology rebuilds next tick
    for (pos in positionsToDestroy) {
        queue.changes.add(StateChangeEvent(pos, StateChangeKind.DESTROYED))
    }
}

// --- Phase 7: Update visual states ---

/**
 * Updates a block's VisualState component and marks it dirty if the state changed.
 * 
 * The VisualStateSystem (which runs after TopologySystem) will read these dirty
 * positions and apply the block interaction state changes.
 *
 * @param pos Block position
 * @param world The game world
 * @param queue State queue; position added to visualDirtyPositions if state changed
 * @param newState The desired interaction state name (e.g., "On", "default")
 * @return true if the state changed, false if it was already the target state
 */
fun setVisualState(pos: Vector3i, worldAccess: WorldAccess, queue: StateChangeEventQueue, newState: String): Boolean {
    val vs = worldAccess.getComponent(pos, ExamplePlugin.visualStateComponentType) ?: return false
    if (vs.state != newState) {
        vs.state = newState
        queue.visualDirtyPositions.add(pos)
        return true
    }
    return false
}

/**
 * Updates visual state for all Lamp blocks in the dirty set.
 * 
 * A lamp is lit if ANY of its connected faces belongs to a network with value ONE.
 * The VisualState is set to "On" if lit, "default" otherwise.
 *
 * @param dirtyBlocks Blocks to check for Lamp components
 * @param worldAccess The world access interface
 * @param queue State queue with current net values
 */
fun updateLamps(dirtyBlocks: Set<Vector3i>, worldAccess: WorldAccess, queue: StateChangeEventQueue) {
    for (pos in dirtyBlocks) {
        val lamp = worldAccess.getComponent(pos, ExamplePlugin.lampComponentType) ?: continue
        val ids = worldAccess.getComponent(pos, ExamplePlugin.powerNetIdsComponentType) ?: continue
        val oldLit = lamp.lit
        var lit = false
        var matchedFace: Int? = null
        val faceNets = mutableListOf<String>()
        for (face in 0..5) {
            val netId = ids.get(face)
            if (netId != UNASSIGNED) {
                val netValue = queue.powerNetValueCache[netId] ?: State4.HIGH_Z
                val powered = (netValue == State4.ONE)
                faceNets.add("${FACE_NAMES[face]}->net$netId(${netValue.name})")
                if (powered && !lit) {
                    lit = true
                    matchedFace = face
                }
            }
        }
        lamp.lit = lit
        setVisualState(pos, worldAccess, queue, if (lit) "On" else "default")
        if (faceNets.isNotEmpty()) {
        }
    }
}

/**
 * Updates visual state for all Relay blocks in the dirty set.
 * 
 * A relay's visual reflects its conducting state (enabled/disabled), NOT its powered state.
 * Visual is "On" when enabled (conduction active), "default" when disabled.
 *
 * @param dirtyBlocks Blocks to check for Relay components
 * @param worldAccess The world access interface
 * @param queue State queue for marking visual dirty positions
 */
fun updateRelayVisuals(dirtyBlocks: Set<Vector3i>, worldAccess: WorldAccess, queue: StateChangeEventQueue) {
    for (pos in dirtyBlocks) {
        val relay = worldAccess.getComponent(pos, ExamplePlugin.relayComponentType) ?: continue
        val newState = if (relay.enabled) "On" else "default"
        if (setVisualState(pos, worldAccess, queue, newState)) {
        }
    }
}

/**
 * Updates visual state for all PowerSource (driver) blocks in the dirty set.
 * 
 * A PowerSource's visual reflects its drive output. Visual is "On" when driving ONE,
 * "default" for all other states (ZERO, HIGH_Z, UNKNOWN_X).
 *
 * @param dirtyBlocks Blocks to check for PowerSource components
 * @param worldAccess The world access interface
 * @param queue State queue for marking visual dirty positions
 */
fun updateDriverVisuals(dirtyBlocks: Set<Vector3i>, worldAccess: WorldAccess, queue: StateChangeEventQueue) {
    for (pos in dirtyBlocks) {
        val source = worldAccess.getComponent(pos, ExamplePlugin.powerSourceComponentType) ?: continue
        val newState = if (source.driveState == State4.ONE) "On" else "default"
        if (setVisualState(pos, worldAccess, queue, newState)) {
        }
    }
}

/**
 * Updates visual state for all Lever blocks in the dirty set.
 * 
 * A Lever's visual reflects its toggle state. Visual is "On" when toggled on,
 * "default" when toggled off.
 *
 * @param dirtyBlocks Blocks to check for Lever components
 * @param worldAccess The world access interface
 * @param queue State queue for marking visual dirty positions
 */
fun updateLeverVisuals(dirtyBlocks: Set<Vector3i>, worldAccess: WorldAccess, queue: StateChangeEventQueue) {
    for (pos in dirtyBlocks) {
        val lever = worldAccess.getComponent(pos, ExamplePlugin.leverComponentType) ?: continue
        val newState = if (lever.isOn) "On" else "default"
        if (setVisualState(pos, worldAccess, queue, newState)) {
        }
    }
}

/**
 * Updates visual state for all PowerWire blocks in the dirty set.
 * 
 * A wire is powered if ANY of its connected network IDs has value ONE.
 * Visual is "On" when powered, "default" otherwise.
 *
 * @param dirtyBlocks Blocks to check for PowerWire components
 * @param worldAccess The world access interface
 * @param queue State queue with current net values
 */
fun updateWireVisuals(dirtyBlocks: Set<Vector3i>, worldAccess: WorldAccess, queue: StateChangeEventQueue) {
    for (pos in dirtyBlocks) {
        val wire = worldAccess.getComponent(pos, ExamplePlugin.powerWireComponentType) ?: continue
        val ids = worldAccess.getComponent(pos, ExamplePlugin.powerNetIdsComponentType) ?: continue
        var powered = false
        for (face in 0..5) {
            val netId = ids.get(face)
            if (netId != UNASSIGNED) {
                val netValue = queue.powerNetValueCache[netId] ?: State4.HIGH_Z
                if (netValue == State4.ONE) { powered = true; break }
            }
        }
        if (setVisualState(pos, worldAccess, queue, if (powered) "On" else "default")) {
        }
    }
}

/**
 * Updates visual state for all InputPort blocks in the dirty set.
 * 
 * An InputPort's visual reflects the value of the network it probes (on its output face,
 * opposite the driver side). Visual is "On" when probed net is ONE, "default" otherwise.
 *
 * @param dirtyBlocks Blocks to check for InputPort components
 * @param worldAccess The world access interface
 * @param queue State queue with current net values
 */
fun updateInputPortVisuals(dirtyBlocks: Set<Vector3i>, worldAccess: WorldAccess, queue: StateChangeEventQueue) {
    for (pos in dirtyBlocks) {
        val inputPort = worldAccess.getComponent(pos, ExamplePlugin.inputPortComponentType) ?: continue
        // Probe the net on the InputPort's output face (opposite of driver side)
        val outputFace = OPPOSITE_FACE[inputPort.driverSideFace]
        val (probePos, probeFace) = neighborOfFace(pos, outputFace)
        val probeIds = worldAccess.getComponent(probePos, ExamplePlugin.powerNetIdsComponentType)
        val netId = probeIds?.get(probeFace) ?: UNASSIGNED
        val netValue = if (netId != UNASSIGNED) {
            queue.powerNetValueCache[netId] ?: State4.HIGH_Z
        } else {
            State4.HIGH_Z
        }
        val newState = if (netValue == State4.ONE) "On" else "default"
        if (setVisualState(pos, worldAccess, queue, newState)) {
        }
    }
}

/**
 * Updates visual state for all MUX blocks in the dirty set.
 *
 * The MUX block that is receiving the selected input and passing it through turns "On".
 * Specifically: the block whose input face is currently conducting (the selected side)
 * lights up. The non-selected block stays "default".
 *
 * If the MUX is incomplete or disconnected, both blocks show "default".
 *
 * @param dirtyBlocks Blocks to check for Mux2Part components
 * @param worldAccess The world access interface
 * @param queue State queue for marking visual dirty positions
 */
fun updateMuxVisuals(dirtyBlocks: Set<Vector3i>, worldAccess: WorldAccess, queue: StateChangeEventQueue) {
    val processed = mutableSetOf<Vector3i>()
    for (pos in dirtyBlocks) {
        if (pos in processed) continue
        val mux = worldAccess.getComponent(pos, ExamplePlugin.mux2PartComponentType) ?: continue
        processed.add(pos)
        mux.pairedPos?.let { processed.add(it) }

        if (!mux.isComplete || mux.isDisconnected) {
            setVisualState(pos, worldAccess, queue, "default")
            mux.pairedPos?.let { setVisualState(it, worldAccess, queue, "default") }
            continue
        }

        val pairedPos = mux.pairedPos ?: continue

        // Determine which block is the selected input side (A=0 or B=1)
        // S is on one block's narrow face → that block is "closer to S" → that's A (index 0)
        var sFace = findSelectFace(pos, mux.pairFace, worldAccess)
        val sIsOnThisBlock = sFace != -1

        // Block closer to S = A (index 0), farther = B (index 1)
        val aBlockPos = if (sIsOnThisBlock) pos else pairedPos
        val bBlockPos = if (sIsOnThisBlock) pairedPos else pos

        // The selected block lights up
        val selectedBlockPos = if (mux.selectedInput == 0) aBlockPos else bBlockPos
        val nonSelectedBlockPos = if (mux.selectedInput == 0) bBlockPos else aBlockPos

        if (setVisualState(selectedBlockPos, worldAccess, queue, "On")) {
        }
        if (setVisualState(nonSelectedBlockPos, worldAccess, queue, "default")) {
        }
    }
}

// --- Main system ---

/**
 * Core ticking system that manages power network topology, evaluation, and visual updates.
 * 
 * # Architecture Overview
 * 
 * TopologySystem implements a 4-state logic network (ZERO, ONE, HIGH_Z, UNKNOWN_X) with:
 * - **Per-face network assignment**: Each block face can belong to a different network
 * - **Multi-driver resolution**: Multiple sources can drive the same net (tri-state logic)
 * - **Delta-cycle evaluation**: Networks stabilize through iterative evaluation
 * - **Relay-induced topology changes**: Enabled/disabled relays alter network connectivity
 * - **Conflict detection**: UNKNOWN_X state destroys blocks (magic smoke)
 * 
 * # Execution Flow (per tick)
 * 
 * ## Outer Loop: Topology Rounds (max 8)
 * Handles relay state changes that alter network connectivity.
 * 
 * ### Phase 1-2: Topology Rebuild
 * - Expand seed blocks to include neighbors + InputPort driver blocks
 * - Clear all networks touching seed blocks (Phase 1: clearNetsFromSeeds)
 * - Rebuild networks via flood fill (Phase 2: rebuildPowerTopology)
 * - Iteratively expand for adjacent InputPorts until stable
 * 
 * ### Phase 3-5: Network Evaluation
 * - Collect all dirty network IDs
 * - Initialize all nets to HIGH_Z
 * - **Inner Delta-Cycle Loop** (max 64 cycles):
 *   - Evaluate all PowerSource blocks (inverter logic)
 *   - Resolve network values (multi-driver 4-state)
 *   - Check if stable (no net values changed)
 *   - If unstable after 64 cycles → force all nets to UNKNOWN_X
 * 
 * ### Phase 6-7: Relay Control & Topology Check
 * - Evaluate relay control states (enabled/disabled based on InputPort probes)
 * - If any relay toggled → collect toggled positions and loop back for another topology round
 * - If stable or rounds exhausted → break outer loop
 * 
 * ## Post-Topology Processing
 * 
 * ### Phase 8: Destroy X Nets
 * - Find all networks with UNKNOWN_X value
 * - Destroy all blocks connected to those nets
 * - Queue DESTROYED events for next tick
 * 
 * ### Phase 9-10: Visual Updates
 * - Update VisualState for all block types (Lamp, Relay, PowerSource, InputPort, PowerWire)
 * - Mark positions dirty for VisualStateSystem to apply
 * 
 * # Key Limits
 * - MAX_DELTA_CYCLES = 64 (inner evaluation loop)
 * - MAX_TOPOLOGY_ROUNDS = 8 (outer relay toggle loop)
 */
class TopologySystem : TickingSystem<ChunkStore>() {
    companion object {
        const val MAX_DELTA_CYCLES = 64
        const val MAX_TOPOLOGY_ROUNDS = 8
    }

    override fun tick(dt: Float, systemIndex: Int, store: Store<ChunkStore>) {
        val changesQueue = store.getResource(ExamplePlugin.stateChangeQueueType)
        if (changesQueue.changes.isEmpty()) return

        // Phase 1: Drain seeds
        val seeds = mutableSetOf<Vector3i>()
        while (changesQueue.changes.isNotEmpty()) {
            val event = changesQueue.changes.removeFirst()
            seeds.add(event.pos)
        }

        val world = store.externalData.world
        val worldAccess: WorldAccess = HytaleWorldAccess(world)

        // Phase 2: Expand seeds by 1 hop, then include driver-side neighbors of InputPorts
        var topologySeeds = expandForInputPortDrivers(expandThroughConnected(seeds, worldAccess), worldAccess)

        // Accumulate all dirty blocks/nets across topology rounds for post-step processing
        val allDirtyBlocks = mutableSetOf<Vector3i>()
        val allDirtyNetIds = mutableSetOf<Int>()
        val alreadyToggledMuxes = mutableSetOf<Vector3i>() // Prevent same MUX oscillating across rounds
        var topologyStable = false

        // Outer topology loop: handles relay-induced topology changes
        for (round in 0 until MAX_TOPOLOGY_ROUNDS) {

            // Phase 3: Clear all nets touched by seed blocks
            var dirtyBlocks: Set<Vector3i> = clearNetsFromSeeds(topologySeeds, worldAccess, changesQueue)

            // Iteratively expand for InputPort drivers until no new blocks are found.
            // Each iteration may discover new blocks (via net membership) whose neighbors
            // contain InputPorts that feed relays/sources not yet in the dirty set.
            while (true) {
                val expanded = expandForAdjacentInputPorts(
                    expandForInputPortDrivers(dirtyBlocks, worldAccess), worldAccess
                )
                if (expanded.size <= dirtyBlocks.size) break
                val newPositions = expanded - dirtyBlocks
                val extraDirty = clearNetsFromSeeds(newPositions, worldAccess, changesQueue)
                dirtyBlocks = expanded + extraDirty
            }

            allDirtyBlocks.addAll(dirtyBlocks)

            // Reset all dirty relays to disabled before rebuilding topology (round 0 only).
            // This prevents stale relay state from merging nets and creating false feedback
            // (e.g., cathode power flowing back through a conducting relay to the InputPort).
            // Subsequent rounds use the relay state from evaluation, so only round 0 resets.
            if (round == 0) {
                for (pos in dirtyBlocks) {
                    val relay = worldAccess.getComponent(pos, ExamplePlugin.relayComponentType) ?: continue
                    relay.lastEnabled = relay.enabled
                    relay.enabled = false
                }
            }

            // Phase 4: Rebuild topology via flood fill
            rebuildPowerTopology(dirtyBlocks, worldAccess, changesQueue)

            // Phase 5: Collect dirty net IDs
            val dirtyNetIds = collectNetIds(dirtyBlocks, worldAccess)
            allDirtyNetIds.addAll(dirtyNetIds)

            // Phase 6: Initialize all dirty nets to HIGH_Z
            for (netId in dirtyNetIds) {
                changesQueue.powerNetValueCache[netId] = State4.HIGH_Z
            }

            // Phase 7: Delta-cycle evaluation loop
            var stable = false
            var cycle = 0
            val prevNetValues = mutableMapOf<Int, State4>()
            while (!stable && cycle < MAX_DELTA_CYCLES) {
                cycle++
                prevNetValues.clear()
                for (netId in dirtyNetIds) {
                    prevNetValues[netId] = changesQueue.powerNetValueCache[netId] ?: State4.HIGH_Z
                }
                evaluateSources(dirtyBlocks, worldAccess, changesQueue)
                resolveNets(dirtyNetIds, dirtyBlocks, worldAccess, changesQueue)
                stable = true
                for (netId in dirtyNetIds) {
                    if (changesQueue.powerNetValueCache[netId] != prevNetValues[netId]) {
                        stable = false
                        break
                    }
                }
            }

            // Phase 8: If not stable after MAX_DELTA_CYCLES, force all dirty nets to X
            if (!stable) {
                println("[TopologySystem] UNSTABLE after $MAX_DELTA_CYCLES cycles, forcing X")
                for (netId in dirtyNetIds) {
                    changesQueue.powerNetValueCache[netId] = State4.UNKNOWN_X
                }
            }

            // Phase 8.5: Evaluate relay and MUX controls
            // Evaluate MUX controls on every round, but skip MUXes that already toggled
            // in a previous round (prevents oscillation from re-probing cleared S nets).
            // New MUXes whose S input changed due to upstream MUX toggling will be evaluated.
            val anyRelayToggled = evaluateAllRelayControls(dirtyBlocks, worldAccess, changesQueue)
            val anyMuxToggled = evaluateAllMuxControls(dirtyBlocks, worldAccess, changesQueue, alreadyToggledMuxes)
            if (!anyRelayToggled && !anyMuxToggled) {
                topologyStable = true
                break
            }

            // Relay or MUX toggled — collect toggled positions as seeds for next round
            val toggledRelays = collectToggledRelayPositions(dirtyBlocks, worldAccess)
            val toggledMuxes = collectToggledMuxPositions(dirtyBlocks, worldAccess)
            alreadyToggledMuxes.addAll(toggledMuxes)
            val allToggled = toggledRelays.apply { addAll(toggledMuxes) }
            // Expand through MUX-adjacent InputPorts to include far-side blocks (power sources)
            val expandedToggled = expand(allToggled).toMutableSet()
            for (pos in expandedToggled.toSet()) {
                val ip = worldAccess.getComponent(pos, ExamplePlugin.inputPortComponentType) ?: continue
                // Add the block on the far side of the InputPort (opposite of driver side)
                val farFace = OPPOSITE_FACE[ip.driverSideFace]
                val (farPos, _) = neighborOfFace(pos, farFace)
                expandedToggled.add(farPos)
                // Also expand that block's neighbors
                for (f in 0..5) {
                    expandedToggled.add(Vector3i(farPos.x + FACE_DX[f], farPos.y + FACE_DY[f], farPos.z + FACE_DZ[f]))
                }
            }
            topologySeeds = expandForInputPortDrivers(expandedToggled, worldAccess)
        }

        // If topology loop exhausted, force all accumulated nets to X
        if (!topologyStable) {
            println("[TopologySystem] TOPOLOGY UNSTABLE after $MAX_TOPOLOGY_ROUNDS rounds, forcing X")
            for (netId in allDirtyNetIds) {
                changesQueue.powerNetValueCache[netId] = State4.UNKNOWN_X
            }
        }

        // Phase 9: Destroy blocks on X nets
        destroyXNets(allDirtyNetIds, world, changesQueue)

        // Phase 10: Update visual states for all block types
        updateLamps(allDirtyBlocks, worldAccess, changesQueue)
        updateRelayVisuals(allDirtyBlocks, worldAccess, changesQueue)
        updateDriverVisuals(allDirtyBlocks, worldAccess, changesQueue)
        updateLeverVisuals(allDirtyBlocks, worldAccess, changesQueue)
        updateInputPortVisuals(allDirtyBlocks, worldAccess, changesQueue)
        updateWireVisuals(allDirtyBlocks, worldAccess, changesQueue)
        updateMuxVisuals(allDirtyBlocks, worldAccess, changesQueue)

        if (allDirtyNetIds.isNotEmpty()) {
        }
    }
}
