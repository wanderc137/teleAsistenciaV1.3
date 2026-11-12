package com.alertamayor.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertamayor.app.di.AppContainer
import com.alertamayor.app.domain.model.AppMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OnboardingViewModel(container: AppContainer) : ViewModel() {

    private val settings = container.settingsRepository
    private val coordinator = container.callCoordinator

    private val _onboardingDone = MutableStateFlow(false)
    val onboardingDone: StateFlow<Boolean> = _onboardingDone

    init {
        viewModelScope.launch {
            _onboardingDone.value = settings.onboardingDone.first()
        }
    }

    fun selectMode(mode: AppMode) {
        viewModelScope.launch {
            settings.saveMode(mode)
            settings.setOnboardingDone(true)
            // Ciclo 10-fix: aplicar el modo TAMBIÉN en el CallCoordinator en memoria.
            // Antes solo se guardaba en DataStore; si el CallCoordinator arrancó antes
            // (con el default SUPERVISED), seguía auto-aceptando en modo Supervisor.
            coordinator.setMode(mode)
            _onboardingDone.value = true
        }
    }
}
