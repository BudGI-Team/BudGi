// __tests__/transactions.test.js
// Integration tests for the transactions module.
// Tests CRUD, summary, validation, and pagination.

'use strict';

const http = require('http');
const app = require('../app');

let server;
let baseUrl;
let authToken;

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
  name: 'Txn Tester',
  email: 'txn@budgi.com',
  password: 'SecurePass123!',
  currency: 'INR',
  monthlyIncome: 60000,
};

const sampleExpense = {
  amount: 500,
  type: 'expense',
  category: 'Food & Dining',
  description: 'Lunch at cafe',
  date: '2025-04-15',
};

const sampleIncome = {
  amount: 60000,
  type: 'income',
  category: 'Salary',
  description: 'April salary',
  date: '2025-04-01',
};

let userCount = 0;

beforeAll(async () => {
  server = http.createServer(app);
  await new Promise((resolve) => {
    server.listen(0, () => {
      const { port } = server.address();
      baseUrl = `http://localhost:${port}`;
      resolve();
    });
  });
});

beforeEach(async () => {
  userCount++;
  const uniqueUser = {
    ...testUser,
    email: `txn-test-${userCount}-${Date.now()}@budgi.com`,
  };
  const res = await request('POST', '/api/auth/register', { body: uniqueUser });
  authToken = res.body.data.token;
});

afterAll((done) => {
  server.close(done);
});

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('POST /api/transactions', () => {
  test('creates a single transaction', async () => {
    const res = await request('POST', '/api/transactions', {
      body: sampleExpense,
      token: authToken,
    });

    expect(res.status).toBe(201);
    expect(res.body.success).toBe(true);
    expect(res.body.data.transaction).toBeDefined();
    expect(res.body.data.transaction.amount).toBe(500);
    expect(res.body.data.transaction.type).toBe('expense');
    expect(res.body.data.transaction.category).toBe('Food & Dining');
  });

  test('rejects transaction without auth', async () => {
    const res = await request('POST', '/api/transactions', {
      body: sampleExpense,
    });

    expect(res.status).toBe(401);
    expect(res.body.success).toBe(false);
  });

  test('rejects transaction with missing amount', async () => {
    const res = await request('POST', '/api/transactions', {
      body: { type: 'expense', description: 'No amount' },
      token: authToken,
    });

    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
  });

  test('rejects transaction with invalid type', async () => {
    const res = await request('POST', '/api/transactions', {
      body: { amount: 100, type: 'refund', description: 'Invalid type' },
      token: authToken,
    });

    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
  });
});

describe('GET /api/transactions', () => {
  beforeEach(async () => {
    // Seed some transactions
    await request('POST', '/api/transactions', { body: sampleExpense, token: authToken });
    await request('POST', '/api/transactions', { body: sampleIncome, token: authToken });
  });

  test('lists transactions for authenticated user', async () => {
    const res = await request('GET', '/api/transactions', { token: authToken });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.transactions).toBeDefined();
    expect(Array.isArray(res.body.data.transactions)).toBe(true);
    expect(res.body.data.transactions.length).toBeGreaterThanOrEqual(2);
  });

  test('returns pagination metadata', async () => {
    const res = await request('GET', '/api/transactions?page=1&limit=1', { token: authToken });

    expect(res.status).toBe(200);
    expect(res.body.meta).toBeDefined();
    expect(res.body.meta.page).toBe(1);
    expect(res.body.meta.limit).toBe(1);
    expect(res.body.meta.total).toBeGreaterThanOrEqual(2);
  });

  test('filters by type', async () => {
    const res = await request('GET', '/api/transactions?type=income', { token: authToken });

    expect(res.status).toBe(200);
    const types = res.body.data.transactions.map((t) => t.type);
    expect(types.every((t) => t === 'income')).toBe(true);
  });

  test('rejects without auth', async () => {
    const res = await request('GET', '/api/transactions');

    expect(res.status).toBe(401);
  });
});

describe('PUT /api/transactions/:id', () => {
  test('updates a transaction', async () => {
    // Create one
    const created = await request('POST', '/api/transactions', {
      body: sampleExpense,
      token: authToken,
    });
    const id = created.body.data.transaction._id;

    // Update it
    const res = await request('PUT', `/api/transactions/${id}`, {
      body: { amount: 750, description: 'Updated lunch' },
      token: authToken,
    });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.transaction.amount).toBe(750);
    expect(res.body.data.transaction.description).toBe('Updated lunch');
  });

  test('returns 404 for non-existent transaction', async () => {
    const res = await request('PUT', '/api/transactions/000000000000000000000000', {
      body: { amount: 100 },
      token: authToken,
    });

    expect(res.status).toBe(404);
    expect(res.body.success).toBe(false);
  });
});

describe('DELETE /api/transactions/:id', () => {
  test('deletes a transaction', async () => {
    const created = await request('POST', '/api/transactions', {
      body: sampleExpense,
      token: authToken,
    });
    const id = created.body.data.transaction._id;

    const res = await request('DELETE', `/api/transactions/${id}`, { token: authToken });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
  });

  test('returns 404 for non-existent transaction', async () => {
    const res = await request('DELETE', '/api/transactions/000000000000000000000000', {
      token: authToken,
    });

    expect(res.status).toBe(404);
    expect(res.body.success).toBe(false);
  });
});

describe('GET /api/transactions/summary', () => {
  beforeEach(async () => {
    await request('POST', '/api/transactions', { body: sampleExpense, token: authToken });
    await request('POST', '/api/transactions', { body: sampleIncome, token: authToken });
  });

  test('returns financial summary', async () => {
    const res = await request('GET', '/api/transactions/summary', { token: authToken });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data).toHaveProperty('totalIncome');
    expect(res.body.data).toHaveProperty('totalExpense');
    expect(res.body.data).toHaveProperty('netSavings');
    expect(res.body.data.totalIncome).toBeGreaterThanOrEqual(0);
    expect(res.body.data.totalExpense).toBeGreaterThanOrEqual(0);
  });
});
