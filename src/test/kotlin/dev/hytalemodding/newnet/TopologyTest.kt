package dev.hytalemodding.newnet

import com.hypixel.hytale.math.vector.Vector3i
import dev.hytalemodding.newnet.shared.State4
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for the power network topology system.
 *
 * Tests run without a Hytale server by using MockPowerWorld as the WorldAccess implementation.
 * Component types are initialized once via TestComponentTypes.
 */
class TopologyTest {

    companion object {
        init {
            TestComponentTypes.ensureInitialized()
        }

        @JvmStatic
        @BeforeAll
        fun initComponentTypes() {
            // Already initialized in companion init block above
        }
    }

    private lateinit var world: MockPowerWorld
    private lateinit var queue: StateChangeEventQueue

    @BeforeEach
    fun setUp() {
        world = MockPowerWorld()
        queue = world.createQueue()
    }

    /**
     * Runs the full topology pipeline on the given seed positions:
     * clear nets → rebuild → evaluate sources → resolve nets.
     * Performs delta-cycle iteration until stable or max cycles reached.
     */
    private fun runTopology(seeds: Set<Vector3i>) {
        val dirtyBlocks = clearNetsFromSeeds(seeds, world, queue)

        // Expand for InputPort drivers
        var expanded = expandForAdjacentInputPorts(
            expandForInputPortDrivers(dirtyBlocks, world), world
        )
        val allDirty = if (expanded.size > dirtyBlocks.size) {
            val extra = clearNetsFromSeeds(expanded - dirtyBlocks, world, queue)
            expanded + extra
        } else {
            dirtyBlocks
        }

        rebuildPowerTopology(allDirty, world, queue)

        val dirtyNetIds = collectNetIds(allDirty, world)

        // Initialize to HIGH_Z
        for (netId in dirtyNetIds) {
            queue.powerNetValueCache[netId] = State4.HIGH_Z
        }

        // Delta-cycle evaluation
        val prevNetValues = mutableMapOf<Int, State4>()
        var stable = false
        var cycle = 0
        while (!stable && cycle < TopologySystem.MAX_DELTA_CYCLES) {
            cycle++
            prevNetValues.clear()
            for (netId in dirtyNetIds) {
                prevNetValues[netId] = queue.powerNetValueCache[netId] ?: State4.HIGH_Z
            }
            evaluateSources(allDirty, world, queue)
            resolveNets(dirtyNetIds, allDirty, world, queue)
            stable = dirtyNetIds.all {
                queue.powerNetValueCache[it] == prevNetValues[it]
            }
        }

        if (!stable) {
            for (netId in dirtyNetIds) {
                queue.powerNetValueCache[netId] = State4.UNKNOWN_X
            }
        }
    }

    /**
     * Full topology run with relay/MUX control evaluation and topology rounds.
     */
    private fun runTopologyWithControls(seeds: Set<Vector3i>) {
        var topologySeeds = expandForInputPortDrivers(
            expandThroughConnected(seeds, world), world
        )

        val allDirtyBlocks = mutableSetOf<Vector3i>()
        val allDirtyNetIds = mutableSetOf<Int>()

        for (round in 0 until TopologySystem.MAX_TOPOLOGY_ROUNDS) {
            var dirtyBlocks: Set<Vector3i> = clearNetsFromSeeds(topologySeeds, world, queue)

            // Expand for InputPort drivers iteratively
            while (true) {
                val expanded = expandForAdjacentInputPorts(
                    expandForInputPortDrivers(dirtyBlocks, world), world
                )
                if (expanded.size <= dirtyBlocks.size) break
                val newPositions = expanded - dirtyBlocks
                val extraDirty = clearNetsFromSeeds(newPositions, world, queue)
                dirtyBlocks = expanded + extraDirty
            }

            allDirtyBlocks.addAll(dirtyBlocks)

            // Reset relays on round 0
            if (round == 0) {
                for (pos in dirtyBlocks) {
                    val relay = world.getComponent(pos, TestComponentTypes.relayComponentType) ?: continue
                    relay.lastEnabled = relay.enabled
                    relay.enabled = false
                }
            }

            rebuildPowerTopology(dirtyBlocks, world, queue)

            val dirtyNetIds = collectNetIds(dirtyBlocks, world)
            allDirtyNetIds.addAll(dirtyNetIds)

            for (netId in dirtyNetIds) {
                queue.powerNetValueCache[netId] = State4.HIGH_Z
            }

            // Delta-cycle evaluation
            var stable = false
            var cycle = 0
            val prevNetValues = mutableMapOf<Int, State4>()
            while (!stable && cycle < TopologySystem.MAX_DELTA_CYCLES) {
                cycle++
                prevNetValues.clear()
                for (netId in dirtyNetIds) {
                    prevNetValues[netId] = queue.powerNetValueCache[netId] ?: State4.HIGH_Z
                }
                evaluateSources(dirtyBlocks, world, queue)
                resolveNets(dirtyNetIds, dirtyBlocks, world, queue)
                stable = dirtyNetIds.all {
                    queue.powerNetValueCache[it] == prevNetValues[it]
                }
            }

            if (!stable) {
                for (netId in dirtyNetIds) {
                    queue.powerNetValueCache[netId] = State4.UNKNOWN_X
                }
            }

            // Evaluate controls
            val anyRelayToggled = evaluateAllRelayControls(dirtyBlocks, world, queue)
            val anyMuxToggled = if (round == 0) {
                evaluateAllMuxControls(dirtyBlocks, world, queue)
            } else false

            if (!anyRelayToggled && !anyMuxToggled) break

            // Collect toggled positions as seeds for next round
            val toggledRelays = collectToggledRelayPositions(dirtyBlocks, world)
            val toggledMuxes = collectToggledMuxPositions(dirtyBlocks, world)
            val allToggled = toggledRelays.apply { addAll(toggledMuxes) }
            val expandedToggled = expand(allToggled).toMutableSet()
            for (pos in expandedToggled.toSet()) {
                val ip = world.getComponent(pos, TestComponentTypes.inputPortComponentType) ?: continue
                val farFace = OPPOSITE_FACE[ip.driverSideFace]
                val (farPos, _) = neighborOfFace(pos, farFace)
                expandedToggled.add(farPos)
                for (f in 0..5) {
                    expandedToggled.add(Vector3i(farPos.x + FACE_DX[f], farPos.y + FACE_DY[f], farPos.z + FACE_DZ[f]))
                }
            }
            topologySeeds = expandForInputPortDrivers(expandedToggled, world)
        }

        // Update visuals
        updateLamps(allDirtyBlocks, world, queue)
    }

    /**
     * Gets the net value for a specific face of a block.
     */
    private fun getNetValue(pos: Vector3i, face: Int): State4 {
        val ids = world.getComponent(pos, TestComponentTypes.powerNetIdsComponentType) ?: return State4.HIGH_Z
        val netId = ids.get(face)
        if (netId == UNASSIGNED) return State4.HIGH_Z
        return queue.powerNetValueCache[netId] ?: State4.HIGH_Z
    }

    /**
     * Gets the net value for any face of a block (returns first non-HIGH_Z value found).
     */
    private fun getAnyNetValue(pos: Vector3i): State4 {
        val ids = world.getComponent(pos, TestComponentTypes.powerNetIdsComponentType) ?: return State4.HIGH_Z
        for (face in 0..5) {
            val netId = ids.get(face)
            if (netId != UNASSIGNED) {
                val v = queue.powerNetValueCache[netId] ?: State4.HIGH_Z
                if (v != State4.HIGH_Z) return v
            }
        }
        // Return value of first assigned net
        for (face in 0..5) {
            val netId = ids.get(face)
            if (netId != UNASSIGNED) return queue.powerNetValueCache[netId] ?: State4.HIGH_Z
        }
        return State4.HIGH_Z
    }

    // ============================================================
    // Basic tests
    // ============================================================

    @Test
    fun testWireChainPropagation() {
        // Source at (0,0,0), 5 wires, lamp at (6,0,0)
        world.placeSource(Vector3i(0, 0, 0))
        for (i in 1..5) {
            world.placeWire(Vector3i(i, 0, 0))
        }
        world.placeLamp(Vector3i(6, 0, 0))

        val seeds = (0..6).map { Vector3i(it, 0, 0) }.toSet()
        runTopology(seeds)

        // Lamp's net should have value ONE
        assertEquals(State4.ONE, getAnyNetValue(Vector3i(6, 0, 0)),
            "Lamp should be on net with value ONE")
    }

    @Test
    fun testSourceInverter() {
        // Source at (0,0,0), InputPort at (1,0,0) with driverSideFace=WEST(4) pointing at source
        // Wire at (2,0,0) connected to InputPort output side
        // Another source at (3,0,0) driving ONE onto the wire at (2,0,0)
        world.placeSource(Vector3i(0, 0, 0))
        world.placeInputPort(Vector3i(1, 0, 0), driverSideFace = 4) // WEST → toward source at (0,0,0)
        world.placeWire(Vector3i(2, 0, 0))
        world.placeSource(Vector3i(3, 0, 0)) // drives ONE onto wire at (2,0,0)

        val seeds = setOf(
            Vector3i(0, 0, 0), Vector3i(1, 0, 0),
            Vector3i(2, 0, 0), Vector3i(3, 0, 0)
        )
        runTopology(seeds)

        // Source at (0,0,0) reads InputPort which probes wire at (2,0,0) = ONE
        // Inverter: input ONE → drive ZERO
        val source = world.getComponent(Vector3i(0, 0, 0), TestComponentTypes.powerSourceComponentType)!!
        assertEquals(State4.ZERO, source.driveState,
            "Source with input ONE should drive ZERO (inverter)")
    }

    @Test
    fun testDisconnectedWire() {
        // Wire with no source
        world.placeWire(Vector3i(5, 5, 5))

        val seeds = setOf(Vector3i(5, 5, 5))
        runTopology(seeds)

        assertEquals(State4.HIGH_Z, getAnyNetValue(Vector3i(5, 5, 5)),
            "Disconnected wire should be HIGH_Z")
    }

    @Test
    fun testRelayEnabled() {
        // Source → wire → relay (controlled by another source via InputPort) → wire → lamp
        // Control source drives ONE → relay enabled → power passes through
        world.placeSource(Vector3i(0, 0, 0)) // power source
        world.placeWire(Vector3i(1, 0, 0))
        world.placeRelay(Vector3i(2, 0, 0))
        world.placeWire(Vector3i(3, 0, 0))
        world.placeLamp(Vector3i(4, 0, 0))

        // Control: InputPort on UP face of relay, with source above
        world.placeInputPort(Vector3i(2, 1, 0), driverSideFace = 0) // DOWN → toward relay at (2,0,0)
        world.placeSource(Vector3i(2, 2, 0)) // control source drives ONE
        // Wire to probe on output side of InputPort (opposite of driver = UP)
        // InputPort output = UP face, probes block at (2,2,0) - but that's the control source
        // Actually: InputPort at (2,1,0), driverSideFace=0 (DOWN) → output face = UP (1)
        // Probes block at (2,2,0) face DOWN(0) - which is the control source
        // Control source drives ONE → relay reads ONE → enabled

        // Need a wire for the control source to drive onto so the InputPort can read it
        // Let me restructure: control source → wire → InputPort (output side reads wire)
        // Source at (2,3,0), wire at (2,2,0), InputPort at (2,1,0) driverSideFace=DOWN(0)
        world.remove(Vector3i(2, 2, 0))
        world.placeWire(Vector3i(2, 2, 0))
        world.placeSource(Vector3i(2, 3, 0)) // control source

        val seeds = (0..4).map { Vector3i(it, 0, 0) }.toSet() +
            setOf(Vector3i(2, 1, 0), Vector3i(2, 2, 0), Vector3i(2, 3, 0))

        runTopologyWithControls(seeds)

        val relay = world.getComponent(Vector3i(2, 0, 0), TestComponentTypes.relayComponentType)!!
        assertTrue(relay.enabled, "Relay with control=ONE should be enabled")

        assertEquals(State4.ONE, getAnyNetValue(Vector3i(4, 0, 0)),
            "Lamp net should be ONE when relay is enabled")
    }

    @Test
    fun testRelayDisabled() {
        // Same layout but no control source → relay stays disabled
        world.placeSource(Vector3i(0, 0, 0))
        world.placeWire(Vector3i(1, 0, 0))
        world.placeRelay(Vector3i(2, 0, 0))
        world.placeWire(Vector3i(3, 0, 0))
        world.placeLamp(Vector3i(4, 0, 0))

        // No InputPort → relay has no control → disabled
        val seeds = (0..4).map { Vector3i(it, 0, 0) }.toSet()
        runTopologyWithControls(seeds)

        val relay = world.getComponent(Vector3i(2, 0, 0), TestComponentTypes.relayComponentType)!!
        assertFalse(relay.enabled, "Relay with no control should be disabled")

        // Power should NOT pass through
        assertEquals(State4.HIGH_Z, getAnyNetValue(Vector3i(4, 0, 0)),
            "Lamp net should be HIGH_Z when relay is disabled")
    }

    // ============================================================
    // MUX tests
    // ============================================================

    /**
     * Helper: sets up a MUX with S, A, B inputs and output wire + lamp.
     *
     * Layout (East-West pair axis):
     * MUX block A at (5,0,0) - closer to S (WEST narrow face)
     * MUX block B at (6,0,0) - farther from S (EAST narrow face)
     *
     * S: InputPort at (4,0,0) driverSideFace=EAST(5) → MUX A's WEST face
     * A: InputPort at (5,0,-1) driverSideFace=SOUTH(3) → MUX A's NORTH face
     * B: InputPort at (6,0,-1) driverSideFace=SOUTH(3) → MUX B's NORTH face
     * Output: SOUTH face of both blocks → wire at (5,0,1) and (6,0,1) → lamp at (5,0,2)
     */
    private fun setupMux(): Triple<Vector3i, Vector3i, Vector3i> {
        val muxA = Vector3i(5, 0, 0)
        val muxB = Vector3i(6, 0, 0)
        world.placeMux(muxA, muxB)

        // Output wires on SOUTH face
        world.placeWire(Vector3i(5, 0, 1))
        world.placeWire(Vector3i(6, 0, 1))
        world.placeLamp(Vector3i(5, 0, 2))

        return Triple(muxA, muxB, Vector3i(5, 0, 2)) // muxA, muxB, lampPos
    }

    private fun allMuxPositions(): Set<Vector3i> {
        val positions = mutableSetOf<Vector3i>()
        // MUX blocks
        positions.add(Vector3i(5, 0, 0))
        positions.add(Vector3i(6, 0, 0))
        // Output chain
        positions.add(Vector3i(5, 0, 1))
        positions.add(Vector3i(6, 0, 1))
        positions.add(Vector3i(5, 0, 2))
        // S input area
        positions.add(Vector3i(4, 0, 0))
        positions.add(Vector3i(3, 0, 0))
        // A input area
        positions.add(Vector3i(5, 0, -1))
        positions.add(Vector3i(5, 0, -2))
        // B input area
        positions.add(Vector3i(6, 0, -1))
        positions.add(Vector3i(6, 0, -2))
        return positions
    }

    @Test
    fun testMuxSelectA() {
        val (muxA, muxB, lampPos) = setupMux()

        // S = ZERO → select A (input 0)
        world.placeInputPort(Vector3i(4, 0, 0), driverSideFace = 5) // EAST → toward muxA
        // S source drives ZERO (source with input ONE inverts to ZERO, or we set driveState directly)
        // Simpler: place a wire for S to read and no source → HIGH_Z → disconnected
        // Actually for S=ZERO, we need a source driving ZERO
        // Place source with driveState=ZERO
        world.placeSource(Vector3i(3, 0, 0))
        world.getComponent(Vector3i(3, 0, 0), TestComponentTypes.powerSourceComponentType)!!.driveState = State4.ZERO

        // A input: power wire chain
        world.placeInputPort(Vector3i(5, 0, -1), driverSideFace = 3) // SOUTH → toward muxA
        world.placeWire(Vector3i(5, 0, -2))
        world.placeSource(Vector3i(5, 0, -3))

        val seeds = allMuxPositions() + setOf(Vector3i(5, 0, -3))
        runTopologyWithControls(seeds)

        assertEquals(State4.ONE, getAnyNetValue(lampPos),
            "MUX output should be ONE when S=ZERO selects A which has power")
    }

    @Test
    fun testMuxSelectB() {
        val (muxA, muxB, lampPos) = setupMux()

        // S = ONE → select B (input 1)
        world.placeInputPort(Vector3i(4, 0, 0), driverSideFace = 5)
        world.placeWire(Vector3i(3, 0, 0))
        world.placeSource(Vector3i(2, 0, 0)) // drives ONE → S=ONE

        // B input: power wire chain
        world.placeInputPort(Vector3i(6, 0, -1), driverSideFace = 3) // SOUTH → toward muxB
        world.placeWire(Vector3i(6, 0, -2))
        world.placeSource(Vector3i(6, 0, -3))

        val seeds = allMuxPositions() + setOf(Vector3i(2, 0, 0), Vector3i(6, 0, -3), Vector3i(6, 0, -2))
        runTopologyWithControls(seeds)

        assertEquals(State4.ONE, getAnyNetValue(lampPos),
            "MUX output should be ONE when S=ONE selects B which has power")
    }

    @Test
    fun testMuxIsolatesUnselected() {
        val (muxA, muxB, lampPos) = setupMux()

        // S = ZERO → select A
        world.placeInputPort(Vector3i(4, 0, 0), driverSideFace = 5)
        world.placeSource(Vector3i(3, 0, 0))
        world.getComponent(Vector3i(3, 0, 0), TestComponentTypes.powerSourceComponentType)!!.driveState = State4.ZERO

        // Power on B only (not A)
        world.placeInputPort(Vector3i(6, 0, -1), driverSideFace = 3)
        world.placeWire(Vector3i(6, 0, -2))
        world.placeSource(Vector3i(6, 0, -3))

        val seeds = allMuxPositions() + setOf(Vector3i(6, 0, -3), Vector3i(6, 0, -2))
        runTopologyWithControls(seeds)

        // A is selected but has no power → output should be HIGH_Z
        val v = getAnyNetValue(lampPos)
        assertTrue(v == State4.HIGH_Z || v == State4.HIGH_Z,
            "MUX output should be HIGH_Z when selected input (A) has no power. Got: $v")
    }

    @Test
    fun testMuxDisconnectedWhenNoS() {
        val (muxA, muxB, lampPos) = setupMux()

        // No S connected at all
        // Power on A
        world.placeInputPort(Vector3i(5, 0, -1), driverSideFace = 3)
        world.placeWire(Vector3i(5, 0, -2))
        world.placeSource(Vector3i(5, 0, -3))

        val seeds = allMuxPositions() + setOf(Vector3i(5, 0, -3), Vector3i(5, 0, -2))
        runTopologyWithControls(seeds)

        val mux = world.getComponent(muxA, TestComponentTypes.mux2PartComponentType)!!
        assertTrue(mux.isDisconnected, "MUX should be disconnected when no S")

        // Output should be HIGH_Z since MUX is disconnected
        val v = getAnyNetValue(lampPos)
        assertEquals(State4.HIGH_Z, v,
            "MUX output should be HIGH_Z when disconnected (no S)")
    }

    @Test
    fun testMuxInputPortPassThrough() {
        val (muxA, muxB, lampPos) = setupMux()

        // S = ZERO → select A
        world.placeInputPort(Vector3i(4, 0, 0), driverSideFace = 5)
        world.placeSource(Vector3i(3, 0, 0))
        world.getComponent(Vector3i(3, 0, 0), TestComponentTypes.powerSourceComponentType)!!.driveState = State4.ZERO

        // A input through InputPort
        world.placeInputPort(Vector3i(5, 0, -1), driverSideFace = 3) // SOUTH → toward muxA NORTH face
        world.placeWire(Vector3i(5, 0, -2))
        world.placeSource(Vector3i(5, 0, -3))

        val seeds = allMuxPositions() + setOf(Vector3i(5, 0, -3), Vector3i(5, 0, -2))
        runTopologyWithControls(seeds)

        assertEquals(State4.ONE, getAnyNetValue(lampPos),
            "Power through InputPort → MUX input → output should be ONE")
    }

    @Test
    fun testMuxToggle() {
        val (muxA, muxB, lampPos) = setupMux()

        // S starts at ZERO → select A
        world.placeInputPort(Vector3i(4, 0, 0), driverSideFace = 5)
        world.placeWire(Vector3i(3, 0, 0))
        val sSource = Vector3i(2, 0, 0)
        world.placeSource(sSource)
        world.getComponent(sSource, TestComponentTypes.powerSourceComponentType)!!.driveState = State4.ZERO

        // Power on A
        world.placeInputPort(Vector3i(5, 0, -1), driverSideFace = 3)
        world.placeWire(Vector3i(5, 0, -2))
        world.placeSource(Vector3i(5, 0, -3))

        val seeds = allMuxPositions() + setOf(sSource, Vector3i(5, 0, -3), Vector3i(5, 0, -2))
        runTopologyWithControls(seeds)

        assertEquals(State4.ONE, getAnyNetValue(lampPos),
            "With S=ZERO selecting A (powered), output should be ONE")

        // Now toggle S to ONE → select B (no power on B)
        world.getComponent(sSource, TestComponentTypes.powerSourceComponentType)!!.driveState = State4.ONE

        // Re-run topology
        queue = world.createQueue() // fresh queue
        runTopologyWithControls(seeds)

        // B has no power → output should be HIGH_Z
        val v = getAnyNetValue(lampPos)
        assertTrue(v == State4.HIGH_Z || v != State4.ONE,
            "After toggling S to ONE (selecting B with no power), output should not be ONE. Got: $v")
    }

    @Test
    fun testMuxABOnDifferentFaces() {
        // MUX pair on Y axis (UP-DOWN)
        val muxA = Vector3i(0, 5, 0) // A block (closer to S)
        val muxB = Vector3i(0, 6, 0) // B block
        world.placeMux(muxA, muxB)

        // S on SOUTH narrow face of A (or DOWN narrow face of A)
        // For Y-axis pair: narrow faces are DOWN(0) for A, UP(1) for B
        // S on A's DOWN face
        world.placeInputPort(Vector3i(0, 4, 0), driverSideFace = 1) // UP → toward muxA
        world.placeWire(Vector3i(0, 3, 0)) // S wire
        world.placeSource(Vector3i(0, 2, 0))
        world.getComponent(Vector3i(0, 2, 0), TestComponentTypes.powerSourceComponentType)!!.driveState = State4.ZERO

        // A input on NORTH face of muxA
        world.placeInputPort(Vector3i(0, 5, -1), driverSideFace = 3) // SOUTH → toward muxA
        world.placeWire(Vector3i(0, 5, -2))
        world.placeSource(Vector3i(0, 5, -3))

        // B input on WEST face of muxB
        world.placeInputPort(Vector3i(-1, 6, 0), driverSideFace = 5) // EAST → toward muxB
        world.placeWire(Vector3i(-2, 6, 0))
        world.placeSource(Vector3i(-3, 6, 0))

        // Output on SOUTH face
        world.placeWire(Vector3i(0, 5, 1))
        world.placeWire(Vector3i(0, 6, 1))
        world.placeLamp(Vector3i(0, 5, 2))

        val seeds = setOf(
            muxA, muxB,
            Vector3i(0, 4, 0), Vector3i(0, 3, 0), Vector3i(0, 2, 0),
            Vector3i(0, 5, -1), Vector3i(0, 5, -2), Vector3i(0, 5, -3),
            Vector3i(-1, 6, 0), Vector3i(-2, 6, 0), Vector3i(-3, 6, 0),
            Vector3i(0, 5, 1), Vector3i(0, 6, 1), Vector3i(0, 5, 2)
        )

        runTopologyWithControls(seeds)

        // S=ZERO → A selected, A has power → output ONE
        assertEquals(State4.ONE, getAnyNetValue(Vector3i(0, 5, 2)),
            "MUX with A on NORTH, B on WEST, S=ZERO selecting A → output should be ONE")
    }

    // ============================================================
    // Edge cases
    // ============================================================

    @Test
    fun testMuxLongWireToS() {
        val (muxA, muxB, lampPos) = setupMux()

        // S through 10 wires
        world.placeInputPort(Vector3i(4, 0, 0), driverSideFace = 5)
        for (i in 3 downTo -7) {
            world.placeWire(Vector3i(i, 0, 0))
        }
        world.placeSource(Vector3i(-8, 0, 0))
        world.getComponent(Vector3i(-8, 0, 0), TestComponentTypes.powerSourceComponentType)!!.driveState = State4.ZERO

        // A input
        world.placeInputPort(Vector3i(5, 0, -1), driverSideFace = 3)
        world.placeWire(Vector3i(5, 0, -2))
        world.placeSource(Vector3i(5, 0, -3))

        val seeds = allMuxPositions() +
            (3 downTo -8).map { Vector3i(it, 0, 0) }.toSet() +
            setOf(Vector3i(5, 0, -3), Vector3i(5, 0, -2))
        runTopologyWithControls(seeds)

        assertEquals(State4.ONE, getAnyNetValue(lampPos),
            "MUX should work with S driven through long wire chain")
    }

    @Test
    fun testOscillationForcesX() {
        // Create a feedback loop: Source → wire → InputPort → Source (same source reads its own output)
        // Source at (0,0,0)
        // Wire at (1,0,0)
        // InputPort at (-1,0,0) driverSideFace=EAST(5) → toward source
        // Wire at (-2,0,0) connected to source's output AND InputPort's probe side

        // Simpler oscillation: single source that reads its own output
        // Source at (0,0,0) drives onto wire at (1,0,0)
        // InputPort at (-1,0,0) driverSideFace=EAST(5) pointing at source
        // Wire at (-2,0,0) connects back to source output via a loop

        // Simplest: Source → Wire (east) and Source ← InputPort ← Wire (west)
        // where the wire loops around
        world.placeSource(Vector3i(0, 0, 0))
        world.placeWire(Vector3i(1, 0, 0))
        world.placeWire(Vector3i(2, 0, 0))
        world.placeWire(Vector3i(2, 0, -1))
        world.placeWire(Vector3i(1, 0, -1))
        world.placeWire(Vector3i(0, 0, -1))
        world.placeInputPort(Vector3i(-1, 0, 0), driverSideFace = 5) // EAST → source
        world.placeWire(Vector3i(-1, 0, -1))
        world.placeWire(Vector3i(0, 0, -1)) // already placed above, skip duplicate

        // Connect InputPort output side to the wire loop
        // InputPort at (-1,0,0), output face = WEST(4), probes block at (-2,0,0)
        world.placeWire(Vector3i(-2, 0, 0))
        world.placeWire(Vector3i(-2, 0, -1))
        // Connect the loop: wire chain from source output back to InputPort probe
        // Source (0,0,0) → wire(1,0,0) → wire(2,0,0) → wire(2,0,-1) → wire(1,0,-1) → wire(0,0,-1) → wire(-1,0,-1) → wire(-2,0,-1) → wire(-2,0,0) → [InputPort probe side]

        val seeds = setOf(
            Vector3i(0, 0, 0), Vector3i(1, 0, 0), Vector3i(2, 0, 0),
            Vector3i(2, 0, -1), Vector3i(1, 0, -1), Vector3i(0, 0, -1),
            Vector3i(-1, 0, 0), Vector3i(-1, 0, -1),
            Vector3i(-2, 0, 0), Vector3i(-2, 0, -1)
        )
        runTopology(seeds)

        // The source reads its own output (inverted), which changes its output,
        // which changes its input... oscillation → should force UNKNOWN_X
        val v = getAnyNetValue(Vector3i(1, 0, 0))
        assertEquals(State4.UNKNOWN_X, v,
            "Oscillating feedback loop should force UNKNOWN_X")
    }
}
