package com.nendo.argosy.core.input

enum class SoundType {
    SILENT,
    NAVIGATE,
    BOUNDARY,
    SECTION_CHANGE,
    SELECT,
    BACK,
    OPEN_MODAL,
    CLOSE_MODAL,
    FAVORITE,
    UNFAVORITE,
    DOWNLOAD_START,
    DOWNLOAD_COMPLETE,
    DOWNLOAD_CANCEL,
    ERROR,
    VOLUME_PREVIEW,
    TOGGLE,
    LAUNCH_GAME
}

data class SoundConfig(
    val presetName: String? = null,
    val customFilePath: String? = null
) {
    val isRommSource: Boolean get() = customFilePath != null && presetName == ROMM_SOURCE

    companion object {
        const val ROMM_SOURCE = "ROMM_MUSIC"
    }
}
