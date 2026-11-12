# Alerta Mayor — Documentación para desarrolladores

**Versión:** 1.3.0 · **Package:** `com.alertamayor.app` · **Kotlin 2.0.21** · **Compose + Material 3**

Este documento explica cómo está construida la app, por qué está construida así y cómo extenderla sin romper lo que ya funciona. Está pensado para alguien que abre el repositorio por primera vez y quiere entender el código en una sesión de lectura.

---

## Índice

1. [Principios de diseño](#principios-de-diseño)
2. [Stack y toolchain](#stack-y-toolchain)
3. [Mapa de módulos](#mapa-de-módulos)
4. [Arranque de la aplicación](#arranque-de-la-aplicación)
5. [Capa de dominio](#capa-de-dominio)
6. [Persistencia](#persistencia)
7. [Red y señalización](#red-y-señalización)
8. [WebRTC](#webrtc)
9. [Servicios, notificaciones y FCM](#servicios-notificaciones-y-fcm)
10. [Capa de UI](#capa-de-ui)
11. [Flujos completos](#flujos-completos)
12. [Concurrencia y threading](#concurrencia-y-threading)
13. [Build y variantes](#build-y-variantes)
14. [Firma y release](#firma-y-release)
15. [Guía de extensión](#guía-de-extensión)
16. [Depuración](#depuración)
17. [Deuda técnica y riesgos](#deuda-técnica-y-riesgos)
18. [Referencia rápida](#referencia-rápida)

---

## Principios de diseño

Cinco decisiones explican casi todo el código:

1. **El estado de llamada vive fuera de la UI.** `CallCoordinator` es un singleton en `AppContainer`; las pantallas son observadoras. Así una llamada sobrevive a la rotación, al cierre de la pantalla y a un cambio de Activity.
2. **El camino del adulto mayor no tiene decisiones.** Sin botón de aceptar, sin navegación, sin Back. Todo lo que se pueda automatizar se automatiza.
3. **Toda operación destructiva es idempotente.** `cleanupCall()`, `WebRtcManager.close()` y la cancelación de notificaciones pueden invocarse varias veces sin efectos secundarios.
4. **El servidor es tonto a propósito.** Enruta JSON y guarda tres mapas. No autentica, no persiste, no toca medios. Es fácil de razonar y de reemplazar.
5. **Los secretos no entran al repositorio.** `google-services.json`, `local.properties`, `serviceAccountKey.json` y cualquier keystore están excluidos por `.gitignore`.

---

## Stack y toolchain

| Pieza | Versión | Dónde se declara |
|---|---|---|
| Gradle Wrapper | 8.13 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.9.2 | `gradle/libs.versions.toml` |
| Kotlin | 2.0.21 | idem |
| Compose Compiler | plugin de Kotlin 2.0 | `app/build.gradle.kts` |
| Compose BOM | 2025.01.01 | catálogo |
| JVM target | 17 | `compileOptions` y `kotlinOptions` |
| compileSdk / targetSdk | 35 / 35 | `app/build.gradle.kts` |
| minSdk | 24 | idem |
| KSP | 2.0.21-1.0.28 | declarado, **no aplicado** (no hay procesadores) |

### Plugins aplicados

```kotlin
// raíz
alias(libs.plugins.android.application) apply false
alias(libs.plugins.kotlin.android) apply false
alias(libs.plugins.kotlin.compose) apply false
alias(libs.plugins.kotlin.serialization) apply false
alias(libs.plugins.google.services) apply false

// app
alias(libs.plugins.android.application)
alias(libs.plugins.kotlin.android)
alias(libs.plugins.kotlin.compose)
alias(libs.plugins.kotlin.serialization)
alias(libs.plugins.google.services)
```

`google-services` es obligatorio: sin `app/google-services.json` el build falla en `processDebugGoogleServices`.

### Catálogo de versiones

`gradle/libs.versions.toml` es la **fuente de verdad**. No hay versiones hardcodeadas en los `build.gradle.kts` salvo las de los plugins raíz. Para actualizar una librería se cambia el catálogo, no el módulo.

---

## Mapa de módulos

Un solo módulo `:app`, organizado por responsabilidad:

```
com.alertamayor.app
├── MainActivity.kt          Activity única; permisos, tema, intents de notificación
├── AlertaMayorApp.kt        Application; crea AppContainer y canales
├── BootReceiver.kt          Arranque tras BOOT_COMPLETED
├── di/
│   └── AppContainer.kt      DI manual; dueño del ciclo de vida de los singletons
├── domain/
│   ├── QrLinkCodec.kt       encode/decode del QR (esquema + JSON legado)
│   └── model/
│       ├── AppMode.kt       SUPERVISED / SUPERVISOR
│       ├── CallState.kt     IDLE…ENDED
│       └── DeviceInfo.kt    deviceId, name, online
├── data/
│   ├── local/SettingsRepository.kt      DataStore, 13 claves
│   ├── network/ConnectivityObserver.kt  Wi-Fi / datos / ninguno
│   └── signaling/
│       ├── SignalingMessage.kt          DTO serializable
│       ├── SignalingEvents.kt           eventos de dominio
│       └── SignalingClient.kt           OkHttp WebSocket + reconexión
├── webrtc/
│   ├── CallCoordinator.kt      orquestador y máquina de estados
│   ├── WebRtcManager.kt        PeerConnection, captura, ICE, renderers
│   └── AudioSpeakerController.kt  ruteo de audio
├── service/
│   ├── CallService.kt          FGS camera|microphone
│   ├── GuardService.kt         FGS dataSync + wakelock
│   ├── FcmService.kt           push de emergencia
│   └── NotificationHelper.kt   canales, IDs, builders
└── ui/
    ├── navigation/NavGraph.kt
    ├── onboarding/ (ModeSelectionScreen, OnboardingViewModel)
    ├── supervised/ (SupervisedHomeScreen, SupervisedHomeViewModel)
    ├── supervisor/ (SupervisorHomeScreen, SupervisorHomeViewModel)
    ├── qr/QrScannerScreen.kt
    ├── call/ (CallScreen, CallViewModel)
    ├── settings/ (SettingsScreen, SettingsViewModel)
    ├── theme/ (Color, Theme, Type)
    └── AppViewModelFactory.kt
```

### Regla de dependencias

```
ui → webrtc/domain/data → service
```

La UI **nunca** importa `org.webrtc` ni `okhttp3`. Si necesitas algo de ahí en una pantalla, falta un método en `CallCoordinator`.

---

## Arranque de la aplicación

### `AlertaMayorApp`

```kotlin
class AlertaMayorApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationHelper.createChannels()
    }
}
```

Crea el contenedor **antes** de cualquier Activity, para que `MainActivity` pueda pedir el coordinador sin condiciones de carrera.

### `AppContainer`

Es el grafo de dependencias, a mano:

| Miembro | Tipo | Notas |
|---|---|---|
| `settingsRepository` | `SettingsRepository` | Único dueño de DataStore |
| `connectivityObserver` | `ConnectivityObserver` | Registra callbacks |
| `webRtcManager` | `WebRtcManager` | Crea EglBase y factory una vez |
| `audioSpeakerController` | `AudioSpeakerController` | Envuelve `AudioManager` |
| `notificationHelper` | `NotificationHelper` | Canales y builders |
| `callCoordinator` | `CallCoordinator` | `by lazy`; depende de los anteriores |
| `startCallService/stopCallService` | métodos | Intent + `stopService` |
| `startGuardService/stopGuardService` | métodos | Idem |
| `defaultPeerName()` | método | "Familiar" si no hay nombre |

`stopCallService` comprueba `getRunningServices` antes de parar: llamar `stopService` sobre un servicio no iniciado es inofensivo, pero la comprobación evita ruido en logs y efectos raros en algunos fabricantes.

### `MainActivity`

Responsabilidades, en orden:

1. `setContent { AlertaMayorTheme(theme) { NavGraph() } }`.
2. `window.addFlags(FLAG_KEEP_SCREEN_ON)`.
3. `coordinator.connect()` — arranca `GuardService` y la señalización.
4. Pedir exención de batería (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
5. Pedir `POST_NOTIFICATIONS` en API 33+.
6. Procesar `ACCEPT_CALL` / `REJECT_CALL` del intent, en `onCreate` **y** en `onNewIntent` (por `singleTask`).
7. Observar `settingsRepository.theme` y recomponer.

### `BootReceiver`

Escucha `BOOT_COMPLETED` y lanza `MainActivity` con `FLAG_ACTIVITY_NEW_TASK`. En algunos fabricantes el sistema bloquea el arranque en frío; el receptor está declarado `exported="true"` porque el broadcast es del sistema.

---

## Capa de dominio

### `QrLinkCodec`

```kotlin
object QrLinkCodec {
    const val SCHEME = "alertamayor:link"

    fun encode(deviceId: String): String = "$SCHEME?deviceId=$deviceId"

    fun decode(raw: String): String?      // devuelve el UUID o null
}
```

Reglas de `decode`:

1. Si empieza por `alertamayor:link`, parsea el query y exige `deviceId` no vacío.
2. Si no, intenta `Json.decodeFromString<Map<String, String>>(raw)` y lee `deviceId`.
3. Cualquier excepción devuelve `null`; el escáner muestra "No se pudo leer el código QR".

**Por qué acepta JSON:** permite generar códigos de prueba con cualquier generador online sin implementar el esquema. Es deuda deliberada y acotada.

**Test mental:** `decode(encode(id)) == id` para cualquier UUID válido. No hay test automatizado; es el primer candidato.

### `AppMode`

```kotlin
enum class AppMode { SUPERVISED, SUPERVISOR;
    companion object { fun fromName(value: String?): AppMode }
}
```

`fromName` cae en `SUPERVISOR` si el valor es desconocido o nulo. Ojo: la ruta inicial no depende del modo sino de `onboarding_done`.

### `CallState`

```
IDLE → DIALING → RINGING → CONNECTING → ACTIVE → ENDED → (cleanup) → IDLE
```

No todas las transiciones pasan por todos los estados:

- Saliente: `IDLE → DIALING → RINGING` (si el otro timbra) `→ CONNECTING → ACTIVE`.
- Entrante: `IDLE → RINGING → CONNECTING → ACTIVE`.
- Auto-aceptada (supervisado): `RINGING → CONNECTING` sin intervención.

`ENDED` es un estado **observable**, no un reset inmediato: permite que la UI muestre "Llamada finalizada" durante 1200 ms antes de navegar.

### `DeviceInfo`

DTO de presentación (`deviceId`, `name`, `online`). No se persiste como tal: se compone desde DataStore y los eventos de presencia.

---

## Persistencia

### `SettingsRepository`

Envuelve `Preferences DataStore` con nombre `alertamayor_settings`. Expone `Flow<T>` para lectura y `suspend fun` para escritura.

| Clave | Tipo | Default | Consumidor |
|---|---|---|---|
| `device_id` | String | UUID nuevo | `CallCoordinator` |
| `device_name` | String | `Build.MODEL` | UI, `link` |
| `mode` | String | `SUPERVISOR` | Navegación, auto-respuesta |
| `linked_peer_id` | String? | null | Llamadas, presencia |
| `linked_peer_name` | String? | null | Tarjeta y botón |
| `signaling_server_url` | String | `BuildConfig.SIGNALING_SERVER_URL` | `SignalingClient` |
| `onboarding_done` | Boolean | false | `startDestination` |
| `theme` | String | `system` | `MainActivity` |
| `fcm_token` | String? | null | Servidor |
| `turn_url` | String? | null | ICE |
| `turn_username` | String? | null | ICE |
| `turn_password` | String? | null | ICE |
| `language` | String | `es` | `AppCompatDelegate` |

### Generación del `deviceId`

```kotlin
suspend fun getOrCreateDeviceId(): String =
    prefs.data.map { it[DEVICE_ID] }.first() ?: run {
        val id = UUID.randomUUID().toString()
        prefs.edit { it[DEVICE_ID] = id }
        id
    }
```

Se genera una vez y se reutiliza. `resetLinkAndDeviceId()` borra `linked_peer_id`, `linked_peer_name` y `device_id`; la siguiente lectura crea uno nuevo.

### Limitación conocida

`fcm_token` y `turn_password` se guardan en claro. DataStore no cifra. La solución correcta es `EncryptedSharedPreferences` o una clave de Keystore, pero añade complejidad y una migración. Está anotado como deuda, no es un olvido.

---

## Red y señalización

### `ConnectivityObserver`

```kotlin
enum class NetworkStatus { WIFI, CELLULAR, NONE }
val status: StateFlow<NetworkStatus>
```

- `registerDefaultNetworkCallback` para cambios inmediatos.
- **Poller cada 4 s** como red de seguridad: en EMUI (Huawei) y algunas ROMs el callback no llega al perder Wi-Fi.
- Solo cuenta una red como disponible si tiene `NET_CAPABILITY_INTERNET` **y** `NET_CAPABILITY_VALIDATED`. Un Wi-Fi con portal cautivo se considera `NONE`.

El poller es la razón por la que el botón se reactiva en ≤ 4 s. Si se migra a `NetworkRequest` con `registerNetworkCallback`, hay que mantener el poller o asumir el riesgo.

### `SignalingMessage`

```kotlin
@Serializable
data class SignalingMessage(
    val type: String,
    val from: String? = null,
    val to: String? = null,
    val payload: JsonElement? = null
)
```

`payload` es `JsonElement` a propósito: cada tipo decide su forma y no hay que mantener diez DTOs. El coste es parseo manual en los eventos que lo necesitan (`sdp`, `candidate`, `name`).

### `SignalingClient`

Constructor: `(serverUrl, deviceIdProvider, scope)`. Responsabilidades:

1. Abrir `OkHttpClient.newWebSocket` con `pingInterval(20, SECONDS)`.
2. Serializar `register { deviceId, fcmToken }` al abrir.
3. Emitir `SignalingEvent` a un `Channel(UNLIMITED)`.
4. Exponer `connected: StateFlow<Boolean>`.
5. Reconectar tras `onFailure`/`onClosed` con `delay(3000)` y un `Job` cancelable.
6. `startSignaling` / `stopSignaling` idempotentes.

```kotlin
fun interface DeviceIdProvider { suspend fun deviceId(): String }
```

El provider es `suspend` porque el UUID puede requerir leer DataStore. Se resuelve una vez por conexión.

### Tabla de eventos

| Evento | Campos | Efecto en `CallCoordinator` |
|---|---|---|
| `Registered` | — | `connectionStatus = CONNECTED` |
| `IncomingCall` | `from`, `fromName` | RINGING; auto-acepta si SUPERVISED |
| `CallAccepted` | `from` | Inicia WebRTC como iniciador |
| `CallRejected` | — | ENDED + cleanup |
| `Offer` | `sdp` | `setRemoteOffer` + `createAnswer` |
| `Answer` | `sdp` | `setRemoteAnswer` |
| `IceCandidate` | `candidate`, `sdpMid`, `sdpMLineIndex` | `addIceCandidate` |
| `EndCall` | — | ENDED + cleanup |
| `PeerUnavailable` | — | ENDED + cleanup |
| `PeerOnline` / `PeerOffline` | `deviceId` | Actualiza `peerOnline` si es el par |
| `PeersOnline` | `deviceIds` | `peerOnline = contains(linkedPeerId)` |
| `LinkedByPeer` | `from`, `name` | Persiste el vínculo entrante |
| `LinkAck` | — | No-op (confirmación) |

### Servidor: decisiones de implementación

- **Mapas en memoria**, sin base de datos. Reiniciar pierde `pendingCalls` y `fcmTokens`; los clientes se re-registran solos.
- **Un socket por `deviceId`**: al registrarse de nuevo, se cierra el anterior. Evita el caso de dos sesiones peleando por el mismo destino.
- **`pendingCalls` con un solo mensaje por destino**: la última llamada gana. No es una cola FIFO; es una "llamada perdida pendiente".
- **FCM dentro del enrutado**: si no hay socket y el mensaje es `call`, se intenta push antes de responder `peer-unavailable`.
- **Normalización de `from`**: el servidor confía en el `deviceId` del socket, no en el campo del cliente, para evitar suplantación trivial entre clientes conectados.

### Riesgo de seguridad del protocolo

No hay autenticación. Cualquiera con la URL puede abrir un socket, registrarse con un `deviceId` ajeno (si está libre) y recibir sus llamadas. En una red de confianza es aceptable; en Internet, no. Las mitigaciones propuestas pasan por un token por instalación validado en `register`. Ver [BITACORA](BITACORA.md).

---

## WebRTC

### `WebRtcManager`

Ciclo de vida de los objetos nativos:

```
init:
  EglBase.create()
  PeerConnectionFactory.builder()
      .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl, true, true))
      .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl))
      .createPeerConnectionFactory()
  localView / remoteView (SurfaceViewRenderer)

prepareCapture():
  Camera2Enumerator(context)
  frontal ?: trasera
  SurfaceTextureHelper.create("CaptureThread", egl)
  videoSource = factory.createVideoSource(capturer.isScreencast)
  audioSource = factory.createAudioSource(AudioSource(isMuted))
  capturer.initialize(helper, context, videoSource.capturerObserver)
  capturer.startCapture(1280, 720, 30)
  videoSource.capturerObserver.onCapturerStarted? (implícito)
  localView.setMirror(true)
  videoSource.addSink(localView)

startPeerConnection(initiator, iceServers):
  pc = factory.createPeerConnection(iceServers, observer)
  if (initiator) pc.addTrack(videoTrack, "video")
                 pc.addTrack(audioTrack, "audio")
  pc.addTransceiver? (no: UNIFIED_PLAN con addTrack es suficiente)

close():
  if (closed) return
  synchronized(this) { if (closed) return; closed = true }
  capturer.stopCapture(); capturer.dispose()
  pc?.dispose(); pc = null
  videoSource?.dispose(); audioSource?.dispose()
  helper?.dispose()
  localView.clearImage(); remoteView.clearImage()
```

Detalles que importan:

- **`closed` + `synchronized`**: el `dispose()` nativo es irreversible. Un segundo `close()` sin guard produce `SIGABRT`.
- **Orden de liberación**: capturador → PC → fuentes → helper. Invertirlo deja hilos nativos con referencias colgando.
- **`prepareCapture()` por llamada**: la cámara no se mantiene abierta entre llamadas (batería, indicador de privacidad, permisos).
- **Reutilización de renderers**: se crean una vez y se limpian con `clearImage()`. Recrear `SurfaceViewRenderer` por llamada filtra EGL contexts.
- **`pendingOfferSdp`**: si el offer llega antes de que la PC exista, se guarda y se aplica en `startPeerConnection`.
- **`pendingIceCandidates`**: los candidatos que llegan antes del `remoteDescription` se encolan y se drenan después de `setRemoteDescription`.

### ICE

```kotlin
private fun buildIceServers(): List<PeerConnection.IceServer> = buildList {
    add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
    if (!turnUrl.isNullOrBlank()) {
        add(PeerConnection.IceServer.builder(turnUrl)
            .setUsername(turnUsername ?: "")
            .setPassword(turnPassword ?: "")
            .createIceServer())
    }
}
```

El TURN es **posicional**: URL, usuario y contraseña se leen de DataStore al iniciar la llamada. Si falta uno, se omite el TURN (no se envía a medias).

### `CallCoordinator`

#### Estado

```kotlin
val connectionStatus: StateFlow<ConnectionStatus>  // DISCONNECTED / CONNECTING / CONNECTED
val callState: StateFlow<CallState>
val activePeerId: StateFlow<String?>
val incomingPeerId: StateFlow<String?>
val muted: StateFlow<Boolean>
val speakerOn: StateFlow<Boolean>
val peerOnline: StateFlow<Boolean>
val callInProgress: StateFlow<Boolean>
```

#### Ciclo de vida

`connect()` se llama desde `MainActivity.onCreate`:

1. Arranca `GuardService` (en ambos modos).
2. Lee `deviceId`, `mode`, `linkedPeerId`, TURN.
3. Crea `SignalingClient` y `startSignaling`.
4. Reenvía el token FCM si existe.
5. Expone los eventos al `handleEvent`.

#### `handleEvent`

| Evento | Lógica |
|---|---|
| `IncomingCall` | `callState = RINGING`; guarda `incomingPeerId`; si `mode == SUPERVISED`, `acceptIncomingCall()`; si no, lanza `onIncomingCall` (notificación + overlay) |
| `CallAccepted` | `startWebRtcAsInitiator()` |
| `Offer` | `startWebRtcAsReceiver()`, `setRemoteOffer`, `createAnswer` |
| `Answer` | `webRtc.setRemoteAnswer` |
| `IceCandidate` | `webRtc.addIceCandidate` |
| `CallRejected` / `EndCall` / `PeerUnavailable` | `callState = ENDED`, `cleanupCall()` |
| `PeerOnline` / `PeerOffline` | Filtra por `peerId` o `linkedPeerId` y actualiza `peerOnline` |
| `PeersOnline` | `peerOnline = deviceIds.contains(linkedPeerId)` |
| `LinkedByPeer` | `persistIncomingLink(from, name)` |

#### `cleanupCall()`

```kotlin
private val cleaningUp = AtomicBoolean(false)

private fun cleanupCall() {
    if (!cleaningUp.compareAndSet(false, true)) return
    webRtc.close()
    audio.restore()
    callState = ENDED
    activePeerId = null
    incomingPeerId = null
    muted = false
    speakerOn = false
    callInProgress = false
    onForegroundStop()
    notificationHelper.cancel(1001); cancel(1002)
    scope.launch {
        delay(500)
        callState = IDLE
        cleaningUp.set(false)
    }
}
```

**`peerOnline` no se resetea**: es estado de presencia, no de llamada. Si se reseteara, la tarjeta mostraría "Sin conexión" tras colgar hasta el siguiente `peer-online`.

#### Threading de `onDisconnected`

```kotlin
override fun onDisconnected() {
    callState = ENDED
    scope.launch { cleanupCall() }   // Main dispatcher
}
```

El callback llega desde el hilo de señalización nativo de WebRTC. Llamar `dispose()` desde ahí produce un `SIGABRT` (bug 3721). **No cambiar esto por una llamada directa "más simple".**

### `AudioSpeakerController`

```kotlin
fun enableSpeakerphone() {
    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    audioManager.isSpeakerphoneOn = true
}
fun disableSpeakerphone() { audioManager.isSpeakerphoneOn = false }
fun restore() { audioManager.mode = AudioManager.MODE_NORMAL; isSpeakerphoneOn = false }
```

- En modo **SUPERVISED**, `enableSpeakerphone()` se llama automáticamente al iniciar la llamada.
- En **SUPERVISOR**, el usuario controla el altavoz.
- `restore()` se llama siempre en `cleanupCall()`, incluso si la llamada falló antes de empezar.

---

## Servicios, notificaciones y FCM

### `CallService`

- `LifecycleService` con `startForeground(1001, notification, FOREGROUND_SERVICE_TYPE_CAMERA or MICROPHONE)`.
- Acción `com.alertamayor.app.action.STOP_CALL` para detenerlo desde la notificación o desde el contenedor.
- En `onDestroy`: `stopForeground(STOP_FOREGROUND_REMOVE)`.

### `GuardService`

- FGS tipo `dataSync` con notificación 1003.
- `PARTIAL_WAKE_LOCK` con tag `AlertaMayor::GuardService`, **no reference-counted**: se adquiere una vez y se libera en `onDestroy`.
- En `onStartCommand` llama `coordinator.ensureConnected()`: si el socket se cayó, lo levanta.
- Se arranca en **ambos modos**, también en el teléfono del supervisor, para que la tarjeta de presencia sea fiable.

### `FcmService`

```kotlin
override fun onNewToken(token: String) {
    scope.launch {
        settings.saveFcmToken(token)
        coordinator.registerFcmToken(token)
    }
}

override fun onMessageReceived(message: RemoteMessage) {
    val data = message.data
    if (data["type"] != "call") return
    val fromDeviceId = data["fromDeviceId"] ?: return
    val fromName = data["fromName"] ?: "Familiar"
    notificationHelper.showIncomingCall(fromName)
    coordinator.onFcmIncomingCall(fromDeviceId, fromName)
}
```

**Contrato del push:**

| Clave | Obligatoria | Ejemplo |
|---|---|---|
| `type` | Sí (`"call"`) | `call` |
| `fromDeviceId` | Sí | UUID del emisor |
| `fromName` | No | `Mamá` |

El mensaje **no** lleva bloque `notification`. Si lo llevara, Android lo pintaría y `onMessageReceived` no se ejecutaría con la app en background.

### `NotificationHelper`

| ID | Canal | Importancia | Contenido |
|---|---|---|---|
| 1001 | `calls` | HIGH | Llamada en curso |
| 1002 | `calls` | HIGH | Llamada entrante (alarma, vibración, 30 s, full screen) |
| 1003 | `guard` | LOW | En guardia |

Detalles:

- `setTimeoutAfter(30_000)` en la entrante.
- `setFullScreenIntent(pendingIntent, true)`.
- `setCategory(CATEGORY_CALL)`, `setPriority(PRIORITY_MAX)`.
- Sin `addAction`: las acciones viven en el overlay de Compose.
- `PendingIntent` con `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`.

---

## Capa de UI

### Navegación

`NavGraph` decide el `startDestination`:

```kotlin
val onboardingDone by settings.onboardingDone.collectAsStateWithLifecycle(initialValue = null)
if (onboardingDone == null) return            // espera a DataStore
val start = if (onboardingDone == true) homeFor(mode) else "onboarding"
```

Rutas y argumentos:

```kotlin
composable("call/{role}/{peerId}") { backStackEntry ->
    val role = backStackEntry.arguments?.getString("role")
    val peerId = backStackEntry.arguments?.getString("peerId")
    ...
}
```

### Navegación automática a la llamada

```kotlin
LaunchedEffect(callState, activePeerId, incomingPeerId) {
    if (callState in setOf(RINGING, CONNECTING, ACTIVE)) {
        val peer = activePeerId ?: incomingPeerId ?: return@LaunchedEffect
        navController.navigate("call/$role/$peer") { launchSingleTop = true }
    }
}
```

`launchSingleTop` evita apilar dos pantallas de llamada si el estado oscila.

### ViewModels

| ViewModel | Depende de | Expone |
|---|---|---|
| `OnboardingViewModel` | Settings | `selectMode(mode)` |
| `SupervisedHomeViewModel` | Settings, Coordinator, Connectivity | `deviceId`, `name`, `isLinked`, `connected`, `qrBitmap`, `callLinkedPeer()` |
| `SupervisorHomeViewModel` | Settings, Coordinator | `peerId`, `peerName`, `peerOnline`, `linkPeer()`, `unlinkPeer()`, `callLinkedPeer()` |
| `CallViewModel` | Coordinator | `callState`, `muted`, `speakerOn`, `peerName`, `elapsed`, `toggleMute()`, … |
| `SettingsViewModel` | Settings, Coordinator | Estado de todos los campos + `save*()` |

`AppViewModelFactory` es un `ViewModelProvider.Factory` con un `when (modelClass)`:

```kotlin
override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
    modelClass.isAssignableFrom(SupervisedHomeViewModel::class.java) ->
        SupervisedHomeViewModel(container.settingsRepository, container.callCoordinator, container.connectivityObserver) as T
    ...
    else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
}
```

Si añades un ViewModel, **añade su rama** o el fallo será en runtime, no en compilación.

### Estado en Compose

- `collectAsStateWithLifecycle()` en todas las pantallas.
- `remember` para estado efímero (diálogos, posición del vídeo local).
- Sin `rememberSaveable` para el nombre del peer: se persiste en DataStore.
- El QR se genera en `Dispatchers.Default` y se expone como `ImageBitmap`.

### Pantalla supervisada: detalles

```kotlin
BackHandler(enabled = true) { /* bloqueado a propósito */ }
```

La esfera usa `Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = ...) }` con `HapticFeedbackType.LongPress`. El rebote es una `rememberInfiniteTransition` de 1600 ms que anima escala 1.0 → 0.96 → 1.0.

El QR se dibuja con `QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)` y `Bitmap.createBitmap(pixels, 512, 512, RGB_565)`.

### Escáner QR

- `PreviewView` + `ImageAnalysis` con `STRATEGY_KEEP_ONLY_LATEST`.
- `BarcodeScanning.getClient()` con formato `QR_CODE`.
- Flag `AtomicBoolean processed` para no decodificar dos veces.
- Executor de un solo hilo, cerrado en `DisposableEffect`.
- Al detectar: `QrLinkCodec.decode(raw)`; si es válido, callback al `NavGraph` y `popBackStack`.

### Llamada: layout

```
Box(fillMaxSize)
 ├── AndroidView(remoteView, fillMaxSize)
 ├── Box(Modifier.offset(drag).size(140.dp)) { AndroidView(localView) }
 ├── Column(top) { estado + timer }
 └── Row(bottom) { mute, speaker, switch, hangup }
```

El arrastre se implementa con `detectDragGestures` y un `IntOffset` en `remember`, sin límites: la miniatura puede salir de pantalla, lo cual es aceptable y simple. Si se quiere acotar, hay que restar el tamaño del contenedor.

---

## Flujos completos

### Llamada saliente

```
UI.tap
 └─ VM.callLinkedPeer()
     └─ Coordinator.startOutgoingCall(peerId, name)
         ├─ if (callInProgress) return
         ├─ callState = DIALING; callInProgress = true
         ├─ onForegroundStart() → CallService.start()
         └─ signaling.sendCall(peerId, name)

[llega call-accepted]
 └─ startWebRtcAsInitiator()
     ├─ callState = CONNECTING
     ├─ webRtc.prepareCapture()
     ├─ if (mode == SUPERVISED) audio.enableSpeakerphone()
     ├─ webRtc.startPeerConnection(initiator = true, iceServers)
     └─ webRtc.createOffer { signaling.sendOffer(peerId, it) }

[llega answer] → webRtc.setRemoteAnswer
[llegan ICE]  → webRtc.addIceCandidate
[ICE conectado] → callState = ACTIVE
```

### Llamada entrante (supervisor)

```
[call]
 └─ handleEvent(IncomingCall)
     ├─ incomingPeerId = from; callState = RINGING
     ├─ onIncomingCall(fromName) → notificación 1002
     └─ UI: overlay Aceptar / Rechazar

[Aceptar] → coordinator.acceptIncomingCall()
             ├─ signaling.sendCallAccepted(from)
             ├─ callState = CONNECTING
             └─ (espera offer)

[offer] → startWebRtcAsReceiver()
           ├─ prepareCapture(); startPeerConnection(false)
           ├─ setRemoteOffer(sdp)
           └─ createAnswer → sendAnswer

[Rechazar] → signaling.sendCallRejected(from); callState = ENDED; cleanupCall()
```

### Llamada entrante (supervisado, auto-respuesta)

```
[call] → RINGING
       → si mode == SUPERVISED: acceptIncomingCall() inmediato
       → CONNECTING → offer → answer → ACTIVE
```

No hay UI de aceptación. La notificación 1002 también se muestra, pero la llamada ya está en curso; sirve como indicador visual.

### Vinculación

```
Supervisado: deviceId → QrLinkCodec.encode → QR en pantalla
Supervisor:  cámara → ML Kit → decode → diálogo nombre
             → settings.saveLinkedPeer(id, name)
             → signaling.sendLink(id, deviceName)
Supervisado: [link] → persistIncomingLink(from, name)
             → settings.saveLinkedPeer(from, name) si no había
             → signaling.sendLinkAck(from)
```

`persistIncomingLink` **no sobrescribe** un vínculo existente con otro distinto: si ya hay un `linked_peer_id` diferente, ignora el mensaje. Esto evita que un tercero con el QR robe el vínculo.

### Reconexión

```
socket.onFailure
 └─ connected = false; connectionStatus = DISCONNECTED
 └─ scope.launch { delay(3000); connect() }
     └─ onOpen: send register + fcmToken
         └─ servidor responde registered + peers-online + pendingCalls
```

Si la app estaba en llamada, el estado queda en `ENDED` tras `onDisconnected`; no hay reconexión de medios automática. Es una limitación conocida.

---

## Concurrencia y threading

| Contexto | Qué corre ahí | Regla |
|---|---|---|
| **Main** | UI, `CallCoordinator.scope`, ViewModels | Nada bloqueante |
| **OkHttp** | `SignalingClient` callbacks | Solo emite al `Channel`; no toca UI |
| **WebRTC signaling thread** | `onIceCandidate`, `onConnectionChange` | **Nunca** `dispose()`; despachar a Main |
| **WebRTC capture thread** | Captura de cámara | No tocar |
| **Dispatchers.Default** | Generación de QR | Devuelve `ImageBitmap` |
| **Dispatchers.IO** | DataStore (implícito) | Usar `suspend` |

### Reglas de oro

1. `cleanupCall()` solo desde Main.
2. `WebRtcManager.close()` es idempotente y sincronizado.
3. No crear un `PeerConnection` nuevo sin cerrar el anterior.
4. No llamar `setRemoteDescription` antes de `createPeerConnection`.
5. Los `StateFlow` se actualizan desde Main; los eventos del socket se reemiten en el scope del coordinador.

---

## Build y variantes

### `app/build.gradle.kts` (extracto relevante)

```kotlin
android {
    namespace = "com.alertamayor.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alertamayor.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.3.0"
    }

    buildFeatures { compose = true; buildConfig = true }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // network_security_config de debug permite ws:// en LAN
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // sin signingConfig → APK sin firmar
        }
    }
}
```

### `BuildConfig.SIGNALING_SERVER_URL`

```kotlin
val signalingServerUrl = (project.findProperty("signalingServerUrl") as String?)
    ?: "wss://TU-DOMINIO.com"
buildConfigField("String", "SIGNALING_SERVER_URL", "\"$signalingServerUrl\"")
```

`project.findProperty` lee `-P` y `gradle.properties`, **no** `local.properties`. El comentario del archivo sugiere lo contrario; es una imprecisión conocida. La URL guardada en DataStore tiene prioridad en runtime.

### Network security config

| Variante | Archivo | Cleartext |
|---|---|---|
| main | `src/main/res/xml/network_security_config.xml` | `false` |
| debug | `src/debug/res/xml/network_security_config.xml` | `true` solo para hosts declarados |
| release | `src/release/res/xml/network_security_config.xml` | `false` |

Para probar en LAN con una IP nueva hay que **añadirla al XML de debug y recompilar**. No se puede cambiar en runtime.

### `google-services.json`

Se coloca en `app/`. El plugin lo lee en `processDebugGoogleServices` y genera recursos de Firebase. Sin él:

```
File google-services.json is missing.
```

No se versiona. Cada desarrollador usa su propio proyecto de Firebase con el package `com.alertamayor.app`.

---

## Firma y release

El repositorio **no** incluye keystore ni bloque `signingConfigs`. Para producir un APK instalable fuera de debug:

```kotlin
// app/build.gradle.kts
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    signingConfigs {
        create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile") ?: "release.jks")
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.getByName("release") }
    }
}
```

Y `keystore.properties` fuera de git:

```properties
storeFile=../alertamayor-release.jks
storePassword=…
keyAlias=alertamayor
keyPassword=…
```

### Checklist de release

- [ ] `versionCode` incrementado.
- [ ] `versionName` coherente.
- [ ] `google-services.json` del proyecto correcto.
- [ ] URL por defecto con `wss://`.
- [ ] `network_security_config` de release sin excepciones.
- [ ] Keystore propio y respaldado.
- [ ] `isMinifyEnabled = true` (opcional, probar R8).
- [ ] Prueba de humo en dos dispositivos.
- [ ] No incluir `serviceAccountKey.json` en el APK (no ocurre: vive en el servidor).

---

## Guía de extensión

### Añadir una pantalla

1. Crea `ui/miPantalla/MiPantallaScreen.kt` con un composable que reciba estado y callbacks.
2. Crea `MiPantallaViewModel` si necesita lógica.
3. Añade su rama en `AppViewModelFactory`.
4. Declara la ruta en `NavGraph`:

```kotlin
composable("miPantalla") {
    val vm: MiPantallaViewModel = viewModel(factory = AppViewModelFactory(container))
    MiPantallaScreen(vm, onBack = { navController.popBackStack() })
}
```

5. Navega con `navController.navigate("miPantalla")`.

### Añadir un tipo de mensaje al protocolo

1. Añade el `type` en el servidor (`server/index.js`) y su enrutado.
2. Añade el evento en `SignalingEvents.kt`.
3. Parsea el `payload` en `SignalingClient` (o en el coordinador) y emite el evento.
4. Maneja el evento en `CallCoordinator.handleEvent`.
5. Documenta el mensaje en este archivo y en el README.

**Compatibilidad:** el servidor reenvía cualquier `type` con `to`, así que un cliente viejo no rompe a uno nuevo salvo que el nuevo dependa de una respuesta que el viejo no envía. Mantén los mensajes existentes y añade, no modifiques.

### Añadir un permiso

1. Decláralo en `AndroidManifest.xml`.
2. Si es runtime, pídelo con el `ActivityResultContracts.RequestPermission` correspondiente en la pantalla que lo necesita.
3. Si es para un FGS, declara también el tipo (`FOREGROUND_SERVICE_*`) y añádelo al `startForeground`.
4. Actualiza la tabla de permisos del README y del datasheet.

### Añadir un idioma

1. Crea `app/src/main/res/values-<código>/strings.xml` con todas las claves.
2. Añade el código a `res/xml/locales_config.xml`.
3. Añade la opción al selector de `SettingsScreen`.
4. Traduce también los `contentDescription`.

No traduzcas `app_name`: es marca.

### Cambiar de servidor o añadir autenticación

- La URL se cambia en Configuración o en `BuildConfig`.
- Para autenticación: añade un campo `token` a `register`, valídalo en el servidor y guárdalo en DataStore (idealmente cifrado). Rechaza los mensajes de clientes no autenticados.
- Para TURN efímero: el servidor debería emitir credenciales temporales (`turnserver --use-auth-secret`) y la app pedirlas al registrarse.

### Añadir tests

No hay infraestructura de tests. Punto de partida razonable:

```kotlin
// app/src/test/java/.../QrLinkCodecTest.kt
@Test fun `round trip`() {
    val id = UUID.randomUUID().toString()
    assertEquals(id, QrLinkCodec.decode(QrLinkCodec.encode(id)))
}

@Test fun `rejects garbage`() {
    assertNull(QrLinkCodec.decode("https://example.com"))
}
```

Para los guards del coordinador hace falta abstraer el reloj y el dispatcher; hoy usa `scope.launch { delay(…) }` directo.

### Añadir historial de llamadas

1. Añade una entidad (Room o un JSON en DataStore).
2. Registra en `startOutgoingCall`, `acceptIncomingCall` y `cleanupCall`.
3. Expón un `Flow<List<CallRecord>>` y una pantalla.
4. Define retención y borrado.

Cuidado con la privacidad: el historial revela patrones de uso. Debe poder desactivarse.

---

## Depuración

### Logs útiles

```bash
adb logcat --pid=$(adb shell pidof -s com.alertamayor.app)
adb logcat | grep -Ei 'AlertaMayor|WebRTC|Signaling|FATAL|AndroidRuntime'
adb logcat -c
```

### Puntos de ruptura recomendados

| Síntoma | Dónde mirar |
|---|---|
| No conecta | `SignalingClient.onOpen/onFailure`, `BuildConfig.SIGNALING_SERVER_URL` |
| No suena | `FcmService.onMessageReceived`, `NotificationHelper.showIncomingCall` |
| Se queda en CONNECTING | `WebRtcManager.createOffer`, `setRemoteAnswer`, ICE candidates |
| Se cierra al colgar | `cleanupCall`, `close()` (buscar doble dispose) |
| Estado raro de la UI | `CallCoordinator` StateFlows, `LaunchedEffect` del NavGraph |
| QR no lee | `QrScannerScreen` (flag `processed`, formato) |

### Inspeccionar el servidor

```bash
# salud
curl -s http://127.0.0.1:3000/health

# logs en systemd
journalctl -u alertamayor-signaling -f

# conexiones activas
ss -tnp | grep 3000
```

### Probar sin cámara

Puedes generar el QR con cualquier librería y cargar el UUID a mano en DataStore solo en debug. No hay una pantalla de "pegar ID" en producción, a propósito.

---

## Deuda técnica y riesgos

| # | Deuda | Impacto | Mitigación sugerida |
|---|---|---|---|
| 1 | Sin autenticación en el servidor | Alto en Internet | Token por instalación |
| 2 | QR como credencial permanente | Medio | Expiración + rotación |
| 3 | Secretos sin cifrar en DataStore | Medio | Keystore |
| 4 | Sin tests | Medio | Empezar por `QrLinkCodec` y guards |
| 5 | `isMinifyEnabled = false` | Bajo | Probar R8 |
| 6 | `versionCode 2` vs `1.3.0` | Bajo | Unificar esquema |
| 7 | Strings sin usar | Bajo | Limpieza |
| 8 | Poller de red cada 4 s | Bajo | Migrar a NetworkRequest |
| 9 | Sin reconexión de medios | Medio | Reintentar ICE restart |
| 10 | Un solo peer | Medio | Modelo de datos con lista |
| 11 | `findProperty` y `local.properties` | Bajo | Corregir comentario o cargar el archivo |
| 12 | Sin `signingConfigs` | Bajo | Documentado en release |

### Riesgos de regresión

- **WebRTC**: cualquier cambio en el orden de `dispose()` puede producir `SIGABRT`. Probar colgar 20 veces seguidas.
- **Modo**: cambiar `setMode()` sin actualizar el coordinador deja el auto-answer activo en Supervisor.
- **FCM**: añadir un bloque `notification` al push rompe la lógica en background. Mantener data-only.
- **Navegación**: quitar `launchSingleTop` puede apilar pantallas de llamada y romper el `popBackStack`.

---

## Referencia rápida

### Archivos por tamaño (aproximado)

| Archivo | Líneas | Rol |
|---|---|---|
| `webrtc/CallCoordinator.kt` | 473 | Orquestador |
| `ui/supervised/SupervisedHomeScreen.kt` | 405 | Botón SOS |
| `webrtc/WebRtcManager.kt` | 357 | Stack nativo |
| `ui/call/CallScreen.kt` | 266 | UI de llamada |
| `data/signaling/SignalingClient.kt` | 265 | WebSocket |
| `ui/settings/SettingsScreen.kt` | 242 | Ajustes |
| `ui/navigation/NavGraph.kt` | 205 | Rutas |
| `ui/supervisor/SupervisorHomeScreen.kt` | 185 | Tarjeta |
| `ui/qr/QrScannerScreen.kt` | 164 | Cámara + ML Kit |
| `data/local/SettingsRepository.kt` | 122 | DataStore |
| `data/network/ConnectivityObserver.kt` | 121 | Red |
| `MainActivity.kt` | 118 | Entrada |

### API de `CallCoordinator`

```kotlin
fun connect()
fun ensureConnected()
fun setMode(mode: AppMode)
fun setServerUrl(url: String)
fun setTurnConfig(url: String?, user: String?, pass: String?)
fun updateLinkedPeer(peerId: String?, peerName: String?)
fun sendLinkRequest(peerId: String, name: String)
fun sendLinkAck(peerId: String)
fun registerFcmToken(token: String)
fun persistIncomingLink(from: String, name: String)

fun startOutgoingCall(peerId: String, peerName: String)
fun acceptIncomingCall(peerId: String)
fun rejectIncomingCall(peerId: String)
fun hangUp()

fun toggleMute(); fun toggleSpeaker(); fun switchCamera()
fun onFcmIncomingCall(fromDeviceId: String, fromName: String)
```

### API de `WebRtcManager`

```kotlin
fun prepareCapture()
fun startPeerConnection(initiator: Boolean, iceServers: List<PeerConnection.IceServer>)
fun createOffer(onSdp: (String) -> Unit)
fun setRemoteOffer(sdp: String, onAnswer: (String) -> Unit)
fun setRemoteAnswer(sdp: String)
fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int)
fun setAudioEnabled(enabled: Boolean)
fun setVideoEnabled(enabled: Boolean)
fun switchCamera()
fun close()
```

### Constantes y valores

| Constante | Valor |
|---|---|
| Esquema QR | `alertamayor:link` |
| Canal de notificaciones | `calls`, `guard` |
| IDs de notificación | 1001, 1002, 1003 |
| Acción de parada | `com.alertamayor.app.action.STOP_CALL` |
| Tag de wakelock | `AlertaMayor::GuardService` |
| DataStore | `alertamayor_settings` |
| STUN | `stun:stun.l.google.com:19302` |
| Captura | 1280×720@30 |

### Convenciones del código

- Sin `!!` salvo en callbacks donde el tipo es garantizado por la API.
- `StateFlow` para estado, `Flow` para eventos.
- Nombres de eventos en pasado (`Registered`, `CallAccepted`).
- Comentarios solo para decisiones no obvias ("por qué", no "qué").
- Un archivo por clase pública.
- Español en documentación y comentarios; inglés en identificadores.

---

## Referencias técnicas

- [README](../README.md) — visión general y compilación.
- [datasheet.md](datasheet.md) — manual de usuario.
- [vps-deployment.md](vps-deployment.md) — despliegue.
- [REFERENCES.md](REFERENCES.md) — estándares y documentación.
- [BITACORA.md](BITACORA.md) — decisiones y lecciones.

---

<div align="center">

**Alerta Mayor 1.3.0** · Documentación para desarrolladores

*El código dice qué hace; este documento intenta decir por qué.*

</div>
