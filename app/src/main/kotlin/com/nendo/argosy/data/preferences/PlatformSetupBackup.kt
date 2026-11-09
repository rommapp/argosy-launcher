package com.nendo.argosy.data.preferences

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.EmulatorLaunchArgsDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.EmulatorLaunchArgsEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class PlatformSetupImport(val applied: Int, val skipped: Int)

/**
 * The emulator, core and Launch Args choices in the settings backup. Platforms are named by slug
 * and fs_slug and games by RomM id, so a file written on one install applies on another. File
 * locations and GPU driver picks name things on the device that wrote the file and are never
 * carried; import keeps the values the device already holds for them.
 */
class PlatformSetupBackup @Inject constructor(
    private val emulatorConfigDao: EmulatorConfigDao,
    private val launchArgsDao: EmulatorLaunchArgsDao,
    private val platformDao: PlatformDao,
    private val gameDao: GameDao
) {

    suspend fun export(): JSONObject {
        val platforms = platformDao.getAllPlatforms().associateBy { it.id }
        val platformRows = JSONArray()
        emulatorConfigDao.getPlatformDefaults().forEach { config ->
            val platform = platforms[config.platformId] ?: return@forEach
            platformRows.put(platformKey(platform).put(FIELD_CONFIG, configJson(config)))
        }
        val gameRows = JSONArray()
        val overrides = emulatorConfigDao.getGameOverrides()
        val rommIds = gameDao.getByIds(overrides.mapNotNull { it.gameId })
            .mapNotNull { game -> game.rommId?.let { game.id to it } }
            .toMap()
        overrides.forEach { config ->
            val rommId = rommIds[config.gameId] ?: return@forEach
            gameRows.put(JSONObject().put(FIELD_ROMM_ID, rommId).put(FIELD_CONFIG, configJson(config)))
        }
        val argRows = JSONArray()
        launchArgsDao.getAll().forEach { args ->
            val platform = platforms[args.platformId] ?: return@forEach
            argRows.put(platformKey(platform).put(FIELD_LAUNCH_ARGS, launchArgsJson(args)))
        }
        return JSONObject()
            .put(FIELD_PLATFORMS, platformRows)
            .put(FIELD_GAMES, gameRows)
            .put(FIELD_LAUNCH_ARGS, argRows)
    }

    fun count(section: JSONObject): Int = listOf(FIELD_PLATFORMS, FIELD_GAMES, FIELD_LAUNCH_ARGS)
        .sumOf { section.optJSONArray(it)?.length() ?: 0 }

    suspend fun import(section: JSONObject): PlatformSetupImport {
        var applied = 0
        var skipped = 0
        fun tally(wrote: Boolean) = if (wrote) applied++ else skipped++

        section.optJSONArray(FIELD_PLATFORMS).objects().forEach { tally(importPlatform(it)) }
        section.optJSONArray(FIELD_GAMES).objects().forEach { tally(importGame(it)) }
        section.optJSONArray(FIELD_LAUNCH_ARGS).objects().forEach { tally(importLaunchArgs(it)) }
        return PlatformSetupImport(applied, skipped)
    }

    private suspend fun importPlatform(row: JSONObject): Boolean {
        val platform = resolvePlatform(row) ?: return false
        val config = row.optJSONObject(FIELD_CONFIG) ?: return false
        val existing = emulatorConfigDao.getDefaultForPlatform(platform.id)
        emulatorConfigDao.clearPlatformDefaults(platform.id)
        emulatorConfigDao.insert(
            config.toEntity(platformId = platform.id, gameId = null, isDefault = true, kept = existing)
        )
        return true
    }

    private suspend fun importGame(row: JSONObject): Boolean {
        if (!row.has(FIELD_ROMM_ID)) return false
        val game = gameDao.getByRommId(row.optLong(FIELD_ROMM_ID)) ?: return false
        val config = row.optJSONObject(FIELD_CONFIG) ?: return false
        val existing = emulatorConfigDao.getByGameId(game.id)
        emulatorConfigDao.deleteGameOverride(game.id)
        emulatorConfigDao.insert(
            config.toEntity(platformId = game.platformId, gameId = game.id, isDefault = false, kept = existing)
        )
        return true
    }

    private suspend fun importLaunchArgs(row: JSONObject): Boolean {
        val platform = resolvePlatform(row) ?: return false
        val args = row.optJSONObject(FIELD_LAUNCH_ARGS) ?: return false
        val emulatorId = args.optStringOrNull("emulator_id") ?: return false
        launchArgsDao.upsert(
            EmulatorLaunchArgsEntity(
                platformId = platform.id,
                emulatorId = emulatorId,
                launchMethod = args.optStringOrNull("launch_method"),
                romPathFormat = args.optStringOrNull("rom_path_format"),
                intentFlagsMask = if (args.has("intent_flags_mask")) args.optInt("intent_flags_mask") else null,
                mimeType = args.optStringOrNull("mime_type"),
                dataBinding = args.optStringOrNull("data_binding"),
                extraBinding = args.optStringOrNull("extra_binding"),
                clipDataBinding = args.optStringOrNull("clip_data_binding"),
                customExtras = args.optStringOrNull("custom_extras")
            )
        )
        return true
    }

    private suspend fun resolvePlatform(row: JSONObject): PlatformEntity? {
        val slug = row.optStringOrNull(FIELD_SLUG) ?: return null
        return platformDao.getBySlugAndFsSlug(slug, row.optStringOrNull(FIELD_FS_SLUG))
    }

    private fun platformKey(platform: PlatformEntity): JSONObject = JSONObject()
        .put(FIELD_SLUG, platform.slug)
        .apply { platform.fsSlug?.let { put(FIELD_FS_SLUG, it) } }

    private fun configJson(config: EmulatorConfigEntity): JSONObject = JSONObject().apply {
        config.packageName?.let { put("package_name", it) }
        config.displayName?.let { put("display_name", it) }
        config.coreName?.let { put("core_name", it) }
        config.preferredExtension?.let { put("preferred_extension", it) }
        config.useFileUri?.let { put("use_file_uri", it) }
        config.displayTarget?.let { put("display_target", it) }
        config.controllerTypes?.let { put("controller_types", it) }
        config.graphicsBackend?.let { put("graphics_backend", it) }
    }

    private fun JSONObject.toEntity(
        platformId: Long?,
        gameId: Long?,
        isDefault: Boolean,
        kept: EmulatorConfigEntity?
    ) = EmulatorConfigEntity(
        platformId = platformId,
        gameId = gameId,
        packageName = optStringOrNull("package_name"),
        displayName = optStringOrNull("display_name"),
        coreName = optStringOrNull("core_name"),
        isDefault = isDefault,
        preferredExtension = optStringOrNull("preferred_extension"),
        useFileUri = if (has("use_file_uri")) optBoolean("use_file_uri") else null,
        displayTarget = optStringOrNull("display_target"),
        controllerTypes = optStringOrNull("controller_types"),
        graphicsBackend = optStringOrNull("graphics_backend"),
        savePath = kept?.savePath,
        selectedMemcardPath = kept?.selectedMemcardPath,
        gpuDriver = kept?.gpuDriver
    )

    private fun launchArgsJson(args: EmulatorLaunchArgsEntity): JSONObject = JSONObject().apply {
        put("emulator_id", args.emulatorId)
        args.launchMethod?.let { put("launch_method", it) }
        args.romPathFormat?.let { put("rom_path_format", it) }
        args.intentFlagsMask?.let { put("intent_flags_mask", it) }
        args.mimeType?.let { put("mime_type", it) }
        args.dataBinding?.let { put("data_binding", it) }
        args.extraBinding?.let { put("extra_binding", it) }
        args.clipDataBinding?.let { put("clip_data_binding", it) }
        args.customExtras?.let { put("custom_extras", it) }
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) optString(name) else null

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    companion object {
        const val FIELD_SECTION = "platform_setup"
        private const val FIELD_PLATFORMS = "platforms"
        private const val FIELD_GAMES = "games"
        private const val FIELD_LAUNCH_ARGS = "launch_args"
        private const val FIELD_CONFIG = "config"
        private const val FIELD_SLUG = "slug"
        private const val FIELD_FS_SLUG = "fs_slug"
        private const val FIELD_ROMM_ID = "romm_id"
    }
}
