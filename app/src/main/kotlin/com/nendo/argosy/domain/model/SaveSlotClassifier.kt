package com.nendo.argosy.domain.model

import com.nendo.argosy.data.repository.SaveSyncApiClient

/**
 * Which row of the save list an entry belongs on. A null channel means three different things
 * upstream (a local archive, a server null-slot archive, and the legacy latest save), so the
 * channel name alone cannot decide, and the two surfaces disagreed while each read it directly.
 */
enum class SaveSlotKind { AUTOSAVE, ARCHIVE, NAMED }

object SaveSlotClassifier {

    fun kindOf(channelName: String?, isLatest: Boolean, isArchival: Boolean): SaveSlotKind = when {
        isArchival -> SaveSlotKind.ARCHIVE
        isLatest -> SaveSlotKind.AUTOSAVE
        channelName == null -> SaveSlotKind.ARCHIVE
        channelName.equals(SaveSyncApiClient.AUTOSAVE_SLOT_NAME, ignoreCase = true) -> SaveSlotKind.AUTOSAVE
        channelName.equals(SaveSyncApiClient.DEFAULT_SAVE_NAME, ignoreCase = true) -> SaveSlotKind.AUTOSAVE
        else -> SaveSlotKind.NAMED
    }

    /**
     * The channel every surface files an entry under, so an entry classified [SaveSlotKind.AUTOSAVE]
     * lands on one row whether it arrived with a null channel or the literal autosave slot.
     */
    fun slotKeyOf(channelName: String?, isLatest: Boolean, isArchival: Boolean): String? =
        when (kindOf(channelName, isLatest, isArchival)) {
            SaveSlotKind.AUTOSAVE -> SaveSyncApiClient.AUTOSAVE_SLOT_NAME
            SaveSlotKind.ARCHIVE -> null
            SaveSlotKind.NAMED -> channelName
        }

    fun isActiveSlot(slotKey: String?, activeChannel: String?): Boolean {
        val effectiveActive = activeChannel ?: SaveSyncApiClient.AUTOSAVE_SLOT_NAME
        if (slotKey == null) return false
        return slotKey.equals(effectiveActive, ignoreCase = true)
    }
}
