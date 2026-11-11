package com.nendo.argosy.ui.screens.firstrun

import com.nendo.argosy.domain.model.SetupFacts
import com.nendo.argosy.domain.model.SetupItem
import com.nendo.argosy.domain.model.SetupState

internal const val PLATFORM_SELECT_THRESHOLD = 10

internal const val SAVE_SYNC_ON_INDEX = 0
internal const val SAVE_SYNC_OFF_INDEX = 1
internal const val SAVE_SYNC_NEXT_INDEX = 2

internal fun showsPlatformSelect(platformCount: Int): Boolean = platformCount > PLATFORM_SELECT_THRESHOLD

internal fun stepAfter(step: FirstRunStep, platformCount: Int): FirstRunStep = when (step) {
    FirstRunStep.WELCOME -> FirstRunStep.ROMM_LOGIN
    FirstRunStep.ROMM_LOGIN -> FirstRunStep.ROMM_SUCCESS
    FirstRunStep.ROMM_SUCCESS -> FirstRunStep.PERMISSIONS
    FirstRunStep.PERMISSIONS -> FirstRunStep.ROM_PATH
    FirstRunStep.ROM_PATH -> FirstRunStep.IMAGE_CACHE
    FirstRunStep.IMAGE_CACHE -> FirstRunStep.SAVE_SYNC
    FirstRunStep.SAVE_SYNC ->
        if (showsPlatformSelect(platformCount)) FirstRunStep.PLATFORM_SELECT else FirstRunStep.CORE_PROMPT
    FirstRunStep.PLATFORM_SELECT -> FirstRunStep.CORE_PROMPT
    FirstRunStep.CORE_PROMPT -> FirstRunStep.CORE_DOWNLOAD
    FirstRunStep.CORE_DOWNLOAD -> FirstRunStep.COMPLETE
    FirstRunStep.COMPLETE -> FirstRunStep.COMPLETE
}

internal fun stepBefore(step: FirstRunStep, platformCount: Int): FirstRunStep = when (step) {
    FirstRunStep.WELCOME -> FirstRunStep.WELCOME
    FirstRunStep.ROMM_LOGIN -> FirstRunStep.WELCOME
    FirstRunStep.ROMM_SUCCESS -> FirstRunStep.ROMM_LOGIN
    FirstRunStep.PERMISSIONS -> FirstRunStep.ROMM_SUCCESS
    FirstRunStep.ROM_PATH -> FirstRunStep.PERMISSIONS
    FirstRunStep.IMAGE_CACHE -> FirstRunStep.ROM_PATH
    FirstRunStep.SAVE_SYNC -> FirstRunStep.IMAGE_CACHE
    FirstRunStep.PLATFORM_SELECT -> FirstRunStep.SAVE_SYNC
    FirstRunStep.CORE_PROMPT ->
        if (showsPlatformSelect(platformCount)) FirstRunStep.PLATFORM_SELECT else FirstRunStep.SAVE_SYNC
    FirstRunStep.CORE_DOWNLOAD -> FirstRunStep.CORE_PROMPT
    FirstRunStep.COMPLETE -> FirstRunStep.CORE_PROMPT
}

internal fun entryStepFor(state: SetupState, facts: SetupFacts): FirstRunStep = when (state) {
    SetupState.Complete -> FirstRunStep.WELCOME
    is SetupState.Incomplete -> when (state.firstMissing) {
        SetupItem.LIBRARY_SOURCE -> when {
            facts.librarySignedIn -> FirstRunStep.ROMM_SUCCESS
            facts.anyConfirmed -> FirstRunStep.ROMM_LOGIN
            else -> FirstRunStep.WELCOME
        }
        SetupItem.ALL_FILES_ACCESS,
        SetupItem.USAGE_ACCESS,
        SetupItem.NOTIFICATIONS -> FirstRunStep.PERMISSIONS
        SetupItem.ROM_FOLDER -> FirstRunStep.ROM_PATH
        SetupItem.SAVE_SYNC -> FirstRunStep.SAVE_SYNC
        SetupItem.DEFAULT_HOME -> FirstRunStep.COMPLETE
    }
}
