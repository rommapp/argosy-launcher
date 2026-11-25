package com.nendo.argosy.data.music.analysis

import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicTrackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin

class TrackAnalysisStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()
    private val sampleRate = 44_100
    private val frames = TrackAnalysis.BLOCK_FRAMES * 4 + 123

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    private fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }

    private fun sineDecoder(calls: AtomicInteger) = object : TrackPcmDecoder {
        override suspend fun decode(source: MusicTrackSource, sink: TrackPcmSink): Boolean {
            calls.incrementAndGet()
            sink.onFormat(sampleRate, 1)
            val chunk = 1000
            var at = 0
            while (at < frames) {
                val count = minOf(chunk, frames - at)
                val start = at
                val samples = FloatArray(count) { i ->
                    (0.5 * sin(2.0 * PI * 440.0 * (start + i) / sampleRate)).toFloat()
                }
                sink.onFrames(samples, count)
                at += count
            }
            return true
        }
    }

    private fun failingDecoder(calls: AtomicInteger) = object : TrackPcmDecoder {
        override suspend fun decode(source: MusicTrackSource, sink: TrackPcmSink): Boolean {
            calls.incrementAndGet()
            return false
        }
    }

    private fun sidecarFor(dir: File, id: String): File {
        val hex = MessageDigest.getInstance("SHA-1").digest(id.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hex.tan")
    }

    private fun expectedSidecarBytes(): Long {
        val blocks = (frames + TrackAnalysis.BLOCK_FRAMES - 1) / TrackAnalysis.BLOCK_FRAMES
        return TrackAnalysisSidecar.HEADER_BYTES + blocks * (4L + TrackAnalysis.BAND_COUNT)
    }

    private fun localTrack(): MusicQueueTrack {
        val audio = temp.newFile("song.ogg").apply { writeBytes(ByteArray(64) { it.toByte() }) }
        return MusicQueueTrack(id = "track-1", source = MusicTrackSource.Local(audio.absolutePath), title = "Song")
    }

    @Test
    fun `sidecar round trips`() {
        val analysis = GrowingTrackAnalysis(sampleRate, 1).apply {
            add(FloatArray(frames) { i -> sin(i * 0.01f) }, frames)
        }.snapshot()
        val file = File(temp.root, "a.tan")
        TrackAnalysisSidecar.write(analysis, 77L, file)
        val read = TrackAnalysisSidecar.read(file, 77L)
        assertNotNull(read)
        assertEquals(analysis.sampleRate, read!!.sampleRate)
        assertEquals(analysis.frames, read.frames)
        assertArrayEquals(analysis.peaks, read.peaks)
        assertArrayEquals(analysis.bands, read.bands)
        assertNull(TrackAnalysisSidecar.read(file, 78L))
        assertEquals(false, File(temp.root, "a.tan.partial").exists())
    }

    @Test
    fun `truncated sidecar is rejected`() {
        val analysis = GrowingTrackAnalysis(sampleRate, 1).apply { add(FloatArray(frames), frames) }.snapshot()
        val file = File(temp.root, "b.tan")
        TrackAnalysisSidecar.write(analysis, 1L, file)
        file.writeBytes(file.readBytes().copyOf(file.length().toInt() - 1))
        assertNull(TrackAnalysisSidecar.read(file, 1L))
    }

    @Test
    fun `mismatched sidecar is rebuilt and then served from cache`() = runBlocking {
        val dir = temp.newFolder("music-analysis")
        val track = localTrack()
        val sidecar = sidecarFor(dir, track.id)
        sidecar.writeBytes(ByteArray(200) { 7 })

        val decodes = AtomicInteger()
        val store = TrackAnalysisStore({ dir }, sineDecoder(decodes), scope())
        val built = withTimeout(TIMEOUT_MS) {
            store.analysis(track).first { it != null && it.frames == frames.toLong() }
        }
        assertEquals(1, decodes.get())
        withTimeout(TIMEOUT_MS) {
            while (sidecar.length() != expectedSidecarBytes()) delay(10)
        }

        val cachedDecodes = AtomicInteger()
        val cachedStore = TrackAnalysisStore({ dir }, failingDecoder(cachedDecodes), scope())
        val cached = withTimeout(TIMEOUT_MS) { cachedStore.analysis(track).first { it != null } }
        assertEquals(0, cachedDecodes.get())
        assertArrayEquals(built!!.peaks, cached!!.peaks)
        assertArrayEquals(built.bands, cached.bands)
    }

    @Test
    fun `sidecar for another version of the source is rebuilt`() = runBlocking {
        val dir = temp.newFolder("music-analysis")
        val track = localTrack()
        val stale = GrowingTrackAnalysis(sampleRate, 1).apply { add(FloatArray(100), 100) }.snapshot()
        TrackAnalysisSidecar.write(stale, 42L, sidecarFor(dir, track.id))

        val decodes = AtomicInteger()
        val store = TrackAnalysisStore({ dir }, sineDecoder(decodes), scope())
        val built = withTimeout(TIMEOUT_MS) {
            store.analysis(track).first { it != null && it.frames == frames.toLong() }
        }
        assertEquals(1, decodes.get())
        assertEquals(frames.toLong(), built!!.frames)
    }

    @Test
    fun `undecodable track stays null`() = runBlocking {
        val dir = temp.newFolder("music-analysis")
        val track = localTrack()
        val decodes = AtomicInteger()
        val store = TrackAnalysisStore({ dir }, failingDecoder(decodes), scope())
        val flow = store.analysis(track)
        withTimeout(TIMEOUT_MS) {
            while (decodes.get() == 0) delay(10)
        }
        delay(50)
        assertNull(flow.value)
        assertEquals(false, sidecarFor(dir, track.id).exists())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
