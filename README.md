<div align="center">

# 🆘 Alerta Mayor

### Teleasistencia para adultos mayores con un solo botón

**App Android nativa que convierte dos teléfonos en un canal de videollamada de emergencia: el adulto mayor pulsa un botón rojo gigante y su familiar lo ve y lo escucha por el altavoz, sin tocar nada más.**

<br/>

![Plataforma](https://img.shields.io/badge/plataforma-Android%207.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![WebRTC](https://img.shields.io/badge/WebRTC-P2P-333333?style=flat-square&logo=webrtc&logoColor=white)
![Node.js](https://img.shields.io/badge/Node.js-%E2%89%A518-339933?style=flat-square&logo=nodedotjs&logoColor=white)
![Versión](https://img.shields.io/badge/versi%C3%B3n-1.3.0-D32F2F?style=flat-square)
![Licencia](https://img.shields.io/badge/licencia-MIT-blue?style=flat-square)
![Idiomas](https://img.shields.io/badge/idiomas-ES%20%7C%20EN-yellow?style=flat-square)

<br/>

**[Qué es](#qué-es-alerta-mayor)** ·
**[Cómo funciona](#principio-de-funcionamiento)** ·
**[Modos](#los-dos-modos-de-uso)** ·
**[Pantallas](#pantallas-y-navegación)** ·
**[Compilar](#compilación-e-instalación)** ·
**[Servidor](#servidor-de-señalización)** ·
**[Problemas](#solución-de-problemas)**

</div>

---

## Tabla de contenidos

1. [Qué es Alerta Mayor](#qué-es-alerta-mayor) · [El problema](#el-problema-que-resuelve) · [Principio de funcionamiento](#principio-de-funcionamiento)
2. [Los dos modos](#los-dos-modos-de-uso) · [Pantallas](#pantallas-y-navegación) · [Flujo QR](#flujo-de-vinculación-por-qr)
3. [Señalización](#señalización-en-tiempo-real) · [WebRTC](#videollamada-webrtc) · [FCM](#notificaciones-push-con-fcm)
4. [Permisos](#permisos) · [Arquitectura](#arquitectura-mvvm) · [Estructura](#estructura-del-proyecto)
5. [Requisitos](#requisitos) · [Compilación e instalación](#compilación-e-instalación) · [Servidor](#servidor-de-señalización)
6. [Ejemplos de uso](#ejemplos-de-uso-realistas) · [Seguridad](#seguridad-y-privacidad) · [Rendimiento](#rendimiento-y-consumo)
7. [Accesibilidad](#accesibilidad-idioma-y-tema) · [Problemas](#solución-de-problemas) · [Limitaciones](#limitaciones-conocidas)
8. [Cómo se construyó](#cómo-se-construyó-este-proyecto) · [Roadmap](#roadmap) · [Documentación](#documentación-adicional)
9. [Referencias](#referencias) · [Licencia](#licencia)

---
## Qué es Alerta Mayor

**Alerta Mayor** es una aplicación Android de **teleasistencia doméstica** para personas mayores que viven solas o pasan muchas horas solas, y para el familiar o cuidador que responde por ellas.

En el teléfono del adulto mayor la app muestra **una esfera roja de 200 dp con el texto "SOS"** en el centro de la pantalla. Al pulsarla, el teléfono del familiar suena como una alarma; cuando atiende, se abre una **videollamada punto a punto** con el **altavoz ya activado** y la **cámara encendida** en ambos lados. El adulto mayor no acepta nada, no busca contactos, no recuerda contraseñas: **un toque, una llamada**. En la dirección inversa, el familiar puede llamar cuando quiera y la app del mayor **responde sola en altavoz**, de modo que solo tiene que hablar.

La misma aplicación contiene los dos roles. En el primer arranque se elige uno y queda persistido; puede cambiarse después desde Configuración. Un teléfono hace de **Supervisado** (el adulto mayor) y otro de **Supervisor** (el familiar), y se vinculan escaneando un **código QR**: sin cuentas, sin registro y sin contraseñas.

| Dato | Valor |
|---|---|
| Nombre | **Alerta Mayor** (marca; no se traduce) |
| Versión documentada | **1.3.0** (`versionCode 2`) |
| Package / applicationId | `com.alertamayor.app` |
| Lenguaje / UI | Kotlin 2.0.21 · Jetpack Compose + Material 3 |
| minSdk / targetSdk / compileSdk | 24 (Android 7.0) / 35 / 35 |
| Idiomas | Español (por defecto) e Inglés |
| Medios | WebRTC P2P (audio + vídeo) |
| Señales | WebSocket JSON (Node.js + `ws`) |
| Backend propio | `server/` (Node.js ≥ 18) + Firebase Cloud Messaging |

> **Nota de honestidad.** Es un proyecto real y funcional, pero **no es un producto certificado de teleasistencia**. No sustituye a una central de alarmas ni a un servicio de emergencias. Ver [Limitaciones](#limitaciones-conocidas) y [Seguridad](#seguridad-y-privacidad).

---

## El problema que resuelve

Los dispositivos de teleasistencia tradicionales (el colgante con botón, la central con altavoz) resuelven un caso: pulsar y que alguien llame. Pero arrastran tres fricciones: **costo y contrato**, **unidireccionalidad** (audio, no vídeo) y **dependencia de un operador** que la familia no controla.

Al mismo tiempo, la mayoría de las personas mayores ya tienen un smartphone —o pueden tener uno de gama baja reutilizado— con cámara, micrófono, altavoz, Wi-Fi y datos. Lo que ese teléfono no tiene es una interfaz que una persona con visión reducida, pulso tembloroso o poca familiaridad tecnológica pueda usar **bajo estrés**.

La hipótesis de diseño del proyecto:

> Si el único gesto necesario es **tocar una superficie grande y roja**, y el resto (marcar, llamar, aceptar, activar altavoz, encender cámara) lo hace la app, entonces una persona mayor puede pedir ayuda audiovisual sin aprender nada nuevo.

De ahí salen las decisiones que atraviesan todo el código:

- **Cero navegación** en la pantalla del mayor: un botón, un indicador de red y un engranaje pequeño. Nada más.
- **Cero aceptación** en el lado supervisado: la llamada entrante se contesta sola y en altavoz.
- **Cero cuentas**: el vínculo es un UUID intercambiado por QR.
- **Cero dependencia de la pantalla**: servicios en primer plano, auto-arranque al encender y exención de batería para que el teléfono siga «en guardia» bloqueado.

**Qué NO es:** no llama al 112/911 ni avisa a una central; no detecta caídas ni monitoriza constantes; no tiene chat ni historial; no es multiusuario (un supervisado ↔ un supervisor).

---

## Principio de funcionamiento

```
                 ┌──────────────────────────────────────────────┐
                 │        SERVIDOR DE SEÑALIZACIÓN (Node.js)    │
                 │        WebSocket · JSON · puerto 3000        │
                 │  register · call · offer · answer · ICE      │
                 │  link · peers-online · pendingCalls · FCM    │
                 └───────────────▲───────────────▲──────────────┘
             WebSocket (señales) │               │ WebSocket (señales)
        ┌────────────────────────┴───┐     ┌─────┴────────────────────────┐
        │  SUPERVISADO (adulto mayor)│     │  SUPERVISOR (familiar)       │
        │  • Botón SOS rojo          │     │  • Escáner QR                │
        │  • QR de vinculación       │     │  • Tarjeta del dispositivo   │
        │  • Auto-respuesta + altavoz│     │  • Llamar / Aceptar / Rechazar│
        └────────────┬───────────────┘     └──────────────┬───────────────┘
                     └──────── ═══ WebRTC P2P ═══ ────────┘
                          audio + vídeo (no pasa por el servidor)
```

| Plano | Qué transporta | Tecnología | ¿Pasa por el servidor? |
|---|---|---|---|
| **Vinculación** | Un UUID (`deviceId`) y un nombre | QR → cámara → DataStore | No (el QR es local) |
| **Señalización** | Quién llama, SDP y candidatos ICE | WebSocket JSON | **Sí**, solo metadatos de sesión |
| **Medios** | Audio y vídeo | WebRTC (SRTP/DTLS) | **No**, punto a punto (o relay TURN) |

Esa separación es la clave de la privacidad: el servidor **nunca ve ni reenvía** el audio o el vídeo; solo ayuda a que los teléfonos se encuentren y negocien.

**1. Identidad sin cuentas.** En la primera ejecución se genera un **UUID v4** guardado en `DataStore`. Ese UUID es la identidad ante el servidor; no hay login ni email. El servidor mantiene un `Map<deviceId, WebSocket>` y enruta por `to`. Restablecer el vínculo **regenera el UUID**.

**2. Vinculación por QR.** El supervisado muestra `alertamayor:link?deviceId=<UUID>`; el supervisor lo escanea, le pone nombre y guarda, y envía un mensaje `link` con su ID y nombre para que el mayor sepa a quién llamar.

**3. Señalización.** `call` se reenvía si el destino está conectado; si no, el servidor guarda la llamada en `pendingCalls` y manda un **push FCM** para despertarlo. Al aceptar, `offer`/`answer` y candidatos ICE en *trickle*.

**4. Medios.** Stack `com.infobip:google-webrtc:1.0.47617t`, captura **1280×720 a 30 fps**, cámara **frontal** preferida. Audio en `MODE_IN_COMMUNICATION` con altavoz **automático** en modo Supervisado. ICE = STUN de Google + **TURN opcional**.

---
## Los dos modos de uso

La app es la misma; cambia el rol y, con él, toda la interfaz. El modo se elige en el primer arranque, se guarda en `DataStore` y puede cambiarse en caliente desde Configuración (el coordinador se actualiza sin reiniciar).

| | **Modo Supervisado** | **Modo Supervisor** |
|---|---|---|
| **Quién lo usa** | El adulto mayor | El familiar o cuidador |
| **Pantalla principal** | Esfera roja + "SOS" amarillo | Escanear QR + tarjeta del dispositivo |
| **Acción típica** | Tocar el botón | Escanear el QR una vez, luego llamar |
| **Llamada entrante** | **Auto-respuesta** + altavoz + cámara | **Overlay Aceptar / Rechazar** + notificación |
| **Vinculación** | **Muestra** el QR | **Escanea** el QR |
| **Botón Back** | Bloqueado (`BackHandler {}`) | Normal |

### Modo Supervisado (adulto mayor)

- Fondo **negro absoluto** para no deslumbrar de noche y maximizar el contraste.
- Esfera de **200 dp** con gradiente radial, sombra y **rebote** de 1600 ms: se percibe como un objeto físico pulsable.
- **"SOS"** en amarillo (`#FFC107`) a **120 sp** en negrita y **feedback háptico** al pulsar.
- **Indicador de red** arriba a la izquierda: Wi-Fi, datos o sin conexión.
- Sin internet la esfera se vuelve **gris con barra diagonal roja** y no inicia una llamada condenada a fallar.
- Sin vínculo se muestra el **QR** con la instrucción "Muestra este código QR a tu familiar".
- **Back bloqueado** y **pantalla siempre encendida** (`FLAG_KEEP_SCREEN_ON`).

### Modo Supervisor (familiar)

- Fondo negro coherente; botón **"Escanear QR para vincular"** de 64 dp.
- Tarjeta del dispositivo con nombre, estado **En línea / Sin conexión** y botón **Llamar**.
- Al recibir llamada: notificación de alta prioridad con **sonido de alarma**, vibración, `fullScreenIntent` y **overlay Aceptar / Rechazar**. El banner no tiene acciones: la decisión se toma en el overlay, evitando toques accidentales.
- El estado "En línea" usa `peer-online`, `peer-offline` y `peers-online` (la lista completa al registrarse evita falsos "Sin conexión").

---
## Pantallas y navegación

Navegación con **Navigation Compose** y seis rutas:

```
onboarding ──► supervisedHome ──► call/{role}/{peerId}
     │                └─────────► settings
     └────────► supervisorHome ──► qrScanner
                      └─────────► call/{role}/{peerId}
```

| Ruta | Pantalla | Contenido | Salida |
|---|---|---|---|
| `onboarding` | `ModeSelectionScreen` | "¿Cómo usarás la app?" + dos tarjetas de 120 dp (Supervisado rojo, Supervisor azul) | Guarda modo + `onboardingDone` y navega con `popUpTo(inclusive)` |
| `supervisedHome` | `SupervisedHomeScreen` | Esfera SOS o QR + indicador de red + engranaje | Botón → `call/supervised/<peerId>`; engranaje → `settings` |
| `supervisorHome` | `SupervisorHomeScreen` | Escanear QR + tarjeta + Llamar | Escanear → `qrScanner`; Llamar → `call/supervisor/<peerId>` |
| `qrScanner` | `QrScannerScreen` | Preview CameraX + hint + spinner | Al leer un QR válido, vuelve y abre el diálogo de nombre |
| `call/{role}/{peerId}` | `CallScreen` | Vídeo remoto a pantalla completa, local arrastrable (140 dp), estado/temporizador, controles | Colgar → home del rol |
| `settings` | `SettingsScreen` | Idioma, modo, tema, URL del servidor, TURN, vínculo y reset | Flecha atrás |

Detalles que importan:

- **`launchMode="singleTask"`**: una notificación con la app abierta no crea otra instancia; el intent se procesa en `onNewIntent`.
- **Navegación automática a la llamada**: las dos homes observan `callState`, `activePeerId` e `incomingPeerId`; al entrar en `RINGING`, `CONNECTING` o `ACTIVE` navegan solas. En modo supervisado, el mayor ni siquiera ve un botón de aceptar.
- **Salida limpia**: `popBackStack(homeRoute, false)` y, si falla, `navigate(homeRoute)` con `popUpTo(0)`.
- **Diálogo de nombre**: el escaneo no persiste directamente; abre un `AlertDialog` con el UUID precargado para escribir "Mamá" o "Abuelo".

---
## Flujo de vinculación por QR

Es el único momento en que los dos teléfonos necesitan presentarse. Está pensado para hacerse **una vez, en cinco minutos, con las dos personas juntas**.

1. El supervisado genera un **UUID v4** (si no existía) y lo guarda en `DataStore`.
2. Codifica `alertamayor:link?deviceId=<UUID>` y dibuja un **QR de 512×512** con ZXing en pantalla.
3. El supervisor abre el escáner: **CameraX** + **ML Kit** decodifican el código y `QrLinkCodec.decode()` valida el UUID.
4. Se abre un diálogo con el UUID precargado; el familiar escribe un nombre ("Mamá") y guarda.
5. El supervisor envía `link { name }`; el supervisado hace `persistIncomingLink()` y guarda el par. Deja de mostrar el QR y aparece la esfera roja.
6. El supervisado responde `link-ack` (confirmación; el supervisor no hace nada con ella).

| Campo | Valor |
|---|---|
| Esquema / parámetro | `alertamayor:link` / `deviceId` (UUID v4) |
| Ejemplo | `alertamayor:link?deviceId=8f14e45f-ea4c-4b3d-9c1a-0f2b7d6e5a11` |
| Formato legado | `{"deviceId":"8f14e45f-..."}` |
| QR | 512 × 512 px, negro sobre blanco (`RGB_565`) |
| Generación / lectura | ZXing `core` 3.5.3 / ML Kit `barcode-scanning` 17.3.0 (**bundled**) |

**Por qué ML Kit *bundled*:** el escaneo funciona **sin Google Play Services y sin conexión**, para que la vinculación funcione también en un Huawei sin GMS. El coste es un APK más grande.

**Cuándo repetirla:** al pulsar **Restablecer vínculo** (borra el par, **regenera el UUID** y el QR anterior deja de valer), al reinstalar borrando datos, o al cambiar de teléfono. Cambiar de modo **no** borra el vínculo.

> ⚠️ El QR contiene el `deviceId`, la única credencial del dispositivo. **Quien lo fotografie puede llamar a ese teléfono.**

---
## Señalización en tiempo real

El servidor es un proceso **Node.js + `ws`** de unas 230 líneas, sin base de datos: su estado vive en memoria y se pierde al reiniciar. Es intencional, pero **un reinicio corta las llamadas en curso**.

Los mensajes son JSON con `type`, `from`, `to` y `payload`:

| `type` | Dirección | Payload | Propósito |
|---|---|---|---|
| `register` / `register-fcm` | cliente → servidor | `{ deviceId, fcmToken? }` / `{ token }` | Registra socket y token FCM |
| `registered` | servidor → cliente | `"<deviceId>"` o `"fcm"` | Confirmación |
| `call` | cliente → destino | `{ fromName }` | Solicitud de llamada |
| `call-accepted` / `call-rejected` | destino → emisor | — | Aceptar / rechazar |
| `offer` / `answer` | ambos | `{ sdp }` | Negociación SDP |
| `ice-candidate` | ambos | `{ candidate, sdpMid, sdpMLineIndex }` | Candidato ICE (*trickle*) |
| `end-call` | ambos | — | Colgar |
| `link` / `link-ack` | supervisor ↔ supervisado | `{ name }` / — | Vínculo y confirmación |
| `peers-online` | servidor → cliente | `{ deviceIds: [...] }` | Lista completa al registrarse |
| `peer-online` / `peer-offline` | servidor → todos | `from = deviceId` | Presencia |
| `peer-unavailable` | servidor → emisor | `to` | Destino no registrado |
| `error` | servidor → cliente | texto | Error genérico |

**Estado del servidor:** `clients` (`deviceId → WebSocket`), `fcmTokens` y `pendingCalls`.

1. **Registro**: guarda el socket, cierra una conexión duplicada previa, responde `registered`, difunde `peer-online` y envía `peers-online` con todos los conectados.
2. **Reentrega**: si había una `call` pendiente, la envía al reconectar y la borra.
3. **Enrutamiento**: todo mensaje con `to` se reenvía tal cual, normalizando `from` si faltaba.
4. **Destino ausente**: si es `call` y hay token FCM, guarda en `pendingCalls` y manda push de prioridad `high`; sin token, responde `peer-unavailable`.
5. **Desconexión**: elimina el socket y difunde `peer-offline`. `GET /health` responde `ok`.

### Cliente Android: `SignalingClient`

OkHttp WebSocket con `pingInterval(20 s)` y **reconexión cada 3 s** (con un `Job` que evita duplicados). Los eventos se exponen como `Flow<SignalingEvent>` sobre un `Channel(UNLIMITED)` y el estado como `StateFlow<Boolean>`: la UI nunca toca el socket. Al abrirse, **se re-registra** y **reenvía el token FCM** si ya existe.

### `CallCoordinator`: estado de la llamada

Singleton en `AppContainer`; sobrevive a la rotación y al cierre de pantalla, y es la única fuente de verdad:

```
IDLE ──► DIALING ──► RINGING ──► CONNECTING ──► ACTIVE ──► ENDED
  ▲         │            │            │                        │
  │         │            └──── rechazo / peer-unavailable ─────┤
  └──────────────────────── cleanup ──────────────────────────┘
```

Expone `connectionStatus`, `activePeerId`, `incomingPeerId`, `muted`, `speakerOn`, `peerOnline` y `callInProgress`, todos `StateFlow`.

**Guards de idempotencia** (ver [BITACORA](docs/BITACORA.md)): `cleaningUp: AtomicBoolean` ejecuta `cleanupCall()` una sola vez aunque lleguen `hangUp`, `EndCall`, `CallRejected` y `onDisconnected` a la vez; `callInProgress` evita dobles toques; `closed` + `synchronized` en `WebRtcManager.close()` convierte el segundo `close()` en no-op en vez de un `SIGABRT`.

---
## Videollamada WebRTC

1. El iniciador prepara captura (cámara frontal 720p30 + audio) y crea la `PeerConnection`.
2. Genera un **offer SDP** y lo envía por el WebSocket.
3. El receptor prepara su captura, aplica `setRemoteOffer` y responde con un **answer SDP**.
4. Ambos intercambian **candidatos ICE** en *trickle* hasta que ICE llega a `CONNECTED`.
5. `callState` pasa a `ACTIVE` y el audio/vídeo fluye **directo**, sin pasar por el servidor.

### Configuración de la sesión

| Parámetro | Valor | Motivo |
|---|---|---|
| Semántica SDP | `UNIFIED_PLAN` | Estándar moderno, compatible con `onTrack` |
| Encoder / decoder | `DefaultVideoEncoderFactory(egl, true, true)` / `DefaultVideoDecoderFactory(egl)` | H.264 + VP8/VP9 con aceleración por hardware si existe |
| Captura | 1280 × 720 @ 30 fps | Equilibrio nitidez/ancho de banda para ver una cara |
| Cámara | Frontal preferida, trasera como fallback | La cara es lo importante |
| STUN / TURN | `stun:stun.l.google.com:19302` / opcional | Descubrimiento NAT / redes móviles |
| Audio | `MODE_IN_COMMUNICATION` + altavoz | Manos libres para el mayor |

### Ciclo de vida

- `WebRtcManager` crea **una sola vez** `PeerConnectionFactory`, `EglBase` y los dos `SurfaceViewRenderer`.
- `prepareCapture()` se llama **al iniciar cada llamada**, no antes: crear la cámara sin permiso o sin llamada desperdicia batería y puede fallar.
- `CallService` es un **foreground service** `camera|microphone`: mantiene la llamada viva al cambiar de app o bloquear la pantalla.
- Al colgar: `stopCapture()` → `dispose()` → tracks → fuentes → `SurfaceTextureHelper` → limpiar imágenes. **Ese orden importa.**
- `onDisconnected` **no** llama a `cleanupCall()` directamente: se despacha al hilo principal, porque el callback llega desde el *signaling thread* nativo y `dispose()` desde ahí provoca `SIGABRT` (bug 3721 de WebRTC).

### Controles en llamada

| Control | Efecto | Implementación |
|---|---|---|
| 🎤 Micrófono | Silencia / reactiva audio local | `AudioTrack.setEnabled` |
| 🔊 Altavoz | Alterna altavoz / auricular | `AudioManager.isSpeakerphoneOn` |
| 🔄 Cámara | Frontal ⇄ trasera | `CameraVideoCapturer.switchCamera` |
| 📴 Colgar | Termina y vuelve a la home | `end-call` + `cleanupCall` |
| ⏱️ Temporizador | `mm:ss` desde `ACTIVE` | `LaunchedEffect` + `delay(1000)` |
| 🪟 Vídeo local | Miniatura arrastrable 140 dp | `detectDragGestures` + `IntOffset` |

---
## Notificaciones push con FCM

El WebSocket resuelve la señalización **mientras la app está viva**. Pero Android suspende apps en segundo plano (Doze, fabricantes agresivos como Huawei/Xiaomi/Samsung) y el socket puede caerse. Para que una emergencia **siempre** despierte al supervisor hay una segunda vía: **Firebase Cloud Messaging**.

```
SUPERVISADO                    SERVIDOR                        SUPERVISOR
     │ ── call ─────────────────► │                                │
     │                            │ ¿clients[to]? NO               │
     │                            │  → pendingCalls[to] = call     │
     │                            │  → messaging.send(data-only)   │
     │                            │            └──── push ────────► │
     │                            │                                │ onMessageReceived
     │                            │                                │  → RINGING + overlay
     │                            │ ◄──── register (reconexión) ───┤
     │ ◄──── call (reentrega) ────┤   reentrega pendingCalls[to]   │
```

| Decisión | Por qué |
|---|---|
| **Data-only** (sin bloque `notification`) | Con `notification`, Android pinta el push y `onMessageReceived` **no se ejecuta** en background; no podríamos fijar `RINGING` |
| **`android.priority = "high"`** | Despierta el dispositivo y evita el batching de Doze |
| **Notificación creada desde el servicio** | Funciona aunque el `NavGraph` no esté montado |
| **`fullScreenIntent`** | Muestra la llamada sobre el bloqueo |
| **`setTimeoutAfter(30000)`** | Deja de sonar a los 30 s si nadie contesta |
| **Sin acciones en el banner** | La decisión se toma en el overlay central |

El token sigue este camino: `FcmService.onNewToken` → `DataStore` (`fcm_token`) → `register-fcm` (o dentro de `register`) → reenvío en cada conexión, porque el token rota.

---
## Permisos

La app declara **14 permisos**. La columna *Runtime* indica si Android exige diálogo en ejecución.

| Permiso | Para qué | Runtime | Se pide en |
|---|---|---|---|
| `INTERNET` | WebSocket y WebRTC | No | — |
| `ACCESS_NETWORK_STATE` | Indicador de red y poller | No | — |
| `CAMERA` | Videollamada y escáner QR | **Sí** (23+) | Homes y escáner |
| `RECORD_AUDIO` | Micrófono | **Sí** (23+) | Homes |
| `MODIFY_AUDIO_SETTINGS` | Forzar altavoz y modo comunicación | No | — |
| `WAKE_LOCK` | `PARTIAL_WAKE_LOCK` del `GuardService` | No | — |
| `FOREGROUND_SERVICE` | Servicios de llamada y guardia | No | — |
| `FOREGROUND_SERVICE_CAMERA` | Tipo del `CallService` (34+) | No | — |
| `FOREGROUND_SERVICE_MICROPHONE` | Tipo del `CallService` (34+) | No | — |
| `FOREGROUND_SERVICE_DATA_SYNC` | Tipo del `GuardService` (34+) | No | — |
| `VIBRATE` | Vibración de la emergencia | No | — |
| `POST_NOTIFICATIONS` | Notificación de llamada entrante | **Sí** (33+) | `MainActivity` + homes |
| `USE_FULL_SCREEN_INTENT` | Pantalla completa sobre bloqueo | No | — |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Excluir de Doze | **Sí** (diálogo, 23+) | `MainActivity` |
| `RECEIVE_BOOT_COMPLETED` | Auto-arranque al encender | No | — |

**Permisos que NO se piden (y es relevante):** `READ_CONTACTS` (no hay agenda), `ACCESS_FINE_LOCATION` (no se usa ubicación), `READ_PHONE_STATE` y `SYSTEM_ALERT_WINDOW`.

Notas de plataforma:

- **Android 13+**: `POST_NOTIFICATIONS` es runtime; sin él el supervisor no ve la alerta.
- **Android 14+**: los FGS deben declarar tipo (`camera|microphone`, `dataSync`).
- **Android 12+**: `PendingIntent` inmutable (`FLAG_IMMUTABLE`), presente en las tres notificaciones.
- **Fabricantes agresivos**: además de la exención de batería, Huawei/Xiaomi pueden requerir ajustes propios.

---

## Arquitectura MVVM

```
UI (Compose) → ViewModels → CallCoordinator / dominio → datos (SignalingClient,
WebRtcManager, SettingsRepository, ConnectivityObserver) → servicios Android
(CallService, GuardService, FcmService, BootReceiver)
```

| Capa | Componentes | Responsabilidad |
|---|---|---|
| **UI** | Seis pantallas + `AppViewModelFactory` | Renderizar `StateFlow` e invocar métodos |
| **ViewModels** | Onboarding, SupervisedHome, SupervisorHome, Call, Settings | Puente entre estado y pantalla |
| **Dominio** | `CallCoordinator`, `QrLinkCodec`, modelos | Estado de llamada, guards, auto-respuesta |
| **Datos** | Señalización, WebRTC, DataStore, red, audio | Infraestructura y persistencia |
| **Servicios** | FGS de llamada/guardia, FCM, boot | Supervivencia en segundo plano |

| Decisión | Alternativa descartada | Motivo |
|---|---|---|
| **DI manual** (`AppContainer`) | Hilt / KSP | Grafo de 7 objetos: menos dependencias y menos superficie de fallo |
| **`CallCoordinator` singleton** | Estado en el VM de pantalla | La llamada sobrevive a rotación, cierre de pantalla y cambio de Activity |
| **`StateFlow` + `Channel`→`Flow`** | `LiveData` / callbacks | Compatible con Compose y desacoplado del hilo de OkHttp |
| **DataStore Preferences** | Room / SharedPreferences | Asíncrono y transaccional para 13 claves |
| **Un solo módulo `:app`** | Multi-módulo | ~4.200 líneas de Kotlin: dividir añadiría fricción |

### Flujo de una llamada saliente

`callLinkedPeer()` → `startOutgoingCall()` (guards + `callState = DIALING` + `CallService`) → `sendCall` → llega `call-accepted` → `startWebRtcAsInitiator()` (`CONNECTING`, captura, altavoz si supervisado, `createOffer`) → `answer` + ICE → `ACTIVE`.

### Persistencia

`SettingsRepository` encapsula **13 claves** de `DataStore` (`alertamayor_settings`): `device_id`, `device_name`, `mode`, `linked_peer_id`, `linked_peer_name`, `signaling_server_url`, `onboarding_done`, `theme`, `fcm_token`, `turn_url`, `turn_username`, `turn_password` y `language`.

> ⚠️ `fcm_token` y `turn_password` se guardan **sin cifrar**. Para producción haría falta Android Keystore. Es una limitación documentada, no oculta.

---
## Estructura del proyecto

```
teleAsistenciaV1.3/
├── README.md · LICENSE · build.gradle.kts · settings.gradle.kts
├── gradle.properties · gradle/libs.versions.toml · gradle/wrapper/ (8.13)
├── local.properties                ⛔ EXCLUIDO: ruta del SDK
├── app/
│   ├── build.gradle.kts            (compileSdk 35, minSdk 24, JVM 17)
│   ├── google-services.json        ⛔ EXCLUIDO: Firebase
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/alertamayor/app/
│       │   ├── MainActivity.kt · AlertaMayorApp.kt · BootReceiver.kt
│       │   ├── di/AppContainer.kt
│       │   ├── domain/ (QrLinkCodec · model/)
│       │   ├── data/ (local/ · network/ · signaling/)
│       │   ├── webrtc/ (CallCoordinator · WebRtcManager · AudioSpeakerController)
│       │   ├── service/ (CallService · GuardService · FcmService · NotificationHelper)
│       │   └── ui/ (navigation · onboarding · supervised · supervisor
│       │            qr · call · settings · theme · AppViewModelFactory)
│       └── res/ (values + values-en + xml + drawable)
│       ├── debug/res/xml/network_security_config.xml    (permite ws:// en LAN)
│       └── release/res/xml/network_security_config.xml  (prohíbe cleartext)
├── server/ (index.js · package.json · deploy-vps.sh · README.md)
│   └── serviceAccountKey.json      ⛔ EXCLUIDO: credencial Firebase
└── docs/ (datasheet · developer · vps-deployment · REFERENCES · BITACORA
          assets/architecture.svg · call-flow.svg · qr-pairing.svg · deployment.svg)
```

| Componente | Líneas |
|---|---|
| Kotlin (32 archivos) | ~4.175 |
| Servidor Node.js | ~230 |
| Recursos ES + EN | ~250 |

Los archivos mayores: `CallCoordinator.kt` (473), `SupervisedHomeScreen.kt` (405), `WebRtcManager.kt` (357).

---
## Requisitos

| Herramienta | Versión mínima | Notas |
|---|---|---|
| **JDK** | **17** | AGP 8.9 lo exige; JDK 21 también sirve |
| **Android SDK** | Platform 35 + Build-Tools 35.x | `compileSdk = 35` |
| **Gradle** | 8.13 (**wrapper incluido**) | `./gradlew` lo descarga; no instalar a mano |
| **Android Studio** | Ladybug o posterior (opcional) | Solo si prefieres IDE |
| **Node.js** | 18 (recomendado 20 LTS) | Solo para el servidor |
| **Firebase** | Proyecto con app `com.alertamayor.app` | Necesario para `google-services.json` |
| **Teléfonos** | 2 × Android 7.0+ | Cable USB + Depuración USB |

Para el servidor: Linux/macOS/Windows, `npm` 9+ y que los teléfonos alcancen el puerto 3000 (LAN) o 443 (VPS con TLS).

---

## Compilación e instalación

### 1. Variables de entorno

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

java -version     # 17 o 21
adb version
```

Si falta el SDK:

```bash
mkdir -p "$ANDROID_HOME/cmdline-tools"
# descomprime commandlinetools-*.zip de developer.android.com en cmdline-tools/latest
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

### 2. `local.properties` (no está en el repo)

```properties
# local.properties — NO subir a git
sdk.dir=/home/TU_USUARIO/Android/Sdk
```

### 3. `google-services.json` (obligatorio)

El plugin `com.google.gms.google-services` **falla sin este archivo**. Pasos:

1. Crea un proyecto en [console.firebase.google.com](https://console.firebase.google.com).
2. Añade una app Android con package **`com.alertamayor.app`**.
3. Descarga `google-services.json` y colócalo en **`app/google-services.json`**.

> ⛔ No lo subas al repositorio. Está excluido por `.gitignore` y en este repo **no** se incluye.

### 4. URL del servidor en tiempo de compilación (opcional)

```kotlin
val signalingServerUrl = (project.findProperty("signalingServerUrl") as String?)
    ?: "wss://TU-DOMINIO.com"
```

```bash
./gradlew assembleDebug -PsignalingServerUrl=ws://mi-pc.local:3000
# o en gradle.properties: signalingServerUrl=wss://TU-DOMINIO.com
```

> ℹ️ El comentario del Gradle menciona `local.properties`, pero `project.findProperty()` **no lee** ese archivo. Usa `-P`, `gradle.properties`, o cambia la URL en Configuración (tiene prioridad porque va a `DataStore`).

### 5. Compilar

```bash
cd /ruta/a/teleAsistenciaV1.3
./gradlew clean          # opcional
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Primera vez: 2–5 min (descarga Gradle y dependencias). Siguientes: 20–60 s.

### 6. Instalar y lanzar

```bash
adb devices
# R58M12ABCDE     device      ← debe decir "device", no "unauthorized"

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.alertamayor.app/com.alertamayor.app.MainActivity
```

### 7. Dos teléfonos

```bash
adb -s R58M12ABCDE install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 3A91F2XYZW  install -r app/build/outputs/apk/debug/app-debug.apk

adb -s R58M12ABCDE shell am start -n com.alertamayor.app/com.alertamayor.app.MainActivity
adb -s 3A91F2XYZW  shell am start -n com.alertamayor.app/com.alertamayor.app.MainActivity
```

### 8. Logs

```bash
adb logcat --pid=$(adb shell pidof -s com.alertamayor.app)
adb logcat | grep -Ei 'AlertaMayor|WebRTC|Signaling|FATAL|AndroidRuntime'
adb logcat -c      # limpiar antes de reproducir un fallo
```

### 9. Release y firma

El repo **no incluye keystore** ni `signingConfigs`, así que `assembleRelease` produce un APK **sin firmar**.

```bash
keytool -genkeypair -v -keystore alertamayor-release.jks \
  -alias alertamayor -keyalg RSA -keysize 2048 -validity 10000

cat > keystore.properties <<'EOF'
storeFile=../alertamayor-release.jks
storePassword=TU_PASSWORD
keyAlias=alertamayor
keyPassword=TU_PASSWORD
EOF

./gradlew assembleRelease
./gradlew bundleRelease
```

> Para distribuirlo hay que añadir el bloque `signingConfigs` que lea `keystore.properties`. Ver [developer.md](docs/developer.md).

### 10. Verificación rápida

```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk    # ~40–60 MB
adb shell pm list packages | grep alertamayor
curl -s http://127.0.0.1:3000/health                # -> ok
```

---

## Servidor de señalización

### Arranque local

```bash
cd server
npm install
node index.js          # 0.0.0.0:3000
# o: PORT=8080 node index.js
```

Salida esperada: `Servidor de señalización (ws) escuchando en 0.0.0.0:3000`. Si existe `serviceAccountKey.json`, además `FCM inicializado`. Sin ese archivo el servidor **funciona igual**, solo se pierde el push.

### Comprobar alcance desde los teléfonos

```bash
hostname -I                 # p. ej. mi-pc.local
sudo ufw allow 3000/tcp
# En el navegador del teléfono: http://mi-pc.local:3000/health -> "ok"
```

Configura **Configuración → URL del servidor** → `ws://mi-pc.local:3000` → Guardar.

> ⚠️ El build **release** bloquea `ws://`. Para LAN usa **debug** (que permite cleartext solo para hosts privados declarados en `app/src/debug/res/xml/network_security_config.xml`; si tu PC tiene otra IP, añádela y recompila) o monta TLS.

### FCM en el servidor

1. Firebase → *Configuración del proyecto* → *Cuentas de servicio* → **Generar nueva clave privada**.
2. Guárdala como `server/serviceAccountKey.json` y reinicia: debe imprimir `FCM inicializado`.

> ⛔ Nunca subas esa clave: da control total sobre el proyecto Firebase.

### TLS y VPS

Para usar la app fuera de la LAN necesitas `wss://`. El repo incluye `server/deploy-vps.sh` (Node 20, systemd, Nginx, Let's Encrypt y coturn opcional) y `docs/vps-deployment.md`:

```bash
sudo bash deploy-vps.sh --dominio TU-DOMINIO.com --turn
```

Imprime al final la URL `wss://`, la URL TURN y las credenciales generadas, que se introducen en Configuración.

| Puerto | Protocolo | Uso | ¿Necesario? |
|---|---|---|---|
| 3000 | TCP | WebSocket sin TLS (tras proxy) | Solo LAN/directo |
| 80 | TCP | HTTP + desafío ACME | Para emitir certificado |
| 443 | TCP | `wss://` (Nginx → 3000) | **Sí**, en producción |
| 3478 | TCP/UDP | TURN | Sí con redes móviles/NAT estricto |
| 5349 | TCP/UDP | TURN sobre TLS | Opcional |
| 49152–65535 | UDP | Relay de medios TURN | Sí, si se usa TURN |

---

## Ejemplos de uso realistas

**1. Preparar el teléfono de mamá (10 minutos, una vez).** Ana instala el APK en el teléfono viejo de su madre y elige **Modo Supervisado**; aparece un QR. Instala la app en el suyo, elige **Modo Supervisor**, pulsa **Escanear QR**, apunta y escribe **"Mamá"**. En el teléfono de su madre el QR desaparece y queda la esfera roja. Deja el teléfono enchufado y comprueba que la pantalla no se apaga. Su madre no ha escrito, ni se ha registrado, ni ha recordado nada.

**2. La emergencia de las 3 de la mañana.** La madre se marea al levantarse y toca la esfera. El teléfono de Ana está en No Molestar, pero el canal de llamadas con prioridad máxima, sonido de alarma y `fullScreenIntent` enciende la pantalla: **"LLAMADA DE EMERGENCIA — Mamá está pidiendo ayuda"**. Acepta en el overlay y ve el vídeo; el altavoz del teléfono de su madre ya está activo, así que puede hablarle mientras se viste. La app hizo sola: marcar, timbrar, despertar al supervisor, abrir pantalla completa, auto-aceptar, activar altavoz y encender cámara.

**3. Llamada de control desde el trabajo.** Ana abre la app, ve **"Mamá · En línea"** y pulsa **Llamar**. El teléfono de su madre suena una vez y se auto-acepta; ella solo tiene que acercarse y hablar.

**4. Sin internet en el pueblo.** El router se cae. El indicador pasa a **sin conexión** y la esfera se vuelve **gris con barra diagonal**: no se inicia una llamada condenada a "Llamando…". Cuando vuelve el router, el poller de 4 s detecta la red y la esfera recupera el color.

**5. Cambiar todo a inglés.** Configuración → **Language** → **English**. La interfaz cambia al instante (`AppCompatDelegate.setApplicationLocales`), sin reiniciar, y persiste porque se guarda en `DataStore` y se reaplica antes de la primera pantalla.

**6. Cambiar de servidor sin reinstalar.** Configuración → **URL del servidor** → `wss://TU-DOMINIO.com` → Guardar. La app reconecta en caliente (`setServerUrl`); el TURN se actualiza con `setTurnConfig`. Restablecer el vínculo regenera el UUID y el QR viejo deja de servir.

---
## Seguridad y privacidad

| Activo | Dónde vive | Protección actual |
|---|---|---|
| `deviceId` | DataStore + QR | UUID aleatorio (122 bits); sin criptografía adicional |
| Audio y vídeo | Directo entre teléfonos (o relay TURN) | **SRTP + DTLS** obligatorio en WebRTC |
| SDP e ICE | Servidor de señalización | TLS con `wss://`; en claro solo en LAN con build debug |
| Token FCM / contraseña TURN | DataStore + memoria del servidor | **Sin cifrar** |
| `serviceAccountKey.json` | Servidor | Fuera del repo |
| `google-services.json` | Build de la app | Fuera del repo |

**Bien resuelto:** los medios van cifrados por diseño (aunque el servidor fuese malicioso no puede descifrarlos); el servidor es ciego a los medios y no graba; no hay cuentas ni PII (el nombre del par es una etiqueta local); cleartext prohibido en release; `PendingIntent` inmutables; secretos fuera del repositorio (`google-services.json`, `local.properties`, `serviceAccountKey.json`, `*.jks`, `keystore.properties`, `secrets.properties`).

**No resuelto:** el QR **es** la credencial y no caduca; el servidor **no autentica** ni limita tráfico; TURN usa credenciales estáticas; no hay cifrado en reposo; no hay certificate pinning ni ofuscación (`isMinifyEnabled = false`); FCM revela `fromDeviceId`/`fromName` a Google.

**Recomendaciones para uso real:** `wss://` con dominio propio; autenticación por token en el servidor; Keystore para secretos locales; R8 + firma propia; TURN efímero (`use-auth-secret`); rotar el vínculo si se pierde el teléfono; no publicar capturas del QR.

> 🚫 **No usar como único canal para emergencias médicas.** Es un proyecto técnico y educativo. Para teleasistencia real, contrata un servicio certificado.

---

## Rendimiento y consumo

| Recurso | Consumo | Notas |
|---|---|---|
| Ancho de banda | 0,5–1,5 Mbps por sentido | 720p30 H.264/VP8 |
| Batería en llamada | ~8–15 %/hora | Pantalla + cámara + red |
| Memoria | ~120–200 MB | El stack nativo de WebRTC es el grueso |
| APK | ~40–60 MB | WebRTC (4 ABIs) + ML Kit bundled + Compose |
| En espera | ~1–3 %/hora | `GuardService` con wakelock + notificación |

**Costes intencionales:** el poller de red cada **4 s** existe porque en EMUI/Huawei los callbacks no siempre llegan; el `ping` cada **20 s** mantiene el NAT abierto; `FLAG_KEEP_SCREEN_ON` evita que se apague la pantalla (conviene tener el teléfono enchufado).

**Optimizaciones:** `STRATEGY_KEEP_ONLY_LATEST` y flag `processed` en el escáner; QR en `Dispatchers.Default` con `RGB_565`; `collectAsStateWithLifecycle`; `SurfaceViewRenderer` reutilizado (no recrea EGL); guards de idempotencia; `singleTask`.

**Mejoras posibles:** 640×480 en redes móviles, `AudioFocusRequest` moderno, R8 y App Startup.

---

## Accesibilidad, idioma y tema

| Aspecto | Implementación |
|---|---|
| Objetivo táctil | Esfera de 200 dp (mínimo recomendado: 48 dp) |
| Contraste | Rojo `#D32F2F` y amarillo `#FFC107` sobre negro |
| Tipografía / feedback | "SOS" a 120 sp; háptico al pulsar; alarma sonora |
| Prevención de errores | Back bloqueado, botón deshabilitado sin red, guards |
| Screen readers | `contentDescription` en QR, engranaje, red y controles |

**Falta:** prueba sistemática con TalkBack, `fontScale` al 200 % y tamaños escalables (hoy `dp`/`sp` fijos).

**Multilenguaje (v1.3):** `values/strings.xml` (ES) + `values-en/strings.xml` (EN), `locales_config.xml` con `es`/`en`, `AppLocalesMetadataHolderService` con `autoStoreLocales=true` para el backport en Android < 13, y `AppCompatDelegate.setApplicationLocales(...)` en `SettingsViewModel.saveLanguage()`. La preferencia va a `DataStore` y `MainActivity` la aplica **antes** de la primera pantalla. La marca no se traduce.

**Tema:** Sistema / Claro / Oscuro en caliente observando `settingsRepository.theme`. Las pantallas principales son siempre negras por diseño.

---

## Solución de problemas

### Compilación

| Síntoma | Causa | Solución |
|---|---|---|
| `File google-services.json is missing` | Falta el archivo | Colócalo en `app/google-services.json` |
| `SDK location not found` | Falta `local.properties` | `sdk.dir=/ruta/al/Sdk` |
| `Unsupported class file major version` | JDK ≤ 11 | Usa JDK 17 o 21 |
| `Could not resolve com.infobip:google-webrtc` | Sin red o proxy | Revisa conexión; está en Maven Central |
| `processDebugGoogleServices` falla | Package del JSON ≠ app | Crea la app con `com.alertamayor.app` |
| `OutOfMemoryError` en Gradle | `-Xmx2048m` corto | Sube a `-Xmx4096m` |

### Instalación

| Síntoma | Causa | Solución |
|---|---|---|
| `no devices/emulators found` | Cable de carga, drivers o depuración off | Otro cable, activar Depuración USB, `adb kill-server` |
| `device unauthorized` | Huella RSA no aceptada | Desbloquea y acepta el diálogo |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Firma distinta | `adb uninstall com.alertamayor.app` |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | `versionCode` menor | Desinstala o sube el código |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | Poco espacio | Libera ~60 MB |

### En ejecución

| Síntoma | Causa | Solución |
|---|---|---|
| Queda en "Desconectado" | URL mal, servidor caído o firewall | Abre `http://IP:3000/health` en el teléfono |
| `ws://` falla en release | Cleartext bloqueado | Build debug o `wss://` |
| `ws://` falla en debug | IP fuera de la allowlist | Añádela al `network_security_config.xml` de debug |
| El QR no se lee | Lente sucia, poca luz o QR pequeño | Limpia, acerca, sube el brillo |
| El supervisor no recibe la llamada | Sin notificaciones, FCM o batería optimizada | Concede permisos y exención de batería |
| La notificación no suena | Canal "Llamadas" silenciado | Ajustes → Apps → Alerta Mayor → Notificaciones |
| Se queda en "Llamando…" | Destino desconectado sin FCM | Revisa que esté en línea; configura FCM |
| Conecta en Wi-Fi pero no en 4G | Falta TURN | Configura coturn y los tres campos TURN |
| Se oye pero no se ve | Permiso de cámara denegado | Concédelo en ambos teléfonos |
| Eco en la voz | Dos altavoces abiertos | Auricular en un lado o silenciar uno |
| No arranca tras reiniciar | Permiso o fabricante restrictivo | Revisa `RECEIVE_BOOT_COMPLETED` y autoarranque |
| El botón está gris | Sin internet detectado | Recupera la red; refresca en ≤ 4 s |

### Servidor

| Síntoma | Causa | Solución |
|---|---|---|
| `EADDRINUSE :3000` | Puerto ocupado | `lsof -i :3000` o `PORT=3001` |
| `Cannot find module 'ws'` | Falta instalar | `cd server && npm install` |
| `FCM no inicializado` | Falta `serviceAccountKey.json` | Añádelo o ignora si no usas push |
| `Error enviando push FCM` | Token inválido o proyecto distinto | Misma cuenta de servicio y `google-services.json` |
| Nginx 502 | Node no escucha | `systemctl status alertamayor-signaling` |

---

## Limitaciones conocidas

1. **Un solo par por teléfono.** `linked_peer_id` es un único valor; no hay grupos ni varios cuidadores.
2. **El QR es la única credencial y no caduca.** Sin autenticación de supervisor ni revocación remota.
3. **El servidor no autentica ni limita tráfico.** Pensado para red de confianza o VPS privado.
4. **Estado en memoria.** Un reinicio corta llamadas y borra `pendingCalls` y `fcmTokens`.
5. **Sin historial** de llamadas, duración ni eventos.
6. **Sin detección de caídas ni telemetría.**
7. **Sin grabación** (decisión de privacidad, pero también limitación operativa).
8. **Sin cifrado en reposo** para token FCM y contraseña TURN.
9. **Dependencia de Google** para STUN y FCM.
10. **Fabricantes agresivos** pueden matar servicios pese al FGS.
11. **El modo supervisado depende de la pantalla encendida**: no hay botón físico, widget ni atajo del sistema.
12. **Un idioma por dispositivo**; no hay UI bilingüe simultánea.
13. **Accesibilidad incompleta** (TalkBack, `fontScale`).
14. **Sin firma de release configurada.**
15. **Sin tests automatizados**: pruebas manuales con dos teléfonos y servidor local.
16. **`versionCode 2` con `versionName 1.3.0`**: deuda menor de versionado.
17. **Recursos sin usar** (`settings_show_qr`, `settings_permissions`, `notification_incoming_*`) y algún parámetro de UI obsoleto.

---

## Cómo se construyó este proyecto

Se desarrolló en **ciclos cortos y verificables**, cada uno con una hipótesis, una implementación y una prueba física en dos teléfonos. No fue lineal: la mayoría de los aprendizajes vinieron de fallos que solo aparecen en hardware real.

| Etapa | Qué se construyó | Qué se aprendió |
|---|---|---|
| 1 | Esqueleto Compose, roles, navegación | Fijar la navegación por rol antes que la UI |
| 2 | Servidor + cliente OkHttp | Registro y `peer-unavailable` son el 80 % de la depuración |
| 3 | WebRTC: captura, offer/answer, ICE | El orden de `dispose()` no es negociable |
| 4 | Pantalla de llamada, audio, guards | El doble `dispose()` provoca `SIGABRT` |
| 5 | Servicios en primer plano | Android 14 obliga a declarar tipos de FGS |
| 6 | QR, FCM data-only, conectividad | El escaneo debía funcionar sin Play Services; un push con `notification` no ejecuta `onMessageReceived` |
| 7 | Auto-arranque, batería, multilenguaje | Doze y los fabricantes rompen la "siempre activa" |

Lecciones que quedaron en el código:

- **`onDisconnected` se despacha al hilo principal**: `dispose()` desde el hilo nativo provoca `SIGABRT` (bug 3721). Está comentado para que nadie lo "optimice" de vuelta.
- **`cleanupCall()` y `close()` son idempotentes**: cuatro caminos pueden dispararlos a la vez.
- **El modo se aplica en caliente al coordinador**, no solo a `DataStore`; si no, seguía auto-aceptando en modo Supervisor (bug real de la v1.2).
- **La home supervisada bloquea Back** y **deshabilita el botón sin red**.

La bitácora completa está en **[docs/BITACORA.md](docs/BITACORA.md)**. Los comentarios del código conservan referencias a ciclos e incidencias (`F-002`, `I-003`, `M-001`) porque son la memoria del proyecto: explican por qué una línea rara es rara.

---
## Roadmap

1. **Autenticación del servidor**: token por instalación y allowlist de pares.
2. **Varios cuidadores** por supervisado.
3. **Historial local** de llamadas y eventos con retención configurable.
4. **Cifrado en reposo** con Android Keystore.
5. **TURN efímero** (`use-auth-secret`) y métricas de calidad (`getStats`).
6. **Botón físico / atajo de accesibilidad** para el modo supervisado.
7. **Tests** unitarios (`QrLinkCodec`, guards) e instrumentados.
8. **R8 + firma** y CI que genere APK firmado.
9. **Accesibilidad**: `fontScale`, TalkBack, alto contraste.
10. **Adaptación de vídeo** al ancho de banda (simulcast o escalado dinámico).

---

## Documentación adicional

| Documento | Contenido |
|---|---|
| **[docs/datasheet.md](docs/datasheet.md)** | Manual de usuario: instalación, operación por modo, configuración, FAQ, compatibilidad y problemas |
| **[docs/developer.md](docs/developer.md)** | Arquitectura, módulos, protocolo, ciclo de vida WebRTC y guía de extensión |
| **[docs/vps-deployment.md](docs/vps-deployment.md)** | Despliegue en VPS: Node, systemd, Nginx, Let's Encrypt y coturn |
| **[docs/REFERENCES.md](docs/REFERENCES.md)** | Estándares, documentación oficial y lecturas |
| **[docs/BITACORA.md](docs/BITACORA.md)** | Bitácora de ingeniería: problemas, descartes y lecciones |
| **[docs/assets/](docs/assets/)** | Diagramas SVG: arquitectura, flujo de llamada, vinculación QR y despliegue |
| **[server/README.md](server/README.md)** | Guía del servidor de señalización |

---

## Referencias

- [Android Developers](https://developer.android.com/docs) · [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Permisos en tiempo de ejecución](https://developer.android.com/guide/topics/permissions/overview) · [Servicios en primer plano](https://developer.android.com/develop/background-work/services/fgs)
- [Network security configuration](https://developer.android.com/privacy-and-security/security-config) · [Per-app languages](https://developer.android.com/guide/topics/resources/app-languages)
- [WebRTC — W3C](https://www.w3.org/TR/webrtc/) · [WebRTC for the Curious](https://webrtcforthecurious.com/)
- [RFC 8445 — ICE](https://datatracker.ietf.org/doc/html/rfc8445) · [RFC 3264 — Offer/Answer](https://datatracker.ietf.org/doc/html/rfc3264)
- [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging/android/client) · [ML Kit Barcode](https://developers.google.com/ml-kit/vision/barcode-scanning/android)
- [CameraX](https://developer.android.com/media/camera/camerax) · [OkHttp WebSockets](https://square.github.io/okhttp/features/websockets/)
- [Kotlin Flow](https://kotlinlang.org/docs/flow.html) · [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- [Node.js `ws`](https://github.com/websockets/ws) · [coturn](https://github.com/coturn/coturn)

---
## Licencia

MIT License — **Copyright (c) 2026 wanderc137**. Ver [LICENSE](LICENSE) para el texto completo.

---

<div align="center">

**Alerta Mayor v1.3.0** — Kotlin, Compose y WebRTC.

*Porque pedir ayuda no debería requerir aprender una app.*

</div>
