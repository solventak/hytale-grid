package dev.hytalemodding.newnet

import com.hypixel.hytale.math.vector.Vector3i

import dev.hytalemodding.ExamplePlugin

/**
 * Handles MUX multiblock pairing, validation, and self-destruction logic.
 *
 * ## Pairing Rules
 * When a Mux2Part block is placed:
 * 1. Scan all 6 adjacent faces for another Mux2Part block
 * 2. If found an unpaired Mux2Part → pair both blocks, mark complete
 * 3. If found a paired Mux2Part (already in a complete pair) → destroy the new block
 *    (can't form a 3-block MUX)
 * 4. If no adjacent Mux2Part → mark as incomplete (inert)
 *
 * ## Validation Rules (incomplete MUX)
 * An incomplete (single) MUX block self-destructs if any non-MUX PowerConnectable
 * block connects to it. This prevents dangling half-MUX blocks from interfering
 * with the power network.
 *
 * ## Destruction Rules
 * When a Mux2Part block is destroyed:
 * 1. If it was part of a complete pair → unpair the other block, mark it incomplete
 * 2. The now-incomplete block will self-destruct if it has non-MUX neighbors
 *
 * These functions are called from PowerBlockAddedSystem and PowerBlockBreakEvent.
 */

/**
 * Attempts to pair a newly placed MUX block with an adjacent MUX block.
 *
 * @param pos Position of the newly placed MUX block
 * @param mux The Mux2Part component of the newly placed block
 * @param world The game world
 * @return true if pairing succeeded (complete MUX formed), false if incomplete
 */
fun tryPairMux(pos: Vector3i, mux: Mux2Part, worldAccess: WorldAccess): Boolean {
    for (face in 0..5) {
        val (npos, nface) = neighborOfFace(pos, face)
        val neighborMux = worldAccess.getComponent(npos, ExamplePlugin.mux2PartComponentType)
            ?: continue

        // Special case: if neighbor is already paired TO THIS POSITION, sync up with it
        // This handles world load where blocks may load in any order
        if (neighborMux.isComplete && neighborMux.pairedPos == pos) {
            mux.pairedPos = npos
            mux.pairFace = face
            mux.isComplete = true
            mux.isDisconnected = neighborMux.isDisconnected
            println("[Mux2Placement] MUX at $pos synced with existing pair at $npos (world load recovery)")
            return true
        }

        if (neighborMux.isComplete) {
            // Neighbor is already in a complete pair with someone else — can't attach to it
            println("[Mux2Placement] MUX at $pos can't pair: neighbor at $npos already complete")
            continue
        }

        // Found an unpaired neighbor — form pair
        mux.pairedPos = npos
        mux.pairFace = face
        mux.isComplete = true
        mux.isDisconnected = true // Start disconnected until S is evaluated

        neighborMux.pairedPos = pos
        neighborMux.pairFace = nface
        neighborMux.isComplete = true
        neighborMux.isDisconnected = true

        println("[Mux2Placement] MUX pair formed: $pos <-> $npos (axis: ${FACE_NAMES[face]})")
        return true
    }

    // No valid neighbor found — stays incomplete
    mux.isComplete = false
    mux.pairedPos = null
    mux.pairFace = -1
    println("[Mux2Placement] MUX at $pos placed incomplete (no adjacent MUX)")
    return false
}

/**
 * Validates an incomplete (unpaired) MUX block.
 *
 * An incomplete MUX must not have any non-MUX PowerConnectable neighbors.
 * If it does, the MUX block is invalid and should be destroyed.
 *
 * @param pos Position of the incomplete MUX block
 * @param world The game world
 * @return true if the block should be destroyed (has invalid neighbors)
 */
fun shouldDestroyIncompleteMux(pos: Vector3i, worldAccess: WorldAccess): Boolean {
    for (face in 0..5) {
        val (npos, _) = neighborOfFace(pos, face)
        // If neighbor is a MUX part, only allow unpaired ones (potential future pair)
        val neighborMux = worldAccess.getComponent(npos, ExamplePlugin.mux2PartComponentType)
        if (neighborMux != null) {
            if (neighborMux.isComplete) {
                println("[Mux2Placement] Incomplete MUX at $pos adjacent to complete MUX at $npos — destroying")
                return true
            }
            continue // Unpaired MUX neighbor is fine
        }
        // Check if neighbor is a PowerConnectable (wire, relay, lamp, source, etc.)
        if (worldAccess.getComponent(npos, ExamplePlugin.powerConnectableComponentType) != null) {
            println("[Mux2Placement] Incomplete MUX at $pos has non-MUX connectable neighbor at $npos — destroying")
            return true
        }
        // Check if neighbor is an InputPort
        if (worldAccess.getComponent(npos, ExamplePlugin.inputPortComponentType) != null) {
            println("[Mux2Placement] Incomplete MUX at $pos has InputPort neighbor at $npos — destroying")
            return true
        }
    }
    return false
}

/**
 * Handles the destruction of a MUX block that was part of a complete pair.
 *
 * Unpairs the remaining block and marks it as incomplete. The remaining block
 * may then self-destruct if it has invalid neighbors.
 *
 * @param destroyedPos Position of the MUX block being destroyed
 * @param world The game world
 */
fun handleMuxDestroyed(destroyedPos: Vector3i, worldAccess: WorldAccess) {
    val mux = worldAccess.getComponent(destroyedPos, ExamplePlugin.mux2PartComponentType)
        ?: return

    val pairedPos = mux.pairedPos ?: return
    val pairedMux = worldAccess.getComponent(pairedPos, ExamplePlugin.mux2PartComponentType)

    if (pairedMux != null) {
        pairedMux.pairedPos = null
        pairedMux.isComplete = false
        pairedMux.pairFace = -1
        pairedMux.isDisconnected = true
        pairedMux.controlFault = false
        println("[Mux2Placement] MUX pair broken: $destroyedPos destroyed, $pairedPos now incomplete")

        // Check if the now-incomplete block should also be destroyed
        if (shouldDestroyIncompleteMux(pairedPos, worldAccess)) {
            println("[Mux2Placement] Remaining MUX at $pairedPos has invalid neighbors — scheduling destroy")
            if (worldAccess is HytaleWorldAccess) {
                worldAccess.world.execute { worldAccess.world.setBlock(pairedPos.x, pairedPos.y, pairedPos.z, "Empty") }
            }
        }
    }
}
