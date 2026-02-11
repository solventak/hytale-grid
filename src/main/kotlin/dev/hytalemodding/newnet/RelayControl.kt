package dev.hytalemodding.newnet

import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.universe.world.World
import dev.hytalemodding.ExamplePlugin
import dev.hytalemodding.newnet.shared.State4

/**
 * Returns a bitmask of control faces for a relay at [relayPos].
 * A face is a control face iff the adjacent block on that face is an InputPort
 * whose driverSideFace points back toward the relay.
 */
fun getControlFaces(relayPos: Vector3i, world: World): Int {
    var mask = 0
    for (face in 0..5) {
        val (npos, nface) = neighborOfFace(relayPos, face)
        val inputPort = getComponentForGlobalXyz(world, npos, ExamplePlugin.inputPortComponentType)
            ?: continue
        // InputPort's driverSideFace should point back at the relay (== nface, the face facing the relay)
        if (inputPort.driverSideFace == nface) {
            mask = mask or (1 shl face)
        }
    }
    return mask
}

/**
 * Evaluates a single relay's control state from resolved net values.
 * Returns (enabled, controlFault).
 */
fun evaluateRelayControl(
    relayPos: Vector3i,
    world: World,
    queue: StateChangeEventQueue
): Pair<Boolean, Boolean> {
    val controlMask = getControlFaces(relayPos, world)
    if (controlMask == 0) {
        // No control faces → disabled, no fault
        return Pair(false, false)
    }

    var has1 = false
    var hasX = false

    for (face in 0..5) {
        if (controlMask and (1 shl face) == 0) continue
        // Probe the block adjacent to the InputPort's output face (not the InputPort itself).
        // InputPort has no PowerNetIds — it's a pure probe that reads the neighboring block's net.
        val (npos, nface) = neighborOfFace(relayPos, face)
        val inputPort = getComponentForGlobalXyz(world, npos, ExamplePlugin.inputPortComponentType)
            ?: continue
        val outputFace = OPPOSITE_FACE[inputPort.driverSideFace]
        val (probePos, probeFace) = neighborOfFace(npos, outputFace)
        val probeIds = getComponentForGlobalXyz(world, probePos, ExamplePlugin.powerNetIdsComponentType)
        val netId = probeIds?.get(probeFace) ?: UNASSIGNED
        val netValue = if (netId != UNASSIGNED) {
            queue.powerNetValueCache[netId] ?: State4.HIGH_Z
        } else {
            State4.HIGH_Z
        }
        when (netValue) {
            State4.ONE -> has1 = true
            State4.UNKNOWN_X -> hasX = true
            else -> {} // ZERO and HIGH_Z: no effect on flags
        }
    }

    return when {
        hasX -> Pair(false, true)   // safe-off on unknown
        has1 -> Pair(true, false)   // any 1 enables (pure OR)
        else -> Pair(false, false)  // only 0/Z inputs
    }
}

/**
 * Evaluates all relays in [dirtyBlocks] and updates their enabled/controlFault state.
 * Returns true if any relay's enabled state changed (topology needs rebuild).
 */
fun evaluateAllRelayControls(
    dirtyBlocks: Set<Vector3i>,
    world: World,
    queue: StateChangeEventQueue
): Boolean {
    var anyToggled = false
    for (pos in dirtyBlocks) {
        val relay = getComponentForGlobalXyz(world, pos, ExamplePlugin.relayComponentType) ?: continue
        val (enabled, controlFault) = evaluateRelayControl(pos, world, queue)
        relay.lastEnabled = relay.enabled
        relay.enabled = enabled
        relay.controlFault = controlFault
        if (relay.enabled != relay.lastEnabled) {
            println("[RelayControl] Relay at $pos toggled: enabled=$enabled (was=${relay.lastEnabled}), controlFault=$controlFault")
            anyToggled = true
        }
    }
    return anyToggled
}

/**
 * Collects positions of relays whose enabled state changed in this round.
 * These positions become seeds for the next topology rebuild round.
 */
fun collectToggledRelayPositions(dirtyBlocks: Set<Vector3i>, world: World): MutableSet<Vector3i> {
    val toggled = mutableSetOf<Vector3i>()
    for (pos in dirtyBlocks) {
        val relay = getComponentForGlobalXyz(world, pos, ExamplePlugin.relayComponentType) ?: continue
        if (relay.enabled != relay.lastEnabled) {
            toggled.add(pos)
        }
    }
    return toggled
}
