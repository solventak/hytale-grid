package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin

/**
 * Marker component indicating a block is a power wire.
 * 
 * Wires have special connectivity behavior in topology flood fill:
 * - All 6 connectable faces are star-connected (belong to the same network)
 * - Wires bridge multiple faces, allowing power to flow through the block
 * 
 * During visual updates, wires are automatically swapped to different model variants
 * (straight, corner, T-junction, etc.) based on which neighbors are connectable.
 * See WireLookupTable for the 24 canonical wire variants.
 * 
 * Visual state:
 * - "On" when any connected network has value ONE
 * - "default" otherwise
 */
class PowerWire : Component<ChunkStore> {
    companion object {
        @JvmField
        val CODEC: BuilderCodec<PowerWire> =
            BuilderCodec.builder(PowerWire::class.java) { PowerWire() }.build()
    }

    fun getComponentType(): ComponentType<ChunkStore, PowerWire> {
        return ExamplePlugin.powerWireComponentType
    }

    override fun clone(): Component<ChunkStore> = PowerWire()
}
