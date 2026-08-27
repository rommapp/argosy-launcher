package com.nendo.argosy.data.music

import android.content.Context
import com.nendo.argosy.data.preferences.StoragePreferences
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The track number reaches the on-disk filename, and whether a track counts as downloaded is
 * decided by recomputing that name and calling `exists()`. A locale that renders digits in its
 * own numerals therefore makes every downloaded track look missing and re-download under a
 * second name, so the formatting has to stay invariant no matter what the device is set to.
 */
class MusicDirectoryManagerLocaleTest {

    private fun manager(): MusicDirectoryManager {
        val prefs = mockk<StoragePreferencesRepository>()
        every { prefs.preferences } returns
            flowOf(StoragePreferences(musicStoragePath = "/music"))
        return MusicDirectoryManager(mockk<Context>(relaxed = true), prefs)
    }

    private fun <T> withDefaultLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `track filename keeps ascii digits under a non-latin numeral locale`() = runTest {
        val name = withDefaultLocale(Locale.forLanguageTag("ar-EG")) {
            kotlinx.coroutines.runBlocking {
                manager().targetFileFor("Wii", "Wii Shop", 5, "Shop Channel", "shop.mp3").name
            }
        }
        assertEquals("05 - Shop Channel.mp3", name)
    }

    @Test
    fun `track filename is identical across locales`() = runTest {
        val locales = listOf("en-US", "ar-EG", "fa-IR", "bn-IN", "hi-IN", "ru-RU")
        val names = locales.map { tag ->
            withDefaultLocale(Locale.forLanguageTag(tag)) {
                kotlinx.coroutines.runBlocking {
                    manager().targetFileFor("Wii", "Wii Shop", 5, "Shop Channel", "shop.mp3").path
                }
            }
        }
        assertEquals(1, names.toSet().size)
        assertEquals("/music/Wii/Wii Shop/05 - Shop Channel.mp3", names.first())
    }
}
