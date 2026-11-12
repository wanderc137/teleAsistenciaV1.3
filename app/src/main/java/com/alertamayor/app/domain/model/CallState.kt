package com.alertamayor.app.domain.model

/** Estado de la llamada WebRTC. */
enum class CallState {
    IDLE,
    DIALING,
    RINGING,
    CONNECTING,
    ACTIVE,
    ENDED,
}
