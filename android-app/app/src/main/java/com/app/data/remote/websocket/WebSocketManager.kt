package com.app.data.remote.websocket

import android.util.Log
import com.app.BuildConfig
import com.app.data.local.TokenManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

@Singleton
class WebSocketManager @Inject constructor(
    private val tokenManager: TokenManager
) {
    private val TAG = "WebSocketManager"
    private val gson = Gson()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var waitingForPong = false

    private val _events = MutableSharedFlow<WebSocketEvent>(replay = 0, extraBufferCapacity = 20)
    val events: SharedFlow<WebSocketEvent> = _events.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect() {
        val token = tokenManager.accessToken ?: return
        if (isConnected || webSocket != null) return

        val baseUrl = BuildConfig.API_BASE_URL
        val wsUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://") + "/ws"

        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected, sending auth")
                val authMsg = gson.toJson(mapOf("type" to "AUTH", "token" to token))
                webSocket.send(authMsg)
                isConnected = true
                reconnectAttempt = 0
                startPing(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = gson.fromJson(text, JsonObject::class.java)
                    val type = msg.get("type")?.asString ?: return

                    when (type) {
                        "AUTH_OK" -> {
                            Log.d(TAG, "WebSocket authenticated")
                            scope.launch { _events.emit(WebSocketEvent.Connected) }
                        }
                        "PONG" -> { 
                            waitingForPong = false
                        }
                        "TRANSACTION_ADDED" -> scope.launch { _events.emit(WebSocketEvent.TransactionAdded) }
                        "TRANSACTION_DELETED" -> scope.launch { _events.emit(WebSocketEvent.TransactionDeleted) }
                        "IMPORT_DONE" -> scope.launch { _events.emit(WebSocketEvent.ImportDone) }
                        "BUDGET_ADDED", "BUDGET_UPDATED", "BUDGET_DELETED" -> scope.launch { _events.emit(WebSocketEvent.BudgetsUpdated) }
                        "GOAL_ADDED", "GOAL_UPDATED", "GOAL_DELETED" -> scope.launch { _events.emit(WebSocketEvent.GoalsUpdated) }
                        "ALERT_NEW" -> {
                            val message = msg.getAsJsonObject("data")?.get("message")?.asString
                            val severity = msg.getAsJsonObject("data")?.get("severity")?.asString
                            scope.launch { _events.emit(WebSocketEvent.AlertNew(message, severity)) }
                        }
                        "BUDGET_EXCEEDED" -> {
                            val category = msg.getAsJsonObject("data")?.get("category")?.asString
                            scope.launch { _events.emit(WebSocketEvent.BudgetExceeded(category)) }
                        }
                        "HEALTH_SCORE" -> scope.launch { _events.emit(WebSocketEvent.HealthScoreUpdated) }
                        "WEEKLY_SUMMARY" -> scope.launch { _events.emit(WebSocketEvent.WeeklySummary) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing WS message", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                isConnected = false
                stopPing()
                if (code != 1000) scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                isConnected = false
                stopPing()
                scheduleReconnect()
            }
        })
    }

    fun disconnect() {
        reconnectJob?.cancel()
        stopPing()
        webSocket?.close(1000, "logout")
        webSocket = null
        isConnected = false
        reconnectAttempt = 0
    }

    private fun startPing(ws: WebSocket) {
        stopPing()
        waitingForPong = false
        pingJob = scope.launch {
            while (isActive) {
                delay(30_000)
                if (isConnected) {
                    if (waitingForPong) {
                        Log.e(TAG, "WebSocket ping timeout")
                        ws.close(1000, "ping_timeout")
                        scheduleReconnect()
                        break
                    }
                    waitingForPong = true
                    ws.send(gson.toJson(mapOf("type" to "PING")))
                }
            }
        }
    }

    private fun stopPing() {
        pingJob?.cancel()
        pingJob = null
    }

    private fun scheduleReconnect() {
        if (tokenManager.accessToken == null) return

        if (reconnectAttempt >= 30) {
            Log.e(TAG, "Max reconnect attempts reached. Giving up.")
            scope.launch { _events.emit(WebSocketEvent.Disconnected) }
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val backoff = min(1000L * 2.0.pow(reconnectAttempt.toDouble()).toLong(), 30_000L)
            reconnectAttempt++
            Log.d(TAG, "Reconnecting in ${backoff}ms (attempt $reconnectAttempt)")
            delay(backoff)
            webSocket = null
            connect()
        }
    }
}

sealed interface WebSocketEvent {
    data object Connected : WebSocketEvent
    data object Disconnected : WebSocketEvent
    data object TransactionAdded : WebSocketEvent
    data object TransactionDeleted : WebSocketEvent
    data object ImportDone : WebSocketEvent
    data object BudgetsUpdated : WebSocketEvent
    data object GoalsUpdated : WebSocketEvent
    data class AlertNew(val message: String?, val severity: String?) : WebSocketEvent
    data class BudgetExceeded(val category: String?) : WebSocketEvent
    data object HealthScoreUpdated : WebSocketEvent
    data object WeeklySummary : WebSocketEvent
}
