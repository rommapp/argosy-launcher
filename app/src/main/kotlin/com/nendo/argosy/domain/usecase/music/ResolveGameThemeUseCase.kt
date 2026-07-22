package com.nendo.argosy.domain.usecase.music

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.model.VariantCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val MIN_THEME_DURATION_SECONDS = 30.0

sealed interface GameThemeSource {
    val title: String

    data class Local(
        val path: String,
        override val title: String
    ) : GameThemeSource

    data class Stream(
        val rommFileId: Long,
        val fileName: String,
        override val title: String,
        val platformName: String,
        val gameName: String,
        val trackNumber: Int?
    ) : GameThemeSource
}

/** Picks a game's explicitly theme-titled soundtrack track, or null when none qualifies. */
class ResolveGameThemeUseCase @Inject constructor(
    private val gameFileDao: GameFileDao,
    private val gameDao: GameDao,
    private val platformDao: PlatformDao
) {
    private val mainTheme = Regex("(?i)\\bmain\\s+theme\\b")
    private val titleTheme = Regex("(?i)\\btitle\\s+(theme|screen)\\b")
    private val openingTheme = Regex("(?i)\\b((opening|intro)\\s+theme|theme\\s+song)\\b")
    private val themeWord = Regex("(?i)\\b(main|title|opening|intro)\\b")
    private val trackNumberPrefix = Regex("^\\s*\\d+([.\\-]\\d+)*\\s*[-. ]\\s*")

    suspend operator fun invoke(gameId: Long): GameThemeSource? = withContext(Dispatchers.IO) {
        val best = gameFileDao.getFilesByCategory(gameId, VariantCategory.SOUNDTRACK.key)
            .asSequence()
            .filter { (it.durationSeconds ?: 0.0) >= MIN_THEME_DURATION_SECONDS }
            .mapNotNull { row ->
                themeTier(matchText(row.trackTitle, row.fileName))?.let { tier -> row to tier }
            }
            .filter { (row, _) ->
                localPathIfPresent(row.localPath) != null || row.rommFileId != null
            }
            .sortedWith(compareBy({ it.first.trackNumber ?: Int.MAX_VALUE }, { it.second }))
            .map { it.first }
            .firstOrNull()
            ?: return@withContext null

        val displayTitle = best.trackTitle ?: cleanFileName(best.fileName)
        val localPath = localPathIfPresent(best.localPath)
        when {
            localPath != null -> GameThemeSource.Local(localPath, displayTitle)
            best.rommFileId != null -> {
                val game = gameDao.getById(gameId)
                val platformName = game?.platformId?.let { platformDao.getById(it)?.name }
                    ?: game?.platformSlug.orEmpty()
                GameThemeSource.Stream(
                    rommFileId = best.rommFileId,
                    fileName = best.fileName,
                    title = displayTitle,
                    platformName = platformName,
                    gameName = game?.title.orEmpty(),
                    trackNumber = best.trackNumber
                )
            }
            else -> null
        }
    }

    private fun matchText(trackTitle: String?, fileName: String): String =
        trackTitle?.takeIf { it.isNotBlank() } ?: cleanFileName(fileName)

    private fun cleanFileName(fileName: String): String =
        trackNumberPrefix.replace(fileName.substringBeforeLast('.'), "").trim()

    private fun themeTier(text: String): Int? = when {
        mainTheme.containsMatchIn(text) -> 0
        titleTheme.containsMatchIn(text) -> 1
        openingTheme.containsMatchIn(text) -> 2
        themeWord.containsMatchIn(text) -> 3
        else -> null
    }

    private fun localPathIfPresent(localPath: String?): String? =
        localPath?.takeIf { File(it).exists() }
}
