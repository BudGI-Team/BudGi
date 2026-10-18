# Budgi API — Standardized Reference

All responses follow the same envelope. No exceptions.

---

## Response Envelope

```json
// Success
{
  "success": true,
  "message": "Human-readable status",
  "data": { ... },
  "meta": { "page": 1, "limit": 50, "total": 120, "pages": 3 }
}

// Error
{
  "success": false,
  "message": "What went wrong",
  "data": null,
  "errors": [
    { "field": "amount", "message": "amount must be a positive number" }
  ]
}
```

`meta` is only present on paginated list endpoints.  
`errors` is only present on validation failures (400) and conflicts (409).

---

## Authentication

All protected endpoints require:

```
Authorization: Bearer <jwt_token>
```

---

## POST /api/auth/register

**Request**

```json
{
  "name": "Arjun Sharma",
  "email": "arjun@example.com",
  "password": "mypassword123",
  "currency": "INR",
  "monthlyIncome": 80000
}
```

**Response 201**

```json
{
  "success": true,
  "message": "Account created",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5...",
    "user": {
      "_id": "664f1a2b3c4d5e6f7a8b9c0d",
      "name": "Arjun Sharma",
      "email": "arjun@example.com",
      "currency": "INR",
      "monthlyIncome": 80000
    }
  }
}
```

**Response 400 — Validation**

```json
{
  "success": false,
  "message": "Password must be at least 8 characters",
  "data": null
}
```

---

## POST /api/auth/login

**Request**

```json
{ "email": "arjun@example.com", "password": "mypassword123" }
```

**Response 200**

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5...",
    "user": { "_id": "664f...", "name": "Arjun Sharma", "email": "arjun@example.com" }
  }
}
```

**Response 401**

```json
{ "success": false, "message": "Invalid credentials", "data": null }
```

---

## GET /api/transactions

**Query params** — all optional

| Param     | Type              | Default | Notes                |
| --------- | ----------------- | ------- | -------------------- |
| page      | number            | 1       |                      |
| limit     | number            | 50      | Max 200              |
| sort      | string            | date    | Any field name       |
| order     | asc \| desc       | desc    |                      |
| type      | income \| expense | —       |                      |
| category  | string            | —       | Exact match          |
| startDate | ISO date          | —       |                      |
| endDate   | ISO date          | —       |                      |
| search    | string            | —       | Regex on description |

**Response 200**

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "transactions": [
      {
        "_id": "664f1a2b3c4d5e6f7a8b9c01",
        "userId": "664f1a2b3c4d5e6f7a8b9c0d",
        "amount": 1200,
        "type": "expense",
        "category": "Food & Dining",
        "description": "Dinner at Barbeque Nation",
        "merchant": "Barbeque Nation",
        "date": "2025-04-20T00:00:00.000Z",
        "source": "manual",
        "isRecurring": false,
        "isAnomaly": false,
        "createdAt": "2025-04-20T14:32:00.000Z"
      }
    ]
  },
  "meta": { "page": 1, "limit": 50, "total": 214, "pages": 5 }
}
```

---

## POST /api/transactions — Single

Creates one transaction. If `amount` is a string it will be coerced; a non-numeric value returns 400.

**Request**

```json
{
  "amount": 1200,
  "type": "expense",
  "date": "2025-04-20",
  "description": "Dinner at Barbeque Nation",
  "merchant": "Barbeque Nation",
  "category": "Food & Dining"
}
```

`category` is optional — AI auto-categorizes if omitted.

**Response 201**

```json
{
  "success": true,
  "message": "Transaction created",
  "data": {
    "transaction": {
      "_id": "664f1a2b3c4d5e6f7a8b9c01",
      "amount": 1200,
      "type": "expense",
      "category": "Food & Dining",
      "merchant": "Barbeque Nation",
      "date": "2025-04-20T00:00:00.000Z",
      "source": "manual"
    }
  }
}
```

**Response 400 — Validation**

```json
{
  "success": false,
  "message": "amount must be a positive number; type is required",
  "data": null,
  "errors": [
    { "field": "amount", "message": "amount must be a positive number" },
    { "field": "type", "message": "type is required" }
  ]
}
```

**Response 409 — Duplicate**

```json
{
  "success": false,
  "message": "Duplicate transaction — same amount, merchant, and date already recorded",
  "data": null,
  "errors": [
    {
      "field": "_fingerprint",
      "message": "Transaction already exists",
      "existingId": "664f1a2b3c4d5e6f7a8b9c01"
    }
  ]
}
```

---

## POST /api/transactions — Bulk

Same endpoint. Send `{ "transactions": [...] }` to insert up to 500 at once.  
Duplicates are silently skipped (counted, not rejected).

**Request**

```json
{
  "transactions": [
    {
      "amount": 500,
      "type": "expense",
      "date": "2025-04-18",
      "merchant": "Swiggy",
      "description": "Lunch order"
    },
    {
      "amount": 45000,
      "type": "income",
      "date": "2025-04-01",
      "description": "Salary April",
      "category": "Salary"
    }
  ]
}
```

**Response 200**

```json
{
  "success": true,
  "message": "2 transaction(s) imported",
  "data": {
    "imported": 2,
    "duplicates": 0,
    "failed": 0,
    "total": 2
  }
}
```

**Response 400 — Validation (all errors returned at once)**

```json
{
  "success": false,
  "message": "transactions[0].amount must be a positive number; transactions[1].type is required",
  "data": null,
  "errors": [
    { "field": "transactions[0].amount", "message": "amount must be a positive number" },
    { "field": "transactions[1].type", "message": "type is required" }
  ]
}
```

---

## PUT /api/transactions/:id

All fields optional — only sent fields are updated.

**Request**

```json
{ "amount": 1350, "category": "Entertainment" }
```

**Response 200**

```json
{
  "success": true,
  "message": "Transaction updated",
  "data": {
    "transaction": { "_id": "664f...", "amount": 1350, "category": "Entertainment" }
  }
}
```

---

## DELETE /api/transactions/:id

**Response 200**

```json
{ "success": true, "message": "Transaction deleted", "data": {} }
```

**Response 404**

```json
{ "success": false, "message": "Transaction not found", "data": null }
```

---

## GET /api/transactions/summary

**Query params** — `startDate`, `endDate` (both optional ISO dates)

**Response 200**

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "totalIncome": 80000,
    "totalExpense": 34500,
    "netSavings": 45500,
    "savingsRate": 57,
    "incomeCount": 1,
    "expenseCount": 23,
    "byCategory": [
      { "category": "Food & Dining", "total": 8200, "count": 9, "percentage": 24 },
      { "category": "Transport", "total": 4100, "count": 6, "percentage": 12 }
    ]
  }
}
```

---

## POST /api/transactions/import (CSV)

Multipart form upload, field name `file`.

**Response 200**

```json
{
  "success": true,
  "message": "47 transaction(s) imported",
  "data": {
    "imported": 47,
    "duplicates": 3,
    "failed": 0,
    "total": 50,
    "skipped": 0,
    "parseErrors": 0
  }
}
```

---

## GET /api/ai/insights

**Response 200**

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "insights": [
      {
        "type": "spending_spike",
        "title": "Food spending up 34%",
        "detail": "You spent ₹8,200 on Food & Dining this month vs ₹6,100 last month.",
        "severity": "warning"
      }
    ],
    "generatedAt": "2025-04-20T14:00:00.000Z"
  }
}
```

---

## POST /api/ai/chat

**Request**

```json
{
  "messages": [{ "role": "user", "content": "Where am I overspending this month?" }]
}
```

Max 20 messages per request. Valid roles: `user`, `assistant`, `system`.

**Response 200**

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "reply": "Based on your last 60 days, Food & Dining is your highest category at ₹8,200 — about 24% of your total spend. That's up from ₹6,100 last month. Consider setting a budget cap for it."
  }
}
```

**Response 400 — Validation**

```json
{
  "success": false,
  "message": "messages must be a non-empty array",
  "data": null,
  "errors": [{ "field": "messages", "message": "messages must be a non-empty array" }]
}
```

---

## GET /api/ai/suggestions

**Response 200**

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "suggestions": [
      {
        "title": "Set a Food budget",
        "detail": "You have no budget cap for Food & Dining. Based on your history, ₹7,000/month would save you ~₹1,200.",
        "action": "create_budget",
        "params": { "category": "Food & Dining", "limit": 7000 }
      }
    ]
  }
}
```

---

## GET /api/budgets

**Response 200**

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "budgets": [
      {
        "_id": "664f...",
        "category": "Food & Dining",
        "limit": 7000,
        "period": "monthly",
        "alertAt": 0.9,
        "color": "#f97316",
        "spent": 8200,
        "remaining": 0,
        "percentage": 117,
        "isExceeded": true,
        "isWarning": true
      }
    ]
  }
}
```

---

## GET /api/alerts

**Query params** — `unreadOnly=true`, `limit` (max 100, default 20)

**Response 200**

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "alerts": [
      {
        "_id": "664f...",
        "type": "budget_exceeded",
        "title": "Food & Dining budget exceeded",
        "message": "You've spent ₹8,200 of your ₹7,000 budget (117%).",
        "read": false,
        "createdAt": "2025-04-20T09:15:00.000Z"
      }
    ],
    "unreadCount": 3
  }
}
```

---

## Deduplication

A SHA-256 fingerprint is computed from `userId + amount + merchant + date(day)`.

- **Single POST**: returns 409 with `existingId` if the fingerprint exists.
- **Bulk POST**: duplicates are silently skipped and counted in `data.duplicates`.
- **CSV import**: same as bulk.

The fingerprint updates automatically if you `PUT` a transaction's amount, merchant, or date.

---

## HTTP Status Codes

| Code | Meaning                                              |
| ---- | ---------------------------------------------------- |
| 200  | Success                                              |
| 201  | Created                                              |
| 400  | Validation error — check `errors[]` for field detail |
| 401  | Missing / invalid / expired token                    |
| 403  | Forbidden                                            |
| 404  | Resource not found                                   |
| 409  | Conflict (duplicate)                                 |
| 429  | Rate limit exceeded                                  |
| 500  | Internal server error                                |

---

## Rate Limits

| Scope         | Limit                   |
| ------------- | ----------------------- |
| All routes    | 200 req / 15 min per IP |
| `/api/auth/*` | 20 req / 15 min per IP  |
