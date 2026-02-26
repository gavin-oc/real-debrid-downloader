package com.realdebrid.downloader.ui.screens.downloads

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.realdebrid.downloader.data.local.DownloadEntity
import com.realdebrid.downloader.data.local.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                actions = {
                    if (!uiState.isEmpty) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (uiState.failedCount > 0) {
                                    DropdownMenuItem(
                                        text = { Text("Retry all failed") },
                                        onClick = {
                                            viewModel.retryAllFailed()
                                            showMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.Refresh, null) }
                                    )
                                }
                                if (uiState.completedCount > 0) {
                                    DropdownMenuItem(
                                        text = { Text("Clear completed") },
                                        onClick = {
                                            viewModel.clearCompleted()
                                            showMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.ClearAll, null) }
                                    )
                                }
                                if (uiState.failedCount > 0) {
                                    DropdownMenuItem(
                                        text = { Text("Clear failed") },
                                        onClick = {
                                            viewModel.clearFailed()
                                            showMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No downloads yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Add links from the Home tab",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloads, key = { it.id }) { download ->
                    DownloadCard(
                        download = download,
                        onStart = { viewModel.startDownload(download) },
                        onPause = { viewModel.pauseDownload(download) },
                        onResume = { viewModel.resumeDownload(download) },
                        onCancel = { viewModel.cancelDownload(download) },
                        onDelete = { viewModel.deleteDownload(download) },
                        onRetry = { viewModel.retryDownload(download) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadCard(
    download: DownloadEntity,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = when (download.status) {
            DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
            DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.tertiary
            DownloadStatus.PAUSED -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "statusColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Filename
            Text(
                text = download.filename,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            when (download.status) {
                DownloadStatus.DOWNLOADING -> {
                    LinearProgressIndicator(
                        progress = download.progress / 100f,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                DownloadStatus.QUEUED -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                DownloadStatus.COMPLETED -> {
                    LinearProgressIndicator(
                        progress = 1f,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                else -> {
                    LinearProgressIndicator(
                        progress = download.progress / 100f,
                        modifier = Modifier.fillMaxWidth(),
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    // Progress info
                    Text(
                        text = buildProgressText(download),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Status badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = getStatusIcon(download.status),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = statusColor
                        )
                        Text(
                            text = getStatusText(download),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                }

                // Action buttons
                Row {
                    when (download.status) {
                        DownloadStatus.QUEUED -> {
                            IconButton(onClick = onStart) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                        DownloadStatus.DOWNLOADING -> {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause")
                            }
                            IconButton(onClick = onCancel) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            IconButton(onClick = onResume) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                        DownloadStatus.FAILED -> {
                            IconButton(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry")
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                        DownloadStatus.CANCELLED -> {
                            IconButton(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry")
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }

            // Error message
            if (download.status == DownloadStatus.FAILED && !download.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = download.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun buildProgressText(download: DownloadEntity): String {
    val downloaded = formatBytes(download.bytesDownloaded)
    val total = formatBytes(download.fileSize)

    return when (download.status) {
        DownloadStatus.DOWNLOADING -> "${download.progress}% - $downloaded / $total"
        DownloadStatus.COMPLETED -> total
        DownloadStatus.PAUSED -> "${download.progress}% - $downloaded / $total"
        else -> if (download.fileSize > 0) total else ""
    }
}

private fun getStatusText(download: DownloadEntity): String {
    return when (download.status) {
        DownloadStatus.QUEUED -> "Queued"
        DownloadStatus.DOWNLOADING -> "Downloading"
        DownloadStatus.PAUSED -> "Paused"
        DownloadStatus.COMPLETED -> "Completed"
        DownloadStatus.FAILED -> "Failed"
        DownloadStatus.CANCELLED -> "Cancelled"
    }
}

private fun getStatusIcon(status: DownloadStatus) = when (status) {
    DownloadStatus.QUEUED -> Icons.Default.Schedule
    DownloadStatus.DOWNLOADING -> Icons.Default.Download
    DownloadStatus.PAUSED -> Icons.Default.Pause
    DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
    DownloadStatus.FAILED -> Icons.Default.Error
    DownloadStatus.CANCELLED -> Icons.Default.Cancel
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    return when {
        bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
