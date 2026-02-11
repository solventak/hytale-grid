package dev.hytalemodding.newnet

import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.universe.world.World
import dev.hytalemodding.ExamplePlugin
import dev.hytalemodding.newnet.shared.State4

/**
 * Determines which faces of a relay block are control faces.
 * 
 * A face is a control face if:
 * - The adjacent block on that face is an InputPort
 * - The InputPort's driverSideFace points back toward the relay
 * 
 * Control faces are isolated from conduction during topology flood fill.
 * This allows relays to read control signals without conducting them.
 * 
 * @param relayPos Position of the relay block
 * @param world The game world
 * @return 6-bit mask where bit N set means face N is a control face
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
 * Evaluates a relay's control state by reading all control InputPort probes.
 * 
 * Control evaluation rules (pure OR logic):
 * - No control faces → (disabled, no fault)
 * - Any control net is UNKNOWN_X → (disabled, controlFault=true) — safe-off
 * - Any control net is ONE → (enabled, no fault)
 * - All control nets ZERO/HIGH_Z → (disabled, no fault)
 * 
 * For each control face:
 * 1. Find the adjacent InputPort
 * 2. Probe the block on the InputPort's output face
 * 3. Read that block's PowerNetIds to get the network ID
 * 4. Look up the 4-state value in powerNetValueCache
 * 
 * @param relayPos Position of the relay block
 * @param world The game world
 * @param queue State queue with current net values
 * @return Pair of (enabled, controlFault)
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
 * Evaluates all relay blocks in the dirty set and updates their control state.
 * 
 * For each relay:
 * - Reads all control InputPort values
 * - Computes new enabled/controlFault state
 * - Compares to previous state to detect toggles
 * 
 * If any relay toggled (enabled changed), topology must be rebuilt because
 * relay conduction affects network connectivity (enabled relays star-connect
 * their conduction faces; disabled relays have no internal connectivity).
 * 
 * Called after delta-cycle evaluation completes in each topology round.
 *
 * @param dirtyBlocks Blocks affected by this topology round
 * @param world The game world
 * @param queue State queue with resolved net values
 * @return true if any relay's enabled state changed (topology rebuild required)
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
 * Collects positions of all relays whose enabled state changed.
 * 
 * These positions become seeds for the next topology rebuild round.
 * When a relay toggles, the networks on its conduction faces merge (if enabling)
 * or split (if disabling), requiring full topology recalculation.
 * 
 * @param dirtyBlocks Blocks to scan for toggled relays
 * @param world The game world
 * @return Set of relay positions where enabled != lastEnabled
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
