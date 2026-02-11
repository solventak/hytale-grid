package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin

class VisualState : Component<ChunkStore> {
    /** Interaction state name applied by VisualStateSystem. Common values: "default", "On" */
    var state: String = "default"

    companion object {
        @JvmField
        val CODEC: BuilderCodec<VisualState> =
            BuilderCodec.builder(VisualState::class.java) { VisualState() }.build()
    }

    fun getComponentType(): ComponentType<ChunkStore, VisualState> {
        return ExamplePlugin.visualStateComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return VisualState().also { it.state = this.state }
    }
}
