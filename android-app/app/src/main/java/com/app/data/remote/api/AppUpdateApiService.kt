package com.app.data.remote.api

import com.app.data.remote.dto.ApiResponse
import com.app.data.remote.dto.AppUpdateDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface AppUpdateApiService {
    @GET("api/v1/mobile/check-update")
    suspend fun checkUpdate(
        @Query("versionCode") versionCode: Int
    ): Response<ApiResponse<AppUpdateDto>>

    @GET("api/v1/mobile/config")
    suspend fun getConfig(): Response<ApiResponse<AppUpdateDto>>
}
