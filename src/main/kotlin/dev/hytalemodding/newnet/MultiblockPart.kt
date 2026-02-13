package dev.hytalemodding.newnet

import com.hypixel.hytale.component.Component
import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

/**
 * Abstract base component for multiblock structures.
 *
 * Multiblock structures are arrangements of multiple adjacent blocks that function
 * as a single logical unit. Examples include MUX (1×2), decoders (various L-shapes).
 *
 * ## Structure Lifecycle
 * 1. **Incomplete**: Blocks placed but don't match any valid shape → inert, may self-destruct
 * 2. **Complete**: Blocks match a shape template → structure activates and functions
 *
 * ## Face Roles
 * Each external face of the multiblock has a role:
 * - **Input**: Requires adjacent InputPort, read via probe logic
 * - **Output**: Drives connected net (PowerSource-like behavior)
 * - **Internal**: Connects blocks within the structure (not externally accessible)
 * - **Unused**: Structural/spare faces (not connectable)
 *
 * Only output faces participate in PowerConnectable networks.
 */
abstract class MultiblockPart : Component<ChunkStore> {
    /**
     * List of positions of all blocks in this multiblock structure.
     * Empty if structure is incomplete.
     * All blocks in a complete structure share the same position list.
     */
    var structurePositions: List<Vector3i> = emptyList()

    /**
     * Whether this multiblock structure is complete (all blocks present and valid).
     * Only complete structures function.
     */
    var isComplete: Boolean = false

    /**
     * Set of face indices (0-5) on THIS block that are internal
     * (connect to other blocks in the structure).
     * These faces are not externally accessible and don't participate in networks.
     */
    var internalFaces: Set<Int> = emptySet()

    /**
     * Set of face indices (0-5) on THIS block designated as input faces.
     * Input faces require adjacent InputPort blocks and are read via probe logic.
     * Input faces are NOT connectable (not part of any net).
     */
    var inputFaces: Set<Int> = emptySet()

    /**
     * Set of face indices (0-5) on THIS block designated as output faces.
     * Output faces drive their connected nets (like PowerSource behavior).
     * Only output faces are connectable (participate in networks).
     */
    var outputFaces: Set<Int> = emptySet()

    /**
     * Shape template ID this structure matches (for reference/debugging).
     * Example: "mux_1x2", "decoder_4x16_lshape"
     */
    var shapeId: String = ""
}
