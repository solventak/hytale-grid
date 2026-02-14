package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.GridPlugin

/**
 * Marker component indicating a block is a power wire.
 * 
 * Wires have special connectivity behavior in topology flood fill:
 * - All 6 connectable faces are star-connected (belong to the same network)
 * - Wires bridge multiple faces, allowing power to flow through the block
 * - Wires only connect to other wires with the same channel (colored wire isolation)
 * 
 * During visual updates, wires are automatically swapped to different model variants
 * (straight, corner, T-junction, etc.) based on which neighbors are connectable.
 * See WireLookupTable for the 24 canonical wire variants.
 * 
 * Visual state:
 * - "On" when any connected network has value ONE
 * - "default" otherwise
 * 
 * @property channel Wire channel identifier (1=default, 2=ch_2, 3=ch_3, 4=ch_4)
 */
class PowerWire(
    var channel: Int = 1
) : Component<ChunkStore> {
    companion object {
        @JvmField
        val CODEC: BuilderCodec<PowerWire> =
            BuilderCodec.builder(PowerWire::class.java) { PowerWire() }
                .appendInherited(
                    KeyedCodec("Channel", Codec.INTEGER),
                    { obj, value -> obj.channel = value },
                    { obj -> obj.channel },
                    { obj, parent -> obj.channel = parent.channel }
                )
                .add()
                .build()
    }

    fun getComponentType(): ComponentType<ChunkStore, PowerWire> {
        return GridPlugin.powerWireComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return PowerWire().also {
            it.channel = this.channel
        }
    }
}
