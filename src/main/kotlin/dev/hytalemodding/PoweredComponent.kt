//package dev.hytalemodding
//
//import com.hypixel.hytale.codec.Codec
//import com.hypixel.hytale.codec.KeyedCodec
//import com.hypixel.hytale.codec.builder.BuilderCodec
//import com.hypixel.hytale.codec.codecs.set.SetCodec
//import com.hypixel.hytale.component.*
//import com.hypixel.hytale.component.query.Query
//import com.hypixel.hytale.component.system.EntityEventSystem
//import com.hypixel.hytale.component.system.RefSystem
//import com.hypixel.hytale.component.system.tick.TickingSystem
//import com.hypixel.hytale.math.util.ChunkUtil
//import com.hypixel.hytale.math.vector.Vector3i
//import com.hypixel.hytale.server.core.entity.entities.Player
//import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent
//import com.hypixel.hytale.server.core.modules.block.BlockModule
//import com.hypixel.hytale.server.core.universe.world.World
//import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk
//import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
//import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
//import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
//import dev.hytalemodding.wire.SignalControlled
//import dev.hytalemodding.wire.SignalSense
//import dev.hytalemodding.wire.WireVisualUpdateTracker
//
//fun getConnectableNeighbors(
//    current: Vector3i,
//    currentRef: Ref<ChunkStore>,
//    blockComponentChunk: BlockComponentChunk,
//    cmdBuf: CommandBuffer<ChunkStore>
//): List<Pair<Vector3i, Ref<ChunkStore>>> {
//    val currentTransmitsNode = cmdBuf.getComponent(currentRef, GridPlugin.transmitsComponentType)
//
//    // Source blocks only propagate through Power, not Signal
//    val isCurrentSource = cmdBuf.getComponent(currentRef, GridPlugin.powerSourceComponentType) != null
//    val effectiveCurrentTransmits = if (isCurrentSource && currentTransmitsNode != null) {
//        currentTransmitsNode.transmits.filter { it == "Power" }.toSet()
//    } else {
//        currentTransmitsNode?.transmits
//    }
//
//    return getAllAdjacent(current).mapNotNull { adjacent ->
//        val adjBlockIndex = ChunkUtil.indexBlockInColumn(adjacent.x and 31, adjacent.y, adjacent.z and 31)
//        val adjRef = blockComponentChunk.getEntityReference(adjBlockIndex) ?: return@mapNotNull null
//
//        // Must have ElectricalNode to be part of the network
//        cmdBuf.getComponent(adjRef, GridPlugin.electricalNodeComponentType)
//            ?: return@mapNotNull null
//
//        val adjTransmitsNode = cmdBuf.getComponent(adjRef, GridPlugin.transmitsComponentType)
//
//        // Source blocks only propagate through Power, not Signal
//        val isAdjSource = cmdBuf.getComponent(adjRef, GridPlugin.powerSourceComponentType) != null
//        val effectiveAdjTransmits = if (isAdjSource && adjTransmitsNode != null) {
//            adjTransmitsNode.transmits.filter { it == "Power" }.toSet()
//        } else {
//            adjTransmitsNode?.transmits
//        }
//
//        // Check wire type compatibility
//        // Empty transmits acts as universal sink (connects to any wire type but won't propagate further)
//        val canConnect = when {
//            effectiveCurrentTransmits.isNullOrEmpty() -> true      // Non-wire or empty transmits connects to all
//            effectiveAdjTransmits.isNullOrEmpty() -> true           // Wire connects to non-wire or empty transmits
//            else -> effectiveCurrentTransmits.intersect(effectiveAdjTransmits).isNotEmpty()
//        }
//
//        if (canConnect) Pair(adjacent, adjRef) else null
//    }
//}
//
//class ElectricalNode : Component<ChunkStore> {
//    companion object {
//        @JvmField
//        val CODEC: BuilderCodec<ElectricalNode> = BuilderCodec.builder(ElectricalNode::class.java) { ElectricalNode() }.build()
//    }
//
//    fun getComponentType(): ComponentType<ChunkStore, ElectricalNode> {
//        return GridPlugin.electricalNodeComponentType
//    }
//
//    override fun clone(): Component<ChunkStore> {
//        return ElectricalNode()
//    }
//}
//
//class PowerSource : Component<ChunkStore>, SignalControlled {
//    var disabled = false
//
//    companion object {
//        @JvmField
//        val CODEC: BuilderCodec<PowerSource> = BuilderCodec.builder(PowerSource::class.java) { PowerSource() }.build()
//    }
//
//    override fun shouldConduct(signalPresent: Boolean): Boolean {
//        // Source is normally on; signal turns it off
//        return !signalPresent
//    }
//
//    fun getComponentType(): ComponentType<ChunkStore, PowerSource> {
//        return GridPlugin.powerSourceComponentType
//    }
//
//    override fun clone(): Component<ChunkStore> {
//        return PowerSource().also { it.disabled = this.disabled }
//    }
//}
//
//class Powerable : Component<ChunkStore> {
//    var powered = false
//    var hasPoweredVisualState = false
//    var constant = false
//
//    companion object {
//        @JvmField
//        val CODEC: BuilderCodec<Powerable> = BuilderCodec.builder(Powerable::class.java) { Powerable() }
//            .appendInherited(
//                KeyedCodec("Powered", Codec.BOOLEAN),
//                { obj, value -> obj.powered = value },
//                { obj -> obj.powered },
//                { obj, parent -> obj.powered = parent.powered }
//            )
//            .add()
//            .appendInherited(
//                KeyedCodec("Constant", Codec.BOOLEAN),
//                { obj, value -> obj.constant = value },
//                { obj -> obj.constant },
//                { obj, parent -> obj.constant = parent.constant }
//            )
//            .add()
//            .appendInherited(
//                KeyedCodec("HasPoweredVisualState", Codec.BOOLEAN),
//                { obj, value -> obj.hasPoweredVisualState = value },
//                { obj -> obj.hasPoweredVisualState },
//                { obj, parent -> obj.hasPoweredVisualState = parent.hasPoweredVisualState }
//            )
//            .add()
//            .build()
//    }
//
//    fun getComponentType(): ComponentType<ChunkStore, Powerable> {
//        return GridPlugin.powerableComponentType
//    }
//
//    override fun clone(): Component<ChunkStore> {
//        return Powerable().also {
//            it.powered = this.powered
//            it.hasPoweredVisualState = this.hasPoweredVisualState
//            it.constant = this.constant
//        }
//    }
//
//    fun changePowered(store: Store<ChunkStore>, cmdBuf: CommandBuffer<ChunkStore>, powered: Boolean, pos: Vector3i) {
//        if (!powered && constant) {
//            println("Cannot change power status for a constant block")
//            return
//        }
//        this.powered = powered
//        if (hasPoweredVisualState) {
//            val chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z)
//            val chunkRef = store.externalData.getChunkReference(chunkIndex)
//            if (chunkRef == null) { println("[changePowered] FAIL: chunkRef null"); return }
//            val worldChunk = cmdBuf.getComponent(chunkRef, WorldChunk.getComponentType())
//            if (worldChunk == null) { println("[Render] FAIL: worldChunk null"); return }
//            val blockType = worldChunk.getBlockType(pos)
//            if (blockType == null) { println("[changePowered] FAIL: blockType null"); return }
//            worldChunk.setBlockInteractionState(pos, blockType, if (powered) "On" else "default")
//        }
//    }
//}
//
//class Transmits : Component<ChunkStore> {
//    var transmits: MutableSet<String> = mutableSetOf()
//
//    companion object {
//        @JvmField
//        val CODEC: BuilderCodec<Transmits> = BuilderCodec.builder(Transmits::class.java) { Transmits() }
//            .appendInherited(
//                KeyedCodec("Transmits", SetCodec(Codec.STRING, ::HashSet, true)),
//                { obj, value -> obj.transmits = value.toMutableSet() },
//                { obj -> obj.transmits },
//                { obj, parent -> obj.transmits = parent.transmits.toMutableSet() }
//            )
//            .add()
//            .build()
//    }
//
//    fun getComponentType(): ComponentType<ChunkStore, Transmits> {
//        return GridPlugin.transmitsComponentType
//    }
//
//    override fun clone(): Component<ChunkStore> {
//        return Transmits().also {
//            it.transmits = this.transmits.toMutableSet()
//        }
//    }
//}
//
//enum class StateChangeType { PLACED_OR_UPDATED, DESTROYED }
//
//data class StateChangeEvent(
//    val pos: Vector3i,
//    val changeType: StateChangeType,
//    val processAfterTick: Long = 0  // 0 = process immediately, >0 = wait until that tick
//)
//
//class StateChangeEventQueue : Resource<ChunkStore> {
//    val pending: MutableList<StateChangeEvent> = mutableListOf()
//    val dirtySignalPositions: MutableSet<Vector3i> = mutableSetOf()
//    val toggledThisCycle: MutableSet<Vector3i> = mutableSetOf()
//
//    companion object {
//        @JvmField
//        val CODEC: BuilderCodec<StateChangeEventQueue> =
//            BuilderCodec.builder(StateChangeEventQueue::class.java) { StateChangeEventQueue() }.build()
//    }
//
//    override fun clone(): Resource<ChunkStore> = StateChangeEventQueue()
//}
//
////class PowerChangeRenderEvent(
////    val pos: Vector3i,
////    val powered: Boolean
////) : EcsEvent()
////
////class PowerChangeRenderSystem : WorldEventSystem<ChunkStore, PowerChangeRenderEvent>(PowerChangeRenderEvent::class.java) {
////    override fun handle(
////        store: Store<ChunkStore>,
////        commandBuffer: CommandBuffer<ChunkStore>,
////        event: PowerChangeRenderEvent
////    ) {
////        println("[Render] Event received: pos=${event.pos}, powered=${event.powered}")
////
////        val chunkIndex = ChunkUtil.indexChunkFromBlock(event.pos.x, event.pos.z)
////        val chunkRef = store.externalData.getChunkReference(chunkIndex)
////        if (chunkRef == null) { println("[Render] FAIL: chunkRef null"); return }
////
////        val blockComponentChunk = commandBuffer.getComponent(chunkRef, BlockComponentChunk.getComponentType())
////        if (blockComponentChunk == null) { println("[Render] FAIL: blockComponentChunk null"); return }
////
////        val worldChunk = commandBuffer.getComponent(chunkRef, WorldChunk.getComponentType())
////        if (worldChunk == null) { println("[Render] FAIL: worldChunk null"); return }
////
////        val localX = event.pos.x and 31
////        val localZ = event.pos.z and 31
////        val blockIndex = ChunkUtil.indexBlockInColumn(localX, event.pos.y, localZ)
////        val blockRef = blockComponentChunk.getEntityReference(blockIndex)
////        if (blockRef == null) { println("[Render] FAIL: blockRef null at index $blockIndex"); return }
////
////        val electricalNode = commandBuffer.getComponent(blockRef, GridPlugin.electricalNodeComponentType)
////        if (electricalNode?.onOffVisual != true) {
////            println("[Render] FAIL: onOffVisual=${electricalNode?.onOffVisual}"); return
////        }
////
////        // Skip state change for user-controlled blocks (relays, switches)
////        if (electricalNode.powerControlsState != true) {
////            println("[Render] SKIP: powerControlsState=false, user controls state"); return
////        }
////
////        val blockType = worldChunk.getBlockType(event.pos)
////        if (blockType == null) { println("[Render] FAIL: blockType null"); return }
////
////        val state = if (event.powered) "On" else "default"
////        println("[Render] SUCCESS: Setting $blockType to state '$state' at ${event.pos}")
////        worldChunk.setBlockInteractionState(event.pos, blockType, state)
////    }
////}
//
//fun <T : Component<ChunkStore>> getComponentForGlobalXyz(world: World, pos: Vector3i, type: ComponentType<ChunkStore, T>): T? {
//    val chunkStore = world.chunkStore
//    // Get chunk
//    val chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z)
//    val chunkRef = chunkStore.getChunkReference(chunkIndex) ?: return null
//
//    // Get BlockComponentChunk
//    val blockComponentChunk = chunkStore.store.getComponent(
//        chunkRef,
//        BlockComponentChunk.getComponentType()
//    ) ?: return null
//
//    // Convert to local coords and get block index
//    val localX = pos.x and 31
//    val localZ = pos.z and 31
//    val blockIndex = ChunkUtil.indexBlockInColumn(localX, pos.y, localZ)
//
//    // Get the block's entity reference
//    val blockRef = blockComponentChunk.getEntityReference(blockIndex) ?: return null
//    if (!blockRef.isValid) return null
//    return chunkStore.store.getComponent(blockRef, type)
//}
//
//fun hasAdjacentPoweredSignal(world: World, pos: Vector3i): Boolean {
//    for (adj in getAllAdjacent(pos)) {
//        val node = getComponentForGlobalXyz(world, adj, GridPlugin.electricalNodeComponentType) ?: continue
//        // SignalSense emits signal when powered (but has "Power" transmits, not "Signal")
//        if (getComponentForGlobalXyz(world, adj, GridPlugin.signalSenseComponentType) != null) {
//            val adjPowerable = getComponentForGlobalXyz(world, adj, GridPlugin.powerableComponentType)
//            if (adjPowerable?.powered == true) return true
//            continue
//        }
//        val transmits = getComponentForGlobalXyz(world, adj, GridPlugin.transmitsComponentType) ?: continue
//        if (!transmits.transmits.contains("Signal")) continue
//        val powerable = getComponentForGlobalXyz(world, adj, GridPlugin.powerableComponentType) ?: continue
//        if (powerable.powered) return true
//    }
//    return false
//}
//
//data class ResetResult(
//    val sources: List<Vector3i>,
//    val nonSourceNodes: List<Vector3i>
//)
//
//fun findSourcesAndResetFromMultiple(
//    startPositions: List<Vector3i>,
//    blockComponentChunk: BlockComponentChunk,
//    store: Store<ChunkStore>,
//    cmdBuf: CommandBuffer<ChunkStore>
//): ResetResult {
//    val sources = mutableListOf<Vector3i>()
//    val nodes = mutableListOf<Vector3i>()
////    val resetPositions = mutableSetOf<Vector3i>()
////    val allVisualPositions = mutableSetOf<Vector3i>()  // Track ALL positions needing visual sync
//    val queue = mutableListOf<Vector3i>()
//    val visited = mutableSetOf<Vector3i>()
//
//    // Initialize with all start positions
//    for (start in startPositions) {
//        if (start !in visited) {
//            queue.add(start)
//            visited.add(start)
//        }
//    }
//
//    while (queue.isNotEmpty()) {
//        val current = queue.removeAt(0)
//        val blockIndex = ChunkUtil.indexBlockInColumn(current.x and 31, current.y, current.z and 31)
//        val blockRef = blockComponentChunk.getEntityReference(blockIndex)
//        if (blockRef == null) {
//            println("[BFS] pos=$current - blockRef null, skipping")
//            continue
//        }
//
//        // Reset power state
//        val powerable = cmdBuf.getComponent(blockRef, GridPlugin.powerableComponentType)
//        if (powerable != null) {
//            nodes.add(current)
//            // Relay visual reflects conducting state, not powered state — skip visual update
//            val isRelay = cmdBuf.getComponent(blockRef, GridPlugin.relayComponentType) != null
//            if (isRelay) {
//                if (!powerable.constant) powerable.powered = false
//            } else {
//                powerable.changePowered(store, cmdBuf, false, current)
//            }
//        }
//
//        val transmits = cmdBuf.getComponent(blockRef, GridPlugin.transmitsComponentType)
//        println("[BFS] pos=$current, powered=${powerable?.powered}, transmits=${transmits?.transmits}, onOffVisual=${powerable?.hasPoweredVisualState}")
//
//        // Check if this is a source (skip disabled sources)
//        val powerSource = cmdBuf.getComponent(blockRef, GridPlugin.powerSourceComponentType)
//        if (powerSource != null && !powerSource.disabled) {
//            sources.add(current)
//            println("[findSourcesAndReset] Found source at (${current.x}, ${current.y}, ${current.z})")
//        }
//
//        if (transmits?.transmits?.isNotEmpty() ?: false) {
//            for ((adjacent, _) in getConnectableNeighbors(current, blockRef, blockComponentChunk, cmdBuf)) {
//                if (adjacent !in visited) {
//                    visited.add(adjacent)
//                    queue.add(adjacent)
//                }
//            }
//        }
//    }
//
//    return ResetResult(sources, nodes.toList())
//}
//
//fun propagatePowerFromSource(
//    source: Vector3i,
//    blockComponentChunk: BlockComponentChunk,
//    store: Store<ChunkStore>,
//    cmdBuf: CommandBuffer<ChunkStore>
//): Set<Vector3i> {
////    val poweredPositions = mutableSetOf<Vector3i>()
//    val queue = mutableListOf<Vector3i>()
//    val visited = mutableSetOf<Vector3i>()
//    val blocksTurnedOn = mutableSetOf<Vector3i>()
//
//    queue.add(source)
//    visited.add(source)
//
//    while (queue.isNotEmpty()) {
//        val current = queue.removeAt(0)
//        val blockIndex = ChunkUtil.indexBlockInColumn(current.x and 31, current.y, current.z and 31)
//        val blockRef = blockComponentChunk.getEntityReference(blockIndex) ?: continue
//
//        // Set powered state
//        val powered = cmdBuf.getComponent(blockRef, GridPlugin.powerableComponentType)
//        if (powered != null) {
//            // Relay visual reflects conducting state, not powered state — skip visual update
//            val isRelay = cmdBuf.getComponent(blockRef, GridPlugin.relayComponentType) != null
//            if (isRelay) {
//                powered.powered = true
//            } else {
//                powered.changePowered(store, cmdBuf, true, current)
//            }
//        }
//        blocksTurnedOn.add(current)
//
//        val transmits = cmdBuf.getComponent(blockRef, GridPlugin.transmitsComponentType)
//
//        if (transmits?.transmits?.isNotEmpty() ?: false) {
//            for ((adjacent, _) in getConnectableNeighbors(current, blockRef, blockComponentChunk, cmdBuf)) {
//                if (adjacent !in visited) {
//                    visited.add(adjacent)
//                    queue.add(adjacent)
//                }
//            }
//        }
//    }
//    return blocksTurnedOn
////    println("[propagatePowerFromSource] Powered ${poweredPositions.size} nodes from source at (${source.x}, ${source.y}, ${source.z})")
////    return poweredPositions
//}
//
//fun globalPosFromLocal(info: BlockModule.BlockStateInfo, cmdBuf: CommandBuffer<ChunkStore>): Vector3i? {
//    val worldChunk = cmdBuf.getComponent(info.chunkRef, WorldChunk.getComponentType()) ?: return null
//    val localX = ChunkUtil.xFromBlockInColumn(info.index)
//    val localY = ChunkUtil.yFromBlockInColumn(info.index)
//    val localZ = ChunkUtil.zFromBlockInColumn(info.index)
//    val globalX = localX + (worldChunk.x * 32)
//    val globalZ = localZ + (worldChunk.z * 32)
//    return Vector3i(globalX, localY, globalZ)
//}
//
//class PowerableBlockAddedSystem : RefSystem<ChunkStore>() {
//    override fun onEntityAdded(
//        ref: Ref<ChunkStore>,
//        reason: AddReason,
//        store: Store<ChunkStore>,
//        cmdBuf: CommandBuffer<ChunkStore>
//    ) {
//        val info = cmdBuf.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
//        cmdBuf.getComponent(ref, GridPlugin.electricalNodeComponentType) ?: return
//
//        // Get global position from BlockStateInfo
//        val pos = globalPosFromLocal(info, cmdBuf) ?: return
//
//        // Skip if this is just a visual update from WireConnectionSystem (not a real new block)
//        if (WireVisualUpdateTracker.isUpdating(pos.x, pos.y, pos.z)) {
//            println("[PowerableBlockAddedSystem] Skipping visual-only update at (${pos.x}, ${pos.y}, ${pos.z})")
//            return
//        }
//
//        val queue = store.getResource(GridPlugin.stateChangeQueueType)
//        queue.pending.add(StateChangeEvent(pos, StateChangeType.PLACED_OR_UPDATED))
//        println("[PowerableBlockAddedSystem] Queued PLACED at (${pos.x}, ${pos.y}, ${pos.z}), queue size: ${queue.pending.size}")
//    }
//
//    override fun onEntityRemove(
//        ref: Ref<ChunkStore>,
//        reason: RemoveReason,
//        store: Store<ChunkStore>,
//        cmdBuf: CommandBuffer<ChunkStore>
//    ) {
//        // Not used - we handle removal via BreakBlockEvent
//    }
//
//    override fun getQuery(): Query<ChunkStore> = Query.and(
//        BlockModule.BlockStateInfo.getComponentType(),
//        GridPlugin.electricalNodeComponentType
//    )
//}
//
//class BreakBlockStateChangeEvent : EntityEventSystem<EntityStore, BreakBlockEvent>(BreakBlockEvent::class.java) {
//    override fun handle(
//        index: Int,
//        chunk: ArchetypeChunk<EntityStore>,
//        store: Store<EntityStore>,
//        cmdBuf: CommandBuffer<EntityStore>,
//        event: BreakBlockEvent
//    ) {
//        val world = cmdBuf.externalData.world
//        val pos = event.targetBlock
//
//        // Only queue if block has Powerable component
//        // Note: BreakBlockEvent fires before block is removed, so component should still exist
//        val powerable = getComponentForGlobalXyz(world, pos, GridPlugin.electricalNodeComponentType)
//        if (powerable != null) {
//            val queue = world.chunkStore.store.getResource(GridPlugin.stateChangeQueueType)
//            queue.pending.add(StateChangeEvent(pos, StateChangeType.DESTROYED))
//            println("[BreakBlockStateChangeEvent] Queued DESTROYED at (${pos.x}, ${pos.y}, ${pos.z}), queue size: ${queue.pending.size}")
//        }
//    }
//
//    override fun getQuery(): Query<EntityStore> = Query.and(Player.getComponentType())
//}
//
//class StateChangeProcessor : TickingSystem<ChunkStore>() {
//    override fun tick(dt: Float, systemIndex: Int, store: Store<ChunkStore>) {
//        val queue = store.getResource(GridPlugin.stateChangeQueueType)
//
//        if (queue.pending.isNotEmpty()) println("[StateChangeProcessor] Processing ${queue.pending.size} queued events")
//
//        // Use forEachChunk to get a CommandBuffer, but only process once
//        var processed = false
//        store.forEachChunk(java.util.function.BiConsumer { _, cmdBuf ->
//            if (processed) return@BiConsumer
//            processed = true
//
//            for (event in queue.pending) {
//                println("[StateChangeProcessor] Processing ${event.changeType} at (${event.pos.x}, ${event.pos.y}, ${event.pos.z}), remaining: ${queue.pending.size}")
//                processStateChange(event, store, cmdBuf)
//            }
//            queue.pending.clear()
//        })
//    }
//
//    private fun processStateChange(
//        event: StateChangeEvent,
//        store: Store<ChunkStore>,
//        cmdBuf: CommandBuffer<ChunkStore>
//    ) {
//        val pos = event.pos
//
//        // Get the chunk for this position
//        val chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z)
//        val chunkRef = cmdBuf.externalData.getChunkReference(chunkIndex) ?: return
//        val blockComponentChunk = cmdBuf.getComponent(chunkRef, BlockComponentChunk.getComponentType()) ?: return
//
//        // For DESTROYED events, the block is already gone so it won't appear in BFS.
//        // Add its position to dirty sets so ElectricalNodeControlSystem
//        // re-evaluates any adjacent signal/power-controlled blocks.
//        if (event.changeType == StateChangeType.DESTROYED) {
//            val queue = store.getResource(GridPlugin.stateChangeQueueType)
//            queue.dirtySignalPositions.add(pos)
//        }
//
//        // For DESTROYED events, the block is already gone by the time we process.
//        // We need to start BFS from the neighbors of the destroyed position.
//        val startPositions = if (event.changeType == StateChangeType.DESTROYED) {
//            getAllAdjacent(pos)
//        } else {
//            listOf(pos)
//        }
//
//        // Phase 1: Find all connected sources while resetting power
//        val resetResult = findSourcesAndResetFromMultiple(startPositions, blockComponentChunk, store, cmdBuf)
//        println("[StateChangeProcessor] Found ${resetResult.nonSourceNodes.size} total nodes, and ${resetResult.sources.size} of them were sources")
//
//        // Phase 1b: Also reset signal wires adjacent to any SignalSense blocks found in Phase 1
//        val signalResetStarts = mutableListOf<Vector3i>()
//        for (node in resetResult.nonSourceNodes) {
//            val bi = ChunkUtil.indexBlockInColumn(node.x and 31, node.y, node.z and 31)
//            val br = blockComponentChunk.getEntityReference(bi) ?: continue
//            if (cmdBuf.getComponent(br, GridPlugin.signalSenseComponentType) != null) {
//                for (adj in getAllAdjacent(node)) {
//                    val adjBi = ChunkUtil.indexBlockInColumn(adj.x and 31, adj.y, adj.z and 31)
//                    val adjBr = blockComponentChunk.getEntityReference(adjBi) ?: continue
//                    val adjTransmits = cmdBuf.getComponent(adjBr, GridPlugin.transmitsComponentType)
//                    if (adjTransmits?.transmits?.contains("Signal") == true) {
//                        signalResetStarts.add(adj)
//                    }
//                }
//            }
//        }
//        val signalResetNodes = if (signalResetStarts.isNotEmpty()) {
//            val signalResetResult = findSourcesAndResetFromMultiple(signalResetStarts, blockComponentChunk, store, cmdBuf)
//            println("[StateChangeProcessor] Reset ${signalResetResult.nonSourceNodes.size} signal nodes adjacent to SignalSense blocks")
//            signalResetResult.nonSourceNodes
//        } else emptyList()
//
//        // Phase 2: Propagate power from each source
//        val allVisited = mutableSetOf<Vector3i>()
//        allVisited.addAll(resetResult.nonSourceNodes)
//        allVisited.addAll(signalResetNodes)
//        for (source in resetResult.sources) {
//            val poweredOnBlocks = propagatePowerFromSource(source, blockComponentChunk, store, cmdBuf)
//            allVisited.addAll(poweredOnBlocks)
//            println("[StateChangeProcessor] Power propagated from source at (${source.x}, ${source.y}, ${source.z}) to ${poweredOnBlocks.size} connected nodes.")
//        }
//
//        // Phase 3: Propagate signal across SignalSense ↔ signal wire boundaries
//        // Find all powered SignalSense positions — either in allVisited (power network event)
//        // or adjacent to visited signal wires (signal wire placement event)
//        val signalPropagationStarts = mutableSetOf<Vector3i>()
//        for (visitedPos in allVisited.toList()) {
//            val bi = ChunkUtil.indexBlockInColumn(visitedPos.x and 31, visitedPos.y, visitedPos.z and 31)
//            val br = blockComponentChunk.getEntityReference(bi) ?: continue
//            if (cmdBuf.getComponent(br, GridPlugin.signalSenseComponentType) != null) {
//                // Case 1: Visited position IS a powered SignalSense → propagate to adjacent signal wires
//                val pw = cmdBuf.getComponent(br, GridPlugin.powerableComponentType)
//                if (pw?.powered == true) {
//                    for (adj in getAllAdjacent(visitedPos)) {
//                        val adjBi = ChunkUtil.indexBlockInColumn(adj.x and 31, adj.y, adj.z and 31)
//                        val adjBr = blockComponentChunk.getEntityReference(adjBi) ?: continue
//                        val adjTransmits = cmdBuf.getComponent(adjBr, GridPlugin.transmitsComponentType)
//                        if (adjTransmits?.transmits?.contains("Signal") == true) {
//                            signalPropagationStarts.add(adj)
//                        }
//                    }
//                }
//            } else {
//                // Case 2: Visited position is a signal wire → check adjacents for powered SignalSense
//                val transmits = cmdBuf.getComponent(br, GridPlugin.transmitsComponentType)
//                if (transmits?.transmits?.contains("Signal") == true) {
//                    for (adj in getAllAdjacent(visitedPos)) {
//                        val adjBi = ChunkUtil.indexBlockInColumn(adj.x and 31, adj.y, adj.z and 31)
//                        val adjBr = blockComponentChunk.getEntityReference(adjBi) ?: continue
//                        if (cmdBuf.getComponent(adjBr, GridPlugin.signalSenseComponentType) == null) continue
//                        val adjPw = cmdBuf.getComponent(adjBr, GridPlugin.powerableComponentType)
//                        if (adjPw?.powered == true) {
//                            signalPropagationStarts.add(visitedPos)
//                            break
//                        }
//                    }
//                }
//            }
//        }
//        for (start in signalPropagationStarts) {
//            val signalPowered = propagatePowerFromSource(start, blockComponentChunk, store, cmdBuf)
//            allVisited.addAll(signalPowered)
//            println("[StateChangeProcessor] Signal propagated from SignalSense boundary through ${signalPowered.size} signal nodes starting at (${start.x}, ${start.y}, ${start.z})")
//        }
//
//        // Track visited signal and power wire positions for ElectricalNodeControlSystem
//        val queue = store.getResource(GridPlugin.stateChangeQueueType)
//        for (visitedPos in allVisited) {
//            val blockIndex = ChunkUtil.indexBlockInColumn(visitedPos.x and 31, visitedPos.y, visitedPos.z and 31)
//            val blockRef = blockComponentChunk.getEntityReference(blockIndex) ?: continue
//            val transmits = cmdBuf.getComponent(blockRef, GridPlugin.transmitsComponentType)
//            if (transmits?.transmits?.contains("Signal") ?: false) {
//                queue.dirtySignalPositions.add(visitedPos)
//            }
//            if (cmdBuf.getComponent(blockRef, GridPlugin.signalSenseComponentType) != null) {
//                queue.dirtySignalPositions.add(visitedPos)
//            }
//        }
//
//        // Ensure signal-controlled blocks get evaluated on placement — with Wire(Power), BFS no longer
//        // traverses adjacent signal wires, so we explicitly mark adjacents as dirty
//        if (event.changeType == StateChangeType.PLACED_OR_UPDATED) {
//            val blockIndex = ChunkUtil.indexBlockInColumn(pos.x and 31, pos.y, pos.z and 31)
//            val blockRef = blockComponentChunk.getEntityReference(blockIndex)
//            if (blockRef != null) {
//                if (cmdBuf.getComponent(blockRef, GridPlugin.relayComponentType) != null ||
//                    cmdBuf.getComponent(blockRef, GridPlugin.powerSourceComponentType) != null
//                ) {
//                    for (adj in getAllAdjacent(pos)) {
//                        queue.dirtySignalPositions.add(adj)
//                    }
//                }
//                if (cmdBuf.getComponent(blockRef, GridPlugin.signalSenseComponentType) != null) {
//                    for (adj in getAllAdjacent(pos)) {
//                        queue.dirtySignalPositions.add(adj)
//                    }
//                }
//            }
//        }
//    }
//}
