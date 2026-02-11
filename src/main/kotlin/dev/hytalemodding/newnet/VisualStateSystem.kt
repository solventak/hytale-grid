package dev.hytalemodding.newnet

import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin
import dev.hytalemodding.wire.WireLookupTable
import dev.hytalemodding.wire.getConnections
import dev.hytalemodding.wire.isWireBlock

/**
 * Processes wire shape updates for all dirty wire positions.
 * 
 * For each wire block marked dirty:
 * 1. Scans all 6 neighbors for connectable blocks (using PowerConnectable.facesMask)
 * 2. Looks up the correct wire variant + rotation in WireLookupTable
 * 3. If the current block variant doesn't match, swaps to the target variant
 * 4. Preserves powered visual state ("On") if the wire was powered
 * 
 * The WireVisualUpdateTracker prevents event systems from re-queuing topology
 * changes when setBlock internally destroys + recreates the block entity.
 *
 * @param queue State queue with wireDirtyPositions to process
 * @param world The game world
 */
private fun processWireShapeUpdates(queue: StateChangeEventQueue, world: World) {
    val wireDirty = queue.wireDirtyPositions
    if (wireDirty.isEmpty()) return

    val positionsToProcess = wireDirty.toList()
    wireDirty.clear()

    for (pos in positionsToProcess) {
        // Only process positions that are actually wire blocks
        val hasPowerWire = getComponentForGlobalXyz(world, pos, ExamplePlugin.powerWireComponentType) ?: continue

        val chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z)
        val chunkRef = world.chunkStore.getChunkReference(chunkIndex) ?: continue
        val worldChunk = world.chunkStore.store.getComponent(
            chunkRef, WorldChunk.getComponentType()
        ) ?: continue

        val currentBlockType = worldChunk.getBlockType(pos) ?: continue
        val currentBlockId = currentBlockType.id ?: continue
        if (!isWireBlock(currentBlockId)) continue

        // Compute connections and look up target variant
        val connections = getConnections(world, pos)
        val result = WireLookupTable.lookup(connections)
        val targetBlockTypeId = result.getBlockTypeId("Power")
        val targetRotation = result.yawRotation

        // Check if we already have the correct variant
        val currentBase = currentBlockId.removePrefix("*")
        if (currentBase == targetBlockTypeId) continue

        val blockId = BlockType.getAssetMap().getIndex(targetBlockTypeId)
        val blockType = BlockType.getAssetMap().getAsset(blockId) ?: continue

        println("[WireShape] Swapping $pos from $currentBase to $targetBlockTypeId rot=${targetRotation.name}")

        // Mark/clear must be inside world.execute since setBlock triggers RefSystem synchronously
        world.execute {
            val chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(pos.x, pos.z)) ?: return@execute
            WireVisualUpdateTracker.markUpdating(pos)
            try {
                chunk.setBlock(pos.x, pos.y, pos.z, blockId, blockType, targetRotation.ordinal, 0, 0)

                // Preserve powered visual state if the block had one
                val vs = getComponentForGlobalXyz(world, pos, ExamplePlugin.visualStateComponentType)
                if (vs != null && vs.state != "default") {
                    val newWorldChunk = world.chunkStore.store.getComponent(
                        world.chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(pos.x, pos.z))!!,
                        WorldChunk.getComponentType()
                    )
                    val newBlockType = newWorldChunk?.getBlockType(pos)
                    if (newBlockType != null) {
                        newWorldChunk.setBlockInteractionState(pos, newBlockType, vs.state)
                    }
                }
            } finally {
                WireVisualUpdateTracker.clearUpdating(pos)
            }
        }
    }
}

/**
 * System that applies visual state changes to blocks after TopologySystem completes.
 * 
 * Runs in two phases:
 * 
 * ## Phase 1: Wire Shape Updates
 * - Processes queue.wireDirtyPositions
 * - Swaps wire blocks to correct variant (straight, corner, T, cross, etc.)
 * - Preserves powered visual state across variant swaps
 * 
 * ## Phase 2: Visual State Updates
 * - Processes queue.visualDirtyPositions
 * - Reads VisualState.state for each dirty position
 * - Calls worldChunk.setBlockInteractionState() to apply the visual change
 * 
 * System dependencies:
 * - Runs AFTER TopologySystem (declared via getDependencies)
 * - Ensures all logical state is computed before visuals are applied
 * 
 * This separation prevents:
 * - Race conditions between state computation and visual application
 * - Redundant visual updates (batches changes per tick)
 * - Topology recalculation during visual-only changes
 */
class VisualStateSystem : TickingSystem<ChunkStore>() {

    override fun tick(dt: Float, systemIndex: Int, store: Store<ChunkStore>) {
        val queue = store.getResource(ExamplePlugin.stateChangeQueueType)
        val world = store.externalData.world

        // --- Wire shape update phase (runs before visual state updates) ---
        processWireShapeUpdates(queue, world)

        val dirtyPositions = queue.visualDirtyPositions
        if (dirtyPositions.isEmpty()) return

        // Collect phase: read VisualState for each dirty position
        data class VisualMutation(val pos: Vector3i, val state: String)
        val mutations = mutableListOf<VisualMutation>()

        for (pos in dirtyPositions) {
            val vs = getComponentForGlobalXyz(world, pos, ExamplePlugin.visualStateComponentType)
                ?: continue
            mutations.add(VisualMutation(pos, vs.state))
        }

        // Commit phase: apply all visual mutations
        for ((pos, state) in mutations) {
            val chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z)
            val chunkRef = world.chunkStore.getChunkReference(chunkIndex) ?: continue
            val worldChunk = world.chunkStore.store.getComponent(
                chunkRef, WorldChunk.getComponentType()
            ) ?: continue
            val blockType = worldChunk.getBlockType(pos) ?: continue
            worldChunk.setBlockInteractionState(pos, blockType, state)
            println("[VisualStateSystem] $pos -> $state")
        }

        dirtyPositions.clear()
    }

    override fun getDependencies(): Set<Dependency<ChunkStore>> {
        return setOf(SystemDependency(Order.AFTER, TopologySystem::class.java))
    }
}
