package me.weishu.kernelsu.ui.util

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.registry.ExpectedFile
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo
import okhttp3.Request

/**
 * @author weishu
 * @date 2023/6/22.
 */
suspend fun download(
    url: String,
    fileName: String,
    onDownloaded: (Uri) -> Unit = {},
    onDownloading: () -> Unit = {},
    onProgress: (Int) -> Unit = {},
    expected: ExpectedFile? = null,
    onFailed: (DownloadManager.DownloadState) -> Unit = {},
) {
    onDownloading()

    val downloadId = DownloadManager.enqueue(
        context = ksuApp,
        url = url,
        fileName = fileName,
        onCompleted = onDownloaded,
        expected = expected,
    )

    val finalState = DownloadManager.downloads
        .onEach { map -> map[downloadId]?.let { onProgress(it.progress) } }
        .first { map ->
            val status = map[downloadId]?.status
            status == DownloadManager.Status.COMPLETED ||
                status == DownloadManager.Status.FAILED
        }[downloadId]
    if (finalState?.status == DownloadManager.Status.FAILED) {
        withContext(Dispatchers.Main) { onFailed(finalState) }
    }
}

internal suspend fun isDownloadAvailable(uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        ksuApp.contentResolver.openFileDescriptor(uri, "r").use { it != null }
    }.getOrDefault(false)
}

// Cloud update manifest, served by our own card-key server (no GitHub dependency).
private val UPDATE_MANIFEST_URL: String
    get() = me.weishu.kernelsu.BuildConfig.KEY_SERVER_URL.trimEnd('/') + "/api/v1/manager/latest"

fun checkNewVersion(): LatestVersionInfo {
    if (!isNetworkAvailable(ksuApp)) return LatestVersionInfo()
    return runCatching {
        ksuApp.okhttpClient.newCall(Request.Builder().url(UPDATE_MANIFEST_URL).build())
            .execute().use { response ->
                if (!response.isSuccessful) return LatestVersionInfo()
                val json = org.json.JSONObject(response.body.string())
                LatestVersionInfo(
                    versionCode = json.optInt("versionCode", 0),
                    downloadUrl = json.optString("url"),
                    changelog = json.optString("changelog"),
                )
            }
    }.getOrDefault(LatestVersionInfo())
}
