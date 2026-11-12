# Bitácora de ingeniería

**Alerta Mayor 1.3.0** — Problemas, descartes, decisiones y lecciones.

Este documento no es un changelog. Es el registro de **por qué** el código es como es: los fallos que costaron horas, las alternativas que se probaron y se abandonaron, y las reglas que quedaron grabadas a fuego en los comentarios.

---

## Índice

1. [Cómo leer esta bitácora](#cómo-leer-esta-bitácora)
2. [Línea de tiempo](#línea-de-tiempo)
3. [Problemas y soluciones](#problemas-y-soluciones)
4. [Vías descartadas](#vías-descartadas)
5. [Decisiones técnicas](#decisiones-técnicas)
6. [Lecciones aprendidas](#lecciones-aprendidas)
7. [Qué haría distinto](#qué-haría-distinto)
8. [Deuda y próximos pasos](#deuda-y-próximos-pasos)
9. [Métricas del proyecto](#métricas-del-proyecto)
10. [Glosario de incidentes](#glosario-de-incidentes)

---

## Cómo leer esta bitácora

Cada problema se documenta con esta estructura:

```
Síntoma   → lo que se veía
Causa     → lo que realmente pasaba
Fix       → el cambio aplicado
Lección   → la regla que evita repetirlo
```

Los identificadores (`F-00x`, `I-00x`, `M-00x`) aparecen también en los comentarios del código. No son decoración: permiten rastrear una línea rara hasta el incidente que la motivó.

| Prefijo | Tipo |
|---|---|
| `F-` | Fallo funcional (la app no hace lo que debe) |
| `I-` | Incidencia de integración (WebRTC, FCM, servidor) |
| `M-` | Mejora o decisión de diseño |
| `R-` | Riesgo conocido, no resuelto |

---

## Línea de tiempo

| Ciclo | Objetivo | Resultado | Aprendizaje principal |
|---|---|---|---|
| 1 | Esqueleto Compose, roles, navegación | Base funcional | La navegación por rol se define antes que la UI |
| 2 | Servidor + cliente WebSocket | Registro y presencia | El 80 % de la depuración está en `register` y `peer-unavailable` |
| 3 | WebRTC: captura, offer/answer, ICE | Primera llamada | El orden de liberación no es negociable |
| 4 | Pantalla de llamada, audio, guards | Llamada usable | El doble `dispose()` mata el proceso |
| 5 | Servicios en primer plano | Supervivencia | Android 14 exige tipos de FGS |
| 6 | QR, vinculación, conectividad, tema | Vinculación completa | El escaneo debía funcionar sin GMS |
| 7 | FCM data-only y reentrega | Alarma con app dormida | `notification` y `onMessageReceived` son excluyentes |
| 8 | Auto-arranque, batería, táctil | "Siempre en guardia" | Los fabricantes rompen las reglas |
| 9 | Multilenguaje, tema, ajustes | v1.3 bilingüe | El per-app language necesita backport |
| 10 | Endurecimiento y documentación | v1.3.0 documentada | La documentación también es código |

---

## Problemas y soluciones

### F-001 · El proceso moría al colgar (`SIGABRT`)

**Síntoma.** Al pulsar colgar, la app se cerraba sin diálogo de error. En `logcat`:

```
A/libc: Fatal signal 6 (SIGABRT) ... in thread "signaling_thread"
```

**Causa.** Dos caminos llamaban a `WebRtcManager.close()` casi a la vez: el botón de colgar y el evento `EndCall` del servidor. `PeerConnection.dispose()` no es idempotente; el segundo `dispose()` aborta el proceso.

**Fix.**

```kotlin
private var closed = false

fun close() {
    synchronized(this) {
        if (closed) return
        closed = true
    }
    // ... liberar en orden
}
```

**Lección.** Todo recurso nativo necesita un guard de idempotencia explícito. No basta con "solo se llama una vez": los eventos de red son concurrentes por definición.

---

### F-002 · `SIGABRT` al recibir `onDisconnected`

**Síntoma.** La app se cerraba al perder la red durante una llamada, sin tocar ningún botón.

**Causa.** `PeerConnection.Observer.onConnectionChange(DISCONNECTED)` llega en el **signaling thread** nativo. Llamar desde ahí a `cleanupCall()` → `dispose()` provoca el mismo aborto que F-001, pero por una ruta distinta (bug 3721 del stack de WebRTC).

**Fix.**

```kotlin
override fun onDisconnected() {
    callState = ENDED
    scope.launch { cleanupCall() }   // Main dispatcher
}
```

**Lección.** Los callbacks de WebRTC **no** corren en el hilo principal. Cualquier cosa que toque UI, estado compartido o libere recursos debe despacharse. El comentario en el código dice explícitamente "no optimizar esto".

---

### F-003 · El modo Supervisor seguía auto-aceptando llamadas

**Síntoma.** Al cambiar de Supervisado a Supervisor en Configuración, las llamadas entrantes se contestaban solas: el overlay de Aceptar/Rechazar nunca aparecía.

**Causa.** `SettingsViewModel.saveMode()` escribía en DataStore, pero `CallCoordinator` guardaba el modo en una propiedad en memoria leída solo en `connect()`. El coordinador seguía creyendo que era Supervisado.

**Fix.** `saveMode()` llama también a `coordinator.setMode(mode)`, que actualiza la propiedad en caliente. El modo se lee de DataStore al arrancar y se mantiene sincronizado en cada cambio.

**Lección.** Persistir no es aplicar. Cada preferencia que afecte al comportamiento en runtime necesita un camino de actualización inmediata, no solo un `save`.

---

### I-001 · FCM no ejecutaba `onMessageReceived` en segundo plano

**Síntoma.** Con la app del supervisor cerrada, el push llegaba (el teléfono sonaba con el tono por defecto), pero la app no mostraba el overlay ni cambiaba a `RINGING`.

**Causa.** El servidor enviaba un mensaje con bloque `notification` y `data`. Cuando hay `notification` y la app está en background, Android **pinta la notificación él mismo** y no invoca `onMessageReceived`.

**Fix.** El servidor envía **solo** `data`:

```js
messaging.send({
  token,
  data: { type: 'call', fromDeviceId, fromName },
  android: { priority: 'high' }
});
```

La notificación la construye `NotificationHelper` desde `FcmService`, con el canal, el sonido de alarma, la vibración y el `fullScreenIntent`.

**Lección.** Para lógica en background, data-only. Para notificaciones "fire and forget", `notification`. Mezclarlos da un comportamiento distinto según el estado de la app, que es exactamente lo que no quieres en una alarma.

---

### I-002 · "Sin conexión" falso al abrir la app

**Síntoma.** El supervisor abría la app y la tarjeta decía "Sin conexión" aunque el teléfono del mayor estuviera conectado. Al rato cambiaba a "En línea".

**Causa.** El servidor solo difundía `peer-online` **cuando alguien se registraba**. Si el supervisado ya estaba conectado antes de que el supervisor abriera la app, este nunca recibía el evento.

**Fix.** Al registrarse, el servidor envía `peers-online` con la lista completa:

```js
send(ws, { type: 'peers-online', payload: { deviceIds: Array.from(clients.keys()) } });
```

El coordinador hace `peerOnline = deviceIds.contains(linkedPeerId)`.

**Lección.** Los eventos de presencia no bastan; hace falta un **snapshot** al entrar. Es el mismo patrón que un chat: historial + eventos en vivo.

---

### I-003 · Llamada perdida si el destino estaba desconectado

**Síntoma.** El supervisor llamaba, el servidor respondía `peer-unavailable`, y la llamada se perdía aunque el teléfono del mayor se reconectara dos segundos después.

**Causa.** El servidor no guardaba nada para destinos ausentes.

**Fix.** `pendingCalls`: si el mensaje es `call` y no hay socket, se guarda y se intenta push FCM. Al reconectar, se reentrega y se borra.

**Limitación.** Solo se guarda **una** llamada por destino (la última gana). No es una cola; es una "llamada perdida pendiente". Para una emergencia es suficiente: si alguien llamó tres veces, la última es la relevante.

**Lección.** Un sistema de alarma debe asumir que el receptor duerme. "No está" no es "no quiere".

---

### I-004 · `ws://` funcionaba en debug y fallaba en release

**Síntoma.** La app compilada en release no conectaba al servidor local, sin mensaje claro.

**Causa.** `network_security_config` bloquea cleartext en release (`cleartextTrafficPermitted="false"`). Es correcto: no se quiere tráfico sin cifrar en producción.

**Fix.** Se mantuvieron dos configuraciones:

| Variante | Política |
|---|---|
| debug | Cleartext permitido **solo** para hosts privados declarados |
| release | Cleartext prohibido |

**Lección.** La seguridad por defecto debe ser la del release. El debug es una excepción explícita y acotada, no un "modo abierto". Y hay que documentarlo, porque el síntoma no dice "cleartext bloqueado".

---

### I-005 · El QR no se leía en un teléfono sin Google Play Services

**Síntoma.** En un Huawei sin GMS, el escáner mostraba la cámara pero nunca detectaba el código.

**Causa.** La primera implementación usaba la variante *unbundled* de ML Kit, que descarga el modelo a través de Play Services.

**Fix.** Se cambió a la variante **bundled** (`barcode-scanning` con modelo incluido). El APK creció unos megabytes y la vinculación pasó a funcionar sin GMS y sin conexión.

**Lección.** Si una función es parte del camino crítico de configuración, no puede depender de un servicio que el usuario podría no tener.

---

### I-006 · El servicio moría a los pocos minutos

**Síntoma.** El teléfono del mayor aparecía "Sin conexión" tras un rato con la pantalla apagada.

**Causa.** Doze y las capas de ahorro de los fabricantes suspendían el proceso. El FGS no era suficiente.

**Fix.** Tres capas:

1. `GuardService` con `dataSync` + `PARTIAL_WAKE_LOCK`.
2. Solicitud de exención de optimización de batería al abrir.
3. Documentación de ajustes por fabricante (Huawei, Xiaomi, Samsung, Oppo).

**Limitación.** Sigue sin ser infalible. En algunos modelos el usuario debe configurar el autostart a mano.

**Lección.** En Android, "siempre activo" es una negociación con el sistema y con el OEM, no una propiedad que se declare.

---

### I-007 · Navegación duplicada a la pantalla de llamada

**Síntoma.** Tras aceptar una llamada, a veces se apilaban dos `CallScreen`; al colgar, una quedaba visible y había que pulsar atrás dos veces.

**Causa.** El `LaunchedEffect` que observa `callState` se disparaba en cada emisión (RINGING, CONNECTING, ACTIVE) y llamaba a `navigate` sin control.

**Fix.**

```kotlin
navController.navigate("call/$role/$peer") { launchSingleTop = true }
```

Y salida con `popBackStack(homeRoute, false)`, con fallback a `navigate(homeRoute) { popUpTo(0) }`.

**Lección.** La navegación reactiva necesita idempotencia igual que los recursos nativos. `launchSingleTop` debería ser el valor por defecto mental.

---

### I-008 · Eco en las llamadas

**Síntoma.** Al hablar, el otro lado se oía a sí mismo con retardo.

**Causa.** Ambos teléfonos con altavoz y micrófono abiertos, cerca uno del otro (pruebas en la misma mesa).

**Fix.** No es un bug: es acústica. Se documentó la recomendación de usar auricular en un lado o silenciar el micrófono. El stack de WebRTC incluye cancelación de eco, pero no puede hacer magia con dos altavoces enfrentados.

**Lección.** Algunos "bugs" son física. Documentarlos evita horas de búsqueda en el código.

---

### M-001 · La notificación entrante perdió sus botones

**Contexto.** La primera versión tenía acciones "Aceptar" y "Rechazar" en el banner.

**Problema.** En la pantalla de bloqueo, esos botones se pulsaban sin querer. Un rechazo accidental en una emergencia es inaceptable.

**Decisión.** Se quitaron las acciones del banner. El banner solo avisa; la decisión se toma en el overlay de la app, que requiere desbloquear y un toque deliberado.

**Lección.** En una emergencia, la fricción deliberada es una feature. La accesibilidad no está reñida con la prevención de errores.

---

### M-002 · El botón se deshabilita sin red

**Contexto.** Si no hay internet, la llamada se queda en "Llamando…" hasta el timeout.

**Decisión.** `ConnectivityObserver` expone `NONE` y la pantalla supervisada pinta la esfera en gris con una barra diagonal, sin `onClick`.

**Por qué no un mensaje de error.** Un adulto mayor no necesita leer "no hay conexión": necesita entender que el botón no está disponible. El cambio visual es inmediato y no requiere interpretar texto.

**Lección.** Para usuarios con poca familiaridad tecnológica, el estado debe ser visual y binario, no un mensaje.

---

### M-003 · `ML Kit` bundled vs. APK pequeño

Se eligió robustez sobre tamaño. El APK pasa de ~40 a ~50 MB, pero la vinculación funciona en cualquier teléfono. Para una app que se instala una vez y se usa durante años, es el intercambio correcto.

---

## Vías descartadas

| Vía | Por qué se consideró | Por qué se descartó |
|---|---|---|
| **Hilt** | DI estándar en Android | Grafo de 7 objetos; KSP y anotaciones añaden build y superficie de fallo sin beneficio |
| **Room** | Persistencia robusta | 13 claves; DataStore es suficiente y más simple |
| **Retrofit** | Cliente HTTP cómodo | No hay API REST, solo WebSocket |
| **Cuentas de usuario** | Identidad "de verdad" | Añade registro, recuperación de contraseña y PII; el QR resuelve el caso |
| **Firebase Auth** | Login con teléfono | Dependencia, coste y fricción para una persona mayor |
| **WebView + `getUserMedia`** | Reutilizar web | Permisos, latencia y ciclo de vida peores que WebRTC nativo |
| **Compilar WebRTC desde fuente** | Control total | Días de build, NDK, mantenimiento imposible para un solo desarrollador |
| **Twilio / Agora / Jitsi** | Calidad gestionada | Cuenta, coste por minuto y dependencia de un tercero |
| **Notificación con acciones** | Aceptar desde el banner | Rechazos accidentales en pantalla bloqueada |
| **TURN obligatorio** | Conectar siempre | Coste de ancho de banda; en LAN no hace falta |
| **Un servidor por familia** | Privacidad | Demasiada fricción; un VPS compartido con señalización es suficiente |
| **Chat de texto** | Canal alternativo | Fuera de alcance; distrae del gesto único |
| **Detección de caídas** | Valor añadido | Sensores, falsos positivos y responsabilidad legal; no es el problema que se resolvía |
| **Historial en la nube** | Trazabilidad | Privacidad y coste; el historial local sería el siguiente paso |
| **Pantalla de "pegar ID"** | Probar sin cámara | Se descartó en producción para no abrir una vía de vinculación manual insegura |
| **Widget / botón físico** | Acceso más rápido | Requiere permisos y UI adicional; queda en roadmap |

### Descartes que dolieron

- **Cuentas de usuario.** Habrían permitido varios cuidadores, historial y revocación. Pero el coste para el usuario final era alto: registro, contraseña, recuperación. El QR ganó por usabilidad; la deuda es el modelo de un solo par.
- **TURN obligatorio.** Habría hecho que "siempre conecta". Pero obliga a pagar ancho de banda incluso cuando la llamada es en la misma casa. Se dejó opcional y documentado.

---

## Decisiones técnicas

### D-01 · `CallCoordinator` como singleton del contenedor

**Contexto.** El estado de llamada debe sobrevivir a rotaciones, cierres de pantalla y cambios de Activity.

**Alternativas.** ViewModel con scope de navegación; `SavedStateHandle`; servicio con binder.

**Decisión.** Singleton en `AppContainer` con `SupervisorJob + Dispatchers.Main`.

**Consecuencia.** Estado consistente y fácil de observar. El coste es que el contenedor se convierte en el dueño del ciclo de vida y hay que limpiarlo a mano en tests.

---

### D-02 · DI manual en lugar de Hilt

**Contexto.** Siete objetos, un módulo, un desarrollador.

**Decisión.** `AppContainer` + `AppViewModelFactory` con un `when`.

**Consecuencia.** Build más rápido, sin KSP, sin magia. El coste es que añadir un ViewModel exige tocar la factory a mano y el error es en runtime.

**Cuándo revisar.** Si el grafo pasa de ~15 objetos o aparecen varios módulos, Hilt empieza a compensar.

---

### D-03 · DataStore en lugar de Room

**Contexto.** 13 claves escalares, sin relaciones.

**Decisión.** `Preferences DataStore`.

**Consecuencia.** API asíncrona, transaccional y sin migraciones de esquema. El coste es que no hay consultas ni cifrado integrado.

---

### D-04 · `StateFlow` + `Channel` en lugar de callbacks

**Contexto.** OkHttp entrega eventos en su propio hilo; la UI necesita estado en Main.

**Decisión.** `SignalingClient` emite a un `Channel(UNLIMITED)`; el coordinador transforma a `StateFlow`.

**Consecuencia.** Desacoplamiento y múltiples consumidores. El riesgo es el crecimiento del buffer si el consumidor se atasca; en la práctica, el volumen es bajo.

---

### D-05 · Protocolo JSON plano sobre WebSocket

**Contexto.** Se necesitaban unos 19 tipos de mensaje.

**Alternativas.** gRPC, MessagePack, MQTT.

**Decisión.** JSON con `type`, `from`, `to`, `payload` (`JsonElement`).

**Consecuencia.** Depurable con `wscat`, sin esquemas que mantener, fácil de portar. El coste es el parseo manual del payload y la ausencia de validación formal.

---

### D-06 · Un solo par por dispositivo

**Contexto.** El modelo de datos guarda `linked_peer_id`.

**Decisión.** Un vínculo único, mutable solo por restablecimiento.

**Consecuencia.** Simplicidad y una UI clara. El coste es que no hay varios cuidadores ni delegación.

**Cuándo revisar.** Si aparece la necesidad de que un hermano o un vecino también pueda llamar.

---

### D-07 · Auto-respuesta solo en modo Supervisado

**Contexto.** El adulto mayor no debe tener que aceptar.

**Decisión.** `mode == SUPERVISED` implica `acceptIncomingCall()` automático.

**Consecuencia.** Un toque (o ninguno) para el mayor. El riesgo es que cualquier persona con el `deviceId` puede abrir la cámara; por eso el QR es sensible y el servidor no autentica (deuda R-01).

---

### D-08 · FCM como red de seguridad, no como canal principal

**Contexto.** El WebSocket es rápido pero frágil en background.

**Decisión.** WebSocket para señalización; FCM solo para despertar y reentregar.

**Consecuencia.** La app funciona sin FCM en primer plano; con FCM, sobrevive a Doze. El coste es la dependencia de Google y la duplicación de caminos.

---

### D-09 · Renderers de vídeo reutilizados

**Contexto.** Crear `SurfaceViewRenderer` por llamada filtra contextos EGL.

**Decisión.** Se crean una vez en el contenedor y se limpian con `clearImage()`.

**Consecuencia.** Menos memoria y arranque más rápido. El coste es que el manager vive toda la sesión de la app.

---

### D-10 · Documentar la deuda en el código y en la docs

**Contexto.** Un proyecto de portfolio tiende a ocultar lo que no funciona.

**Decisión.** Cada limitación está escrita: QR sin caducidad, servidor sin auth, secretos sin cifrar, sin tests, `versionCode` desalineado.

**Consecuencia.** Quien lee sabe qué esperar. Es más útil que un README aspiracional.

---

## Lecciones aprendidas

### Sobre WebRTC

1. **`dispose()` no es idempotente.** Guard explícito, siempre.
2. **Los callbacks no están en tu hilo.** Despacha a Main antes de tocar estado o liberar.
3. **El orden de liberación importa.** Capturador → PC → fuentes → helper.
4. **ICE es asíncrono y con race conditions.** Encola candidatos hasta tener `remoteDescription`.
5. **El vídeo no es el problema; el audio sí.** El 90 % de los bugs de usuario son de ruteo o de eco.

### Sobre Android

6. **Los FGS necesitan tipo desde Android 14.** Sin él, excepción en runtime.
7. **Doze y los OEM son el verdadero enemigo de "siempre activo".** Pide exención y documenta ajustes.
8. **`PendingIntent` inmutable** desde Android 12; no es opcional.
9. **`launchMode="singleTask"`** evita Activities duplicadas con notificaciones.
10. **El cleartext se bloquea en release.** Diseña para `wss://` desde el principio.

### Sobre el producto

11. **Un solo gesto es un requisito técnico, no una preferencia estética.** Cambia la navegación, los permisos y el modelo de estado.
12. **La fricción deliberada puede ser accesibilidad.** Confirmar en el overlay evita rechazos accidentales.
13. **El estado debe ser visual y binario.** Gris con barra diagonal comunica más que "Error de red".
14. **Documentar lo que no se hizo es parte del trabajo.** Las limitaciones explícitas generan confianza.

### Sobre el proceso

15. **Probar en dos teléfonos reales no es opcional.** El emulador no reproduce Doze, altavoz, eco ni fabricantes.
16. **Los bugs de integración aparecen en la frontera.** WebSocket + FCM + WebRTC tienen tres relojes distintos.
17. **Un guard de idempotencia cuesta cinco líneas y ahorra un postmortem.**
18. **La documentación se escribe al final, pero se piensa desde el principio.** Si no puedes explicar el flujo, el diseño está incompleto.

---

## Qué haría distinto

| Área | Qué cambiaría | Por qué |
|---|---|---|
| **Modelo de datos** | Lista de cuidadores desde el día uno | Migrar de un `String` a una lista después duele |
| **Autenticación** | Token por instalación en `register` desde la v1 | Añadirla después obliga a tocar servidor y app a la vez |
| **Tests** | Empezar por `QrLinkCodec` y los guards | Son baratos y cubren las reglas más frágiles |
| **Versionado** | `versionCode` derivado de `versionName` | Evita la desalineación actual (2 vs 1.3.0) |
| **Secretos** | Keystore desde el principio | Cambiar el almacenamiento después requiere migración |
| **Módulos** | Separar `:core` y `:app` si crece | Hoy es un solo módulo; el límite se acerca |
| **Analítica** | No añadirla | La privacidad es una feature; se mantiene sin telemetría |
| **Accesibilidad** | Probar con TalkBack antes de la v1.0 | El coste de adaptar después es mayor |
| **Notificaciones** | Diseñar el canal con nombres estables desde el inicio | Renombrar canales deja a usuarios con configuración huérfana |

---

## Deuda y próximos pasos

| Prioridad | Deuda | Esfuerzo | Impacto |
|---|---|---|---|
| Alta | Autenticación del servidor | Medio | Cierra R-01 |
| Alta | Caducidad del QR | Bajo | Reduce R-02 |
| Alta | Cifrado de secretos locales | Medio | Reduce R-03 |
| Media | Tests de `QrLinkCodec` y guards | Bajo | Evita regresiones |
| Media | Varios cuidadores | Alto | Cambia modelo y UI |
| Media | Historial local con retención | Medio | Valor para la familia |
| Media | Reconexión de medios (ICE restart) | Alto | Llamadas más robustas |
| Baja | R8 y firma de release | Bajo | APK más pequeño y distribuible |
| Baja | Limpieza de strings sin usar | Bajo | Higiene |
| Baja | `versionCode` coherente | Bajo | Higiene |
| Baja | TalkBack y `fontScale` | Medio | Accesibilidad real |

### Riesgos abiertos

| ID | Riesgo | Mitigación actual |
|---|---|---|
| R-01 | Servidor sin autenticación | Red de confianza / VPS privado |
| R-02 | QR como credencial permanente | Restablecer vínculo manual |
| R-03 | Secretos sin cifrar | Ninguna; documentado |
| R-04 | Reinicio del servidor corta llamadas | Aceptado; estado en memoria |
| R-05 | Fabricantes matan el servicio | Exención de batería + guía |
| R-06 | Sin tests | Pruebas manuales documentadas |

---

## Métricas del proyecto

| Métrica | Valor |
|---|---|
| Archivos Kotlin | 32 |
| Líneas Kotlin | ~4.175 |
| Líneas del servidor | ~230 |
| Pantallas | 6 |
| Rutas de navegación | 6 |
| Tipos de mensaje de señalización | 19 |
| Claves persistidas | 13 |
| Permisos declarados | 14 |
| Strings (ES) | ~120 |
| Idiomas | 2 |
| Dependencias directas | ~15 |
| Ciclos de desarrollo | 10 |
| Versión | 1.3.0 (`versionCode 2`) |

### Distribución del código

| Capa | Líneas aproximadas | % |
|---|---|---|
| UI | ~1.900 | 45 % |
| WebRTC / coordinación | ~950 | 23 % |
| Datos / red | ~500 | 12 % |
| Servicios | ~450 | 11 % |
| Dominio / DI / entrada | ~375 | 9 % |

La UI es la capa más grande, como es esperable en Compose. La lógica de llamada, que es la parte delicada, está concentrada en tres archivos.

---

## Glosario de incidentes

| ID | Título | Archivo afectado |
|---|---|---|
| F-001 | `SIGABRT` por doble `dispose()` | `WebRtcManager.kt` |
| F-002 | `SIGABRT` en `onDisconnected` | `CallCoordinator.kt` |
| F-003 | Auto-respuesta tras cambiar de modo | `SettingsViewModel.kt`, `CallCoordinator.kt` |
| I-001 | FCM sin `onMessageReceived` en background | `server/index.js`, `FcmService.kt` |
| I-002 | Presencia falsa al abrir la app | `server/index.js`, `CallCoordinator.kt` |
| I-003 | Llamada perdida con destino ausente | `server/index.js` |
| I-004 | Cleartext bloqueado en release | `network_security_config.xml` |
| I-005 | QR sin GMS | `app/build.gradle.kts` |
| I-006 | Servicio suspendido por Doze | `GuardService.kt`, `MainActivity.kt` |
| I-007 | Navegación duplicada a la llamada | `NavGraph.kt` |
| I-008 | Eco de audio | Documentación de uso |
| M-001 | Notificación sin acciones | `NotificationHelper.kt` |
| M-002 | Botón deshabilitado sin red | `SupervisedHomeScreen.kt` |
| M-003 | ML Kit bundled | `app/build.gradle.kts` |

---

## Cierre

Este proyecto empezó como un ejercicio de "¿se puede hacer una teleasistencia decente con dos teléfonos y WebRTC?" y terminó siendo un recordatorio de que **la parte difícil no es la videollamada, sino todo lo que la rodea**: despertar al que duerme, sobrevivir al fabricante, no cerrarse al colgar y no pedirle a una persona mayor que aprenda una app.

Las decisiones que mejor envejecieron fueron las más simples: un botón, un QR, un servidor tonto y guards de idempotencia. Las que peor envejecieron fueron las que ahorraron trabajo al principio: un solo par, sin auth y sin tests.

La siguiente versión debería empezar por ahí.

---

<div align="center">

**Alerta Mayor 1.3.0** · Bitácora de ingeniería

*Los bugs se olvidan; las lecciones se escriben.*

</div>
