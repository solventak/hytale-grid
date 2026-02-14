//package dev.hytalemodding.wire
//
//import com.hypixel.hytale.codec.Codec
//import com.hypixel.hytale.codec.KeyedCodec
//import com.hypixel.hytale.codec.builder.BuilderCodec
//import com.hypixel.hytale.component.ArchetypeChunk
//import com.hypixel.hytale.component.CommandBuffer
//import com.hypixel.hytale.component.Component
//import com.hypixel.hytale.component.Store
//import com.hypixel.hytale.component.query.Query
//import com.hypixel.hytale.component.system.EntityEventSystem
//import com.hypixel.hytale.math.util.ChunkUtil
//import com.hypixel.hytale.server.core.entity.entities.Player
//import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent
//import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
//import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
//import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
//import dev.hytalemodding.*
//
//interface SignalControlled {
//    fun shouldConduct(signalPresent: Boolean): Boolean
//}
//
//class Relay : Component<ChunkStore>, SignalControlled {
//    var normallyClosed = false
//
//    companion object {
//        @JvmField
//        val CODEC: BuilderCodec<Relay> = BuilderCodec.builder(Relay::class.java) { Relay() }
//            .appendInherited(
//                KeyedCodec("NormallyClosed", Codec.BOOLEAN),
//                { obj, value -> obj.normallyClosed = value },
//                { obj -> obj.normallyClosed },
//                { obj, parent -> obj.normallyClosed = parent.normallyClosed }
//            )
//            .add()
//            .build()
//    }
//
//    override fun shouldConduct(signalPresent: Boolean): Boolean {
//        return if (normallyClosed) !signalPresent else signalPresent
//    }
//
//    override fun clone(): Component<ChunkStore> {
//        return Relay().also {
//            it.normallyClosed = this.normallyClosed
//        }
//    }
//}
//
//class SignalSense : Component<ChunkStore> {
//    companion object {
//        @JvmField
//        val CODEC: BuilderCodec<SignalSense> = BuilderCodec.builder(SignalSense::class.java) { SignalSense() }.build()
//    }
//    override fun clone(): Component<ChunkStore> = SignalSense()
//}
//
//class UseBlockStateChangeEvent : EntityEventSystem<EntityStore, UseBlockEvent.Post>(UseBlockEvent.Post::class.java) {
//    override fun handle(
//        index: Int,
//        chunk: ArchetypeChunk<EntityStore>,
//        store: Store<EntityStore>,
//        cmdBuf: CommandBuffer<EntityStore>,
//        event: UseBlockEvent.Post
//    ) {
//        val world = cmdBuf.externalData.world
//        val pos = event.targetBlock
//
//        println("[UseBlockStateChangeEvent] Interacted at ${pos}")
//
//        val relay = getComponentForGlobalXyz(world, pos, GridPlugin.relayComponentType)
//
//        if (relay != null) {
//            val transmits = getComponentForGlobalXyz(world, pos, GridPlugin.transmitsComponentType) ?: return
//
//            relay.normallyClosed = !relay.normallyClosed
//            println("[UseBlockStateChangeEvent] Toggled relay normallyClosed to ${relay.normallyClosed}")
//
//            val signalPresent = hasAdjacentPoweredSignal(world, pos)
//            val shouldConduct = relay.shouldConduct(signalPresent)
//
//            // Always update transmits to match new state (don't rely on old transmits being correct)
//            transmits.transmits.clear()
//            if (shouldConduct) {
//                transmits.transmits.addAll(listOf("Signal", "Power"))
//            } else {
//                transmits.transmits.add("Signal")
//            }
//
//            // Set visual based on conducting state — relay visual reflects conducting, not powered
//             val chunkStore = world.chunkStore
//            val chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z)
//            val chunkRef = chunkStore.getChunkReference(chunkIndex)
//            if (chunkRef != null) {
//                val worldChunk = chunkStore.store.getComponent(chunkRef, WorldChunk.getComponentType())
//                if (worldChunk != null) {
//                    val blockType = worldChunk.getBlockType(pos)
//                    if (blockType != null) {
//                        worldChunk.setBlockInteractionState(pos, blockType, if (shouldConduct) "On" else "default")
//                    }
//                }
//            }
//
//            val queue = world.chunkStore.store.getResource(GridPlugin.stateChangeQueueType)
//            if (shouldConduct) {
//                queue.pending.add(StateChangeEvent(pos, StateChangeType.PLACED_OR_UPDATED))
//            } else {
//                for (adj in getAllAdjacent(pos)) {
//                    queue.pending.add(StateChangeEvent(adj, StateChangeType.PLACED_OR_UPDATED))
//                }
//            }
//            println("[UseBlockStateChangeEvent] Relay state changed, queued event")
//        } else {
//            return
//        }
//    }
//
//    override fun getQuery(): Query<EntityStore> = Query.and(Player.getComponentType())
//}
