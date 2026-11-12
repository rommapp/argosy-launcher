package com.nendo.argosy.ui.screens.stream

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow

fun noStreamLaunch(): StreamLaunchController.Factory {
    val controller = mockk<StreamLaunchController>(relaxed = true) {
        every { offerFor(any(), any()) } returns null
        every { offers } returns emptyFlow()
    }
    return mockk { every { create(any()) } returns controller }
}
