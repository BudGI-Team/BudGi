package com.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Standard backend response envelope.
 * Backend always returns: { success: boolean, message: string, data: T }
 */
data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: T?,
    @SerializedName("errors") val errors: List<ApiErrorDetail>? = null
)

data class ApiErrorDetail(
    @SerializedName("field") val field: String? = null,
    @SerializedName("message") val message: String
)

/**
 * Paginated response with meta.
 */
data class PaginatedResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: T?,
    @SerializedName("meta") val meta: PaginationMeta?
)

data class PaginationMeta(
    @SerializedName("page") val page: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("total") val total: Int,
    @SerializedName("pages") val pages: Int
)
