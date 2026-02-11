package dev.hytalemodding

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.*
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.asset.type.blocktick.BlockTickStrategy
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import java.util.logging.Logger

class ReactiveChunk : Component<ChunkStore?> {
    companion object {
        @JvmField
        val CODEC: BuilderCodec<ReactiveChunk> = BuilderCodec.builder(ReactiveChunk::class.java) { ReactiveChunk() }.build()
    }

    override fun clone(): Component<ChunkStore?> {
        return ReactiveChunk()
    }
}

/**
 * A system that modifies entity velocity to create low gravity effect.
 * This runs on the server's entity system, modifying the authoritative velocity.
 */
class ReactiveBlockInteractSystem(
    private val reactiveChunkType: ComponentType<ChunkStore, ReactiveChunk>
) : EntityTickingSystem<ChunkStore>() {

    private val logger = Logger.getLogger("ReactiveBlockInteractSystem")
    private var tickCount = 0

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuf: CommandBuffer<ChunkStore>
    ) {
        tickCount++
        logger.fine("[ReactiveBlockInteractSystem] tick() #$tickCount called - dt: $dt, index: $index")

        val reactiveChunkComponent: ReactiveChunk? = chunk.getComponent(index, reactiveChunkType)
        if (reactiveChunkComponent == null) {
            logger.fine("[ReactiveBlockInteractSystem] No ReactiveChunk component found, returning")
            return
        }

        val blocks = chunk.getComponent(index, BlockSection.getComponentType())
        if (blocks == null) {
            logger.fine("[ReactiveBlockInteractSystem] No BlockSection component found, returning")
            return
        }

        val section = chunk.getComponent(index, ChunkSection.getComponentType())
        if (section == null) {
            logger.fine("[ReactiveBlockInteractSystem] No ChunkSection component found, returning")
            return
        }

        if (blocks.tickingBlocksCountCopy == 0) {
            logger.fine("[ReactiveBlockInteractSystem] No ticking blocks in section, returning")
            return
        }

        logger.info("[ReactiveBlockInteractSystem] Processing ${blocks.tickingBlocksCountCopy} ticking blocks in section at y=${section.y}")

        val worldChunk = commandBuf.getComponent(section.chunkColumnReference, WorldChunk.getComponentType())
        val world = worldChunk?.world
        if (world == null) {
            logger.warning("[ReactiveBlockInteractSystem] World or worldChunk is null, returning")
            return
        }

        blocks.forEachTicking(blocks, commandBuf, section.y) { _, _, localX, localY, localZ, blockId ->
            val globalX = localX + (worldChunk.x * 32)
            val globalZ = localZ + (worldChunk.z * 32)
            logger.info("[ReactiveBlockInteractSystem] Setting block at ($globalX, $localY, $globalZ) to Air (was blockId: $blockId)")
            world.execute {
                world.setBlock(globalX, localY, globalZ, "Air")
            }
            BlockTickStrategy.IGNORED
        }
    }

    override fun getQuery(): Query<ChunkStore?> {
        return Query.and(reactiveChunkType)
    }
}
