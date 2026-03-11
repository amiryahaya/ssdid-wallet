#!/bin/bash
set -euo pipefail

# --- Config ---
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LANDING_DIR="/var/www/ssdid-landing"
CONTAINER_NAME="ssdid-email-verify"
IMAGE_NAME="ssdid-email-verify"

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

# --- 4. Stop existing container ---
if podman container exists "$CONTAINER_NAME" 2>/dev/null; then
    echo ">> Stopping existing container..."
    podman stop "$CONTAINER_NAME" || true
    podman rm "$CONTAINER_NAME" || true
fi

# --- 5. Run container ---
echo ">> Starting container..."
RESEND_TOKEN="${RESEND_APITOKEN:-}"
if [ -f "$REPO_ROOT/api/.env" ]; then
    echo ">> Loading .env file..."
    podman run -d \
        --name "$CONTAINER_NAME" \
        --restart always \
        --env-file "$REPO_ROOT/api/.env" \
        -p 127.0.0.1:5000:5000 \
        "$IMAGE_NAME"
elif [ -n "$RESEND_TOKEN" ]; then
    podman run -d \
        --name "$CONTAINER_NAME" \
        --restart always \
        -e "RESEND_APITOKEN=$RESEND_TOKEN" \
        -p 127.0.0.1:5000:5000 \
        "$IMAGE_NAME"
else
    echo "WARNING: No RESEND_APITOKEN set. Create api/.env or export RESEND_APITOKEN first."
    podman run -d \
        --name "$CONTAINER_NAME" \
        --restart always \
        -p 127.0.0.1:5000:5000 \
        "$IMAGE_NAME"
fi

# --- 6. Deploy landing page ---
echo ">> Deploying landing page..."
mkdir -p "$LANDING_DIR"
cp -r "$REPO_ROOT/landing/"* "$LANDING_DIR/"
cp -r "$REPO_ROOT/assets/" "$LANDING_DIR/assets/" 2>/dev/null || true
chown -R www-data:www-data "$LANDING_DIR"

# --- 7. Setup Caddy ---
echo ">> Configuring Caddy..."
mkdir -p /var/log/caddy
cp "$REPO_ROOT/api/Caddyfile" /etc/caddy/Caddyfile
systemctl enable caddy
systemctl restart caddy

# --- 8. Enable container auto-start on boot ---
echo ">> Generating systemd service for container..."
mkdir -p /etc/systemd/system
podman generate systemd --name "$CONTAINER_NAME" --new --files
mv "container-${CONTAINER_NAME}.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable "container-${CONTAINER_NAME}.service"

echo ""
echo "=== Deployment complete ==="
echo ""
echo "Container status:"
podman ps --filter name="$CONTAINER_NAME"
echo ""
echo "Next steps:"
echo "  1. If you haven't set the Resend API token, create api/.env:"
echo "     echo 'RESEND_APITOKEN=re_xxxxx' > $REPO_ROOT/api/.env"
echo "     Then re-run: sudo bash api/deploy.sh"
echo ""
echo "  2. Test:"
echo "     curl https://ssdid.my/health"
echo "     curl https://ssdid.my/"
echo ""
