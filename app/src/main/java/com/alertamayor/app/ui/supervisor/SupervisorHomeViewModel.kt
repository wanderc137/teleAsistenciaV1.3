package com.alertamayor.app.ui.supervisor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertamayor.app.di.AppContainer
import com.alertamayor.app.domain.model.DeviceInfo
import com.alertamayor.app.domain.model.CallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SupervisorUiState(
    val linkedPeerId: String = "",
    val linkedPeerName: String = "",
    val peerOnline: Boolean = false,
    val connected: Boolean = false,
)

class SupervisorHomeViewModel(container: AppContainer) : ViewModel() {

    private val container = container
    private val settings = container.settingsRepository
    private val coordinator = container.callCoordinator

    private val _state = MutableStateFlow(SupervisorUiState())
    val state: StateFlow<SupervisorUiState> = _state

    val callState: StateFlow<CallState> = coordinator.callState
    val peerOnline: StateFlow<Boolean> = coordinator.peerOnline

    /** ID del par en llamada activa (para navegar a la llamada). */
    val activePeerId: StateFlow<String> = coordinator.activePeerId

    /** ID del par de una llamada entrante. */
    val incomingPeerId: StateFlow<String> = coordinator.incomingPeerId

    init {
        viewModelScope.launch {
            combine(
                settings.linkedPeerId,
                settings.linkedPeerName,
                coordinator.peerOnline,
                coordinator.connectionStatus,
            ) { id, name, online, connected ->
                SupervisorUiState(id, name, online, connected)
            }.collect { _state.value = it }
        }
    }

    /** Vincula un dispositivo escaneado. */
    fun linkPeer(deviceId: String, name: String) {
        viewModelScope.launch {
            settings.saveLinkedPeer(deviceId, name)
            coordinator.updateLinkedPeer(deviceId)
            // Notifica al teléfono supervisado (padre) para que guarde el vínculo
            // y muestre el botón rojo. Enviamos el nombre del supervisor (hijo).
            val myName = settings.deviceName.first().ifEmpty { container.defaultPeerName() }
            coordinator.sendLinkRequest(deviceId, myName)
        }
    }

    fun unlinkPeer() {
        viewModelScope.launch {
            settings.saveLinkedPeer(null, null)
        }
    }

    /** Inicia una llamada hacia el dispositivo vinculado. */
    fun callLinkedPeer() {
        val peerId = _state.value.linkedPeerId
        if (peerId.isNotEmpty()) {
            coordinator.startOutgoingCall(peerId, _state.value.linkedPeerName)
        }
    }
}
