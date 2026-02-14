package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.GridPlugin

/**
 * Component for relay blocks (controlled power switches).
 * 
 * Relays alter network topology based on control signals read via InputPorts:
 * - **Control faces**: Faces with adjacent InputPorts (whose driverSideFace points at the relay)
 * - **Conduction faces**: All other connectable faces
 * 
 * When **enabled**:
 * - All conduction faces are star-connected (same network, power flows through)
 * - Control faces remain isolated
 * 
 * When **disabled**:
 * - No internal connectivity (relay acts as 6 isolated network endpoints)
 * - Control faces still isolated
 * 
 * Control evaluation (pure OR logic):
 * - No control faces → disabled, no fault
 * - Any control net is ONE → enabled
 * - Any control net is UNKNOWN_X → disabled with controlFault=true (safe-off)
 * - All control nets ZERO/HIGH_Z → disabled
 * 
 * Relay state changes trigger topology rebuild (outer loop in TopologySystem).
 * This allows cascading logic (relay enables → network merges → another relay toggles).
 * 
 * Visual state:
 * - "On" when enabled=true
 * - "default" when enabled=false
 * 
 * Example:
 * ```
 *  Wire → InputPort → [Relay] → Wire → Lamp
 *         (control)    (conducts when control=ONE)
 * ```
 */
class Relay : Component<ChunkStore> {
    /**
     * Current conduction state.
     * true = relay conducts (conduction faces star-connected)
     * false = relay does not conduct (no internal connectivity)
     */
    var enabled: Boolean = false
    
    /**
     * Set to true if any control InputPort reads UNKNOWN_X.
     * Relay goes to safe-off state (disabled) when faulted.
     */
    var controlFault: Boolean = false
    
    /**
     * Previous topology round's enabled state.
     * Used to detect toggles that require topology rebuild.
     */
    var lastEnabled: Boolean = false

    companion object {
        @JvmField
        val CODEC: BuilderCodec<Relay> =
            BuilderCodec.builder(Relay::class.java) { Relay() }.build()
    }

    fun getComponentType(): ComponentType<ChunkStore, Relay> {
        return GridPlugin.relayComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return Relay().also {
            it.enabled = this.enabled
            it.controlFault = this.controlFault
            it.lastEnabled = this.lastEnabled
        }
    }
}
