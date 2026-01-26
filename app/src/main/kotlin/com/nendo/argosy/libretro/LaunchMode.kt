package com.nendo.argosy.libretro

enum class LaunchMode {
    RESUME,
    NEW_CASUAL,
    NEW_HARDCORE,
    RESUME_HARDCORE;

    val isHardcore: Boolean
        get() = this == NEW_HARDCORE || this == RESUME_HARDCORE

    val isNewGame: Boolean
        get() = this == NEW_CASUAL || this == NEW_HARDCORE

    companion object {
        const val EXTRA_LAUNCH_MODE = "launch_mode"

        fun fromString(value: String?): LaunchMode {
            return when (value) {
                "NEW_CASUAL" -> NEW_CASUAL
                "NEW_HARDCORE" -> NEW_HARDCORE
                "RESUME_HARDCORE" -> RESUME_HARDCORE
                else -> RESUME
            }
        }
    }
}
