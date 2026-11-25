package com.nendo.argosy.data.music.navidrome

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nendo.argosy.data.local.dao.MusicListenDao
import com.nendo.argosy.data.local.entity.MusicListenEntity
import com.nendo.argosy.data.music.identity.MusicAccountReader
import com.nendo.argosy.data.music.identity.MusicAccounts
import com.nendo.argosy.data.preferences.NavidromePreferencesRepository
import com.nendo.argosy.data.remote.navidrome.NavidromeApiClient
import com.nendo.argosy.data.remote.navidrome.NavidromeCredentials
import com.nendo.argosy.data.remote.ssl.UserCertStore
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class NavidromeScrobblerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val ok = """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
    private val owner = 7L
    private val account = "alice@nd"

    private val rows = mutableListOf<MusicListenEntity>()

    private fun listen(id: Long, startedAt: Long, state: String, ownerKey: Long = owner, accountKey: String = account) =
        MusicListenEntity(
            id = id,
            ownerKey = ownerKey,
            startedAt = startedAt,
            endedAt = startedAt + 1,
            localDay = 20261125,
            listenedMs = 200_000,
            endPositionMs = 200_000,
            durationMs = 240_000,
            counted = true,
            endReason = null,
            source = "navidrome",
            sourceAccountKey = accountKey,
            sourceTrackId = "song-$id",
            trackKey = "navidrome:track:song-$id",
            recordingKey = null,
            albumKey = null,
            selectionId = null,
            romId = null,
            romFileId = null,
            isrc = null,
            title = "Song $id",
            artistCredit = null,
            albumTitle = null,
            trackGameTitle = null,
            contextId = null,
            scrobbleState = state,
            scrobbledAt = null
        )

    private fun state(id: Long): String = synchronized(rows) { rows.single { it.id == id }.scrobbleState }

    private fun dao(): MusicListenDao {
        val dao = mockk<MusicListenDao>()
        coEvery { dao.pendingScrobbles(any(), any(), any(), any()) } answers {
            synchronized(rows) {
                rows.filter {
                    it.ownerKey == firstArg<Long>() && it.source == secondArg<String>() &&
                        it.sourceAccountKey == thirdArg<String>() && it.scrobbleState == MusicListenEntity.SCROBBLE_PENDING
                }.sortedBy { it.startedAt }.take(arg(3))
            }
        }
        coEvery { dao.markScrobbleSent(any(), any(), any()) } answers {
            replace(secondArg()) { it.copy(scrobbleState = MusicListenEntity.SCROBBLE_SENT, scrobbledAt = thirdArg()) }
        }
        coEvery { dao.markScrobblePending(any(), any()) } answers {
            replace(secondArg()) {
                if (it.scrobbleState == MusicListenEntity.SCROBBLE_NONE) it.copy(scrobbleState = MusicListenEntity.SCROBBLE_PENDING) else it
            }
        }
        return dao
    }

    private fun replace(id: Long, change: (MusicListenEntity) -> MusicListenEntity) = synchronized(rows) {
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) rows[index] = change(rows[index])
    }

    private fun api(): NavidromeApiClient {
        val certs = mockk<UserCertStore>()
        every { certs.trustManager() } returns null
        return NavidromeApiClient(Moshi.Builder().build(), certs)
    }

    private fun accounts(): MusicAccountReader {
        val reader = mockk<MusicAccountReader>()
        coEvery { reader.current() } returns MusicAccounts(owner, "romm", "$owner@romm", account)
        return reader
    }

    private fun preferences(scope: CoroutineScope): NavidromePreferencesRepository {
        val file = tempFolder.newFile("navidrome.preferences_pb")
        return NavidromePreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @Test
    fun `pending listens go out oldest first with their start time and end up sent`() = runTest {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody(ok)) }
            rows += listen(1, startedAt = 2_000, state = MusicListenEntity.SCROBBLE_PENDING)
            rows += listen(2, startedAt = 1_000, state = MusicListenEntity.SCROBBLE_PENDING)
            rows += listen(3, startedAt = 500, state = MusicListenEntity.SCROBBLE_NONE)
            val scrobbler = NavidromeScrobbler(
                api(), preferences(CoroutineScope(StandardTestDispatcher(testScheduler))), dao(), accounts()
            )

            val sent = scrobbler.flushPending(NavidromeCredentials(server.url("/").toString(), "alice", "pw"), owner, account)

            assertEquals(2, sent)
            val first = server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!
            val second = server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("/rest/scrobble", first.encodedPath)
            assertEquals(listOf("song-2", "song-1"), listOf(first.queryParameter("id"), second.queryParameter("id")))
            assertEquals(listOf("1000", "2000"), listOf(first.queryParameter("time"), second.queryParameter("time")))
            assertEquals("true", first.queryParameter("submission"))
            assertEquals(MusicListenEntity.SCROBBLE_SENT, state(1))
            assertEquals(MusicListenEntity.SCROBBLE_SENT, state(2))
            assertEquals(MusicListenEntity.SCROBBLE_NONE, state(3))
        }
    }

    @Test
    fun `a failed submission stays pending and holds the later ones back`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            rows += listen(1, startedAt = 1_000, state = MusicListenEntity.SCROBBLE_PENDING)
            rows += listen(2, startedAt = 2_000, state = MusicListenEntity.SCROBBLE_PENDING)
            val scrobbler = NavidromeScrobbler(
                api(), preferences(CoroutineScope(StandardTestDispatcher(testScheduler))), dao(), accounts()
            )

            val sent = scrobbler.flushPending(NavidromeCredentials(server.url("/").toString(), "alice", "pw"), owner, account)

            assertEquals(0, sent)
            assertEquals(1, server.requestCount)
            assertEquals(MusicListenEntity.SCROBBLE_PENDING, state(1))
            assertEquals(MusicListenEntity.SCROBBLE_PENDING, state(2))
        }
    }

    @Test
    fun `listens of another owner or another navidrome account are left pending`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(ok))
            rows += listen(1, startedAt = 1_000, state = MusicListenEntity.SCROBBLE_PENDING, ownerKey = 99L)
            rows += listen(2, startedAt = 1_500, state = MusicListenEntity.SCROBBLE_PENDING, accountKey = "bob@nd")
            rows += listen(3, startedAt = 2_000, state = MusicListenEntity.SCROBBLE_PENDING)
            val scrobbler = NavidromeScrobbler(
                api(), preferences(CoroutineScope(StandardTestDispatcher(testScheduler))), dao(), accounts()
            )

            val sent = scrobbler.flushPending(NavidromeCredentials(server.url("/").toString(), "alice", "pw"), owner, account)

            assertEquals(1, sent)
            assertEquals(MusicListenEntity.SCROBBLE_PENDING, state(1))
            assertEquals(MusicListenEntity.SCROBBLE_PENDING, state(2))
            assertEquals(MusicListenEntity.SCROBBLE_SENT, state(3))
        }
    }

    @Test
    fun `a counted listen moves from none to pending to sent once the server answers`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(ok))
            rows += listen(1, startedAt = 1_000, state = MusicListenEntity.SCROBBLE_NONE)
            val prefs = preferences(CoroutineScope(StandardTestDispatcher(testScheduler)))
            val scrobbler = NavidromeScrobbler(api(), prefs, dao(), accounts())
            prefs.setServerUrl(server.url("/").toString())
            prefs.setCredentials("alice", "pw", "0.64.2")

            scrobbler.submitCounted(owner, 1)

            assertEquals(MusicListenEntity.SCROBBLE_SENT, state(1))
            assertEquals("song-1", server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("id"))
        }
    }
}
