package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nendo.argosy.data.local.entity.PlatformEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatformDao {

    @Query("SELECT * FROM platforms WHERE isVisible = 1 AND syncEnabled = 1 AND gameCount > 0 ORDER BY sortOrder ASC, name ASC")
    fun observeVisiblePlatforms(): Flow<List<PlatformEntity>>

    @Query("SELECT * FROM platforms ORDER BY sortOrder ASC, name ASC")
    fun observeAllPlatforms(): Flow<List<PlatformEntity>>

    @Query("SELECT * FROM platforms WHERE id = :id")
    suspend fun getById(id: Long): PlatformEntity?

    @Query("SELECT * FROM platforms WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): PlatformEntity?

    @Query("SELECT * FROM platforms WHERE slug = :slug AND (fsSlug = :fsSlug OR (fsSlug IS NULL AND :fsSlug IS NULL)) LIMIT 1")
    suspend fun getBySlugAndFsSlug(slug: String, fsSlug: String?): PlatformEntity?

    @Query("SELECT * FROM platforms WHERE slug = :slug")
    suspend fun getAllBySlug(slug: String): List<PlatformEntity>

    @Query("SELECT * FROM platforms WHERE slug IN (:slugs)")
    suspend fun getAllBySlugs(slugs: Collection<String>): List<PlatformEntity>

    @Query("SELECT * FROM platforms WHERE gameCount > 0 AND isVisible = 1 AND syncEnabled = 1 ORDER BY sortOrder ASC, name ASC")
    fun observePlatformsWithGames(): Flow<List<PlatformEntity>>

    @Query("SELECT * FROM platforms WHERE gameCount > 0 AND isVisible = 1 AND syncEnabled = 1 ORDER BY sortOrder ASC, name ASC")
    suspend fun getPlatformsWithGames(): List<PlatformEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(platform: PlatformEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(platforms: List<PlatformEntity>)

    @Update
    suspend fun update(platform: PlatformEntity)

    @Query("UPDATE platforms SET gameCount = :count WHERE id = :platformId")
    suspend fun updateGameCount(platformId: Long, count: Int)

    @Query("UPDATE platforms SET sortOrder = :order WHERE id = :platformId")
    suspend fun updateSortOrder(platformId: Long, order: Int)

    @Query("UPDATE platforms SET isVisible = :visible WHERE id = :platformId")
    suspend fun updateVisibility(platformId: Long, visible: Boolean)

    @Query("UPDATE platforms SET logoPath = :path WHERE id = :platformId")
    suspend fun updateLogoPath(platformId: Long, path: String)

    @Query("UPDATE platforms SET logoPath = NULL WHERE id = :platformId")
    suspend fun clearLogoPath(platformId: Long)

    @Query("SELECT * FROM platforms WHERE logoPath LIKE 'http%'")
    suspend fun getPlatformsWithRemoteLogos(): List<PlatformEntity>

    @Query("UPDATE platforms SET syncEnabled = :enabled WHERE id = :platformId")
    suspend fun updateSyncEnabled(platformId: Long, enabled: Boolean)

    @Query("UPDATE platforms SET customRomPath = :path WHERE id = :platformId")
    suspend fun updateCustomRomPath(platformId: Long, path: String?)

    @Query("SELECT * FROM platforms WHERE syncEnabled = 1")
    suspend fun getSyncEnabledPlatforms(): List<PlatformEntity>

    @Query("SELECT * FROM platforms WHERE gameCount > 0 OR syncEnabled = 0 ORDER BY sortOrder ASC, name ASC")
    fun observeConfigurablePlatforms(): Flow<List<PlatformEntity>>

    @Query("DELETE FROM platforms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM platforms")
    suspend fun getAllPlatforms(): List<PlatformEntity>

    @Query("SELECT * FROM platforms ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllPlatformsOrdered(): List<PlatformEntity>

    @Query("SELECT COUNT(*) FROM platforms WHERE syncEnabled = 1")
    suspend fun getEnabledPlatformCount(): Int

    @Query("SELECT COUNT(*) FROM platforms")
    suspend fun getTotalPlatformCount(): Int

    @Query("SELECT slug FROM platforms GROUP BY slug HAVING COUNT(*) > 1")
    suspend fun getAmbiguousSlugs(): List<String>

    @Query("SELECT slug FROM platforms GROUP BY slug HAVING COUNT(*) > 1")
    fun observeAmbiguousSlugs(): Flow<List<String>>
}
