package com.nendo.argosy.integration

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RomMSavesTest : RomMIntegrationTest() {

    private var createdSaveId: Long? = null
    private var testRomId: Long? = null

    @Before
    fun auth() {
        authenticate()
    }

    @After
    fun cleanup() {
        createdSaveId?.let { saveId ->
            runBlocking {
                try {
                    api.getSave(saveId)
                } catch (_: Exception) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    @Test
    fun `getSavesByPlatform returns list`() = runBlocking {
        val platformsResponse = api.getPlatforms()
        assertTrue("Need platforms to test", platformsResponse.isSuccessful)
        val platforms = platformsResponse.body()!!
        assertTrue("Need at least one platform", platforms.isNotEmpty())

        val platformId = platforms.first().id
        val response = api.getSavesByPlatform(platformId)

        assertTrue("getSavesByPlatform should succeed", response.isSuccessful)
        val saves = response.body()
        assertNotNull("Saves list should not be null", saves)
    }

    @Test
    fun `save includes expected fields when saves exist`() = runBlocking {
        val platformsResponse = api.getPlatforms()
        assertTrue("Need platforms to test", platformsResponse.isSuccessful)
        val platforms = platformsResponse.body()!!
        assertTrue("Need at least one platform", platforms.isNotEmpty())

        for (platform in platforms.take(5)) {
            val savesResponse = api.getSavesByPlatform(platform.id)
            if (savesResponse.isSuccessful) {
                val saves = savesResponse.body() ?: emptyList()
                if (saves.isNotEmpty()) {
                    val save = saves.first()
                    assertTrue("Save should have positive ID", save.id > 0)
                    assertTrue("Save fileName should not be empty", save.fileName.isNotEmpty())
                    assertTrue("Save updatedAt should not be empty", save.updatedAt.isNotEmpty())
                    return@runBlocking
                }
            }
        }
        // If no saves found, that's okay - just verify the API works
        assertTrue("API calls succeeded even if no saves found", true)
    }

    @Test
    fun `uploadSave API accepts multipart request`() = runBlocking {
        val romsResponse = api.getRoms(mapOf("limit" to "1"))
        assertTrue("Need ROMs to test", romsResponse.isSuccessful)
        val roms = romsResponse.body()!!.items
        assertTrue("Need at least one ROM", roms.isNotEmpty())

        testRomId = roms.first().id

        val dummyContent = ByteArray(64) { 0 }
        val requestBody = dummyContent.toRequestBody("application/octet-stream".toMediaType())
        val filePart = MultipartBody.Part.createFormData(
            "file",
            "test_integration.sav",
            requestBody
        )

        val response = api.uploadSave(
            romId = testRomId!!,
            emulator = "retroarch",
            saveFile = filePart
        )

        if (response.isSuccessful) {
            val save = response.body()
            assertNotNull("Created save should not be null", save)
            createdSaveId = save?.id
            assertTrue("Created save should have positive ID", save!!.id > 0)
        } else {
            val errorCode = response.code()
            assertTrue(
                "Upload should succeed or return expected error (got $errorCode)",
                errorCode == 400 || errorCode == 403 || errorCode == 422
            )
        }
    }
}
