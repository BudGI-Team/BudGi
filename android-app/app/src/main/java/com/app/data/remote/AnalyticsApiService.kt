package com.app.data.remote

import retrofit2.http.GET

interface AnalyticsApiService {
    @GET("health")
    suspend fun healthCheck(): Map<String, String>
}
