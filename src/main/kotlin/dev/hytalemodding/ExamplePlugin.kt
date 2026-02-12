package dev.hytalemodding

import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.newnet.*
import dev.hytalemodding.newnet.InputPort
import dev.hytalemodding.newnet.PowerSource
import dev.hytalemodding.newnet.StateChangeEventQueue
import java.util.logging.Level

/**
 * Main plugin class for the Hytale modding project.
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
 * - **VisualState**: Generic visual state component
 * 
 * ## Core Systems
 * - **TopologySystem**: Manages network topology, evaluation, and visual updates
 * - **VisualStateSystem**: Applies visual state changes and wire shape updates
 * - **PowerBlockAddedSystem**: Handles block placement events
 * - **PowerBlockBreakEvent**: Handles block destruction events
 * 
 * All components and systems are registered in setup().
 */
class ExamplePlugin(init: JavaPluginInit) : JavaPlugin(init) {

    lateinit var reactiveChunkComponentType: ComponentType<ChunkStore, ReactiveChunk>

    companion object {
        // Component type references stored in companion object for global access
//        lateinit var lampComponentType: ComponentType<ChunkStore, Lamp>
//        lateinit var sourceComponentType: ComponentType<ChunkStore, Source>
//        lateinit var transportComponentType: ComponentType<ChunkStore, Transport>
//        lateinit var sinkComponentType: ComponentType<ChunkStore, Sink>
//        lateinit var poweredComponentType: ComponentType<ChunkStore, Powerable>
//        lateinit var powerSourceComponentType: ComponentType<ChunkStore, PowerSource>
//        lateinit var transmitsComponentType: ComponentType<ChunkStore, Transmits>
//        lateinit var electricalNodeComponentType: ComponentType<ChunkStore, ElectricalNode>
        lateinit var stateChangeQueueType: ResourceType<ChunkStore, StateChangeEventQueue>
//        lateinit var relayComponentType: ComponentType<ChunkStore, Relay>
//        lateinit var signalSenseComponentType: ComponentType<ChunkStore, SignalSense>
//        lateinit var powerableComponentType: ComponentType<ChunkStore, Powerable>
//        lateinit var wireComponentType: ComponentType<ChunkStore, Wire>
        lateinit var powerConnectableComponentType: ComponentType<ChunkStore, PowerConnectable>
        lateinit var powerNetIdsComponentType: ComponentType<ChunkStore, PowerNetIds>
        lateinit var powerSourceComponentType: ComponentType<ChunkStore, PowerSource>
        lateinit var powerWireComponentType: ComponentType<ChunkStore, PowerWire>
        lateinit var lampComponentType: ComponentType<ChunkStore, Lamp>
        lateinit var inputPortComponentType: ComponentType<ChunkStore, InputPort>
        lateinit var relayComponentType: ComponentType<ChunkStore, Relay>
        lateinit var visualStateComponentType: ComponentType<ChunkStore, VisualState>
        lateinit var mux2PartComponentType: ComponentType<ChunkStore, Mux2Part>
    }

    override fun setup() {
        logger.at(Level.INFO).log("[ExamplePlugin] setup() called - beginning initialization")

//        this.reactiveChunkComponentType = this.chunkStoreRegistry.registerComponent(ReactiveChunk::class.java, "ReactiveChunk", ReactiveChunk.CODEC)
//        lampComponentType = this.chunkStoreRegistry.registerComponent(Lamp::class.java, "Lamp", Lamp.CODEC)
//        logger.at(Level.INFO).log("[ExamplePlugin] ReactiveChunk component registered")

//        this.chunkStoreRegistry.registerSystem(ReactiveBlockInteractSystem(this.reactiveChunkComponentType))
        powerConnectableComponentType = this.chunkStoreRegistry.registerComponent(PowerConnectable::class.java, "PowerConnectable", PowerConnectable.CODEC)
        logger.at(Level.INFO).log("[ExamplePlugin] PowerConnectable registered")
        powerNetIdsComponentType = this.chunkStoreRegistry.registerComponent(PowerNetIds::class.java, "PowerNetIds", PowerNetIds.CODEC)
        logger.at(Level.INFO).log("[ExamplePlugin] PowerNetIds registered")
        powerSourceComponentType = this.chunkStoreRegistry.registerComponent(PowerSource::class.java, "PowerSource", PowerSource.CODEC)
        logger.at(Level.INFO).log("[ExamplePlugin] PowerSource registered")
        powerWireComponentType = this.chunkStoreRegistry.registerComponent(PowerWire::class.java, "PowerWire", PowerWire.CODEC)
        logger.at(Level.INFO).log("[ExamplePlugin] PowerWire registered")
        lampComponentType = this.chunkStoreRegistry.registerComponent(Lamp::class.java, "Lamp", Lamp.CODEC)
        logger.at(Level.INFO).log("[ExamplePlugin] Lamp registered")
        inputPortComponentType = this.chunkStoreRegistry.registerComponent(InputPort::class.java, "InputPort", InputPort.CODEC)
        logger.at(Level.INFO).log("[ExamplePlugin] InputPort registered")
        relayComponentType = this.chunkStoreRegistry.registerComponent(Relay::class.java, "Relay", Relay.CODEC)
        logger.at(Level.INFO).log("[ExamplePlugin] Relay registered")
        visualStateComponentType = this.chunkStoreRegistry.registerComponent(VisualState::class.java, "VisualState", VisualState.CODEC)
        logger.at(Level.INFO).log("[ExamplePlugin] VisualState registered")
        mux2PartComponentType = this.chunkStoreRegistry.registerComponent(Mux2Part::class.java, "Mux2Part", Mux2Part.CODEC)
        logger.at(Level.INFO).log("[ExamplePlugin] Mux2Part registered")

        stateChangeQueueType = this.chunkStoreRegistry.registerResource(StateChangeEventQueue::class.java, "StateChangeEventQueue",
            StateChangeEventQueue.CODEC)
        logger.at(Level.INFO).log("[ExamplePlugin] StateChangeEventQueue resource registered")

        // Register event systems for block place/break
        this.chunkStoreRegistry.registerSystem(PowerBlockAddedSystem())
        logger.at(Level.INFO).log("[ExamplePlugin] PowerBlockAddedSystem registered")
        this.entityStoreRegistry.registerSystem(PowerBlockBreakEvent())
        logger.at(Level.INFO).log("[ExamplePlugin] PowerBlockBreakEvent registered")

        // Register topology system
        this.chunkStoreRegistry.registerSystem(TopologySystem())
        logger.at(Level.INFO).log("[ExamplePlugin] TopologySystem registered")

        // Register visual state system (runs after TopologySystem)
        this.chunkStoreRegistry.registerSystem(VisualStateSystem())
        logger.at(Level.INFO).log("[ExamplePlugin] VisualStateSystem registered")

//        this.chunkStoreRegistry.registerSystem(LampInitializer(lampComponentType))
//        logger.at(Level.INFO).log("[ExamplePlugin] LampInitializer registered")

//        this.chunkStoreRegistry.registerSystem(LampSystem(lampComponentType))
//        logger.at(Level.INFO).log("[ExamplePlugin] LampSystem registered")

        // Register all components first
//        sourceComponentType = this.chunkStoreRegistry.registerComponent(Source::class.java, "Source", Source.CODEC)
//        logger.at(Level.INFO).log("[ExamplePlugin] SourceComponentType registered")
//        transportComponentType = this.chunkStoreRegistry.registerComponent(Transport::class.java, "Transport", Transport.CODEC)
//        logger.at(Level.INFO).log("[ExamplePlugin] TransportComponentType registered")
//        sinkComponentType = this.chunkStoreRegistry.registerComponent(Sink::class.java, "Sink", Sink.CODEC)
//        logger.at(Level.INFO).log("[ExamplePlugin] SinkComponentType registered")
//        poweredComponentType = this.chunkStoreRegistry.registerComponent(Powerable::class.java, "Powerable", Powerable.CODEC)
//        logger.at(Level.INFO).log("[ExamplePlugin] Powerable registered")
//        wireComponentType = this.chunkStoreRegistry.registerComponent(Wire::class.java, "Wire", Wire.CODEC)
//        logger.at(Level.INFO).log("[ExamplePlugin] Wire registered")
//        powerSourceComponentType = this.chunkStoreRegistry.registerComponent(PowerSource::class.java, "PowerSource", PowerSource.CODEC)
//        logger.at(Level.INFO).log("[ExamplePlugin] PowerSource registered")
//        electricalNodeComponentType = this.chunkStoreRegistry.registerComponent(ElectricalNode::class.java, "ElectricalNode", ElectricalNode.CODEC)
//        logger.at(Level.INFO).log("[ExamplePlugin] ElectricalNode registered")
//        transmitsComponentType = this.chunkStoreRegistry.registerComponent(Transmits::class.java, "Transmits", Transmits.CODEC)
//        logger.at(Level.INFO).log("[ExamplePlugin] Transmits registered")
//        relayComponentType = this.chunkStoreRegistry.registerComponent(Relay::class.java, "Relay", Relay.CODEC)
//        logger.at(Level.INFO).log("[ExamplePlugin] Relay registered")
//        signalSenseComponentType = this.chunkStoreRegistry.registerComponent(SignalSense::class.java, "SignalSense", SignalSense.CODEC)
//        logger.at(Level.INFO).log("[ExamplePlugin] SignalSense registered")
//        powerableComponentType = this.chunkStoreRegistry.registerComponent(Powerable::class.java, "Powerable", Powerable.CODEC)
//        logger.at(Level.INFO).log("[ExamplePlugin] Powerable registered")
//
//        // Register state change queue resource
//        stateChangeQueueType = this.chunkStoreRegistry.registerResource(
//            StateChangeEventQueue::class.java,
//            "StateChangeEventQueue",
//            StateChangeEventQueue.CODEC
//        )
//        logger.at(Level.INFO).log("[ExamplePlugin] StateChangeEventQueue resource registered")
//
//        // Register state change event systems
//        this.entityStoreRegistry.registerSystem(UseBlockStateChangeEvent())
//        logger.at(Level.INFO).log("[ExamplePlugin] UseBlockStateChangeEvent registered")
//        this.chunkStoreRegistry.registerSystem(PowerableBlockAddedSystem())
//        logger.at(Level.INFO).log("[ExamplePlugin] PowerableBlockAddedSystem registered")
//        this.entityStoreRegistry.registerSystem(BreakBlockStateChangeEvent())
//        logger.at(Level.INFO).log("[ExamplePlugin] BreakBlockStateChangeEvent registered")
//
//        // Register state change processor (ChunkStore ticking system)
//        this.chunkStoreRegistry.registerSystem(StateChangeProcessor())
//        logger.at(Level.INFO).log("[ExamplePlugin] StateChangeProcessor registered")
//
//        // Register electrical node control system (evaluates signal-controlled blocks after power propagation)
//        this.chunkStoreRegistry.registerSystem(ElectricalNodeControlSystem())
//        logger.at(Level.INFO).log("[ExamplePlugin] ElectricalNodeControlSystem registered")

        // Register power change render system (handles visual updates when power changes)
//        this.chunkStoreRegistry.registerSystem(PowerChangeRenderSystem())
//        logger.at(Level.INFO).log("[ExamplePlugin] PowerChangeRenderSystem registered")

        // Register SinkReset BEFORE SourceSystem (SourceSystem depends on SinkReset)
//        this.chunkStoreRegistry.registerSystem(SinkReset(sinkComponentType))
//        logger.at(Level.INFO).log("[ExamplePlugin] SinkReset system registered")
//        this.chunkStoreRegistry.registerSystem(SinkInitializer(sinkComponentType))
//        logger.at(Level.INFO).log("[ExamplePlugin] SinkInitializer system registered")

        // Now register SourceSystem (depends on SinkReset which is already registered)
//        this.chunkStoreRegistry.registerSystem(SourceInitializer(sourceComponentType))
//        logger.at(Level.INFO).log("[ExamplePlugin] SourceInitializer registered")
//        this.chunkStoreRegistry.registerSystem(SourceSystem(sourceComponentType))
//        logger.at(Level.INFO).log("[ExamplePlugin] SourceSystem registered")

        // Wire connection visual systems
//        this.chunkStoreRegistry.registerSystem(WireBlockAddedSystem())
//        logger.at(Level.INFO).log("[ExamplePlugin] WireBlockAddedSystem registered")
//        this.entityStoreRegistry.registerSystem(WireBreakEventSystem())
//        logger.at(Level.INFO).log("[ExamplePlugin] WireBreakEventSystem registered")
//        this.chunkStoreRegistry.registerSystem(WireUpdateSystem())
//        logger.at(Level.INFO).log("[ExamplePlugin] WireUpdateSystem registered")
//
//        logger.at(Level.INFO).log("[ExamplePlugin] setup() complete - all systems initialized")
//
//        logger.at(Level.INFO).log("[GliderPlugin] Initializing glider systems...")
//
//        // Register GliderComponent
//        val gliderComponentType = entityStoreRegistry.registerComponent(
//            GliderComponent::class.java
//        ) { GliderComponent() }
//
//        // Make component type accessible to the component class
//        GliderComponent.componentType = gliderComponentType
//        logger.at(Level.INFO).log("[GliderPlugin] GliderComponent registered")
//
//        // Register systems
//        // PlayerGliderInitSystem adds GliderComponent to players who don't have it
//        entityStoreRegistry.registerSystem(dev.hytalemodding.glider.PlayerGliderInitSystem())
//        logger.at(Level.INFO).log("[GliderPlugin] PlayerGliderInitSystem registered")
//
//        // AirborneDetectionSystem updates gliding state based on airborne status
//        entityStoreRegistry.registerSystem(AirborneDetectionSystem())
//        logger.at(Level.INFO).log("[GliderPlugin] AirborneDetectionSystem registered")
//
//        // GliderControlSystem implements IVelocityModifyingSystem and applies glider physics
//        entityStoreRegistry.registerSystem(GliderControlSystem())
//        logger.at(Level.INFO).log("[GliderPlugin] GliderControlSystem registered")
//
//        logger.at(Level.INFO).log("[GliderPlugin] All systems registered! Glider will auto-activate when airborne.")
    }
}
