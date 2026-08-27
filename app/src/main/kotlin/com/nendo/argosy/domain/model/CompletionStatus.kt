package com.nendo.argosy.domain.model

/**
 * How far through a game the user says they are. [apiValue] is the wire value; the display
 * label lives in `ui/common/CompletionStatusUi.kt`, because this layer must not import `R`.
 */
enum class CompletionStatus(
    val apiValue: String
) {
    INCOMPLETE("incomplete"),
    FINISHED("finished"),
    COMPLETED_100("completed_100"),
    RETIRED("retired"),
    NEVER_PLAYING("never_playing");

    companion object {
        fun fromApiValue(value: String?): CompletionStatus? =
            if (value == null) null else entries.find { it.apiValue == value }

        fun cycleNext(current: String?): String {
            val currentStatus = fromApiValue(current) ?: INCOMPLETE
            return entries[(currentStatus.ordinal + 1).mod(entries.size)].apiValue
        }

        fun cyclePrev(current: String?): String {
            val currentStatus = fromApiValue(current) ?: INCOMPLETE
            return entries[(currentStatus.ordinal - 1).mod(entries.size)].apiValue
        }
    }
}
