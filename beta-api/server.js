import { createServer } from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { sign } from 'jsonwebtoken';

// === Config ===
const PORT = process.env.PORT || 3001;
const CORS_ORIGIN = process.env.CORS_ORIGIN || '*';
const RATE_LIMIT_PER_HOUR = parseInt(process.env.RATE_LIMIT_PER_HOUR || '5');

const ASC_KEY_ID = process.env.ASC_KEY_ID;
const ASC_ISSUER_ID = process.env.ASC_ISSUER_ID;
const ASC_PRIVATE_KEY = process.env.ASC_PRIVATE_KEY
  || (process.env.ASC_PRIVATE_KEY_PATH && existsSync(process.env.ASC_PRIVATE_KEY_PATH)
    ? readFileSync(process.env.ASC_PRIVATE_KEY_PATH, 'utf8')
    : null);
const BETA_GROUP_ID = process.env.BETA_GROUP_ID;

// === Rate limiter (in-memory) ===
const rateLimits = new Map();
setInterval(() => rateLimits.clear(), 60 * 60 * 1000); // Reset hourly

function checkRateLimit(ip) {
  const count = rateLimits.get(ip) || 0;
  if (count >= RATE_LIMIT_PER_HOUR) return false;
  rateLimits.set(ip, count + 1);
  return true;
}

// === App Store Connect API ===
function generateASCToken() {
  const now = Math.floor(Date.now() / 1000);
  return sign({
    iss: ASC_ISSUER_ID,
    iat: now,
    exp: now + 1200, // 20 minutes
    aud: 'appstoreconnect-v1',
  }, ASC_PRIVATE_KEY, {
    algorithm: 'ES256',
    header: { alg: 'ES256', kid: ASC_KEY_ID, typ: 'JWT' },
  });
}

async function addBetaTester(email, firstName, lastName) {
  const token = generateASCToken();

  const body = {
    data: {
      type: 'betaTesters',
      attributes: { email, firstName, lastName },
      relationships: {
        betaGroups: {
          data: [{ type: 'betaGroups', id: BETA_GROUP_ID }],
        },
      },
    },
  };

  const resp = await fetch('https://api.appstoreconnect.apple.com/v1/betaTesters', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });

  if (resp.status === 201) {
    return { success: true };
  }

  if (resp.status === 409) {
    // Already exists — still success (idempotent)
    return { success: true, existing: true };
  }

  const error = await resp.json().catch(() => ({}));
  const detail = error.errors?.[0]?.detail || `App Store Connect returned ${resp.status}`;
  throw new Error(detail);
}

// === HTTP Server ===
function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', CORS_ORIGIN);
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
}

function json(res, status, data) {
  cors(res);
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(data));
}

const server = createServer(async (req, res) => {
  const ip = req.headers['x-forwarded-for']?.split(',')[0]?.trim() || req.socket.remoteAddress;

  // CORS preflight
  if (req.method === 'OPTIONS') {
    cors(res);
    res.writeHead(204);
    res.end();
    return;
  }

  // Health check
  if (req.method === 'GET' && req.url === '/health') {
    const configured = !!(ASC_KEY_ID && ASC_ISSUER_ID && ASC_PRIVATE_KEY && BETA_GROUP_ID);
    return json(res, 200, { status: 'ok', configured });
  }

  // Beta signup
  if (req.method === 'POST' && req.url === '/api/beta/signup') {
    // Rate limit
    if (!checkRateLimit(ip)) {
      return json(res, 429, { message: 'Too many signups. Please try again later.' });
    }

    // Parse body
    let body;
    try {
      const chunks = [];
      for await (const chunk of req) chunks.push(chunk);
      body = JSON.parse(Buffer.concat(chunks).toString());
    } catch {
      return json(res, 400, { message: 'Invalid JSON body' });
    }

    const { email, firstName, lastName } = body;

    // Validate
    if (!email || !firstName || !lastName) {
      return json(res, 400, { message: 'Missing required fields: email, firstName, lastName' });
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return json(res, 400, { message: 'Invalid email format' });
    }
    if (firstName.length > 100 || lastName.length > 100) {
      return json(res, 400, { message: 'Name too long' });
    }

    // Check config
    if (!ASC_KEY_ID || !ASC_ISSUER_ID || !ASC_PRIVATE_KEY || !BETA_GROUP_ID) {
      console.error('App Store Connect API not configured');
      return json(res, 503, { message: 'Beta signup is not yet available. Please try again later.' });
    }

    // Add tester
    try {
      const result = await addBetaTester(email, firstName, lastName);
      console.log(`Beta signup: ${email} (${firstName} ${lastName}) — ${result.existing ? 'already exists' : 'added'}`);
      return json(res, 200, { message: 'TestFlight invitation sent! Check your email.' });
    } catch (err) {
      console.error(`Beta signup failed for ${email}:`, err.message);
      return json(res, 502, { message: 'Failed to send invitation. Please try again later.' });
    }
  }

  // 404
  json(res, 404, { message: 'Not found' });
});

server.listen(PORT, '0.0.0.0', () => {
  const configured = !!(ASC_KEY_ID && ASC_ISSUER_ID && ASC_PRIVATE_KEY && BETA_GROUP_ID);
  console.log(`SSDID Beta API running on :${PORT}`);
  console.log(`App Store Connect: ${configured ? 'configured' : 'NOT configured (set env vars)'}`);
  console.log(`CORS origin: ${CORS_ORIGIN}`);
  console.log(`Rate limit: ${RATE_LIMIT_PER_HOUR}/hour per IP`);
});
