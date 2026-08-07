package com.lightningstudio.watchrss.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data class Downloading(val bytesRead: Long, val totalBytes: Long?) : AppUpdateState
    data class Ready(val apk: File) : AppUpdateState
    data class Failed(val message: String) : AppUpdateState
}

class AppUpdateDownloader(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

    suspend fun download(version: String, url: String) = withContext(Dispatchers.IO) {
        runCatching {
            require(Uri.parse(url).scheme.equals("https", ignoreCase = true)) {
                "安装包地址必须使用 HTTPS"
            }
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            directory.listFiles()?.forEach { it.delete() }
            val target = File(directory, "watchrss-${safeVersion(version)}.apk")
            val temporary = File(directory, "${target.name}.part")
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "下载失败（HTTP ${response.code}）" }
                val body = response.body ?: error("服务器没有返回安装包")
                val total = body.contentLength().takeIf { it > 0 }
                body.byteStream().use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            mutableState.value = AppUpdateState.Downloading(downloaded, total)
                        }
                    }
                }
                check(temporary.length() > 0) { "下载到的安装包为空" }
                val packageInfo = context.packageManager.getPackageArchiveInfo(temporary.absolutePath, 0)
                check(packageInfo?.packageName == context.packageName) {
                    "下载内容不是本应用的有效 APK 安装包"
                }
                check(temporary.renameTo(target)) { "无法保存安装包" }
            }
            mutableState.value = AppUpdateState.Ready(target)
        }.onFailure { error ->
            mutableState.value = AppUpdateState.Failed(error.message ?: "安装包下载失败")
        }
    }

    fun launchInstaller(apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
            return false
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return true
    }

    fun resetFailure() {
        if (mutableState.value is AppUpdateState.Failed) mutableState.value = AppUpdateState.Idle
    }

    private fun safeVersion(version: String): String =
        version.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
