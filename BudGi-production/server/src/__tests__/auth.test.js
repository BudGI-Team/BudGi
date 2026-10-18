// __tests__/auth.test.js
// Integration tests for the authentication module.
// Tests registration, login, profile, and token validation.

'use strict';

const http = require('http');
const app = require('../app');

let server;
let baseUrl;

// ── Helpers ───────────────────────────────────────────────────────────────────

function request(method, path, { body, token } = {}) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, baseUrl);
    const options = {
      method,
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      headers: { 'Content-Type': 'application/json' },
    };
    if (token) options.headers['Authorization'] = `Bearer ${token}`;

    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => (data += chunk));
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(data) });
        } catch {
          resolve({ status: res.statusCode, body: data });
        }
      });
    });
    req.on('error', reject);
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

const testUser = {
  name: 'Test User',
  email: 'test@budgi.com',
  password: 'SecurePass123!',
  currency: 'INR',
  monthlyIncome: 50000,
};

// ── Setup ─────────────────────────────────────────────────────────────────────

beforeAll((done) => {
  server = http.createServer(app);
  server.listen(0, () => {
    const { port } = server.address();
    baseUrl = `http://localhost:${port}`;
    done();
  });
});

afterAll((done) => {
  server.close(done);
});

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('POST /api/auth/register', () => {
  test('registers a new user and returns token', async () => {
    const res = await request('POST', '/api/auth/register', { body: testUser });

    expect(res.status).toBe(201);
    expect(res.body.success).toBe(true);
    expect(res.body.data.token).toBeDefined();
    expect(res.body.data.user).toBeDefined();
    expect(res.body.data.user.email).toBe(testUser.email);
    expect(res.body.data.user.name).toBe(testUser.name);
    // Password should never be returned
    expect(res.body.data.user.password).toBeUndefined();
  });

  test('rejects duplicate email', async () => {
    // Register once
    await request('POST', '/api/auth/register', { body: testUser });
    // Try again
    const res = await request('POST', '/api/auth/register', { body: testUser });

    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
  });

  test('rejects missing required fields', async () => {
    const res = await request('POST', '/api/auth/register', {
      body: { email: 'incomplete@test.com' },
    });

    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
  });

  test('rejects short password', async () => {
    const res = await request('POST', '/api/auth/register', {
      body: { ...testUser, email: 'short@test.com', password: '123' },
    });

    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
  });
});

describe('POST /api/auth/login', () => {
  beforeEach(async () => {
    await request('POST', '/api/auth/register', { body: testUser });
  });

  test('logs in with correct credentials', async () => {
    const res = await request('POST', '/api/auth/login', {
      body: { email: testUser.email, password: testUser.password },
    });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.token).toBeDefined();
    expect(res.body.data.user.email).toBe(testUser.email);
  });

  test('rejects wrong password', async () => {
    const res = await request('POST', '/api/auth/login', {
      body: { email: testUser.email, password: 'WrongPassword123' },
    });

    expect(res.status).toBe(401);
    expect(res.body.success).toBe(false);
  });

  test('rejects non-existent email', async () => {
    const res = await request('POST', '/api/auth/login', {
      body: { email: 'nobody@test.com', password: 'SomePass123' },
    });

    expect(res.status).toBe(401);
    expect(res.body.success).toBe(false);
  });
});

describe('GET /api/auth/me', () => {
  test('returns user profile with valid token', async () => {
    const reg = await request('POST', '/api/auth/register', { body: testUser });
    const token = reg.body.data.token;

    const res = await request('GET', '/api/auth/me', { token });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.user.email).toBe(testUser.email);
  });

  test('rejects request without token', async () => {
    const res = await request('GET', '/api/auth/me');

    expect(res.status).toBe(401);
    expect(res.body.success).toBe(false);
  });

  test('rejects invalid token', async () => {
    const res = await request('GET', '/api/auth/me', { token: 'invalid.token.here' });

    expect(res.status).toBe(401);
    expect(res.body.success).toBe(false);
  });
});

describe('GET /health', () => {
  test('returns healthy status', async () => {
    const res = await request('GET', '/health');

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.status).toBe('healthy');
  });
});
