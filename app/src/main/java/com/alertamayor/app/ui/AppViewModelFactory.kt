package com.alertamayor.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.alertamayor.app.di.AppContainer
import com.alertamayor.app.ui.call.CallViewModel
import com.alertamayor.app.ui.onboarding.OnboardingViewModel
import com.alertamayor.app.ui.settings.SettingsViewModel
import com.alertamayor.app.ui.supervised.SupervisedHomeViewModel
import com.alertamayor.app.ui.supervisor.SupervisorHomeViewModel

/**
 * Factory de ViewModel que proporciona el [AppContainer] (DI manual) a los ViewModels.
 */
class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            modelClass.isAssignableFrom(OnboardingViewModel::class.java) ->
                OnboardingViewModel(container) as T
            modelClass.isAssignableFrom(SupervisedHomeViewModel::class.java) ->
                SupervisedHomeViewModel(container) as T
            modelClass.isAssignableFrom(SupervisorHomeViewModel::class.java) ->
                SupervisorHomeViewModel(container) as T
            modelClass.isAssignableFrom(CallViewModel::class.java) ->
                CallViewModel(container) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(container) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
