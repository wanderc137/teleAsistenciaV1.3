package com.alertamayor.app.data.signaling

/** Eventos de señalización que emite el cliente hacia el resto de la app. */
sealed interface SignalingEvent {
    data class Registered(val deviceId: String) : SignalingEvent
    data class IncomingCall(val from: String) : SignalingEvent
    data class CallAccepted(val from: String) : SignalingEvent
    data class CallRejected(val from: String) : SignalingEvent
    data class Offer(val from: String, val sdp: String) : SignalingEvent
    data class Answer(val from: String, val sdp: String) : SignalingEvent
    data class IceCandidate(val from: String, val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int?) : SignalingEvent
    data class EndCall(val from: String) : SignalingEvent
    data class PeerUnavailable(val target: String) : SignalingEvent
    data class Error(val message: String) : SignalingEvent
    data class PeerOnline(val deviceId: String) : SignalingEvent
    data class PeerOffline(val deviceId: String) : SignalingEvent
    data class PeersOnline(val deviceIds: List<String>) : SignalingEvent
    data class LinkedByPeer(val from: String, val name: String) : SignalingEvent
    data class LinkAck(val from: String) : SignalingEvent
}
