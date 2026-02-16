package com.realdebrid.downloader.ui.debug

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.realdebrid.downloader.data.local.DownloadStatus
import com.realdebrid.downloader.ui.theme.RealDebridTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class DebugDownloadActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            RealDebridTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DebugDownloadScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugDownloadScreen(
    viewModel: DebugDownloadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloads by viewModel.downloads.collectAsState(initial = emptyList())
    var urlInput by remember { mutableStateOf(SAMPLE_URL) }
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Download Engine") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Test URL", style = MaterialTheme.typography.titleSmall)
                    
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Download URL") },
                        singleLine = true,
                        trailingIcon = {
                            TextButton(onClick = {
                                clipboardManager.getText()?.let { urlInput = it.text }
                            }) {
                                Text("Paste")
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startDownload(urlInput) },
                            modifier = Modifier.weight(1f),
                            enabled = urlInput.isNotBlank() && !uiState.isDownloading
                        ) {
                            Text("Start Download")
                        }
                        
                        OutlinedButton(
                            onClick = { urlInput = SAMPLE_URL },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sample URL")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { urlInput = SAMPLE_URL_LARGE },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Large File")
                        }
                        
                        FilledTonalButton(
                            onClick = { urlInput = SAMPLE_URL_VIDEO },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Video File")
                        }
                    }
                }
            }

            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Error: $error",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            uiState.currentDownload?.let { download ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Active Download",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            download.filename,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        LinearProgressIndicator(
                            progress = { download.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${download.progress}%")
                            Text(formatSpeed(download.speed))
                            Text("${formatSize(download.downloadedBytes)} / ${formatSize(download.filesize)}")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (download.status) {
                                DownloadStatus.DOWNLOADING -> {
                                    Button(
                                        onClick = { viewModel.pauseDownload(download.id) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Pause")
                                    }
                                }
                                DownloadStatus.PAUSED -> {
                                    Button(
                                        onClick = { viewModel.resumeDownload(download.id) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Resume")
                                    }
                                }
                                else -> {}
                            }
                            OutlinedButton(
                                onClick = { viewModel.cancelDownload(download.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Download History", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.clearAll() }) {
                    Text("Clear All")
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(downloads, key = { it.id }) { download ->
                    Card {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    download.filename,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                StatusChip(download.status)
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            if (download.status == DownloadStatus.DOWNLOADING || 
                                download.status == DownloadStatus.PAUSED) {
                                LinearProgressIndicator(
                                    progress = { download.progress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    formatSize(download.filesize),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    formatTimestamp(download.createdAt),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            download.errorMessage?.let { error ->
                                Text(
                                    "Error: $error",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            download.localPath?.let { path ->
                                Text(
                                    "Path: $path",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Stats", style = MaterialTheme.typography.titleSmall)
                    Text("Total: ${downloads.size}")
                    Text("Completed: ${downloads.count { it.status == DownloadStatus.COMPLETED }}")
                    Text("Failed: ${downloads.count { it.status == DownloadStatus.FAILED }}")
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: DownloadStatus) {
    val (color, text) = when (status) {
        DownloadStatus.PENDING -> MaterialTheme.colorScheme.outline to "Pending"
        DownloadStatus.QUEUED -> MaterialTheme.colorScheme.tertiary to "Queued"
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary to "Downloading"
        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.secondary to "Paused"
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary to "Completed"
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error to "Failed"
        DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.outline to "Cancelled"
    }
    
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    return when {
        bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "—"
    return when {
        bytesPerSecond >= 1_048_576 -> "%.1f MB/s".format(bytesPerSecond / 1_048_576.0)
        bytesPerSecond >= 1024 -> "%.1f KB/s".format(bytesPerSecond / 1024.0)
        else -> "$bytesPerSecond B/s"
    }
}

private fun formatTimestamp(millis: Long): String {
    return SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(millis))
}

private const val SAMPLE_URL = "https://speed.hetzner.de/100MB.bin"
private const val SAMPLE_URL_LARGE = "https://speed.hetzner.de/1GB.bin"
private const val SAMPLE_URL_VIDEO = "https://sample-videos.com/video321/mp4/720/big_buck_bunny_720p_1mb.mp4"
