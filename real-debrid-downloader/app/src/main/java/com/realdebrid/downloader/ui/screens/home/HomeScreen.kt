package com.realdebrid.downloader.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.realdebrid.downloader.data.model.Download
import com.realdebrid.downloader.data.model.TorrentFile
import com.realdebrid.downloader.data.model.TorrentInfo
import com.realdebrid.downloader.data.model.TorrentStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sharedLink: String? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var linkInput by remember { mutableStateOf(sharedLink ?: "") }
    var showAddDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(sharedLink) {
        sharedLink?.let {
            linkInput = it
            showAddDialog = true
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.downloadStarted) {
        uiState.downloadStarted?.let {
            snackbarHostState.showSnackbar("Started: $it")
            viewModel.clearDownloadStarted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Real-Debrid") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add link")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (uiState.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.user?.let { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Welcome, ${user.username}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Premium: ${if (user.premium > 0) "Active" else "Inactive"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (user.premium > 0) {
                            Text(
                                text = "Expires: ${user.expiration}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            CombinedRdTab(
                torrents = uiState.torrents,
                recentDownloads = uiState.recentDownloads,
                isLoading = uiState.isLoading,
                onDownload = { viewModel.openTorrentFilePicker(it) },
                onDelete = { viewModel.deleteTorrent(it) }
            )
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Link") },
            text = {
                Column {
                    OutlinedTextField(
                        value = linkInput,
                        onValueChange = { linkInput = it },
                        label = { Text("URL or Magnet") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                clipboardManager.getText()?.let {
                                    linkInput = it.text
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (linkInput.isNotBlank()) {
                            viewModel.addLink(linkInput)
                            linkInput = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    uiState.pendingFilePicker?.let { pickerState ->
        FilePickerDialog(
            files = pickerState.files,
            initiallySelected = pickerState.initialSelection,
            isMultiFile = pickerState.isMultiFile,
            defaultFolderName = pickerState.torrentName,
            onConfirm = { selectedIds, folderName ->
                viewModel.confirmFileSelection(pickerState.torrentId, selectedIds, folderName)
            },
            onDismiss = {
                viewModel.dismissFilePicker(pickerState.torrentId)
            }
        )
    }
}

@Composable
private fun CombinedRdTab(
    torrents: List<TorrentInfo>,
    recentDownloads: List<Download>,
    isLoading: Boolean,
    onDownload: (TorrentInfo) -> Unit,
    onDelete: (TorrentInfo) -> Unit
) {
    if (torrents.isEmpty() && recentDownloads.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No torrents",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Add magnets using the + button",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (torrents.isNotEmpty()) {
                item(key = "header_torrents") { SectionHeader("Torrents") }
                items(torrents, key = { "t_${it.id}" }) { torrent ->
                    TorrentCard(
                        torrent = torrent,
                        onDownload = { onDownload(torrent) },
                        onDelete = { onDelete(torrent) },
                        isLoading = isLoading
                    )
                }
            }
            if (recentDownloads.isNotEmpty()) {
                item(key = "header_recent") { SectionHeader("Recent Downloads") }
                items(recentDownloads, key = { "d_${it.id}" }) { download ->
                    RecentDownloadCard(download)
                }
            }
        }
    }
}

@Composable
private fun RecentDownloadCard(download: Download) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = download.filename,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatBytes(download.filesize)} • ${download.host} • ${download.generated.take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
fun FilePickerDialog(
    files: List<TorrentFile>,
    onConfirm: (List<Int>, String?) -> Unit,
    onDismiss: () -> Unit,
    initiallySelected: List<Int> = emptyList(),
    isMultiFile: Boolean = false,
    defaultFolderName: String = ""
) {
    val checkedIds = remember { mutableStateListOf<Int>().also { it.addAll(initiallySelected) } }
    val allSelected = checkedIds.size == files.size
    var folderName by remember { mutableStateOf(defaultFolderName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Files") },
        text = {
            Column {
                if (isMultiFile) {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        label = { Text("Folder name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            if (checked) {
                                checkedIds.clear()
                                checkedIds.addAll(files.map { it.id })
                            } else {
                                checkedIds.clear()
                            }
                        }
                    )
                    Text("Select All", style = MaterialTheme.typography.labelLarge)
                }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(files) { file ->
                        val isChecked = file.id in checkedIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) checkedIds.add(file.id) else checkedIds.remove(file.id)
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.path.substringAfterLast("/"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatFileSize(file.bytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(checkedIds.toList(), folderName.takeIf { isMultiFile }) },
                enabled = checkedIds.isNotEmpty()
            ) {
                val totalBytes = files.filter { it.id in checkedIds }.sumOf { it.bytes }
                Text("Download (${checkedIds.size} files / ${formatFileSize(totalBytes)})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TorrentCard(
    torrent: TorrentInfo,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    isLoading: Boolean
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val status = getTorrentStatus(torrent.status)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = torrent.filename,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (status == TorrentStatus.DOWNLOADING || status == TorrentStatus.COMPRESSING) {
                LinearProgressIndicator(
                    progress = { torrent.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = buildInfoText(torrent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = getStatusIcon(status),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = getStatusColor(status)
                        )
                        Text(
                            text = getStatusText(status, torrent),
                            style = MaterialTheme.typography.labelSmall,
                            color = getStatusColor(status)
                        )
                    }
                }

                Row {
                    if (status == TorrentStatus.DOWNLOADED) {
                        if (torrent.links.size > 1) {
                            IconButton(onClick = onDownload, enabled = !isLoading) {
                                Icon(Icons.Default.Folder, contentDescription = "Open Folder")
                            }
                        } else {
                            IconButton(onClick = onDownload, enabled = !isLoading) {
                                Icon(Icons.Default.Download, contentDescription = "Download")
                            }
                        }
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }

            if (status == TorrentStatus.DOWNLOADING && torrent.speed != null && torrent.speed > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatSpeed(torrent.speed)} • ${torrent.seeders ?: 0} seeders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Torrent") },
            text = { Text("Remove \"${torrent.filename}\" from Real-Debrid?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun buildInfoText(torrent: TorrentInfo): String {
    val size = formatBytes(torrent.bytes)
    val files = torrent.links.size
    return if (files > 0) "$size • $files file${if (files > 1) "s" else ""}" else size
}

private fun getTorrentStatus(status: String): TorrentStatus {
    return TorrentStatus.entries.find { it.value == status } ?: TorrentStatus.QUEUED
}

private fun getStatusText(status: TorrentStatus, torrent: TorrentInfo): String = when (status) {
    TorrentStatus.MAGNET_ERROR -> "Magnet error"
    TorrentStatus.MAGNET_CONVERSION -> "Converting magnet..."
    TorrentStatus.WAITING_FILES_SELECTION -> "Select files"
    TorrentStatus.QUEUED -> "Queued"
    TorrentStatus.DOWNLOADING -> "${torrent.progress}%"
    TorrentStatus.DOWNLOADED -> "Ready to download"
    TorrentStatus.ERROR -> "Error"
    TorrentStatus.VIRUS -> "Virus detected"
    TorrentStatus.COMPRESSING -> "Compressing ${torrent.progress}%"
    TorrentStatus.UPLOADING -> "Uploading"
    TorrentStatus.DEAD -> "Dead (no seeders)"
}

@Composable
private fun getStatusIcon(status: TorrentStatus) = when (status) {
    TorrentStatus.DOWNLOADED -> Icons.Default.CheckCircle
    TorrentStatus.DOWNLOADING -> Icons.Default.CloudDownload
    TorrentStatus.QUEUED -> Icons.Default.Schedule
    TorrentStatus.ERROR, TorrentStatus.MAGNET_ERROR, TorrentStatus.VIRUS -> Icons.Default.Error
    TorrentStatus.DEAD -> Icons.Default.Warning
    TorrentStatus.WAITING_FILES_SELECTION -> Icons.Default.Folder
    else -> Icons.Default.Sync
}

@Composable
private fun getStatusColor(status: TorrentStatus) = when (status) {
    TorrentStatus.DOWNLOADED -> MaterialTheme.colorScheme.primary
    TorrentStatus.DOWNLOADING, TorrentStatus.COMPRESSING, TorrentStatus.UPLOADING -> MaterialTheme.colorScheme.tertiary
    TorrentStatus.ERROR, TorrentStatus.MAGNET_ERROR, TorrentStatus.VIRUS, TorrentStatus.DEAD -> MaterialTheme.colorScheme.error
    TorrentStatus.WAITING_FILES_SELECTION -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1_048_576 -> "%.1f MB/s".format(bytesPerSecond / 1_048_576.0)
    bytesPerSecond >= 1024 -> "%.1f KB/s".format(bytesPerSecond / 1024.0)
    else -> "$bytesPerSecond B/s"
}
