package com.app.data.remote.api

import com.app.data.remote.dto.ApiResponse
import com.app.data.remote.dto.TransactionDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class SmsBatchRequest(
    val messages: List<SmsMessageDto>
)

data class SmsMessageDto(
    val text: String,
    val sender: String,
    val timestamp: Long
)

data class SmsParseResultData(
    val transactions: List<TransactionDto>,
    val failed: List<Any>,
    val total: Int
)

data class ScamRequest(
    val text: String
)

data class ScamResponse(
    val isScam: Boolean,
    val reason: String
)

interface SmsApiService {
    @POST("api/v1/sms/parse-llm")
    suspend fun parseLLM(@Body request: SmsBatchRequest): Response<ApiResponse<SmsParseResultData>>

    @POST("api/v1/sms/verify-scam")
    suspend fun verifyScam(@Body request: ScamRequest): Response<ApiResponse<ScamResponse>>
}
