package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.nendo.argosy.data.preferences.BuiltinEmulatorPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibretroSettingsRepositoryTest {
    private val platformSettingsDao = mockk<PlatformLibretroSettingsDao>()
    private val builtinPreferences = mockk<BuiltinEmulatorPreferencesRepository>(relaxed = true)
    private val repository = LibretroSettingsRepository(platformSettingsDao, builtinPreferences)

    @Test
    fun `setPlatformShaderChain preserves other platform overrides without changing global settings`() = runTest {
        val existing = PlatformLibretroSettingsEntity(
            id = 7L,
            platformId = 42L,
            filter = "Nearest",
            aspectRatio = "4:3",
            rewindEnabled = false
        )
        val saved = slot<PlatformLibretroSettingsEntity>()
        coEvery { platformSettingsDao.getByPlatformId(42L) } returns existing
        coEvery { platformSettingsDao.upsert(capture(saved)) } returns 7L

        repository.setPlatformShaderChain(42L, "Custom", """{"passes":[]}""")

        assertEquals(
            existing.copy(shader = "Custom", shaderChain = """{"passes":[]}"""),
            saved.captured
        )
        coVerify(exactly = 0) { builtinPreferences.setBuiltinShader(any()) }
        coVerify(exactly = 0) { builtinPreferences.setBuiltinShaderChain(any()) }
    }
}
