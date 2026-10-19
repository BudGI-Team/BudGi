# BudGi — Smart AI Finance & Analytics Tracker 📊🤖

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Node Version](https://img.shields.io/badge/node-%3E%3D18-green.svg)](https://nodejs.org/)

BudGi is an industry-grade personal finance and automated budgeting ecosystem. It leverages cutting-edge LLMs (via Groq) to parse user messages, categorize transactions, auto-generate spending recommendations, run smart budgets, detect anomalous/recurring spikes, and notify users via WebSockets in real time.

---

## ⚡ Core Features
- **🤖 AI Insights & Recommendations**: Proactive suggestions based on spending patterns, automatically powered by the Groq SDK.
- **💬 Conversational Financial Copilot**: Native AI chat to ask questions about your monthly overspending.
- **📊 Real-time Sync & WebSockets**: Instant updates on budgets, goals, and transaction alterations across devices.
- **🔒 Production-grade Security**: Equipped with NoSQL injection cleaning, prototype pollution protection, rate limiting, and standard encryption envelope.
- **🌐 Shared Spaces**: Collaborative budgets and goal tracking with friends and family.
- **📱 Android Integration**: Direct synchronization for financial SMS messages parsing and Android Deep Linking verified configurations.

---

## 🛠️ Tech Stack

### Frontend
- **Framework**: React 18, Vite, React Router v6
- **State Management**: Zustand (Persisted)
- **Data Fetching**: TanStack React Query v5
- **Animations/Styles**: Framer Motion, CSS Variables (Custom dark/light themes)
- **Charts/Graphics**: Recharts, Three.js (interactive 3D Wealth Orb)

### Backend & Database
- **Runtime**: Node.js (Express)
- **Database**: MongoDB (Mongoose ODM)
- **Caching**: Redis (using `ioredis`) with automatic in-memory map fallback
- **Scheduling**: Node-cron (daily alert rules, weekly digests, nightly summaries)

---

## 🏗️ Project Directory Structure

```
BudGi/
├── .github/workflows/      # Automated CI pipeline configurations
├── docker-compose.yml      # Dev-ready local MongoDB & Redis services
├── android-app/            # Native Kotlin Android application
└── BudGi-production/       # Main Monorepo root
    ├── package.json        # Central monorepo scripts (ESLint, Prettier, Husky)
    ├── client/             # Vite + React web application client
    └── server/             # Express API engine (clean architecture)
        ├── src/
        │   ├── app.js      # App middleware stack
        │   ├── server.js   # Boot sequence & WebSocket attaching
        │   ├── config/     # db and validated env setup
        │   ├── middleware/ # authentication, rateLimiters, error handler
        │   └── modules/    # modular controller-service-repository units
        └── src/__tests__/  # Auth and Transaction integration test suites
```

---

## 🚀 Quick Start (Local Development)

### 1. Prerequisite Setup
Make sure you have [Node.js (>=18)](https://nodejs.org/), [MongoDB](https://www.mongodb.com/), and [Redis](https://redis.io/) installed. Or run them via docker compose:
```bash
docker compose up -d
```

### 2. Configure Environment Variables
Copy the env templates to your local configs:

**For Server:**
```bash
cp BudGi-production/server/.env.example BudGi-production/server/.env
```
Update `MONGO_URI` and `JWT_SECRET` inside `BudGi-production/server/.env`.

**For Client:**
```bash
cp BudGi-production/client/.env.example BudGi-production/client/.env
```

### 3. Install & Start Development
From the root directory:
```bash
cd BudGi-production
npm run install:all
```
Run both servers concurrently:
```bash
# Server runs on http://localhost:5000
npm run dev:server

# Client runs on http://localhost:5173
npm run dev:client
```

---

## 🧪 Testing, Linting & Formatting

Validate your changes align with quality criteria:

```bash
cd BudGi-production

# Run all backend integration tests
npm run test --prefix server

# Run linter checks
npm run lint

# Format code with Prettier
npm run format
```

---

## 🔌 API Documentation
A detailed REST specification is provided in [API_REFERENCE.md](BudGi-production/server/API_REFERENCE.md).

All responses follow the standard envelope format:
```json
{
  "success": true,
  "message": "Human-readable status",
  "data": { ... }
}
```

---

## 👥 Developers
- **Anuj** ([@anujrathore9](https://github.com/anujrathore9))
- **Divyanshu** ([@DivyanshuDixit01](https://github.com/DivyanshuDixit01))

