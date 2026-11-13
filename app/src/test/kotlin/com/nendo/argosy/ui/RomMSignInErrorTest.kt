package com.nendo.argosy.ui

import com.nendo.argosy.R
import com.nendo.argosy.data.remote.romm.RomMErrorKind
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.ui.screens.settings.delegates.accountsPairingErrorRes
import com.nendo.argosy.ui.screens.settings.delegates.serverSignInErrorRes
import com.nendo.argosy.ui.screens.setup.setupSignInError
import com.nendo.argosy.ui.screens.setup.toProbeError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomMSignInErrorTest {
    @Test
    fun `application failures map to resource backed errors on all sign in surfaces`() {
        for (kind in listOf(RomMErrorKind.SESSION_CHANGED, RomMErrorKind.IDENTITY_UNAVAILABLE)) {
            val setup = kind.setupSignInError("internal-token")
            assertEquals(R.string.firstrun_romm_error_sign_in_interrupted, setup.textRes)
            assertNull(setup.serverMessage)
            assertEquals(setup, RomMResult.Error("internal-token", kind = kind).toProbeError())
            assertEquals(R.string.settings_server_delegate_pairing_interrupted, serverSignInErrorRes(kind))
            assertEquals(R.string.settings_accounts_delegate_pairing_interrupted, accountsPairingErrorRes(kind))
        }
    }

    @Test
    fun `existing server messages and certificate mapping are preserved`() {
        assertEquals("server detail", (null as RomMErrorKind?).setupSignInError("server detail").serverMessage)
        assertNull(serverSignInErrorRes(null))
        assertNull(accountsPairingErrorRes(null))
        assertEquals(R.string.setup_library_address_error_unreachable, RomMResult.Error("network").toProbeError().textRes)
        assertEquals(R.string.firstrun_romm_error_untrusted_certificate,
            RomMErrorKind.UNTRUSTED_CERTIFICATE.setupSignInError("certificate").textRes)
        assertEquals(R.string.settings_romm_config_error_untrusted_certificate,
            serverSignInErrorRes(RomMErrorKind.UNTRUSTED_CERTIFICATE))
    }
}
