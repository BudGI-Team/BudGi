// src/services/socketService.js
/**
 * WebSocket Service — Real-time alert & event push to frontend
 *
 * Uses the native Node.js 'ws' library (lightweight, no socket.io dependency).
 * Each authenticated client opens one persistent WS connection.
 * The server can push events to specific users without polling.
 *
 * Event types:
 *   → ALERT_NEW          new alert triggered (budget, anomaly, etc.)
 *   → HEALTH_SCORE       updated health score
 *   → IMPORT_DONE        CSV import completed
 *   → TRANSACTION_ADDED  new transaction created
 *
 * Auth: client sends   { type: 'AUTH', token: '<jwt>' }  as first message.
 *       Server replies { type: 'AUTH_OK', userId }  or closes with 4001.
 */

'use strict';

const { WebSocketServer } = require('ws');
const jwt = require('jsonwebtoken');
const env = require('../config/env');
const logger = require('./logger');

// userId → Set<WebSocket>  (one user may have multiple tabs open)
const _clients = new Map();

let _wss = null;

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Attach a WebSocket server to an existing HTTP server.
 * Call this once from server.js after app.listen().
 *
 * @param {import('http').Server} httpServer
 */
function attach(httpServer) {
  if (_wss) return; // Already attached

  _wss = new WebSocketServer({
    server: httpServer,
    path: '/ws',
  });

  _wss.on('connection', (ws, _req) => {
    let userId = null;
    let authTimer = null;

    // Require auth within 10 seconds
    authTimer = setTimeout(() => {
      ws.close(4001, 'Authentication timeout');
    }, 10_000);

    ws.on('message', (raw) => {
      let msg;
      try {
        msg = JSON.parse(raw.toString());
      } catch {
        return;
      }

      if (msg.type === 'AUTH') {
        clearTimeout(authTimer);
        try {
          const payload = jwt.verify(msg.token, env.JWT_SECRET);
          userId = payload.id.toString();

          // Register client
          if (!_clients.has(userId)) _clients.set(userId, new Set());
          _clients.get(userId).add(ws);

          ws.send(JSON.stringify({ type: 'AUTH_OK', userId }));
          logger.info(`[WS] User ${userId} connected (${_clients.get(userId).size} tabs)`);
        } catch {
          ws.close(4001, 'Invalid token');
        }
        return;
      }

      // Ping/pong keepalive
      if (msg.type === 'PING') {
        ws.send(JSON.stringify({ type: 'PONG', ts: Date.now() }));
      }
    });

    ws.on('close', () => {
      if (userId && _clients.has(userId)) {
        _clients.get(userId).delete(ws);
        if (_clients.get(userId).size === 0) {
          _clients.delete(userId);
        }
      }
    });

    ws.on('error', (err) => logger.error('[WS] Socket error:', err));
  });

  logger.info('[WS] WebSocket server attached at /ws');
}

/**
 * Push an event payload to all connected sockets for a given user.
 *
 * @param {string} userId
 * @param {string} type   — event type string
 * @param {object} data   — payload
 */
function push(userId, type, data = {}) {
  const sockets = _clients.get(userId.toString());
  if (!sockets || sockets.size === 0) return;

  const msg = JSON.stringify({ type, data, ts: Date.now() });

  for (const ws of sockets) {
    if (ws.readyState === 1 /* OPEN */) {
      try {
        ws.send(msg);
      } catch (e) {
        /* ignore send errors */
      }
    }
  }
}

/**
 * Broadcast to ALL connected users (use sparingly — e.g. maintenance notice).
 */
function broadcast(type, data = {}) {
  const msg = JSON.stringify({ type, data, ts: Date.now() });
  for (const [, sockets] of _clients) {
    for (const ws of sockets) {
      if (ws.readyState === 1) {
        try {
          ws.send(msg);
        } catch {}
      }
    }
  }
}

/**
 * Number of currently connected unique users.
 */
function connectedCount() {
  return _clients.size;
}

/**
 * Emit an event to all members of a space.
 *
 * @param {object} space - The populated Space mongoose document
 * @param {string} type - Event type string
 * @param {object} data - Payload
 */
function emitToSpace(space, type, data = {}) {
  if (!space || !space.members) return;

  for (const member of space.members) {
    if (member.userId) {
      push(member.userId.toString(), type, data);
    }
  }
}

// ── Event type constants ──────────────────────────────────────────────────────
const EVENTS = {
  ALERT_NEW: 'ALERT_NEW',
  HEALTH_SCORE: 'HEALTH_SCORE',
  IMPORT_DONE: 'IMPORT_DONE',
  TRANSACTION_ADDED: 'TRANSACTION_ADDED',
  TRANSACTION_DELETED: 'TRANSACTION_DELETED',
  BUDGET_ADDED: 'BUDGET_ADDED',
  BUDGET_UPDATED: 'BUDGET_UPDATED',
  BUDGET_DELETED: 'BUDGET_DELETED',
  BUDGET_EXCEEDED: 'BUDGET_EXCEEDED',
  GOAL_ADDED: 'GOAL_ADDED',
  GOAL_UPDATED: 'GOAL_UPDATED',
  GOAL_DELETED: 'GOAL_DELETED',
  WEEKLY_SUMMARY: 'WEEKLY_SUMMARY',
  SPACE_UPDATED: 'SPACE_UPDATED',
};

module.exports = { attach, push, broadcast, connectedCount, emitToSpace, EVENTS };
