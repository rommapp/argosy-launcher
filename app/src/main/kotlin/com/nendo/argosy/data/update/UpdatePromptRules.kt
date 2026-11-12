package com.nendo.argosy.data.update

import com.nendo.argosy.data.model.AppUpdateRelease
import com.nendo.argosy.data.remote.github.VersionInfo

object UpdatePromptRules {

    fun checksAllowed(isDebugBuild: Boolean, debugChecksEnabled: Boolean): Boolean =
        !isDebugBuild || debugChecksEnabled

    fun isNewer(tag: String, installedVersion: String): Boolean {
        val candidate = VersionInfo.parse(tag) ?: return false
        val installed = VersionInfo.parse(installedVersion) ?: return false
        return candidate > installed
    }

    fun available(latest: AppUpdateRelease?, installedVersion: String): AppUpdateRelease? =
        latest?.takeIf { isNewer(it.tag, installedVersion) }

    /**
     * The release the automatic prompt offers: the newest release above [installedVersion],
     * unless its tag is the skipped one or was put off with Later during this process.
     */
    fun promptFor(
        latest: AppUpdateRelease?,
        skippedTag: String?,
        deferredTags: Set<String>,
        installedVersion: String
    ): AppUpdateRelease? =
        available(latest, installedVersion)?.takeIf { it.tag != skippedTag && it.tag !in deferredTags }
}
