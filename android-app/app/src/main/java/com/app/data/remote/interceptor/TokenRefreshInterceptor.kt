package com.app.data.remote.interceptor

import com.app.data.local.TokenManager
import com.app.data.remote.dto.ApiResponse
import com.app.data.remote.dto.AuthData
import com.app.data.remote.dto.RefreshRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenRefreshInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {
    private val gson = Gson()
    private val lock = Any()
    
    // Use a separate client for refresh so we don't re-trigger AuthInterceptor (§4.6)
    private val refreshClient = OkHttpClient.Builder().build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code != 401 ||
            request.url.encodedPath.contains("/auth/login") ||
            request.url.encodedPath.contains("/auth/register") ||
            request.url.encodedPath.contains("/auth/refresh")
        ) {
            return response
        }

        synchronized(lock) {
            val currentToken = tokenManager.accessToken
            val requestToken = request.header("Authorization")?.removePrefix("Bearer ")
            if (currentToken != null && currentToken != requestToken) {
                response.close()
                val newRequest = request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
                return chain.proceed(newRequest)
            }

            val refreshToken = tokenManager.refreshToken ?: run {
                tokenManager.clearAll()
                return response
            }

            val refreshBody = gson.toJson(RefreshRequest(refreshToken))
            val refreshRequest = Request.Builder()
                .url(request.url.newBuilder()
                    .encodedPath("/api/v1/auth/refresh")
                    .build())
                .post(refreshBody.toRequestBody("application/json".toMediaType()))
                .build()

            val refreshResponse = refreshClient.newCall(refreshRequest).execute()
            if (refreshResponse.isSuccessful) {
                val body = refreshResponse.body?.string()
                refreshResponse.close()

                val type = object : TypeToken<ApiResponse<AuthData>>() {}.type
                val refreshResult: ApiResponse<AuthData>? = try {
                    gson.fromJson(body, type)
                } catch (e: Exception) {
                    null
                }

                if (refreshResult?.success == true && refreshResult.data != null) {
                    val data = refreshResult.data
                    val token = data.token
                    val user = data.user
                    val newRefreshToken = data.refreshToken ?: refreshToken
                    if (token != null && user != null) {
                        tokenManager.saveAuthData(token, newRefreshToken, user)

                        response.close()
                        val retryRequest = request.newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                        return chain.proceed(retryRequest)
                    }
                }
            } else {
                refreshResponse.close()
            }

            tokenManager.clearAll()
        }

        return response
    }
}
