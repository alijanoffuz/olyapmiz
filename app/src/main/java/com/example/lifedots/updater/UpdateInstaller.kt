package com.example.lifedots.updater

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads a release APK and hands it to the system installer.
 *
 * Download lands in the app's internal cache under updates/, which the
 * FileProvider declared in the manifest exposes as content://.
 */
class UpdateInstaller(private val context: Context) {

    private val tag = "LifeDots/Updater"

    suspend fun download(
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        var partial: File? = null
        var target: File? = null
        try {
            val targetDir = File(context.cacheDir, "updates")
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                error("Could not create update cache directory")
            }
            // Clean older files so we don't accumulate APKs on disk
            targetDir.listFiles()?.forEach { if (it.isFile) it.delete() }
            val targetFile = File(targetDir, "olyapmiz-${info.versionName}.apk")
            val partialFile = File(targetDir, "${targetFile.name}.partial")
            target = targetFile
            partial = partialFile

            conn = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 10_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "olyapmiz-android")
            }
            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("APK download failed with HTTP $responseCode")
            }

            val total = if (info.downloadSize > 0) info.downloadSize else conn.contentLengthLong
            var downloaded = 0L
            conn.inputStream.use { input ->
                partialFile.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                }
            }

            if (downloaded <= 0L) {
                throw IllegalStateException("Downloaded APK is empty")
            }
            if (total > 0L && downloaded != total) {
                throw IllegalStateException("Incomplete APK download: $downloaded of $total bytes")
            }
            if (!partialFile.renameTo(targetFile)) {
                partialFile.copyTo(targetFile, overwrite = true)
                partialFile.delete()
            }

            Log.i(tag, "Downloaded ${targetFile.length()} bytes to $targetFile")
            targetFile
        } catch (e: Exception) {
            Log.w(tag, "Download failed", e)
            partial?.delete()
            target?.delete()
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Checks whether the downloaded APK is signed with the same certificate as
     * the currently installed app. If not, the system installer will refuse the
     * install with "App not installed: signatures do not match" and there's no
     * way around it short of uninstalling first. Detect this up front so we can
     * show a clear message instead of the cryptic system error.
     */
    fun signatureMatchesInstalled(apk: File): SignatureCheck {
        return try {
            val installedHash = signaturesHash(currentPackageSignatures())
            val apkHash = signaturesHash(apkSignatures(apk))
            when {
                installedHash == null || apkHash == null -> SignatureCheck.Unknown
                installedHash == apkHash -> SignatureCheck.Match
                else -> SignatureCheck.Mismatch
            }
        } catch (e: Exception) {
            Log.w(tag, "Signature check failed", e)
            SignatureCheck.Unknown
        }
    }

    /**
     * Hands the APK to the system installer. The user sees Android's standard
     * "Install update?" dialog; existing settings are preserved (same package,
     * same signing key). Returns:
     *  - Started — installer launched successfully
     *  - NeedsUnknownSources — "Install unknown apps" not granted; user redirected
     *  - SignatureMismatch — refuses to launch the installer because Android
     *    would reject the install anyway; caller should show the uninstall flow
     *  - Failed — package installer could not be opened
     */
    @Suppress("DEPRECATION")
    fun launchInstaller(activity: Activity, apk: File): LaunchResult {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            try {
                activity.startActivity(intent)
            } catch (e: Exception) {
                Log.w(tag, "Could not open unknown-sources settings", e)
            }
            return LaunchResult.NeedsUnknownSources
        }

        if (signatureMatchesInstalled(apk) == SignatureCheck.Mismatch) {
            Log.w(tag, "Signature mismatch — would fail; surface a clear message instead")
            return LaunchResult.SignatureMismatch
        }

        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            activity.startActivity(intent)
            LaunchResult.Started
        } catch (e: Exception) {
            Log.w(tag, "Could not launch package installer", e)
            LaunchResult.Failed
        }
    }

    /** Launches the system uninstaller for our own package, for the signature-mismatch flow. */
    fun launchSelfUninstall(activity: Activity) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.w(tag, "Could not launch uninstall", e)
        }
    }

    /** Wipes any cached APKs left behind from previous downloads. */
    fun cleanCache() {
        try {
            val cacheDir = File(context.cacheDir, "updates")
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(tag, "Failed to clean update cache", e)
        }
    }

    private fun currentPackageSignatures(): Array<Signature>? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_SIGNING_CERTIFICATES
            )
            info.signingInfo?.let {
                if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_SIGNATURES
            ).signatures
        }
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun apkSignatures(apk: File): Array<Signature>? = try {
        // For an APK file we can't use GET_SIGNING_CERTIFICATES — only the
        // GET_SIGNATURES path works against a file on disk via getPackageArchiveInfo.
        // It's deprecated for installed packages but supported for file probes.
        context.packageManager.getPackageArchiveInfo(
            apk.absolutePath, PackageManager.GET_SIGNATURES
        )?.signatures
    } catch (e: Exception) {
        null
    }

    private fun signaturesHash(signatures: Array<Signature>?): String? {
        if (signatures == null || signatures.isEmpty()) return null
        val md = MessageDigest.getInstance("SHA-256")
        // Sort so signer order doesn't matter
        signatures.map { md.digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }
            .sorted()
            .joinToString(",")
            .also { return it }
    }

    enum class SignatureCheck { Match, Mismatch, Unknown }
    enum class LaunchResult { Started, NeedsUnknownSources, SignatureMismatch, Failed }
}
