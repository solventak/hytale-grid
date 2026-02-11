package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin
import dev.hytalemodding.newnet.shared.State4

enum class StateChangeKind { PLACED, DESTROYED }

data class StateChangeEvent(
    val pos: Vector3i,
    val kind: StateChangeKind,
)

data class NetEntry(val pos: Vector3i, val face: Int)

class StateChangeEventQueue : Resource<ChunkStore> {
    val changes: MutableList<StateChangeEvent> = mutableListOf()
    var nextNetId: Int = 0
    val powerNetValueCache: MutableMap<Int, State4> = mutableMapOf()
    /** Reverse index: net ID -> all (pos, face) entries on that net */
    val netMembers: MutableMap<Int, MutableSet<NetEntry>> = mutableMapOf()
    /** Positions whose visual state needs updating (populated by logic, consumed by VisualStateSystem) */
    val visualDirtyPositions: MutableSet<Vector3i> = mutableSetOf()
    /** Positions whose wire shape needs updating (populated by event systems, consumed by VisualStateSystem) */
    val wireDirtyPositions: MutableSet<Vector3i> = mutableSetOf()

    companion object {
        @JvmField
        val CODEC: BuilderCodec<StateChangeEventQueue> =
            BuilderCodec.builder(StateChangeEventQueue::class.java) { StateChangeEventQueue() }.build()
    }

    fun allocateNetId(): Int = nextNetId++

    fun addNetMember(netId: Int, pos: Vector3i, face: Int) {
        netMembers.getOrPut(netId) { mutableSetOf() }.add(NetEntry(pos, face))
    }

    fun removeNet(netId: Int) {
        netMembers.remove(netId)
    }

    override fun clone(): Resource<ChunkStore> = StateChangeEventQueue()
}

const val UNASSIGNED = -1

val FACE_NAMES = arrayOf("DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST")

// Offsets indexed by face: DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5
val FACE_DX = intArrayOf(0, 0, 0, 0, -1, 1)
val FACE_DY = intArrayOf(-1, 1, 0, 0, 0, 0)
val FACE_DZ = intArrayOf(0, 0, -1, 1, 0, 0)
val OPPOSITE_FACE = intArrayOf(1, 0, 3, 2, 5, 4)

fun neighborOfFace(pos: Vector3i, face: Int): Pair<Vector3i, Int> {
    return Pair(
        Vector3i(pos.x + FACE_DX[face], pos.y + FACE_DY[face], pos.z + FACE_DZ[face]),
        OPPOSITE_FACE[face]
    )
}

fun expand(seeds: Set<Vector3i>): Set<Vector3i> {
    val expanded = mutableSetOf<Vector3i>()
    for (seed in seeds) {
        expanded.add(seed)
        for (face in 0..5) {
            expanded.add(Vector3i(seed.x + FACE_DX[face], seed.y + FACE_DY[face], seed.z + FACE_DZ[face]))
        }
    }
    return expanded
}

/**
 * Extends seed blocks to include blocks fed by InputPorts in the set.
 * When an InputPort's net changes, the relay/source on its driver side must be re-evaluated.
 * Without this, a relay 2 hops from a changed wire (wire → InputPort → relay) would be missed.
 */
fun expandForInputPortDrivers(seedBlocks: Set<Vector3i>, world: World): Set<Vector3i> {
    val expanded = seedBlocks.toMutableSet()
    for (pos in seedBlocks) {
        val inputPort = getComponentForGlobalXyz(world, pos, ExamplePlugin.inputPortComponentType) ?: continue
        val (driverPos, _) = neighborOfFace(pos, inputPort.driverSideFace)
        expanded.add(driverPos)
    }
    return expanded
}

/**
 * Scans neighbors of dirty blocks for InputPorts and includes the InputPort + its
 * driver-side block (relay/source). This ensures relays are re-evaluated when a
 * wire adjacent to their InputPort changes, even if the relay itself isn't on an
 * affected net.
 */
fun expandForAdjacentInputPorts(dirtyBlocks: Set<Vector3i>, world: World): Set<Vector3i> {
    val expanded = dirtyBlocks.toMutableSet()
    for (pos in dirtyBlocks) {
        for (face in 0..5) {
            val (npos, _) = neighborOfFace(pos, face)
            if (npos in expanded) continue
            val inputPort = getComponentForGlobalXyz(world, npos, ExamplePlugin.inputPortComponentType) ?: continue
            expanded.add(npos)
            val (driverPos, _) = neighborOfFace(npos, inputPort.driverSideFace)
            expanded.add(driverPos)
        }
    }
    return expanded
}

fun <T : Component<ChunkStore>> getComponentForGlobalXyz(world: World, pos: Vector3i, type: ComponentType<ChunkStore, T>): T? {
    val chunkStore = world.chunkStore
    val chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z)
    val chunkRef = chunkStore.getChunkReference(chunkIndex) ?: return null

    val blockComponentChunk = chunkStore.store.getComponent(
        chunkRef,
        BlockComponentChunk.getComponentType()
    ) ?: return null

    val localX = pos.x and 31
    val localZ = pos.z and 31
    val blockIndex = ChunkUtil.indexBlockInColumn(localX, pos.y, localZ)

    val blockRef = blockComponentChunk.getEntityReference(blockIndex) ?: return null
    if (!blockRef.isValid) return null
    return chunkStore.store.getComponent(blockRef, type)
}

// --- Phase 1: Clear nets from seeds ---

fun clearNetsFromSeeds(seedBlocks: Set<Vector3i>, world: World, queue: StateChangeEventQueue): Set<Vector3i> {
    val netsToInvalidate = mutableSetOf<Int>()
    for (pos in seedBlocks) {
        val ids = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerNetIdsComponentType) ?: continue
        for (face in 0..5) {
            val netId = ids.get(face)
            if (netId != UNASSIGNED) {
                netsToInvalidate.add(netId)
            }
        }
    }

    val allDirtyPositions = mutableSetOf<Vector3i>()
    allDirtyPositions.addAll(seedBlocks)

    for (netId in netsToInvalidate) {
        val members = queue.netMembers[netId] ?: continue
        val clearedFaces = mutableListOf<String>()
        for (entry in members) {
            allDirtyPositions.add(entry.pos)
            val ids = getComponentForGlobalXyz(world, entry.pos, ExamplePlugin.powerNetIdsComponentType) ?: continue
            val oldId = ids.get(entry.face)
            ids.set(entry.face, UNASSIGNED)
            if (oldId != UNASSIGNED) {
                clearedFaces.add("${entry.pos} ${FACE_NAMES[entry.face]}(was=$oldId)")
            }
        }
        if (clearedFaces.isNotEmpty()) {
            println("[clearNets] Net $netId cleared: $clearedFaces")
        }
        queue.removeNet(netId)
    }

    return allDirtyPositions
}

// --- Phase 2: Rebuild topology via flood fill ---

fun rebuildPowerTopology(dirtyBlocks: Set<Vector3i>, world: World, queue: StateChangeEventQueue) {
    for (pos in dirtyBlocks) {
        val conn = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerConnectableComponentType) ?: continue
        val ids = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerNetIdsComponentType) ?: continue

        for (face in 0..5) {
            if (conn.facesMask and (1 shl face) == 0) continue
            if (ids.get(face) != UNASSIGNED) continue

            val netId = queue.allocateNetId()
            println("[rebuildTopology] New net $netId starting at $pos face=${FACE_NAMES[face]}")
            floodFillPower(pos, face, netId, world, queue)
        }
    }
}

fun floodFillPower(startPos: Vector3i, startFace: Int, netId: Int, world: World, queue: StateChangeEventQueue) {
    val bfsQueue = ArrayDeque<Pair<Vector3i, Int>>()
    bfsQueue.add(Pair(startPos, startFace))

    while (bfsQueue.isNotEmpty()) {
        val (pos, face) = bfsQueue.removeFirst()

        val conn = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerConnectableComponentType) ?: continue
        val ids = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerNetIdsComponentType) ?: continue

        if (conn.facesMask and (1 shl face) == 0) continue
        if (ids.get(face) != UNASSIGNED) continue

        ids.set(face, netId)
        queue.addNetMember(netId, pos, face)
        println("[floodFill] Assign net $netId to $pos face=${FACE_NAMES[face]}")

        // Internal connectivity: PowerWire bridges all faces, Relay bridges conduction faces only
        val isWire = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerWireComponentType) != null
        if (isWire) {
            for (face2 in 0..5) {
                if (face2 == face) continue
                if (conn.facesMask and (1 shl face2) != 0 && ids.get(face2) == UNASSIGNED) {
                    println("[floodFill]   Wire internal spread $pos ${FACE_NAMES[face]} -> ${FACE_NAMES[face2]}")
                    bfsQueue.add(Pair(pos, face2))
                }
            }
        }

        // Relay internal connectivity: enabled relay star-connects conduction (non-control) faces
        val relay = getComponentForGlobalXyz(world, pos, ExamplePlugin.relayComponentType)
        if (relay != null && relay.enabled) {
            val controlMask = getControlFaces(pos, world)
            val faceIsControl = controlMask and (1 shl face) != 0
            if (!faceIsControl) {
                for (face2 in 0..5) {
                    if (face2 == face) continue
                    val face2IsControl = controlMask and (1 shl face2) != 0
                    if (!face2IsControl && conn.facesMask and (1 shl face2) != 0 && ids.get(face2) == UNASSIGNED) {
                        println("[floodFill]   Relay internal spread $pos ${FACE_NAMES[face]} -> ${FACE_NAMES[face2]}")
                        bfsQueue.add(Pair(pos, face2))
                    }
                }
            }
        }

        // External adjacency: check neighbor block across this face
        val (npos, nface) = neighborOfFace(pos, face)
        val nconn = getComponentForGlobalXyz(world, npos, ExamplePlugin.powerConnectableComponentType) ?: continue
        val nids = getComponentForGlobalXyz(world, npos, ExamplePlugin.powerNetIdsComponentType) ?: continue

        if (nconn.facesMask and (1 shl nface) != 0 && nids.get(nface) == UNASSIGNED) {
            println("[floodFill]   External spread $pos ${FACE_NAMES[face]} -> neighbor $npos ${FACE_NAMES[nface]}")
            bfsQueue.add(Pair(npos, nface))
        }
    }
}

// --- Phase 3: Collect dirty net IDs ---

fun collectNetIds(dirtyBlocks: Set<Vector3i>, world: World): Set<Int> {
    val netIds = mutableSetOf<Int>()
    for (pos in dirtyBlocks) {
        val ids = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerNetIdsComponentType) ?: continue
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

fun computeInverterDrive(sourcePos: Vector3i, world: World, queue: StateChangeEventQueue): State4 {
    val conn = getComponentForGlobalXyz(world, sourcePos, ExamplePlugin.powerConnectableComponentType)
        ?: return State4.ONE

    val inputValues = mutableListOf<State4>()

    for (face in 0..5) {
        if (conn.facesMask and (1 shl face) == 0) continue
        val (npos, nface) = neighborOfFace(sourcePos, face)
        val inputPort = getComponentForGlobalXyz(world, npos, ExamplePlugin.inputPortComponentType)
            ?: continue
        // Check that the InputPort's driverSideFace points back at this source
        if (inputPort.driverSideFace != nface) continue

        // Probe the block adjacent to the InputPort's output face (not the InputPort itself).
        // InputPort has no PowerNetIds — it's a pure probe that reads the neighboring block's net.
        val outputFace = OPPOSITE_FACE[inputPort.driverSideFace]
        val (probePos, probeFace) = neighborOfFace(npos, outputFace)
        val probeIds = getComponentForGlobalXyz(world, probePos, ExamplePlugin.powerNetIdsComponentType)
        val inputNetId = probeIds?.get(probeFace) ?: UNASSIGNED
        val inputNetValue = if (inputNetId != UNASSIGNED) {
            queue.powerNetValueCache[inputNetId] ?: State4.HIGH_Z
        } else {
            State4.HIGH_Z
        }
        inputValues.add(inputNetValue)
    }

    // Inversion rules
    if (inputValues.isEmpty()) return State4.ONE
    if (inputValues.any { it == State4.UNKNOWN_X }) return State4.UNKNOWN_X
    val hasOne = inputValues.any { it == State4.ONE }
    val hasZero = inputValues.any { it == State4.ZERO }
    if (hasOne && hasZero) return State4.UNKNOWN_X
    if (hasOne) return State4.ZERO  // invert
    return State4.ONE               // all ZERO or HIGH_Z -> drive 1
}

fun evaluateSources(dirtyBlocks: Set<Vector3i>, world: World, queue: StateChangeEventQueue) {
    for (pos in dirtyBlocks) {
        val source = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerSourceComponentType) ?: continue
        source.lastDriveState = source.driveState
        source.driveState = computeInverterDrive(pos, world, queue)
        println("[evaluateSources] Source at $pos: drive=${source.driveState} (was ${source.lastDriveState})")
    }
}

// --- Phase 5: Resolve nets (multi-driver 4-state) ---

fun resolveNets(dirtyNetIds: Set<Int>, dirtyBlocks: Set<Vector3i>, world: World, queue: StateChangeEventQueue) {
    val netDrivers = mutableMapOf<Int, MutableList<State4>>()
    for (netId in dirtyNetIds) {
        netDrivers[netId] = mutableListOf()
    }

    for (pos in dirtyBlocks) {
        val source = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerSourceComponentType) ?: continue
        val ids = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerNetIdsComponentType) ?: continue
        val conn = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerConnectableComponentType) ?: continue

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

    println("[destroyXNets] Destroying ${positionsToDestroy.size} blocks on X nets")
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

/** Mark a block's VisualState dirty if it changed. Returns true if changed. */
fun setVisualState(pos: Vector3i, world: World, queue: StateChangeEventQueue, newState: String): Boolean {
    val vs = getComponentForGlobalXyz(world, pos, ExamplePlugin.visualStateComponentType) ?: return false
    if (vs.state != newState) {
        vs.state = newState
        queue.visualDirtyPositions.add(pos)
        return true
    }
    return false
}

fun updateLamps(dirtyBlocks: Set<Vector3i>, world: World, queue: StateChangeEventQueue) {
    for (pos in dirtyBlocks) {
        val lamp = getComponentForGlobalXyz(world, pos, ExamplePlugin.lampComponentType) ?: continue
        val ids = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerNetIdsComponentType) ?: continue
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
        setVisualState(pos, world, queue, if (lit) "On" else "default")
        if (faceNets.isNotEmpty()) {
            println("[updateLamps] Lamp at $pos: nets=$faceNets, lit=$lit (was=$oldLit)" +
                if (matchedFace != null) " powered via ${FACE_NAMES[matchedFace]}" else " no powered net")
        }
    }
}

fun updateRelayVisuals(dirtyBlocks: Set<Vector3i>, world: World, queue: StateChangeEventQueue) {
    for (pos in dirtyBlocks) {
        val relay = getComponentForGlobalXyz(world, pos, ExamplePlugin.relayComponentType) ?: continue
        val newState = if (relay.enabled) "On" else "default"
        if (setVisualState(pos, world, queue, newState)) {
            println("[updateRelayVisuals] Relay at $pos: enabled=${relay.enabled}, state=$newState")
        }
    }
}

fun updateDriverVisuals(dirtyBlocks: Set<Vector3i>, world: World, queue: StateChangeEventQueue) {
    for (pos in dirtyBlocks) {
        val source = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerSourceComponentType) ?: continue
        val newState = if (source.driveState == State4.ONE) "On" else "default"
        if (setVisualState(pos, world, queue, newState)) {
            println("[updateDriverVisuals] PowerSource at $pos: driveState=${source.driveState}, state=$newState")
        }
    }
}

fun updateWireVisuals(dirtyBlocks: Set<Vector3i>, world: World, queue: StateChangeEventQueue) {
    for (pos in dirtyBlocks) {
        val wire = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerWireComponentType) ?: continue
        val ids = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerNetIdsComponentType) ?: continue
        var powered = false
        for (face in 0..5) {
            val netId = ids.get(face)
            if (netId != UNASSIGNED) {
                val netValue = queue.powerNetValueCache[netId] ?: State4.HIGH_Z
                if (netValue == State4.ONE) { powered = true; break }
            }
        }
        if (setVisualState(pos, world, queue, if (powered) "On" else "default")) {
            println("[updateWireVisuals] Wire at $pos: powered=$powered")
        }
    }
}

fun updateInputPortVisuals(dirtyBlocks: Set<Vector3i>, world: World, queue: StateChangeEventQueue) {
    for (pos in dirtyBlocks) {
        val inputPort = getComponentForGlobalXyz(world, pos, ExamplePlugin.inputPortComponentType) ?: continue
        // Probe the net on the InputPort's output face (opposite of driver side)
        val outputFace = OPPOSITE_FACE[inputPort.driverSideFace]
        val (probePos, probeFace) = neighborOfFace(pos, outputFace)
        val probeIds = getComponentForGlobalXyz(world, probePos, ExamplePlugin.powerNetIdsComponentType)
        val netId = probeIds?.get(probeFace) ?: UNASSIGNED
        val netValue = if (netId != UNASSIGNED) {
            queue.powerNetValueCache[netId] ?: State4.HIGH_Z
        } else {
            State4.HIGH_Z
        }
        val newState = if (netValue == State4.ONE) "On" else "default"
        if (setVisualState(pos, world, queue, newState)) {
            println("[updateInputPortVisuals] InputPort at $pos: probeNet=$netId, value=$netValue, state=$newState")
        }
    }
}

// --- Main system ---

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
            println("[TopologySystem] Seed: ${event.pos} (${event.kind})")
        }

        val world = store.externalData.world

        // Phase 2: Expand seeds by 1 hop, then include driver-side neighbors of InputPorts
        var topologySeeds = expandForInputPortDrivers(expand(seeds), world)

        // Accumulate all dirty blocks/nets across topology rounds for post-step processing
        val allDirtyBlocks = mutableSetOf<Vector3i>()
        val allDirtyNetIds = mutableSetOf<Int>()
        var topologyStable = false

        // Outer topology loop: handles relay-induced topology changes
        for (round in 0 until MAX_TOPOLOGY_ROUNDS) {
            println("[TopologySystem] Topology round $round, seeds=${topologySeeds.size}")

            // Phase 3: Clear all nets touched by seed blocks
            var dirtyBlocks: Set<Vector3i> = clearNetsFromSeeds(topologySeeds, world, changesQueue)

            // Iteratively expand for InputPort drivers until no new blocks are found.
            // Each iteration may discover new blocks (via net membership) whose neighbors
            // contain InputPorts that feed relays/sources not yet in the dirty set.
            while (true) {
                val expanded = expandForAdjacentInputPorts(
                    expandForInputPortDrivers(dirtyBlocks, world), world
                )
                if (expanded.size <= dirtyBlocks.size) break
                val newPositions = expanded - dirtyBlocks
                val extraDirty = clearNetsFromSeeds(newPositions, world, changesQueue)
                dirtyBlocks = expanded + extraDirty
            }

            allDirtyBlocks.addAll(dirtyBlocks)
            println("[TopologySystem] Round $round: ${topologySeeds.size} seed blocks -> ${dirtyBlocks.size} dirty blocks")

            // Reset all dirty relays to disabled before rebuilding topology (round 0 only).
            // This prevents stale relay state from merging nets and creating false feedback
            // (e.g., cathode power flowing back through a conducting relay to the InputPort).
            // Subsequent rounds use the relay state from evaluation, so only round 0 resets.
            if (round == 0) {
                for (pos in dirtyBlocks) {
                    val relay = getComponentForGlobalXyz(world, pos, ExamplePlugin.relayComponentType) ?: continue
                    relay.lastEnabled = relay.enabled
                    relay.enabled = false
                }
            }

            // Phase 4: Rebuild topology via flood fill
            rebuildPowerTopology(dirtyBlocks, world, changesQueue)

            // Phase 5: Collect dirty net IDs
            val dirtyNetIds = collectNetIds(dirtyBlocks, world)
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
                evaluateSources(dirtyBlocks, world, changesQueue)
                resolveNets(dirtyNetIds, dirtyBlocks, world, changesQueue)
                stable = true
                for (netId in dirtyNetIds) {
                    if (changesQueue.powerNetValueCache[netId] != prevNetValues[netId]) {
                        stable = false
                        break
                    }
                }
                println("[TopologySystem] Delta cycle $cycle: stable=$stable")
            }

            // Phase 8: If not stable after MAX_DELTA_CYCLES, force all dirty nets to X
            if (!stable) {
                println("[TopologySystem] UNSTABLE after $MAX_DELTA_CYCLES cycles, forcing X")
                for (netId in dirtyNetIds) {
                    changesQueue.powerNetValueCache[netId] = State4.UNKNOWN_X
                }
            }

            // Phase 8.5: Evaluate relay controls
            val anyRelayToggled = evaluateAllRelayControls(dirtyBlocks, world, changesQueue)
            if (!anyRelayToggled) {
                topologyStable = true
                break
            }

            // Relay toggled — collect toggled positions as seeds for next round
            topologySeeds = expandForInputPortDrivers(expand(collectToggledRelayPositions(dirtyBlocks, world)), world)
            println("[TopologySystem] Relay toggled, re-seeding with ${topologySeeds.size} blocks for round ${round + 1}")
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
        updateLamps(allDirtyBlocks, world, changesQueue)
        updateRelayVisuals(allDirtyBlocks, world, changesQueue)
        updateDriverVisuals(allDirtyBlocks, world, changesQueue)
        updateInputPortVisuals(allDirtyBlocks, world, changesQueue)
        updateWireVisuals(allDirtyBlocks, world, changesQueue)

        if (allDirtyNetIds.isNotEmpty()) {
            println("[TopologySystem] Rebuilt: ${allDirtyBlocks.size} dirty blocks, ${allDirtyNetIds.size} nets, values: ${changesQueue.powerNetValueCache}")
        }
    }
}
