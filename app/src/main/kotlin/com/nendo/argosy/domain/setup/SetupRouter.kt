package com.nendo.argosy.domain.setup

import com.nendo.argosy.domain.model.SetupFacts
import com.nendo.argosy.domain.model.SetupItem
import com.nendo.argosy.domain.model.SetupState

object SetupRouter {

    /**
     * The items routing holds the person to, in setup order. [SetupItem.DEFAULT_HOME] is judged by
     * [isSatisfied] but stays out of this list until its setup page exists.
     */
    val ENFORCED_ITEMS: List<SetupItem> = SetupItem.entries - SetupItem.DEFAULT_HOME

    val PERMISSION_ITEMS: List<SetupItem> =
        listOf(SetupItem.ALL_FILES_ACCESS, SetupItem.USAGE_ACCESS, SetupItem.NOTIFICATIONS)

    fun permissionsSatisfied(facts: SetupFacts): Boolean =
        PERMISSION_ITEMS.all { isSatisfied(it, facts) }

    fun isSatisfied(item: SetupItem, facts: SetupFacts): Boolean = when (item) {
        SetupItem.LIBRARY_SOURCE -> facts.librarySignedIn && facts.librarySourceConfirmed
        SetupItem.ALL_FILES_ACCESS -> facts.allFilesAccess
        SetupItem.USAGE_ACCESS -> facts.usageAccess || !facts.usageAccessGrantable
        SetupItem.NOTIFICATIONS -> facts.notifications
        SetupItem.ROM_FOLDER -> facts.romFolderPresent && facts.romFolderConfirmed
        SetupItem.SAVE_SYNC -> facts.saveSyncAnswered
        SetupItem.DEFAULT_HOME ->
            !facts.dualScreenDevice || facts.defaultHome || facts.defaultHomeDeclined
    }

    fun derive(facts: SetupFacts, enforced: List<SetupItem> = ENFORCED_ITEMS): SetupState =
        enforced.sortedBy { it.ordinal }
            .firstOrNull { !isSatisfied(it, facts) }
            ?.let { SetupState.Incomplete(it) }
            ?: SetupState.Complete
}
