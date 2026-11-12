package com.alertamayor.app.webrtc

import android.content.Context
import android.content.Intent
import com.alertamayor.app.R
import com.alertamayor.app.data.local.SettingsRepository
import com.alertamayor.app.data.signaling.SignalingClient
import com.alertamayor.app.data.signaling.SignalingEvent
import com.alertamayor.app.domain.model.AppMode
import com.alertamayor.app.domain.model.CallState
import com.alertamayor.app.service.CallService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordina la señalización y la sesión WebRTC de una llamada.
 *
 * Vive en el [AppContainer] (singleton) para sobrevivir a la rotación, al cierre de
 * pantalla y a la llamada en segundo plano (Foreground Service).
 */
class CallCoordinator(
    private val context: Context,
    private val settings: SettingsRepository,
    private val webRtc: WebRtcManager,
    private val audio: AudioSpeakerController,
    private val onForegroundStart: () -> Unit,
    private val onForegroundStop: () -> Unit,
    private val onGuardStart: () -> Unit,
    private val onGuardStop: () -> Unit,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var signaling: SignalingClient? = null
    private var signalingJob: Job? = null

    private var deviceId: String = ""
    private var mode: AppMode = AppMode.SUPERVISED
    private var peerId: String = ""
    private var peerName: String = ""
    private var linkedPeerId: String = ""

    // --- Caché TURN (rellenado en connect() y en setTurnConfig()) ---
    private var turnUrl: String = ""
    private var turnUsername: String = ""
    private var turnPassword: String = ""

    // --- Estado expuesto a la UI ---
    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> = _connectionStatus

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState

    private val _activePeerId = MutableStateFlow("")
    val activePeerId: StateFlow<String> = _activePeerId

    private val _incomingPeerId = MutableStateFlow("")
    val incomingPeerId: StateFlow<String> = _incomingPeerId

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted

    private val _speakerOn = MutableStateFlow(false)
    val speakerOn: StateFlow<Boolean> = _speakerOn

    private val _peerOnline = MutableStateFlow(false)
    val peerOnline: StateFlow<Boolean> = _peerOnline

    private val _callInProgress = MutableStateFlow(false)
    val callInProgress: StateFlow<Boolean> = _callInProgress

    /** Guard idempotente para que cleanupCall() solo se ejecute una vez (F-002 ciclo 5). */
    private val cleaningUp = AtomicBoolean(false)

    /** Se conecta al servidor de señalización con el deviceId/URL guardados. */
    fun connect() {
        scope.launch {
            deviceId = settings.deviceId.first()
            if (deviceId.isEmpty()) {
                deviceId = UUID.randomUUID().toString()
                settings.saveDeviceId(deviceId)
            }
            mode = settings.mode.first() ?: AppMode.SUPERVISED
            linkedPeerId = settings.linkedPeerId.first()
            turnUrl = settings.turnUrl.first()
            turnUsername = settings.turnUsername.first()
            turnPassword = settings.turnPassword.first()
            // R-004 (ciclo 4): mantener el servicio de guardia en AMBOS modos
            // (SUPERVISOR y SUPERVISED) para que el teléfono supervisado, que es el
            // que debe estar SIEMPRE alerta, conserve el wakeLock y la reconexión
            // ensureConnected() aunque el sistema intente suspender la app.
            // El GuardService ya es genérico (wakeLock + ensureConnected + notificación)
            // y no interfiere con la llamada, que usa onForegroundStart/onForegroundStop.
            onGuardStart()
            val url = settings.serverUrl.first()
            startSignaling(url, deviceId)
        }
    }

    /**
     * Garantiza que la señalización esté conectada. Lo llama el GuardService
     * para reconectar si el sistema suspendió la app y se perdió la conexión.
     */
    fun ensureConnected() {
        scope.launch {
            if (deviceId.isEmpty()) {
                deviceId = settings.deviceId.first()
                if (deviceId.isEmpty()) {
                    deviceId = UUID.randomUUID().toString()
                    settings.saveDeviceId(deviceId)
                }
            }
            if (signaling == null || !_connectionStatus.value) {
                val url = settings.serverUrl.first()
                startSignaling(url, deviceId)
            }
        }
    }

    /** Aplica un cambio de modo en caliente (desde Configuración). */
    fun setMode(newMode: AppMode) {
        mode = newMode
        // R-004 (ciclo 4): el servicio de guardia se mantiene activo en AMBOS modos
        // para que el teléfono supervisado siga siempre alerta.
        onGuardStart()
        scope.launch { linkedPeerId = settings.linkedPeerId.first() }
    }

    /** Aplica un cambio de URL del servidor en caliente (reconecta la señalización). */
    fun setServerUrl(url: String) {
        scope.launch {
            val id = deviceId.ifEmpty { settings.deviceId.first() }
            startSignaling(url, id)
        }
    }

    /** Actualiza el par vinculado en memoria (usado al vincular por QR). */
    fun updateLinkedPeer(id: String) {
        linkedPeerId = id
    }

    /** Aplica la configuración TURN en memoria (llamado desde Configuración). */
    fun setTurnConfig(url: String, username: String, password: String) {
        turnUrl = url.trim()
        turnUsername = username.trim()
        turnPassword = password
    }

    /**
     * Envía una solicitud de vínculo al dispositivo supervisado (padre).
     * Se usa en modo SUPERVISOR justo después de escanear el QR, para que el
     * teléfono del padre guarde el ID del hijo y muestre el botón rojo.
     */
    fun sendLinkRequest(targetId: String, name: String) {
        signaling?.sendLink(targetId, name)
    }

    /** Responde con confirmación de vínculo al supervisor (modo supervisado). */
    fun sendLinkAck(targetId: String) {
        signaling?.sendLinkAck(targetId)
    }

    /**
     * Registra el token FCM actual en el servidor de señalización. Lo llama
     * [com.alertamayor.app.service.FcmService] cuando Firebase genera un token
     * nuevo (onNewToken), para que el servidor pueda despertar esta app con un
     * push de alta prioridad aunque el WebSocket esté caído.
     */
    fun registerFcmToken(token: String) {
        signaling?.sendFcmToken(token)
    }

    /**
     * Guarda el vínculo recibido (modo supervisado): persiste el ID/nombre del
     * supervisor para que la pantalla muestre el botón rojo.
     */
    fun persistIncomingLink(supervisorId: String, supervisorName: String) {
        linkedPeerId = supervisorId
        peerName = supervisorName
        scope.launch {
            settings.saveLinkedPeer(supervisorId, supervisorName)
        }
    }

    private suspend fun startSignaling(url: String, id: String) {
        signaling?.disconnect()
        val client = SignalingClient(
            url,
            id,
            defaultPeerName = context.getString(R.string.default_peer_name),
        )
        signaling = client
        signalingJob = scope.launch {
            client.events.collect { handleEvent(it) }
        }
        client.connect()
        // Estado de conexión + registro del token FCM cuando el WebSocket está abierto.
        // El token puede llegar después de la primera conexión (onNewToken), por eso se
        // reenvía en cada (re)conexión: si no está vacío, el servidor guarda/actualiza.
        scope.launch {
            client.connected.collect { isConnected ->
                _connectionStatus.value = isConnected
                if (isConnected) {
                    val token = settings.fcmToken.first()
                    if (token.isNotBlank()) client.sendFcmToken(token)
                }
            }
        }
    }

    private fun handleEvent(event: SignalingEvent) {
        when (event) {
            is SignalingEvent.IncomingCall -> {
                _incomingPeerId.value = event.from
                if (mode == AppMode.SUPERVISED) {
                    // Auto-respuesta en modo Supervisado
                    acceptIncomingCall()
                } else {
                    // En modo Supervisor se notifica y se marca RINGING para que la UI
                    // navegue a la pantalla/diálogo de llamada entrante con Aceptar/Rechazar.
                    _callState.value = CallState.RINGING
                    onIncomingCall?.invoke(event.from)
                }
            }

            is SignalingEvent.CallAccepted -> {
                // El destino aceptó: iniciar WebRTC y generar offer
                startWebRtcAsInitiator()
            }

            is SignalingEvent.CallRejected -> {
                _callState.value = CallState.ENDED
                cleanupCall()
            }

            is SignalingEvent.Offer -> {
                // Recibimos un offer: crear la PeerConnection y responder
                startWebRtcAsReceiver()
                webRtc.setRemoteOffer(event.sdp)
                webRtc.createAnswer { sdp ->
                    signaling?.sendAnswer(event.from, sdp)
                }
            }

            is SignalingEvent.Answer -> {
                webRtc.setRemoteAnswer(event.sdp)
            }

            is SignalingEvent.IceCandidate -> {
                webRtc.addIceCandidate(event.candidate, event.sdpMid, event.sdpMLineIndex)
            }

            is SignalingEvent.EndCall -> {
                _callState.value = CallState.ENDED
                cleanupCall()
            }

            is SignalingEvent.PeerUnavailable -> {
                _callState.value = CallState.ENDED
                _peerOnline.value = false
                cleanupCall()
            }

            is SignalingEvent.PeerOnline -> {
                if (event.deviceId == peerId || event.deviceId == linkedPeerId) _peerOnline.value = true
            }

            is SignalingEvent.PeerOffline -> {
                if (event.deviceId == peerId || event.deviceId == linkedPeerId) _peerOnline.value = false
            }

            is SignalingEvent.PeersOnline -> {
                // Ciclo 11: al conectarse, el servidor envía la lista de dispositivos
                // conectados. Si nuestro par vinculado está en la lista → "En línea".
                _peerOnline.value = event.deviceIds.contains(linkedPeerId)
            }

            is SignalingEvent.LinkedByPeer -> {
                // El supervisor (hijo) nos acaba de vincular: guardamos su ID y nombre
                // para mostrar el botón rojo en la pantalla supervisada.
                persistIncomingLink(event.from, event.name)
            }

            is SignalingEvent.LinkAck -> {
                // Confirmación del supervisado (padre). No requiere acción adicional;
                // el vínculo local del supervisor ya está guardado.
            }

            else -> {}
        }
    }

    /** Callback para cuando llega una llamada entrante en modo Supervisor. */
    var onIncomingCall: ((String) -> Unit)? = null

    /**
     * Maneja una llamada entrante recibida por push FCM (v1.2).
     * Cuando el Supervisor no tiene WebSocket activo, el servidor envía un push;
     * este método fija el estado RINGING para que la UI muestre la llamada entrante
     * y, si el WebSocket aún no conecta, deja el estado listo para aceptar.
     */
    fun onFcmIncomingCall(fromDeviceId: String, fromName: String) {
        _incomingPeerId.value = fromDeviceId
        peerId = fromDeviceId
        peerName = fromName
        _callState.value = CallState.RINGING
        onIncomingCall?.invoke(fromName)
    }

    /** Inicia una llamada saliente hacia el peer. */
    fun startOutgoingCall(peerId: String, peerName: String) {
        // Guarda anti-duplicado (I-006)
        if (_callInProgress.value) return
        when (_callState.value) {
            CallState.DIALING, CallState.RINGING, CallState.CONNECTING, CallState.ACTIVE -> return
            else -> {}
        }
        this.peerId = peerId
        this.peerName = peerName
        _activePeerId.value = peerId
        _callState.value = CallState.DIALING
        _callInProgress.value = true
        onForegroundStart()
        signaling?.sendCall(peerId)
    }

    /** Acepta una llamada entrante (usado en auto-respuesta supervisada y supervisor). */
    fun acceptIncomingCall() {
        // Guarda anti-duplicado (I-006 / I-001 ciclo 3): RINGING NO bloquea la aceptación,
        // porque handleEvent(IncomingCall) ya fijó RINGING en modo Supervisor antes de que
        // el usuario decida. El flujo auto-aceptado del Supervisado llega con IDLE.
        if (_callInProgress.value) return
        when (_callState.value) {
            CallState.DIALING, CallState.CONNECTING, CallState.ACTIVE -> return
            else -> {}
        }
        val from = _incomingPeerId.value
        if (from.isEmpty()) return
        peerId = from
        _activePeerId.value = from
        _callState.value = CallState.RINGING
        _callInProgress.value = true
        onForegroundStart()
        signaling?.sendCallAccepted(from)
    }

    /** Rechaza una llamada entrante. */
    fun rejectIncomingCall() {
        val from = _incomingPeerId.value
        if (from.isNotEmpty()) {
            signaling?.sendCallRejected(from)
        }
        _incomingPeerId.value = ""
        _callState.value = CallState.ENDED
        // Cancelar la notificación de llamada entrante (I-004 ciclo 3), igual que cleanupCall().
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(com.alertamayor.app.service.NotificationHelper.NOTIFICATION_ID_INCOMING)
    }

    private fun startWebRtcAsInitiator() {
        _callState.value = CallState.CONNECTING
        setupWebRtc(true) { sdp ->
            signaling?.sendOffer(peerId, sdp)
        }
    }

    private fun startWebRtcAsReceiver() {
        _callState.value = CallState.CONNECTING
        setupWebRtc(false) {
            // La answer se envía desde handleEvent Offer
        }
    }

    private fun setupWebRtc(initiator: Boolean, onOfferReady: (String) -> Unit) {
        val prepared = webRtc.prepareCapture()
        // Altavoz automático en modo Supervisado
        if (mode == AppMode.SUPERVISED) {
            audio.enableSpeakerphone()
            _speakerOn.value = true
        }
        webRtc.listener = object : WebRtcManager.Listener {
            override fun onConnectionEstablished() {
                _callState.value = CallState.ACTIVE
            }

            override fun onDisconnected() {
                // F-002 (ciclo 5): NO llamar cleanupCall() inline aquí. Este callback se
                // ejecuta desde el hilo de señalización NATIVO de WebRTC (signaling_thread);
                // hacer dispose() desde ahí causa SIGABRT (bug 3721 de WebRTC). Se despacha
                // al Main looper vía scope.launch para salir del hilo nativo primero.
                _callState.value = CallState.ENDED
                scope.launch { cleanupCall() }
            }

            override fun onIceStateChanged(state: PeerConnection.IceConnectionState) {}
        }
        webRtc.onCandidate = { candidate: IceCandidate ->
            signaling?.sendIceCandidate(peerId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
        }
        val iceServers = WebRtcManager.buildIceServers(turnUrl, turnUsername, turnPassword)
        webRtc.startPeerConnection(initiator, iceServers)
        if (initiator) {
            webRtc.createOffer(onOfferReady)
        }
    }

    fun hangUp() {
        signaling?.sendEndCall(peerId)
        cleanupCall()
        _callState.value = CallState.ENDED
    }

    private fun cleanupCall() {
        // F-002 (ciclo 5): guard idempotente. cleanupCall() puede llegar por varias vías
        // (hangUp, onDisconnected, EndCall, CallRejected, PeerUnavailable); el compareAndSet
        // garantiza que solo el primero ejecute el cleanup y los demás sean no-op.
        if (!cleaningUp.compareAndSet(false, true)) return
        try {
            webRtc.close()
            audio.restore()
            _callInProgress.value = false
            _incomingPeerId.value = ""
            _activePeerId.value = ""
            // Ciclo 11: NO resetear _peerOnline aquí. El estado "En línea" del par es
            // independiente de la llamada; se gestiona con peer-online/peer-offline/
            // peers-online. Al colgar, el par sigue conectado y el indicador debe
            // permanecer correcto.
            onForegroundStop()
            // Cancelar la notificación de llamada en curso y la entrante.
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(com.alertamayor.app.service.NotificationHelper.NOTIFICATION_ID_CALL)
            nm.cancel(com.alertamayor.app.service.NotificationHelper.NOTIFICATION_ID_INCOMING)
        } finally {
            cleaningUp.set(false)
        }
    }

    fun toggleMute() {
        val newState = !_muted.value
        _muted.value = newState
        webRtc.setAudioEnabled(!newState)
    }

    fun toggleSpeaker() {
        if (_speakerOn.value) {
            audio.disableSpeakerphone()
            _speakerOn.value = false
        } else {
            audio.enableSpeakerphone()
            _speakerOn.value = true
        }
    }

    fun switchCamera() {
        webRtc.switchCamera()
    }

    fun stop() {
        scope.cancel()
    }
}
