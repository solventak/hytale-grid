//package dev.hytalemodding.wire
//
//import com.hypixel.hytale.component.CommandBuffer
//import com.hypixel.hytale.component.Store
//import com.hypixel.hytale.component.system.tick.TickingSystem
//import com.hypixel.hytale.math.util.ChunkUtil
//import com.hypixel.hytale.math.vector.Vector3i
//import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk
//import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
//import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
//import com.hypixel.hytale.component.dependency.Dependency
//import com.hypixel.hytale.component.dependency.SystemDependency
//import com.hypixel.hytale.component.dependency.Order
//import dev.hytalemodding.*
//
//class ElectricalNodeControlSystem : TickingSystem<ChunkStore>() {
//
//    companion object {
//        private const val MAX_ITERATIONS = 10
//    }
//
//    override fun tick(dt: Float, systemIndex: Int, store: Store<ChunkStore>) {
//        val queue = store.getResource(ExamplePlugin.stateChangeQueueType)
//
//        if (queue.dirtySignalPositions.isEmpty()) {
//            // Cycle is idle — clear oscillation tracking so next real event starts fresh
//            queue.toggledThisCycle.clear()
//            return
//        }
//
//        var processed = false
//        store.forEachChunk(java.util.function.BiConsumer { _, cmdBuf ->
//            if (processed) return@BiConsumer
//            processed = true
//
//            // Phase 1: Process dirty signal positions (relays and sources)
//            if (queue.dirtySignalPositions.isNotEmpty()) {
//                println("[ElectricalNodeControlSystem] Evaluating ${queue.dirtySignalPositions.size} dirty signal positions")
//
//                val signalControlledPositions = mutableSetOf<Vector3i>()
//
//                for (signalPos in queue.dirtySignalPositions) {
//                    for (adj in getAllAdjacent(signalPos)) {
//                        val chunkIndex = ChunkUtil.indexChunkFromBlock(adj.x, adj.z)
//                        val chunkRef = cmdBuf.externalData.getChunkReference(chunkIndex) ?: continue
//                        val blockComponentChunk = cmdBuf.getComponent(chunkRef, BlockComponentChunk.getComponentType()) ?: continue
//                        val blockIndex = ChunkUtil.indexBlockInColumn(adj.x and 31, adj.y, adj.z and 31)
//                        val blockRef = blockComponentChunk.getEntityReference(blockIndex) ?: continue
//                        val isRelay = cmdBuf.getComponent(blockRef, ExamplePlugin.relayComponentType) != null
//                        val isSource = cmdBuf.getComponent(blockRef, ExamplePlugin.powerSourceComponentType) != null
//                        if (isRelay || isSource) {
//                            signalControlledPositions.add(adj)
//                        }
//                    }
//                }
//
//                queue.dirtySignalPositions.clear()
//
//                for (pos in signalControlledPositions) {
//                    evaluateSignalControlledBlock(pos, store, cmdBuf, queue)
//                }
//            }
//
//        })
//    }
//
//    private fun evaluateSignalControlledBlock(
//        pos: Vector3i,
//        store: Store<ChunkStore>,
//        cmdBuf: CommandBuffer<ChunkStore>,
//        queue: StateChangeEventQueue
//    ) {
//        val chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z)
//        val chunkRef = cmdBuf.externalData.getChunkReference(chunkIndex) ?: return
//        val blockComponentChunk = cmdBuf.getComponent(chunkRef, BlockComponentChunk.getComponentType()) ?: return
//        val blockIndex = ChunkUtil.indexBlockInColumn(pos.x and 31, pos.y, pos.z and 31)
//        val blockRef = blockComponentChunk.getEntityReference(blockIndex) ?: return
//
//        val node = cmdBuf.getComponent(blockRef, ExamplePlugin.electricalNodeComponentType) ?: return
//        val relay = cmdBuf.getComponent(blockRef, ExamplePlugin.relayComponentType)
//        val powerSource = cmdBuf.getComponent(blockRef, ExamplePlugin.powerSourceComponentType)
//
//        // Get the SignalControlled implementation
//        val signalControlled: SignalControlled = relay ?: powerSource ?: return
//
//        // Check adjacent signal wires for powered state
//        val signalPresent = checkAdjacentSignals(pos, blockComponentChunk, cmdBuf)
//        val shouldConduct = signalControlled.shouldConduct(signalPresent)
//
//        if (relay != null) {
//            // Relay: toggle transmits
//            val transmits = cmdBuf.getComponent(blockRef, ExamplePlugin.transmitsComponentType) ?: return
//            val currentlyConducts = "Power" in transmits.transmits
//            if (shouldConduct != currentlyConducts) {
//                if (pos in queue.toggledThisCycle) {
//                    println("[ElectricalNodeControlSystem] Relay at (${pos.x}, ${pos.y}, ${pos.z}) oscillation detected, skipping")
//                    return
//                }
//                queue.toggledThisCycle.add(pos)
//                println("[ElectricalNodeControlSystem] Relay at (${pos.x}, ${pos.y}, ${pos.z}) transmits: ${transmits.transmits} -> $shouldConduct")
//                // Use clear+addAll to avoid mutating shared template; always keep Signal for BFS connectivity
//                transmits.transmits.clear()
//                if (shouldConduct) {
//                    transmits.transmits.addAll(listOf("Signal", "Power"))
//                } else {
//                    transmits.transmits.add("Signal")
//                }
//                updateVisualState(pos, shouldConduct, store, cmdBuf)
//                if (shouldConduct) {
//                    queue.pending.add(StateChangeEvent(pos, StateChangeType.PLACED_OR_UPDATED))
//                } else {
//                    for (adj in getAllAdjacent(pos)) {
//                        queue.pending.add(StateChangeEvent(adj, StateChangeType.PLACED_OR_UPDATED))
//                    }
//                }
//            }
//        } else if (powerSource != null) {
//            // Source: toggle disabled state
//            val wasDisabled = powerSource.disabled
//            powerSource.disabled = !shouldConduct
//            if (wasDisabled != powerSource.disabled) {
//                if (pos in queue.toggledThisCycle) {
//                    println("[ElectricalNodeControlSystem] Source at (${pos.x}, ${pos.y}, ${pos.z}) oscillation detected, skipping")
//                    powerSource.disabled = wasDisabled  // revert
//                    return
//                }
//                queue.toggledThisCycle.add(pos)
//                println("[ElectricalNodeControlSystem] Source at (${pos.x}, ${pos.y}, ${pos.z}) disabled: ${powerSource.disabled}")
//                updateVisualState(pos, shouldConduct, store, cmdBuf)
//                // Re-propagate power from this source's position
//                queue.pending.add(StateChangeEvent(pos, StateChangeType.PLACED_OR_UPDATED))
//            }
//        }
//    }
//
//    private fun checkAdjacentSignals(
//        pos: Vector3i,
//        blockComponentChunk: BlockComponentChunk,
//        cmdBuf: CommandBuffer<ChunkStore>
//    ): Boolean {
//        for (adj in getAllAdjacent(pos)) {
//            val adjBlockIndex = ChunkUtil.indexBlockInColumn(adj.x and 31, adj.y, adj.z and 31)
//            val adjRef = blockComponentChunk.getEntityReference(adjBlockIndex) ?: continue
//            // SignalSense emits signal when powered (but has "Power" transmits, not "Signal")
//            if (cmdBuf.getComponent(adjRef, ExamplePlugin.signalSenseComponentType) != null) {
//                val adjPowerable = cmdBuf.getComponent(adjRef, ExamplePlugin.powerableComponentType)
//                if (adjPowerable?.powered == true) return true
//                continue
//            }
//            val adjTransmits = cmdBuf.getComponent(adjRef, ExamplePlugin.transmitsComponentType) ?: continue
//            if ("Signal" !in adjTransmits.transmits) continue
//            val adjPowerable = cmdBuf.getComponent(adjRef, ExamplePlugin.powerableComponentType) ?: continue
//            if (adjPowerable.powered) return true
//        }
//        return false
//    }
//
//    private fun updateVisualState(
//        pos: Vector3i,
//        conductive: Boolean,
//        store: Store<ChunkStore>,
//        cmdBuf: CommandBuffer<ChunkStore>
//    ) {
//        val chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z)
//        val chunkRef = store.externalData.getChunkReference(chunkIndex) ?: return
//        val worldChunk = cmdBuf.getComponent(chunkRef, WorldChunk.getComponentType()) ?: return
//        val blockType = worldChunk.getBlockType(pos) ?: return
//        worldChunk.setBlockInteractionState(pos, blockType, if (conductive) "On" else "default")
//    }
//
//    override fun getDependencies(): Set<Dependency<ChunkStore>> {
//        return setOf(SystemDependency(Order.AFTER, StateChangeProcessor::class.java))
//    }
//}
