package com.nendo.argosy.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsRedactorTest {

    private val settings: Map<String, Any?> = mapOf(
        "romm_url" to "https://romm.example.com/",
        "romm_token" to "rmm_9f8e7d6c5b4a",
        "romm_username" to "nightowl",
        "ra_username" to "speedrunner42",
        "ra_token" to "RAtok123456",
        "ra_web_api_key" to "abcdEFGH1234",
        "jellyfin_server_url" to "http://192.168.1.20:8096",
        "jellyfin_access_token" to "jf-access-0001",
        "jellyfin_user_name" to "livingroom",
        "social_session_token" to "sess-xyz-987",
        "file_log_level" to "INFO",
        "ui_scale" to 100,
        "save_sync_enabled" to true
    )

    @Test
    fun `credential and identity setting names are sensitive`() {
        listOf(
            "romm_token", "romm_url", "romm_username", "romm_device_id", "romm_snapshots_server",
            "ra_token", "ra_web_api_key", "ra_username", "ra_proxy_address", "social_session_token",
            "social_user_id", "social_username", "social_display_name", "jellyfin_access_token",
            "jellyfin_device_id", "jellyfin_server_url", "jellyfin_user_id", "jellyfin_user_name",
            "quaypass_credential", "quaypass_client_install_id", "quaypass_install_key_alias",
            "quaypass_pending_friend_requests"
        ).forEach { assertTrue("$it should be sensitive", DiagnosticsRedactor.isSensitiveName(it)) }
    }

    @Test
    fun `ordinary setting names are not sensitive`() {
        listOf("file_log_level", "ui_scale", "theme_mode", "background_blur", "secure_saves", "library_layout")
            .forEach { assertFalse("$it should not be sensitive", DiagnosticsRedactor.isSensitiveName(it)) }
    }

    @Test
    fun `a sensitive setting loses its whole value`() {
        val secrets = DiagnosticsRedactor.secretsIn(settings)
        settings.filterKeys { DiagnosticsRedactor.isSensitiveName(it) }.forEach { (name, value) ->
            assertEquals(DiagnosticsRedactor.REDACTED, DiagnosticsRedactor.redactSetting(name, value, secrets))
        }
    }

    @Test
    fun `ordinary settings keep their values`() {
        assertEquals("INFO", DiagnosticsRedactor.redactSetting("file_log_level", "INFO"))
        assertEquals("100", DiagnosticsRedactor.redactSetting("ui_scale", 100))
        assertEquals("true", DiagnosticsRedactor.redactSetting("save_sync_enabled", true))
    }

    @Test
    fun `a boolean under a sensitive name is kept`() {
        assertEquals("true", DiagnosticsRedactor.redactSetting("ra_proxy_enabled_token_gate", true))
    }

    @Test
    fun `a url inside an ordinary setting is redacted`() {
        val redacted = DiagnosticsRedactor.redactSetting("custom_feed", "fetch from https://cdn.example.org/a.json")
        assertFalse(redacted.contains("cdn.example.org"))
        assertTrue(redacted.contains(DiagnosticsRedactor.URL_MARK))
    }

    @Test
    fun `a log line loses urls, hosts, e-mail addresses, tokens and passwords`() {
        val line = "GET https://romm.example.com/api/roms?token=abc123 from 10.0.0.5:8080 " +
            "for player@example.com Authorization: Bearer eyJhbGciOiJIUzI1NiJ9 password=hunter2 " +
            "api_key: \"K3Y-VALUE\""
        val redacted = DiagnosticsRedactor.redactText(line)
        listOf("romm.example.com", "abc123", "10.0.0.5", "player@example.com", "eyJhbGciOiJIUzI1NiJ9", "hunter2", "K3Y-VALUE")
            .forEach { assertFalse("$it leaked into: $redacted", redacted.contains(it)) }
    }

    @Test
    fun `known user names, tokens and server hosts are removed from free text`() {
        val secrets = DiagnosticsRedactor.secretsIn(settings)
        val line = "Signed in to romm.example.com as nightowl, RA user speedrunner42 with RAtok123456; " +
            "jellyfin livingroom on 192.168.1.20"
        val redacted = DiagnosticsRedactor.redactText(line, secrets)
        listOf("romm.example.com", "nightowl", "speedrunner42", "RAtok123456", "livingroom", "192.168.1.20")
            .forEach { assertFalse("$it leaked into: $redacted", redacted.contains(it)) }
    }

    @Test
    fun `a known value is only replaced as a whole word`() {
        val redacted = DiagnosticsRedactor.redactText("owl nightowls nightowl", setOf("nightowl"))
        assertEquals("owl nightowls ${DiagnosticsRedactor.REDACTED}", redacted)
    }

    @Test
    fun `a user name inside a package or dotted id is left alone, and at a sentence end is removed`() {
        val redacted = DiagnosticsRedactor.redactText(
            "com.nightowl.argosy started for nightowl. nightowl.cfg stays",
            setOf("nightowl")
        )
        assertEquals("com.nightowl.argosy started for ${DiagnosticsRedactor.REDACTED}. nightowl.cfg stays", redacted)
    }

    @Test
    fun `secrets carry the host of each url setting`() {
        val secrets = DiagnosticsRedactor.secretsIn(settings)
        assertTrue("romm.example.com" in secrets)
        assertTrue("192.168.1.20" in secrets)
        assertFalse("INFO" in secrets)
    }

    @Test
    fun `text without anything sensitive is unchanged`() {
        val line = "2026-11-13 10:00:01 I/Launcher: core snes9x loaded in 120 ms"
        assertEquals(line, DiagnosticsRedactor.redactText(line))
    }
}
