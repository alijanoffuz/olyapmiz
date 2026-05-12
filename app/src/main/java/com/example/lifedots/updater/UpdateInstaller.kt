package com.example.lifedots.updater

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a release APK and hands it to the system installer.
 *
 * Download lands in app's external-files dir under updates/, which the
 * FileProvider declared in the manifest exposes as content://. We never
 * touch shared storage — no MANAGE_EXTERNAL_STORAGE / scoped storage drama.
 */
class UpdateInstaller(private val context: Context) {

    private val tag = "LifeDots/Updater"

    suspend fun download(
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val targetDir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
            // Clean older files so we don't accumulate APKs on disk
            targetDir.listFiles()?.forEach { if (it.isFile) it.delete() }
            val target = File(targetDir, "olyapmiz-${info.versionName}.apk")

            val conn = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 10_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "olyapmiz-android")
            }
            val total = if (info.downloadSize > 0) info.downloadSize else conn.contentLengthLong
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                }
            }
            conn.disconnect()
            Log.i(tag, "Downloaded ${target.length()} bytes to $target")
            target
        } catch (e: Exception) {
            Log.w(tag, "Download failed", e)
            null
        }
    }

    /**
     * Hands the APK to the system installer. The user sees Android's standard
     * "Install update?" dialog; existing settings are preserved (same package,
     * same signing key). Returns false if "Install unknown apps" permission is
     * not granted for our app — caller should send the user to settings.
     */
    fun launchInstaller(activity: Activity, apk: File): Boolean {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            // User hasn't allowed "Install unknown apps" for us yet.
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            try {
                activity.startActivity(intent)
            } catch (e: Exception) {
                Log.w(tag, "Could not open unknown-sources settings", e)
            }
            return false
        }

        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
        return true
    }
}
