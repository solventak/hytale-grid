package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin

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
