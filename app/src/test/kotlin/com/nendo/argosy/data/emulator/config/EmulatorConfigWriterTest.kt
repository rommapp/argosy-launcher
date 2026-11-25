package com.nendo.argosy.data.emulator.config

import com.nendo.argosy.data.emulator.EdenDriverConfig
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.util.RootShell
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EmulatorConfigWriterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val pkg = "dev.eden.eden_emulator"

    private lateinit var dataRoot: File
    private lateinit var config: File

    @Before
    fun placeConfig() {
        dataRoot = File(temp.root, "Android/data/$pkg/files").apply { mkdirs() }
        config = File(dataRoot, "config/config.ini").apply {
            parentFile?.mkdirs()
            writeText(ConfigFixtures.text("config.ini"))
        }
    }

    private class FakeRoot(var aliveChecks: Int = 0) : ConfigRootRunner {
        val scripts = mutableListOf<String>()
        var tamperOnReplace: ((ByteArray) -> ByteArray)? = null
        var failReplace = false

        override fun run(script: String): RootShell.Result {
            scripts += script
            val quoted = QUOTED.findAll(script).map { it.groupValues[1] }.distinct().toList()
            return when {
                script.startsWith("am force-stop") -> {
                    val alive = aliveChecks > 0
                    if (alive) aliveChecks--
                    RootShell.Result(0, if (alive) listOf("4242") else emptyList())
                }
                "mv -f" in script -> {
                    if (failReplace) return RootShell.Result(6, emptyList())
                    val target = File(quoted[0])
                    val staging = File(CAT.find(script)!!.groupValues[1])
                    val bytes = staging.readBytes()
                    target.writeBytes(tamperOnReplace?.invoke(bytes) ?: bytes)
                    RootShell.Result(0, emptyList())
                }
                else -> {
                    val target = File(quoted[0])
                    if (!target.isFile) return RootShell.Result(ConfigRootScripts.EXIT_MISSING, emptyList())
                    target.copyTo(File(quoted[1]), overwrite = true)
                    RootShell.Result(0, emptyList())
                }
            }
        }

        companion object {
            val QUOTED = Regex("'([^']*)'")
            val CAT = Regex("cat '([^']*)' > '[^']*' \\|\\| \\{")
        }
    }

    private fun engine(root: FakeRoot, locks: EmulatorEditLocks = EmulatorEditLocks()) = ConfigWriteEngine(
        root = root,
        fal = realFsFal(),
        backups = ConfigBackupStore(File(temp.root, "backups")),
        locks = locks,
        workDir = File(temp.root, "work"),
        pause = {}
    )

    private fun capabilities(edit: Boolean = true, writeAppData: Boolean = true) = EmulatorDataCapabilities(
        dataRoot = dataRoot.absolutePath,
        readRoute = EmulatorDataRoute.FILE_ACCESS,
        writeAppDataRoute = EmulatorDataRoute.FILE_ACCESS.takeIf { writeAppData },
        editRoute = EmulatorDataRoute.ROOT.takeIf { edit },
        editBlock = EmulatorEditBlock.NO_ROOT.takeIf { !edit }
    )

    private val spec = EdenConfigSpec.SPEC
    private val option = spec.options.single { it.key == "use_vsync" }

    private fun storedVsync(): String? =
        ConfigFileReaders.ini(config.readText()).lastOrNull { it.section == "Renderer" && it.key == "use_vsync" }?.value

    @Test
    fun `an edit force-stops, backs up, writes through a temp file and verifies`() = runBlocking {
        val original = config.readBytes()
        val root = FakeRoot(aliveChecks = 1)

        val result = engine(root).write(spec, option, "1", pkg, capabilities())

        assertEquals(ConfigWriteResult.Written, result)
        assertEquals("1", storedVsync())
        assertEquals(2, root.scripts.count { it.startsWith("am force-stop") })
        val backups = ConfigBackupStore(File(temp.root, "backups")).list(pkg, option.file)
        assertEquals(1, backups.size)
        assertArrayEquals(original, backups.single().readBytes())
        val replace = root.scripts.single { "mv -f" in it }
        assertTrue(replace.contains("stat -c %u:%g") && replace.contains("chcon") && replace.contains(".argosy-tmp"))
    }

    @Test
    fun `a package data file is edited through root in the package's private folder only`() = runBlocking {
        val renderer = Armsx2ConfigSpec.SPEC.options.single { it.key == "renderer" }
        val root = FakeRoot()

        val noRoot = engine(root).write(Armsx2ConfigSpec.SPEC, renderer, "vulkan", "com.armsx2", capabilities(edit = false))
        val withRoot = engine(root).write(Armsx2ConfigSpec.SPEC, renderer, "vulkan", "com.armsx2", capabilities())

        assertEquals(ConfigWriteResult.Failed(ConfigWriteFailure.NO_EDIT_ROUTE), noRoot)
        assertEquals(ConfigWriteResult.Failed(ConfigWriteFailure.FILE_MISSING), withRoot)
        assertTrue(root.scripts.any { "'/data/data/com.armsx2/shared_prefs/ARMSX2.xml'" in it })
        assertTrue(root.scripts.none { dataRoot.absolutePath in it })
    }

    @Test
    fun `an emulator that stays alive refuses the edit and leaves the file`() = runBlocking {
        val original = config.readBytes()
        val root = FakeRoot(aliveChecks = Int.MAX_VALUE)

        val result = engine(root).write(spec, option, "1", pkg, capabilities())

        assertEquals(ConfigWriteResult.Failed(ConfigWriteFailure.EMULATOR_RUNNING), result)
        assertArrayEquals(original, config.readBytes())
        assertTrue(root.scripts.none { "mv -f" in it })
    }

    @Test
    fun `a write that does not read back restores the backup`() = runBlocking {
        val original = config.readBytes()
        val root = FakeRoot()
        var calls = 0
        root.tamperOnReplace = { bytes -> if (calls++ == 0) bytes.toString(Charsets.UTF_8).replace("use_vsync=1", "use_vsync=3").toByteArray() else bytes }

        val result = engine(root).write(spec, option, "1", pkg, capabilities())

        assertEquals(ConfigWriteResult.Failed(ConfigWriteFailure.VERIFY_FAILED), result)
        assertArrayEquals(original, config.readBytes())
        assertTrue(ConfigBackupStore(File(temp.root, "backups")).list(pkg, option.file).isEmpty())
    }

    @Test
    fun `undo restores the bytes from before the edit and spends the backup`() = runBlocking {
        val original = config.readBytes()
        val root = FakeRoot()
        val engine = engine(root)

        engine.write(spec, option, "0", pkg, capabilities())
        assertEquals(listOf("use_vsync" to "0"), engine.pendingUndos(spec, pkg).map { it.second.key to it.second.written })
        val undone = engine.undo(spec, option, pkg, capabilities())

        assertEquals(ConfigWriteResult.Written, undone)
        assertArrayEquals(original, config.readBytes())
        assertNull(ConfigBackupStore(File(temp.root, "backups")).newest(pkg, option.file))
        assertTrue(engine.pendingUndos(spec, pkg).isEmpty())
        assertEquals(ConfigWriteResult.Failed(ConfigWriteFailure.NO_BACKUP), engine.undo(spec, option, pkg, capabilities()))
    }

    @Test
    fun `undo after the emulator rewrote its file reverts the option alone`() = runBlocking {
        val engine = engine(FakeRoot())
        val before = storedVsync()
        engine.write(spec, option, "0", pkg, capabilities())
        config.appendText("\n[Added]\nby_emulator=1\n")

        val undone = engine.undo(spec, option, pkg, capabilities())

        assertEquals(ConfigWriteResult.Written, undone)
        assertEquals(before, storedVsync())
        assertTrue(config.readText().contains("by_emulator=1"))
    }

    @Test
    fun `undo refuses when the emulator changed the option since`() = runBlocking {
        val engine = engine(FakeRoot())
        engine.write(spec, option, "0", pkg, capabilities())
        config.writeText(config.readText().replace("use_vsync=0", "use_vsync=2"))
        val changed = config.readBytes()

        val undone = engine.undo(spec, option, pkg, capabilities())

        assertEquals(ConfigWriteResult.Failed(ConfigWriteFailure.CHANGED_SINCE), undone)
        assertArrayEquals(changed, config.readBytes())
    }

    @Test
    fun `no edit route, invalid values and non-editable options never touch the emulator`() = runBlocking {
        val original = config.readBytes()
        val root = FakeRoot()
        val engine = engine(root)
        val backend = spec.options.single { it.key == "backend" }

        assertEquals(
            ConfigWriteResult.Failed(ConfigWriteFailure.NO_EDIT_ROUTE),
            engine.write(spec, option, "1", pkg, capabilities(edit = false))
        )
        assertEquals(
            ConfigWriteResult.Failed(ConfigWriteFailure.INVALID_VALUE),
            engine.write(spec, option, "9", pkg, capabilities())
        )
        assertEquals(
            ConfigWriteResult.Failed(ConfigWriteFailure.NOT_EDITABLE),
            engine.write(spec, backend, "0", pkg, capabilities())
        )
        assertTrue(root.scripts.isEmpty())
        assertArrayEquals(original, config.readBytes())
    }

    @Test
    fun `a failed replace leaves the file and reports the write`() = runBlocking {
        val original = config.readBytes()
        val root = FakeRoot().apply { failReplace = true }

        val result = engine(root).write(spec, option, "1", pkg, capabilities())

        assertEquals(ConfigWriteResult.Failed(ConfigWriteFailure.WRITE_FAILED), result)
        assertArrayEquals(original, config.readBytes())
    }

    @Test
    fun `a driver key writes through app-data access without root`() = runBlocking {
        val root = FakeRoot()
        val driver = "${dataRoot.absolutePath}/gpu_drivers/turnip.adpkg.zip"

        val result = engine(root).write(spec, EdenConfigSpec.DRIVER_PATH, driver, pkg, capabilities(edit = false))

        assertEquals(ConfigWriteResult.Written, result)
        assertTrue(root.scripts.isEmpty())
        assertEquals(driver, EdenDriverConfig.driverPath(config.readText()))
        assertFalse(config.readText().contains("turnip_mrpurple"))
    }

    @Test
    fun `a driver path outside the driver folder is refused`() = runBlocking {
        val result = engine(FakeRoot()).write(
            spec, EdenConfigSpec.DRIVER_PATH, "${dataRoot.absolutePath}/nand/x.zip", pkg, capabilities(edit = false)
        )

        assertEquals(ConfigWriteResult.Failed(ConfigWriteFailure.INVALID_VALUE), result)
    }
}
