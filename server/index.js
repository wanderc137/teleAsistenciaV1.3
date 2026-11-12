/**
 * Servidor de señalización WebRTC — Alerta Mayor
 * ----------------------------------------------
 * Node.js + ws (WebSocket).
 *
 * Funciones:
 *  - `register(deviceId, socket)`: mantiene un mapa `Map<deviceId, WebSocket>`.
 *  - Enrutamiento de mensajes (`call`, `offer`, `answer`, `ice-candidate`, `end-call`)
 *    desde el emisor al socket del par de destino (`to`).
 *  - Gestión de desconexiones (limpia el mapa y notifica `peer-offline`/`peer-unavailable`).
 *
 * Levantar:
 *   cd server
 *   npm install
 *   node index.js        # escucha en 0.0.0.0:3000
 */

const fs = require('fs');
const http = require('http');
const https = require('https');
const WebSocket = require('ws');

const PORT = process.env.PORT || 3000;
const TLS_CERT_PATH = process.env.TLS_CERT_PATH;
const TLS_KEY_PATH = process.env.TLS_KEY_PATH;

// Firebase Cloud Messaging: se usa para despertar al Supervisor (hijo) con un push
// de alta prioridad cuando el anciano pulsa ALERTA y el WebSocket del Supervisor
// está caído (app en segundo plano o suspendida por el sistema).
let admin = null;
let messaging = null;
try {
  admin = require('firebase-admin');
  const serviceAccount = require('./serviceAccountKey.json');
  // firebase-admin v14 exporta credential a nivel raíz (admin.cert); v12 usa admin.credential.cert
  const certFn = (admin.cert || (admin.credential && admin.credential.cert));
  const app = admin.initializeApp({
    credential: certFn(serviceAccount),
  });
  // v14: getMessaging desde 'firebase-admin/messaging'; v12: admin.messaging()
  try {
    const m = require('firebase-admin/messaging');
    messaging = m.getMessaging(app);
  } catch (_) {
    messaging = admin.messaging(app);
  }
  console.log('FCM inicializado');
} catch (e) {
  console.warn('FCM no inicializado (sin serviceAccountKey.json o error de red):', e.message);
}

// Mapa deviceId -> WebSocket (reemplaza conexiones duplicadas)
const clients = new Map();

// Mapa deviceId -> fcmToken (se conserva aunque el WebSocket esté caído,
// para poder enviar push de alta prioridad al destino).
const fcmTokens = new Map();

// Cola de mensajes `call` pendientes por deviceId destino. Cuando el destino no
// está conectado y se le envía push FCM, el call se guarda aquí para re-entregarlo
// cuando el dispositivo reconecte (evita que el llamador quede en DIALING infinito).
const pendingCalls = new Map();

// --- Servidor HTTP/HTTPS con TLS opcional ---
// Si TLS_CERT_PATH y TLS_KEY_PATH están definidos, se sirve wss:// directo.
// Si no, se sirve ws:// (modo local) y el proxy (Nginx/Caddy) termina el TLS.
let server;
let scheme = 'ws';
if (TLS_CERT_PATH && TLS_KEY_PATH) {
  server = https.createServer({
    cert: fs.readFileSync(TLS_CERT_PATH),
    key: fs.readFileSync(TLS_KEY_PATH),
  });
  scheme = 'wss';
} else {
  server = http.createServer();
}

// Health check opcional (útil para Nginx/Caddy y monitores)
server.on('request', (req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ok');
  }
});

const wss = new WebSocket.Server({ server });

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Servidor de señalización (${scheme}) escuchando en 0.0.0.0:${PORT}`);
});

function send(ws, message) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(message));
  }
}

function broadcastOnline(deviceId) {
  for (const [, socket] of clients) {
    send(socket, { type: 'peer-online', from: deviceId });
  }
}

function broadcastOffline(deviceId) {
  for (const [, socket] of clients) {
    send(socket, { type: 'peer-offline', from: deviceId });
  }
}

wss.on('connection', (socket) => {
  console.log('Nueva conexión');

  let deviceId = null;

  socket.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch (e) {
      console.warn('Mensaje JSON inválido:', raw.toString());
      return;
    }

    const { type, from, to, payload } = msg;
    console.log(`MSG ${type} from=${from} to=${to || '-'}`);

    // ---- Registro ----
    if (type === 'register') {
      deviceId = (payload && payload.deviceId) || from;
      if (!deviceId) {
        send(socket, { type: 'error', from: 'server', payload: 'deviceId requerido' });
        return;
      }
      // Reemplaza conexión duplicada
      const previous = clients.get(deviceId);
      if (previous && previous !== socket) {
        try { previous.close(); } catch (_) {}
      }
      clients.set(deviceId, socket);
      // Si el registro trae el token FCM, lo guardamos para push de alta prioridad.
      if (payload && payload.fcmToken) {
        fcmTokens.set(deviceId, payload.fcmToken);
        console.log(`FCM token guardado en register para ${deviceId}`);
      }
      send(socket, { type: 'registered', from: 'server', payload: deviceId });
      broadcastOnline(deviceId);
      // Ciclo 11: al registrarse, informar al dispositivo de la lista de pares
      // conectados (para que el estado "En línea" sea correcto aunque el par se
      // conectara antes que este dispositivo).
      send(socket, { type: 'peers-online', from: 'server', payload: { deviceIds: Array.from(clients.keys()) } });
      console.log(`Dispositivo registrado: ${deviceId} (${clients.size} conectados)`);
      // I-004 (v1.2): re-entregar llamada pendiente si el destino reconectó
      // tras recibir el push FCM (o tras estar desconectado).
      const pending = pendingCalls.get(deviceId);
      if (pending) {
        pendingCalls.delete(deviceId);
        send(socket, { ...pending });
        console.log(`Call pendiente re-entregado a ${deviceId}`);
      }
      return;
    }

    // ---- Registro de token FCM ----
    if (type === 'register-fcm') {
      const id = from || deviceId;
      const token = payload && payload.token;
      if (id && token) {
        fcmTokens.set(id, token);
        console.log(`FCM token registrado para ${id}`);
      }
      send(socket, { type: 'registered', from: 'server', payload: 'fcm' });
      return;
    }

    // ---- Enrutamiento al destino `to` ----
    if (to) {
      const targetSocket = clients.get(to);
      if (!targetSocket) {
        // El destino no está conectado: si es una llamada y tiene token FCM,
        // enviar push de alta prioridad para despertar la app.
        if (type === 'call') {
          const fcmToken = fcmTokens.get(to);
          const fromName = (payload && payload.fromName) || from || deviceId;
          // Guardar el call pendiente para re-entregarlo cuando el destino reconecte.
          pendingCalls.set(to, { type: 'call', from: from || deviceId, to: to, payload: { fromName: String(fromName) } });
          if (fcmToken && messaging) {
            messaging.send({
              token: fcmToken,
              data: {
                type: 'call',
                fromDeviceId: from || deviceId,
                fromName: String(fromName),
              },
              android: { priority: 'high' },
            }).then(() => {
              console.log(`Push FCM de llamada enviado a ${to}`);
            }).catch((err) => {
              console.error(`Error enviando push FCM a ${to}:`, err.message);
              send(socket, { type: 'peer-unavailable', from: 'server', to: from || deviceId, payload: to });
            });
            return;
          }
        }
        // Sin destino ni push disponible
        send(socket, { type: 'peer-unavailable', from: 'server', to: from || deviceId, payload: to });
        return;
      }
      send(targetSocket, { ...msg, from: from || deviceId });
      return;
    }

    // ---- Sin destino: respuesta directa (p. ej. error genérico) ----
    send(socket, { ...msg, from: from || deviceId });
  });

  socket.on('close', () => {
    if (deviceId && clients.get(deviceId) === socket) {
      clients.delete(deviceId);
      broadcastOffline(deviceId);
      console.log(`Dispositivo desconectado: ${deviceId} (${clients.size} conectados)`);
    }
  });

  socket.on('error', (err) => {
    console.error('Error de socket:', err.message);
  });
});
