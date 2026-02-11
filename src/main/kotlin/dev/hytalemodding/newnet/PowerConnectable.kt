package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin
import dev.hytalemodding.newnet.shared.FaceMask

class PowerConnectable : Component<ChunkStore> {
    /** 6-bit mask, default all faces connectable */
    var facesMask: Int = FaceMask.ALL

    companion object {
        @JvmField
        val CODEC: BuilderCodec<PowerConnectable> =
            BuilderCodec.builder(PowerConnectable::class.java) { PowerConnectable() }
                // If you later add an int codec, append it here; for now, defaults are fine.
                .build()
    }

    fun getComponentType(): ComponentType<ChunkStore, PowerConnectable> {
        return ExamplePlugin.powerConnectableComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return PowerConnectable().also { it.facesMask = this.facesMask }
    }
}
