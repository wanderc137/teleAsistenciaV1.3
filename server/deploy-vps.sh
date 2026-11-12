#!/usr/bin/env bash
# =============================================================================
# deploy-vps.sh — Despliegue automático del servidor Alerta Mayor en VPS
#
# Instala y configura en una sola pasada:
#   - Node.js 20 + servidor de señalización WebRTC (systemd)
#   - [opcional] Nginx + Let's Encrypt -> wss:// (requiere hostname, ver abajo)
#   - [opcional] coturn (servidor TURN) con credenciales generadas
#   - Firewall ufw con solo los puertos necesarios
#
# USO:
#   sudo bash deploy-vps.sh                          # Modo 1: ws://IP:3000 (básico)
#   sudo bash deploy-vps.sh --dominio midominio.com  # Modo 2: wss://midominio.com + TURN opcional
#   sudo bash deploy-vps.sh --dominio 203-0-113-10.sslip.io  # Modo 2 SIN comprar dominio (gratis)
#   sudo bash deploy-vps.sh --turn                   # añade servidor TURN en cualquier modo
#
# NOTA sobre el dominio: Let's Encrypt NO emite certificados para IPs puras.
# Si no quieres comprar un dominio usa un hostname gratuito que apunte a tu IP:
#   - sslip.io : la IP 1.2.3.4 se convierte en "4-3-2-1.sslip.io" automáticamente
#   - DuckDNS  : registra "tunombre.duckdns.org" gratis en duckdns.org
# =============================================================================
set -euo pipefail

# ----------------------------- Configuración --------------------------------
APP_DIR="/opt/alertamayor"
SERVICE_NAME="alertamayor-signaling"
SIGNALING_PORT=3000
TURN_MIN_PORT=49152
TURN_MAX_PORT=65535
TURN_REALM="alertamayor"

DOMINIO=""
SETUP_TURN=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dominio) DOMINIO="$2"; shift 2 ;;
    --turn)    SETUP_TURN=1; shift ;;
    -h|--help) grep '^#' "$0" | head -25; exit 0 ;;
    *) echo "ERROR: opción desconocida: $1"; exit 1 ;;
  esac
done

[[ $EUID -ne 0 ]] && { echo "Ejecuta con sudo: sudo bash $0 $*"; exit 1; }

echo "==> Detección de IP pública..."
PUBLIC_IP=$(curl -4 -s ifconfig.me || curl -4 -s ipinfo.io/ip)
[[ -z "$PUBLIC_IP" ]] && { echo "ERROR: no pude detectar la IP pública"; exit 1; }
HOSTNAME_PUBLICO="${DOMINIO:-$PUBLIC_IP}"
echo "    IP pública: $PUBLIC_IP"
echo "    Hostname público: $HOSTNAME_PUBLICO"

# ------------------------- 1. Dependencias base ------------------------------
echo "==> [1/6] Instalando dependencias base..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -y -qq
apt-get install -y -qq curl git ufw nginx >/dev/null

if ! command -v node >/dev/null || [[ "$(node -v | cut -dv -f2 | cut -d. -f1)" -lt 18 ]]; then
  echo "    Instalando Node.js 20 (NodeSource)..."
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash - >/dev/null
  apt-get install -y -qq nodejs >/dev/null
fi
echo "    Node $(node -v) OK"

# --------------------- 2. Copiar código del servidor -------------------------
echo "==> [2/6] Desplegando código del servidor en $APP_DIR..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$APP_DIR/server"
cp "$SCRIPT_DIR/index.js" "$SCRIPT_DIR/package.json" "$SCRIPT_DIR/package-lock.json" "$APP_DIR/server/" 2>/dev/null \
  || { echo "ERROR: ejecuta este script desde la carpeta server/ del proyecto"; exit 1; }
cd "$APP_DIR/server"
npm install --omit=dev --no-audit --no-fund --silent

# --------------------------- 3. Servicio systemd -----------------------------
echo "==> [3/6] Creando servicio systemd '$SERVICE_NAME'..."
cat > "/etc/systemd/system/${SERVICE_NAME}.service" <<EOF
[Unit]
Description=Alerta Mayor - Servidor de senalizacion WebRTC
After=network.target

[Service]
Type=simple
WorkingDirectory=${APP_DIR}/server
ExecStart=$(command -v node) index.js
Environment=PORT=${SIGNALING_PORT}
Restart=always
RestartSec=5
User=root
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now "$SERVICE_NAME"
sleep 2
curl -sf "http://127.0.0.1:${SIGNALING_PORT}/health" >/dev/null \
  && echo "    Servidor activo: http://127.0.0.1:${SIGNALING_PORT}/health -> ok" \
  || { echo "ERROR: el servicio no responde; revisa: journalctl -u $SERVICE_NAME -n 30"; exit 1; }

# -------------------- 4. TLS con Nginx + Let's Encrypt -----------------------
if [[ -n "$DOMINIO" ]]; then
  echo "==> [4/6] Configurando Nginx (proxy WebSocket) para $DOMINIO..."
  cat > "/etc/nginx/sites-available/alertamayor" <<EOF
map \$http_upgrade \$connection_upgrade {
    default upgrade;
    ''      close;
}
server {
    listen 80;
    server_name ${DOMINIO};
    location / {
        proxy_pass http://127.0.0.1:${SIGNALING_PORT};
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection \$connection_upgrade;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_read_timeout 86400;
    }
}
EOF
  ln -sf /etc/nginx/sites-available/alertamayor /etc/nginx/sites-enabled/
  rm -f /etc/nginx/sites-enabled/default
  nginx -t && systemctl reload nginx

  echo "    Obteniendo certificado Let's Encrypt para $DOMINIO ..."
  apt-get install -y -qq certbot python3-certbot-nginx >/dev/null
  certbot --nginx -d "$DOMINIO" --non-interactive --agree-tos \
    --register-unsafely-without-email --redirect \
  && echo "    wss://$DOMINIO listo (renovación automática activada)"
else
  echo "==> [4/6] Sin --dominio: se omite TLS (la señalización queda en ws://IP:$SIGNALING_PORT)"
  echo "    AVISO: el build RELEASE de Android bloquea ws:// (cleartext). Usa builds debug"
  echo "    o vuelve a ejecutar con --dominio (puede ser sslip.io / duckdns.org gratis)."
fi

# --------------------------- 5. Servidor TURN --------------------------------
if [[ $SETUP_TURN -eq 1 ]]; then
  echo "==> [5/6] Instalando coturn (servidor TURN)..."
  apt-get install -y -qq coturn >/dev/null
  systemctl enable coturn >/dev/null

  TURN_USER="alerta$(tr -dc 'a-z0-9' </dev/urandom | head -c4)"
  TURN_PASS="$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c16)"

  sed -i 's/^#TURNSERVER_ENABLED/TURNSERVER_ENABLED/' /etc/default/coturn 2>/dev/null || true
  cat > /etc/turnserver.conf <<EOF
listening-port=3478
tls-listening-port=5349
listening-ip=0.0.0.0
external-ip=${PUBLIC_IP}
min-port=${TURN_MIN_PORT}
max-port=${TURN_MAX_PORT}
fingerprint
lt-cred-mech
realm=${TURN_REALM}
user=${TURN_USER}:${TURN_PASS}
no-cli
no-tlsv1
no-tlsv1_1
EOF
  systemctl restart coturn
else
  echo "==> [5/6] Sin --turn: se omite coturn (las videollamadas solo funcionarán en la misma red WiFi)."
fi

# ------------------------------ 6. Firewall ----------------------------------
echo "==> [6/6] Configurando firewall ufw..."
ufw allow OpenSSH >/dev/null
ufw allow 80/tcp  >/dev/null   # HTTP (+ desafío certbot)
ufw allow 443/tcp >/dev/null   # HTTPS/WSS
if [[ -z "$DOMINIO" ]]; then
  ufw allow "${SIGNALING_PORT}/tcp" >/dev/null   # ws:// directo sin proxy
fi
if [[ $SETUP_TURN -eq 1 ]]; then
  ufw allow 3478/tcp  >/dev/null   # TURN TCP
  ufw allow 3478/udp  >/dev/null   # TURN UDP
  ufw allow "${TURN_MIN_PORT}:${TURN_MAX_PORT}/udp" >/dev/null  # relay RTP
fi
yes | ufw enable >/dev/null
systemctl reload nginx 2>/dev/null || true

# ---------------------------- Resumen final ----------------------------------
echo ""
echo "============================================================"
echo " ✅ DESPLIEGUE COMPLETADO"
echo "============================================================"
if [[ -n "$DOMINIO" ]]; then
  echo " Señalización (app):  wss://${DOMINIO}"
else
  echo " Señalización (app):  ws://${PUBLIC_IP}:${SIGNALING_PORT}   [solo build debug]"
fi
if [[ $SETUP_TURN -eq 1 ]]; then
  echo " TURN URL:            turn:${HOSTNAME_PUBLICO}:3478?transport=tcp"
  echo " TURN usuario:        ${TURN_USER}"
  echo " TURN contraseña:     ${TURN_PASS}"
  echo " (guárdalos: se configuran en la app -> Ajustes -> Servidor TURN)"
fi
echo ""
echo " Estado del servicio:  systemctl status ${SERVICE_NAME}"
echo " Logs:                 journalctl -u ${SERVICE_NAME} -f"
echo " Health check:         curl http://127.0.0.1:${SIGNALING_PORT}/health"
echo "============================================================"
