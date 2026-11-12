# Despliegue del servidor de señalización

**Alerta Mayor 1.3.0** · Servidor Node.js + `ws` + Nginx + TLS + coturn opcional

---

## Índice

1. [Cuándo necesitas esto](#cuándo-necesitas-esto)
2. [Arquitectura del despliegue](#arquitectura-del-despliegue)
3. [Prerrequisitos](#prerrequisitos)
4. [Opción A: script automatizado](#opción-a-script-automatizado)
5. [Opción B: despliegue manual](#opción-b-despliegue-manual)
6. [Configurar los teléfonos](#configurar-los-teléfonos)
7. [Verificación](#verificación)
8. [Operación y mantenimiento](#operación-y-mantenimiento)
9. [Solución de problemas](#solución-de-problemas)
10. [Checklist de seguridad](#checklist-de-seguridad)
11. [Costes y dimensionamiento](#costes-y-dimensionamiento)
12. [Alternativas sin dominio propio](#alternativas-sin-dominio-propio)

---

## Cuándo necesitas esto

La app necesita un **servidor de señalización** para que los dos teléfonos se encuentren. Hay tres escenarios:

| Escenario | Servidor | TLS | TURN | Dificultad |
|---|---|---|---|---|
| Probar en casa (misma Wi-Fi) | PC con `node index.js` | No | No | Baja |
| Uso diario en la misma red | Mini-PC / Raspberry Pi | No | No | Baja |
| Familiar fuera de casa o datos móviles | VPS público | **Sí** | **Muy recomendable** | Media |

Esta guía cubre el tercer caso: un VPS con dominio, certificado y, opcionalmente, TURN.

> **Por qué no basta la LAN:** el build de release de la app **bloquea `ws://`** por política de seguridad. Si quieres usar la app fuera de tu red, necesitas `wss://`, y para eso hace falta un dominio y un certificado.

---

## Arquitectura del despliegue

```
                 ┌─────────────────────────────────────────────┐
   Telefonos     │                    VPS                      │
   Android       │                                             │
       │         │   :443  ┌──────────┐   :3000  ┌──────────┐  │
       └─────────┼────────►│  Nginx   │─────────►│ Node.js  │  │
         wss://  │         │  TLS     │  proxy   │  + ws    │  │
                 │         └──────────┘          └────┬─────┘  │
                 │              ▲                     │        │
                 │              │ certbot             │ FCM    │
                 │         ┌────┴─────┐               ▼        │
                 │         │Let's     │        ┌────────────┐  │
                 │         │Encrypt   │        │ Firebase   │  │
                 │         └──────────┘        └────────────┘  │
                 │                                             │
                 │   :3478 ┌──────────┐                        │
                 │◄─────── │ coturn   │  relay UDP 49152-65535 │
                 │         └──────────┘                        │
                 └─────────────────────────────────────────────┘
```

Componentes:

| Componente | Puerto | Función |
|---|---|---|
| **Nginx** | 443/tcp | Termina TLS y hace proxy WebSocket a Node |
| **Node.js** | 3000/tcp (loopback) | Registro, enrutado, `pendingCalls`, push FCM |
| **coturn** | 3478 tcp/udp, 5349 tls, 49152–65535/udp | Relay TURN cuando no hay ruta P2P |
| **certbot** | 80/tcp (ACME) | Certificado y renovación |

El servidor **no transporta audio ni vídeo**. Solo reenvía JSON de sesión. Con TURN, los medios pasan por el relay pero siguen cifrados con SRTP/DTLS.

---

## Prerrequisitos

### Hardware mínimo

| Recurso | Mínimo | Recomendado |
|---|---|---|
| vCPU | 1 | 2 |
| RAM | 512 MB | 1 GB |
| Disco | 10 GB | 20 GB |
| Ancho de banda | 100 GB/mes | 500 GB/mes (si usas TURN) |

Para dos teléfonos y un uso doméstico, el mínimo sobra. Si activas TURN, el consumo crece con las llamadas: cada llamada relayada usa ~0,5–1,5 Mbps por sentido.

### Software

- **Ubuntu 22.04 LTS** o 24.04 (también funciona Debian 12).
- Acceso SSH con `sudo`.
- **Dominio** con registro DNS `A` apuntando a la IP del VPS.
- Puertos abiertos en el firewall del proveedor (no solo en `ufw`).

### DNS

```
Tipo   Nombre              Valor
A      alerta.TU-DOMINIO.com   203.0.113.10
```

> `203.0.113.10` es una IP de documentación (RFC 5737). Sustitúyela por la de tu VPS. No publiques tu IP real en el repositorio.

Verifica la propagación:

```bash
dig +short alerta.TU-DOMINIO.com
# debe devolver la IP del VPS
```

### Puertos en el panel del proveedor

| Puerto | Protocolo | Uso |
|---|---|---|
| 22 | TCP | SSH |
| 80 | TCP | ACME (Let's Encrypt) |
| 443 | TCP | `wss://` |
| 3478 | TCP + UDP | TURN |
| 5349 | TCP + UDP | TURN sobre TLS (opcional) |
| 49152–65535 | UDP | Relay de medios TURN |

> Abrir el rango de relay en el firewall del proveedor es el error más común. Sin él, TURN negocia pero no pasa audio.

---

## Opción A: script automatizado

El repositorio incluye `server/deploy-vps.sh`, que hace todo lo anterior de forma idempotente.

### Uso

```bash
# En el VPS
sudo apt-get update && sudo apt-get install -y git
git clone https://github.com/wanderc137/teleAsistenciaV1.3.git
cd teleAsistenciaV1.3/server

sudo bash deploy-vps.sh --dominio alerta.TU-DOMINIO.com --turn
```

| Opción | Efecto |
|---|---|
| `--dominio <fqdn>` | Configura Nginx, certbot y la URL `wss://` |
| `--turn` | Instala y configura coturn con credenciales aleatorias |
| (sin `--dominio`) | Solo Node en `:3000`, sin TLS (pruebas) |

### Qué hace el script

1. Instala `curl`, `git`, `ufw`, `nginx` y Node.js 20 desde NodeSource.
2. Copia `index.js` y `package*.json` a `/opt/alertamayor`.
3. Ejecuta `npm install --omit=dev`.
4. Crea el servicio `alertamayor-signaling` en systemd:

```ini
[Service]
ExecStart=/usr/bin/node /opt/alertamayor/index.js
Restart=always
RestartSec=3
NoNewPrivileges=true
Environment=PORT=3000
```

5. Configura Nginx como proxy WebSocket.
6. Emite el certificado con `certbot --nginx --redirect`.
7. Si `--turn`, instala `coturn`, genera usuario y contraseña aleatorios y escribe `/etc/turnserver.conf`.
8. Abre `ufw` para 22, 80, 443, 3000 (solo si no hay dominio), 3478 y el rango de relay.
9. Comprueba `GET /health`.
10. Imprime la configuración para la app:

```
 Señalización (app):  wss://alerta.TU-DOMINIO.com
 TURN URL:            turn:alerta.TU-DOMINIO.com:3478?transport=tcp
 TURN usuario:        <generado>
 TURN contraseña:     <generado>
```

> **Guarda esas credenciales.** El script no las vuelve a mostrar. Están en `/etc/turnserver.conf`.

### Cuándo NO usar el script

- Si ya tienes Nginx sirviendo otros sitios (el script añade un `server` block; revisa el resultado).
- Si usas Caddy, Traefik o un balanceador.
- Si el VPS no es Ubuntu/Debian.
- Si no quieres que un script toque `ufw` y systemd.

En esos casos, usa la opción manual.

---

## Opción B: despliegue manual

### 1. Node.js 20

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs
node -v    # v20.x
```

> Ejecutar un script remoto con `curl | bash` es una decisión de confianza. Si prefieres no hacerlo, instala Node desde el repositorio de tu distribución o desde un binario oficial.

### 2. Usuario y directorio

```bash
sudo useradd --system --home /opt/alertamayor --shell /usr/sbin/nologin alertamayor
sudo mkdir -p /opt/alertamayor
sudo chown -R alertamayor:alertamayor /opt/alertamayor

sudo cp index.js package.json package-lock.json /opt/alertamayor/
cd /opt/alertamayor
sudo -u alertamayor npm install --omit=dev
```

### 3. FCM (opcional)

Si quieres push:

```bash
sudo cp serviceAccountKey.json /opt/alertamayor/
sudo chown alertamayor:alertamayor /opt/alertamayor/serviceAccountKey.json
sudo chmod 600 /opt/alertamayor/serviceAccountKey.json
```

> El archivo da control total sobre el proyecto Firebase. Nunca lo subas al repositorio ni lo dejes en `/tmp`.

### 4. Servicio systemd

`/etc/systemd/system/alertamayor-signaling.service`:

```ini
[Unit]
Description=Alerta Mayor signaling server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=alertamayor
Group=alertamayor
WorkingDirectory=/opt/alertamayor
ExecStart=/usr/bin/node index.js
Restart=always
RestartSec=3
Environment=NODE_ENV=production
Environment=PORT=3000
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/alertamayor

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now alertamayor-signaling
sudo systemctl status alertamayor-signaling
```

Prueba local:

```bash
curl -s http://127.0.0.1:3000/health   # -> ok
```

### 5. Nginx

```bash
sudo apt-get install -y nginx
```

`/etc/nginx/sites-available/alertamayor`:

```nginx
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}

server {
    listen 80;
    listen [::]:80;
    server_name alerta.TU-DOMINIO.com;

    location /health {
        proxy_pass http://127.0.0.1:3000/health;
        proxy_set_header Host $host;
    }

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 86400;
        proxy_send_timeout 86400;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/alertamayor /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

**Claves del proxy WebSocket:**

- `proxy_http_version 1.1` — sin esto no hay upgrade.
- `Upgrade` y `Connection` — sin esto Nginx cierra el socket.
- `proxy_read_timeout 86400` — sin esto el socket muere a los 60 s de inactividad.

### 6. TLS con Let's Encrypt

```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d alerta.TU-DOMINIO.com --redirect
```

Certbot modifica el bloque para escuchar en 443 y redirigir 80 → 443. Renovación automática:

```bash
sudo systemctl status certbot.timer
sudo certbot renew --dry-run
```

Comprueba:

```bash
curl -I https://alerta.TU-DOMINIO.com/health
# HTTP/2 200
```

### 7. coturn (TURN)

```bash
sudo apt-get install -y coturn
sudo sed -i 's/^#TURNSERVER_ENABLED=1/TURNSERVER_ENABLED=1/' /etc/default/coturn
```

Genera credenciales:

```bash
TURN_USER="alerta_$(openssl rand -hex 4)"
TURN_PASS="$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)"
echo "usuario: $TURN_USER"
echo "contraseña: $TURN_PASS"
```

`/etc/turnserver.conf`:

```ini
listening-port=3478
tls-listening-port=5349
fingerprint
lt-cred-mech
realm=alertamayor
user=TU_USUARIO:TU_PASSWORD
external-ip=203.0.113.10
min-port=49152
max-port=65535
no-multicast-peers
no-cli
log-file=/var/log/turnserver.log
simple-log
```

```bash
sudo systemctl enable --now coturn
sudo systemctl status coturn
```

Prueba de relay (desde otra red, no desde el propio VPS):

```bash
turnutils_uclient -u TU_USUARIO -w TU_PASSWORD -p 3478 203.0.113.10
```

Si responde con tráfico, TURN funciona.

> **Producción:** `lt-cred-mech` usa credenciales estáticas. Lo correcto es `use-auth-secret` con credenciales efímeras emitidas por el servidor. Requiere cambiar el backend y la app. Está en el roadmap.

### 8. Firewall

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 3478/tcp
sudo ufw allow 3478/udp
sudo ufw allow 49152:65535/udp
sudo ufw enable
sudo ufw status verbose
```

Y abre los mismos puertos en el **panel del proveedor**.

---

## Configurar los teléfonos

### En la app

1. **Configuración → URL del servidor**:

```
wss://alerta.TU-DOMINIO.com
```

2. **Configuración → Servidor TURN** (si lo activaste):

| Campo | Valor |
|---|---|
| URL | `turn:alerta.TU-DOMINIO.com:3478?transport=tcp` |
| Usuario | el generado |
| Contraseña | la generada |

3. Guarda. La app reconecta en caliente.

### Alternativa: fijar la URL en el build

```bash
./gradlew assembleRelease -PsignalingServerUrl=wss://alerta.TU-DOMINIO.com
```

Así el APK ya trae la URL y no hay que configurarla en cada teléfono. La configuración manual sigue teniendo prioridad si el usuario la cambia.

### Verificar en el teléfono

1. La app debe mostrar el estado conectado (sin el icono de "sin conexión").
2. Abre `https://alerta.TU-DOMINIO.com/health` en el navegador del teléfono: debe decir `ok`.
3. Haz una llamada de prueba entre los dos teléfonos, primero en Wi-Fi y luego con datos móviles.

---

## Verificación

### Checklist técnico

```bash
# 1. Servicio activo
systemctl is-active alertamayor-signaling    # active

# 2. Responde en local
curl -s http://127.0.0.1:3000/health         # ok

# 3. Responde por TLS
curl -s https://alerta.TU-DOMINIO.com/health # ok

# 4. Certificado válido y vigente
echo | openssl s_client -connect alerta.TU-DOMINIO.com:443 -servername alerta.TU-DOMINIO.com 2>/dev/null \
  | openssl x509 -noout -dates -issuer

# 5. Upgrade WebSocket (debe devolver 101)
curl -i -N \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Version: 13" \
  -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" \
  https://alerta.TU-DOMINIO.com/ | head -5

# 6. TURN escuchando
ss -lunp | grep 3478
```

### Prueba funcional

1. Instala la app en dos teléfonos con la misma build.
2. Vincula por QR.
3. Llama en la misma Wi-Fi → debe conectar.
4. Desactiva Wi-Fi en uno y usa datos móviles → si falla, revisa TURN.
5. Bloquea la pantalla del supervisado y llama desde el otro → debe sonar.
6. Reinicia el servidor con una llamada en curso → debe cortarse (comportamiento esperado).
7. Apaga y enciende el teléfono del mayor → debe volver a estar "En línea" sin abrir la app.

---

## Operación y mantenimiento

### Logs

```bash
# Servidor
journalctl -u alertamayor-signaling -f
journalctl -u alertamayor-signaling --since "1 hour ago"

# Nginx
tail -f /var/log/nginx/access.log
tail -f /var/log/nginx/error.log

# TURN
tail -f /var/log/turnserver.log
```

### Actualizar el servidor

```bash
cd /ruta/al/repo
git pull
sudo cp server/index.js /opt/alertamayor/
sudo chown alertamayor:alertamayor /opt/alertamayor/index.js
sudo systemctl restart alertamayor-signaling
curl -s https://alerta.TU-DOMINIO.com/health
```

> Reiniciar corta las llamadas en curso: el estado vive en memoria. Hazlo en horario de bajo uso.

### Copias de seguridad

| Qué | Dónde | Frecuencia |
|---|---|---|
| `serviceAccountKey.json` | `/opt/alertamayor/` | Una vez, en lugar seguro |
| `turnserver.conf` | `/etc/` | Tras cada cambio |
| Configuración de Nginx | `/etc/nginx/sites-available/` | Tras cada cambio |
| Certificados | `/etc/letsencrypt/` | Automático (certbot) |

El servidor **no tiene base de datos**, así que no hay datos de usuarios que respaldar. Esa es una ventaja de privacidad y una limitación funcional.

### Rotar credenciales TURN

```bash
sudo nano /etc/turnserver.conf   # cambia user=
sudo systemctl restart coturn
```

Luego actualiza la contraseña en Configuración en **ambos** teléfonos. Si no, las llamadas que necesiten relay fallarán.

### Monitorización mínima

```bash
# Un cron cada 5 minutos
*/5 * * * * curl -fsS https://alerta.TU-DOMINIO.com/health || \
  echo "signaling caido" | mail -s "Alerta Mayor" tu@correo.com
```

Para algo más serio: Uptime Kuma, Healthchecks.io o el monitor de tu proveedor.

---

## Solución de problemas

### El certificado no se emite

| Causa | Solución |
|---|---|
| DNS no propagado | `dig +short TU-DOMINIO.com` debe dar la IP |
| Puerto 80 cerrado | Abre 80 en `ufw` **y** en el panel del proveedor |
| Otro servicio en 80 | `ss -ltnp | grep :80` |
| Demasiados intentos | Let's Encrypt limita a 5 fallos/hora; espera |

### Nginx devuelve 502

```bash
systemctl status alertamayor-signaling
journalctl -u alertamayor-signaling -n 50
ss -ltnp | grep 3000
```

El servicio Node no está escuchando o murió. Revisa el `ExecStart` y los permisos de `/opt/alertamayor`.

### El WebSocket se corta a los 60 segundos

Falta `proxy_read_timeout 86400` (y `proxy_send_timeout`). Sin eso, Nginx cierra la conexión inactiva y la app reconecta cada minuto.

### `wss://` conecta pero no hay llamadas

- Comprueba que ambos teléfonos están registrados (logs del servidor).
- Revisa que el `deviceId` del QR es el correcto.
- Prueba en la misma red: si funciona, el problema es NAT → TURN.

### TURN no relay

1. `external-ip` debe ser la IP **pública** del VPS, no la privada.
2. El rango `49152–65535/udp` debe estar abierto en el proveedor.
3. `realm` debe coincidir con el configurado.
4. Prueba con `turnutils_uclient` desde fuera del VPS.

### "Error enviando push FCM"

- La cuenta de servicio y `google-services.json` deben ser del **mismo** proyecto.
- El token puede estar caducado: la app lo renueva y lo reenvía al reconectar.
- Revisa que el archivo esté en `/opt/alertamayor/serviceAccountKey.json` y sea legible por el usuario del servicio.

### El servidor se reinicia en bucle

```bash
journalctl -u alertamayor-signaling -n 100 --no-pager
```

Causas típicas: falta `node_modules`, ruta incorrecta en `ExecStart`, permisos del directorio, o `PORT` ocupado.

---

## Checklist de seguridad

Antes de dar el servicio por bueno:

- [ ] `wss://` obligatorio; `ws://` solo en LAN y build debug.
- [ ] Certificado válido y renovación automática comprobada.
- [ ] `serviceAccountKey.json` con permisos `600` y fuera del repositorio.
- [ ] Nginx sirve solo los `location` necesarios.
- [ ] `ufw` activo y puertos del proveedor alineados.
- [ ] SSH con clave, sin password y sin root directo.
- [ ] `fail2ban` o similar para SSH (recomendado).
- [ ] Actualizaciones automáticas de seguridad (`unattended-upgrades`).
- [ ] Credenciales TURN fuertes y rotadas si se comparten.
- [ ] Logs sin tokens ni credenciales.
- [ ] Monitor de disponibilidad del endpoint `/health`.

### Lo que este despliegue **no** resuelve

- **Autenticación de clientes.** Cualquiera que conozca la URL puede abrir un socket y registrarse.
- **Rate limiting.** No hay límite de conexiones ni de mensajes.
- **Revocación.** No se puede expulsar a un dispositivo vinculado.
- **Alta disponibilidad.** Un solo VPS y estado en memoria.

Para un uso real, la autenticación por token en `register` es el siguiente paso obligatorio.

---

## Costes y dimensionamiento

| Proveedor típico | Plan | Coste aproximado | Sirve para |
|---|---|---|---|
| VPS básico | 1 vCPU, 512 MB–1 GB | 3–6 USD/mes | Señalización sin TURN |
| VPS con TURN | 1 vCPU, 1 GB, 1 TB | 6–12 USD/mes | Varias familias |
| Dominio | `.com` / `.dev` | 10–15 USD/año | — |
| TLS | Let's Encrypt | 0 | — |
| Firebase | Plan Spark/Blaze | 0 (dentro de cuota) | FCM |

**Tráfico:** sin TURN, el consumo es mínimo (unos pocos KB por llamada). Con TURN, cada llamada relayada consume ~0,5–1,5 Mbps por sentido; 100 horas de llamada al mes rondan 45–130 GB.

---

## Alternativas sin dominio propio

Si no quieres comprar un dominio:

| Opción | Ventaja | Inconveniente |
|---|---|---|
| **sslip.io** | `wss://203-0-113-10.sslip.io` resuelve a la IP sin DNS | El certificado depende del servicio; IP expuesta |
| **DuckDNS** | Subdominio gratuito con DNS dinámico | Hay que mantenerlo actualizado |
| **Cloudflare Tunnel** | No abre puertos; TLS gestionado | Añade una dependencia y latencia |
| **Tailscale / WireGuard** | Red privada entre teléfonos y servidor | Requiere instalar VPN en los teléfonos |

Para pruebas, `sslip.io` es la vía más rápida; para un uso continuado, un dominio propio es más estable y más fácil de explicar a la familia.

---

## Referencias

- [README](../README.md)
- [datasheet.md](datasheet.md)
- [developer.md](developer.md)
- [REFERENCES.md](REFERENCES.md)
- [Node.js — descargas](https://nodejs.org/en/download)
- [Nginx — WebSocket proxying](https://nginx.org/en/docs/http/websocket.html)
- [certbot — instrucciones](https://certbot.eff.org/instructions)
- [coturn — wiki](https://github.com/coturn/coturn/wiki)
- [Let's Encrypt — rate limits](https://letsencrypt.org/docs/rate-limits/)
- [RFC 5737 — direcciones de documentación](https://datatracker.ietf.org/doc/html/rfc5737)

---

<div align="center">

**Alerta Mayor 1.3.0** · Despliegue en VPS

*Un servidor pequeño, un dominio y un certificado: suficiente para que la llamada llegue.*

</div>
