package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.GridPlugin
import dev.hytalemodding.newnet.shared.FaceMask

/**
 * Component indicating which faces of a block can connect to power networks.
 * 
 * Used by wires, relays, lamps, and power sources to declare their connectivity.
 * The topology system checks facesMask during flood fill to determine which faces
 * can participate in networks.
 * 
 * Face indices: DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5
 * 
 * Example:
 * - Wire: facesMask = 0b111111 (all 6 faces connectable)
 * - Lamp: facesMask = 0b111111 (all faces can receive power)
 * - Relay: facesMask varies based on control face configuration
 */
class PowerConnectable : Component<ChunkStore> {
    /**
     * 6-bit mask indicating which faces are connectable.
     * Bit N set = face N is connectable (can join a power network).
     * Default: FaceMask.ALL (0b111111) = all faces connectable.
     */
    var facesMask: Int = FaceMask.ALL

    companion object {
        @JvmField
        val CODEC: BuilderCodec<PowerConnectable> =
            BuilderCodec.builder(PowerConnectable::class.java) { PowerConnectable() }
                // Note: facesMask uses default value; add codec entry if prefab customization needed
                .build()
    }

    fun getComponentType(): ComponentType<ChunkStore, PowerConnectable> {
        return GridPlugin.powerConnectableComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return PowerConnectable().also { it.facesMask = this.facesMask }
    }
}
