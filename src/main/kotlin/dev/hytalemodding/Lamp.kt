//package dev.hytalemodding
//
//import com.hypixel.hytale.codec.builder.BuilderCodec
//import com.hypixel.hytale.component.*
//import com.hypixel.hytale.component.query.Query
//import com.hypixel.hytale.component.system.RefSystem
//import com.hypixel.hytale.component.system.tick.EntityTickingSystem
//import com.hypixel.hytale.math.util.ChunkUtil
//import com.hypixel.hytale.server.core.asset.type.blocktick.BlockTickStrategy
//import com.hypixel.hytale.server.core.modules.block.BlockModule
//import com.hypixel.hytale.server.core.universe.world.World
//import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk
//import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
//import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
//import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection
//import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
//
//
//class Lamp : Component<ChunkStore> {
//    companion object {
//        @JvmField
//        val CODEC: BuilderCodec<Lamp> = BuilderCodec.builder(Lamp::class.java) { Lamp() }.build()
//    }
//
//    fun getComponentType(): ComponentType<ChunkStore, Lamp> {
//        return GridPlugin.lampComponentType
//    }
//
//    override fun clone(): Component<ChunkStore> {
//        return Lamp()
//    }
//
//    fun toggle(x: Int, y: Int, z: Int, on: Boolean, worldChunk: WorldChunk) {
//        val posVec = com.hypixel.hytale.math.vector.Vector3i(x, y, z)
//        val blockType = worldChunk.getBlockType(posVec) ?: return
//        if (on) {
//            worldChunk.setBlockInteractionState(posVec, blockType, "On")
//        } else {
//            worldChunk.setBlockInteractionState(posVec, blockType, "Off")
//        }
//    }
//}
//
//fun hasAdjacentBlock(x: Int, y: Int, z: Int, world: World): Boolean {
//    val adjacentDelta: List<Triple<Int, Int, Int>> = listOf(
//        Triple(0, 1, 0),
//        Triple(0, -1, 0),
//        Triple(1, 0, 0),
//        Triple(-1, 0, 0),
//        Triple(0, 0, 1),
//        Triple(0, 0, -1),
//    )
//    for (delta in adjacentDelta) {
//        // read state of block at the coord
//        val blockState = world.getBlock(x + delta.first, y + delta.second, z + delta.third)
//        if (blockState != 0) {
//            return true
//        }
//    }
//    return false
//}
//
//class LampSystem(
//    private val lampComponentType: ComponentType<ChunkStore, Lamp>
//) : EntityTickingSystem<ChunkStore>() {
//
//    override fun tick(
//        dt: Float,
//        index: Int,
//        archetypeChunk: ArchetypeChunk<ChunkStore>,
//        store: Store<ChunkStore>,
//        commandBuffer: CommandBuffer<ChunkStore>
//    ) {
//        val blocks = archetypeChunk.getComponent(index, BlockSection.getComponentType())
//            ?: return
//
//        if (blocks.tickingBlocksCountCopy == 0) return
//
//        val section = archetypeChunk.getComponent(index, ChunkSection.getComponentType())
//            ?: return
//
//        val blockComponentChunk = commandBuffer.getComponent(
//            section.chunkColumnReference,
//            BlockComponentChunk.getComponentType()
//        ) ?: return
//
//        blocks.forEachTicking(blockComponentChunk, commandBuffer, section.y) { chunk, cmdBuf, localX, localY, localZ, blockId ->
//            val blockRef = chunk.getEntityReference(ChunkUtil.indexBlockInColumn(localX, localY, localZ))
//                ?: return@forEachTicking BlockTickStrategy.IGNORED
//
//            val lamp = cmdBuf.getComponent(blockRef, lampComponentType)
//                ?: return@forEachTicking BlockTickStrategy.IGNORED
//
//            val worldChunk = commandBuffer.getComponent(
//                section.chunkColumnReference,
//                WorldChunk.getComponentType()
//            ) ?: return@forEachTicking BlockTickStrategy.IGNORED
//
//            val globalX = localX + (worldChunk.x * 32)
//            val globalZ = localZ + (worldChunk.z * 32)
//
//            // if adjacent block then turn on
//            lamp.toggle(globalX, localY, globalZ,hasAdjacentBlock(globalX, localY, globalZ, worldChunk.world), worldChunk)
//
//            BlockTickStrategy.CONTINUE
//        }
//    }
//
//    override fun getQuery(): Query<ChunkStore> {
//        return Query.and(BlockSection.getComponentType(), ChunkSection.getComponentType())
//    }
//}
//
//class LampInitializer(
//    private val lampComponentType: ComponentType<ChunkStore, Lamp>
//) : RefSystem<ChunkStore>() {
//
//    override fun onEntityAdded(
//        ref: Ref<ChunkStore>,
//        reason: AddReason,
//        store: Store<ChunkStore>,
//        commandBuffer: CommandBuffer<ChunkStore>
//    ) {
//        val info = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
//            ?: return
//
//        commandBuffer.getComponent(ref, GridPlugin.lampComponentType) ?: return
//
//        val x = ChunkUtil.xFromBlockInColumn(info.index)
//        val y = ChunkUtil.yFromBlockInColumn(info.index)
//        val z = ChunkUtil.zFromBlockInColumn(info.index)
//
//        val worldChunk = commandBuffer.getComponent(info.chunkRef, WorldChunk.getComponentType())
//        worldChunk?.setTicking(x, y, z, true)
//    }
//
//    override fun onEntityRemove(
//        ref: Ref<ChunkStore>,
//        reason: RemoveReason,
//        store: Store<ChunkStore>,
//        commandBuffer: CommandBuffer<ChunkStore>
//    ) {
//        // No cleanup needed
//    }
//
//    override fun getQuery(): Query<ChunkStore> {
//        return Query.and(BlockModule.BlockStateInfo.getComponentType(), lampComponentType)
//    }
//}
