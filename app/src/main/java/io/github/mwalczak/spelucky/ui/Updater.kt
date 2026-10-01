package io.github.mwalczak.spelucky.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import io.github.mwalczak.spelucky.game.UpdateState
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Keeps the game up to date from GitHub Releases.
 *
 * On start (and when the app comes back) it asks GitHub for the latest release. If its
 * build number is higher than ours, the title screen offers the update. Tapping it
 * downloads the APK straight into Android's installer. Android then asks to confirm
 * (on Android 12+ that step is skipped when the system allows it).
 */
class Updater(
    private val activity: Activity,
    private val state: UpdateState,
    currentBuild: Int,
) {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "updater").apply { isDaemon = true } }
    private var lastCheck = 0L
    private var apkUrl: String? = null
    private var apkSize = -1L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onInstallStatus(intent)
    }

    init {
        state.currentBuild = currentBuild
        val filter = IntentFilter(ACTION_INSTALL_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(receiver, filter)
        }
    }

    fun release() {
        try {
            activity.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Already gone.
        }
    }

    /** Asks GitHub for the newest build, at most every few minutes. */
    fun check() {
        if (state.currentBuild == 0 || state.busy) return
        val now = SystemClock.elapsedRealtime()
        if (lastCheck != 0L && now - lastCheck < CHECK_EVERY_MS) return
        lastCheck = now
        worker.execute {
            try {
                val release = JSONObject(get(LATEST_RELEASE_API))
                val assets = release.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name") == APK_NAME) {
                        apkUrl = a.getString("browser_download_url")
                        apkSize = a.optLong("size", -1L)
                        state.latestBuild = UpdateState.buildFromTag(release.getString("tag_name"))
                    }
                }
            } catch (e: Exception) {
                // No internet or GitHub unhappy: just try again later.
                lastCheck = 0L
            }
        }
    }

    /** Called when the update banner is tapped. */
    fun install() {
        if (!state.available || state.busy) return
        val url = apkUrl ?: return
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            // One-time permission: "Allow from this source" in the system settings.
            state.message = "Allow Spelucky to install updates, then come back and tap again"
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return
        }
        state.message = null
        state.progress = 0f
        worker.execute { downloadAndInstall(url) }
    }

    private fun downloadAndInstall(url: String) {
        val installer = activity.packageManager.packageInstaller
        var sessionId = -1
        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(activity.packageName)
            if (apkSize > 0) params.setSize(apkSize)
            if (Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                val conn = open(url)
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: apkSize
                conn.inputStream.use { input ->
                    session.openWrite(APK_NAME, 0, total).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) state.progress = (done.toFloat() / total).coerceIn(0f, 1f)
                        }
                        session.fsync(out)
                    }
                }
                conn.disconnect()
                val intent = Intent(ACTION_INSTALL_STATUS).setPackage(activity.packageName)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(activity, sessionId, intent, flags)
                state.progress = 1f
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
            state.progress = -1f
            state.message = "Update failed (${e.message ?: "no internet?"}). Tap to try again"
        }
    }

    private fun onInstallStatus(intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) activity.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // Android restarts us as the new version.
                state.message = "Updated! Restarting..."
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                state.progress = -1f
                state.message = null // "Cancel" was pressed; offer the update again.
            }
            else -> {
                state.progress = -1f
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"
                state.message = "Update failed ($why). Tap to try again"
            }
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 20000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
        conn.setRequestProperty("User-Agent", "spelucky-updater")
        val code = conn.responseCode
        if (code !in 200..299) throw java.io.IOException("HTTP $code")
        return conn
    }

    private fun get(url: String): String {
        val conn = open(url)
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/mwalczak/spelucky/releases/latest"
        private const val APK_NAME = "spelucky.apk"
        private const val ACTION_INSTALL_STATUS = "io.github.mwalczak.spelucky.INSTALL_STATUS"
        private const val CHECK_EVERY_MS = 10 * 60 * 1000L
    }
}
