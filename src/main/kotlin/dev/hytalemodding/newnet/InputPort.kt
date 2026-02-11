package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin

class InputPort : Component<ChunkStore> {
    /** Face index (0..5) pointing toward the adjacent PowerSource (driver side). */
    var driverSideFace: Int = 0

    companion object {
        @JvmField
        val CODEC: BuilderCodec<InputPort> =
            BuilderCodec.builder(InputPort::class.java) { InputPort() }.build()
    }

    fun getComponentType(): ComponentType<ChunkStore, InputPort> {
        return ExamplePlugin.inputPortComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return InputPort().also { it.driverSideFace = this.driverSideFace }
    }
}
