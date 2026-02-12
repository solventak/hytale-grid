package dev.hytalemodding.newnet

import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

import dev.hytalemodding.newnet.shared.State4

/**
 * Mock WorldAccess implementation for unit testing power network topology.
 *
 * Backed by a HashMap of (position, componentType) → component.
 * Provides helper methods for placing common block types.
 */
class MockPowerWorld : WorldAccess {

    private val components = HashMap<Long, Component<ChunkStore>>()

    private fun key(pos: Vector3i, type: ComponentType<ChunkStore, *>): Long {
        // Combine position hash and type identity into a unique key
        val posKey = (pos.x.toLong() * 73856093L) xor (pos.y.toLong() * 19349663L) xor (pos.z.toLong() * 83492791L)
        return posKey * 31 + System.identityHashCode(type).toLong()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Component<ChunkStore>> getComponent(pos: Vector3i, type: ComponentType<ChunkStore, T>): T? {
        return components[key(pos, type)] as? T
    }

    override fun <T : Component<ChunkStore>> setComponent(pos: Vector3i, type: ComponentType<ChunkStore, T>, value: T) {
        components[key(pos, type)] = value
    }

    // --- Helper methods for placing blocks ---

    fun placeWire(pos: Vector3i) {
        val conn = PowerConnectable().apply { facesMask = 0x3F }
        setComponent(pos, TestComponentTypes.powerConnectableComponentType, conn)
        setComponent(pos, TestComponentTypes.powerNetIdsComponentType, PowerNetIds())
        setComponent(pos, TestComponentTypes.powerWireComponentType, PowerWire())
        setComponent(pos, TestComponentTypes.visualStateComponentType, VisualState())
    }

    fun placeSource(pos: Vector3i) {
        val conn = PowerConnectable().apply { facesMask = 0x3F }
        setComponent(pos, TestComponentTypes.powerConnectableComponentType, conn)
        setComponent(pos, TestComponentTypes.powerNetIdsComponentType, PowerNetIds())
        setComponent(pos, TestComponentTypes.powerSourceComponentType, PowerSource().apply { driveState = State4.ONE })
        setComponent(pos, TestComponentTypes.visualStateComponentType, VisualState())
    }

    fun placeLamp(pos: Vector3i) {
        val conn = PowerConnectable().apply { facesMask = 0x3F }
        setComponent(pos, TestComponentTypes.powerConnectableComponentType, conn)
        setComponent(pos, TestComponentTypes.powerNetIdsComponentType, PowerNetIds())
        setComponent(pos, TestComponentTypes.lampComponentType, Lamp())
        setComponent(pos, TestComponentTypes.visualStateComponentType, VisualState())
    }

    fun placeRelay(pos: Vector3i) {
        val conn = PowerConnectable().apply { facesMask = 0x3F }
        setComponent(pos, TestComponentTypes.powerConnectableComponentType, conn)
        setComponent(pos, TestComponentTypes.powerNetIdsComponentType, PowerNetIds())
        setComponent(pos, TestComponentTypes.relayComponentType, Relay())
        setComponent(pos, TestComponentTypes.visualStateComponentType, VisualState())
    }

    fun placeInputPort(pos: Vector3i, driverSideFace: Int) {
        setComponent(pos, TestComponentTypes.inputPortComponentType, InputPort().apply {
            this.driverSideFace = driverSideFace
        })
        setComponent(pos, TestComponentTypes.visualStateComponentType, VisualState())
    }

    fun placeMux(pos1: Vector3i, pos2: Vector3i) {
        // Determine pair face from relative positions
        val dx = pos2.x - pos1.x
        val dy = pos2.y - pos1.y
        val dz = pos2.z - pos1.z
        val pairFace1 = when {
            dx == 1 -> 5  // EAST
            dx == -1 -> 4 // WEST
            dy == 1 -> 1  // UP
            dy == -1 -> 0 // DOWN
            dz == 1 -> 3  // SOUTH
            dz == -1 -> 2 // NORTH
            else -> error("MUX blocks must be adjacent: $pos1 $pos2")
        }
        val pairFace2 = OPPOSITE_FACE[pairFace1]

        val mux1 = Mux2Part().apply {
            pairedPos = pos2
            pairFace = pairFace1
            isComplete = true
            isDisconnected = true
        }
        val mux2 = Mux2Part().apply {
            pairedPos = pos1
            pairFace = pairFace2
            isComplete = true
            isDisconnected = true
        }

        val conn1 = PowerConnectable().apply { facesMask = 0x3F }
        val conn2 = PowerConnectable().apply { facesMask = 0x3F }

        setComponent(pos1, TestComponentTypes.powerConnectableComponentType, conn1)
        setComponent(pos1, TestComponentTypes.powerNetIdsComponentType, PowerNetIds())
        setComponent(pos1, TestComponentTypes.mux2PartComponentType, mux1)
        setComponent(pos1, TestComponentTypes.visualStateComponentType, VisualState())

        setComponent(pos2, TestComponentTypes.powerConnectableComponentType, conn2)
        setComponent(pos2, TestComponentTypes.powerNetIdsComponentType, PowerNetIds())
        setComponent(pos2, TestComponentTypes.mux2PartComponentType, mux2)
        setComponent(pos2, TestComponentTypes.visualStateComponentType, VisualState())
    }

    fun remove(pos: Vector3i) {
        // Remove all known component types at this position
        val types = listOf(
            TestComponentTypes.powerConnectableComponentType,
            TestComponentTypes.powerNetIdsComponentType,
            TestComponentTypes.powerSourceComponentType,
            TestComponentTypes.powerWireComponentType,
            TestComponentTypes.lampComponentType,
            TestComponentTypes.relayComponentType,
            TestComponentTypes.inputPortComponentType,
            TestComponentTypes.visualStateComponentType,
            TestComponentTypes.mux2PartComponentType,
        )
        for (type in types) {
            components.remove(key(pos, type))
        }
    }

    fun createQueue(): StateChangeEventQueue = StateChangeEventQueue()
}
