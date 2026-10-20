package com.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AppUpdateDto(
    @SerializedName("updateAvailable") val updateAvailable: Boolean = false,
    @SerializedName("isForced") val isForced: Boolean = false,
    @SerializedName("currentVersionCode") val currentVersionCode: Int = 0,
    @SerializedName("latestVersionCode") val latestVersionCode: Int = 0,
    @SerializedName("latestVersionName") val latestVersionName: String = "",
    @SerializedName("apkUrl") val apkUrl: String = "",
    @SerializedName("releasesUrl") val releasesUrl: String = "",
    @SerializedName("releaseNotes") val releaseNotes: String = "",
    @SerializedName("apkChecksumSha256") val apkChecksumSha256: String? = null,
    @SerializedName("publishedAt") val publishedAt: String? = null
)
