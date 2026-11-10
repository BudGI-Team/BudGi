package com.app.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.app.BuildConfig
import com.app.data.remote.api.AppUpdateApiService
import com.app.data.remote.dto.AppUpdateDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: AppUpdateDto) : UpdateState
    data class Downloading(val progressPercent: Int) : UpdateState
    data class ReadyToInstall(val apkFile: File) : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class Error(val message: String) : UpdateState
}

@Singleton
class AppUpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val apiService: AppUpdateApiService,
    private val okHttpClient: OkHttpClient
) {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    suspend fun checkForUpdates(isUserInitiated: Boolean = false): AppUpdateDto? = withContext(Dispatchers.IO) {
        try {
            _updateState.value = UpdateState.Checking

            // Step 1: Query backend API
            val backendResult = try {
                val response = apiService.checkUpdate(BuildConfig.VERSION_CODE)
                if (response.isSuccessful && response.body()?.data != null) {
                    val data = response.body()!!.data!!
                    if (data.updateAvailable) data else null
                } else null
            } catch (_: Exception) {
                null
            }

            if (backendResult != null) {
                _updateState.value = UpdateState.Available(backendResult)
                return@withContext backendResult
            }

            // Step 2: High-availability secondary source: Direct GitHub Releases API
            val githubResult = checkGitHubRelease()
            if (githubResult != null) {
                _updateState.value = UpdateState.Available(githubResult)
                return@withContext githubResult
            }

            // Step 3: Neither source has a newer version — device is up to date
            if (isUserInitiated) {
                _updateState.value = UpdateState.UpToDate(BuildConfig.VERSION_NAME)
            } else {
                _updateState.value = UpdateState.Idle
            }
            return@withContext null
        } catch (e: Exception) {
            val githubResult = checkGitHubRelease()
            if (githubResult != null) {
                _updateState.value = UpdateState.Available(githubResult)
                return@withContext githubResult
            }

            if (isUserInitiated) {
                _updateState.value = UpdateState.Error(e.message ?: "Failed to check for updates")
            } else {
                _updateState.value = UpdateState.Idle
            }
            return@withContext null
        }
    }

    private fun checkGitHubRelease(): AppUpdateDto? {
        return try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/BudGI-Team/BudGi/releases/latest")
                .header("User-Agent", "BudGi-App-Updater")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) return null
            val bodyString = response.body!!.string()
            val json = JSONObject(bodyString)
            val rawTag = json.optString("tag_name", "").trim()
            val tagName = rawTag.removePrefix("v").trim()
            if (tagName.isBlank()) return null

            if (isVersionNewer(tagName, BuildConfig.VERSION_NAME)) {
                val assets = json.optJSONArray("assets")
                var apkUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.equals("BudGi.apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            break
                        } else if (name.endsWith(".apk", ignoreCase = true) && apkUrl.isBlank()) {
                            apkUrl = asset.optString("browser_download_url", "")
                        }
                    }
                }
                if (apkUrl.isBlank()) {
                    apkUrl = "https://github.com/BudGI-Team/BudGi/releases/download/$rawTag/BudGi.apk"
                }

                AppUpdateDto(
                    updateAvailable = true,
                    isForced = false,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    latestVersionCode = BuildConfig.VERSION_CODE + 1,
                    latestVersionName = tagName,
                    apkUrl = apkUrl,
                    releasesUrl = "https://github.com/BudGI-Team/BudGi/releases",
                    releaseNotes = json.optString("body", "• Performance optimizations and latest release enhancements."),
                    apkChecksumSha256 = null,
                    publishedAt = if (json.has("published_at")) json.optString("published_at", "") else null
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isVersionNewer(remoteVersion: String, localVersion: String): Boolean {
        val cleanRemote = remoteVersion.trim().removePrefix("v").substringBefore("-")
        val cleanLocal = localVersion.trim().removePrefix("v").substringBefore("-")
        val remoteParts = cleanRemote.split('.').mapNotNull { it.toIntOrNull() }
        val localParts = cleanLocal.split('.').mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    suspend fun downloadAndInstall(apkUrl: String, expectedChecksum: String? = null) = withContext(Dispatchers.IO) {
        try {
            _updateState.value = UpdateState.Downloading(0)
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val apkFile = File(downloadDir, "BudGi-update.apk")
            if (apkFile.exists()) apkFile.delete()

            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "BudGi-App-Updater")
                .build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful || response.body == null) {
                _updateState.value = UpdateState.Error("Download failed with HTTP ${response.code}")
                return@withContext
            }

            val body = response.body!!
            val totalBytes = body.contentLength()
            var downloadedBytes: Long = 0

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    var lastPercent = 0
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _updateState.value = UpdateState.Downloading(percent)
                            }
                        }
                    }
                    output.flush()
                }
            }

            // Cryptographic Checksum Validation
            if (!expectedChecksum.isNullOrBlank()) {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val computed = apkFile.inputStream().use { stream ->
                    val buf = ByteArray(8192)
                    var bytesRead: Int
                    while (stream.read(buf).also { bytesRead = it } != -1) {
                        digest.update(buf, 0, bytesRead)
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
                if (!computed.equals(expectedChecksum.trim(), ignoreCase = true)) {
                    apkFile.delete()
                    _updateState.value = UpdateState.Error("Update integrity check failed. Please retry.")
                    return@withContext
                }
            }

            _updateState.value = UpdateState.ReadyToInstall(apkFile)
            withContext(Dispatchers.Main) {
                installApk(apkFile)
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Download error: ${e.message}")
        }
    }

    private var pendingApkFile: File? = null

    fun checkPendingInstall(): Boolean {
        val file = pendingApkFile ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
            pendingApkFile = null
            installApk(file)
            return true
        }
        return false
    }

    fun installApk(apkFile: File) {
        try {
            // Check Android 8.0+ unknown sources permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                pendingApkFile = apkFile
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Installation error: ${e.message}")
        }
    }

    fun dismiss() {
        _updateState.value = UpdateState.Idle
    }
}
