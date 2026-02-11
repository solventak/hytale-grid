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
 * Tracks positions currently being visually updated by the wire shape system.
 * Prevents PowerBlockAddedSystem / PowerBlockBreakEvent from re-queuing events
 * when setBlock swaps a wire to a different variant (which destroys + recreates the entity).
 */
object WireVisualUpdateTracker {
    private val updatingPositions = java.util.concurrent.ConcurrentHashMap.newKeySet<Vector3i>()

    fun markUpdating(pos: Vector3i) { updatingPositions.add(pos) }
    fun clearUpdating(pos: Vector3i) { updatingPositions.remove(pos) }
    fun isUpdating(pos: Vector3i): Boolean = updatingPositions.contains(pos)
}

fun globalPosFromLocal(info: BlockModule.BlockStateInfo, cmdBuf: CommandBuffer<ChunkStore>): Vector3i? {
    val worldChunk = cmdBuf.getComponent(info.chunkRef, WorldChunk.getComponentType()) ?: return null
    val localX = ChunkUtil.xFromBlockInColumn(info.index)
    val localY = ChunkUtil.yFromBlockInColumn(info.index)
    val localZ = ChunkUtil.zFromBlockInColumn(info.index)
    val globalX = localX + (worldChunk.x * 32)
    val globalZ = localZ + (worldChunk.z * 32)
    return Vector3i(globalX, localY, globalZ)
}

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
