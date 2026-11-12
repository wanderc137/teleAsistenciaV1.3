package com.alertamayor.app.domain.model

/** Información de un dispositivo vinculado (par). */
data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val online: Boolean = false,
)
