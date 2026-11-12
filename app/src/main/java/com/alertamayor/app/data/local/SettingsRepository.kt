package com.alertamayor.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alertamayor.app.BuildConfig
import com.alertamayor.app.domain.model.AppMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "alertamayor_settings")

/**
 * Repositorio de configuración persistida con DataStore Preferences.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val MODE = stringPreferencesKey("mode")
        val LINKED_PEER_ID = stringPreferencesKey("linked_peer_id")
        val LINKED_PEER_NAME = stringPreferencesKey("linked_peer_name")
        val SERVER_URL = stringPreferencesKey("signaling_server_url")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val THEME = stringPreferencesKey("theme")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        val TURN_URL = stringPreferencesKey("turn_url")
        val TURN_USERNAME = stringPreferencesKey("turn_username")
        val TURN_PASSWORD = stringPreferencesKey("turn_password")
        val LANGUAGE = stringPreferencesKey("language")
    }

    val deviceId: Flow<String> = context.dataStore.data.map { it[Keys.DEVICE_ID] ?: "" }
    val deviceName: Flow<String> = context.dataStore.data.map { it[Keys.DEVICE_NAME] ?: "" }
    val mode: Flow<AppMode?> = context.dataStore.data.map { AppMode.fromName(it[Keys.MODE]) }
    val linkedPeerId: Flow<String> = context.dataStore.data.map { it[Keys.LINKED_PEER_ID] ?: "" }
    val linkedPeerName: Flow<String> = context.dataStore.data.map { it[Keys.LINKED_PEER_NAME] ?: "" }
    val serverUrl: Flow<String> =
        context.dataStore.data.map { it[Keys.SERVER_URL]?.takeIf { url -> url.isNotBlank() } ?: BuildConfig.SIGNALING_SERVER_URL }
    val onboardingDone: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    /** Preferencia de tema: "system" (default) | "light" | "dark". */
    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "system" }

    /** Token FCM registrado para este dispositivo (vacío si aún no se ha generado). */
    val fcmToken: Flow<String> = context.dataStore.data.map { it[Keys.FCM_TOKEN] ?: "" }

    /** URL del servidor TURN (p. ej. "turn:TU-DOMINIO.com:3478?transport=tcp"). */
    val turnUrl: Flow<String> = context.dataStore.data.map { it[Keys.TURN_URL] ?: "" }

    /** Usuario del servidor TURN. */
    val turnUsername: Flow<String> = context.dataStore.data.map { it[Keys.TURN_USERNAME] ?: "" }

    /** Contraseña del servidor TURN. */
    val turnPassword: Flow<String> = context.dataStore.data.map { it[Keys.TURN_PASSWORD] ?: "" }

    /** Idioma de la interfaz: "es" (default) | "en". — V1.3 */
    val language: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "es" }

    suspend fun saveDeviceId(id: String) {
        context.dataStore.edit { it[Keys.DEVICE_ID] = id }
    }

    suspend fun saveDeviceName(name: String) {
        context.dataStore.edit { it[Keys.DEVICE_NAME] = name }
    }

    suspend fun saveMode(mode: AppMode) {
        context.dataStore.edit { it[Keys.MODE] = mode.name }
    }

    suspend fun saveLinkedPeer(id: String?, name: String?) {
        context.dataStore.edit {
            it[Keys.LINKED_PEER_ID] = id ?: ""
            it[Keys.LINKED_PEER_NAME] = name ?: ""
        }
    }

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { it[Keys.SERVER_URL] = url }
    }

    suspend fun setOnboardingDone(done: Boolean = true) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    /** Guarda la preferencia de tema ("system" | "light" | "dark"). */
    suspend fun saveTheme(theme: String) {
        context.dataStore.edit { it[Keys.THEME] = theme }
    }

    /** Guarda el token FCM del dispositivo (lo genera FirebaseMessagingService.onNewToken). */
    suspend fun saveFcmToken(token: String) {
        context.dataStore.edit { it[Keys.FCM_TOKEN] = token }
    }

    /** Guarda la configuración TURN (URL, usuario y contraseña) en un único edit atómico. */
    suspend fun saveTurnConfig(url: String, username: String, password: String) {
        context.dataStore.edit {
            it[Keys.TURN_URL] = url.trim()
            it[Keys.TURN_USERNAME] = username.trim()
            it[Keys.TURN_PASSWORD] = password
        }
    }

    /** Guarda el idioma de la interfaz ("es" | "en"). — V1.3 */
    suspend fun saveLanguage(lang: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = lang }
    }

    suspend fun resetLinkAndDeviceId() {
        context.dataStore.edit {
            it[Keys.LINKED_PEER_ID] = ""
            it[Keys.LINKED_PEER_NAME] = ""
            it[Keys.DEVICE_ID] = java.util.UUID.randomUUID().toString()
        }
    }
}
