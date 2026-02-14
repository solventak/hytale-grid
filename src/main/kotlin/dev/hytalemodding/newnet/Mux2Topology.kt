package dev.hytalemodding.newnet

import com.hypixel.hytale.math.vector.Vector3i

import dev.hytalemodding.GridPlugin

/**
 * MUX topology integration — defines how MUX blocks participate in flood fill.
 *
 * ## Physical Layout Recap
 * Two MUX blocks side-by-side along a pair axis. Three fat ends visible from outside:
 * - **Input fat end**: the 2-wide face where A and B InputPorts connect
 * - **Output fat end**: the opposite 2-wide face where Y conducts
 * - **Internal face**: the shared face between the two MUX blocks (pairFace)
 *
 * Each MUX block has one face contributing to the input fat end (oppPairFace)
 * and one face contributing to the output fat end... wait, no. Both blocks share
 * the same pair axis. The fat ends are perpendicular to the pair axis. Let me
 * clarify with coordinates:
 *
 * ```
 * Pair axis: EAST-WEST (blocks at X=0 and X=1)
 *
 * Block A (X=0):                    Block B (X=1):
 *   WEST face  → narrow (exterior)    EAST face  → narrow (exterior)
 *   EAST face  → internal (pairFace)  WEST face  → internal (pairFace)
 *   NORTH face → half of fat end 1    NORTH face → half of fat end 1
 *   SOUTH face → half of fat end 2    SOUTH face → half of fat end 2
 *   UP face    → half of fat end 3    UP face    → half of fat end 3
 *   DOWN face  → half of fat end 4    DOWN face  → half of fat end 4
 * ```
 *
 * The "fat ends" are any face perpendicular to the pair axis — there are 4 such
 * face-directions, each exposing both blocks. S goes on a narrow face (only one
 * block exposed). A and B go on one fat end. Y comes out the opposite fat end.
 *
 * ## Flood Fill Rules for MUX
 *
 * A complete, non-disconnected MUX internally connects:
 * 1. The two MUX blocks' internal faces (pairFace) — always connected when complete
 * 2. Selected input face → through internal → to output face
 *
 * Specifically, when S=0 (select A):
 * - Block A's input-side face connects through Block A's pairFace → Block B's pairFace → Block B's output-side face
 * - Also Block A's input-side face → Block A's output-side face (direct)
 * - Block B's input-side face (B input) is ISOLATED
 *
 * When S=1 (select B):
 * - Block B's input-side face connects through to output
 * - Block A's input-side face (A input) is ISOLATED
 *
 * ## Implementation Strategy
 *
 * Rather than complex face-pair routing, we model the MUX as having a dynamic
 * facesMask per block that changes based on select state:
 *
 * - **Internal face (pairFace)**: Always connectable between the two MUX blocks
 *   (like a wire bridging them internally)
 * - **Selected input face**: Connectable (conducts through the MUX)
 * - **Non-selected input face**: NOT connectable (isolated)
 * - **Output face(s)**: Always connectable
 * - **Narrow faces (except S)**: Not connectable
 *
 * This means in the flood fill, a MUX block acts like a wire but with a dynamic
 * subset of faces that are connectable, determined by the select state.
 */

/**
 * Determines which faces of a MUX block should conduct during flood fill.
 *
 * Returns a face mask indicating which faces participate in the current topology.
 * This mask is used instead of the static PowerConnectable.facesMask.
 *
 * @param pos Position of this MUX block
 * @param mux The Mux2Part component
 * @param world The game world
 * @return 6-bit face mask of conducting faces, or 0 if disconnected/incomplete
 */
fun getMuxConductionMask(pos: Vector3i, mux: Mux2Part, worldAccess: WorldAccess): Int {
    if (!mux.isComplete || mux.isDisconnected) return 0

    val pairedPos = mux.pairedPos ?: return 0
    val pairFace = mux.pairFace

    // Find S to determine A/B assignment
    var sFace = findSelectFace(pos, pairFace, worldAccess)
    val sIsOnThisBlock = sFace != -1

    if (!sIsOnThisBlock) {
        sFace = findSelectFace(pairedPos, OPPOSITE_FACE[pairFace], worldAccess)
    }

    if (sFace == -1) return 0 // No S → can't determine orientation

    // A is the block closer to S, B is the other
    val sBlockPos = if (sIsOnThisBlock) pos else pairedPos
    val thisIsABlock = (pos == sBlockPos)
    val thisInputIndex = if (thisIsABlock) 0 else 1

    // Find THIS block's InputPort face (A and B can be on different faces)
    val myInputFace = findBlockInputFace(pos, pairFace, worldAccess)

    var mask = 0

    // Internal face (pairFace) — always conducts in a complete MUX
    mask = mask or (1 shl pairFace)

    // Output faces — all fat faces that don't have an InputPort on this block
    // (fat faces = perpendicular to pair axis, i.e. not pairFace or oppPairFace)
    val oppPairFace = OPPOSITE_FACE[pairFace]
    for (face in 0..5) {
        if (face == pairFace || face == oppPairFace) continue // skip pair axis (narrow faces)
        if (face == myInputFace) continue // skip this block's input face
        mask = mask or (1 shl face) // output face — always conducts
    }

    // Input face — only conducts if this block's input is selected
    if (myInputFace != -1 && mux.selectedInput == thisInputIndex) {
        mask = mask or (1 shl myInputFace)
    }

    val faceNames = arrayOf("DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST")
    val side = if (thisIsABlock) "A" else "B"
    val inputName = if (myInputFace != -1) faceNames.getOrElse(myInputFace) { "?" } else "NONE"
    val sel = if (myInputFace != -1 && mux.selectedInput == thisInputIndex) "CONDUCTS" else "ISOLATED"

    return mask
}

/**
 * Finds which face of a single MUX block has an InputPort adjacent.
 *
 * Scans the 4 perpendicular face directions (not pair axis) for an InputPort
 * pointing at this specific block. A and B can be on different face directions.
 *
 * @param pos Position of this MUX block
 * @param pairFace Face from this block toward its pair
 * @param world The game world
 * @return Face direction where this block's InputPort is, or -1 if none
 */
fun findBlockInputFace(pos: Vector3i, pairFace: Int, worldAccess: WorldAccess): Int {
    val oppPairFace = OPPOSITE_FACE[pairFace]
    for (face in 0..5) {
        if (face == pairFace || face == oppPairFace) continue // skip pair axis
        val (npos, nface) = neighborOfFace(pos, face)
        val ip = worldAccess.getComponent(npos, GridPlugin.inputPortComponentType)
        if (ip != null && ip.driverSideFace == nface) return face
    }
    return -1
}

/**
 * Finds which fat-end direction has InputPorts (the input side of the MUX).
 *
 * Scans the 4 perpendicular face directions (not pair axis) for InputPorts
 * adjacent to either MUX block.
 *
 * @param pos Position of one MUX block
 * @param pairedPos Position of the other MUX block
 * @param pairFace Face from pos toward pairedPos
 * @param world The game world
 * @return Face direction of the input fat end, or -1 if not found
 */
fun findInputFatEnd(pos: Vector3i, pairedPos: Vector3i, pairFace: Int, worldAccess: WorldAccess): Int {
    val oppPairFace = OPPOSITE_FACE[pairFace]

    for (face in 0..5) {
        if (face == pairFace || face == oppPairFace) continue // Skip pair axis

        // Check if either MUX block has an InputPort on this face direction
        val (npos1, nface1) = neighborOfFace(pos, face)
        val ip1 = worldAccess.getComponent(npos1, GridPlugin.inputPortComponentType)
        if (ip1 != null && ip1.driverSideFace == nface1) return face

        val (npos2, nface2) = neighborOfFace(pairedPos, face)
        val ip2 = worldAccess.getComponent(npos2, GridPlugin.inputPortComponentType)
        if (ip2 != null && ip2.driverSideFace == nface2) return face
    }
    return -1
}
