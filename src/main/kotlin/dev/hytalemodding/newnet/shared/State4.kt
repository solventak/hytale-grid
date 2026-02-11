package dev.hytalemodding.newnet.shared

/**
 * Four-state logic values used for multi-driver power network resolution.
 * 
 * This implements Verilog-style tri-state logic, allowing multiple drivers
 * on the same network while detecting conflicts.
 * 
 * States:
 * - **ZERO**: Logic low (off, false, 0)
 * - **ONE**: Logic high (on, true, 1)
 * - **HIGH_Z**: High-impedance (floating, no driver, tri-state)
 * - **UNKNOWN_X**: Unknown/conflict (error state, multiple conflicting drivers)
 * 
 * Usage:
 * - PowerSource blocks drive ZERO or ONE (or UNKNOWN_X on error)
 * - Networks with no drivers default to HIGH_Z (floating)
 * - Networks with conflicting drivers (e.g., one drives ZERO, another ONE) resolve to UNKNOWN_X
 * - UNKNOWN_X networks trigger "magic smoke" (all connected blocks are destroyed)
 */
enum class State4 {
    /** Logic low (off) */
    ZERO,
    /** Logic high (on) */
    ONE,
    /** High-impedance (floating, no driver) */
    HIGH_Z,
    /** Unknown/conflict (error state) */
    UNKNOWN_X;

    companion object {
        /**
         * Resolves a network's value from multiple driver contributions.
         * 
         * Resolution rules:
         * 1. Filter out HIGH_Z (tri-state drivers don't affect resolution)
         * 2. If no non-Z drivers remain → HIGH_Z (floating)
         * 3. If any driver is UNKNOWN_X → UNKNOWN_X (error propagates)
         * 4. If all non-Z drivers agree (all ZERO or all ONE) → that value
         * 5. If non-Z drivers conflict (mix of ZERO and ONE) → UNKNOWN_X (short circuit)
         * 
         * Examples:
         * - [] → HIGH_Z
         * - [ONE] → ONE
         * - [ONE, HIGH_Z] → ONE
         * - [ONE, ONE] → ONE
         * - [ZERO, ONE] → UNKNOWN_X (conflict)
         * - [ONE, UNKNOWN_X] → UNKNOWN_X (error propagates)
         * 
         * @param drivers Collection of 4-state values from all drivers on the network
         * @return The resolved network value
         */
        fun resolve(drivers: Collection<State4>): State4 {
            // Ignore tri-state (floating) drivers
            val nonZ = drivers.filter { it != HIGH_Z }
            if (nonZ.isEmpty()) return HIGH_Z  // No active drivers
            if (nonZ.any { it == UNKNOWN_X }) return UNKNOWN_X  // Error propagates
            val distinct = nonZ.toSet()
            // All drivers agree → use that value; conflict → UNKNOWN_X
            return if (distinct.size == 1) distinct.first() else UNKNOWN_X
        }
    }
}
