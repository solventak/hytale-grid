package dev.hytalemodding.newnet.shared

/**
 * Enum representing the 6 block faces with their bit positions for masking.
 * 
 * Used for:
 * - PowerConnectable.facesMask (which faces can connect to networks)
 * - Relay control face detection (which faces have InputPorts)
 * - Face iteration and bit manipulation
 * 
 * Face indices match Hytale's internal face ordering:
 * - 0 = DOWN (-Y)
 * - 1 = UP (+Y)
 * - 2 = NORTH (-Z)
 * - 3 = SOUTH (+Z)
 * - 4 = WEST (-X)
 * - 5 = EAST (+X)
 * 
 * Bit manipulation example:
 * ```kotlin
 * val mask = FaceMask.UP.bit or FaceMask.DOWN.bit  // 0b000011 (top and bottom)
 * val hasUp = (mask and FaceMask.UP.bit) != 0      // true
 * val hasNorth = (mask and FaceMask.NORTH.bit) != 0 // false
 * ```
 */
enum class FaceMask(val bit: Int) {
    /** Bottom face (-Y direction), bit 0 */
    DOWN(1 shl 0),
    /** Top face (+Y direction), bit 1 */
    UP(1 shl 1),
    /** North face (-Z direction), bit 2 */
    NORTH(1 shl 2),
    /** South face (+Z direction), bit 3 */
    SOUTH(1 shl 3),
    /** West face (-X direction), bit 4 */
    WEST(1 shl 4),
    /** East face (+X direction), bit 5 */
    EAST(1 shl 5);

    companion object {
        /** Bitmask with all 6 faces set (0b111111 = 63) */
        const val ALL: Int = (1 shl 6) - 1
    }
}
