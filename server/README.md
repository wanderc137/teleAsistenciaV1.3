# Servidor de señalización — Alerta Mayor

Servidor de señalización WebRTC para la app **Alerta Mayor**. Es un servidor
WebSocket minimalista en **Node.js + ws** que registra dispositivos por `deviceId`
y enruta los mensajes SDP/ICE entre pares vinculados.

## Levantar el servidor

Requisitos: Node.js >= 18 y npm.

```bash
cd server
npm install
node index.js
```

Por defecto escucha en `0.0.0.0:3000`. Puedes cambiar el puerto con la variable de entorno `PORT`.

```bash
PORT=8080 node index.js
```

### Requisitos de red

- El PC Linux y los teléfonos deben estar en la **misma red WiFi**.
- El firewall del PC debe permitir conexiones entrantes en el puerto 3000
  (TCP). Ejemplo con `ufw`: `sudo ufw allow 3000/tcp`.
- La IP del PC se configura en la app en **Configuración → URL del servidor**
  (por defecto `ws://192.0.2.200:3000`, sobrescribible también vía
  `local.properties` con la propiedad `signalingServerUrl`).

## Protocolo JSON

Todos los mensajes se intercambian como JSON con esta forma:

```json
{
  "type": "<tipo>",
  "from": "<deviceId emisor>",
  "to": "<deviceId destino>",
  "payload": { "...": "..." }
}
```

### Tipos de mensaje

| type             | Dirección                | Propósito                                        |
|------------------|--------------------------|--------------------------------------------------|
| `register`       | cliente → servidor       | Registrar el `deviceId` (en `payload.deviceId`)  |
| `registered`     | servidor → cliente       | Confirmación de registro                         |
| `call`           | cliente → destino        | Solicitud de llamada / timbre                    |
| `call-accepted`  | destino → emisor         | Aceptar la llamada                               |
| `call-rejected`  | destino → emisor         | Rechazar la llamada                              |
| `offer`          | emisor → destino         | SDP offer (payload.sdp)                          |
| `answer`         | destino → emisor         | SDP answer (payload.sdp)                         |
| `ice-candidate`  | cliente → destino        | Candidato ICE (trickle)                          |
| `end-call`       | cliente → destino        | Colgar / finalizar la llamada                    |
| `peer-unavailable` | servidor → emisor      | El destino no está registrado                    |
| `peer-online`    | servidor → todos         | Un dispositivo se registró                       |
| `peer-offline`   | servidor → todos         | Un dispositivo se desconectó                     |
| `error`          | servidor → cliente       | Error genérico                                   |

### Enrutamiento

El servidor reenvía cualquier mensaje con campo `to` al socket del destino
registrado. Si el destino no está conectado, responde `peer-unavailable` al emisor.
La app cliente filtra `peer-online`/`peer-offline` por su par vinculado.

## Notas de producción

- Para llamadas entre redes distintas o NAT estricto se requiere un servidor
  **TURN** (p. ej. `coturn`). En la misma red WiFi no es necesario.
- La detección de llamadas entrantes con la app en segundo plano requiere
  **FCM (push)** en una fase posterior; esta fase asume la app en primer plano
  o con el Foreground Service activo.
