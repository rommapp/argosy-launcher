package com.nendo.argosy.data.music.navidrome

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nendo.argosy.data.preferences.NavidromePreferencesRepository
import com.nendo.argosy.data.remote.navidrome.NavidromeApiClient
import com.nendo.argosy.data.remote.navidrome.NavidromeEnvelope
import com.nendo.argosy.data.remote.ssl.UserCertStore
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.domain.model.MusicSourceKind
import com.squareup.moshi.Moshi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NavidromeMusicSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val albumListBody = """
        {"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome",
        "serverVersion":"0.64.2","openSubsonic":true,
        "albumList2":{"album":[
          {"id":"al-1","name":"Kind of Blue","artist":"Miles Davis","artistId":"ar-9","year":1959,
           "genre":"Jazz","songCount":5,"duration":2760,"coverArt":"al-1","playCount":12},
          {"id":"al-2","name":"Untitled","songCount":0,"duration":0}
        ]}}}
    """.trimIndent()

    private fun repository(scope: CoroutineScope): NavidromePreferencesRepository {
        val file = tempFolder.newFile("settings.preferences_pb")
        return NavidromePreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    private fun api(): NavidromeApiClient {
        val certs = mockk<UserCertStore>()
        every { certs.trustManager() } returns null
        return NavidromeApiClient(Moshi.Builder().build(), certs)
    }

    @Test
    fun `album list json maps to music albums with credential-free cache keys`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(albumListBody))
            val prefs = repository(CoroutineScope(StandardTestDispatcher(testScheduler)))
            prefs.setServerUrl(server.url("/").toString())
            prefs.setCredentials("alice", "sesame", "0.64.2")
            val source = NavidromeMusicSource(prefs, api())

            val albums = source.albums().getOrThrow()

            assertEquals(2, albums.size)
            val album = albums[0]
            assertEquals("navidrome:album:al-1", album.key)
            assertEquals(MusicSourceKind.NAVIDROME, album.source)
            assertEquals("al-1", album.id)
            assertEquals("Kind of Blue", album.title)
            assertEquals("Miles Davis", album.artist)
            assertEquals(listOf("Miles Davis"), album.credits)
            assertEquals("ar-9", album.artistId)
            assertEquals(1959, album.year)
            assertEquals("Jazz", album.genre)
            assertEquals(5, album.trackCount)
            assertEquals(2760, album.durationSec)
            assertEquals(12, album.playCount)
            assertFalse(album.downloaded)

            val art = album.art as MusicArt.Remote
            assertEquals("navidrome:cover:al-1", art.cacheKey)
            val artUrl = art.url.toHttpUrl()
            assertEquals("/rest/getCoverArt", artUrl.encodedPath)
            assertEquals("al-1", artUrl.queryParameter("id"))
            assertEquals("600", artUrl.queryParameter("size"))
            assertEquals("alice", artUrl.queryParameter("u"))
            assertFalse(art.cacheKey.contains("alice"))

            val bare = albums[1]
            assertNull(bare.art)
            assertNull(bare.artist)
            assertNull(bare.year)
            assertTrue(bare.credits.isEmpty())

            val request = server.takeRequest().requestUrl!!
            assertEquals("/rest/getAlbumList2", request.encodedPath)
            assertEquals("alphabeticalByName", request.queryParameter("type"))
            assertEquals("500", request.queryParameter("size"))
            assertEquals("0", request.queryParameter("offset"))
        }
    }

    @Test
    fun `song isrc arrives as an array and maps to the first non-blank code with blank mbid dropped`() {
        val body = """
            {"subsonic-response":{"status":"ok","album":{"id":"al-1","song":[
              {"id":"s-1","title":"Irene","artist":"YASUHIRO ABE","musicBrainzId":"",
               "isrc":["","JPTO08831620"]},
              {"id":"s-2","title":"Bare"}
            ]}}}
        """.trimIndent()
        val adapter = Moshi.Builder().build().adapter(NavidromeEnvelope::class.java)

        val songs = NavidromeApiClient.parseResponse(body, adapter::fromJson).album!!.song!!
        val tagged = songs[0].toMusicTrack(null)
        val bare = songs[1].toMusicTrack(null)

        assertEquals("navidrome:track:s-1", tagged.key)
        assertEquals("JPTO08831620", tagged.isrc)
        assertNull(tagged.musicBrainzId)
        assertNull(bare.isrc)
    }

    @Test
    fun `a source with no account fails without calling the server`() = runTest {
        val prefs = repository(CoroutineScope(StandardTestDispatcher(testScheduler)))
        val source = NavidromeMusicSource(prefs, api())

        assertFalse(source.isConfigured())
        assertTrue(source.albums().isFailure)
    }
}
