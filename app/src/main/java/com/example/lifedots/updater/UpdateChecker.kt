package com.example.lifedots.updater

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.lifedots.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hits the GitHub Releases API to find a release newer than the currently
 * installed version. No third-party network library — just HttpURLConnection,
 * keeps the APK tiny.
 *
 * The API endpoint /releases/latest only returns the most recent NON-prerelease
 * release; that's exactly what we want.
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val downloadSize: Long,
    val releaseNotes: String,
    val htmlUrl: String
)

class UpdateChecker(private val context: Context) {

    private val tag = "LifeDots/Updater"

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest")
            Log.i(tag, "Fetching $url")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "olyapmiz-android/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(tag, "GitHub returned HTTP $code")
                return@withContext Result.NetworkError("HTTP $code from GitHub")
            }
            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)

            val json = JSONObject(body)
            val tagName = json.getString("tag_name")                 // e.g. "v1.2.3"
            val htmlUrl = json.getString("html_url")
            val notes = json.optString("body").ifBlank { "No release notes." }
            val versionName = tagName.removePrefix("v")
            val newVersionCode = tagToVersionCode(versionName)
                ?: return@withContext Result.NetworkError("Could not parse tag $tagName")

            val installedVersionCode = currentVersionCode()
            Log.i(tag, "Installed=$installedVersionCode, latest=$newVersionCode ($tagName)")
            if (newVersionCode <= installedVersionCode) {
                return@withContext Result.UpToDate(versionName)
            }

            // Prefer the stable release asset name; fall back to any APK if an
            // older release was uploaded before the stable name existed.
            val assets = json.getJSONArray("assets")
            val apkAssets = mutableListOf<JSONObject>()
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkAssets += asset
                }
            }
            val asset = apkAssets.firstOrNull {
                it.getString("name").equals("olyapmiz.apk", ignoreCase = true)
            } ?: apkAssets.firstOrNull {
                it.getString("name").equals("olyapmiz-$versionName.apk", ignoreCase = true)
            } ?: apkAssets.firstOrNull()
            if (asset != null) {
                return@withContext Result.UpdateAvailable(
                    UpdateInfo(
                        versionName = versionName,
                        versionCode = newVersionCode,
                        downloadUrl = asset.getString("browser_download_url"),
                        downloadSize = asset.optLong("size", 0L),
                        releaseNotes = notes,
                        htmlUrl = htmlUrl
                    )
                )
            }
            Result.NetworkError("Release $tagName has no APK asset")
        } catch (e: Exception) {
            Log.w(tag, "Update check failed", e)
            Result.NetworkError(e.message ?: e.javaClass.simpleName)
        } finally {
            conn?.disconnect()
        }
    }

    private fun currentVersionCode(): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    } catch (e: PackageManager.NameNotFoundException) {
        0
    }

    private fun tagToVersionCode(versionName: String): Int? {
        val parts = versionName.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return null
        val (major, minor, patch) = parts
        return major * 10_000 + minor * 100 + patch
    }

    sealed interface Result {
        data class UpdateAvailable(val info: UpdateInfo) : Result
        data class UpToDate(val version: String) : Result
        data class NetworkError(val message: String) : Result
    }
}
