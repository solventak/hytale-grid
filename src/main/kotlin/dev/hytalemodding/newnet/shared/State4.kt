package dev.hytalemodding.newnet.shared

enum class State4 {
    ZERO,
    ONE,
    HIGH_Z,
    UNKNOWN_X;

    companion object {
        fun resolve(drivers: Collection<State4>): State4 {
            val nonZ = drivers.filter { it != HIGH_Z }
            if (nonZ.isEmpty()) return HIGH_Z
            if (nonZ.any { it == UNKNOWN_X }) return UNKNOWN_X
            val distinct = nonZ.toSet()
            return if (distinct.size == 1) distinct.first() else UNKNOWN_X
        }
    }
}
