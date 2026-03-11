#!/bin/bash
set -euo pipefail

# --- Config ---
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LANDING_DIR="/var/www/ssdid-landing"
CONTAINER_NAME="ssdid-email-verify"
IMAGE_NAME="ssdid-email-verify"
ENV_DIR="/opt/ssdid-email-verify"
QUADLET_DIR="/etc/containers/systemd"

echo "=== SSDID — Deployment Script ==="
echo "Repo root: $REPO_ROOT"

# --- 1. Install Podman ---
if ! command -v podman &>/dev/null; then
    echo ">> Installing Podman..."
    apt-get update
    apt-get install -y podman
else
    echo ">> Podman already installed: $(podman --version)"
fi

# --- 2. Install Caddy ---
if ! command -v caddy &>/dev/null; then
    echo ">> Installing Caddy..."
    apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list
    apt-get update
    apt-get install -y caddy
else
    echo ">> Caddy already installed: $(caddy version)"
fi

# --- 3. Build container image ---
echo ">> Building container image..."
cd "$REPO_ROOT/api"
podman build -t "$IMAGE_NAME" .

# --- 4. Stop existing container (if running) ---
if podman container exists "$CONTAINER_NAME" 2>/dev/null; then
    echo ">> Stopping existing container..."
    podman stop "$CONTAINER_NAME" || true
    podman rm "$CONTAINER_NAME" || true
fi

# --- 5. Setup .env file ---
echo ">> Setting up environment..."
mkdir -p "$ENV_DIR"
if [ -f "$REPO_ROOT/api/.env" ]; then
    cp "$REPO_ROOT/api/.env" "$ENV_DIR/.env"
    chmod 600 "$ENV_DIR/.env"
    echo ">> .env copied to $ENV_DIR/.env"
elif [ ! -f "$ENV_DIR/.env" ]; then
    echo "WARNING: No .env file found. Create $ENV_DIR/.env with RESEND_APITOKEN=re_xxxxx"
    touch "$ENV_DIR/.env"
fi

# --- 6. Install Quadlet (systemd container unit) ---
echo ">> Installing Quadlet..."
mkdir -p "$QUADLET_DIR"
cp "$REPO_ROOT/api/ssdid-email-verify.container" "$QUADLET_DIR/"
systemctl daemon-reload
systemctl restart "$CONTAINER_NAME"

# --- 7. Deploy landing page ---
echo ">> Deploying landing page..."
mkdir -p "$LANDING_DIR"
cp -r "$REPO_ROOT/landing/"* "$LANDING_DIR/"
cp -r "$REPO_ROOT/assets/" "$LANDING_DIR/assets/" 2>/dev/null || true
chown -R www-data:www-data "$LANDING_DIR"

# --- 8. Setup Caddy ---
echo ">> Configuring Caddy..."
mkdir -p /var/log/caddy
cp "$REPO_ROOT/api/Caddyfile" /etc/caddy/Caddyfile
systemctl enable caddy
systemctl restart caddy

echo ""
echo "=== Deployment complete ==="
echo ""
echo "Container status:"
podman ps --filter name="$CONTAINER_NAME"
echo ""
echo "Next steps:"
echo "  1. If you haven't set the Resend API token:"
echo "     echo 'RESEND_APITOKEN=re_xxxxx' > $ENV_DIR/.env"
echo "     sudo systemctl restart $CONTAINER_NAME"
echo ""
echo "  2. Test:"
echo "     curl https://ssdid.my/health"
echo "     curl https://ssdid.my/"
echo ""
