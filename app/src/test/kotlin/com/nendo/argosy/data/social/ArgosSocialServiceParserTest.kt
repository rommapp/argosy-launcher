package com.nendo.argosy.data.social

import android.app.Application
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgosSocialServiceParserTest {

    private fun newService(): ArgosSocialService {
        val application = mockk<Application>(relaxed = true)
        return ArgosSocialService(application)
    }

    private val refusal = """{"type":"error","error":"authentication_failed"}"""

    @Test
    fun oneAuthRefusalKeepsTheSession() = runBlocking {
        val service = newService()
        var revoked = 0
        service.onSessionRevoked = { revoked++ }

        service.handleMessageForTest(refusal)

        assertEquals(0, revoked)
    }

    @Test
    fun repeatedAuthRefusalsEndTheSession() = runBlocking {
        val service = newService()
        var revoked = 0
        service.onSessionRevoked = { revoked++ }

        repeat(ArgosSocialService.AUTH_REFUSALS_BEFORE_LOGOUT) { service.handleMessageForTest(refusal) }

        assertEquals(1, revoked)
    }

    @Test
    fun anAcceptedAuthResetsTheRefusalCount() = runBlocking {
        val service = newService()
        var revoked = 0
        service.onSessionRevoked = { revoked++ }

        repeat(ArgosSocialService.AUTH_REFUSALS_BEFORE_LOGOUT - 1) { service.handleMessageForTest(refusal) }
        service.handleMessageForTest("""{"type":"auth_success"}""")
        repeat(ArgosSocialService.AUTH_REFUSALS_BEFORE_LOGOUT - 1) { service.handleMessageForTest(refusal) }

        assertEquals(0, revoked)
    }

    @Test
    fun aFailedSessionLookupNeverEndsTheSession() = runBlocking {
        val service = newService()
        var revoked = 0
        service.onSessionRevoked = { revoked++ }

        repeat(10) { service.handleMessageForTest("""{"type":"error","error":"session_lookup_failed"}""") }

        assertEquals(0, revoked)
    }

    @Test
    fun parsesNetplayInviteWithIgdbId() = runBlocking {
        val service = newService()
        val envelope = """
            {
              "type": "netplay_invite",
              "payload": {
                "session_id": "sess-42",
                "host_user_id": "user-1",
                "host_username": "alice",
                "game_title": "Super Mario",
                "game_igdb_id": 1234,
                "core_id": "fceumm",
                "rom_hash_prefix": "abc123",
                "core_hash": "def456",
                "protocol_version": 1
              }
            }
        """.trimIndent()

        service.handleMessageForTest(envelope)
        val message = withTimeout(2000) { service.incomingMessages.first() }
        assertTrue(
            "expected NetplayInvite but got ${message::class.simpleName}",
            message is ArgosSocialService.IncomingMessage.NetplayInvite
        )
        val payload = (message as ArgosSocialService.IncomingMessage.NetplayInvite).payload
        assertEquals("sess-42", payload.sessionId)
        assertEquals("user-1", payload.hostUserId)
        assertEquals("alice", payload.hostUsername)
        assertEquals("Super Mario", payload.gameTitle)
        assertEquals(1234, payload.gameIgdbId)
        assertEquals("fceumm", payload.coreId)
        assertEquals("abc123", payload.romHashPrefix)
        assertEquals("def456", payload.coreHash)
        assertEquals(1, payload.protocolVersion)
    }

    @Test
    fun parsesNetplayInviteWithoutIgdbId() = runBlocking {
        val service = newService()
        val envelope = """
            {
              "type": "netplay_invite",
              "payload": {
                "session_id": "sess-1",
                "host_user_id": "user-x",
                "host_username": "bob",
                "game_title": "Unknown ROM",
                "core_id": "snes9x",
                "rom_hash_prefix": "aa",
                "core_hash": "bb",
                "protocol_version": 1
              }
            }
        """.trimIndent()

        service.handleMessageForTest(envelope)
        val message = withTimeout(2000) { service.incomingMessages.first() }
        val payload = (message as ArgosSocialService.IncomingMessage.NetplayInvite).payload
        assertNull(payload.gameIgdbId)
        assertEquals("bob", payload.hostUsername)
    }

    @Test
    fun readsAReviewAuthorsName() = runBlocking {
        val page = reviewsPageFrom(
            """
            "users": {
              "user-1": {
                "id": "user-1",
                "username": "alice",
                "display_name": "Alice",
                "avatar_color": "#6366f1"
              }
            }
            """.trimIndent()
        )

        assertEquals("Alice", page.users["user-1"]?.displayName)
    }

    @Test
    fun `falls back to the username when a review author has no display name`() = runBlocking {
        val page = reviewsPageFrom(
            """
            "users": {
              "user-1": {
                "id": "user-1",
                "username": "alice",
                "display_name": "",
                "avatar_color": "#6366f1"
              }
            }
            """.trimIndent()
        )

        assertEquals("alice", page.users["user-1"]?.displayName)
    }

    @Test
    fun keepsTheMapKeyAsTheAuthorIdWhenTheEntryOmitsOne() = runBlocking {
        val page = reviewsPageFrom(
            """
            "users": {
              "user-1": {
                "username": "alice",
                "display_name": "Alice",
                "avatar_color": "#6366f1"
              }
            }
            """.trimIndent()
        )

        assertEquals("user-1", page.users["user-1"]?.id)
    }

    private suspend fun reviewsPageFrom(usersBlock: String): GameReviewsPage {
        val service = newService()
        val envelope = """
            {
              "type": "game_reviews_data",
              "payload": {
                "igdb_id": 1234,
                "friends": [],
                "public": [
                  {
                    "user_id": "user-1",
                    "igdb_id": 1234,
                    "recommended": true,
                    "body": "good",
                    "visibility": "public",
                    "play_minutes": 60,
                    "created_at": "2026-01-01T00:00:00Z",
                    "updated_at": "2026-01-01T00:00:00Z",
                    "is_friend": false,
                    "helpful_count": 0,
                    "unhelpful_count": 0
                  }
                ],
                "has_more": false,
                $usersBlock
              }
            }
        """.trimIndent()

        service.handleMessageForTest(envelope)
        val message = withTimeout(2000) { service.incomingMessages.first() }
        return (message as ArgosSocialService.IncomingMessage.GameReviewsData).page
    }
}
