package dev.hytalemodding

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

//import dev.hytalemodding.ExamplePlugin.Companion.sinkComponentType
//import dev.hytalemodding.ExamplePlugin.Companion.transportComponentType

//class Source : Component<ChunkStore> {
//    var power: Int = 8
//
//    companion object {
//        @JvmField
//        val CODEC: BuilderCodec<Source> = BuilderCodec.builder(Source::class.java) { Source() }.build()
//    }
//
//    fun getComponentType(): ComponentType<ChunkStore, Source> {
//        return ExamplePlugin.sourceComponentType
//    }
//
//    override fun clone(): Component<ChunkStore> {
//        return Source()
//    }
//}

//class SourceInitializer(
//    private val sourceComponentType: ComponentType<ChunkStore, Source>
//) : RefSystem<ChunkStore>() {
//    override fun onEntityAdded(
//        ref: Ref<ChunkStore>,
//        reason: AddReason,
//        store: Store<ChunkStore>,
//        cmdBuf: CommandBuffer<ChunkStore>
//    ) {
//        println("Added block with Source type.mem")
//        val info = cmdBuf.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
//            ?: return
//
//        cmdBuf.getComponent(ref, ExamplePlugin.sourceComponentType) ?: return
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
//        println("Removed block with Source type")
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
//        return Query.and(BlockModule.BlockStateInfo.getComponentType(), sourceComponentType)
//    }
//}

fun getAllAdjacent(pos: Vector3i): List<Vector3i> {
    val adjacentDelta: List<Triple<Int, Int, Int>> = listOf(
        Triple(0, 1, 0),
        Triple(0, -1, 0),
        Triple(1, 0, 0),
        Triple(-1, 0, 0),
        Triple(0, 0, 1),
        Triple(0, 0, -1),
    )
    val adjacents: MutableList<Vector3i> = mutableListOf()
    for (delta in adjacentDelta) {
        adjacents.add(Vector3i(pos.x + delta.first, pos.y + delta.second, pos.z + delta.third))
    }
    return adjacents
}

fun <T : Component<ChunkStore>> blockIsType(
    atPos: Vector3i,
    type: ComponentType<ChunkStore, T>,
    blockComponentChunk: BlockComponentChunk,
    cmdBuf: CommandBuffer<ChunkStore>
): Boolean {
    val index = ChunkUtil.indexBlockInColumn(atPos.x, atPos.y, atPos.z)
    val blockRef = blockComponentChunk.getEntityReference(index) ?: return false
    return cmdBuf.getComponent(blockRef, type) != null
}

private var lastBfsLogTime: Long = 0
private var lastTransportLogTime: Long = 0

data class BfsResult(val sinks: List<Vector3i>, val paths: Map<Vector3i, List<Vector3i>>)

fun <Transport : Component<ChunkStore>, Sink : Component<ChunkStore>> bfs(
    start: Vector3i,
    on: ComponentType<ChunkStore, Transport>,
    to: ComponentType<ChunkStore, Sink>,
    blockComponentChunk: BlockComponentChunk,
    cmdBuf: CommandBuffer<ChunkStore>
): BfsResult {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastBfsLogTime >= 5000) {
        println("BFS running from start position: ${start.x}, ${start.y}, ${start.z}")
        lastBfsLogTime = currentTime
    }

    var queue = mutableListOf<Vector3i>()
    var visited = mutableListOf<Vector3i>()
    val sinks = mutableListOf<Vector3i>()
    val parent = mutableMapOf<Vector3i, Vector3i?>()
    val sinkPaths = mutableMapOf<Vector3i, List<Vector3i>>()

    queue.add(start)
    visited.add(start)
    parent[start] = null

    while (queue.isNotEmpty()) {
        val current = queue.removeAt(0)

        // if sink, then track the pos and reconstruct path
        if (blockIsType(current, to, blockComponentChunk, cmdBuf)) {
            sinks.add(current)
            // Reconstruct path from start to this sink
            val path = mutableListOf<Vector3i>()
            var node: Vector3i? = current
            while (node != null) {
                path.add(0, node)
                node = parent[node]
            }
            sinkPaths[current] = path
        }

        val isTransport = blockIsType(current, on, blockComponentChunk, cmdBuf)
        if (isTransport) {
            val now = System.currentTimeMillis()
            if (now - lastTransportLogTime >= 5000) {
                println("Found connected transport at: ${current.x}, ${current.y}, ${current.z}")
                lastTransportLogTime = now
            }
        }

        // Expand from start (source) or from transports
        if (current == start || isTransport) {
            for (adjacent in getAllAdjacent(current)) {
                if (adjacent !in visited) {
                    visited.add(adjacent)
                    queue.add(adjacent)
                    parent[adjacent] = current
                }
            }
        }
    }
    return BfsResult(sinks, sinkPaths)
}

//class SourceSystem(
//    private val sourceComponentType: ComponentType<ChunkStore, Source>
//) : EntityTickingSystem<ChunkStore>() {
//    private var lastFoundSinkLogLine: Long = 0
//    private var lastTickLog: Long = 0
//
//    override fun tick(
//        dt: Float,
//        index: Int,
//        archetypeChunk: ArchetypeChunk<ChunkStore>,
//        store: Store<ChunkStore>,
//        commandBuffer: CommandBuffer<ChunkStore>
//    ) {
//        val now = System.currentTimeMillis()
//        if (now - lastTickLog >= 5000) {
//            println("[SourceSystem] tick() called")
//            lastTickLog = now
//        }
//
//        val blocks = archetypeChunk.getComponent(index, BlockSection.getComponentType())
//            ?: return
//
//        if (blocks.tickingBlocksCountCopy == 0) return
//
//        val now2 = System.currentTimeMillis()
//        if (now2 - lastTickLog >= 5000) {
//            println("[SourceSystem] has ${blocks.tickingBlocksCountCopy} ticking blocks")
//        }
//
//        val section = archetypeChunk.getComponent(index, ChunkSection.getComponentType())
//            ?: return
//
//        val blockComponentChunk = commandBuffer.getComponent(
//            section.chunkColumnReference,
//            BlockComponentChunk.getComponentType()
//        ) ?: return
//
//        blocks.forEachTicking(
//            blockComponentChunk,
//            commandBuffer,
//            section.y
//        ) { chunk, cmdBuf, localX, localY, localZ, blockId ->
//            val blockRef = chunk.getEntityReference(ChunkUtil.indexBlockInColumn(localX, localY, localZ))
//                ?: return@forEachTicking BlockTickStrategy.IGNORED
//
//            // Only run BFS from actual Source blocks, not other ticking blocks (like Sinks)
//            cmdBuf.getComponent(blockRef, sourceComponentType)
//                ?: return@forEachTicking BlockTickStrategy.CONTINUE
//
//            val worldChunk = commandBuffer.getComponent(
//                section.chunkColumnReference,
//                WorldChunk.getComponentType()
//            ) ?: return@forEachTicking BlockTickStrategy.IGNORED
//
//            val globalX = localX + (worldChunk.x * 32)
//            val globalZ = localZ + (worldChunk.z * 32)
//
//            // in each tick:
//            // search for all connected sinks
//            val bfsResult =
//                bfs(Vector3i(globalX, localY, globalZ), transportComponentType, sinkComponentType, blockComponentChunk, cmdBuf)
//            // set the connected sinks' component state.on = true
//            for (sinkPos in bfsResult.sinks) {
//                val now = System.currentTimeMillis()
//                if (now - lastFoundSinkLogLine >= 5000) {
//                    println(String.format("Found sink at (%d, %d, %d)!", sinkPos.x, sinkPos.y, sinkPos.z))
//                    val path = bfsResult.paths[sinkPos]
//                    if (path != null) {
//                        println("  Path to sink (${path.size} steps):")
//                        for ((i, pos) in path.withIndex()) {
//                            println("    [$i] (${pos.x}, ${pos.y}, ${pos.z})")
//                        }
//                    }
//                    lastFoundSinkLogLine = now
//                }
//                setPoweredState(sinkPos, true, worldChunk)
//            }
//            BlockTickStrategy.CONTINUE
//        }
//    }
//
//    override fun getDependencies(): Set<Dependency<ChunkStore>> {
//        return setOf(SystemDependency(Order.AFTER, SinkReset::class.java))
//    }
//
//    override fun getQuery(): Query<ChunkStore> {
//        return Query.and(BlockSection.getComponentType(), ChunkSection.getComponentType())
//    }
//}