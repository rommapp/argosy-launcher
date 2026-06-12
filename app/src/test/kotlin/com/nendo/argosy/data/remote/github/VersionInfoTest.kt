package com.nendo.argosy.data.remote.github

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionInfoTest {

    private fun v(s: String): VersionInfo {
        val parsed = VersionInfo.parse(s)
        assertNotNull("failed to parse $s", parsed)
        return parsed!!
    }

    @Test
    fun `a build flavor suffix is the same version as the clean release`() {
        assertTrue(v("2125.1.2-vanilla").compareTo(v("2125.1.2")) == 0)
        assertTrue(v("2125.1.2").compareTo(v("2125.1.2-vanilla")) == 0)
    }

    @Test
    fun `a real prerelease ranks below the clean release`() {
        assertTrue(v("1.10.4-beta.1") < v("1.10.4"))
        assertTrue(v("2.0.0-rc1") < v("2.0.0"))
        assertTrue(v("1.0.0-alpha") < v("1.0.0"))
    }

    @Test
    fun `numeric ordering still wins`() {
        assertTrue(v("1.0.0") < v("1.0.1"))
        assertTrue(v("2125.1.2") < v("2125.1.3"))
        assertTrue(v("1.9.0") < v("1.10.0"))
    }
}
