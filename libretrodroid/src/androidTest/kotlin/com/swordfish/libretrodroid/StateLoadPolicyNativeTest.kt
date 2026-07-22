/*
 *     Copyright (C) 2026  Argosy
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package com.swordfish.libretrodroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StateLoadPolicyNativeTest {

    @Test
    fun runNativeStateLoadPolicyTests() {
        val passed = LibretroDroid.runStateLoadPolicyTests()
        assertEquals("All native state-load policy tests should pass", 7, passed)
    }
}
