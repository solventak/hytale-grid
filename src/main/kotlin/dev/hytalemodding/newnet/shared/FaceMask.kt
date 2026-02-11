package dev.hytalemodding.newnet.shared

enum class FaceMask(val bit: Int) {
    DOWN(1 shl 0),
    UP(1 shl 1),
    NORTH(1 shl 2),
    SOUTH(1 shl 3),
    WEST(1 shl 4),
    EAST(1 shl 5);

    companion object {
        const val ALL: Int = (1 shl 6) - 1
    }
}
