package com.app.data.remote.api

import com.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface TransactionApiService {
    @GET("api/v1/transactions")
    suspend fun list(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("type") type: String? = null,
        @Query("category") category: String? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<ApiResponse<TransactionListData>>

    @POST("api/v1/transactions")
    suspend fun create(@Body transaction: TransactionDto): Response<ApiResponse<TransactionSingleData>>

    @POST("api/v1/transactions")
    suspend fun bulkCreate(@Body transactions: List<TransactionDto>): Response<ApiResponse<Any>>

    @PUT("api/v1/transactions/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body changes: Map<String, @JvmSuppressWildcards Any>
    ): Response<ApiResponse<TransactionSingleData>>

    @DELETE("api/v1/transactions/{id}")
    suspend fun delete(@Path("id") id: String): Response<ApiResponse<Any>>

    @DELETE("api/v1/transactions/clear")
    suspend fun clearTransactions(): Response<ApiResponse<Any>>

    @GET("api/v1/transactions/summary")
    suspend fun summary(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<ApiResponse<TransactionSummaryData>>

    @Multipart
    @POST("api/v1/transactions/scan")
    suspend fun scanReceipt(
        @Part receipt: okhttp3.MultipartBody.Part
    ): Response<ApiResponse<ScannedReceiptDto>>

    @Multipart
    @POST("api/v1/transactions/import")
    suspend fun importCsv(
        @Part file: okhttp3.MultipartBody.Part
    ): Response<ApiResponse<CsvImportResultDto>>
}
