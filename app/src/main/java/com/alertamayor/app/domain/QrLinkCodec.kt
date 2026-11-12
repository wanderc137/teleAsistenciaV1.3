package com.alertamayor.app.domain

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Codifica y decodifica el contenido del código QR de vinculación.
 *
 * Formato: esquema propio `alertamayor:link?deviceId=<uuid>` para extensibilidad futura.
 */
object QrLinkCodec {

    const val SCHEME = "alertamayor:link"

    /** Genera el contenido del QR a partir del deviceId. */
    fun encode(deviceId: String): String = "$SCHEME?deviceId=$deviceId"

    /**
     * Extrae el deviceId del contenido escaneado.
     * @return el deviceId, o null si el contenido no es válido.
     */
    fun decode(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith(SCHEME)) {
            val query = trimmed.substringAfter("?", "")
            val params = query.split("&")
                .map { it.split("=", limit = 2) }
                .filter { it.size == 2 }
                .associate { it[0] to it[1] }
            val id = params["deviceId"]
            return id?.takeIf { it.isNotBlank() }
        }
        // Retrocompatibilidad con formato JSON plano {"deviceId":"..."}
        return runCatching {
            val json = kotlinx.serialization.json.Json.parseToJsonElement(trimmed).jsonObject
            json["deviceId"]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
