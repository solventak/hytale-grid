package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin

/**
 * Component marking a block as a manually-toggleable power source (lever).
 * 
 * Levers are interactive blocks that players can right-click to toggle between
 * ON and OFF states. Unlike PowerSource (which is an inverting multi-input NOR gate),
 * levers are simple binary switches controlled directly by player interaction.
 * 
 * A lever block has both:
 * - This Lever component (stores UI toggle state)
 * - PowerSource component (drives the network with ZERO or ONE)
 * 
 * Behavior:
 * - OFF state: PowerSource.driveState = ZERO (no signal)
 * - ON state: PowerSource.driveState = ONE (strong signal)
 * - Right-click interaction toggles between states
 * - State persists through world save/load
 * - Drops as item when broken (renewable resource)
 * 
 * Visual:
 * - Uses VisualState component for appearance switching
 * - "default" interaction state when OFF (gray texture)
 * - "On" interaction state when ON (white texture)
 */
class Lever : Component<ChunkStore> {
    /**
     * Current toggle state of the lever.
     * true = ON (outputs ONE), false = OFF (outputs ZERO)
     */
    var isOn: Boolean = false

    companion object {
        @JvmField
        val CODEC: BuilderCodec<Lever> = BuilderCodec.builder(Lever::class.java) { Lever() }
            .appendInherited(
                KeyedCodec("IsOn", Codec.BOOLEAN),
                { obj, value -> obj.isOn = value },
                { obj -> obj.isOn },
                { obj, parent -> obj.isOn = parent.isOn }
            )
            .add()
            .build()
    }

    fun getComponentType(): ComponentType<ChunkStore, Lever> {
        return ExamplePlugin.leverComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return Lever().also {
            it.isOn = this.isOn
        }
    }
}
