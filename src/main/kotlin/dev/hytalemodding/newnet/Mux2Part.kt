package dev.hytalemodding.newnet

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import dev.hytalemodding.ExamplePlugin

/**
 * Component for one half of a 2:1 MUX multiblock.
 *
 * A complete MUX consists of two adjacent Mux2Part blocks forming a 1×2 unit.
 * The MUX is a selective relay: it connects its output face to either input A's net
 * or input B's net based on the select signal S. It does NOT drive power — it passes
 * it through like a relay.
 *
 * ## Physical Layout
 * ```
 *         S (InputPort on narrow side)
 *         │
 *    ┌────┴────┐
 *    │ MUX  MUX│  ← two Mux2Part blocks side by side
 *    └────┬────┘
 *   IP(A) │ IP(B)  ← InputPorts on fat end (A closer to S, B farther)
 *         │
 *     (opposite fat end)
 *         Y  ← output, PowerConnectable face (one InputPort allowed on either block)
 * ```
 *
 * ## Port Assignment
 * - **S**: InputPort on the narrow side (the side with only one MUX block face exposed)
 * - **A (input 0)**: InputPort on the fat end, on the block closer to S
 * - **B (input 1)**: InputPort on the fat end, on the block farther from S
 * - **Y (output)**: Opposite fat end — conducts to whichever input is selected
 *
 * ## States
 * - **Incomplete**: Only one MUX block placed. Inert — does nothing.
 *   Self-destructs if any non-MUX block connects to it.
 * - **Complete**: Two MUX blocks adjacent. Activates and accepts InputPort connections.
 *   Participates in topology as a selective relay.
 *
 * ## Topology Behavior
 * - S=0 (ZERO) → Y faces connect to A's net
 * - S=1 (ONE) → Y faces connect to B's net
 * - S=HIGH_Z → disconnected (no conduction)
 * - S=UNKNOWN_X → disconnected, controlFault=true (safe-off)
 * - selectedInput changes → topology rebuild (like relay toggle)
 */
class Mux2Part : Component<ChunkStore> {
    /**
     * Position of the paired MUX block, or null if incomplete.
     * Set during placement when two MUX blocks become adjacent.
     */
    var pairedPos: Vector3i? = null

    /**
     * Whether this MUX multiblock is complete (has a valid pair).
     */
    var isComplete: Boolean = false

    /**
     * Which input is currently selected: 0=A (closer to S), 1=B (farther from S).
     * Only meaningful when isComplete=true and S is driven.
     */
    var selectedInput: Int = 0

    /**
     * Previous topology round's selectedInput.
     * Used to detect changes that require topology rebuild.
     */
    var lastSelectedInput: Int = 0

    /**
     * True if S reads UNKNOWN_X — MUX goes to safe-off (disconnected).
     */
    var controlFault: Boolean = false

    /**
     * True if S is HIGH_Z (floating) — MUX is disconnected.
     */
    var isDisconnected: Boolean = true

    /**
     * Previous topology round's disconnected state.
     * Used to detect changes that require topology rebuild.
     */
    var lastIsDisconnected: Boolean = true

    /**
     * The face axis along which the two MUX blocks are paired.
     * This is the "fat" axis. -1 if not yet paired.
     * Face index of the face on THIS block that points toward the paired block.
     */
    var pairFace: Int = -1

    companion object {
        @JvmField
        val CODEC: BuilderCodec<Mux2Part> =
            BuilderCodec.builder(Mux2Part::class.java) { Mux2Part() }.build()
    }

    fun getComponentType(): ComponentType<ChunkStore, Mux2Part> {
        return ExamplePlugin.mux2PartComponentType
    }

    override fun clone(): Component<ChunkStore> {
        return Mux2Part().also {
            it.pairedPos = this.pairedPos
            it.isComplete = this.isComplete
            it.selectedInput = this.selectedInput
            it.lastSelectedInput = this.lastSelectedInput
            it.controlFault = this.controlFault
            it.isDisconnected = this.isDisconnected
            it.lastIsDisconnected = this.lastIsDisconnected
            it.pairFace = this.pairFace
        }
    }
}
