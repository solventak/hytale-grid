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
import dev.hytalemodding.ExamplePlugin

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
 * ChunkStore RefSystem that handles block placement/addition events.
 * 
 * Triggers when:
 * - A block entity is added to the world (placement, chunk load, visual variant swap)
 * - The block has PowerConnectable or InputPort components
 * 
 * Responsibilities:
 * 1. **InputPort validation**: If block is an InputPort, scans neighbors for PowerSource/Relay
 *    - If no valid driver found → destroys the block
 *    - If driver found → configures inputPort.driverSideFace
 * 
 * 2. **Topology event queuing**: Adds a PLACED event to queue.changes
 *    - TopologySystem will process the event in its next tick
 * 
 * 3. **Wire shape marking**: If block is a PowerWire, marks itself + 6 neighbors dirty
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
        val hasPowerConnectable = cmdBuf.getComponent(ref, ExamplePlugin.powerConnectableComponentType) != null
        val inputPort = cmdBuf.getComponent(ref, ExamplePlugin.inputPortComponentType)
        if (!hasPowerConnectable && inputPort == null) return

        val pos = globalPosFromLocal(info, cmdBuf) ?: return

        // Skip if this is a wire shape swap (not a real placement)
        if (WireVisualUpdateTracker.isUpdating(pos)) return

        // If this block has an InputPort component, validate and configure its driverSideFace
        if (inputPort != null) {
            val world = store.externalData.world
            var foundDriverFace: Int? = null
            for (face in 0..5) {
                val (npos, _) = neighborOfFace(pos, face)
                val neighborSource = getComponentForGlobalXyz(world, npos, ExamplePlugin.powerSourceComponentType)
                val neighborRelay = getComponentForGlobalXyz(world, npos, ExamplePlugin.relayComponentType)
                if (neighborSource != null || neighborRelay != null) {
                    foundDriverFace = face
                    break
                }
            }
            if (foundDriverFace == null) {
                println("[PowerBlockAddedSystem] InputPort at $pos has no adjacent PowerSource or Relay, destroying")
                world.execute { world.setBlock(pos.x, pos.y, pos.z, "Empty") }
                return
            }
            inputPort.driverSideFace = foundDriverFace
            println("[PowerBlockAddedSystem] InputPort at $pos configured: driverSide=${FACE_NAMES[foundDriverFace]}")
        }

        val queue = store.getResource(ExamplePlugin.stateChangeQueueType)
        queue.changes.add(StateChangeEvent(pos, StateChangeKind.PLACED))
        println("[PowerBlockAddedSystem] Queued PLACED at (${pos.x}, ${pos.y}, ${pos.z}), queue size: ${queue.changes.size}")

        // If this block is a wire, mark it + neighbors for wire shape update
        val hasPowerWire = cmdBuf.getComponent(ref, ExamplePlugin.powerWireComponentType) != null
        if (hasPowerWire) {
            queue.wireDirtyPositions.add(pos)
            for (face in 0..5) {
                queue.wireDirtyPositions.add(
                    Vector3i(pos.x + FACE_DX[face], pos.y + FACE_DY[face], pos.z + FACE_DZ[face])
                )
            }
            println("[PowerBlockAddedSystem] Wire placed at $pos, marked ${queue.wireDirtyPositions.size} wire dirty positions")
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
        Query.and(BlockModule.BlockStateInfo.getComponentType(), ExamplePlugin.powerConnectableComponentType),
        Query.and(BlockModule.BlockStateInfo.getComponentType(), ExamplePlugin.inputPortComponentType)
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
        val pos = event.targetBlock

        // Skip if this is a wire shape swap (not a real break)
        if (WireVisualUpdateTracker.isUpdating(pos)) return

        val hasPowerConnectable = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerConnectableComponentType) != null
        val hasInputPort = getComponentForGlobalXyz(world, pos, ExamplePlugin.inputPortComponentType) != null
        if (!hasPowerConnectable && !hasInputPort) return

        val queue = world.chunkStore.store.getResource(ExamplePlugin.stateChangeQueueType)
        queue.changes.add(StateChangeEvent(pos, StateChangeKind.DESTROYED))
        println("[PowerBlockBreakEvent] Queued DESTROYED at (${pos.x}, ${pos.y}, ${pos.z}), queue size: ${queue.changes.size}")

        // If this block is a wire, mark neighbors for wire shape update
        val hasPowerWire = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerWireComponentType) != null
        if (hasPowerWire) {
            for (face in 0..5) {
                queue.wireDirtyPositions.add(
                    Vector3i(pos.x + FACE_DX[face], pos.y + FACE_DY[face], pos.z + FACE_DZ[face])
                )
            }
            println("[PowerBlockBreakEvent] Wire broken at $pos, marked neighbors for wire shape update")
        }
    }

    override fun getQuery(): Query<EntityStore> = Query.and(Player.getComponentType())
}
