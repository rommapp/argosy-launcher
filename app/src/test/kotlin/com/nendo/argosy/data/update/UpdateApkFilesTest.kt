package com.nendo.argosy.data.update

import com.nendo.argosy.data.model.AppUpdateRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class UpdateApkFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var dir: File
    private lateinit var files: UpdateApkFiles

    private val payload = "argosy apk bytes".toByteArray()
    private val payloadSha = MessageDigest.getInstance("SHA-256").digest(payload)
        .joinToString("") { "%02x".format(it) }

    @Before
    fun setUp() {
        dir = folder.newFolder("updates")
        files = UpdateApkFiles(dir)
    }

    private fun release(
        tag: String = "v3.1.0",
        size: Long = payload.size.toLong(),
        digest: String? = "sha256:$payloadSha",
        abi: String = "arm64"
    ) = AppUpdateRelease(
        tag = tag,
        name = tag,
        notes = null,
        assetName = "argosy-$tag-$abi.apk",
        assetUrl = "https://example/argosy-$tag-$abi.apk",
        assetSize = size,
        assetDigest = digest,
        abi = abi
    )

    private fun store(release: AppUpdateRelease, bytes: ByteArray = payload): File =
        files.fileFor(release).also { it.writeBytes(bytes) }

    @Test
    fun `stored files are named by tag and abi`() {
        assertEquals("argosy-v3.1.0-beta.2-x86_64.apk", files.fileFor(release(tag = "v3.1.0-beta.2", abi = "x86_64")).name)
    }

    @Test
    fun `a complete file with matching size and digest is reused`() {
        val release = release()
        val stored = store(release)
        assertEquals(stored, files.reusable(release))
    }

    @Test
    fun `a file of the wrong size is not reused`() {
        val release = release()
        store(release, payload.copyOf(payload.size - 1))
        assertNull(files.reusable(release))
    }

    @Test
    fun `a file of the right size with a different digest is not reused`() {
        val release = release()
        val tampered = payload.copyOf().also { it[0] = (it[0] + 1).toByte() }
        store(release, tampered)
        assertNull(files.reusable(release))
    }

    @Test
    fun `a release without a digest is reused on size alone`() {
        val release = release(digest = null)
        val tampered = payload.copyOf().also { it[0] = (it[0] + 1).toByte() }
        store(release, tampered)
        assertNotNull(files.reusable(release))
    }

    @Test
    fun `a digest of another algorithm falls back to the size check`() {
        val release = release(digest = "sha512:abcd")
        store(release)
        assertNotNull(files.reusable(release))
    }

    @Test
    fun `an uppercase digest matches`() {
        val release = release(digest = "sha256:${payloadSha.uppercase()}")
        store(release)
        assertNotNull(files.reusable(release))
    }

    @Test
    fun `no stored file is nothing to reuse`() {
        assertNull(files.reusable(release()))
    }

    @Test
    fun `commit renames a matching part file into place`() {
        val release = release()
        files.newPart(release).writeBytes(payload)
        val committed = files.commit(release)
        assertEquals(files.fileFor(release), committed)
        assertTrue(committed.isFile)
        assertFalse(files.partFor(release).exists())
    }

    @Test
    fun `commit rejects and deletes a short part file`() {
        val release = release()
        files.newPart(release).writeBytes(payload.copyOf(3))
        val failure = runCatching { files.commit(release) }.exceptionOrNull()
        assertTrue(failure is UpdateFileMismatchException)
        assertFalse(files.partFor(release).exists())
        assertFalse(files.fileFor(release).exists())
    }

    @Test
    fun `commit rejects a part file whose digest differs`() {
        val release = release()
        files.newPart(release).writeBytes(payload.copyOf().also { it[1] = (it[1] + 1).toByte() })
        assertTrue(runCatching { files.commit(release) }.exceptionOrNull() is UpdateFileMismatchException)
        assertFalse(files.fileFor(release).exists())
    }

    @Test
    fun `cleanup deletes versions at or below the installed one and every part file`() {
        val older = store(release(tag = "v2.9.0"))
        val same = store(release(tag = "v3.0.0"))
        val newer = store(release(tag = "v3.1.0"))
        val newerBeta = store(release(tag = "v3.1.0-beta.1", abi = "universal"))
        val part = File(dir, "argosy-v3.2.0-arm64.apk.part").also { it.writeBytes(payload) }
        val unrelated = File(dir, "notes.txt").also { it.writeText("keep") }

        val removed = files.cleanup("3.0.0")

        assertEquals(setOf(older, same, part), removed.toSet())
        assertFalse(older.exists())
        assertFalse(same.exists())
        assertFalse(part.exists())
        assertTrue(newer.exists())
        assertTrue(newerBeta.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun `cleanup deletes a prerelease once its stable version is installed`() {
        val beta = store(release(tag = "v3.0.0-beta.2"))
        files.cleanup("3.0.0")
        assertFalse(beta.exists())
    }

    @Test
    fun `cleanup deletes a stored update whose name carries no version`() {
        val unnamed = File(dir, "argosy-.apk").also { it.writeBytes(payload) }
        files.cleanup("3.0.0")
        assertFalse(unnamed.exists())
    }

    @Test
    fun `cleanup of a missing folder removes nothing`() {
        assertTrue(UpdateApkFiles(File(dir, "absent")).cleanup("3.0.0").isEmpty())
    }

    @Test
    fun `delete removes the stored file and its part file`() {
        val release = release()
        store(release)
        files.partFor(release).writeBytes(payload)
        files.delete(release)
        assertFalse(files.fileFor(release).exists())
        assertFalse(files.partFor(release).exists())
    }

    @Test
    fun `legacy cache apks are deleted and other cache files kept`() {
        val cache = folder.newFolder("cache")
        val legacy = File(cache, "argosy-v2.30.0.apk").also { it.writeBytes(payload) }
        val other = File(cache, "cover.png").also { it.writeBytes(payload) }
        val removed = UpdateApkFiles.deleteLegacyCacheApks(cache)
        assertEquals(listOf(legacy), removed)
        assertFalse(legacy.exists())
        assertTrue(other.exists())
    }

    @Test
    fun `tag is read back from a stored file name`() {
        assertEquals("v3.1.0-beta.2", UpdateApkFiles.tagOf("argosy-v3.1.0-beta.2-x86_64.apk"))
        assertEquals("v3.1.0", UpdateApkFiles.tagOf("argosy-v3.1.0-universal.apk"))
        assertNull(UpdateApkFiles.tagOf("argosy-.apk"))
    }
}
