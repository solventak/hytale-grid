// -----------------------------
// Model
// -----------------------------

enum class WireType {
    Power,
    Signal
}

sealed class WireProperties {

    data class Wire(val type: WireType) : WireProperties()

    object NonWire : WireProperties()

    // -----------------------------
    // Serialization
    // -----------------------------

    /**
     * Serialize to a JSON-compatible value.
     *
     * Wire      -> { "WireType": "Signal" }
     * NonWire  -> "NonWire"
     */
    fun toJson(): Any =
        when (this) {
            is Wire ->
                mapOf("WireType" to type.name)

            NonWire ->
                "NonWire"
        }

    companion object {

        // -----------------------------
        // Deserialization
        // -----------------------------

        /**
         * Deserialize from a JSON-compatible value.
         *
         * Returns null if:
         * - value is missing
         * - shape is invalid
         * - enum value is unknown
         */
        fun fromJson(value: Any?): WireProperties? {
            return when (value) {

                null -> null

                is String ->
                    if (value == "NonWire") NonWire else null

                is Map<*, *> -> {
                    val typeName = value["WireType"] as? String
                    val type = typeName
                        ?.let { runCatching { WireType.valueOf(it) }.getOrNull() }

                    type?.let { Wire(it) }
                }

                else -> null
            }
        }
    }
}
