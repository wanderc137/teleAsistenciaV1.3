package com.alertamayor.app.ui.call

import androidx.lifecycle.ViewModel
import com.alertamayor.app.di.AppContainer
import com.alertamayor.app.domain.model.CallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CallUiState(
    val callState: CallState = CallState.IDLE,
    val muted: Boolean = false,
    val speakerOn: Boolean = false,
    val callInProgress: Boolean = false,
)

class CallViewModel(container: AppContainer) : ViewModel() {

    private val coordinator = container.callCoordinator
    private val webRtc = container.webRtcManager

    val callState: StateFlow<CallState> = coordinator.callState
    val muted: StateFlow<Boolean> = coordinator.muted
    val speakerOn: StateFlow<Boolean> = coordinator.speakerOn

    val localVideoView get() = webRtc.localVideoView
    val remoteVideoView get() = webRtc.remoteVideoView

    init {
        // Si llegamos aquí por una llamada entrante auto-aceptada en modo supervisado
        // o aceptada manualmente, el coordinator ya gestiona el estado.
    }

    fun hangUp() {
        coordinator.hangUp()
    }

    fun toggleMute() {
        coordinator.toggleMute()
    }

    fun toggleSpeaker() {
        coordinator.toggleSpeaker()
    }

    fun switchCamera() {
        coordinator.switchCamera()
    }

    fun acceptIncomingCall() {
        coordinator.acceptIncomingCall()
    }

    fun rejectIncomingCall() {
        coordinator.rejectIncomingCall()
    }
}
