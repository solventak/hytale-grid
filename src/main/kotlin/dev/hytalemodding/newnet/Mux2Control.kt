package dev.hytalemodding.newnet

import com.hypixel.hytale.math.vector.Vector3i

import dev.hytalemodding.GridPlugin
import dev.hytalemodding.newnet.shared.State4

/**
 * Determines the S (select) face for a complete MUX multiblock.
 *
 * The S InputPort must be on one of the two "narrow" faces — the faces that are
 * perpendicular to the pair axis and only expose one MUX block (not the 2-wide fat faces).
 *
 * For a pair axis along face F, the narrow faces are the 4 faces that are NOT F or opposite(F).
 * But S must be on a face where only ONE of the two MUX blocks has an adjacent InputPort
 * pointing at it. In practice, this means S is on the "end" of one of the MUX blocks
 * that isn't the fat face.
 *
 * @param muxPos Position of one MUX block in the pair
 * @param pairFace Face index pointing toward the paired block
 * @param world The game world
 * @return The face index where S is connected, or -1 if no S found
 */
fun findSelectFace(muxPos: Vector3i, pairFace: Int, worldAccess: WorldAccess): Int {
    // The narrow face is oppPairFace — the exterior face that only exposes this one MUX block.
    // S must be on a narrow face (not a fat face, which is where A/B inputs go).
    val narrowFace = OPPOSITE_FACE[pairFace]
    val (npos, nface) = neighborOfFace(muxPos, narrowFace)
    val inputPort = worldAccess.getComponent(npos, GridPlugin.inputPortComponentType)
        ?: return -1
    if (inputPort.driverSideFace == nface) {
        return narrowFace
    }
    return -1
}

/**
 * Identifies which MUX block in the pair is "closer to S" (input A / index 0)
 * and which is "farther from S" (input B / index 1).
 *
 * S is on a narrow face of one specific MUX block. That block's fat-end face
 * (opposite of pair face) hosts input A. The other block's fat-end face hosts input B.
 *
 * @param muxPos Position of the MUX block that has S on it
 * @param pairedPos Position of the other MUX block
 * @param pairFace Face of muxPos pointing toward pairedPos
 * @return Pair of (blockWithA, blockWithB) positions
 */
fun identifyInputBlocks(
    muxPos: Vector3i,
    pairedPos: Vector3i,
    pairFace: Int,
    sFace: Int,
    worldAccess: WorldAccess
): Pair<Vector3i, Vector3i> {
    // S is on muxPos's narrow face → muxPos is closer to S → muxPos has A
    // But we need to check which block actually has S
    val (sNeighborPos, sNeighborFace) = neighborOfFace(muxPos, sFace)
    val inputPort = worldAccess.getComponent(sNeighborPos, GridPlugin.inputPortComponentType)
    return if (inputPort != null && inputPort.driverSideFace == sNeighborFace) {
        // S is on muxPos → muxPos is the A side
        Pair(muxPos, pairedPos)
    } else {
        Pair(pairedPos, muxPos)
    }
}

/**
 * Evaluates the select signal S for a MUX multiblock.
 *
 * Reads the InputPort on the narrow side (S face), probes the network it's sensing,
 * and returns the select state. Same probe logic as relay control evaluation.
 *
 * @param muxPos Position of the MUX block that has S adjacent
 * @param sFace Face index where S InputPort is connected
 * @param world The game world
 * @param queue State queue with current net values
 * @return Triple of (selectedInput, isDisconnected, controlFault)
 */
fun evaluateMuxSelect(
    muxPos: Vector3i,
    sFace: Int,
    worldAccess: WorldAccess,
    queue: StateChangeEventQueue
): Triple<Int, Boolean, Boolean> {
    val (npos, nface) = neighborOfFace(muxPos, sFace)
    val inputPort = worldAccess.getComponent(npos, GridPlugin.inputPortComponentType)
        ?: return Triple(0, true, false) // No InputPort → disconnected

    // Probe: read the net on the InputPort's output face (opposite of driverSideFace)
    val outputFace = OPPOSITE_FACE[inputPort.driverSideFace]
    val (probePos, probeFace) = neighborOfFace(npos, outputFace)
    val probeIds = worldAccess.getComponent(probePos, GridPlugin.powerNetIdsComponentType)
    val netId = probeIds?.get(probeFace) ?: UNASSIGNED
    val netValue = if (netId != UNASSIGNED) {
        queue.powerNetValueCache[netId] ?: State4.HIGH_Z
    } else {
        State4.HIGH_Z
    }


    return when (netValue) {
        State4.ZERO -> Triple(0, false, false)      // S=0 → select A
        State4.ONE -> Triple(1, false, false)        // S=1 → select B
        State4.HIGH_Z -> Triple(0, true, false)      // S floating → disconnected
        State4.UNKNOWN_X -> Triple(0, true, true)    // S unknown → safe-off
    }
}

/**
 * Evaluates all MUX multiblocks in the dirty set and updates their control state.
 *
 * For each complete MUX pair:
 * - Finds the S InputPort
 * - Reads S value to determine selectedInput
 * - Compares to previous state to detect toggles
 *
 * If any MUX's routing changed (selectedInput or isDisconnected), topology must be
 * rebuilt because the MUX connects different nets based on S.
 *
 * Only evaluates one block per pair (the one that has S adjacent) to avoid
 * double-processing.
 *
 * @param dirtyBlocks Blocks affected by this topology round
 * @param world The game world
 * @param queue State queue with resolved net values
 * @return true if any MUX's routing state changed (topology rebuild required)
 */
fun evaluateAllMuxControls(
    dirtyBlocks: Set<Vector3i>,
    worldAccess: WorldAccess,
    queue: StateChangeEventQueue,
    excludePositions: Set<Vector3i> = emptySet()
): Boolean {
    var anyToggled = false
    val processed = mutableSetOf<Vector3i>() // Track pairs to avoid double-eval

    for (pos in dirtyBlocks) {
        if (pos in processed) continue
        if (pos in excludePositions) continue
        val mux = worldAccess.getComponent(pos, GridPlugin.mux2PartComponentType) ?: continue
        if (!mux.isComplete) continue

        val pairedPos = mux.pairedPos ?: continue
        processed.add(pos)
        processed.add(pairedPos)

        val pairedMux = worldAccess.getComponent(pairedPos, GridPlugin.mux2PartComponentType) ?: continue

        // Find S on either block
        var sFace = findSelectFace(pos, mux.pairFace, worldAccess)
        var sBlockPos = pos
        if (sFace == -1) {
            sFace = findSelectFace(pairedPos, OPPOSITE_FACE[mux.pairFace], worldAccess)
            sBlockPos = pairedPos
        }

        if (sFace == -1) {
            // No S connected → both blocks disconnected
            mux.lastSelectedInput = mux.selectedInput
            mux.lastIsDisconnected = mux.isDisconnected
            mux.isDisconnected = true
            mux.controlFault = false
            pairedMux.lastSelectedInput = pairedMux.selectedInput
            pairedMux.lastIsDisconnected = pairedMux.isDisconnected
            pairedMux.isDisconnected = true
            pairedMux.controlFault = false
            if (mux.isDisconnected != mux.lastIsDisconnected) anyToggled = true
            continue
        }

        val (selectedInput, isDisconnected, controlFault) = evaluateMuxSelect(sBlockPos, sFace, worldAccess, queue)

        // Update both blocks in the pair with the same state
        for (m in listOf(mux, pairedMux)) {
            m.lastSelectedInput = m.selectedInput
            m.lastIsDisconnected = m.isDisconnected
            m.selectedInput = selectedInput
            m.isDisconnected = isDisconnected
            m.controlFault = controlFault
        }

        val routingChanged = (mux.selectedInput != mux.lastSelectedInput) ||
                (mux.isDisconnected != mux.lastIsDisconnected)
        if (routingChanged) {
            anyToggled = true
        }
    }
    return anyToggled
}

/**
 * Collects positions of all MUX blocks whose routing state changed.
 *
 * These positions become seeds for the next topology rebuild round.
 * When a MUX toggles, the nets on its output face merge with a different input net,
 * requiring full topology recalculation.
 *
 * @param dirtyBlocks Blocks to scan for toggled MUX parts
 * @param world The game world
 * @return Set of MUX positions where routing changed
 */
fun collectToggledMuxPositions(dirtyBlocks: Set<Vector3i>, worldAccess: WorldAccess): MutableSet<Vector3i> {
    val toggled = mutableSetOf<Vector3i>()
    for (pos in dirtyBlocks) {
        val mux = worldAccess.getComponent(pos, GridPlugin.mux2PartComponentType) ?: continue
        if (mux.selectedInput != mux.lastSelectedInput || mux.isDisconnected != mux.lastIsDisconnected) {
            toggled.add(pos)
            mux.pairedPos?.let { toggled.add(it) }
        }
    }
    return toggled
}
