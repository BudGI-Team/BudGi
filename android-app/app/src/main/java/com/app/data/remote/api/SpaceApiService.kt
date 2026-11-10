package com.app.data.remote.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class CreateSpaceRequest(
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String
)

data class AddGoalRequest(
    @SerializedName("title") val title: String,
    @SerializedName("targetAmount") val targetAmount: Double,
    @SerializedName("category") val category: String
)

data class SpaceDto(
    @SerializedName(value = "id", alternate = ["_id"])
    val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("inviteCode") val inviteCode: String? = null,
    @SerializedName("members") val members: List<SpaceMemberDto>? = emptyList(),
    @SerializedName("goals") val goals: List<SpaceGoalDto>? = emptyList()
)

data class SpaceMemberDto(
    @SerializedName("userId") val userId: UserDto? = null,
    @SerializedName("role") val role: String? = "MEMBER",
    @SerializedName("joinedAt") val joinedAt: String? = null
)

data class UserDto(
    @SerializedName(value = "id", alternate = ["_id"])
    val _id: String? = null,
    @SerializedName("firstName") val firstName: String? = null,
    @SerializedName("lastName") val lastName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("profileImage") val profileImage: String? = null
)

data class SpaceGoalDto(
    @SerializedName(value = "id", alternate = ["_id"])
    val _id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("targetAmount") val targetAmount: Double? = 0.0,
    @SerializedName("currentAmount") val currentAmount: Double? = 0.0,
    @SerializedName("category") val category: String? = "Savings"
)

data class SpaceResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: SpaceDataWrapper = SpaceDataWrapper(null, null)
)

data class SpaceDataWrapper(
    @SerializedName("space") val space: SpaceDto? = null,
    @SerializedName("spaces") val spaces: List<SpaceDto>? = null
)

interface SpaceApiService {
    @POST("api/v1/spaces")
    suspend fun createSpace(@Body request: CreateSpaceRequest): SpaceResponse

    @GET("api/v1/spaces")
    suspend fun getSpaces(): SpaceResponse

    @GET("api/v1/spaces/{id}")
    suspend fun getSpaceDetails(@Path("id") id: String): SpaceResponse

    @POST("api/v1/spaces/join/{code}")
    suspend fun joinSpace(@Path("code") code: String): SpaceResponse

    @POST("api/v1/spaces/{id}/goals")
    suspend fun addGoal(@Path("id") id: String, @Body request: AddGoalRequest): SpaceResponse
}
