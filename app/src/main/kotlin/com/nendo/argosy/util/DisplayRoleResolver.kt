package com.nendo.argosy.util

import com.nendo.argosy.data.preferences.SessionStateStore

class DisplayRoleResolver(
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val sessionStateStore: SessionStateStore
) {
    val isSwapped: Boolean
        get() = when (sessionStateStore.getDisplayRoleOverride()) {
            "STANDARD" -> false
            "SWAPPED" -> true
            else -> displayAffinityHelper.secondaryDisplayType == SecondaryDisplayType.EXTERNAL
        }
}
