package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin

/**
 * Component for power-consuming lamp blocks.
 * 
 * Lamps passively receive power from connected networks. They do not alter
 * topology or drive signals — they only visualize network state.
 * 
 * A lamp is lit if ANY of its 6 connectable faces belongs to a network
 * with value ONE. If all faces are ZERO/HIGH_Z, the lamp is off.
 * 
 * Visual state:
 * - "On" when lit=true
 * - "default" when lit=false
 * 
 * Lamps are useful for debugging network state and creating visible indicators.
 */
class Lamp : Component<ChunkStore> {
    /**
     * Derived state: true if any connected network is powered (value ONE).
     * Computed by TopologySystem.updateLamps() each tick.
     */
    var lit: Boolean = false

    companion object {
        @JvmField
        val CODEC: BuilderCodec<Lamp> =
            BuilderCodec.builder(Lamp::class.java) { Lamp() }.build()
    }

    fun getComponentType(): ComponentType<ChunkStore, Lamp> {
        return ExamplePlugin.lampComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return Lamp().also { it.lit = this.lit }
    }
}
