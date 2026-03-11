# SSDID Deployment Guide

Deploy the SSDID landing page and email verification API to a VPS using Podman.

## Prerequisites

- VPS with Ubuntu/Debian (tested on Contabo VPS 10 — 4 CPU, 8 GB RAM)
- DNS A record for `ssdid.my` pointing to server IP
- Ports 80 and 443 open in firewall
- Resend API account with verified domain

## Server: 194.233.95.97

## Install required packages

```bash
sudo apt update && sudo apt install -y \
  curl \
  git \
  ufw \
  podman \
  debian-keyring \
  debian-archive-keyring \
  apt-transport-https
```

### Open firewall ports

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

> The deploy script handles installing Podman and Caddy automatically if not present.

## What gets deployed

| Component | URL | Location on server |
|-----------|-----|-------------------|
| Landing page | `https://ssdid.my/` | `/var/www/ssdid-landing/` |
| Email verify API | `https://ssdid.my/api/email/verify/*` | Podman container `ssdid-email-verify` |
| Health check | `https://ssdid.my/health` | — |

## Quick deploy

```bash
# On your local machine
ssh root@194.233.95.97

# On the server — first time
git clone https://github.com/amiryahaya/ssdid-wallet.git /tmp/ssdid-wallet
cd /tmp/ssdid-wallet

# Set Resend API token before deploying
echo 'RESEND_APITOKEN=re_your_api_token_here' > api/.env

# Deploy
sudo bash api/deploy.sh
```

The script will:
1. Install Podman (if not present)
2. Install Caddy web server (if not present)
3. Build and run the .NET API as a Podman container
4. Copy landing page to `/var/www/ssdid-landing/`
5. Configure Caddy with automatic HTTPS (Let's Encrypt)
6. Generate systemd service for container auto-start on boot

## Verify

```bash
# Health check
curl https://ssdid.my/health

# Landing page
curl -I https://ssdid.my/

# Send test OTP
curl -X POST https://ssdid.my/api/email/verify/send \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","deviceId":"test-device"}'
```

## Updating

```bash
ssh root@194.233.95.97
cd /tmp/ssdid-wallet
git pull
sudo bash api/deploy.sh
```

## Service management

```bash
# Container
podman ps                                         # running containers
podman logs -f ssdid-email-verify                 # live logs
podman restart ssdid-email-verify                 # restart
podman stop ssdid-email-verify                    # stop

# Systemd (auto-start)
sudo systemctl status container-ssdid-email-verify
sudo systemctl restart container-ssdid-email-verify

# Caddy
sudo systemctl status caddy
sudo systemctl restart caddy
sudo journalctl -u caddy -f                       # live logs
cat /var/log/caddy/ssdid.log                      # access logs
```

## Architecture

```
Internet
  │
  ▼
Caddy (ports 80/443, auto HTTPS)
  ├── /api/*  →  localhost:5000 (Podman container)
  ├── /health →  localhost:5000
  └── /*      →  /var/www/ssdid-landing/ (static files)
```
