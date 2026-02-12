package dev.hytalemodding.newnet

import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import sun.misc.Unsafe

/**
 * Initializes ExamplePlugin's companion object component type fields with
 * fresh ComponentType instances for testing purposes.
 *
 * Uses Unsafe to set fields on the companion object without triggering
 * Kotlin's lateinit property accessors or class initialization side effects.
 */
object TestComponentTypes {
    // Component types for test use — avoids loading ExamplePlugin (which needs Hytale runtime)
    val powerConnectableComponentType = ComponentType<ChunkStore, PowerConnectable>()
    val powerNetIdsComponentType = ComponentType<ChunkStore, PowerNetIds>()
    val powerSourceComponentType = ComponentType<ChunkStore, PowerSource>()
    val powerWireComponentType = ComponentType<ChunkStore, PowerWire>()
    val lampComponentType = ComponentType<ChunkStore, Lamp>()
    val inputPortComponentType = ComponentType<ChunkStore, InputPort>()
    val relayComponentType = ComponentType<ChunkStore, Relay>()
    val visualStateComponentType = ComponentType<ChunkStore, VisualState>()
    val mux2PartComponentType = ComponentType<ChunkStore, Mux2Part>()

    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        initialized = true

        // Set HytaleLogManager before any Hytale class loading to avoid HytaleLogger init failure
        System.setProperty("java.util.logging.manager", "com.hypixel.hytale.logger.HytaleLogManager")

        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").let {
            it.isAccessible = true
            it.get(null) as Unsafe
        }

        // Load ExamplePlugin WITHOUT triggering static initialization
        val pluginClass = Class.forName("dev.hytalemodding.ExamplePlugin", false, Thread.currentThread().contextClassLoader)
        val companionClass = Class.forName("dev.hytalemodding.ExamplePlugin\$Companion", false, Thread.currentThread().contextClassLoader)

        fun setStaticField(name: String, value: Any) {
            val field = pluginClass.getDeclaredField(name)
            val offset = unsafe.staticFieldOffset(field)
            unsafe.putObject(pluginClass, offset, value)
        }

        // Set the Companion instance
        val companionField = pluginClass.getDeclaredField("Companion")
        val companionOffset = unsafe.staticFieldOffset(companionField)
        if (unsafe.getObject(pluginClass, companionOffset) == null) {
            unsafe.putObject(pluginClass, companionOffset, unsafe.allocateInstance(companionClass))
        }

        // Point ExamplePlugin's static fields to our test instances
        setStaticField("powerConnectableComponentType", powerConnectableComponentType)
        setStaticField("powerNetIdsComponentType", powerNetIdsComponentType)
        setStaticField("powerSourceComponentType", powerSourceComponentType)
        setStaticField("powerWireComponentType", powerWireComponentType)
        setStaticField("lampComponentType", lampComponentType)
        setStaticField("inputPortComponentType", inputPortComponentType)
        setStaticField("relayComponentType", relayComponentType)
        setStaticField("visualStateComponentType", visualStateComponentType)
        setStaticField("mux2PartComponentType", mux2PartComponentType)
        setStaticField("stateChangeQueueType", ResourceType<ChunkStore, StateChangeEventQueue>())
    }
}
