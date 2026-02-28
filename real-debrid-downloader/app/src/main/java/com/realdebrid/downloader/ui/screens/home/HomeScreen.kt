package com.realdebrid.downloader.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
                filteredTorrents = uiState.filteredTorrents,
                searchQuery = uiState.searchQuery,
                sortBy = uiState.sortBy,
                sortAscending = uiState.sortAscending,
                statusFilter = uiState.statusFilter,
                isLoading = uiState.isLoading,
                onSearchQueryChange = viewModel::setSearchQuery,
                onSortByChange = viewModel::setSortBy,
                onToggleSortDirection = viewModel::toggleSortDirection,
                onStatusFilterChange = viewModel::setStatusFilter,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CombinedRdTab(
    torrents: List<TorrentInfo>,
    filteredTorrents: List<TorrentInfo>,
    searchQuery: String,
    sortBy: SortBy,
    sortAscending: Boolean,
    statusFilter: StatusFilter,
    isLoading: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSortByChange: (SortBy) -> Unit,
    onToggleSortDirection: () -> Unit,
    onStatusFilterChange: (StatusFilter) -> Unit,
    onDownload: (TorrentInfo) -> Unit,
    onDelete: (TorrentInfo) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    if (torrents.isEmpty()) {
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
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Search torrents") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        listOf(
                            SortBy.DATE_ADDED to "Date Added",
                            SortBy.NAME to "Name",
                            SortBy.SIZE to "Size",
                            SortBy.STATUS to "Status"
                        ).forEach { (sort, label) ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (sortBy == sort) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        else Spacer(modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(label)
                                    }
                                },
                                onClick = { onSortByChange(sort); showSortMenu = false }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (sortAscending) "↑ Ascending" else "↓ Descending") },
                            onClick = { onToggleSortDirection(); showSortMenu = false }
                        )
                    }
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = statusFilter == StatusFilter.ALL,
                        onClick = { onStatusFilterChange(StatusFilter.ALL) },
                        label = { Text("All") }
                    )
                }
                item {
                    FilterChip(
                        selected = statusFilter == StatusFilter.DOWNLOADING,
                        onClick = { onStatusFilterChange(StatusFilter.DOWNLOADING) },
                        label = { Text("Downloading") }
                    )
                }
                item {
                    FilterChip(
                        selected = statusFilter == StatusFilter.DOWNLOADED,
                        onClick = { onStatusFilterChange(StatusFilter.DOWNLOADED) },
                        label = { Text("Downloaded") }
                    )
                }
                item {
                    FilterChip(
                        selected = statusFilter == StatusFilter.ERROR,
                        onClick = { onStatusFilterChange(StatusFilter.ERROR) },
                        label = { Text("Error") }
                    )
                }
            }

            if (filteredTorrents.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No results",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Try adjusting your search or filters",
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
                    item(key = "header_torrents") { SectionHeader("Torrents") }
                    items(filteredTorrents, key = { "t_${it.id}" }) { torrent ->
                        TorrentCard(
                            torrent = torrent,
                            onDownload = { onDownload(torrent) },
                            onDelete = { onDelete(torrent) },
                            isLoading = isLoading
                        )
                    }
                }
            }
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
