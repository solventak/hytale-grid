package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin

class PowerNetIds : Component<ChunkStore> {
    /** NetId per face index 0..5 (DOWN,UP,NORTH,SOUTH,WEST,EAST). -1 = unassigned */
    var downNetId: Int = -1
    var upNetId: Int = -1
    var northNetId: Int = -1
    var southNetId: Int = -1
    var westNetId: Int = -1
    var eastNetId: Int = -1

    fun get(faceIndex: Int): Int = when (faceIndex) {
        0 -> downNetId
        1 -> upNetId
        2 -> northNetId
        3 -> southNetId
        4 -> westNetId
        5 -> eastNetId
        else -> -1
    }

    fun set(faceIndex: Int, value: Int) {
        when (faceIndex) {
            0 -> downNetId = value
            1 -> upNetId = value
            2 -> northNetId = value
            3 -> southNetId = value
            4 -> westNetId = value
            5 -> eastNetId = value
        }
    }

    companion object {
        @JvmField
        val CODEC: BuilderCodec<PowerNetIds> =
            BuilderCodec.builder(PowerNetIds::class.java) { PowerNetIds() }.build()
    }

    fun getComponentType(): ComponentType<ChunkStore, PowerNetIds> {
        return ExamplePlugin.powerNetIdsComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return PowerNetIds().also {
            it.downNetId = this.downNetId
            it.upNetId = this.upNetId
            it.northNetId = this.northNetId
            it.southNetId = this.southNetId
            it.westNetId = this.westNetId
            it.eastNetId = this.eastNetId
        }
    }
}
