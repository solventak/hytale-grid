package dev.hytalemodding.newnet.shared

/**
 * Enum for different types of power networks.
 * 
 * Currently only POWER networks are implemented. This enum is reserved for
 * future expansion (e.g., signal networks, fluid networks, data networks).
 * 
 * In the current implementation, all networks are implicitly POWER networks.
 * This enum exists as a placeholder for type-safe network classification in
 * future multi-type network systems.
 */
enum class NetKind {
    /** Power networks (4-state logic with multi-driver resolution) */
    POWER
}
