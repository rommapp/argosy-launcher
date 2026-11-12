package com.nendo.argosy.domain.setup

import com.nendo.argosy.domain.model.SetupFacts
import com.nendo.argosy.domain.model.SetupItem
import com.nendo.argosy.domain.model.SetupState
import com.nendo.argosy.domain.model.isComplete

object SetupRouter {

    /**
     * The items routing holds the person to, in setup order. [SetupItem.DEFAULT_HOME] is offered
     * during setup but never enforced: an Argosy that is not the home app runs as a single-screen
     * app on whichever display opened it.
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

    /**
     * Whether launch opens setup: a required item is unsatisfied, or the flow has not reached Done
     * yet, in which case setup resumes at its first optional page not yet seen.
     */
    fun needsSetup(facts: SetupFacts): Boolean = !derive(facts).isComplete || !facts.flowFinished
}
