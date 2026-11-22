package com.nendo.argosy.data.remote.ra

import com.nendo.argosy.data.repository.RaMetadataSource.Companion.toEarnedAchievements
import com.nendo.argosy.domain.usecase.recap.UnlockWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RAWebModelsTest {

    private val moshi = RAWebClient.moshi()

    @Test
    fun `user summary reads rich presence, its date and the last game`() {
        val json = """
            {"User":"Player","RichPresenceMsg":"Exploring Hyrule","RichPresenceMsgDate":"2026-10-01 11:30:00",
             "LastGameID":55,"TotalPoints":1200,"LastGame":{"ID":55,"Title":"Zelda"}}
        """.trimIndent()

        val summary = moshi.adapter(RAWebUserSummary::class.java).fromJson(json)!!

        assertEquals("Exploring Hyrule", summary.richPresenceMsg)
        assertEquals("2026-10-01 11:30:00", summary.richPresenceMsgDate)
        assertEquals(55L, summary.lastGameId)
    }

    @Test
    fun `comments page reads each commenter, date and text`() {
        val json = """
            {"Count":2,"Total":7,"Results":[
              {"User":"PlayTester","ULID":"00003EMFWR7XB8SDPEHB3K56ZQ","Submitted":"2024-07-31T11:22:23.000000Z","CommentText":"Comment 1"},
              {"User":"Other","ULID":"00003EMFWR7XB8SDPEHB3K56ZR","Submitted":"2024-07-30T09:00:00.000000Z","CommentText":"Comment 2"}
            ]}
        """.trimIndent()

        val page = moshi.adapter(RAWebCommentsPage::class.java).fromJson(json)!!

        assertEquals(7, page.total)
        assertEquals(
            listOf(
                RAWebComment("PlayTester", "2024-07-31T11:22:23.000000Z", "Comment 1"),
                RAWebComment("Other", "2024-07-30T09:00:00.000000Z", "Comment 2")
            ),
            page.results
        )
    }

    @Test
    fun `game progress reads per-achievement earned dates and award metadata`() {
        val json = """
            {"ID":55,"Title":"Zelda","NumAchievements":2,
             "Achievements":{
               "9":{"ID":9,"Title":"First","Points":5,"BadgeName":"00042","DateEarned":"2026-10-01 10:30:00","DateEarnedHardcore":"2026-10-01 10:30:00"},
               "10":{"ID":10,"Title":"Second","Points":10,"BadgeName":"00043"}
             },
             "HighestAwardKind":"beaten-hardcore","HighestAwardDate":"2026-10-01T10:45:00+00:00"}
        """.trimIndent()

        val progress = moshi.adapter(RAWebGameProgress::class.java).fromJson(json)!!

        assertEquals(2, progress.achievements.byId.size)
        assertEquals("2026-10-01 10:30:00", progress.achievements.byId.getValue("9").dateEarnedHardcore)
        assertNull(progress.achievements.byId.getValue("10").dateEarned)
        assertEquals("beaten-hardcore", progress.highestAwardKind)
        assertEquals("2026-10-01T10:45:00+00:00", progress.highestAwardDate)
    }

    @Test
    fun `a game with no achievements sends an empty array and reads as an empty map`() {
        val json = """{"ID":56,"Title":"Homebrew","Achievements":[],"HighestAwardKind":null}"""

        val progress = moshi.adapter(RAWebGameProgress::class.java).fromJson(json)!!

        assertTrue(progress.achievements.byId.isEmpty())
    }

    @Test
    fun `completion progress reads one page of games`() {
        val json = """
            {"Count":1,"Total":1,"Results":[{"GameID":55,"Title":"Zelda","MaxPossible":20,"NumAwarded":20,
             "NumAwardedHardcore":20,"MostRecentAwardedDate":"2026-10-01T11:00:00+00:00",
             "HighestAwardKind":"mastered","HighestAwardDate":"2026-10-01T11:00:00+00:00"}]}
        """.trimIndent()

        val page = moshi.adapter(RAWebCompletionPage::class.java).fromJson(json)!!

        assertEquals(1, page.total)
        assertEquals(55L, page.results.single().gameId)
        assertEquals("mastered", page.results.single().highestAwardKind)
    }

    @Test
    fun `earned dates without an offset are read as UTC`() {
        val progress = RAWebGameProgress(
            achievements = RAWebAchievements(
                mapOf("9" to RAWebAchievementProgress(9L, "00042", dateEarned = "2026-10-01 23:30:00"))
            )
        )

        val unlock = UnlockWindow.unlocksWithin(
            progress.toEarnedAchievements(),
            Instant.parse("2026-10-01T23:00:00Z"),
            Instant.parse("2026-10-02T00:00:00Z")
        ).single()

        assertEquals(Instant.parse("2026-10-01T23:30:00Z"), unlock.unlockedAt)
    }

    @Test
    fun `credentials never print the key`() {
        assertTrue("secret-key" !in RAWebCredentials("Player", "secret-key").toString())
    }

    @Test
    fun `a cached progress entry serves reads fetched after it and only within the ttl`() {
        var now = Instant.parse("2026-10-01T12:00:00Z")
        val cache = RAWebProgressCache(Duration.ofMinutes(15)) { now }
        val progress = RAWebGameProgress(id = 55L)
        cache.put("Player", 55L, progress)

        assertEquals(progress, cache.get("player", 55L, Instant.parse("2026-10-01T11:59:00Z")))
        assertNull(cache.get("Player", 55L, Instant.parse("2026-10-01T12:00:01Z")))
        assertNull(cache.get("Player", 56L, Instant.parse("2026-10-01T11:59:00Z")))

        now = now.plus(Duration.ofMinutes(16))
        assertNull(cache.get("Player", 55L, Instant.parse("2026-10-01T11:59:00Z")))
    }
}
