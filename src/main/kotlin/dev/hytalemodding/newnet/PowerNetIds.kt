package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.GridPlugin

/**
 * Component storing the network ID assignment for each face of a block.
 * 
 * Each of the 6 faces can belong to a different power network. This enables
 * fine-grained network segmentation (e.g., relay control faces isolated from
 * conduction faces, wire networks merging at junctions).
 * 
 * Network IDs are assigned by TopologySystem during flood fill and cleared
 * when topology is invalidated. A value of -1 (UNASSIGNED) means the face
 * is not currently part of any network.
 * 
 * Face indices: DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5
 */
class PowerNetIds : Component<ChunkStore> {
    /** Network ID for DOWN face (index 0). -1 = unassigned. */
    var downNetId: Int = -1
    /** Network ID for UP face (index 1). -1 = unassigned. */
    var upNetId: Int = -1
    /** Network ID for NORTH face (index 2). -1 = unassigned. */
    var northNetId: Int = -1
    /** Network ID for SOUTH face (index 3). -1 = unassigned. */
    var southNetId: Int = -1
    /** Network ID for WEST face (index 4). -1 = unassigned. */
    var westNetId: Int = -1
    /** Network ID for EAST face (index 5). -1 = unassigned. */
    var eastNetId: Int = -1

    /**
     * Gets the network ID for a given face index.
     * @param faceIndex Face index (0-5)
     * @return Network ID, or -1 if unassigned or invalid face index
     */
    fun get(faceIndex: Int): Int = when (faceIndex) {
        0 -> downNetId
        1 -> upNetId
        2 -> northNetId
        3 -> southNetId
        4 -> westNetId
        5 -> eastNetId
        else -> -1
    }

    /**
     * Sets the network ID for a given face index.
     * @param faceIndex Face index (0-5)
     * @param value Network ID to assign
     */
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
        return GridPlugin.powerNetIdsComponentType
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
