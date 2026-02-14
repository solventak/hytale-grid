package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.GridPlugin

/**
 * Component for input probe blocks that read network state.
 * 
 * InputPort is the bridge between power networks and control logic:
 * - Has NO PowerConnectable or PowerNetIds (not part of any network)
 * - Acts as a transparent probe between two faces
 * - One face (driverSideFace) points toward a PowerSource or Relay
 * - Opposite face (output face) probes the adjacent block's network
 * 
 * Configuration:
 * - driverSideFace is auto-detected during placement (PowerBlockAddedSystem)
 * - If no adjacent PowerSource/Relay found on any face → block is destroyed
 * - driverSideFace must point toward the controlling block
 * 
 * Operation:
 * 1. PowerSource/Relay reads the InputPort's probed network value
 * 2. Probed network is found by checking the block adjacent to the output face
 * 3. That block's PowerNetIds component is read to get the network ID
 * 4. The 4-state value is looked up in powerNetValueCache
 * 
 * Visual state:
 * - "On" when probed network value is ONE
 * - "default" otherwise
 * 
 * Example:
 * ```
 *  Wire (net A) ← [InputPort] → PowerSource
 *    (probed)      (probe)       (reads net A value)
 * ```
 * 
 * The PowerSource inverts net A's value and drives it onto its connected nets.
 */
class InputPort : Component<ChunkStore> {
    /**
     * Face index (0-5) pointing toward the adjacent PowerSource or Relay (driver side).
     * The opposite face is the output face that probes a network.
     * Auto-configured by PowerBlockAddedSystem when the block is placed.
     */
    var driverSideFace: Int = 0

    companion object {
        @JvmField
        val CODEC: BuilderCodec<InputPort> =
            BuilderCodec.builder(InputPort::class.java) { InputPort() }.build()
    }

    fun getComponentType(): ComponentType<ChunkStore, InputPort> {
        return GridPlugin.inputPortComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return InputPort().also { it.driverSideFace = this.driverSideFace }
    }
}
