package com.nendo.argosy.ui.screens.running

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RunningAppsViewModel @Inject constructor(
    controllerFactory: RunningAppsController.Factory
) : ViewModel() {
    val controller: RunningAppsController = controllerFactory.create(viewModelScope)
}
