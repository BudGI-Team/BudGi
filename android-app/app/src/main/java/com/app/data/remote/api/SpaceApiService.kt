package com.app.data.remote.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class CreateSpaceRequest(
    val name: String,
    val type: String
)

data class AddGoalRequest(
    val title: String,
    val targetAmount: Double,
    val category: String
)

data class SpaceDto(
    @SerializedName(value = "id", alternate = ["_id"])
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val inviteCode: String? = null,
    val members: List<SpaceMemberDto>? = emptyList(),
    val goals: List<SpaceGoalDto>? = emptyList()
)

data class SpaceMemberDto(
    val userId: UserDto? = null,
    val role: String? = "MEMBER",
    val joinedAt: String? = null
)

data class UserDto(
    @SerializedName(value = "id", alternate = ["_id"])
    val _id: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val profileImage: String? = null
)

data class SpaceGoalDto(
    @SerializedName(value = "id", alternate = ["_id"])
    val _id: String? = null,
    val title: String? = null,
    val targetAmount: Double? = 0.0,
    val currentAmount: Double? = 0.0,
    val category: String? = "Savings"
)

data class SpaceResponse(
    val success: Boolean,
    val message: String?,
    val data: SpaceDataWrapper
)

data class SpaceDataWrapper(
    val space: SpaceDto?,
    val spaces: List<SpaceDto>?
)

interface SpaceApiService {
    @POST("v1/spaces")
    suspend fun createSpace(@Body request: CreateSpaceRequest): SpaceResponse

    @GET("v1/spaces")
    suspend fun getSpaces(): SpaceResponse

    @GET("v1/spaces/{id}")
    suspend fun getSpaceDetails(@Path("id") id: String): SpaceResponse

    @POST("v1/spaces/join/{code}")
    suspend fun joinSpace(@Path("code") code: String): SpaceResponse

    @POST("v1/spaces/{id}/goals")
    suspend fun addGoal(@Path("id") id: String, @Body request: AddGoalRequest): SpaceResponse
}
