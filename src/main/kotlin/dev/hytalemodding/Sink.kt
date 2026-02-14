package dev.hytalemodding

//class Sink : Component<ChunkStore> {
//    var on: Boolean = false
//
//    companion object {
//        @JvmField
//        val CODEC: BuilderCodec<Sink> = BuilderCodec.builder(Sink::class.java) { Sink() }.build()
//    }
//
//    fun getComponentType(): ComponentType<ChunkStore, Sink> {
//        return GridPlugin.sinkComponentType
//    }
//
//    override fun clone(): Component<ChunkStore> {
//        return Sink()
//    }
//}

//class SinkInitializer(
//    private val sinkComponentType: ComponentType<ChunkStore, Sink>
//) : RefSystem<ChunkStore>() {
//    override fun onEntityAdded(
//        ref: Ref<ChunkStore>,
//        reason: AddReason,
//        store: Store<ChunkStore>,
//        cmdBuf: CommandBuffer<ChunkStore>
//    ) {
//        println("Added block with Sink type")
//        val info = cmdBuf.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
//            ?: return
//
//        cmdBuf.getComponent(ref, GridPlugin.sinkComponentType) ?: return
//
//        val x = ChunkUtil.xFromBlockInColumn(info.index)
//        val y = ChunkUtil.yFromBlockInColumn(info.index)
//        val z = ChunkUtil.zFromBlockInColumn(info.index)
//
//        val worldChunk = cmdBuf.getComponent(info.chunkRef, WorldChunk.getComponentType())
//        worldChunk?.setTicking(x, y, z, true)
//    }
//
//    override fun onEntityRemove(
//        ref: Ref<ChunkStore>,
//        reason: RemoveReason,
//        store: Store<ChunkStore>,
//        cmdBuf: CommandBuffer<ChunkStore>
//    ) {
//        println("Removed block with Sink type")
//        val info = cmdBuf.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
//            ?: return
//
//        val x = ChunkUtil.xFromBlockInColumn(info.index)
//        val y = ChunkUtil.yFromBlockInColumn(info.index)
//        val z = ChunkUtil.zFromBlockInColumn(info.index)
//
//        val worldChunk = cmdBuf.getComponent(info.chunkRef, WorldChunk.getComponentType())
//        worldChunk?.setTicking(x, y, z, false)
//    }
//
//    override fun getQuery(): Query<ChunkStore> {
//        return Query.and(BlockModule.BlockStateInfo.getComponentType(), sinkComponentType)
//    }
//}
//
//class SinkReset(
//    private val sinkComponentType: ComponentType<ChunkStore, Sink>
//) : EntityTickingSystem<ChunkStore>() {
//    private var lastTickLog: Long = 0
//    private var lastResetLog: Long = 0
//
//    override fun tick(
//        dt: Float,
//        index: Int,
//        chunk: ArchetypeChunk<ChunkStore>,
//        store: Store<ChunkStore>,
//        cmdBuf: CommandBuffer<ChunkStore>
//    ) {
//        val now = System.currentTimeMillis()
//        if (now - lastTickLog >= 5000) {
//            println("[SinkReset] tick() called")
//            lastTickLog = now
//        }
//
//        val blockSectionComponent = BlockSection.getComponentType()
//        val chunkSectionComponent = ChunkSection.getComponentType()
//        val blocks = chunk.getComponent(index, blockSectionComponent)
//            ?: return
//        val section = chunk.getComponent(index, chunkSectionComponent)
//            ?: return
//        val blockComponentChunk = cmdBuf.getComponent(section.chunkColumnReference, BlockComponentChunk.getComponentType())
//            ?: return
//
//        val tickingCount = blocks.tickingBlocksCountCopy
//        if (tickingCount > 0 && now - lastTickLog >= 5000) {
//            println("[SinkReset] Section has $tickingCount ticking blocks")
//        }
//
//        blocks.forEachTicking(blockComponentChunk, cmdBuf, section.y) { chunk, cmdBuf, localX, localY, localZ, blockId ->
//            val blockRef = chunk.getEntityReference(ChunkUtil.indexBlockInColumn(localX, localY, localZ))
//                ?: return@forEachTicking BlockTickStrategy.IGNORED
//            val worldChunk = cmdBuf.getComponent(section.chunkColumnReference, WorldChunk.getComponentType())
//                ?: return@forEachTicking BlockTickStrategy.CONTINUE
//            val globalX = localX + (worldChunk.x * 32)
//            val globalZ = localZ + (worldChunk.z * 32)
//            val atPos = Vector3i(globalX, localY, globalZ)
//
//            if (now - lastResetLog >= 2000) {
//                println("[SinkReset] Resetting sink at ($globalX, $localY, $globalZ)")
//            }
//            setPoweredState(atPos, false, worldChunk)
//
//            BlockTickStrategy.CONTINUE
//        }
//    }
//
//
//
//    override fun getQuery(): Query<ChunkStore> {
//        return Query.and(BlockSection.getComponentType(), ChunkSection.getComponentType())
//    }
//}
