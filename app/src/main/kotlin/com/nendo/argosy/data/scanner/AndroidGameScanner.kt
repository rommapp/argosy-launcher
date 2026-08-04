package com.nendo.argosy.data.scanner

import android.util.Log
import com.nendo.argosy.data.local.dao.AppCategoryDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.AppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

import com.nendo.argosy.data.platform.LocalPlatformIds

private const val TAG = "AndroidGameScanner"
private const val ANDROID_SLUG = "android"

data class AndroidScanResult(
    val added: Int = 0,
    val enriched: Int = 0
)


@Singleton
class AndroidGameScanner @Inject constructor(
    private val appsRepository: AppsRepository,
    private val appCategoryDao: AppCategoryDao,
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository,
    private val metadataFetcher: AndroidAppMetadataFetcher
) {

    /**
     * Adds installed apps that declare themselves games to the Android platform.
     *
     * Classification is local: the package's own category, plus anything the user has already
     * flagged by hand in the Apps screen. Nothing is asked over the network, so this is instant
     * and works offline, and the cost is that a game shipping no category at all is not found -
     * the Apps screen stays the way to add those, and a manual flag is remembered here.
     *
     * Existing rows are left alone. An app already in the library, however it got there, is not
     * touched, so re-running this cannot disturb a RomM-sourced Android game.
     *
     * A RomM-sourced Android game the server has given us but that is not yet tied to a package
     * is claimed by [relinkInstalledRommAndroidApps] first, and any that stay unclaimed are held
     * back from insertion. Inserting alongside one of those is how this feature historically
     * produced duplicate rows for a single game.
     */
    suspend fun scanInstalledGames(): AndroidScanResult = withContext(Dispatchers.IO) {
        ensureAndroidPlatformExists()
        relinkInstalledRommAndroidApps()

        val unclaimed = gameDao
            .getByPlatform(LocalPlatformIds.ANDROID, syncPreferencesRepository.getRommUserId())
            .filter { it.packageName == null && it.source != GameSource.ANDROID_APP }
        val spokenFor = buildSet {
            unclaimed.forEach { game ->
                game.titleId?.let { add(it) }
                matchKey(game.title).takeIf { it.isNotEmpty() }?.let { add(it) }
            }
        }

        val installed = appsRepository.getInstalledApps(includeSystemApps = false)
            .filter { !isEmulatorPackage(it.packageName) }
        var added = 0
        var enriched = 0

        for (app in installed) {
            val existing = gameDao.getByPackageName(app.packageName)
            if (existing != null) {
                if (needsMetadata(existing)) {
                    metadataFetcher.fetch(existing.id, app.packageName)
                    enriched++
                }
                continue
            }
            if (app.packageName in spokenFor || matchKey(app.label) in spokenFor) {
                Log.d(TAG, "scanInstalledGames: ${app.packageName} matches an unlinked RomM row, skipping")
                continue
            }
            if (!looksLikeGame(app)) continue

            val gameId = gameDao.insert(
                GameEntity(
                    platformId = LocalPlatformIds.ANDROID,
                    platformSlug = ANDROID_SLUG,
                    title = app.label,
                    sortTitle = sortTitleFor(app.label),
                    localPath = null,
                    rommId = null,
                    igdbId = null,
                    source = GameSource.ANDROID_APP,
                    packageName = app.packageName,
                    titleId = app.packageName
                )
            )
            added++
            metadataFetcher.fetch(gameId, app.packageName)
        }

        if (added > 0) updatePlatformGameCount()
        Log.d(TAG, "scanInstalledGames: added $added, enriched $enriched of ${installed.size} installed apps")
        AndroidScanResult(added = added, enriched = enriched)
    }

    /**
     * Whether a row is still missing what the store could tell us about it.
     *
     * Only apps we added ourselves qualify. A RomM-sourced game already carries the server's
     * artwork, and re-running a store fetch over it would replace that cover with a Play Store
     * one - the regression #223 was filed for.
     *
     * A cover alone is not evidence of a successful lookup, because a failed one still caches the
     * launcher icon as the cover. The description is the honest signal that the store answered.
     */
    private fun needsMetadata(game: GameEntity): Boolean =
        game.source == GameSource.ANDROID_APP &&
            (game.description.isNullOrBlank() || game.coverPath == null)

    /**
     * The Android platform exists whether or not anything is on it, because its own settings
     * screen is where a user goes to scan apps onto it.
     */
    suspend fun ensureAndroidPlatformExists() = withContext(Dispatchers.IO) {
        if (platformDao.getById(LocalPlatformIds.ANDROID) != null) return@withContext
        com.nendo.argosy.data.platform.PlatformDefinitions.getBySlug(ANDROID_SLUG)
            ?.let { com.nendo.argosy.data.platform.PlatformDefinitions.toLocalPlatformEntity(it) }
            ?.let { platformDao.insert(it) }
    }

    private fun sortTitleFor(label: String): String = label.lowercase()
        .removePrefix("the ")
        .removePrefix("a ")
        .removePrefix("an ")
        .trim()

    /**
     * Whether an installed app is a game, from signals that exist without asking the network: a
     * flag the user set by hand in the Apps screen, else the package's own declared category.
     *
     * This gates the title-based relink as well as insertion. It used to be answered by a stored
     * Play Store classification, which nothing populates any more, so that check passed for every
     * app whose row was simply absent and stopped separating games from anything else sharing a
     * name. Package identity does not go through here - an exact package match needs no guess.
     */
    private suspend fun looksLikeGame(app: com.nendo.argosy.data.repository.InstalledApp): Boolean {
        val flagged = appCategoryDao.getByPackageName(app.packageName)
        if (flagged?.isManualOverride == true) return flagged.isGame
        return app.declaresGameCategory
    }

    suspend fun relinkInstalledRommAndroidApps(): Int = withContext(Dispatchers.IO) {
        val candidates = gameDao
            .getByPlatform(LocalPlatformIds.ANDROID, syncPreferencesRepository.getRommUserId())
            .filter { it.packageName == null && it.source != GameSource.ANDROID_APP }
        if (candidates.isEmpty()) return@withContext 0

        val installedApps = appsRepository.getInstalledApps(includeSystemApps = false)
            .filter { !isEmulatorPackage(it.packageName) }
            .filter { appCategoryDao.getByPackageName(it.packageName)?.isGame != false }
        val installedByPackage = installedApps.associateBy { it.packageName }
        val installedByName = installedApps
            .filter { looksLikeGame(it) }
            .groupBy { matchKey(it.label) }

        var relinked = 0
        for (game in candidates) {
            val byPackage = game.titleId?.let { installedByPackage[it] }
            val byTitle = matchKey(game.title).takeIf { it.isNotEmpty() }
                ?.let { installedByName[it]?.singleOrNull() }
            val match = byPackage ?: byTitle ?: continue
            val holder = gameDao.getByPackageName(match.packageName)
            if (holder != null && (holder.id == game.id || holder.rommId != null)) continue
            if (holder != null) gameDao.delete(holder.id)
            gameDao.update(
                game.copy(
                    packageName = match.packageName,
                    titleId = match.packageName,
                    source = GameSource.ANDROID_APP,
                    localPath = null,
                    isFavorite = game.isFavorite || holder?.isFavorite == true,
                    playCount = game.playCount + (holder?.playCount ?: 0),
                    playTimeMinutes = game.playTimeMinutes + (holder?.playTimeMinutes ?: 0),
                    lastPlayed = listOfNotNull(game.lastPlayed, holder?.lastPlayed).maxOrNull(),
                    coverPath = game.coverPath ?: holder?.coverPath
                )
            )
            relinked++
            Log.d(TAG, "Relinked Android game '${game.title}' -> ${match.packageName}" +
                if (holder != null) " (merged duplicate ${holder.id})" else "")
        }
        if (relinked > 0) updatePlatformGameCount()
        relinked
    }

    private fun matchKey(title: String): String =
        createSortTitle(title).replace(Regex("[^a-z0-9]"), "")





    private suspend fun updatePlatformGameCount() {
        val count = gameDao.countByPlatform(
            LocalPlatformIds.ANDROID,
            syncPreferencesRepository.getRommUserId()
        )
        platformDao.updateGameCount(LocalPlatformIds.ANDROID, count)
    }

    private fun createSortTitle(title: String): String {
        return title.lowercase()
            .removePrefix("the ")
            .removePrefix("a ")
            .removePrefix("an ")
            .trim()
    }

    private fun isEmulatorPackage(packageName: String): Boolean {
        if (EmulatorRegistry.getByPackage(packageName) != null) return true
        if (EmulatorRegistry.findFamilyForPackage(packageName) != null) return true
        return false
    }


}
