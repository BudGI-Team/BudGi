package com.app.data.remote.api

import com.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface AIChatApiService {
    @POST("api/v1/ai/chat")
    suspend fun chat(@Body request: AIChatRequest): Response<ApiResponse<AIChatResponseData>>

    @GET("api/v1/ai/chat/history")
    suspend fun getHistory(): Response<ApiResponse<AIChatHistoryData>>

    @DELETE("api/v1/ai/chat/history")
    suspend fun clearHistory(): Response<ApiResponse<Any>>

    @GET("api/v1/ai/insights")
    suspend fun getInsights(): Response<ApiResponse<AIInsightsData>>

    @GET("api/v1/ai/health-score")
    suspend fun getHealthScore(): Response<ApiResponse<HealthScoreData>>
}

interface AlertApiService {
    @GET("api/v1/alerts")
    suspend fun list(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<AlertListData>>

    @GET("api/v1/alerts/count")
    suspend fun unreadCount(): Response<ApiResponse<AlertCountData>>

    @PUT("api/v1/alerts/read")
    suspend fun markRead(@Body request: MarkReadRequest): Response<ApiResponse<Any>>
}
