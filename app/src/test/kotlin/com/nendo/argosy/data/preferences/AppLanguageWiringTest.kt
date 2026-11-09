package com.nendo.argosy.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The resource directory an Android resource qualifier maps to for a BCP 47 language tag.
 */
private fun qualifierFor(tag: String): String {
    val parts = tag.split("-")
    val language = parts[0].lowercase()
    val script = parts.firstOrNull { it.length == 4 && it[0].isUpperCase() }
    val region = parts.firstOrNull { it.length == 2 && it.all(Char::isUpperCase) }
    return when {
        script != null -> "values-b+$language+${script.replaceFirstChar { it.uppercase() }}"
        region != null -> "values-$language-r$region"
        else -> "values-$language"
    }
}

class AppLanguageWiringTest {

    private val resRoot = listOf(File("src/main/res"), File("app/src/main/res"))
        .first { it.isDirectory }

    private val launcherTags = AppLanguage.entries.map { it.tag } - AppLanguage.SYSTEM.tag

    /** English needs no directory of its own: it is the base `values`. */
    private val translatedTags = launcherTags - AppLanguage.ENGLISH.tag

    private fun directoryNames() = resRoot.listFiles().orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values") }
        .map { it.name }
        .toSet()

    private fun configuredTags(): Set<String> {
        val config = File(resRoot, "xml/locales_config.xml")
        return Regex("<locale[^>]*android:name=\"([^\"]+)\"")
            .findAll(config.readText())
            .map { it.groupValues[1].trim() }
            .toSet()
    }

    @Test
    fun `every language the launcher offers has a resource directory`() {
        val missing = translatedTags.filter { qualifierFor(it) !in directoryNames() }
        assertTrue("no resource directory for: $missing", missing.isEmpty())
    }

    @Test
    fun `every localized resource directory belongs to a language the launcher offers`() {
        val offered = translatedTags.map { qualifierFor(it) }.toSet()
        val localeDirectories = directoryNames().filter {
            it.matches(Regex("values-b\\+[a-z]{2,3}\\+.+")) ||
                it.matches(Regex("values-[a-z]{2,3}(-r[A-Z]{2})?"))
        }
        val orphans = localeDirectories - offered
        assertTrue("resource directories no language selects: $orphans", orphans.isEmpty())
    }

    @Test
    fun `the per-app locale config lists exactly the languages the launcher offers`() {
        assertEquals(launcherTags.toSet(), configuredTags())
    }
}
