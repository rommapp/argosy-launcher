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

    private const val PRERELEASE_SEPARATOR = '-'
}
