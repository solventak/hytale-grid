package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin

class Lamp : Component<ChunkStore> {
    /** Derived by systems: lamp is lit if any connected face's net is powered */
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
