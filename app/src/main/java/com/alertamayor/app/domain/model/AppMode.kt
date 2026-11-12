package com.alertamayor.app.domain.model

/** Modo de uso de la app. */
enum class AppMode {
    SUPERVISED,
    SUPERVISOR;

    companion object {
        fun fromName(name: String?): AppMode? =
            entries.firstOrNull { it.name == name }
    }
}
