package com.alertamayor.app.data.signaling

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Tipos de mensaje del protocolo de señalización. */
object SignalingTypes {
    const val REGISTER = "register"
    const val REGISTERED = "registered"
    const val CALL = "call"
    const val CALL_ACCEPTED = "call-accepted"
    const val CALL_REJECTED = "call-rejected"
    const val OFFER = "offer"
    const val ANSWER = "answer"
    const val ICE_CANDIDATE = "ice-candidate"
    const val END_CALL = "end-call"
    const val PEER_UNAVAILABLE = "peer-unavailable"
    const val ERROR = "error"
    const val PEER_ONLINE = "peer-online"
    const val PEER_OFFLINE = "peer-offline"
    const val PEERS_ONLINE = "peers-online"
    const val LINK = "link"
    const val LINK_ACK = "link-ack"
    const val REGISTER_FCM = "register-fcm"
}

/** Mensaje genérico de señalización (serializable a JSON). */
@Serializable
data class SignalingMessage(
    val type: String,
    val from: String? = null,
    val to: String? = null,
    val payload: JsonElement? = null,
)
