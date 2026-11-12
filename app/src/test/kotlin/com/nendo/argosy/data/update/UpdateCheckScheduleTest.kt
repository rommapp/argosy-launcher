package com.nendo.argosy.data.update

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class UpdateCheckScheduleTest {

    @Test
    fun `the periodic check runs every 6 hours`() {
        val spec = UpdateCheckWorker.buildRequest().workSpec
        assertEquals(TimeUnit.HOURS.toMillis(6), spec.intervalDuration)
        assertEquals(TimeUnit.HOURS.toMillis(1), spec.flexDuration)
    }

    @Test
    fun `the periodic check waits for a connected network`() {
        val spec = UpdateCheckWorker.buildRequest().workSpec
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
    }

    @Test
    fun `scheduling replaces the old 12 hour work instead of keeping it`() {
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, UpdateCheckWorker.SCHEDULE_POLICY)
    }
}
