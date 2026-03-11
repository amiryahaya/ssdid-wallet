#!/bin/bash
set -euo pipefail

# --- Config ---
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="/opt/ssdid-email-verify"
LANDING_DIR="/var/www/ssdid-landing"
SERVICE_NAME="ssdid-email-verify"
DOTNET_VERSION="10.0"

echo "=== SSDID — Deployment Script ==="
echo "Repo root: $REPO_ROOT"

# --- 1. Install .NET runtime ---
if ! command -v dotnet &>/dev/null; then
    echo ">> Installing .NET $DOTNET_VERSION runtime..."
    curl -sSL https://dot.net/v1/dotnet-install.sh | bash -s -- --runtime aspnetcore --version latest --channel $DOTNET_VERSION --install-dir /usr/share/dotnet
    ln -sf /usr/share/dotnet/dotnet /usr/local/bin/dotnet
else
    echo ">> .NET already installed: $(dotnet --version)"
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

# --- 3. Publish API ---
echo ">> Publishing .NET app..."
cd "$REPO_ROOT/api/SsdidEmailVerify"
dotnet publish -c Release -o "$APP_DIR" --self-contained false
chown -R www-data:www-data "$APP_DIR"

# --- 4. Deploy landing page ---
echo ">> Deploying landing page..."
mkdir -p "$LANDING_DIR"
cp -r "$REPO_ROOT/landing/"* "$LANDING_DIR/"
cp -r "$REPO_ROOT/assets/" "$LANDING_DIR/assets/" 2>/dev/null || true
chown -R www-data:www-data "$LANDING_DIR"

# --- 5. Setup systemd service ---
echo ">> Setting up systemd service..."
cp "$REPO_ROOT/api/ssdid-email-verify.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
systemctl restart "$SERVICE_NAME"

# --- 6. Setup Caddy ---
echo ">> Configuring Caddy..."
mkdir -p /var/log/caddy
cp "$REPO_ROOT/api/Caddyfile" /etc/caddy/Caddyfile
systemctl enable caddy
systemctl restart caddy

echo ""
echo "=== Deployment complete ==="
echo ""
echo "Next steps:"
echo "  1. Set your Resend API token:"
echo "     sudo systemctl edit ssdid-email-verify"
echo "     Add: Environment=RESEND_APITOKEN=re_xxxxx"
echo "     Then: sudo systemctl restart ssdid-email-verify"
echo ""
echo "  2. Test:"
echo "     curl https://ssdid.my/health"
echo "     curl https://ssdid.my/"
echo ""
