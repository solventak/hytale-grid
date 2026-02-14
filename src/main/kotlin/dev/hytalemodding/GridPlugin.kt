package dev.hytalemodding

import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.newnet.*
import java.util.logging.Level

/**
 * Main plugin class for the Grid modding project.
 * 
 * Implements a 4-state logic power network system with:
 * - Per-face network assignment (each block face can belong to different networks)
 * - Multi-driver resolution (multiple sources can drive the same net)
 * - Delta-cycle evaluation (iterative stabilization)
 * - Relay-controlled topology (relays alter network connectivity)
 * - Conflict detection (UNKNOWN_X state destroys blocks)
 * 
 * ## Core Components
 * - **PowerConnectable**: Declares which faces can connect to networks
 * - **PowerNetIds**: Stores network ID for each face (6 IDs per block)
 * - **PowerSource**: Inverting driver blocks (multi-input NOR gates)
 * - **PowerWire**: Wire blocks that bridge all faces
 * - **Lamp**: Power consumer blocks (light up when powered)
 * - **Relay**: Controlled switches (alter topology based on control signals)
 * - **InputPort**: Network probe blocks (read net state for relay/source control)
 * - **Lever**: Interactive toggle blocks (player-controlled power sources)
 * - **VisualState**: Generic visual state component
 * - **Mux2Part**: 2-to-1 multiplexer component
 * 
 * ## Core Systems
 * - **TopologySystem**: Manages network topology, evaluation, and visual updates
 * - **VisualStateSystem**: Applies visual state changes and wire shape updates
 * - **PowerBlockAddedSystem**: Handles block placement events
 * - **PowerBlockBreakEvent**: Handles block destruction events
 * 
 * All components and systems are registered in setup().
 */
class GridPlugin(init: JavaPluginInit) : JavaPlugin(init) {

    companion object {
        // Component type references stored in companion object for global access
        lateinit var stateChangeQueueType: ResourceType<ChunkStore, StateChangeEventQueue>
        lateinit var powerConnectableComponentType: ComponentType<ChunkStore, PowerConnectable>
        lateinit var powerNetIdsComponentType: ComponentType<ChunkStore, PowerNetIds>
        lateinit var powerSourceComponentType: ComponentType<ChunkStore, PowerSource>
        lateinit var powerWireComponentType: ComponentType<ChunkStore, PowerWire>
        lateinit var lampComponentType: ComponentType<ChunkStore, Lamp>
        lateinit var inputPortComponentType: ComponentType<ChunkStore, InputPort>
        lateinit var relayComponentType: ComponentType<ChunkStore, Relay>
        lateinit var visualStateComponentType: ComponentType<ChunkStore, VisualState>
        lateinit var mux2PartComponentType: ComponentType<ChunkStore, Mux2Part>
        lateinit var leverComponentType: ComponentType<ChunkStore, Lever>
    }

    override fun setup() {
        logger.at(Level.INFO).log("[GridPlugin] setup() called - beginning initialization")

//        this.reactiveChunkComponentType = this.chunkStoreRegistry.registerComponent(ReactiveChunk::class.java, "ReactiveChunk", ReactiveChunk.CODEC)
//        lampComponentType = this.chunkStoreRegistry.registerComponent(Lamp::class.java, "Lamp", Lamp.CODEC)
//        logger.at(Level.INFO).log("[GridPlugin] ReactiveChunk component registered")

//        this.chunkStoreRegistry.registerSystem(ReactiveBlockInteractSystem(this.reactiveChunkComponentType))
        powerConnectableComponentType = this.chunkStoreRegistry.registerComponent(PowerConnectable::class.java, "PowerConnectable", PowerConnectable.CODEC)
        logger.at(Level.INFO).log("[GridPlugin] PowerConnectable registered")
        powerNetIdsComponentType = this.chunkStoreRegistry.registerComponent(PowerNetIds::class.java, "PowerNetIds", PowerNetIds.CODEC)
        logger.at(Level.INFO).log("[GridPlugin] PowerNetIds registered")
        powerSourceComponentType = this.chunkStoreRegistry.registerComponent(PowerSource::class.java, "PowerSource", PowerSource.CODEC)
        logger.at(Level.INFO).log("[GridPlugin] PowerSource registered")
        powerWireComponentType = this.chunkStoreRegistry.registerComponent(PowerWire::class.java, "PowerWire", PowerWire.CODEC)
        logger.at(Level.INFO).log("[GridPlugin] PowerWire registered")
        lampComponentType = this.chunkStoreRegistry.registerComponent(Lamp::class.java, "Lamp", Lamp.CODEC)
        logger.at(Level.INFO).log("[GridPlugin] Lamp registered")
        inputPortComponentType = this.chunkStoreRegistry.registerComponent(InputPort::class.java, "InputPort", InputPort.CODEC)
        logger.at(Level.INFO).log("[GridPlugin] InputPort registered")
        relayComponentType = this.chunkStoreRegistry.registerComponent(Relay::class.java, "Relay", Relay.CODEC)
        logger.at(Level.INFO).log("[GridPlugin] Relay registered")
        visualStateComponentType = this.chunkStoreRegistry.registerComponent(VisualState::class.java, "VisualState", VisualState.CODEC)
        logger.at(Level.INFO).log("[GridPlugin] VisualState registered")
        mux2PartComponentType = this.chunkStoreRegistry.registerComponent(Mux2Part::class.java, "Mux2Part", Mux2Part.CODEC)
        logger.at(Level.INFO).log("[GridPlugin] Mux2Part registered")
        leverComponentType = this.chunkStoreRegistry.registerComponent(Lever::class.java, "Lever", Lever.CODEC)
        logger.at(Level.INFO).log("[GridPlugin] Lever registered")

        stateChangeQueueType = this.chunkStoreRegistry.registerResource(StateChangeEventQueue::class.java, "StateChangeEventQueue",
            StateChangeEventQueue.CODEC)
        logger.at(Level.INFO).log("[GridPlugin] StateChangeEventQueue resource registered")

        // Register event systems for block place/break
        this.chunkStoreRegistry.registerSystem(PowerBlockAddedSystem())
        logger.at(Level.INFO).log("[Grid] PowerBlockAddedSystem registered")

        this.entityStoreRegistry.registerSystem(PowerBlockBreakEvent())
        logger.at(Level.INFO).log("[GridPlugin] PowerBlockBreakEvent registered")
        
        // Register lever interaction system
        this.entityStoreRegistry.registerSystem(LeverInteractionSystem())
        logger.at(Level.INFO).log("[GridPlugin] LeverInteractionSystem registered")

        // Register topology system
        this.chunkStoreRegistry.registerSystem(TopologySystem())
        logger.at(Level.INFO).log("[Grid] TopologySystem registered")

        // Register visual state system (runs after TopologySystem)
        this.chunkStoreRegistry.registerSystem(VisualStateSystem())
        logger.at(Level.INFO).log("[Grid] VisualStateSystem registered")

        logger.at(Level.INFO).log("[Grid] setup() complete - all systems initialized")
    }
}
