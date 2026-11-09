package com.nendo.argosy.ui.components.recap

import com.nendo.argosy.domain.model.MonthlyDigest
import com.nendo.argosy.domain.usecase.recap.GetMonthlyRecapUseCase
import com.nendo.argosy.domain.usecase.recap.RecapGate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyRecapControllerTest {

    private val month = YearMonth.of(2026, 9)
    private val dispatcher = UnconfinedTestDispatcher()
    private val controllerScope = CoroutineScope(SupervisorJob() + dispatcher)

    private val gate: RecapGate = mockk {
        coEvery { monthlyDigestDue(any(), any()) } returns month
        coEvery { markDigestShown(any()) } returns Unit
    }
    private val useCase: GetMonthlyRecapUseCase = mockk()
    private val controller = MonthlyRecapController(gate, useCase, emptySet(), controllerScope, dispatcher)

    @After
    fun tearDown() {
        controllerScope.cancel()
    }

    private fun digest(sessionCount: Int) = MonthlyDigest(
        month = month,
        totalActiveMs = 60_000L * sessionCount,
        sessionCount = sessionCount,
        longestSessionMs = 60_000L * sessionCount,
        days = emptyList(),
        topGames = emptyList(),
        unlocks = emptyList(),
        awards = emptyList()
    )

    @Test
    fun `a ready load shows the card and marks the month shown`() {
        coEvery { useCase(month, any()) } returns digest(sessionCount = 3)

        controller.onHomeShown()

        assertEquals(MonthlyRecapCardState.Ready(month, digest(sessionCount = 3)), controller.state.value)
        coVerify(exactly = 1) { gate.markDigestShown(month) }
    }

    @Test
    fun `the card shows loading while the recap waits and the month is not marked until it is ready`() {
        val pending = CompletableDeferred<MonthlyDigest>()
        coEvery { useCase(month, any()) } coAnswers { pending.await() }

        controller.onHomeShown()

        assertEquals(MonthlyRecapCardState.Loading(month), controller.state.value)
        coVerify(exactly = 0) { gate.markDigestShown(any()) }

        pending.complete(digest(sessionCount = 1))

        assertEquals(MonthlyRecapCardState.Ready(month, digest(sessionCount = 1)), controller.state.value)
        coVerify(exactly = 1) { gate.markDigestShown(month) }
    }

    @Test
    fun `a failed load leaves the month eligible for the next home resume`() {
        coEvery { useCase(month, any()) } throws IllegalStateException("pull failed")

        controller.onHomeShown()

        assertEquals(MonthlyRecapCardState.Failed(month), controller.state.value)
        coVerify(exactly = 0) { gate.markDigestShown(any()) }

        controller.close()
        coEvery { useCase(month, any()) } returns digest(sessionCount = 2)
        controller.onHomeShown()

        assertEquals(MonthlyRecapCardState.Ready(month, digest(sessionCount = 2)), controller.state.value)
        coVerify(exactly = 1) { gate.markDigestShown(month) }
    }

    @Test
    fun `an empty load leaves the month eligible`() {
        coEvery { useCase(month, any()) } returns digest(sessionCount = 0)

        controller.onHomeShown()

        assertEquals(MonthlyRecapCardState.Empty(month), controller.state.value)
        coVerify(exactly = 0) { gate.markDigestShown(any()) }
    }

    @Test
    fun `closing the card mid-load leaves the month eligible`() {
        coEvery { useCase(month, any()) } coAnswers { CompletableDeferred<MonthlyDigest>().await() }

        controller.onHomeShown()
        controller.close()

        coVerify(exactly = 0) { gate.markDigestShown(any()) }
    }

    @Test
    fun `reopening last month by hand never marks it shown`() {
        coEvery { useCase(any(), any()) } returns digest(sessionCount = 3)

        controller.openLastMonth()

        coVerify(exactly = 0) { gate.markDigestShown(any()) }
        coVerify(exactly = 0) { gate.monthlyDigestDue(any(), any()) }
    }
}
