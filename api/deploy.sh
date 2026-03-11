#!/bin/bash
set -euo pipefail

# --- Config ---
APP_DIR="/opt/ssdid-email-verify"
SERVICE_NAME="ssdid-email-verify"
DOTNET_VERSION="10.0"

echo "=== SSDID Email Verify — Deployment Script ==="

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

# --- 3. Publish app ---
echo ">> Publishing .NET app..."
cd "$(dirname "$0")/SsdidEmailVerify"
dotnet publish -c Release -o "$APP_DIR" --self-contained false
chown -R www-data:www-data "$APP_DIR"

# --- 4. Setup systemd service ---
echo ">> Setting up systemd service..."
cp "$(dirname "$0")/ssdid-email-verify.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
systemctl restart "$SERVICE_NAME"

# --- 5. Setup Caddy ---
echo ">> Configuring Caddy..."
mkdir -p /var/log/caddy
cp "$(dirname "$0")/Caddyfile" /etc/caddy/Caddyfile
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
echo "  2. Make sure DNS A record for ssdid.my points to this server"
echo "     Caddy will auto-provision Let's Encrypt cert"
echo ""
echo "  3. Test:"
echo "     curl https://ssdid.my/health"
echo ""
