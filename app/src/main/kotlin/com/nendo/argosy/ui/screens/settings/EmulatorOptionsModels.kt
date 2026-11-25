package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.data.emulator.config.ConfigFileState
import com.nendo.argosy.data.emulator.config.ConfigValueType
import com.nendo.argosy.data.emulator.config.EmulatorDataRoute
import com.nendo.argosy.data.emulator.config.EmulatorEditBlock

enum class EmulatorOptionsPhase { LOADING, READY, UNSUPPORTED, FAILED }

data class EmulatorOptionChoiceUi(val token: String, val name: String)

data class EmulatorOptionRangeUi(val min: Double, val max: Double, val step: Double)

/**
 * One known option as the submenu shows it. [value] is the emulator's name for the effective
 * value, else the stored token, else the registry default; null only for a file that could not be
 * parsed. [token] is the effective value an edit steps from; [editable] marks an option Argosy may change.
 */
data class EmulatorOptionUi(
    val section: String,
    val key: String,
    val value: String?,
    val isDefault: Boolean,
    val hidden: Boolean,
    val fileId: String = "",
    val type: ConfigValueType = ConfigValueType.STRING,
    val token: String? = null,
    val choices: List<EmulatorOptionChoiceUi> = emptyList(),
    val range: EmulatorOptionRangeUi? = null,
    val editable: Boolean = false
) {
    val id: String get() = emulatorOptionId(fileId, section, key)
}

fun emulatorOptionId(fileId: String, section: String, key: String): String =
    listOf(fileId, section, key).joinToString(OPTION_ID_SEPARATOR.toString())

/**
 * A GPU driver the driver option can point at; [path] is the value written for it.
 */
data class EmulatorDriverChoiceUi(val id: String, val name: String, val path: String)

/**
 * An edit waiting on the user's go-ahead to close the emulator.
 */
data class EmulatorPendingEditUi(val optionId: String, val token: String)

data class EmulatorOtherKeyUi(
    val section: String,
    val key: String,
    val value: String?,
    val hidden: Boolean
)

data class EmulatorPlayTimeUi(
    val gameId: String,
    val playedMs: Long
)

data class EmulatorOptionsFileUi(
    val id: String,
    val path: String,
    val state: ConfigFileState,
    val modifiedAt: Long?,
    val options: List<EmulatorOptionUi>,
    val otherKeys: List<EmulatorOtherKeyUi>,
    val playTimes: List<EmulatorPlayTimeUi>
)

data class EmulatorOptionsState(
    val emulatorId: String? = null,
    val packageName: String? = null,
    val emulatorName: String? = null,
    val phase: EmulatorOptionsPhase = EmulatorOptionsPhase.LOADING,
    val dataRoot: String? = null,
    val readRoute: EmulatorDataRoute? = null,
    val editRoute: EmulatorDataRoute? = null,
    val editBlock: EmulatorEditBlock? = null,
    val writeAppDataRoute: EmulatorDataRoute? = null,
    val files: List<EmulatorOptionsFileUi> = emptyList(),
    val expandedFileIds: Set<String> = emptySet(),
    val pendingEdit: EmulatorPendingEditUi? = null,
    val writing: Boolean = false,
    val undoableIds: Set<String> = emptySet(),
    val driverOptionId: String? = null,
    val drivers: List<EmulatorDriverChoiceUi> = emptyList()
) {
    /**
     * Whether [optionId] can be written now: a GPU driver option needs only app-data write access,
     * every other option needs the edit route.
     */
    fun canWrite(optionId: String): Boolean = when {
        phase != EmulatorOptionsPhase.READY -> false
        optionId == driverOptionId -> editRoute != null || writeAppDataRoute != null
        else -> editRoute != null
    }

    /**
     * The values the driver option cycles through: the system driver (empty) first, then each driver.
     */
    val driverTokens: List<String> get() = listOf("") + drivers.map { it.path }
}

private const val OPTION_ID_SEPARATOR = '|'
