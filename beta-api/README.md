# SSDID Beta API

Simple API for TestFlight beta signup. Receives email from the landing page → calls App Store Connect API → Apple sends TestFlight invitation.

## Setup

### 1. Get App Store Connect API Key

1. Go to [App Store Connect → Keys](https://appstoreconnect.apple.com/access/integrations/api)
2. Click "+" → name it "Beta API" → role "App Manager"
3. Download the `.p8` file
4. Note the **Key ID** and **Issuer ID**

### 2. Get Beta Group ID

1. Go to App Store Connect → Your App → TestFlight → Groups
2. Create a group (e.g., "Public Beta")
3. Copy the group ID from the URL

### 3. Run with Podman

```bash
# Build
podman build -t ssdid-beta-api -f Containerfile .

# Run
podman run -d \
  --name ssdid-beta-api \
  -p 3001:3001 \
  -e ASC_KEY_ID=YOUR_KEY_ID \
  -e ASC_ISSUER_ID=YOUR_ISSUER_ID \
  -e ASC_PRIVATE_KEY="$(cat AuthKey_XXXXXXXX.p8)" \
  -e BETA_GROUP_ID=YOUR_BETA_GROUP_ID \
  -e CORS_ORIGIN=https://ssdid.my \
  -e RATE_LIMIT_PER_HOUR=5 \
  ssdid-beta-api

# Check health
curl http://localhost:3001/health

# Test signup
curl -X POST http://localhost:3001/api/beta/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","firstName":"Test","lastName":"User"}'
```

### 4. Deploy behind Nginx

```nginx
location /api/beta/ {
    proxy_pass http://127.0.0.1:3001;
    proxy_set_header X-Forwarded-For $remote_addr;
}
```

## API

### `POST /api/beta/signup`

```json
{
  "email": "user@example.com",
  "firstName": "User",
  "lastName": "Name"
}
```

**Success (200):**
```json
{ "message": "TestFlight invitation sent! Check your email." }
```

**Errors:**
- `400` — Invalid input
- `429` — Rate limited (5/hour per IP)
- `502` — App Store Connect API error
- `503` — Not configured

### `GET /health`

```json
{ "status": "ok", "configured": true }
```

## Environment Variables

| Variable | Required | Description |
|----------|:---:|-------------|
| `ASC_KEY_ID` | ✅ | App Store Connect API key ID |
| `ASC_ISSUER_ID` | ✅ | App Store Connect issuer ID |
| `ASC_PRIVATE_KEY` | ✅* | API key content (PEM string) |
| `ASC_PRIVATE_KEY_PATH` | ✅* | Path to .p8 file (alternative to ASC_PRIVATE_KEY) |
| `BETA_GROUP_ID` | ✅ | TestFlight beta group ID |
| `PORT` | | Server port (default: 3001) |
| `CORS_ORIGIN` | | Allowed origin (default: *) |
| `RATE_LIMIT_PER_HOUR` | | Max signups per IP per hour (default: 5) |

\* Provide either `ASC_PRIVATE_KEY` or `ASC_PRIVATE_KEY_PATH`
