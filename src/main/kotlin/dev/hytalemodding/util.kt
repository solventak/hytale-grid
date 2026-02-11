package dev.hytalemodding

import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk

/**
 * Legacy utility function for setting block powered state.
 * 
 * Note: This function is from the old power system and is no longer used.
 * The new system uses VisualState component + VisualStateSystem.
 * 
 * @param atPos Block position
 * @param on Whether the block should be powered (true) or unpowered (false)
 * @param worldChunk The chunk containing the block
 */
fun setPoweredState(atPos: Vector3i, on: Boolean, worldChunk: WorldChunk) {
    val blockType = worldChunk.getBlockType(atPos) ?: return
    val on = if (on) "default" else "Off"
    worldChunk.setBlockInteractionState(atPos, blockType, on)
}
