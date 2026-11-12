# Referencias

**Alerta Mayor 1.3.0** — Estándares, documentación oficial y lecturas que sostienen las decisiones del proyecto.

> Los enlaces se comprobaron en la fecha de publicación de esta documentación. Los estándares y documentación oficial suelen ser estables; los blogs y respuestas de foros, no. Si un enlace cambia, busca el título exacto.

---

## Índice

1. [Estándares de WebRTC y redes](#estándares-de-webrtc-y-redes)
2. [Android](#android)
3. [Jetpack Compose](#jetpack-compose)
4. [Permisos, privacidad y segundo plano](#permisos-privacidad-y-segundo-plano)
5. [WebRTC en Android](#webrtc-en-android)
6. [Cámara y códigos QR](#cámara-y-códigos-qr)
7. [Red y serialización](#red-y-serialización)
8. [Firebase](#firebase)
9. [Servidor y despliegue](#servidor-y-despliegue)
10. [Accesibilidad y diseño](#accesibilidad-y-diseño)
11. [Lecturas recomendadas](#lecturas-recomendadas)
12. [Cómo se eligieron las dependencias](#cómo-se-eligieron-las-dependencias)

---

## Estándares de WebRTC y redes

| Documento | Qué aporta | Uso en el proyecto |
|---|---|---|
| [W3C — WebRTC 1.0](https://www.w3.org/TR/webrtc/) | API y modelo de sesión | `RTCPeerConnection`, oferta/respuesta, tracks |
| [RFC 8445 — ICE](https://datatracker.ietf.org/doc/html/rfc8445) | Interactive Connectivity Establishment | Descubrimiento de rutas, candidatos, checklist |
| [RFC 5389 — STUN](https://datatracker.ietf.org/doc/html/rfc5389) | Session Traversal Utilities for NAT | `stun:stun.l.google.com:19302` |
| [RFC 5766 — TURN](https://datatracker.ietf.org/doc/html/rfc5766) | Relay traversal | coturn, configuración opcional |
| [RFC 3264 — Offer/Answer](https://datatracker.ietf.org/doc/html/rfc3264) | Negociación SDP | `createOffer` / `createAnswer` |
| [RFC 4566 — SDP](https://datatracker.ietf.org/doc/html/rfc4566) | Formato de descripción de sesión | Payload `sdp` del protocolo |
| [RFC 3711 — SRTP](https://datatracker.ietf.org/doc/html/rfc3711) | Cifrado de medios | Garantía de confidencialidad del audio/vídeo |
| [RFC 6455 — WebSocket](https://datatracker.ietf.org/doc/html/rfc6455) | Protocolo de señalización | Cliente OkHttp y servidor `ws` |
| [RFC 8827 — WebRTC Security Architecture](https://datatracker.ietf.org/doc/html/rfc8827) | Modelo de seguridad | Justifica DTLS/SRTP obligatorio |
| [RFC 5737 — IPv4 para documentación](https://datatracker.ietf.org/doc/html/rfc5737) | Rangos `192.0.2.0/24`, `198.51.100.0/24`, `203.0.113.0/24` | Placeholders de esta documentación |

### Por qué importa cada uno

- **ICE** explica por qué una llamada puede funcionar en Wi-Fi y fallar en datos móviles: no es un bug de la app, es el NAT.
- **TURN** es la respuesta cuando ICE no encuentra ruta directa. Sin TURN, el P2P es una promesa condicionada a la red.
- **Offer/Answer** explica el orden estricto: no se puede aplicar un `answer` antes de un `offer`, ni crear una respuesta sin una `PeerConnection`.

---

## Android

| Recurso | Para qué |
|---|---|
| [Android Developers](https://developer.android.com/docs) | Punto de entrada |
| [Android 15 — cambios de comportamiento](https://developer.android.com/about/versions/15/behavior-changes-15) | `targetSdk 35` |
| [Android 14 — FGS types](https://developer.android.com/about/versions/14/changes/fgs-types-required) | `camera`, `microphone`, `dataSync` |
| [Servicios en primer plano](https://developer.android.com/develop/background-work/services/fgs) | `CallService`, `GuardService` |
| [Notificaciones](https://developer.android.com/develop/ui/views/notifications) | Canales, prioridad, full screen intent |
| [Permisos en tiempo de ejecución](https://developer.android.com/guide/topics/permissions/overview) | Cámara, micrófono, notificaciones |
| [Optimización de batería](https://developer.android.com/training/monitoring-device-state/doze-standby) | Doze y exenciones |
| [Network security configuration](https://developer.android.com/privacy-and-security/security-config) | `ws://` en debug, `wss://` en release |
| [Per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages) | Selector ES/EN |
| [AppCompat — locales](https://developer.android.com/reference/androidx/appcompat/app/AppCompatDelegate) | Backport en Android < 13 |
| [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) | Persistencia |
| [ViewModel y StateFlow](https://developer.android.com/topic/architecture/ui-layer) | MVVM |
| [Navigation Compose](https://developer.android.com/jetpack/compose/navigation) | Rutas |
| [`PendingIntent` inmutable](https://developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability) | Android 12+ |

### Documentación puntual que resolvió dudas

- **`FLAG_KEEP_SCREEN_ON`**: la forma correcta es en la `Activity`, no un wakelock. Evita el permiso `WAKE_LOCK` para la pantalla (que sí se usa en el servicio de guardia).
- **`launchMode="singleTask"`**: necesario para que una notificación con la app abierta no cree otra `MainActivity`.
- **`setFullScreenIntent`**: en Android 14+ el permiso `USE_FULL_SCREEN_INTENT` puede requerir aprobación del usuario en algunos casos; el código lo declara y lo usa con fallback a heads-up.

---

## Jetpack Compose

| Recurso | Para qué |
|---|---|
| [Compose — descripción general](https://developer.android.com/jetpack/compose) | Modelo declarativo |
| [Estado y ciclo de vida](https://developer.android.com/jetpack/compose/state) | `remember`, `LaunchedEffect` |
| [Efectos secundarios](https://developer.android.com/jetpack/compose/side-effects) | `DisposableEffect`, `LaunchedEffect` |
| [Listas y rendimiento](https://developer.android.com/jetpack/compose/performance) | `key`, recomposición |
| [Material 3](https://developer.android.com/jetpack/compose/designsystems/material3) | Tema y componentes |
| [Accesibilidad en Compose](https://developer.android.com/jetpack/compose/accessibility) | `contentDescription`, semántica |
| [`collectAsStateWithLifecycle`](https://developer.android.com/reference/kotlin/androidx/lifecycle/compose/package-summary) | Recolección consciente del ciclo de vida |

### Patrones usados

- **Estado unidireccional**: la pantalla recibe estado y callbacks; nunca muta el ViewModel directamente.
- **`LaunchedEffect(clave)`** para navegación reactiva y temporizadores.
- **`AndroidView`** para incrustar `SurfaceViewRenderer` y `PreviewView`, que son vistas clásicas.
- **`pointerInput` + `detectDragGestures`** para la miniatura arrastrable del vídeo local.

---

## Permisos, privacidad y segundo plano

| Recurso | Para qué |
|---|---|
| [Privacidad en Android](https://developer.android.com/privacy-and-security) | Buenas prácticas |
| [Indicadores de privacidad](https://developer.android.com/privacy-and-security/security-tips#privacy-indicators) | Cámara/micrófono en uso |
| [Restricciones de inicio en segundo plano](https://developer.android.com/develop/background-work/background-tasks/broadcasts) | `BOOT_COMPLETED` |
| [Doze y App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby) | Exención de batería |
| [Guía de fabricantes (dontkillmyapp)](https://dontkillmyapp.com/) | Ajustes por OEM |

### Nota sobre fabricantes

La documentación de Android describe el comportamiento **estándar**. Fabricantes como Huawei, Xiaomi, Samsung, Oppo o Vivo añaden capas de ahorro de energía que pueden matar servicios en primer plano. `dontkillmyapp.com` mantiene una lista actualizada de ajustes por modelo; el datasheet resume los cinco casos más comunes.

---

## WebRTC en Android

| Recurso | Para qué |
|---|---|
| [webrtc.googlesource.com](https://webrtc.googlesource.com/src/) | Código fuente del stack nativo |
| [Infobip — google-webrtc](https://github.com/infobip/mobile-webrtc) | Artefacto Maven usado (`1.0.47617t`) |
| [WebRTC for the Curious](https://webrtcforthecurious.com/) | Libro abierto; la mejor explicación de ICE y SDP |
| [WebRTC samples](https://webrtc.github.io/samples/) | Ejemplos de API web (conceptos equivalentes) |
| [Bug 3721 — SIGABRT al dispose desde signaling thread](https://bugs.chromium.org/p/webrtc/issues/detail?id=3721) | Origen del despacho a Main |

### Por qué `com.infobip:google-webrtc`

| Alternativa | Motivo para descartarla |
|---|---|
| `org.webrtc:google-webrtc` (antigua) | Artefacto descontinuado, sin actualizaciones |
| Compilar el stack desde fuente | Días de build y toolchain NDK; inviable para este alcance |
| Librería de terceros tipo Twilio/Jitsi | Añade cuenta, coste y dependencia de servicio |
| WebView con `getUserMedia` | Latencia, permisos y ciclo de vida peores en Android |

El artefacto de Infobip es un empaquetado del stack oficial, con ABI múltiple y actualizaciones razonables. El coste es el tamaño del APK.

### Aprendizajes que no están en la documentación

- `PeerConnection.dispose()` **no** es idempotente.
- El orden de liberación importa: capturador → PC → fuentes → helper.
- `onConnectionChange(DISCONNECTED)` llega en un hilo nativo; tocar la UI o liberar desde ahí es un `SIGABRT`.
- `addIceCandidate` antes de `setRemoteDescription` falla silenciosamente en algunas versiones; hay que encolar.

---

## Cámara y códigos QR

| Recurso | Para qué |
|---|---|
| [CameraX](https://developer.android.com/media/camera/camerax) | Preview y análisis |
| [CameraX — ImageAnalysis](https://developer.android.com/media/camera/camerax/analyze) | `STRATEGY_KEEP_ONLY_LATEST` |
| [ML Kit — Barcode Scanning](https://developers.google.com/ml-kit/vision/barcode-scanning/android) | Lectura de QR |
| [ML Kit — modelo incluido vs. descargado](https://developers.google.com/ml-kit/vision/barcode-scanning/android#bundled-vs-unbundled) | Decisión de usar bundled |
| [ZXing](https://github.com/zxing/zxing) | Generación de QR |
| [ZXing — `QRCodeWriter`](https://zxing.github.io/zxing/apidocs/com/google/zxing/qrcode/QRCodeWriter.html) | API usada |

### Bundled vs. unbundled

| | Bundled | Unbundled (Play Services) |
|---|---|---|
| Tamaño | +2–3 MB | 0 |
| Sin GMS | **Funciona** | No funciona |
| Sin conexión | **Funciona** | La primera vez descarga el modelo |
| Actualizaciones | Con la app | Automáticas |

Se eligió **bundled** para que la vinculación funcione en cualquier teléfono, incluso sin Google Play Services. Es una decisión de robustez sobre tamaño.

---

## Red y serialización

| Recurso | Para qué |
|---|---|
| [OkHttp — WebSocket](https://square.github.io/okhttp/features/websockets/) | Cliente de señalización |
| [OkHttp — `pingInterval`](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-okhttp-client-builder/ping-interval/) | Mantener NAT |
| [kotlinx.serialization](https://kotlinlang.org/docs/serialization.html) | JSON |
| [Kotlin Coroutines — Flow](https://kotlinlang.org/docs/flow.html) | Estado y eventos |
| [Kotlin Coroutines — `Channel`](https://kotlinlang.org/docs/channels.html) | Puente callback → Flow |
| [Android — `ConnectivityManager`](https://developer.android.com/reference/android/net/ConnectivityManager) | Estado de red |
| [`NetworkCapabilities`](https://developer.android.com/reference/android/net/NetworkCapabilities) | `INTERNET` + `VALIDATED` |

### Por qué `Channel(UNLIMITED)`

El cliente WebSocket recibe callbacks en un hilo de OkHttp. Un `Channel` sin buffer podría suspender ese hilo y bloquear la lectura del socket; con `UNLIMITED`, la emisión nunca suspende. El consumidor (el coordinador) procesa en Main. El riesgo teórico es crecimiento sin límite si el consumidor se atasca; en la práctica el volumen es de decenas de mensajes por llamada.

---

## Firebase

| Recurso | Para qué |
|---|---|
| [Firebase — añadir app Android](https://firebase.google.com/docs/android/setup) | `google-services.json` |
| [FCM — Android client](https://firebase.google.com/docs/cloud-messaging/android/client) | Token y recepción |
| [FCM — tipos de mensaje](https://firebase.google.com/docs/cloud-messaging/concept-options#notifications_and_data_messages) | Data-only vs. notification |
| [FCM — prioridad](https://firebase.google.com/docs/cloud-messaging/android/message-priority) | `high` y Doze |
| [firebase-admin — Node.js](https://firebase.google.com/docs/admin/setup) | Envío desde el servidor |
| [Firebase — cuentas de servicio](https://firebase.google.com/docs/admin/setup#initialize-sdk) | `serviceAccountKey.json` |

### La regla que hay que recordar

Si el push incluye un bloque `notification`, **Android lo pinta** y `onMessageReceived` no se ejecuta cuando la app está en segundo plano. Para una alarma que debe cambiar el estado de la app, el mensaje tiene que ser **data-only**. Está documentado, pero se descubre a golpes.

---

## Servidor y despliegue

| Recurso | Para qué |
|---|---|
| [Node.js](https://nodejs.org/en/docs) | Runtime |
| [`ws` — WebSocket para Node](https://github.com/websockets/ws) | Servidor |
| [Nginx — WebSocket proxying](https://nginx.org/en/docs/http/websocket.html) | `Upgrade` y `Connection` |
| [Nginx — `proxy_read_timeout`](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_read_timeout) | Evitar cortes a los 60 s |
| [certbot](https://certbot.eff.org/instructions) | TLS |
| [Let's Encrypt — límites](https://letsencrypt.org/docs/rate-limits/) | Diagnóstico de fallos |
| [coturn](https://github.com/coturn/coturn) | TURN/STUN |
| [coturn wiki](https://github.com/coturn/coturn/wiki) | `turnserver.conf` |
| [systemd — servicio](https://www.freedesktop.org/software/systemd/man/systemd.service.html) | Unidad del servicio |
| [Mozilla SSL Configuration Generator](https://ssl-config.mozilla.org/) | Cifrados de Nginx |
| [sslip.io](https://sslip.io/) | DNS comodín para pruebas |

### Comandos que se usaron para verificar

```bash
curl -s https://TU-DOMINIO.com/health
openssl s_client -connect TU-DOMINIO.com:443 -servername TU-DOMINIO.com
ss -lunp | grep 3478
journalctl -u alertamayor-signaling -n 50
```

---

## Accesibilidad y diseño

| Recurso | Para qué |
|---|---|
| [WCAG 2.2 — contraste](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) | Rojo/amarillo sobre negro |
| [Material 3 — accesibilidad](https://m3.material.io/foundations/accessible-design/overview) | Objetivos táctiles, tipografía |
| [Android — tamaño de objetivo táctil](https://support.google.com/accessibility/android/answer/7101858) | Mínimo 48 dp |
| [Android — TalkBack](https://support.google.com/accessibility/android/answer/6283677) | Pruebas pendientes |
| [Nielsen — usabilidad para mayores](https://www.nngroup.com/articles/usability-for-senior-citizens/) | Decisiones de tamaño y contraste |

### Criterios aplicados

| Criterio | Valor | Dónde |
|---|---|---|
| Contraste texto/fondo | ≥ 7:1 | SOS amarillo sobre negro |
| Objetivo táctil | 200 dp | Esfera |
| Texto mínimo | 16 sp (títulos 24–120 sp) | Pantallas |
| Feedback multimodal | Visual + háptico + sonoro | Botón y notificación |
| Sin gestos obligatorios | Solo toques | Toda la app |

---

## Lecturas recomendadas

Ordenadas por utilidad real para este proyecto:

1. **WebRTC for the Curious** — explica ICE, SDP y NAT sin asumir conocimiento previo. Si solo lees un recurso, que sea este.
2. **Android — guía de servicios en primer plano** — obligatoria desde Android 14; evita el `MissingForegroundServiceTypeException`.
3. **FCM — tipos de mensaje** — la diferencia entre data-only y notification es la causa del 80 % de los bugs de push.
4. **Nginx — WebSocket proxying** — dos líneas de configuración separan "funciona" de "se corta cada minuto".
5. **dontkillmyapp.com** — la documentación no oficial más útil sobre fabricantes.
6. **RFC 8445 (ICE)** — denso, pero explica por qué TURN existe.
7. **Material 3 — accesibilidad** — tamaños y contraste con criterio.
8. **Nielsen — usability for senior citizens** — justifica las decisiones de diseño.

---

## Cómo se eligieron las dependencias

| Criterio | Peso | Ejemplo |
|---|---|---|
| Mantenimiento activo | Alto | OkHttp, Compose, CameraX |
| Tamaño | Medio | Se aceptó ML Kit bundled por robustez |
| Sin cuenta obligatoria | Alto | Se descartó Twilio/Jitsi |
| Compatibilidad con minSdk 24 | Alto | Se verificó cada artefacto |
| Documentación | Medio | Firebase y Android están bien documentados |
| Licencia | Alto | Apache 2.0 / MIT en todas |

### Dependencias descartadas

| Dependencia | Motivo |
|---|---|
| Hilt | Grafo de 7 objetos; KSP y anotaciones no compensan |
| Room | 13 claves; DataStore es suficiente |
| Retrofit | No hay API REST, solo WebSocket |
| Coil/Glide | No hay imágenes remotas |
| Accompanist | Las APIs necesarias ya están en Compose estable |
| Librería de QR de terceros | ZXing core es pequeño y suficiente |
| WebRTC de terceros con SDK cloud | Cuenta, coste y dependencia |

---

<div align="center">

**Alerta Mayor 1.3.0** · Referencias

*Documentar las fuentes es parte de documentar el código.*

</div>
