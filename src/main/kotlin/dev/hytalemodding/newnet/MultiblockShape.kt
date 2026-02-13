package dev.hytalemodding.newnet

import com.hypixel.hytale.math.vector.Vector3i

/**
 * Defines a multiblock structure's shape and face roles.
 *
 * A shape is defined in a canonical orientation (e.g., along +X axis), then
 * the placement system tries all rotations/orientations when matching placed blocks.
 *
 * ## Coordinate System
 * Positions are relative to an arbitrary "origin" block (typically at 0,0,0).
 * The placement system translates/rotates these positions to match world coordinates.
 *
 * ## Face Role Tags
 * Each external face of each block can be tagged with a role:
 * - **Input**: Accepts adjacent InputPort (e.g., "S", "EN", "A")
 * - **Output**: Drives connected net (e.g., "Y0", "Y1")
 * - **Internal**: Connects blocks within structure (computed automatically)
 * - **Unused**: No role (structural/spare)
 *
 * Face roles are defined per-block position + face index (0-5).
 *
 * ## Example: 1×2 MUX
 * ```
 * Positions: [(0,0,0), (1,0,0)]  // Two blocks along X axis
 * Input faces:
 *   (0,0,0) face WEST (4) → "S" (select input)
 *   (0,0,0) face SOUTH (2) → "A" (data input A)
 *   (1,0,0) face SOUTH (2) → "B" (data input B)
 * Output faces:
 *   (0,0,0) face NORTH (3) → "Y" (output)
 *   (1,0,0) face NORTH (3) → "Y" (output, same net)
 * Internal faces:
 *   (0,0,0) face EAST (5) ↔ (1,0,0) face WEST (4)
 * ```
 */
data class MultiblockShape(
    /** Unique identifier for this shape (e.g., "mux_1x2", "decoder_4x16") */
    val id: String,

    /**
     * List of relative block positions that make up this structure.
     * One position should be at (0,0,0) as the reference origin.
     */
    val positions: List<Vector3i>,

    /**
     * Map of (position, faceIndex) → input role name.
     * Example: (Vector3i(0,0,0), 4) → "S" means face WEST on block at (0,0,0) is select input.
     */
    val inputFaces: Map<Pair<Vector3i, Int>, String>,

    /**
     * Map of (position, faceIndex) → output role name.
     * Example: (Vector3i(0,0,0), 3) → "Y0" means face NORTH on block at (0,0,0) is output Y0.
     */
    val outputFaces: Map<Pair<Vector3i, Int>, String>
) {
    /**
     * Compute internal faces (faces between blocks in this structure).
     * Returns map of (position, faceIndex) → neighboring position within structure.
     */
    fun computeInternalFaces(): Map<Pair<Vector3i, Int>, Vector3i> {
        val posSet = positions.toSet()
        val internalMap = mutableMapOf<Pair<Vector3i, Int>, Vector3i>()

        for (pos in positions) {
            for (face in 0..5) {
                val (neighborPos, _) = neighborOfFaceRelative(pos, face)
                if (neighborPos in posSet) {
                    internalMap[Pair(pos, face)] = neighborPos
                }
            }
        }

        return internalMap
    }

    /**
     * Get all 24 possible orientations of this shape (rotations around all axes).
     * Returns list of transformed shapes (same ID, different positions/face mappings).
     */
    fun getAllOrientations(): List<OrientedShape> {
        // TODO: Implement rotation logic
        // For now, return only the canonical orientation
        return listOf(OrientedShape(this, Vector3i(0, 0, 0), 0))
    }
}

/**
 * A shape with a specific orientation/rotation applied.
 * Used during placement matching to try all possible orientations.
 */
data class OrientedShape(
    val shape: MultiblockShape,
    val translation: Vector3i,
    val rotationIndex: Int // 0-23 for all possible rotations
) {
    /**
     * Transform a relative position from canonical space to world space.
     */
    fun worldPosition(relativePos: Vector3i): Vector3i {
        // TODO: Apply rotation, then translation
        // For now, just translate
        return Vector3i(
            relativePos.x + translation.x,
            relativePos.y + translation.y,
            relativePos.z + translation.z
        )
    }

    /**
     * Transform a face index from canonical space to world space.
     */
    fun worldFace(canonicalFace: Int): Int {
        // TODO: Apply rotation to face index
        // For now, return unchanged
        return canonicalFace
    }
}

/**
 * Compute neighbor position and opposite face for a relative position + face.
 * Helper for computing internal faces in canonical space.
 */
private fun neighborOfFaceRelative(pos: Vector3i, face: Int): Pair<Vector3i, Int> {
    val offset = when (face) {
        0 -> Vector3i(0, -1, 0)  // DOWN
        1 -> Vector3i(0, 1, 0)   // UP
        2 -> Vector3i(0, 0, -1)  // SOUTH
        3 -> Vector3i(0, 0, 1)   // NORTH
        4 -> Vector3i(-1, 0, 0)  // WEST
        5 -> Vector3i(1, 0, 0)   // EAST
        else -> Vector3i(0, 0, 0)
    }
    val neighborPos = Vector3i(pos.x + offset.x, pos.y + offset.y, pos.z + offset.z)
    val oppositeFace = when (face) {
        0 -> 1; 1 -> 0; 2 -> 3; 3 -> 2; 4 -> 5; 5 -> 4
        else -> face
    }
    return Pair(neighborPos, oppositeFace)
}
