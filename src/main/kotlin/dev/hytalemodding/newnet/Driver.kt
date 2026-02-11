package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin
import dev.hytalemodding.newnet.shared.State4

class PowerSource : Component<ChunkStore> {
    var driveState: State4 = State4.ONE
    var lastDriveState: State4 = State4.ONE

    companion object {
        @JvmField
        val CODEC: BuilderCodec<PowerSource> =
            BuilderCodec.builder(PowerSource::class.java) { PowerSource() }.build()
    }

    fun getComponentType(): ComponentType<ChunkStore, PowerSource> {
        return ExamplePlugin.powerSourceComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return PowerSource().also {
            it.driveState = this.driveState
            it.lastDriveState = this.lastDriveState
        }
    }
}
