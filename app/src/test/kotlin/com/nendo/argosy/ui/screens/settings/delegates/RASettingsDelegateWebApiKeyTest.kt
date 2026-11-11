package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.repository.RAWebApiKeyCheck
import com.nendo.argosy.data.repository.RAWebApiKeyRepository
import com.nendo.argosy.ui.components.TextEntryRow
import com.nendo.argosy.ui.screens.settings.RAWebApiKeyAction
import com.nendo.argosy.ui.screens.settings.RAWebApiKeyStatus
import com.nendo.argosy.ui.util.ClipboardTextReader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RASettingsDelegateWebApiKeyTest {

    private val keyRepository: RAWebApiKeyRepository = mockk(relaxed = true)
    private val clipboard: ClipboardTextReader = mockk()
    private val delegate = RASettingsDelegate(
        raRepository = mockk(),
        prefsRepository = mockk(),
        libretroSettingsRepo = mockk(),
        webApiKeyRepository = keyRepository,
        keyHealth = com.nendo.argosy.data.repository.RAWebKeyHealth(),
        clipboard = clipboard,
        context = mockk()
    )

    private val editor get() = delegate.state.value.webApiKeyEditor

    @Test
    fun `opening the editor focuses the field with save preselected`() {
        delegate.openWebApiKeyEditor()

        assertEquals(TextEntryRow.FIELD, editor!!.row)
        assertEquals(listOf(RAWebApiKeyAction.PASTE, RAWebApiKeyAction.CANCEL, RAWebApiKeyAction.SAVE), editor!!.actions)
        assertEquals(RAWebApiKeyAction.SAVE, editor!!.actions[editor!!.buttonIndex])
    }

    @Test
    fun `confirm on the field reopens the keyboard, confirm on a button runs it`() = runTest {
        coEvery { keyRepository.validateAndSave("abcdefgh1234") } returns RAWebApiKeyCheck.SAVED
        var saved = 0
        delegate.openWebApiKeyEditor()
        delegate.setWebApiKeyDraft("abcdefgh1234")

        delegate.confirmWebApiKeyEditor(this, onSaved = { saved++ }, onCleared = {})
        assertEquals(TextEntryRow.FIELD, editor!!.row)
        assertEquals(1, editor!!.keyboardRequest)

        delegate.moveWebApiKeyRow(TextEntryRow.BUTTONS)
        delegate.confirmWebApiKeyEditor(this, onSaved = { saved++ }, onCleared = {})
        advanceUntilIdle()

        assertNull(editor)
        assertEquals(1, saved)
        assertTrue(delegate.state.value.hasWebApiKey)
        assertEquals("1234", delegate.state.value.webApiKeySuffix)
    }

    @Test
    fun `save shows validating while the check runs and blocks dismissal`() = runTest {
        val gate = CompletableDeferred<RAWebApiKeyCheck>()
        coEvery { keyRepository.validateAndSave(any()) } coAnswers { gate.await() }
        delegate.openWebApiKeyEditor()
        delegate.setWebApiKeyDraft("abcdefgh1234")

        delegate.runWebApiKeyAction(this, RAWebApiKeyAction.SAVE, onSaved = {}, onCleared = {})
        advanceUntilIdle()
        assertEquals(RAWebApiKeyStatus.VALIDATING, editor!!.status)

        delegate.closeWebApiKeyEditor()
        assertEquals(RAWebApiKeyStatus.VALIDATING, editor!!.status)

        gate.complete(RAWebApiKeyCheck.REJECTED)
        advanceUntilIdle()
        assertEquals(RAWebApiKeyStatus.REJECTED, editor!!.status)
        assertFalse(delegate.state.value.hasWebApiKey)
    }

    @Test
    fun `an unreachable check keeps the editor open with its own status`() = runTest {
        coEvery { keyRepository.validateAndSave(any()) } returns RAWebApiKeyCheck.UNREACHABLE
        delegate.openWebApiKeyEditor()
        delegate.setWebApiKeyDraft("abcdefgh1234")

        delegate.runWebApiKeyAction(this, RAWebApiKeyAction.SAVE, onSaved = {}, onCleared = {})
        advanceUntilIdle()

        assertEquals(RAWebApiKeyStatus.UNREACHABLE, editor!!.status)
    }

    @Test
    fun `editing the draft clears a failure status`() = runTest {
        coEvery { keyRepository.validateAndSave(any()) } returns RAWebApiKeyCheck.REJECTED
        delegate.openWebApiKeyEditor()
        delegate.setWebApiKeyDraft("bad")
        delegate.runWebApiKeyAction(this, RAWebApiKeyAction.SAVE, onSaved = {}, onCleared = {})
        advanceUntilIdle()

        delegate.setWebApiKeyDraft("better")

        assertEquals(RAWebApiKeyStatus.IDLE, editor!!.status)
    }

    @Test
    fun `save with an empty draft never calls the server`() = runTest {
        delegate.openWebApiKeyEditor()

        delegate.runWebApiKeyAction(this, RAWebApiKeyAction.SAVE, onSaved = {}, onCleared = {})
        advanceUntilIdle()

        coVerify(exactly = 0) { keyRepository.validateAndSave(any()) }
    }

    @Test
    fun `paste fills the draft from the clipboard`() = runTest {
        every { clipboard.read() } returns "  abcdefgh1234\n"
        delegate.openWebApiKeyEditor()

        delegate.runWebApiKeyAction(this, RAWebApiKeyAction.PASTE, onSaved = {}, onCleared = {})

        assertEquals("abcdefgh1234", editor!!.draft)
    }

    @Test
    fun `paste with an empty clipboard says so`() = runTest {
        every { clipboard.read() } returns null
        delegate.openWebApiKeyEditor()

        delegate.runWebApiKeyAction(this, RAWebApiKeyAction.PASTE, onSaved = {}, onCleared = {})

        assertEquals(RAWebApiKeyStatus.CLIPBOARD_EMPTY, editor!!.status)
    }

    @Test
    fun `clear is offered only with a stored key and removes it`() = runTest {
        coEvery { keyRepository.validateAndSave(any()) } returns RAWebApiKeyCheck.SAVED
        delegate.openWebApiKeyEditor()
        delegate.setWebApiKeyDraft("abcdefgh1234")
        delegate.runWebApiKeyAction(this, RAWebApiKeyAction.SAVE, onSaved = {}, onCleared = {})
        advanceUntilIdle()
        var cleared = 0

        delegate.openWebApiKeyEditor()
        assertTrue(RAWebApiKeyAction.CLEAR in editor!!.actions)
        delegate.runWebApiKeyAction(this, RAWebApiKeyAction.CLEAR, onSaved = {}, onCleared = { cleared++ })
        advanceUntilIdle()

        coVerify(exactly = 1) { keyRepository.clear() }
        assertEquals(1, cleared)
        assertNull(editor)
        assertFalse(delegate.state.value.hasWebApiKey)
    }

    @Test
    fun `button focus wraps both ways`() {
        delegate.openWebApiKeyEditor()
        delegate.moveWebApiKeyRow(TextEntryRow.BUTTONS)

        delegate.moveWebApiKeyButton(1)
        assertEquals(RAWebApiKeyAction.PASTE, editor!!.focusedAction)

        delegate.moveWebApiKeyButton(-1)
        assertEquals(RAWebApiKeyAction.SAVE, editor!!.focusedAction)
    }
}
