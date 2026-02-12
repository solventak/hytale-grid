package dev.hytalemodding.newnet

import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

/**
 * Abstraction over world component access for power network topology logic.
 *
 * This interface allows topology code to be tested without a running Hytale server
 * by providing a mock implementation backed by simple data structures.
 */
interface WorldAccess {
    /**
     * Retrieves a component from a block at global world coordinates.
     *
     * @param pos Global block position
     * @param type The component type to retrieve
     * @return The component instance, or null if block doesn't exist or lacks the component
     */
    fun <T : Component<ChunkStore>> getComponent(pos: Vector3i, type: ComponentType<ChunkStore, T>): T?

    /**
     * Sets a component on a block at global world coordinates.
     *
     * @param pos Global block position
     * @param type The component type to set
     * @param value The component value to assign
     */
    fun <T : Component<ChunkStore>> setComponent(pos: Vector3i, type: ComponentType<ChunkStore, T>, value: T)
}

/**
 * WorldAccess implementation that wraps a real Hytale [World] object.
 *
 * Delegates to the chunk-based coordinate conversion logic that was previously
 * in the standalone `getComponentForGlobalXyz` function.
 */
class HytaleWorldAccess(val world: World) : WorldAccess {

    override fun <T : Component<ChunkStore>> getComponent(pos: Vector3i, type: ComponentType<ChunkStore, T>): T? {
        return getComponentForGlobalXyz(world, pos, type)
    }

    override fun <T : Component<ChunkStore>> setComponent(pos: Vector3i, type: ComponentType<ChunkStore, T>, value: T) {
        // In the real Hytale world, components are mutated in place after retrieval.
        // This method exists primarily for the mock implementation.
        // For HytaleWorldAccess, components are retrieved via getComponent and mutated directly.
    }
}

/**
 * Retrieves a component from a block at global world coordinates.
 *
 * Handles the chunk/block coordinate conversion required to access components
 * in Hytale's ECS architecture.
 *
 * @param world The game world
 * @param pos Global block position
 * @param type The component type to retrieve
 * @return The component instance, or null if block doesn't exist or lacks the component
 */
internal fun <T : Component<ChunkStore>> getComponentForGlobalXyz(
    world: World,
    pos: Vector3i,
    type: ComponentType<ChunkStore, T>
): T? {
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
