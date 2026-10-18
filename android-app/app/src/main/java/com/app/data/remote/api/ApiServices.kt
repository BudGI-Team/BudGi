package com.app.data.remote.api

import com.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface BudgetApiService {
    @GET("api/v1/budgets")
    suspend fun list(): Response<ApiResponse<BudgetListData>>

    @POST("api/v1/budgets")
    suspend fun create(@Body budget: BudgetDto): Response<ApiResponse<BudgetSingleData>>

    @PUT("api/v1/budgets/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body budget: BudgetDto
    ): Response<ApiResponse<BudgetSingleData>>

    @DELETE("api/v1/budgets/{id}")
    suspend fun delete(@Path("id") id: String): Response<ApiResponse<Any>>
}

interface GoalApiService {
    @GET("api/v1/goals")
    suspend fun list(): Response<ApiResponse<GoalListData>>

    @POST("api/v1/goals")
    suspend fun create(@Body goal: GoalDto): Response<ApiResponse<GoalSingleData>>

    @PUT("api/v1/goals/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body changes: Map<String, @JvmSuppressWildcards Any>
    ): Response<ApiResponse<GoalSingleData>>

    @DELETE("api/v1/goals/{id}")
    suspend fun delete(@Path("id") id: String): Response<ApiResponse<Any>>
}


interface SyncApiService {
    @POST("api/v1/sync")
    suspend fun fullSync(@Body request: SyncRequest): Response<ApiResponse<SyncResponseData>>

    @GET("api/v1/sync/pull")
    suspend fun deltaPull(@Query("since") since: String?): Response<ApiResponse<DeltaPullData>>
}

interface AnalyticsApiService {
    @GET("api/v1/analytics/summary")
    suspend fun summary(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<ApiResponse<AnalyticsSummaryData>>

    @GET("health")
    suspend fun healthCheck(): Response<ApiResponse<Any>>
}
