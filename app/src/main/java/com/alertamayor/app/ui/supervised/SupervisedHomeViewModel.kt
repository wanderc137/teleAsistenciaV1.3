package com.alertamayor.app.ui.supervised

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertamayor.app.R
import com.alertamayor.app.data.network.NetworkStatus
import com.alertamayor.app.di.AppContainer
import com.alertamayor.app.domain.QrLinkCodec
import com.alertamayor.app.domain.model.CallState
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SupervisedUiState(
    val deviceId: String = "",
    val deviceName: String = "",
    val linkedPeerId: String = "",
    val linkedPeerName: String = "",
    val connected: Boolean = false,
)

class SupervisedHomeViewModel(container: AppContainer) : ViewModel() {

    private val settings = container.settingsRepository
    private val coordinator = container.callCoordinator

    private val _state = MutableStateFlow(SupervisedUiState())
    val state: StateFlow<SupervisedUiState> = _state

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap

    val isLinked = MutableStateFlow(false)

    /** Estado de conectividad a internet (WIFI / CELLULAR / NONE). — M-002 (ciclo 6) */
    val networkStatus: StateFlow<NetworkStatus> = container.connectivityObserver.status

    val callState: StateFlow<CallState> = coordinator.callState

    /** ID del par en llamada activa (para navegar a la llamada en auto-respuesta). */
    val activePeerId: StateFlow<String> = coordinator.activePeerId

    /** ID del par de una llamada entrante. */
    val incomingPeerId: StateFlow<String> = coordinator.incomingPeerId

    init {
        viewModelScope.launch {
            combine(
                settings.deviceId,
                settings.deviceName,
                settings.linkedPeerId,
                settings.linkedPeerName,
                coordinator.connectionStatus,
            ) { deviceId, name, peerId, peerName, connected ->
                SupervisedUiState(deviceId, name, peerId, peerName, connected)
            }.collect { _state.value = it }
        }
        viewModelScope.launch {
            _state.collect { isLinked.value = it.linkedPeerId.isNotEmpty() }
        }
    }

    fun generateQr(context: Context) {
        viewModelScope.launch {
            val deviceId = _state.value.deviceId.ifEmpty {
                val id = settings.deviceId.first()
                id
            }
            if (deviceId.isEmpty()) return@launch
            val content = QrLinkCodec.encode(deviceId)
            val bitmap = withContext(Dispatchers.Default) { createQrBitmap(content) }
            _qrBitmap.value = bitmap
        }
    }

    /** Inicia la llamada al familiar vinculado. */
    fun callLinkedPeer() {
        val peerId = _state.value.linkedPeerId
        if (peerId.isNotEmpty()) {
            coordinator.startOutgoingCall(peerId, _state.value.linkedPeerName)
        }
    }

    private fun createQrBitmap(content: String, size: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }
}
