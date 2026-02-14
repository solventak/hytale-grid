package dev.hytalemodding.newnet

import com.hypixel.hytale.component.*
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import dev.hytalemodding.GridPlugin
import dev.hytalemodding.newnet.shared.State4

/**
 * Thread-safe tracker for wire blocks currently undergoing visual shape updates.
 * 
 * Problem:
 * - VisualStateSystem calls world.setBlock() to swap a wire to a different variant
 * - setBlock internally destroys the old block entity and creates a new one
 * - This triggers PowerBlockAddedSystem and PowerBlockBreakEvent
 * - Without tracking, these events would re-queue topology changes for visual-only swaps
 * 
 * Solution:
 * - Before calling setBlock, VisualStateSystem marks the position as "updating"
 * - Event systems check isUpdating() and skip re-queuing if true
 * - After setBlock completes, position is cleared
 * 
 * Thread safety:
 * - Uses ConcurrentHashMap.newKeySet() for lock-free concurrent access
 * - Multiple systems may check simultaneously during parallel execution
 */
object WireVisualUpdateTracker {
    private val updatingPositions = java.util.concurrent.ConcurrentHashMap.newKeySet<Vector3i>()

    /** Marks a position as currently undergoing a visual-only update */
    fun markUpdating(pos: Vector3i) { updatingPositions.add(pos) }
    
    /** Clears the updating flag for a position */
    fun clearUpdating(pos: Vector3i) { updatingPositions.remove(pos) }
    
    /** Checks if a position is currently undergoing a visual-only update */
    fun isUpdating(pos: Vector3i): Boolean = updatingPositions.contains(pos)
}

/**
 * Converts block-local coordinates (from BlockStateInfo) to global world coordinates.
 * 
 * BlockStateInfo stores a block's position relative to its chunk (0-31 for X/Z).
 * This function retrieves the chunk's world position and computes the absolute coords.
 *
 * @param info BlockStateInfo component from the block entity
 * @param cmdBuf Command buffer for accessing WorldChunk component
 * @return Global position (Vector3i), or null if WorldChunk unavailable
 */
fun globalPosFromLocal(info: BlockModule.BlockStateInfo, cmdBuf: CommandBuffer<ChunkStore>): Vector3i? {
    val worldChunk = cmdBuf.getComponent(info.chunkRef, WorldChunk.getComponentType()) ?: return null
    val localX = ChunkUtil.xFromBlockInColumn(info.index)
    val localY = ChunkUtil.yFromBlockInColumn(info.index)
    val localZ = ChunkUtil.zFromBlockInColumn(info.index)
    val globalX = localX + (worldChunk.x * 32)
    val globalZ = localZ + (worldChunk.z * 32)
    return Vector3i(globalX, localY, globalZ)
}

/**
 * Scans the power network starting from a given position to find all PowerSource blocks.
 * Uses BFS to traverse wire/relay/MUX connections.
 * 
 * @param startPos Starting block position
 * @param worldAccess World access for component queries
 * @return Set of positions containing PowerSource components on this network
 */
fun scanNetworkForPowerSources(startPos: Vector3i, worldAccess: WorldAccess): Set<Vector3i> {
    val powerSources = mutableSetOf<Vector3i>()
    val visited = mutableSetOf<Pair<Vector3i, Int>>()
    val bfsQueue = ArrayDeque<Pair<Vector3i, Int>>()
    
    val startConn = worldAccess.getComponent(startPos, GridPlugin.powerConnectableComponentType) ?: return powerSources
    for (face in 0..5) {
        if (startConn.facesMask and (1 shl face) != 0) {
            bfsQueue.add(Pair(startPos, face))
        }
    }
    
    while (bfsQueue.isNotEmpty()) {
        val (pos, face) = bfsQueue.removeFirst()
        if (!visited.add(Pair(pos, face))) continue
        
        val conn = worldAccess.getComponent(pos, GridPlugin.powerConnectableComponentType) ?: continue
        
        val powerSource = worldAccess.getComponent(pos, GridPlugin.powerSourceComponentType)
        if (powerSource != null) {
            powerSources.add(pos)
        }
        
        val muxCheck = worldAccess.getComponent(pos, GridPlugin.mux2PartComponentType)
        val effectiveMask = if (muxCheck != null && muxCheck.isComplete && !muxCheck.isDisconnected) {
            getMuxConductionMask(pos, muxCheck, worldAccess)
        } else {
            conn.facesMask
        }
        if (effectiveMask and (1 shl face) == 0) continue
        
        val wire = worldAccess.getComponent(pos, GridPlugin.powerWireComponentType)
        if (wire != null) {
            for (face2 in 0..5) {
                if (face2 != face && conn.facesMask and (1 shl face2) != 0) {
                    bfsQueue.add(Pair(pos, face2))
                }
            }
        }
        
        val relay = worldAccess.getComponent(pos, GridPlugin.relayComponentType)
        if (relay != null && relay.enabled) {
            val controlMask = getControlFaces(pos, worldAccess)
            val faceIsControl = controlMask and (1 shl face) != 0
            if (!faceIsControl) {
                for (face2 in 0..5) {
                    if (face2 != face) {
                        val face2IsControl = controlMask and (1 shl face2) != 0
                        if (!face2IsControl && conn.facesMask and (1 shl face2) != 0) {
                            bfsQueue.add(Pair(pos, face2))
                        }
                    }
                }
            }
        }
        
        val mux = worldAccess.getComponent(pos, GridPlugin.mux2PartComponentType)
        if (mux != null && mux.isComplete && !mux.isDisconnected) {
            val conductionMask = getMuxConductionMask(pos, mux, worldAccess)
            if (conductionMask and (1 shl face) != 0) {
                for (face2 in 0..5) {
                    if (face2 != face && conductionMask and (1 shl face2) != 0) {
                        bfsQueue.add(Pair(pos, face2))
                    }
                }
            }
        }
        
        val (neighborPos, oppositeFace) = neighborOfFace(pos, face)
        val neighborConn = worldAccess.getComponent(neighborPos, GridPlugin.powerConnectableComponentType)
        if (neighborConn != null) {
            val neighborMux = worldAccess.getComponent(neighborPos, GridPlugin.mux2PartComponentType)
            val neighborEffectiveMask = if (neighborMux != null && neighborMux.isComplete && !neighborMux.isDisconnected) {
                getMuxConductionMask(neighborPos, neighborMux, worldAccess)
            } else {
                neighborConn.facesMask
            }
            if (neighborEffectiveMask and (1 shl oppositeFace) != 0) {
                // Wire channel isolation: wires only connect to same-channel wires
                val neighborWire = worldAccess.getComponent(neighborPos, GridPlugin.powerWireComponentType)
                val bothWires = wire != null && neighborWire != null
                if (bothWires && wire.channel != neighborWire.channel) {
                    // Different wire channels don't connect
                    continue
                }
                bfsQueue.add(Pair(neighborPos, oppositeFace))
            }
        }
    }
    
    return powerSources
}

/**
 * ChunkStore RefSystem that handles block placement/addition events.
 * 
 * Triggers when:
 * - A block entity is added to the world (placement, chunk load, visual variant swap)
 * - The block has PowerConnectable or InputPort components
 * 
 * Responsibilities:
 * 1. **Short circuit prevention**: Validates PowerSource and connectable block placement
 *    - Rejects placement if it would create a short circuit (ONE + ZERO on same net)
 * 
 * 2. **InputPort validation**: If block is an InputPort, scans neighbors for PowerSource/Relay
 *    - If no valid driver found → destroys the block
 *    - If driver found → configures inputPort.driverSideFace
 * 
 * 3. **Topology event queuing**: Adds a PLACED event to queue.changes
 *    - TopologySystem will process the event in its next tick
 * 
 * 4. **Wire shape marking**: If block is a PowerWire, marks itself + 6 neighbors dirty
 *    - VisualStateSystem will update wire shapes for all marked positions
 * 
 * Skips processing if:
 * - Position is tracked by WireVisualUpdateTracker (visual-only swap in progress)
 */
class PowerBlockAddedSystem : RefSystem<ChunkStore>() {
    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        cmdBuf: CommandBuffer<ChunkStore>
    ) {
        val info = cmdBuf.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val hasPowerConnectable = cmdBuf.getComponent(ref, GridPlugin.powerConnectableComponentType) != null
        val inputPort = cmdBuf.getComponent(ref, GridPlugin.inputPortComponentType)
        if (!hasPowerConnectable && inputPort == null) return

        val pos = globalPosFromLocal(info, cmdBuf) ?: return

        // Skip if this is a wire shape swap (not a real placement)
        if (WireVisualUpdateTracker.isUpdating(pos)) return

        val world = store.externalData.world
        val worldAccess: WorldAccess = HytaleWorldAccess(world)

        // Initialize Lever driveState before validation (if this is a Lever)
        val lever = cmdBuf.getComponent(ref, GridPlugin.leverComponentType)
        val powerSource = cmdBuf.getComponent(ref, GridPlugin.powerSourceComponentType)
        if (lever != null && powerSource != null) {
            powerSource.driveState = if (lever.isOn) State4.ZERO else State4.ONE
            powerSource.lastDriveState = powerSource.driveState
        }

        // Short circuit prevention: Validate PowerSource placement
        if (powerSource != null) {
            val existingSources = scanNetworkForPowerSources(pos, worldAccess)
            for (existingPos in existingSources) {
                if (existingPos == pos) continue
                val existingSource = worldAccess.getComponent(existingPos, GridPlugin.powerSourceComponentType)
                if (existingSource != null && existingSource.driveState != powerSource.driveState) {
                    world.execute { world.setBlock(pos.x, pos.y, pos.z, "Empty") }
                    return
                }
            }
        }

        // Short circuit prevention: Validate connectable block (wire/relay/MUX) placement
        if (hasPowerConnectable && powerSource == null) {
            val conn = cmdBuf.getComponent(ref, GridPlugin.powerConnectableComponentType)
            if (conn != null) {
                val allSourceStates = mutableSetOf<State4>()
                
                for (face in 0..5) {
                    if (conn.facesMask and (1 shl face) == 0) continue
                    
                    val (neighborPos, oppositeFace) = neighborOfFace(pos, face)
                    val neighborConn = worldAccess.getComponent(neighborPos, GridPlugin.powerConnectableComponentType)
                    if (neighborConn == null) continue
                    
                    val neighborMux = worldAccess.getComponent(neighborPos, GridPlugin.mux2PartComponentType)
                    val neighborEffectiveMask = if (neighborMux != null && neighborMux.isComplete && !neighborMux.isDisconnected) {
                        getMuxConductionMask(neighborPos, neighborMux, worldAccess)
                    } else {
                        neighborConn.facesMask
                    }
                    if (neighborEffectiveMask and (1 shl oppositeFace) == 0) continue
                    
                    val sourcesInNeighborNetwork = scanNetworkForPowerSources(neighborPos, worldAccess)
                    for (sourcePos in sourcesInNeighborNetwork) {
                        val source = worldAccess.getComponent(sourcePos, GridPlugin.powerSourceComponentType)
                        if (source != null) {
                            allSourceStates.add(source.driveState)
                        }
                    }
                }
                
                if (State4.ONE in allSourceStates && State4.ZERO in allSourceStates) {
                    world.execute { world.setBlock(pos.x, pos.y, pos.z, "Empty") }
                    return
                }
            }
        }

        // If this block has an InputPort component, validate and configure its driverSideFace
        if (inputPort != null) {
            var foundDriverFace: Int? = null
            for (face in 0..5) {
                val (npos, _) = neighborOfFace(pos, face)
                val neighborSource = worldAccess.getComponent(npos, GridPlugin.powerSourceComponentType)
                val neighborRelay = worldAccess.getComponent(npos, GridPlugin.relayComponentType)
                val neighborMux = worldAccess.getComponent(npos, GridPlugin.mux2PartComponentType)
                if (neighborSource != null || neighborRelay != null || (neighborMux != null && neighborMux.isComplete)) {
                    foundDriverFace = face
                    break
                }
            }
            if (foundDriverFace == null) {
                world.execute { world.setBlock(pos.x, pos.y, pos.z, "Empty") }
                return
            }
            inputPort.driverSideFace = foundDriverFace
        }

        // If this block is a Mux2Part, handle pairing logic
        val mux = cmdBuf.getComponent(ref, GridPlugin.mux2PartComponentType)
        if (mux != null) {
            tryPairMux(pos, mux, worldAccess)
            
            // Note: We've removed the immediate validation/destruction logic that was here.
            // During world load, blocks may load in any order, causing false positives where
            // a MUX appears incomplete simply because its paired block hasn't loaded yet.
            // 
            // The updated tryPairMux now handles bidirectional syncing, so if block A loads
            // before block B, then block B loads and finds A already paired to it, they sync.
            // 
            // Incomplete MUXes (truly unpaired) are inert and won't cause issues - they just
            // won't function. Players can break and re-place them to pair properly.
        }

        val queue = store.getResource(GridPlugin.stateChangeQueueType)
        queue.changes.add(StateChangeEvent(pos, StateChangeKind.PLACED))

        // If this block is a wire, mark it + neighbors for wire shape update
        val hasPowerWire = cmdBuf.getComponent(ref, GridPlugin.powerWireComponentType) != null
        if (hasPowerWire) {
            queue.wireDirtyPositions.add(pos)
            for (face in 0..5) {
                queue.wireDirtyPositions.add(
                    Vector3i(pos.x + FACE_DX[face], pos.y + FACE_DY[face], pos.z + FACE_DZ[face])
                )
            }
        }
    }

    override fun onEntityRemove(
        ref: Ref<ChunkStore>,
        reason: RemoveReason,
        store: Store<ChunkStore>,
        cmdBuf: CommandBuffer<ChunkStore>
    ) {
        // Handled via BreakBlockEvent on EntityStore
    }


    override fun getQuery(): Query<ChunkStore> = Query.or(
        Query.and(BlockModule.BlockStateInfo.getComponentType(), GridPlugin.powerConnectableComponentType),
        Query.and(BlockModule.BlockStateInfo.getComponentType(), GridPlugin.inputPortComponentType),
        Query.and(BlockModule.BlockStateInfo.getComponentType(), GridPlugin.mux2PartComponentType)
    )
}

/**
 * EntityStore EventSystem that handles block break events.
 * 
 * Triggers when:
 * - A player breaks a block (via BreakBlockEvent on EntityStore)
 * - The block has PowerConnectable or InputPort components
 * 
 * Note: This runs on EntityStore (player entity system), not ChunkStore.
 * Cross-store access is used to queue events on the ChunkStore resource.
 * 
 * Responsibilities:
 * 1. **Topology event queuing**: Adds a DESTROYED event to queue.changes
 *    - TopologySystem will process the event in its next tick
 *    - Networks touching the destroyed block will be invalidated and rebuilt
 * 
 * 2. **Wire shape marking**: If block is a PowerWire, marks 6 neighbors dirty
 *    - VisualStateSystem will update neighbor wire shapes to remove connections
 * 
 * Skips processing if:
 * - Position is tracked by WireVisualUpdateTracker (visual-only swap in progress)
 * 
 * Important:
 * - BreakBlockEvent fires BEFORE the block is actually removed
 * - Components can still be read during this handler
 * - By the time TopologySystem processes DESTROYED events, block is gone
 */
class PowerBlockBreakEvent : EntityEventSystem<EntityStore, BreakBlockEvent>(BreakBlockEvent::class.java) {
    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        cmdBuf: CommandBuffer<EntityStore>,
        event: BreakBlockEvent
    ) {
        val world = cmdBuf.externalData.world
        val worldAccess: WorldAccess = HytaleWorldAccess(world)
        val pos = event.targetBlock

        // Skip if this is a wire shape swap (not a real break)
        if (WireVisualUpdateTracker.isUpdating(pos)) return

        val hasPowerConnectable = worldAccess.getComponent(pos, GridPlugin.powerConnectableComponentType) != null
        val hasInputPort = worldAccess.getComponent(pos, GridPlugin.inputPortComponentType) != null
        val hasMux = worldAccess.getComponent(pos, GridPlugin.mux2PartComponentType) != null
        if (!hasPowerConnectable && !hasInputPort && !hasMux) return

        // If this is a MUX block, handle pair destruction
        if (hasMux) {
            handleMuxDestroyed(pos, worldAccess)
        }

        val queue = world.chunkStore.store.getResource(GridPlugin.stateChangeQueueType)
        queue.changes.add(StateChangeEvent(pos, StateChangeKind.DESTROYED))

        // If this block is a wire, mark neighbors for wire shape update
        val hasPowerWire = worldAccess.getComponent(pos, GridPlugin.powerWireComponentType) != null
        if (hasPowerWire) {
            for (face in 0..5) {
                queue.wireDirtyPositions.add(
                    Vector3i(pos.x + FACE_DX[face], pos.y + FACE_DY[face], pos.z + FACE_DZ[face])
                )
            }
        }
    }

    override fun getQuery(): Query<EntityStore> = Query.and(Player.getComponentType())
}
