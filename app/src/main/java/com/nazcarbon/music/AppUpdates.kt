package com.nazcarbon.music

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nazcarbon.music.data.AppRelease
import com.nazcarbon.music.data.UpdatePromptPolicy
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal class AppUpdateState(private val activity: ComponentActivity, private val scope: CoroutineScope) {
    private val prefs = activity.getSharedPreferences("pipes_updates", Context.MODE_PRIVATE)
    private val manager = activity.getSystemService(DownloadManager::class.java)
    private val installed = version(activity.packageManager.getPackageInfo(activity.packageName, 0))
    var release by mutableStateOf(AppRelease.decode(prefs.getString("release", "").orEmpty())?.takeIf { it.versionCode > installed })
        private set
    var downloadId by mutableStateOf(if (release != null) prefs.getLong("download_id", -1) else -1)
        private set
    var checking by mutableStateOf(false)
        private set
    var automaticChecks by mutableStateOf(prefs.getBoolean("automatic_checks", true))
        private set
    var visible by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var progress by mutableStateOf<Float?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    private var checkingJob: Job? = null
    private var verifying = false
    private var manualRequested = false

    init {
        if (release == null) {
            val staleId = prefs.getLong("download_id", -1)
            if (staleId != -1L) manager.remove(staleId)
            prefs.edit().remove("release").remove("download_id").apply()
        }
    }

    fun permissionError() { message = "Couldn't open install permission settings. Try again." }

    fun changeAutomaticChecks(enabled: Boolean) {
        automaticChecks = enabled
        prefs.edit().putBoolean("automatic_checks", enabled).apply()
        if (enabled) check(false)
    }

    fun dismiss() {
        if (release != null) prefs.edit()
            .putLong("remind_after", UpdatePromptPolicy.nextReminder(System.currentTimeMillis())).apply()
        visible = false
    }

    private fun showAvailable(manual: Boolean) {
        if ((manual || automaticChecks) && UpdatePromptPolicy.shouldPrompt(installed, release?.versionCode,
                prefs.getLong("remind_after", 0), System.currentTimeMillis(), manual)) {
            visible = true
            prefs.edit().putLong("remind_after", UpdatePromptPolicy.nextReminder(System.currentTimeMillis())).apply()
        }
    }

    fun check(manual: Boolean) {
        if (!manual && !automaticChecks) return
        if (manual && downloadId != -1L) { showAvailable(true); return }
        if (checkingJob?.isActive == true) { if (manual) manualRequested = true; return }
        if (!manual && System.currentTimeMillis() - prefs.getLong("checked_at", 0) in 0 until 6 * 60 * 60 * 1000L) {
            showAvailable(false)
            return
        }
        manualRequested = manual
        checkingJob = scope.launch {
            checking = true
            if (manual) message = null
            try {
                val latest = withContext(Dispatchers.IO) {
                    val connection = URL("https://api.github.com/repos/brimzy/pipes/releases/latest").openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 15_000; connection.readTimeout = 15_000
                        connection.setRequestProperty("Accept", "application/vnd.github+json")
                        connection.setRequestProperty("User-Agent", "Pipes-Android")
                        require(connection.responseCode == 200) { "Couldn't check GitHub releases. Try again later." }
                        val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                        AppRelease.parse(json) ?: error("This release has no compatible update APK.")
                    } finally { connection.disconnect() }
                }
                prefs.edit().putLong("checked_at", System.currentTimeMillis()).apply()
                if (latest.versionCode > installed) {
                    if (downloadId == -1L) { release = latest; prefs.edit().putString("release", latest.encode()).apply() }
                    message = null
                    showAvailable(manualRequested)
                } else {
                    if (downloadId == -1L) { release = null; prefs.edit().remove("release").apply() }
                    if (manualRequested) message = "You're up to date."
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (manualRequested) message = error.message ?: "Couldn't check for updates. Try again." }
            finally { checking = false; manualRequested = false }
        }
    }

    private fun apk(release: AppRelease): File = File(activity.getExternalFilesDir(null), "updates/pipes-${release.versionCode}.apk")

    fun download() {
        val update = release ?: return
        if (downloadId != -1L) return
        try {
            apk(update).parentFile?.mkdirs()
            apk(update).parentFile?.listFiles()?.forEach { it.delete() }
            ready = false; message = null; progress = null
            downloadId = manager.enqueue(DownloadManager.Request(Uri.parse(update.url))
                .setTitle("Pipes ${update.tag}").setDescription("App update")
                .setMimeType("application/vnd.android.package-archive")
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(activity, null, "updates/pipes-${update.versionCode}.apk"))
            prefs.edit().putLong("download_id", downloadId).putString("release", update.encode()).apply()
        } catch (_: Exception) { message = "Couldn't start the download. Try again." }
    }

    suspend fun monitor(id: Long) {
        while (id == downloadId && id != -1L) {
            try {
                val status = withContext(Dispatchers.IO) {
                    manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                        if (!cursor.moveToFirst()) return@withContext null
                        Triple(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                            cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                            cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
                    }
                }
                if (status == null || status.first == DownloadManager.STATUS_FAILED) error("Download failed. Tap Download to retry.")
                progress = status.third.takeIf { it > 0 }?.let { (status.second.toFloat() / it).coerceIn(0f, 1f) }
                if (status.first == DownloadManager.STATUS_SUCCESSFUL) {
                    val update = release ?: return
                    withContext(Dispatchers.IO) { verify(update) }
                    ready = true
                    return
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                manager.remove(id); downloadId = -1; ready = false
                prefs.edit().remove("download_id").apply()
                message = error.message ?: "Couldn't verify the update. Download it again."
                return
            }
            delay(1000)
        }
    }

    private fun verify(update: AppRelease) {
        val file = apk(update)
        update.verifyDownload(file)
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = activity.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("The downloaded APK is invalid.")
        require(archive.packageName == activity.packageName && version(archive) == update.versionCode) { "The APK doesn't match this update." }
        val current = activity.packageManager.getPackageInfo(activity.packageName, flags)
        require(signatures(current) == signatures(archive) && signatures(current).isNotEmpty()) {
            "This update uses a different signing key. Install the official release version to receive updates."
        }
    }

    fun install(requestPermission: () -> Unit) {
        if (!ready || verifying) return
        if (!activity.packageManager.canRequestPackageInstalls()) { requestPermission(); return }
        val update = release ?: return
        verifying = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { verify(update) }
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apk(update))
                activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message ?: "Couldn't open the Android installer." }
            finally { verifying = false }
        }
    }

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): Set<String> =
        (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
            .orEmpty().map { it.toCharsString() }.toSet()

    @Suppress("DEPRECATION")
    private fun version(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
}

@Composable internal fun rememberAppUpdates(activity: ComponentActivity): AppUpdateState {
    val scope = rememberCoroutineScope()
    val state = remember { AppUpdateState(activity, scope) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) state.check(false) }
        activity.lifecycle.addObserver(observer)
        state.check(false)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.downloadId) { state.monitor(state.downloadId) }
    return state
}

@Composable internal fun AppUpdateDialog(activity: ComponentActivity, state: AppUpdateState) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (activity.packageManager.canRequestPackageInstalls()) state.install {}
    }
    fun install() = state.install {
        runCatching { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))) }
            .onFailure { state.permissionError() }
    }
    if (state.visible && state.release != null) AlertDialog(
        onDismissRequest = state::dismiss,
        title = { Text(if (state.release != null) "Pipes update · ${state.release?.tag}" else "App updates") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(when {
                    state.checking -> "Checking GitHub releases…"
                    state.message != null -> state.message!!
                    state.ready -> "Update ready. Android will ask you to confirm installation. Your library and settings stay saved."
                    state.downloadId != -1L -> "Downloading update" + (state.progress?.let { " · ${(it * 100).toInt()}%" } ?: "…")
                    else -> "Would you like to update Pipes? Choose Later to be reminded in five days."
                })
                if (state.downloadId != -1L && !state.ready) {
                    val progress = state.progress
                    if (progress != null) LinearProgressIndicator(progress = { progress }) else LinearProgressIndicator()
                }
            }
        },
        confirmButton = {
            if (!state.checking && state.release != null) TextButton(onClick = { if (state.ready) install() else state.download() },
                enabled = state.ready || state.downloadId == -1L) { Text(if (state.ready) "Install update" else "Download") }
            else if (!state.checking && state.message != "You're up to date.") TextButton(onClick = { state.check(true) }) { Text("Retry") }
        },
        dismissButton = { TextButton(onClick = state::dismiss) { Text("Later") } },
    )
}
