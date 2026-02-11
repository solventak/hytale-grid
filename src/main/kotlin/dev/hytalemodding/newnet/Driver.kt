package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin
import dev.hytalemodding.newnet.shared.State4

/**
 * Component marking a block as a power source (inverting driver).
 * 
 * PowerSource blocks act as multi-input NOR gates:
 * - Scan adjacent InputPort blocks whose driverSideFace points toward this source
 * - Read the 4-state value from each InputPort's probed network
 * - Compute inverted output:
 *   - No inputs → ONE (default-on)
 *   - Any input ONE → ZERO (invert)
 *   - All inputs ZERO/HIGH_Z → ONE (invert)
 *   - Any input UNKNOWN_X or conflict → UNKNOWN_X
 * 
 * The driveState is applied to all connectable faces of the source block.
 * Sources are the only blocks that actively drive networks; all other blocks
 * (wires, relays, lamps) passively route or consume power.
 * 
 * Example circuit:
 * ```
 *  Wire → InputPort → [PowerSource] → Wire → Lamp
 *                      (inverts signal)
 * ```
 */
class PowerSource : Component<ChunkStore> {
    /**
     * Current 4-state drive output (ZERO, ONE, HIGH_Z, UNKNOWN_X).
     * Computed each delta-cycle by evaluateSources().
     */
    var driveState: State4 = State4.ONE
    
    /**
     * Previous delta-cycle's drive state.
     * Used to detect oscillation during delta-cycle evaluation.
     */
    var lastDriveState: State4 = State4.ONE

    companion object {
        @JvmField
        val CODEC: BuilderCodec<PowerSource> =
            BuilderCodec.builder(PowerSource::class.java) { PowerSource() }.build()
    }

    fun getComponentType(): ComponentType<ChunkStore, PowerSource> {
        return ExamplePlugin.powerSourceComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return PowerSource().also {
            it.driveState = this.driveState
            it.lastDriveState = this.lastDriveState
        }
    }
}
