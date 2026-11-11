package com.nendo.argosy.ui.components.keyboard

import com.nendo.argosy.ui.components.keyboard.FunctionKey
import com.nendo.argosy.ui.components.keyboard.KeyboardEngine
import com.nendo.argosy.ui.components.keyboard.KeyboardFieldType
import com.nendo.argosy.ui.components.keyboard.KeyboardKey
import com.nendo.argosy.ui.components.keyboard.KeyboardLayout
import com.nendo.argosy.ui.components.keyboard.ShiftRule
import com.squareup.moshi.JsonDataException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutFilesTest {

    private val assetDir = listOf(File("src/main/assets/keyboard"), File("app/src/main/assets/keyboard"))
        .first { it.isDirectory }

    private fun readFile(id: String): String? = File(assetDir, "$id.json").takeIf { it.isFile }?.readText()

    private fun load(id: String): KeyboardLayout = parseLayout(readFile(id)!!, ::readFile)

    private val allFileIds = assetDir.listFiles { file -> file.extension == "json" }!!.map { it.nameWithoutExtension }

    @Test
    fun `every known layout id has a file`() {
        KeyboardLayoutRepository.LAYOUT_IDS.forEach { id ->
            assertTrue("missing $id.json", File(assetDir, "$id.json").isFile)
        }
        assertTrue(File(assetDir, "${KeyboardLayout.SHARED_SYMBOLS_ID}.json").isFile)
    }

    @Test
    fun `every layout file parses with an id matching its file name`() {
        allFileIds.forEach { id -> assertEquals(id, load(id).id) }
    }

    @Test
    fun `ascii only pages hold printable ascii alone`() {
        allFileIds.map(::load).forEach { layout ->
            layout.pages.filter { it.asciiOnly }.forEach { page ->
                page.rows.flatMap { it.keys }.filterIsInstance<KeyboardKey.Character>().forEach { key ->
                    (key.text + key.shifted.orEmpty()).forEach { char ->
                        assertTrue(
                            "${layout.id}/${page.id} has non-ascii '$char'",
                            char.code in 0x20..0x7E
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `every letters page has four rows and the engine adds a fifth`() {
        KeyboardLayoutRepository.LAYOUT_IDS.map(::load).forEach { layout ->
            val letters = layout.page(KeyboardLayout.LETTERS_PAGE)!!
            assertEquals(layout.id, 4, letters.rows.size)
            val expected = if (layout.id == KeyboardLayout.NUMERIC_LAYOUT_ID) 4 else 5
            assertEquals(layout.id, expected, KeyboardEngine.composeRows(layout, letters, KeyboardFieldType.TEXT).size)
        }
    }

    @Test
    fun `no page of any file has an empty row`() {
        allFileIds.map(::load).forEach { layout ->
            layout.pages.forEach { page ->
                assertTrue("${layout.id}/${page.id}", page.rows.isNotEmpty())
                page.rows.forEach { assertTrue("${layout.id}/${page.id}", it.keys.isNotEmpty()) }
            }
        }
    }

    @Test
    fun `symbols page resolves through the shared reference`() {
        val layout = load(KeyboardLayout.QWERTY_LAYOUT_ID)
        val symbols = layout.page(KeyboardLayout.SYMBOLS_PAGE)!!
        assertTrue(symbols.asciiOnly)
        assertEquals(ShiftRule.None, symbols.shift)
        assertEquals(4, symbols.rows.size)
        assertEquals(KeyboardKey.Function(FunctionKey.BACKSPACE), symbols.rows[3].keys.last())
    }

    @Test
    fun `letter layouts with a symbols page carry the dollar currency accents`() {
        KeyboardLayoutRepository.LAYOUT_IDS
            .filter { it != KeyboardLayout.NUMERIC_LAYOUT_ID }
            .map(::load)
            .forEach { assertEquals(it.id, 6, it.accents["$"]?.size) }
    }

    @Test
    fun `string pad fn and k entries map to their key types`() {
        val json = """
            {"id": "fixture", "version": 1, "script": "Latn", "locales": ["en"], "composer": "none",
             "pages": {"letters": {"shift": "upper", "rows": [
               ["a", {"pad": 0.5}, {"fn": "backspace", "w": 1.5}, {"k": "1", "s": "!"}]
             ]}}}
        """.trimIndent()
        val keys = parseLayout(json, { null }).pages.single().rows.single().keys
        assertEquals(
            listOf(
                KeyboardKey.Character("a"),
                KeyboardKey.Gap(0.5f),
                KeyboardKey.Function(FunctionKey.BACKSPACE, 1.5f),
                KeyboardKey.Character("1", "!")
            ),
            keys
        )
    }

    @Test(expected = JsonDataException::class)
    fun `unknown function token is rejected`() {
        val json = """
            {"id": "fixture", "version": 1, "script": "Latn", "composer": "none",
             "pages": {"letters": {"rows": [[{"fn": "teleport"}]]}}}
        """.trimIndent()
        parseLayout(json, { null })
    }

    @Test(expected = JsonDataException::class)
    fun `missing reference is rejected`() {
        val json = """
            {"id": "fixture", "version": 1, "script": "Latn", "composer": "none",
             "pages": {"symbols": {"ref": "absent"}}}
        """.trimIndent()
        parseLayout(json, { null })
    }
}
