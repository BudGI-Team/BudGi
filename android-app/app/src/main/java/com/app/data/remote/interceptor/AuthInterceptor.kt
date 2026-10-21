package com.app.data.remote.interceptor

import com.app.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches Bearer token to all outgoing requests.
 * Skips auth endpoints (login, register, refresh).
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        // Skip auth for public endpoints
        if (path.contains("/auth/login") ||
            path.contains("/auth/register") ||
            path.contains("/auth/refresh") ||
            path.contains("/auth/forgot-password") ||
            path.contains("/auth/reset-password") ||
            path.contains("/auth/verify-email") ||
            path.contains("/auth/phone/") ||
            path.contains("/auth/firebase/") ||
            path.contains("/auth/google") ||
            path.contains("/health")
        ) {
            return chain.proceed(request)
        }

        val token = tokenManager.accessToken
        if (!token.isNullOrBlank()) {
            val authenticatedRequest = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            return chain.proceed(authenticatedRequest)
        }

        return chain.proceed(request)
    }
}
