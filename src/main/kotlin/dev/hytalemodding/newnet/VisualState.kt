package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.GridPlugin

/**
 * Generic component for storing a block's desired visual interaction state.
 * 
 * Separates logical state computation (in TopologySystem) from visual application
 * (in VisualStateSystem). This two-phase approach prevents race conditions and
 * allows batched visual updates.
 * 
 * Usage pattern:
 * 1. TopologySystem computes new state and updates VisualState.state
 * 2. TopologySystem marks position dirty (queue.visualDirtyPositions.add)
 * 3. VisualStateSystem (runs after TopologySystem) reads dirty positions
 * 4. VisualStateSystem calls worldChunk.setBlockInteractionState()
 * 
 * Common state names:
 * - "default" - Off/inactive appearance
 * - "On" - Powered/active appearance
 * 
 * Block types define interaction states in their .blocktype JSON:
 * ```json
 * "interactions": {
 *   "default": { "model": "Lamp_Off" },
 *   "On": { "model": "Lamp_On" }
 * }
 * ```
 */
class VisualState : Component<ChunkStore> {
    /**
     * Desired interaction state name.
     * Will be applied by VisualStateSystem via setBlockInteractionState().
     * Default: "default" (typically the off/inactive state).
     */
    var state: String = "default"

    companion object {
        @JvmField
        val CODEC: BuilderCodec<VisualState> =
            BuilderCodec.builder(VisualState::class.java) { VisualState() }.build()
    }

    fun getComponentType(): ComponentType<ChunkStore, VisualState> {
        return GridPlugin.visualStateComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return VisualState().also { it.state = this.state }
    }
}
