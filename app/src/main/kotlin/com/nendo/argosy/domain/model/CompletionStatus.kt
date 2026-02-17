package com.nendo.argosy.domain.model

enum class CompletionStatus(
    val apiValue: String,
    val label: String
) {
    INCOMPLETE("incomplete", "Incomplete"),
    FINISHED("finished", "Finished"),
    COMPLETED_100("completed_100", "100%"),
    RETIRED("retired", "Retired"),
    NEVER_PLAYING("never_playing", "Won't Play");

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
