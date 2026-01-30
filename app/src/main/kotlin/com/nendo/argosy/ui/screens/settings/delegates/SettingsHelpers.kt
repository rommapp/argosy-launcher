package com.nendo.argosy.ui.screens.settings.delegates

inline fun <reified T : Enum<T>> cycleEnum(current: T, direction: Int = 1): T {
    val values = enumValues<T>()
    val currentIndex = values.indexOf(current)
    return values[(currentIndex + direction).mod(values.size)]
}

fun cycleInList(current: Int, values: List<Int>, direction: Int = 1): Int {
    val currentIndex = values.indexOf(current).coerceAtLeast(0)
    return values[(currentIndex + direction).mod(values.size)]
}

fun adjustInList(current: Int, values: List<Int>, delta: Int): Int? {
    val currentIndex = values.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: 0
    val newIndex = (currentIndex + delta).coerceIn(0, values.lastIndex)
    val newValue = values[newIndex]
    return if (newValue != current) newValue else null
}

object VolumeLevels {
    val UI_SOUNDS = listOf(50, 70, 85, 95, 100)
    val AMBIENT_AUDIO = listOf(2, 5, 10, 20, 35)
}
