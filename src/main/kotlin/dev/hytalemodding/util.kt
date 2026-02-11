package dev.hytalemodding

import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk

fun setPoweredState(atPos: Vector3i, on: Boolean, worldChunk: WorldChunk) {
    val blockType = worldChunk.getBlockType(atPos) ?: return
    val on = if (on) "default" else "Off"
    worldChunk.setBlockInteractionState(atPos, blockType, on)
}
