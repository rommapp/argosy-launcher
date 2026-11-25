package com.nendo.argosy.data.preferences

enum class MusicRepeatMode {
    ALL, ONE, OFF;

    val next: MusicRepeatMode get() = entries[(ordinal + 1).mod(entries.size)]

    companion object {
        fun fromString(value: String?): MusicRepeatMode = entries.find { it.name == value } ?: ALL
    }
}
