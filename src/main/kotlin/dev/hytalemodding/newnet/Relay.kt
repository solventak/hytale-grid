package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin

class Relay : Component<ChunkStore> {
    var enabled: Boolean = false
    var controlFault: Boolean = false
    var lastEnabled: Boolean = false

    companion object {
        @JvmField
        val CODEC: BuilderCodec<Relay> =
            BuilderCodec.builder(Relay::class.java) { Relay() }.build()
    }

    fun getComponentType(): ComponentType<ChunkStore, Relay> {
        return ExamplePlugin.relayComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return Relay().also {
            it.enabled = this.enabled
            it.controlFault = this.controlFault
            it.lastEnabled = this.lastEnabled
        }
    }
}
