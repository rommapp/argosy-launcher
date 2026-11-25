package com.nendo.argosy.data.emulator.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigPatchersTest {

    private fun EmulatorConfigSpec.option(section: String, key: String): ConfigOption =
        options.single { it.section == section && it.key == key }

    private fun changedLines(before: String, after: String): List<Pair<String?, String?>> {
        val a = before.split("\n")
        val b = after.split("\n")
        return (0 until maxOf(a.size, b.size))
            .map { a.getOrNull(it) to b.getOrNull(it) }
            .filter { it.first != it.second }
    }

    private fun decoded(spec: EmulatorConfigSpec, option: ConfigOption, text: String): ConfigOptionSnapshot =
        ConfigOptionDecoder.decode(spec, option, ConfigFileReaders.read(option.file.format, text))

    @Test
    fun `eden edit sets the value and clears the compiled default flag in place`() {
        val spec = EdenConfigSpec.SPEC
        val option = spec.option("Renderer", "resolution_setup")
        val before = ConfigFixtures.text("config.ini")

        val after = requireNotNull(ConfigPatchers.patch(spec, option, "6", before))

        assertEquals(
            listOf(
                "resolution_setup\\default=true" to "resolution_setup\\default=false",
                "resolution_setup=3" to "resolution_setup=6"
            ),
            changedLines(before, after)
        )
        val read = decoded(spec, option, after)
        assertEquals("6", read.raw)
        assertEquals("2x", read.valueName)
    }

    @Test
    fun `eden edit round trips through the reader and leaves every other byte`() {
        val spec = EdenConfigSpec.SPEC
        val option = spec.option("Renderer", "use_vsync")
        val before = ConfigFixtures.text("config.ini")

        val changed = requireNotNull(ConfigPatchers.patch(spec, option, "1", before))
        val restored = requireNotNull(ConfigPatchers.patch(spec, option, "2", changed))

        assertEquals(before.replace("use_vsync\\default=true", "use_vsync\\default=false"), restored)
        assertEquals(before.length + 1, restored.length)
    }

    @Test
    fun `ini edit keeps crlf line endings`() {
        val spec = EdenConfigSpec.SPEC
        val option = spec.option("Renderer", "use_vsync")
        val before = ConfigFixtures.text("config.ini").replace("\n", "\r\n")

        val after = requireNotNull(ConfigPatchers.patch(spec, option, "0", before))

        assertTrue(after.contains("use_vsync\\default=false\r\nuse_vsync=0\r\n"))
        assertEquals(after.count { it == '\n' }, after.split("\r\n").size - 1)
    }

    @Test
    fun `eden key missing from its section goes in below the header with its flag`() {
        val text = "[Renderer]\nbackend\\default=true\nbackend=1\n\n[Audio]\nvolume=100\n"

        val after = IniPatcher.set(text, "Renderer", "anti_aliasing", "2", ignoreCase = false, defaultFlagSuffix = "\\default")

        assertEquals(
            "[Renderer]\nanti_aliasing\\default=false\nanti_aliasing=2\nbackend\\default=true\nbackend=1\n\n[Audio]\nvolume=100\n",
            after
        )
    }

    @Test
    fun `dolphin edit matches keys and sections without regard to case and keeps its spacing`() {
        val spec = DolphinConfigSpec.SPEC
        val option = spec.option("Core", "CPUThread")
        val before = "[core]\ncputhread = True\n[DSP]\nVolume = 100\n"

        val after = requireNotNull(ConfigPatchers.patch(spec, option, "False", before))

        assertEquals("[core]\ncputhread = False\n[DSP]\nVolume = 100\n", after)
    }

    @Test
    fun `dolphin writes a missing key into its existing section`() {
        val spec = DolphinConfigSpec.SPEC
        val option = spec.option("DSP", "Volume")
        val before = ConfigFixtures.text("Dolphin.ini")

        val after = requireNotNull(ConfigPatchers.patch(spec, option, "80", before))

        assertEquals(before.replace("[DSP]\n", "[DSP]\nVolume = 80\n"), after)
        assertEquals("80", decoded(spec, option, after).raw)
    }

    @Test
    fun `dolphin writes a missing section at the end and into an empty file`() {
        val spec = DolphinConfigSpec.SPEC
        val option = spec.option("Settings", "InternalResolution")
        val dolphinIni = ConfigFixtures.text("Dolphin.ini")

        val appended = IniPatcher.set(dolphinIni, "Settings", "InternalResolution", "3", ignoreCase = true, defaultFlagSuffix = null)
        val fresh = requireNotNull(ConfigPatchers.patch(spec, option, "3", ""))

        assertEquals("$dolphinIni\n[Settings]\nInternalResolution = 3\n", appended)
        assertEquals("[Settings]\nInternalResolution=3\n", fresh)
        assertEquals("3", decoded(spec, option, fresh).raw)
    }

    @Test
    fun `cemu edit replaces the element text and nothing else`() {
        val spec = CemuConfigSpec.SPEC
        val option = spec.option("Graphic/Overlay", "Position")
        val before = ConfigFixtures.text("settings.xml")

        val after = requireNotNull(ConfigPatchers.patch(spec, option, "3", before))

        assertEquals(
            listOf("            <Position>0</Position>" to "            <Position>3</Position>"),
            changedLines(before, after)
        )
        assertEquals("1", decoded(spec, spec.option("Graphic/Notification", "Position"), after).raw)
        assertEquals("3", decoded(spec, option, after).raw)
    }

    @Test
    fun `cemu edit fills an empty element and escapes the value`() {
        val spec = CemuConfigSpec.SPEC
        val path = "/storage/emulated/0/Android/data/info.cemu.cemu/files/customDrivers/a&b"
        val before = ConfigFixtures.text("settings.xml")

        val after = requireNotNull(ConfigPatchers.patch(spec, CemuConfigSpec.CUSTOM_DRIVER_PATH, path, before))

        assertTrue(after.contains("<custom_driver_path>/storage/emulated/0/Android/data/info.cemu.cemu/files/customDrivers/a&amp;b</custom_driver_path>"))
        assertEquals(path, decoded(spec, CemuConfigSpec.CUSTOM_DRIVER_PATH, after).raw)
    }

    @Test
    fun `cemu edit fills a self-closing element and refuses a missing or parent element`() {
        val text = "<content>\n    <a/>\n    <b><c>1</c></b>\n</content>\n"

        assertEquals("<content>\n    <a>5</a>\n    <b><c>1</c></b>\n</content>\n", XmlPatcher.set(text, "", "a", "5"))
        assertNull(XmlPatcher.set(text, "", "b", "5"))
        assertNull(XmlPatcher.set(text, "", "missing", "5"))
    }

    @Test
    fun `vita3k edit replaces the top-level line and nothing else`() {
        val spec = Vita3kConfigSpec.SPEC
        val option = spec.option("", "screen-filter")
        val before = ConfigFixtures.text("config.yml")

        val after = requireNotNull(ConfigPatchers.patch(spec, option, "Bicubic", before))

        assertEquals(listOf("screen-filter: FXAA" to "screen-filter: Bicubic"), changedLines(before, after))
        assertEquals("Bicubic", decoded(spec, option, after).raw)
    }

    @Test
    fun `vita3k edit keeps quotes, refuses a block key and adds a missing key before the end marker`() {
        val before = ConfigFixtures.text("config.yml")

        val quoted = requireNotNull(YamlPatcher.set(before, "user-lang", "en"))
        val added = requireNotNull(YamlPatcher.set(before, "fps-limit", "60"))

        assertTrue(quoted.contains("\nuser-lang: \"en\"\n"))
        assertNull(YamlPatcher.set(before, "controller-binds", "1"))
        assertTrue(added.contains("  - 4\nfps-limit: 60\n..."))
    }

    @Test
    fun `json files are never patched`() {
        val spec = Armsx2ConfigSpec.SPEC
        val option = ConfigOption(Armsx2ConfigSpec.MIRROR, "global", "renderer", ConfigValueType.ENUM, "auto")

        assertNull(ConfigPatchers.patch(spec, option, "vulkan", ConfigFixtures.text("armsx2-settings.json")))
    }

    private val prefs = ConfigFixtures.text("ARMSX2.xml")
    private fun armsx2(key: String) = Armsx2ConfigSpec.SPEC.options.single { it.section == "config.global" && it.key == key }

    private fun globalJson(text: String): String {
        val entries = requireNotNull(ConfigFileReaders.prefsJson(text))
        return entries.filter { it.section == "config.global" }.joinToString { "${it.key}=${it.value}" }
    }

    @Test
    fun `armsx2 prefs patch one json key and keep every other byte`() {
        val patched = requireNotNull(ConfigPatchers.patch(Armsx2ConfigSpec.SPEC, armsx2("renderer"), "opengl", prefs))

        assertEquals(prefs.replace("&quot;renderer&quot;:&quot;vulkan&quot;", "&quot;renderer&quot;:&quot;opengl&quot;"), patched)
    }

    @Test
    fun `armsx2 prefs keep a stored value's kind`() {
        val bool = requireNotNull(ConfigPatchers.patch(Armsx2ConfigSpec.SPEC, armsx2("vsyncEnable"), "true", prefs))
        val ordinal = requireNotNull(ConfigPatchers.patch(Armsx2ConfigSpec.SPEC, armsx2("aspectRatio"), "3", prefs))

        assertTrue(bool.contains("&quot;vsyncEnable&quot;:true,"))
        assertTrue(ordinal.contains("&quot;aspectRatio&quot;:3,"))
        assertTrue(globalJson(ordinal).contains("aspectRatio=3"))
    }

    @Test
    fun `armsx2 prefs add a missing key before the closing brace`() {
        val patched = requireNotNull(ConfigPatchers.patch(Armsx2ConfigSpec.SPEC, armsx2("upscaler"), "2", prefs))

        assertTrue(patched.contains(",&quot;upscaler&quot;:2}</string>"))
        assertTrue(globalJson(patched).contains("upscaler=2"))
    }

    @Test
    fun `armsx2 prefs skip nested values and match only top level keys`() {
        val json = "{\"games\":{\"renderer\":\"software\"},\"list\":[1,{\"a\":2}],\"renderer\":\"auto\"}"

        val patched = requireNotNull(JsonTopLevel.set(json, "renderer", "vulkan", quoted = true))

        assertEquals("{\"games\":{\"renderer\":\"software\"},\"list\":[1,{\"a\":2}],\"renderer\":\"vulkan\"}", patched)
    }

    @Test
    fun `armsx2 prefs refuse escaping android would not write`() {
        val odd = prefs.replace("&quot;renderer&quot;", "&#34;renderer&#34;")

        assertNull(ConfigPatchers.patch(Armsx2ConfigSpec.SPEC, armsx2("renderer"), "opengl", odd))
    }

    @Test
    fun `armsx2 prefs without the global preference are not patched`() {
        val empty = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map />\n"

        assertNull(ConfigPatchers.patch(Armsx2ConfigSpec.SPEC, armsx2("renderer"), "opengl", empty))
    }
}
