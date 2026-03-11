# SSDID Deployment Guide

Deploy the SSDID landing page and email verification API to a VPS.

## Prerequisites

- VPS with Ubuntu/Debian (tested on Contabo VPS 10 — 4 CPU, 8 GB RAM)
- DNS A record for `ssdid.my` pointing to server IP
- Ports 80 and 443 open in firewall
- Resend API account with verified domain

## Server: 194.233.95.97

## What gets deployed

| Component | URL | Location on server |
|-----------|-----|-------------------|
| Landing page | `https://ssdid.my/` | `/var/www/ssdid-landing/` |
| Email verify API | `https://ssdid.my/api/email/verify/*` | `/opt/ssdid-email-verify/` |
| Health check | `https://ssdid.my/health` | — |

## Quick deploy

```bash
# On your local machine
ssh root@194.233.95.97

# On the server
git clone https://github.com/amiryahaya/ssdid-wallet.git /tmp/ssdid-wallet
cd /tmp/ssdid-wallet
sudo bash api/deploy.sh
```

The script will:
1. Install .NET 10 runtime (if not present)
2. Install Caddy web server (if not present)
3. Publish the .NET API to `/opt/ssdid-email-verify/`
4. Copy landing page to `/var/www/ssdid-landing/`
5. Configure systemd service for the API
6. Configure Caddy with automatic HTTPS (Let's Encrypt)

## Post-deploy: set Resend API token

```bash
sudo systemctl edit ssdid-email-verify
```

Add this line between the comment markers:
```ini
[Service]
Environment=RESEND_APITOKEN=re_your_api_token_here
```

Then restart:
```bash
sudo systemctl restart ssdid-email-verify
```

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
# API service
sudo systemctl status ssdid-email-verify
sudo systemctl restart ssdid-email-verify
sudo journalctl -u ssdid-email-verify -f    # live logs

# Caddy
sudo systemctl status caddy
sudo systemctl restart caddy
sudo journalctl -u caddy -f                  # live logs
cat /var/log/caddy/ssdid.log                 # access logs
```

## Architecture

```
Internet
  │
  ▼
Caddy (ports 80/443, auto HTTPS)
  ├── /api/*  →  localhost:5000 (.NET app)
  ├── /health →  localhost:5000
  └── /*      →  /var/www/ssdid-landing/ (static files)
```
