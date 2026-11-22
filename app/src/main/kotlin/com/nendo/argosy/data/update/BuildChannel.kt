package com.nendo.argosy.data.update

import com.nendo.argosy.BuildConfig

/**
 * Whether a build is a prerelease: a debug build, or a version name carrying a prerelease suffix
 * such as `3.0.0-beta.1`. [isPrereleaseBuild] is the answer for the running build.
 */
object BuildChannel {

    fun isPrerelease(isDebugBuild: Boolean, versionName: String): Boolean =
        isDebugBuild || versionName.contains(PRERELEASE_SEPARATOR)

    val isPrereleaseBuild: Boolean = isPrerelease(BuildConfig.DEBUG, BuildConfig.VERSION_NAME)

    /**
     * Whether this build checks GitHub for Argosy updates and installs them. Off for builds a
     * store signs and updates itself (`-Pfdroid`), where a GitHub APK cannot install over them.
     */
    val selfUpdates: Boolean = BuildConfig.SELF_UPDATE

    private const val PRERELEASE_SEPARATOR = '-'
}
