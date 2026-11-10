package com.nendo.argosy.data.storage

import com.nendo.argosy.data.storage.AndroidDataMirror.Action
import com.nendo.argosy.data.storage.AndroidDataMirror.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDataMirrorPlanTest {

    private val key = "/data/media/0/Android/data/com.github.stenzek.duckstation/files/memcards/Game_1.mcd"
    private val dirKey = "/data/media/0/Android/data/com.github.stenzek.duckstation/files/memcards/sub"
    private val old = Entry(isDirectory = false, size = 131072, mtimeSec = 1_000)
    private val emulatorWrote = Entry(isDirectory = false, size = 131072, mtimeSec = 2_000)
    private val argosyWrote = Entry(isDirectory = false, size = 131072, mtimeSec = 3_000)
    private val dir = Entry(isDirectory = true, size = 0, mtimeSec = 0)
    private val cap = 512L * 1024 * 1024

    private fun planOne(real: Entry?, mirror: Entry?, base: Entry?, k: String = key): List<Action> =
        AndroidDataMirror.plan(
            real = listOfNotNull(real?.let { k to it }).toMap(),
            mirror = listOfNotNull(mirror?.let { k to it }).toMap(),
            base = listOfNotNull(base?.let { k to it }).toMap(),
            maxFileBytes = cap
        )

    @Test
    fun `nothing changed on either side does nothing`() {
        assertTrue(planOne(real = old, mirror = old, base = old).isEmpty())
    }

    @Test
    fun `a file never seen before is copied in`() {
        assertEquals(listOf(Action.Pull(key, old)), planOne(real = old, mirror = null, base = null))
    }

    @Test
    fun `an emulator write is copied in`() {
        assertEquals(listOf(Action.Pull(key, emulatorWrote)), planOne(real = emulatorWrote, mirror = old, base = old))
    }

    @Test
    fun `a file the emulator deleted leaves the copy`() {
        assertEquals(listOf(Action.DropFromMirror(key)), planOne(real = null, mirror = old, base = old))
    }

    @Test
    fun `an Argosy write is carried out to an existing file`() {
        assertEquals(listOf(Action.Push(key, argosyWrote, isNew = false)), planOne(real = old, mirror = argosyWrote, base = old))
    }

    @Test
    fun `a file Argosy created is carried out as new`() {
        assertEquals(listOf(Action.Push(key, argosyWrote, isNew = true)), planOne(real = null, mirror = argosyWrote, base = null))
    }

    @Test
    fun `a file Argosy deleted is deleted for real only while the real file is unchanged`() {
        assertEquals(listOf(Action.DeleteReal(key, isDirectory = false)), planOne(real = old, mirror = null, base = old))
    }

    @Test
    fun `when both sides changed differently the emulator's file wins`() {
        assertEquals(listOf(Action.Pull(key, emulatorWrote)), planOne(real = emulatorWrote, mirror = argosyWrote, base = old))
    }

    @Test
    fun `when the emulator deleted a file Argosy changed the deletion wins`() {
        assertEquals(listOf(Action.DropFromMirror(key)), planOne(real = null, mirror = argosyWrote, base = old))
    }

    @Test
    fun `when Argosy deleted a file the emulator changed the emulator's file comes back`() {
        assertEquals(listOf(Action.Pull(key, emulatorWrote)), planOne(real = emulatorWrote, mirror = null, base = old))
    }

    @Test
    fun `when both sides changed to the same state it is adopted without copying`() {
        assertEquals(listOf(Action.Adopt(key, argosyWrote)), planOne(real = argosyWrote, mirror = argosyWrote, base = old))
    }

    @Test
    fun `when both sides created the same file differently the emulator's file wins`() {
        assertEquals(listOf(Action.Pull(key, emulatorWrote)), planOne(real = emulatorWrote, mirror = old, base = null))
    }

    @Test
    fun `files over the size cap are never copied, pushed or deleted`() {
        val huge = Entry(isDirectory = false, size = cap + 1, mtimeSec = 1_000)
        assertTrue(planOne(real = huge, mirror = null, base = null).isEmpty())
        assertTrue(planOne(real = huge, mirror = null, base = huge).isEmpty())
    }

    @Test
    fun `folders compare by kind only`() {
        assertTrue(planOne(real = dir, mirror = dir.copy(mtimeSec = 99), base = dir, k = dirKey).isEmpty())
        assertEquals(listOf(Action.Pull(dirKey, dir)), planOne(real = dir, mirror = null, base = null, k = dirKey))
        assertEquals(listOf(Action.DeleteReal(dirKey, isDirectory = true)), planOne(real = dir, mirror = null, base = dir, k = dirKey))
    }

    @Test
    fun `lowers emulated and sdcard paths and refuses anything outside Android data or obb`() {
        assertEquals("/data/media/0/Android/data/pkg/files/a.sav", AndroidDataMirror.lowerOf("/storage/emulated/0/Android/data/pkg/files/a.sav"))
        assertEquals("/data/media/0/Android/data/pkg/files/a.sav", AndroidDataMirror.lowerOf("/storage/emulated/0/⁦Android/data/pkg/files/a.sav"))
        assertEquals("/data/media/0/Android/data/pkg/files", AndroidDataMirror.lowerOf("/sdcard/Android/data/pkg/files/"))
        assertEquals("/data/media/10/Android/obb/pkg", AndroidDataMirror.lowerOf("/storage/emulated/10/Android/obb/pkg"))
        assertEquals("/data/media/0/Android/data", AndroidDataMirror.lowerOf("/storage/emulated/0/Android/data"))
        assertNull(AndroidDataMirror.lowerOf("/storage/emulated/0/RetroArch/saves/a.srm"))
        assertNull(AndroidDataMirror.lowerOf("/storage/546B-6466/Android/data/pkg/a.sav"))
        assertNull(AndroidDataMirror.lowerOf("/storage/emulated/0/Android/data/pkg/../../x"))
    }

    @Test
    fun `a shell route reaches each key through shared storage and maps its listing back to the same key`() {
        val keys = listOf(
            "/data/media/0/Android/data/pkg/files/a.sav",
            "/data/media/10/Android/obb/pkg",
            "/data/media/0/Android/data"
        )
        keys.forEach { key ->
            val upper = AndroidDataMirror.upperOf(key)
            assertTrue(upper.startsWith("/storage/emulated/"))
            assertEquals(key, AndroidDataMirror.lowerOf(upper))
        }
    }

    @Test
    fun `a folder's parent is its scope except at the top of Android data`() {
        assertEquals("/data/media/0/Android/data/pkg", AndroidDataMirror.parentScope("/data/media/0/Android/data/pkg/files"))
        assertEquals("/data/media/0/Android/data", AndroidDataMirror.parentScope("/data/media/0/Android/data/pkg"))
        assertNull(AndroidDataMirror.parentScope("/data/media/0/Android/data"))
    }

    @Test
    fun `reads toybox stat lines and skips anything that is not a file or folder`() {
        assertEquals(
            "/data/media/0/Android/data/pkg/a|b.sav" to Entry(false, 12, 1700000000),
            AndroidDataMirror.parseStatLine("regular file|12|1700000000|/data/media/0/Android/data/pkg/a|b.sav")
        )
        assertEquals(
            "/data/media/0/Android/data/pkg/e" to Entry(false, 0, 5),
            AndroidDataMirror.parseStatLine("regular empty file|0|5|/data/media/0/Android/data/pkg/e")
        )
        assertEquals(
            "/data/media/0/Android/data/pkg/d" to Entry(true, 0, 5),
            AndroidDataMirror.parseStatLine("directory|3452|5|/data/media/0/Android/data/pkg/d")
        )
        assertNull(AndroidDataMirror.parseStatLine("symbolic link|10|5|/data/media/0/Android/data/pkg/l"))
    }

    @Test
    fun `shallow scopes hold direct children only`() {
        val scope = "/data/media/0/Android/data/pkg"
        assertTrue(AndroidDataMirror.isUnder("$scope/a", scope, recursive = false))
        assertTrue(!AndroidDataMirror.isUnder("$scope/a/b", scope, recursive = false))
        assertTrue(AndroidDataMirror.isUnder("$scope/a/b", scope, recursive = true))
        assertTrue(!AndroidDataMirror.isUnder("${scope}2/a", scope, recursive = true))
    }
}
