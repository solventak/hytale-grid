package dev.hytalemodding.wire

import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation
import java.util.logging.Logger

private val lookupLogger = Logger.getLogger("WireLookupTable")

/**
 * Represents the 6 possible connection directions for a wire block.
 * Each boolean indicates whether a connection exists in that direction.
 */
data class WireConnections(
    val up: Boolean = false,
    val down: Boolean = false,
    val north: Boolean = false,
    val east: Boolean = false,
    val south: Boolean = false,
    val west: Boolean = false
) {
    /**
     * Convert to a 6-bit integer for lookup table indexing.
     * Bit order: U D N E S W (bit 5 to bit 0)
     */
    fun toIndex(): Int {
        var index = 0
        if (up) index = index or 0b100000
        if (down) index = index or 0b010000
        if (north) index = index or 0b001000
        if (east) index = index or 0b000100
        if (south) index = index or 0b000010
        if (west) index = index or 0b000001
        return index
    }

    companion object {
        fun fromIndex(index: Int): WireConnections {
            return WireConnections(
                up = (index and 0b100000) != 0,
                down = (index and 0b010000) != 0,
                north = (index and 0b001000) != 0,
                east = (index and 0b000100) != 0,
                south = (index and 0b000010) != 0,
                west = (index and 0b000001) != 0
            )
        }
    }
}

/**
 * The 24 canonical wire model variants.
 *
 * Naming: Wire_[vertical]_[horizontal]
 * - Vertical: XX (none), UX (up), XD (down), UD (both)
 * - Horizontal: 0 (none), 1 (single/N), 2a (adjacent/NE), 2o (opposite/NS), 3 (three/NEW), 4 (all/NESW)
 */
enum class WireVariant(val modelName: String) {
    // No vertical connections (XX)
    WIRE_XX_0("Wire_XX_0"),   // center only
    WIRE_XX_1("Wire_XX_1"),   // N (single)
    WIRE_XX_2A("Wire_XX_2a"), // NE (adjacent pair)
    WIRE_XX_2O("Wire_XX_2o"), // NS (opposite pair)
    WIRE_XX_3("Wire_XX_3"),   // NEW (three-way T)
    WIRE_XX_4("Wire_XX_4"),   // NESW (all four)

    // Up only (UX)
    WIRE_UX_0("Wire_UX_0"),
    WIRE_UX_1("Wire_UX_1"),
    WIRE_UX_2A("Wire_UX_2a"),
    WIRE_UX_2O("Wire_UX_2o"),
    WIRE_UX_3("Wire_UX_3"),
    WIRE_UX_4("Wire_UX_4"),

    // Down only (XD)
    WIRE_XD_0("Wire_XD_0"),
    WIRE_XD_1("Wire_XD_1"),
    WIRE_XD_2A("Wire_XD_2a"),
    WIRE_XD_2O("Wire_XD_2o"),
    WIRE_XD_3("Wire_XD_3"),
    WIRE_XD_4("Wire_XD_4"),

    // Both up and down (UD)
    WIRE_UD_0("Wire_UD_0"),
    WIRE_UD_1("Wire_UD_1"),
    WIRE_UD_2A("Wire_UD_2a"),
    WIRE_UD_2O("Wire_UD_2o"),
    WIRE_UD_3("Wire_UD_3"),
    WIRE_UD_4("Wire_UD_4");
}

/**
 * Result of looking up a wire connection state.
 */
data class WireVariantResult(
    val variant: WireVariant,
    val yawRotation: Rotation
) {
    /** Get the block type ID */
    fun getBlockTypeId(wireType: String): String = "${variant.modelName}_$wireType"
}

/**
 * Lookup table mapping all 64 connection states to their canonical variant + yaw rotation.
 */
object WireLookupTable {

    private val lookupTable: Array<WireVariantResult> = Array(64) { index ->
        computeVariant(WireConnections.fromIndex(index))
    }

    /**
     * Get the wire variant and rotation for a given connection state.
     */
    fun lookup(connections: WireConnections): WireVariantResult {
        return lookupTable[connections.toIndex()]
    }

    /**
     * Get the wire variant and rotation for a given index (0-63).
     */
    fun lookup(index: Int): WireVariantResult {
        return lookupTable[index]
    }

    /**
     * Get the block type ID for placing.
     */
    fun getBlockTypeId(connections: WireConnections): String {
        return lookup(connections).variant.modelName
    }

    /**
     * Compute the canonical variant and required yaw rotation for a connection state.
     */
    private fun computeVariant(conn: WireConnections): WireVariantResult {
        // Determine vertical component (which set of 6 variants to use)
        val verticalBase = when {
            conn.up && conn.down -> WireVariant.WIRE_UD_0
            conn.up -> WireVariant.WIRE_UX_0
            conn.down -> WireVariant.WIRE_XD_0
            else -> WireVariant.WIRE_XX_0
        }

        // Determine horizontal pattern and rotation
        val (horizontalOffset, rotation) = computeHorizontalPattern(conn.north, conn.east, conn.south, conn.west)

        // Combine vertical base with horizontal offset
        val variantIndex = verticalBase.ordinal + horizontalOffset
        val variant = WireVariant.entries[variantIndex]

        return WireVariantResult(variant, rotation)
    }

    /**
     * Compute the horizontal pattern type (0-5) and required yaw rotation.
     * Returns Pair(patternOffset, rotation)
     *
     * Pattern offsets:
     * 0 = none (center only)
     * 1 = single connection (canonical: North)
     * 2 = adjacent pair (canonical: North-East)
     * 3 = opposite pair (canonical: North-South)
     * 4 = three-way (canonical: North-East-West, missing South)
     * 5 = all four
     */
    private fun computeHorizontalPattern(n: Boolean, e: Boolean, s: Boolean, w: Boolean): Pair<Int, Rotation> {
        val count = listOf(n, e, s, w).count { it }

        return when (count) {
            0 -> Pair(0, Rotation.None)

            1 -> {
                // Single connection - canonical is North
                val rotation = when {
                    n -> Rotation.None
                    e -> Rotation.TwoSeventy
                    s -> Rotation.OneEighty
                    w -> Rotation.Ninety
                    else -> Rotation.None
                }
                Pair(1, rotation)
            }

            2 -> {
                // Check if opposite or adjacent
                if ((n && s) || (e && w)) {
                    // Opposite pair - canonical is North-South
                    val rotation = if (n && s) Rotation.None else Rotation.Ninety
                    Pair(3, rotation) // 2o pattern is at offset 3
                } else {
                    // Adjacent pair - canonical is North-East
                    val rotation = when {
                        n && e -> Rotation.None
                        e && s -> Rotation.TwoSeventy
                        s && w -> Rotation.OneEighty
                        w && n -> Rotation.Ninety
                        else -> Rotation.None
                    }
                    Pair(2, rotation) // 2a pattern is at offset 2
                }
            }

            3 -> {
                // Three-way - canonical is NEW (missing South)
                val rotation = when {
                    !s -> Rotation.None       // NEW, missing S
                    !w -> Rotation.TwoSeventy // NES, missing W (was Ninety, fixed 180° flip)
                    !n -> Rotation.OneEighty  // ESW, missing N
                    !e -> Rotation.Ninety     // SWN, missing E (was TwoSeventy, fixed 180° flip)
                    else -> Rotation.None
                }
                Pair(4, rotation)
            }

            4 -> Pair(5, Rotation.None)

            else -> Pair(0, Rotation.None)
        }
    }

    /**
     * Print the full lookup table for debugging/verification.
     */
    fun printTable() {
        println("Wire Lookup Table (64 entries)")
        println("=".repeat(60))
        println("Index | U D N E S W | Variant      | Rotation")
        println("-".repeat(60))

        for (i in 0 until 64) {
            val conn = WireConnections.fromIndex(i)
            val result = lookupTable[i]
            val bits = listOf(conn.up, conn.down, conn.north, conn.east, conn.south, conn.west)
                .map { if (it) "1" else "0" }
                .joinToString(" ")
            println("%5d | %s | %-12s | %s".format(
                i, bits, result.variant.modelName, result.yawRotation.name
            ))
        }
    }
}
