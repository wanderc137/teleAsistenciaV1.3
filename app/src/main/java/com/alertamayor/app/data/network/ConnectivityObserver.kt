package com.alertamayor.app.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Estado de conectividad a internet del dispositivo.
 *
 * - [WIFI]: hay red Wi-Fi activa Y validada con internet.
 * - [CELLULAR]: hay datos móviles (celular) activos Y validados con internet.
 * - [NONE]: no hay internet por ninguna vía (ni WiFi ni celular validados).
 */
enum class NetworkStatus { WIFI, CELLULAR, NONE }

/**
 * Observador de conectividad a internet basado en
 * [ConnectivityManager.registerDefaultNetworkCallback] (API 24+).
 *
 * Expone un [StateFlow] que se actualiza en tiempo real cuando el dispositivo
 * pierde/recupera Wi-Fi o datos móviles. Es un singleton creado en
 * [com.alertamayor.app.di.AppContainer].
 *
 * Adicionalmente ejecuta una **verificación periódica** (poller) cada ~4 segundos
 * que relee el estado real de la red. Esto garantiza que, aunque los callbacks de
 * red no disparen de inmediato (p. ej. en EMUI/Huawei al cortar la WiFi), la app
 * detecte la pérdida de internet en pocos segundos.
 */
class ConnectivityObserver(context: Context) {

    private val connectivityManager =
        context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    /** Scope propio del observador para la verificación periódica de conectividad. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _status.value = readStatus()
        }

        override fun onLost(network: Network) {
            _status.value = readStatus()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _status.value = readStatus()
        }
    }

    init {
        register()
        startPoller()
    }

    /**
     * Lee el estado de red actual consultando la red activa y sus capacidades.
     *
     * Devuelve [NetworkStatus.NONE] cuando:
     * - no hay red activa (`activeNetwork == null`), o
     * - no se pueden leer sus capacidades (`getNetworkCapabilities(...) == null`), o
     * - la red NO tiene [NetworkCapabilities.NET_CAPABILITY_INTERNET], o
     * - la red NO tiene [NetworkCapabilities.NET_CAPABILITY_VALIDATED].
     *
     * Solo devuelve [NetworkStatus.WIFI] si la red tiene transporte WiFi Y está
     * validada con internet; [NetworkStatus.CELLULAR] si tiene transporte celular Y
     * está validada con internet; en cualquier otro caso, [NetworkStatus.NONE].
     */
    private fun readStatus(): NetworkStatus {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkStatus.NONE
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkStatus.NONE
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!hasInternet) return NetworkStatus.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkStatus.CELLULAR
            else -> NetworkStatus.NONE
        }
    }

    /**
     * Verificación periódica de respaldo: cada ~4 segundos relee [readStatus] y
     * actualiza [status] si el valor cambió. Garantiza que la app detecte la pérdida
     * de internet aunque los callbacks de red no disparen a tiempo.
     */
    private fun startPoller() {
        scope.launch {
            while (isActive) {
                val s = readStatus()
                if (_status.value != s) _status.value = s
                delay(4000)
            }
        }
    }

    /** Registra el callback de red (se llama en el init; idempotente). */
    fun register() {
        runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }
    }

    /** Desregistra el callback de red y detiene limpiamente el poller de respaldo. */
    fun unregister() {
        scope.cancel()
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
}