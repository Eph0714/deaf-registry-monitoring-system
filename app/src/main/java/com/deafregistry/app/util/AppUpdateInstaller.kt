package com.deafregistry.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class UpdateInstallResult {
    /** The APK downloaded and the system package installer was launched - as close to a silent,
     * one-tap update as a non-system app is allowed to get on Android. The OS still shows its own
     * "Install this update?" confirmation before replacing the app - no app without device-owner
     * or root privileges can skip that final tap, it's an OS security boundary, not a choice made
     * here - but everything before it (opening a browser, finding the download, opening the file)
     * is gone. */
    INSTALLER_LAUNCHED,

    /** This device has never allowed installs from this app before - Android requires that be
     * granted once per source app via Settings before ACTION_VIEW on an APK will do anything
     * (silently failing otherwise). Settings was opened for the user to flip it on; call
     * downloadAndInstall again afterward to actually download and install. */
    PERMISSION_REQUESTED
}

/**
 * Downloads the update APK the admin published (Control Panel > App Update) straight into the
 * app's own cache and immediately launches the package installer on it - replacing the previous
 * "Update Now" behavior of opening the .apk URL in a browser, which left the user to manually find
 * the download, open it, and only then see the installer.
 */
object AppUpdateInstaller {

    suspend fun downloadAndInstall(context: Context, apkUrl: String, onProgress: (Int) -> Unit = {}): UpdateInstallResult =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                withContext(Dispatchers.Main) {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                return@withContext UpdateInstallResult.PERMISSION_REQUESTED
            }

            val apkFile = File(context.cacheDir, "update.apk")
            val connection = URL(apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            try {
                connection.connect()
                val totalBytes = connection.contentLength
                connection.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var downloadedBytes = 0
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) onProgress(downloadedBytes * 100 / totalBytes)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            UpdateInstallResult.INSTALLER_LAUNCHED
        }
}
