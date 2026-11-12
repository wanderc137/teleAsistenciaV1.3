# Alerta Mayor — Manual de usuario

**Versión documentada:** 1.3.0 · **Package:** `com.alertamayor.app` · **Plataforma:** Android 7.0 o superior

---

## Índice

1. [Ficha técnica](#ficha-técnica)
2. [Qué es y qué no es](#qué-es-y-qué-no-es)
3. [Compatibilidad](#compatibilidad)
4. [Antes de empezar](#antes-de-empezar)
5. [Instalación](#instalación)
6. [Primer arranque: elegir modo](#primer-arranque-elegir-modo)
7. [Vincular los dos teléfonos](#vincular-los-dos-telefonos)
8. [Uso diario: teléfono supervisado](#uso-diario-teléfono-supervisado)
9. [Uso diario: teléfono supervisor](#uso-diario-teléfono-supervisor)
10. [La pantalla de llamada](#la-pantalla-de-llamada)
11. [Notificaciones y permisos](#notificaciones-y-permisos)
12. [Configuración explicada](#configuración-explicada)
13. [Indicadores de estado](#indicadores-de-estado)
14. [Auto-arranque y batería](#auto-arranque-y-batería)
15. [Idioma y tema](#idioma-y-tema)
16. [El servidor: qué es y cuándo lo necesitas](#el-servidor-qué-es-y-cuándo-lo-necesitas)
17. [Preguntas frecuentes](#preguntas-frecuentes)
18. [Solución de problemas](#solución-de-problemas)
19. [Privacidad y datos](#privacidad-y-datos)
20. [Limitaciones](#limitaciones)
21. [Glosario](#glosario)
22. [Documentos relacionados](#documentos-relacionados)

---

## Ficha técnica

| Campo | Valor |
|---|---|
| Nombre comercial | **Alerta Mayor** |
| Versión | 1.3.0 (`versionCode 2`) |
| Package | `com.alertamayor.app` |
| Tipo | Aplicación nativa Android |
| Lenguaje | Kotlin 2.0.21 |
| Interfaz | Jetpack Compose + Material 3 |
| minSdk | 24 — Android 7.0 Nougat |
| targetSdk / compileSdk | 35 — Android 15 |
| Arquitectura soportada | arm64-v8a, armeabi-v7a, x86_64 (según build) |
| Idiomas | Español (predeterminado), Inglés |
| Videollamada | WebRTC punto a punto, audio + vídeo |
| Códec de vídeo | H.264 / VP8 / VP9 (según hardware) |
| Resolución | 1280 × 720 a 30 fps |
| Señalización | WebSocket JSON sobre TLS |
| Notificaciones | Firebase Cloud Messaging (data-only) |
| Almacenamiento | DataStore Preferences (13 claves) |
| Permisos | 14 (ver tabla en el README) |
| Firmado | APK de depuración; release requiere keystore propio |

### Dependencias principales

| Componente | Versión | Para qué |
|---|---|---|
| Android Gradle Plugin | 8.9.2 | Compilación |
| Gradle | 8.13 | Build |
| Jetpack Compose BOM | 2025.01.01 | UI |
| Navigation Compose | 2.8.5 | Rutas |
| Lifecycle | 2.8.7 | Ciclo de vida y `collectAsStateWithLifecycle` |
| DataStore | 1.1.1 | Preferencias |
| OkHttp | 4.12.0 | WebSocket |
| kotlinx.serialization | 1.7.3 | JSON |
| WebRTC (Infobip) | 1.0.47617t | Medios |
| ZXing core | 3.5.3 | Generar QR |
| ML Kit barcode | 17.3.0 | Leer QR (modelo incluido) |
| CameraX | 1.4.1 | Vista de cámara |
| Firebase BOM | 33.7.0 | FCM |
| Coroutines | 1.9.0 | Concurrencia |

> Los números exactos viven en `gradle/libs.versions.toml`. Esta tabla es una foto de la versión 1.3.0.

---

## Qué es y qué no es

**Alerta Mayor** conecta dos teléfonos Android para que una persona mayor pueda pedir ayuda con un solo toque y su familiar pueda verla y hablarle de inmediato.

### Lo que sí hace

- Muestra un **botón rojo gigante** que inicia una videollamada.
- **Contesta sola** las llamadas entrantes en el teléfono del adulto mayor, con el altavoz ya activo.
- Permite al familiar **llamar cuando quiera** y ver el estado del otro teléfono.
- Vincula los dos teléfonos con un **código QR**, sin cuentas ni contraseñas.
- Funciona en **español e inglés**, con tema claro, oscuro o del sistema.
- Se **reinicia sola** tras un apagón o reinicio del teléfono.
- Sigue **en guardia** con la pantalla apagada, mediante un servicio en primer plano.

### Lo que no hace

- **No llama al 112 ni a una central de emergencias.** Solo llama al teléfono vinculado.
- **No detecta caídas** ni monitoriza signos vitales.
- **No graba** audio ni vídeo.
- **No guarda historial** de llamadas.
- **No admite varios cuidadores** por teléfono.
- **No funciona sin internet** (Wi-Fi o datos).
- **No sustituye** un servicio profesional de teleasistencia.

---

## Compatibilidad

| Versión de Android | Nombre | Estado | Notas |
|---|---|---|---|
| 7.0 – 7.1 | Nougat | Soportado | Mínimo técnico (`minSdk 24`) |
| 8.0 – 8.1 | Oreo | Soportado | Canales de notificación |
| 9 | Pie | Soportado | — |
| 10 | Q | Soportado | — |
| 11 | R | Soportado | — |
| 12 – 12L | S | Soportado | `PendingIntent` inmutable obligatorio |
| 13 | Tiramisú | Soportado | Permiso de notificaciones en ejecución |
| 14 | Upside Down Cake | Soportado | Tipos de servicio en primer plano |
| 15 | Vanilla Ice Cream | Soportado | `targetSdk 35` |
| 16+ | — | No verificado | Se espera compatible, sin pruebas |

### Requisitos del dispositivo

- Cámara (frontal recomendada) y micrófono.
- Altavoz.
- Wi-Fi o datos móviles.
- Al menos **150 MB libres** (instalación + datos).
- Batería de al menos 3.000 mAh recomendada para el teléfono del mayor.

### Funciones que dependen del teléfono

| Función | Dependencia | Si falta |
|---|---|---|
| Escanear QR | Cámara + ML Kit incluido | No se puede vincular por QR (requiere cámara) |
| Vídeo | Cámara frontal | La llamada puede seguir solo con audio |
| Notificación de emergencia | Google Play Services + FCM | Solo funciona con la app abierta o el socket vivo |
| Pantalla completa sobre bloqueo | `USE_FULL_SCREEN_INTENT` | La notificación aparece sin abrir pantalla |
| Per-app language | Android 13+ o backport de AppCompat | El selector usa el backport, funciona en versiones anteriores |

---

## Antes de empezar

Necesitas:

1. **Dos teléfonos Android** con Android 7.0 o superior.
2. **El APK** de Alerta Mayor instalado en ambos.
3. **El servidor de señalización** en ejecución (LAN o VPS) y su URL.
4. **Conexión a internet** en ambos teléfonos.
5. **Cinco minutos** y las dos personas juntas para la vinculación inicial.

> Si no tienes servidor, la app abre y muestra la interfaz, pero **no podrá llamar**: la pantalla indicará "Desconectado".

### Decidir quién es quién

| Rol | Quién lo usa | Qué pantalla ve |
|---|---|---|
| **Supervisado** | La persona mayor | Botón rojo SOS o QR |
| **Supervisor** | El familiar o cuidador | Escanear QR + tarjeta + Llamar |

Un teléfono no puede ser las dos cosas a la vez. Si te equivocas, puedes cambiar el modo en Configuración sin reinstalar.

---

## Instalación

### Opción A — Desde el APK (recomendada para usuarios)

1. Copia `app-debug.apk` al teléfono (cable, nube o mensajería).
2. Abre el archivo desde el gestor de archivos.
3. Android pedirá permitir la instalación desde esa fuente: **Permitir**.
4. Pulsa **Instalar** y luego **Abrir**.

> Si el sistema bloquea la instalación por "origen desconocido", actívalo en Ajustes → Seguridad → Instalar aplicaciones desconocidas, para la app con la que abriste el APK.

### Opción B — Desde un ordenador con `adb`

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.alertamayor.app/com.alertamayor.app.MainActivity
```

Con dos teléfonos conectados:

```bash
adb -s <SERIAL_1> install -r app-debug.apk
adb -s <SERIAL_2> install -r app-debug.apk
```

### Después de instalar

Al abrir por primera vez, la app pedirá:

1. **Permiso de notificaciones** (Android 13+). Acepta: sin él no se ve la alerta de llamada.
2. **Exención de optimización de batería.** Acepta: es lo que permite que el teléfono siga en guardia.
3. **Cámara y micrófono.** Se piden al entrar en la pantalla principal o al abrir el escáner.

Puedes cambiar estos permisos luego en Ajustes → Aplicaciones → Alerta Mayor → Permisos.

---

## Primer arranque: elegir modo

La primera pantalla pregunta **"¿Cómo usarás la app?"** con dos tarjetas:

| Tarjeta | Color | Elige esta si… |
|---|---|---|
| **Modo Supervisado** | Rojo | Es el teléfono de la persona mayor |
| **Modo Supervisor** | Azul | Es el teléfono del familiar o cuidador |

Al pulsar una, la elección se guarda y no vuelve a preguntarse. La app abre directamente en la pantalla del rol.

> **Importante:** el modo no cambia el hardware ni el número de teléfono, solo la interfaz y el comportamiento de las llamadas. Puedes cambiarlo después en Configuración → Modo.

### Qué pasa si me equivoco

Configuración → **Modo** → elige el otro. El cambio se aplica en caliente:
- El coordinador de llamadas deja de auto-responder si pasas a Supervisor.
- Las pantallas se adaptan al nuevo rol.
- El vínculo existente **se conserva**.

---

## Vincular los dos teléfonos

La vinculación se hace **una sola vez**. Necesitas los dos teléfonos delante.

### En el teléfono supervisado

1. Si aún no hay vínculo, la pantalla muestra un **código QR** grande.
2. Debajo aparece el texto "Muestra este código QR a tu familiar".
3. No toques nada: el QR debe quedar visible y con brillo suficiente.

### En el teléfono supervisor

1. Pulsa **Escanear QR para vincular**.
2. Concede el permiso de cámara si lo pide.
3. Apunta al QR del otro teléfono, a unos 20–30 cm.
4. Cuando lo detecte, la pantalla vuelve y se abre un diálogo.
5. Escribe un nombre reconocible: **"Mamá"**, **"Abuela"**, **"Papá"**, **"Tía Rosa"**.
6. Pulsa **Guardar**.

### Qué ocurre por dentro

1. El supervisor guarda el identificador del otro teléfono.
2. Envía un mensaje `link` con su propio identificador y el nombre elegido.
3. El teléfono del mayor recibe el mensaje, guarda el vínculo y **cambia el QR por el botón rojo**.
4. El supervisor ve la tarjeta con el nombre y el estado del dispositivo.

### Cómo saber que funcionó

| Teléfono | Señal de éxito |
|---|---|
| Supervisado | Desaparece el QR y aparece la esfera roja con "SOS" |
| Supervisor | Aparece la tarjeta con el nombre y el botón **Llamar** |

### Si el QR no se lee

- Limpia la lente de la cámara.
- Sube el brillo del teléfono que muestra el QR.
- Acerca o aleja el teléfono lentamente.
- Evita reflejos en la pantalla.
- Comprueba que el QR se ve completo, sin recortes.

### Volver a vincular

Configuración → **Restablecer vínculo** → confirmar.

Esto:
- Borra el par guardado.
- **Genera un identificador nuevo.**
- Hace que el QR anterior deje de ser válido.
- No borra el nombre del dispositivo ni el modo.

Después hay que repetir el escaneo con el QR nuevo.

---

## Uso diario: teléfono supervisado

### La pantalla normal

El teléfono del adulto mayor muestra:

- **Fondo negro.**
- Una **esfera roja de 200 dp** en el centro, con brillo y sombra.
- El texto **SOS** en amarillo debajo.
- Un **icono de red** arriba a la izquierda.
- Un **engranaje** pequeño arriba a la derecha.
- Una etiqueta que indica que está **"Actualmente Supervisado"**.

La pantalla **no se apaga** mientras la app está abierta. El botón **Atrás** está bloqueado para que no se salga por accidente.

### Pedir ayuda

1. Toca la esfera roja.
2. Notarás una **vibración**.
3. La app marca al familiar vinculado.
4. Cuando el familiar acepta, se abre la videollamada.
5. El **altavoz ya está activo**: solo hay que hablar.

No hay que aceptar nada, ni desbloquear, ni buscar contactos.

### Recibir una llamada del familiar

La app **responde automáticamente**:

1. Suena la llamada.
2. Se abre la pantalla de vídeo.
3. El altavoz se activa.
4. La cámara se enciende.

La persona mayor no tiene que tocar el teléfono.

### Si no hay internet

- El icono de red cambia a **sin conexión** (rojo).
- La esfera se vuelve **gris con una barra diagonal**.
- El botón **no responde** a toques.
- Cuando vuelve la conexión, se recupera en **4 segundos o menos**.

Esto evita dejar a la persona en un "Llamando…" que nunca termina.

### Si aún no está vinculado

En lugar del botón se muestra el **QR**. Hay que vincular antes de poder llamar.

---

## Uso diario: teléfono supervisor

### La pantalla normal

- Fondo negro.
- Botón **Escanear QR para vincular**.
- **Tarjeta** del dispositivo vinculado con:
  - Nombre ("Mamá").
  - Estado: **En línea** (verde) o **Sin conexión** (gris).
  - Botón **Llamar** (rojo).

### Llamar al adulto mayor

1. Comprueba que la tarjeta dice **En línea**.
2. Pulsa **Llamar**.
3. Verás el estado "Llamando…".
4. El otro teléfono responde solo.
5. Se abre el vídeo con el altavoz activo en el lado del mayor.

> Si dice "Sin conexión", el teléfono del mayor está apagado, sin internet o la app fue cerrada por el sistema. Puedes intentarlo igualmente: si FCM está configurado, el push puede despertarlo.

### Recibir una emergencia

1. El teléfono suena con **tono de alarma** y vibra de forma larga.
2. Aparece una **notificación a pantalla completa**.
3. Se muestra un **overlay** con **Aceptar** y **Rechazar**.
4. Pulsa **Aceptar** para entrar en la llamada.

Detalles:

- La notificación **no tiene botones**: la decisión se toma en el overlay, para evitar toques accidentales.
- Si no contestas en **30 segundos**, deja de sonar.
- El vídeo se abre con la cámara del mayor ya encendida.

### Rechazar una llamada

Pulsa **Rechazar** en el overlay. El emisor ve "Llamada rechazada" y vuelve a su pantalla.

### Desvincular

Configuración → **Restablecer vínculo**. Se borra el par y se genera un identificador nuevo; el QR anterior deja de servir.

---

## La pantalla de llamada

### Distribución

| Zona | Contenido |
|---|---|
| Fondo | Vídeo de la otra persona a pantalla completa |
| Esquina | Tu cámara en una miniatura de 140 dp, **arrastrable** |
| Arriba | Estado de la llamada y temporizador `mm:ss` |
| Abajo | Controles |

### Controles

| Botón | Qué hace |
|---|---|
| 🎤 | Silencia o reactiva tu micrófono |
| 🔊 | Cambia entre altavoz y auricular |
| 🔄 | Cambia entre cámara frontal y trasera |
| 📴 | Cuelga y vuelve a la pantalla principal |

### Estados que verás

| Estado | Significado |
|---|---|
| **Llamando…** | Se envió la petición; esperando respuesta |
| **Sonando** | El otro teléfono está timbrando |
| **Conectando** | Se está negociando el vídeo |
| **En llamada** | Audio y vídeo activos; corre el temporizador |
| **Finalizada** | La llamada terminó; la pantalla se cierra en ~1 segundo |

### Supervisado vs. supervisor en la llamada

- **Supervisado**: nunca ve botones de aceptar; la llamada entra directamente.
- **Supervisor**: cuando la llamada es entrante, ve el overlay **Aceptar / Rechazar** antes de entrar.

### Si la llamada se corta

- La app vuelve sola a la pantalla principal.
- No se queda bloqueada.
- Puedes volver a llamar inmediatamente.

---

## Notificaciones y permisos

### Notificaciones que envía la app

| Notificación | Cuándo | Comportamiento |
|---|---|---|
| **Llamada en curso** | Durante una llamada | Persistente, no se puede descartar |
| **Llamada entrante** | El otro teléfono llama | Pantalla completa, alarma, vibración, 30 s |
| **En guardia** | Siempre que la app está activa | Silenciosa, indica que sigue conectada |

### Permisos y para qué sirven

| Permiso | Uso | ¿Obligatorio? |
|---|---|---|
| Cámara | Videollamada y escáner QR | Sí para vídeo y QR |
| Micrófono | Voz en la llamada | Sí para hablar |
| Notificaciones | Aviso de emergencia | Muy recomendable |
| Batería (exención) | Seguir en guardia | Muy recomendable |
| Auto-arranque | Arrancar tras reiniciar | Recomendable |

### Cómo comprobar que las notificaciones están bien

1. Ajustes → Aplicaciones → Alerta Mayor → Notificaciones.
2. Verifica que el canal **Llamadas** está activo y con sonido.
3. Comprueba que no está en modo "Silenciar todo".
4. En Android 13+, confirma que el permiso está concedido.

---

## Configuración explicada

| Opción | Qué hace | Valores |
|---|---|---|
| **Idioma** | Cambia toda la interfaz al instante | Español, English |
| **Modo** | Cambia el rol y el comportamiento | Supervisado, Supervisor |
| **Tema** | Apariencia | Sistema, Claro, Oscuro |
| **URL del servidor** | Dirección del WebSocket | `wss://…` o `ws://…` (solo debug/LAN) |
| **Servidor TURN — URL** | Relay para redes difíciles | `turn:TU-DOMINIO:3478?transport=tcp` |
| **Servidor TURN — usuario** | Credencial del TURN | Texto |
| **Servidor TURN — contraseña** | Credencial del TURN | Texto (se guarda sin cifrar) |
| **Vínculo** | Muestra el ID y el par actual | Solo lectura |
| **Restablecer vínculo** | Borra el par y genera un UUID nuevo | Acción irreversible |

### Cuándo tocar cada cosa

- **URL del servidor**: al cambiar de red o de servidor. Se aplica en caliente.
- **TURN**: solo si las llamadas no conectan en datos móviles o en otra red Wi-Fi. Sin TURN, la app funciona en la misma red o con NAT permisivo.
- **Restablecer vínculo**: al cambiar de teléfono o si sospechas que alguien tiene tu QR.
- **Tema e idioma**: preferencias personales; no afectan a las llamadas.

### Aviso sobre `ws://`

El build de **release** bloquea las conexiones sin cifrar. Si escribes `ws://` en una versión release, la conexión fallará. Usa `wss://` siempre que el servidor esté fuera de tu red local. En el build de depuración se permite `ws://` solo para direcciones privadas declaradas al compilar.

---

## Indicadores de estado

### En la pantalla del supervisado

| Icono | Significado |
|---|---|
| 📶 Wi-Fi | Conectado por Wi-Fi |
| 📱 Datos | Conectado por red móvil |
| 🚫 Sin conexión (rojo) | No hay internet validado |
| Esfera roja | Listo para llamar |
| Esfera gris con barra | Sin conexión: botón inhabilitado |
| QR | Todavía no hay vínculo |

### En la pantalla del supervisor

| Estado | Significado |
|---|---|
| 🟢 **En línea** | El teléfono del mayor tiene el socket conectado |
| ⚪ **Sin conexión** | No está registrado en el servidor ahora mismo |
| "Llamando…" | Esperando aceptación |
| "Tienes una llamada entrante" | Overlay con Aceptar / Rechazar |

### En Configuración

| Campo | Significado |
|---|---|
| **ID del dispositivo** | UUID que identifica este teléfono |
| **Vinculado con** | Nombre del otro teléfono |
| **URL actual** | Servidor en uso |

> El estado "En línea" es una foto del momento. Puede tardar unos segundos en actualizarse tras abrir o cerrar la app en el otro teléfono.

---

## Auto-arranque y batería

### Cómo se mantiene activa

La app usa dos mecanismos:

1. **Servicio en primer plano de guardia** (`GuardService`): mantiene la conexión y muestra una notificación discreta.
2. **Wake lock parcial**: evita que el teléfono duerma por completo la conexión.

Además, al reiniciar el teléfono, un receptor de `BOOT_COMPLETED` vuelve a abrir la app.

### Qué debes hacer en el teléfono del mayor

1. **Concede la exención de optimización de batería** cuando la app lo pida.
2. En **Huawei / Honor**: Ajustes → Batería → Inicio de aplicaciones → Alerta Mayor → **Administrar manualmente** (autoarranque, secundaria, ejecución en segundo plano).
3. En **Xiaomi / Redmi / POCO**: Ajustes → Aplicaciones → Alerta Mayor → **Autostart** activado y **Ahorro de batería: sin restricciones**.
4. En **Samsung**: Ajustes → Batería → Límites de uso en segundo plano → **Nunca suspender**.
5. En **Oppo / Realme / OnePlus**: Ajustes → Batería → Optimización → Alerta Mayor → **No optimizar**.
6. Deja el teléfono **enchufado** si va a estar fijo en una habitación.

### Cómo comprobar que sigue en guardia

- La notificación "En guardia" debe estar visible.
- En el teléfono del familiar, la tarjeta debe decir **En línea**.
- Si dice "Sin conexión" estando el otro teléfono encendido, revisa la batería y la red.

### Consumo estimado

| Situación | Consumo |
|---|---|
| En espera, pantalla apagada | 1–3 % por hora |
| En llamada | 8–15 % por hora |
| Pantalla encendida sin llamada | 5–10 % por hora |

---

## Idioma y tema

### Cambiar idioma

Configuración → **Idioma** → Español o English.

- El cambio es **inmediato**, sin reiniciar.
- Se guarda y se aplica en el próximo arranque.
- La marca "Alerta Mayor" y los textos técnicos no se traducen.
- En Android 13+, el idioma también aparece en los ajustes del sistema para esta app.

### Cambiar tema

Configuración → **Tema** → Sistema, Claro u Oscuro.

- **Sistema** sigue la configuración de Android (incluido el modo oscuro automático).
- Las pantallas principales (SOS y llamada) son **siempre negras** por diseño, para no deslumbrar de noche y maximizar el contraste del botón.

---

## El servidor: qué es y cuándo lo necesitas

La app **no se conecta directamente** entre teléfonos para "encontrarse": necesita un servidor de señalización que diga a cada uno dónde está el otro y les pase los mensajes de negociación.

### Qué necesitas

| Escenario | Servidor | TURN |
|---|---|---|
| Probar en casa, misma Wi-Fi | PC con `node server/index.js` | No |
| Uso diario en la misma casa | Un mini-PC o Raspberry Pi en la red | No suele hacer falta |
| Familiar fuera de casa / datos móviles | VPS con dominio y TLS | Muy recomendable |
| Uso real a distancia | VPS + dominio + TURN | Sí |

### Cómo se configura en la app

1. Abre Configuración.
2. En **URL del servidor** escribe la dirección completa:
   - LAN: `ws://mi-pc.local:3000`
   - Internet: `wss://TU-DOMINIO.com`
3. Pulsa **Guardar**.
4. Comprueba que el estado pasa a conectado.

### Qué pasa si el servidor se cae

- Las llamadas en curso **se cortan** (el servidor no transporta medios, pero sin señalización no hay reconexión).
- La app reintenta cada 3 segundos.
- Al volver el servidor, se reconecta sola y re-registra el dispositivo.

### Qué NO pasa por el servidor

- Tu voz.
- Tu imagen.
- Grabaciones.

El servidor solo reenvía mensajes de sesión (quién llama, claves de negociación y direcciones candidatas). El audio y el vídeo viajan **cifrados directamente entre los teléfonos**, salvo que se configure TURN, en cuyo caso pasan por el relay también cifrados.

> La guía de despliegue completa está en [vps-deployment.md](vps-deployment.md).

---

## Preguntas frecuentes

**¿Necesito crear una cuenta?**
No. No hay registro, ni email, ni contraseña. La identidad es un identificador aleatorio que se intercambia por QR.

**¿Puedo usar la app entre tres o más familiares?**
No en esta versión. Cada teléfono guarda un único vínculo. Para cambiar de cuidador hay que restablecer el vínculo y escanear de nuevo.

**¿Se puede usar en una tablet?**
Sí, siempre que tenga cámara y Android 7.0+. La interfaz está pensada para teléfono en vertical.

**¿Funciona sin Google Play Services?**
La app y el escaneo QR funcionan. Las notificaciones push (FCM) **no**. Sin FCM, la llamada solo llega si la app del mayor mantiene el socket abierto.

**¿Cuánto datos consume una llamada?**
Entre 0,5 y 1,5 Mbps por sentido. Una llamada de 10 minutos consume aproximadamente 40–100 MB.

**¿Se graban las llamadas?**
No. No hay grabación local ni en el servidor.

**¿Puedo ver el historial de llamadas?**
No. La versión 1.3.0 no guarda historial.

**¿Qué pasa si el teléfono del mayor está apagado?**
No se puede llamar. El supervisor verá "Sin conexión". Si FCM está configurado y el teléfono está encendido pero con la app dormida, el push puede despertarla.

**¿Qué pasa si no hay internet?**
El botón se deshabilita y no se inicia la llamada. Es intencional.

**¿Puedo cambiar el nombre del otro teléfono?**
Sí, volviendo a vincular: Restablecer vínculo y escanear de nuevo con otro nombre.

**¿La app escucha o graba en segundo plano?**
No. El micrófono y la cámara solo se activan durante una llamada, y Android lo indica con el indicador de privacidad.

**¿Por qué pide permiso para ignorar la optimización de batería?**
Porque los fabricantes suspenden apps en segundo plano y la app debe seguir en guardia para recibir una emergencia. Es una exención, no un permiso para consumir batería sin control.

**¿Puedo instalar la app en el teléfono del mayor y usar el mío como supervisor con otra versión?**
Ambos deben usar la misma versión del protocolo. En la práctica, instala la **misma build** en los dos.

**¿Cómo se actualiza?**
Instalando el APK nuevo con `adb install -r` o desde el gestor de archivos. Los datos y el vínculo se conservan si el `applicationId` y la firma coinciden.

---

## Solución de problemas

### La app no conecta

| Comprobación | Cómo |
|---|---|
| ¿El servidor está encendido? | `curl http://IP:3000/health` debe responder `ok` |
| ¿La URL es correcta? | Debe empezar por `ws://` (LAN/debug) o `wss://` (Internet) |
| ¿El teléfono llega al servidor? | Abre la URL de health en el navegador del teléfono |
| ¿Hay firewall? | `sudo ufw allow 3000/tcp` en el servidor |
| ¿Es build release con `ws://`? | Cambia a `wss://` o usa build debug |

### No aparece la notificación de llamada

1. Ajustes → Apps → Alerta Mayor → Notificaciones → canal **Llamadas** activo.
2. Permiso de notificaciones concedido (Android 13+).
3. Exención de batería concedida.
4. Comprueba que `google-services.json` y la clave del servidor son del **mismo proyecto** de Firebase.

### La cámara no funciona

- Revisa el permiso en Ajustes → Apps → Alerta Mayor → Permisos → Cámara.
- Cierra otras apps que estén usando la cámara.
- Reinicia el teléfono si la cámara quedó bloqueada por otra app.

### No se oye nada

- Comprueba el volumen multimedia (no el de llamada).
- Verifica que el micrófono no está silenciado en la pantalla de llamada.
- Prueba a activar y desactivar el altavoz.
- En el teléfono del mayor, el altavoz se activa solo; si usas auricular Bluetooth, puede cambiar el ruteo.

### La llamada se queda en "Conectando"

- Suele ser un problema de NAT: configura TURN.
- Prueba primero en la misma red Wi-Fi para descartar el servidor.
- Revisa que ambos teléfonos tengan internet estable.

### El QR no se lee

- Limpia la cámara.
- Sube el brillo del teléfono que muestra el QR.
- Mantén el teléfono quieto 2–3 segundos.
- Comprueba que el QR está completo en pantalla.

### La app se cierra sola

- Actualiza a la última versión.
- Borra datos de la app (perderás el vínculo) y vuelve a vincular.
- Revisa el espacio libre.
- Si persiste, recoge el log: `adb logcat | grep -Ei "FATAL|AndroidRuntime"`.

### Se desconecta al rato

- Concede la exención de batería.
- Revisa los ajustes del fabricante (ver [Auto-arranque y batería](#auto-arranque-y-batería)).
- Mantén el teléfono enchufado.

---

## Privacidad y datos

### Qué se guarda en el teléfono

| Dato | Dónde | Se puede borrar |
|---|---|---|
| Identificador del dispositivo | DataStore | Sí, Restablecer vínculo |
| Nombre del par | DataStore | Sí |
| Modo, tema, idioma | DataStore | Sí, borrando datos |
| Token FCM | DataStore | Sí |
| Credenciales TURN | DataStore (sin cifrar) | Sí |

### Qué ve el servidor

- Identificadores de dispositivo conectados.
- Tokens FCM.
- Mensajes de señalización (quién llama a quién, SDP, candidatos ICE).
- **No** ve audio ni vídeo.
- **No** guarda historial en base de datos (todo en memoria).

### Qué ve Firebase

- El token del dispositivo.
- El contenido del push: `type`, `fromDeviceId`, `fromName`.

### Buenos hábitos

1. No publiques capturas del QR ni del identificador.
2. Restablece el vínculo si regalas o pierdes el teléfono.
3. Usa `wss://` en producción.
4. No compartas `google-services.json` ni `serviceAccountKey.json`.
5. Revisa los permisos después de cada actualización.

---

## Limitaciones

1. Solo un vínculo por teléfono.
2. El QR no caduca: es la credencial.
3. Sin historial ni grabación.
4. Sin detección de caídas.
5. Requiere internet en ambos lados.
6. Sin cifrado en reposo para token y TURN.
7. Sin tests automatizados; las pruebas son manuales.
8. Sin firma de release incluida.
9. La accesibilidad con TalkBack no está probada a fondo.
10. Depende de Google para STUN y FCM.

---

## Glosario

| Término | Significado |
|---|---|
| **Supervisado** | Teléfono de la persona mayor |
| **Supervisor** | Teléfono del familiar o cuidador |
| **Señalización** | Intercambio de mensajes para coordinar una llamada |
| **SDP** | Descripción de la sesión (códecs, direcciones, cifrado) |
| **ICE** | Mecanismo para encontrar una ruta de red entre dos dispositivos |
| **STUN** | Servidor que ayuda a descubrir la dirección pública |
| **TURN** | Servidor que retransmite medios cuando no hay ruta directa |
| **FCM** | Firebase Cloud Messaging, el servicio de push de Google |
| **UUID** | Identificador único aleatorio |
| **DataStore** | Almacenamiento de preferencias de Android |
| **Foreground service** | Servicio con notificación visible que Android no suspende fácilmente |
| **Doze** | Modo de ahorro de energía de Android |
| **WSS** | WebSocket sobre TLS (cifrado) |

---

## Documentos relacionados

| Documento | Para qué |
|---|---|
| [README](../README.md) | Visión general, arquitectura y compilación |
| [developer.md](developer.md) | Detalle técnico y guía de extensión |
| [vps-deployment.md](vps-deployment.md) | Poner el servidor en Internet con TLS y TURN |
| [REFERENCES.md](REFERENCES.md) | Estándares y documentación oficial |
| [BITACORA.md](BITACORA.md) | Decisiones de ingeniería y lecciones |

---

<div align="center">

**Alerta Mayor 1.3.0** · Manual de usuario
Si algo de este documento no coincide con la app, la app tiene razón: abre una incidencia.

</div>
