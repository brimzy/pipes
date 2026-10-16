package com.nazcarbon.music

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun AppSettingsScreen(
    activity: ComponentActivity,
    updates: AppUpdateState,
    onBack: () -> Unit,
    regionPicker: @Composable () -> Unit,
) {
    val appVersion = remember {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        "${info.versionName} · build $code"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("App updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Check for new releases when you open Pipes. Declining an update postpones reminders for five days.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Automatic update checks", modifier = Modifier.weight(1f))
                Switch(checked = updates.automaticChecks, onCheckedChange = updates::changeAutomaticChecks)
            }
            val update = updates.release
            Text(when {
                updates.checking -> "Checking for updates…"
                updates.message != null -> updates.message!!
                updates.ready -> "${update?.tag} is ready to install."
                updates.downloadId != -1L -> "Downloading ${update?.tag}" + (updates.progress?.let { " · ${(it * 100).toInt()}%" } ?: "…")
                update != null -> "${update.tag} is available."
                else -> "Installed version: $appVersion"
            }, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { updates.check(true) }, enabled = !updates.checking) {
                Text(when {
                    updates.ready -> "Install update"
                    updates.downloadId != -1L -> "View download"
                    else -> "Check for updates"
                })
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Recommendations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Choose a region for new recommendations, or follow the current song automatically.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        regionPicker()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("About Pipes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Version $appVersion", style = MaterialTheme.typography.bodyMedium)
            Text("Your music, in motion.", style = MaterialTheme.typography.bodyLarge)
            Text("Browse record sleeves on Home and in your library. Swipe the player artwork to change songs. Public Instagram video downloads, song radio, queues, playlists, and local audio are all here.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Privacy", style = MaterialTheme.typography.titleSmall)
            Text("Your library stays on this device. Online music, artwork, downloads, and update checks contact their providers, which receive your IP address and requests. Pipes has no analytics service.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
