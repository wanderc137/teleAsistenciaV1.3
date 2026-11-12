package com.alertamayor.app.data.signaling

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Cliente de señalización WebRTC basado en OkHttp WebSocket.
 *
 * Conecta al servidor Node.js, registra el deviceId y enruta los mensajes
 * SDP/ICE entre pares vinculados.
 */
class SignalingClient(
    private val serverUrl: String,
    private val deviceId: String,
    private val defaultPeerName: String,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var webSocket: WebSocket? = null
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default)

    private val _events = Channel<SignalingEvent>(Channel.UNLIMITED)
    val events: Flow<SignalingEvent> = _events.receiveAsFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private var reconnectJob: kotlinx.coroutines.Job? = null

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun connect() {
        val request = Request.Builder().url(serverUrl).build()
        val ws = client.newWebSocket(request, listener)
        webSocket = ws
    }

    fun register() {
        sendMessage(
            SignalingMessage(
                type = SignalingTypes.REGISTER,
                from = deviceId,
                to = null,
                payload = buildJsonObject { put("deviceId", deviceId) }
            )
        )
    }

    fun sendCall(targetId: String) {
        sendMessage(
            SignalingMessage(
                type = SignalingTypes.CALL,
                from = deviceId,
                to = targetId,
                payload = buildJsonObject { put("fromName", deviceId) }
            )
        )
    }

    fun sendCallAccepted(targetId: String) {
        sendMessage(SignalingMessage(type = SignalingTypes.CALL_ACCEPTED, from = deviceId, to = targetId))
    }

    fun sendCallRejected(targetId: String) {
        sendMessage(SignalingMessage(type = SignalingTypes.CALL_REJECTED, from = deviceId, to = targetId))
    }

    fun sendOffer(targetId: String, sdp: String) {
        sendMessage(
            SignalingMessage(
                type = SignalingTypes.OFFER,
                from = deviceId,
                to = targetId,
                payload = buildJsonObject { put("sdp", sdp) }
            )
        )
    }

    fun sendAnswer(targetId: String, sdp: String) {
        sendMessage(
            SignalingMessage(
                type = SignalingTypes.ANSWER,
                from = deviceId,
                to = targetId,
                payload = buildJsonObject { put("sdp", sdp) }
            )
        )
    }

    fun sendIceCandidate(targetId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
        sendMessage(
            SignalingMessage(
                type = SignalingTypes.ICE_CANDIDATE,
                from = deviceId,
                to = targetId,
                payload = buildJsonObject {
                    put("candidate", candidate)
                    put("sdpMid", sdpMid ?: "")
                    put("sdpMLineIndex", sdpMLineIndex ?: 0)
                }
            )
        )
    }

    fun sendEndCall(targetId: String) {
        sendMessage(SignalingMessage(type = SignalingTypes.END_CALL, from = deviceId, to = targetId))
    }

    fun sendLink(targetId: String, name: String) {
        sendMessage(
            SignalingMessage(
                type = SignalingTypes.LINK,
                from = deviceId,
                to = targetId,
                payload = buildJsonObject { put("name", name) }
            )
        )
    }

    fun sendLinkAck(targetId: String) {
        sendMessage(SignalingMessage(type = SignalingTypes.LINK_ACK, from = deviceId, to = targetId))
    }

    /** Registra el token FCM en el servidor para poder recibir push de alta prioridad. */
    fun sendFcmToken(token: String) {
        sendMessage(
            SignalingMessage(
                type = SignalingTypes.REGISTER_FCM,
                from = deviceId,
                to = null,
                payload = buildJsonObject { put("token", token) }
            )
        )
    }

    private fun sendMessage(message: SignalingMessage) {
        val text = json.encodeToString(SignalingMessage.serializer(), message)
        webSocket?.send(text)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connected.value = true
            register()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = runCatching {
                json.decodeFromString(SignalingMessage.serializer(), text)
            }.getOrNull() ?: return

            handleMessage(message)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connected.value = false
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connected.value = false
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            kotlinx.coroutines.delay(3000)
            connect()
        }
    }

    private fun handleMessage(message: SignalingMessage) {
        when (message.type) {
            SignalingTypes.REGISTERED -> scope.launch {
                _events.send(SignalingEvent.Registered(message.payload?.toString()?.trim('"') ?: ""))
            }
            SignalingTypes.CALL -> scope.launch {
                message.from?.let { _events.send(SignalingEvent.IncomingCall(it)) }
            }
            SignalingTypes.CALL_ACCEPTED -> scope.launch {
                message.from?.let { _events.send(SignalingEvent.CallAccepted(it)) }
            }
            SignalingTypes.CALL_REJECTED -> scope.launch {
                message.from?.let { _events.send(SignalingEvent.CallRejected(it)) }
            }
            SignalingTypes.OFFER -> scope.launch {
                val sdp = message.payload?.jsonObject?.get("sdp")?.jsonPrimitive?.content ?: ""
                message.from?.let { _events.send(SignalingEvent.Offer(it, sdp)) }
            }
            SignalingTypes.ANSWER -> scope.launch {
                val sdp = message.payload?.jsonObject?.get("sdp")?.jsonPrimitive?.content ?: ""
                message.from?.let { _events.send(SignalingEvent.Answer(it, sdp)) }
            }
            SignalingTypes.ICE_CANDIDATE -> scope.launch {
                val payload = message.payload?.jsonObject
                val candidate = payload?.get("candidate")?.jsonPrimitive?.content ?: ""
                val mid = payload?.get("sdpMid")?.jsonPrimitive?.content
                val index = payload?.get("sdpMLineIndex")?.toString()?.toIntOrNull()
                message.from?.let { _events.send(SignalingEvent.IceCandidate(it, candidate, mid, index)) }
            }
            SignalingTypes.END_CALL -> scope.launch {
                message.from?.let { _events.send(SignalingEvent.EndCall(it)) }
            }
            SignalingTypes.PEER_UNAVAILABLE -> scope.launch {
                message.to?.let { _events.send(SignalingEvent.PeerUnavailable(it)) }
                    ?: message.from?.let { _events.send(SignalingEvent.PeerUnavailable(it)) }
            }
            SignalingTypes.ERROR -> scope.launch {
                val msg = message.payload?.toString()?.trim('"') ?: "error"
                _events.send(SignalingEvent.Error(msg))
            }
            SignalingTypes.PEER_ONLINE -> scope.launch {
                message.from?.let { _events.send(SignalingEvent.PeerOnline(it)) }
            }
            SignalingTypes.PEER_OFFLINE -> scope.launch {
                message.from?.let { _events.send(SignalingEvent.PeerOffline(it)) }
            }
            SignalingTypes.PEERS_ONLINE -> scope.launch {
                // Ciclo 11: el servidor envía la lista de dispositivos conectados al
                // registrarse; con ella el CallCoordinator marca "En línea" correctamente.
                val ids = message.payload?.jsonObject?.get("deviceIds")
                    ?.let { el -> el.jsonArray.map { it.jsonPrimitive.content } }
                    ?: emptyList()
                _events.send(SignalingEvent.PeersOnline(ids))
            }
            SignalingTypes.LINK -> scope.launch {
                val name = message.payload?.jsonObject?.get("name")?.jsonPrimitive?.content ?: defaultPeerName
                message.from?.let { _events.send(SignalingEvent.LinkedByPeer(it, name)) }
            }
            SignalingTypes.LINK_ACK -> scope.launch {
                message.from?.let { _events.send(SignalingEvent.LinkAck(it)) }
            }
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "bye")
        webSocket = null
        scope.cancel()
    }
}
