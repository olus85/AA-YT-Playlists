package app.olus.ytmusic.autolauncher.ui.compose.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.olus.ytmusic.autolauncher.R
import app.olus.ytmusic.autolauncher.data.repository.BackupManager
import app.olus.ytmusic.autolauncher.data.repository.JellyfinRepository
import app.olus.ytmusic.autolauncher.ui.compose.theme.YTRed
import app.olus.ytmusic.autolauncher.util.AALogger
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full settings dialog combining Jellyfin configuration and Diagnostics.
 */
@Composable
fun SettingsDialog(
    jellyfinRepository: JellyfinRepository,
    backupManager: BackupManager,
    onJellyfinConnected: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ─── Jellyfin State ───
    var jfServerUrl by remember { mutableStateOf(jellyfinRepository.serverUrl) }
    var jfUsername by remember { mutableStateOf(jellyfinRepository.username) }
    var jfPassword by remember { mutableStateOf("") }
    var jfIsConnecting by remember { mutableStateOf(false) }
    var jfIsConnected by remember { mutableStateOf(jellyfinRepository.isConfigured) }
    var jfError by remember { mutableStateOf<String?>(null) }
    var jfTestResult by remember { mutableStateOf<Boolean?>(null) }

    // ─── Diagnostics State ───
    var debugEnabled by remember { mutableStateOf(AALogger.isEnabled) }

    // ─── Auto-Lyrics State ───
    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    var autoLyricsEnabled by remember {
        mutableStateOf(prefs.getBoolean("auto_lyrics", false))
    }

    // ─── Cache Settings State ───
    val cacheSizeOptions = listOf(100L, 250L, 500L, 1000L) // MB
    var selectedCacheSize by remember {
        mutableStateOf(prefs.getLong("audio_cache_limit_mb", 500L))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ════════════════════════════════════════════
                // Jellyfin Section
                // ════════════════════════════════════════════
                Text(
                    "Jellyfin Server",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (jfIsConnected) {
                    // Connected state
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1B5E20).copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Verbunden",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                                Text(
                                    "${jellyfinRepository.username}@${jellyfinRepository.serverUrl}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                jellyfinRepository.disconnect()
                                jfIsConnected = false
                                jfServerUrl = ""
                                jfUsername = ""
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Trennen",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // Test connection button
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                jfTestResult = jellyfinRepository.testConnection()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verbindung testen")
                    }

                    jfTestResult?.let { success ->
                        Text(
                            if (success) "✓ Verbindung erfolgreich" else "✗ Verbindung fehlgeschlagen",
                            color = if (success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    // Login form
                    OutlinedTextField(
                        value = jfServerUrl,
                        onValueChange = { jfServerUrl = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("https://jellyfin.example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YTRed,
                            focusedLabelColor = YTRed,
                            cursorColor = YTRed
                        )
                    )

                    OutlinedTextField(
                        value = jfUsername,
                        onValueChange = { jfUsername = it },
                        label = { Text("Benutzername") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YTRed,
                            focusedLabelColor = YTRed,
                            cursorColor = YTRed
                        )
                    )

                    OutlinedTextField(
                        value = jfPassword,
                        onValueChange = { jfPassword = it },
                        label = { Text("Passwort") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YTRed,
                            focusedLabelColor = YTRed,
                            cursorColor = YTRed
                        )
                    )

                    jfError?.let { error ->
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = {
                            jfIsConnecting = true
                            jfError = null
                            scope.launch {
                                val result = jellyfinRepository.authenticate(jfServerUrl, jfUsername, jfPassword)
                                jfIsConnecting = false
                                result.fold(
                                    onSuccess = {
                                        jfIsConnected = true
                                        jfPassword = ""
                                        onJellyfinConnected()
                                    },
                                    onFailure = { e ->
                                        jfError = e.message
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = jfServerUrl.isNotBlank() && jfUsername.isNotBlank() && !jfIsConnecting,
                        colors = ButtonDefaults.buttonColors(containerColor = YTRed)
                    ) {
                        if (jfIsConnecting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text("Verbinden", color = Color.White)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ════════════════════════════════════════════
                // Diagnostics Section
                // ════════════════════════════════════════════
                Text(
                    stringResource(R.string.diagnostics_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Debug toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.debug_mode),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = debugEnabled,
                        onCheckedChange = {
                            debugEnabled = it
                            AALogger.isEnabled = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = YTRed
                        )
                    )
                }

                Text(
                    stringResource(R.string.debug_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            val logs = AALogger.getLogs()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "AA Debug Logs")
                                putExtra(Intent.EXTRA_TEXT, logs)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Logs teilen").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.share_logs), color = YTRed)
                    }
                    TextButton(
                        onClick = {
                            AALogger.clearLogs()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.clear_logs), color = MaterialTheme.colorScheme.error)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ════════════════════════════════════════════
                // Auto-Lyrics Section
                // ════════════════════════════════════════════
                Text(
                    "Songtexte",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Automatisch anzeigen", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = autoLyricsEnabled,
                        onCheckedChange = {
                            autoLyricsEnabled = it
                            prefs.edit().putBoolean("auto_lyrics", it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = YTRed
                        )
                    )
                }

                Text(
                    "Öffnet die App automatisch bei neuen Songs, um Songtexte anzuzeigen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ════════════════════════════════════════════
                // Cache Settings Section
                // ════════════════════════════════════════════
                Text(
                    "Audio-Cache",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    "Jellyfin-Audiostreams werden lokal zwischengespeichert für Offline-Wiedergabe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cacheSizeOptions.forEach { sizeMb ->
                        val isSelected = selectedCacheSize == sizeMb
                        OutlinedButton(
                            onClick = {
                                selectedCacheSize = sizeMb
                                prefs.edit().putLong("audio_cache_limit_mb", sizeMb).apply()
                            },
                            modifier = Modifier.weight(1f),
                            colors = if (isSelected) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = YTRed.copy(alpha = 0.15f)
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) YTRed else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Text(
                                if (sizeMb >= 1000) "${sizeMb / 1000} GB" else "$sizeMb MB",
                                color = if (isSelected) YTRed else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ════════════════════════════════════════════
                // Backup & Restore Section
                // ════════════════════════════════════════════
                Text(
                    "Backup & Restore",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    "Alle Playlists, Einstellungen und Jellyfin-Konfiguration sichern oder wiederherstellen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                var isRestoring by remember { mutableStateOf(false) }
                var restoreMessage by remember { mutableStateOf<String?>(null) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val backup = backupManager.createBackup()
                            val json = backupManager.toJson(backup)
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault())
                            val fileName = "aa_yt_playlists_backup_${dateFormat.format(Date())}.json"
                            val file = File(context.getExternalFilesDir(null), fileName)
                            file.writeText(json)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                                putExtra(Intent.EXTRA_SUBJECT, "AA YT Playlists Backup")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Backup teilen").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Backup")
                    }

                    val restoreLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri ->
                        uri?.let {
                            try {
                                val json = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText()
                                if (json != null) {
                                    val backup = backupManager.fromJson(json)
                                    if (backup != null) {
                                        isRestoring = true
                                        scope.launch {
                                            backupManager.restoreBackup(backup)
                                            isRestoring = false
                                            restoreMessage = "Wiederherstellung erfolgreich!"
                                        }
                                    } else {
                                        restoreMessage = "Fehler: Ungültiges Backup-Format"
                                    }
                                }
                            } catch (e: Exception) {
                                restoreMessage = "Fehler: ${e.message}"
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            restoreLauncher.launch("application/json")
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isRestoring
                    ) {
                        if (isRestoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isRestoring) "..." else "Restore")
                    }
                }

                restoreMessage?.let { msg ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (msg.startsWith("Fehler")) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}
