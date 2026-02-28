package com.realdebrid.downloader.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realdebrid.downloader.data.model.Download
import com.realdebrid.downloader.data.model.TorrentFile
import com.realdebrid.downloader.data.model.TorrentInfo
import com.realdebrid.downloader.data.model.TorrentStatus
import com.realdebrid.downloader.data.model.User
import com.realdebrid.downloader.data.repository.DownloadRepository
import com.realdebrid.downloader.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortBy { DATE_ADDED, NAME, SIZE, STATUS }
enum class StatusFilter { ALL, DOWNLOADING, DOWNLOADED, ERROR }

data class TorrentFilesState(
    val torrentId: String,
    val torrentName: String = "",
    val files: List<TorrentFile>,
    val links: List<String> = emptyList(),
    val initialSelection: List<Int> = emptyList(),
    val isMultiFile: Boolean = false
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val user: User? = null,
    val recentDownloads: List<Download> = emptyList(),
    val torrents: List<TorrentInfo> = emptyList(),
    val error: String? = null,
    val addLinkSuccess: Boolean = false,
    val downloadStarted: String? = null,
    val pendingFilePicker: TorrentFilesState? = null,
    val searchQuery: String = "",
    val sortBy: SortBy = SortBy.DATE_ADDED,
    val sortAscending: Boolean = false,
    val statusFilter: StatusFilter = StatusFilter.ALL,
    val filteredTorrents: List<TorrentInfo> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUser()
        loadDownloads()
        loadTorrents()
    }

    private fun loadUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getUser()
                .onSuccess { user ->
                    _uiState.update { it.copy(user = user, error = null) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun loadDownloads() {
        viewModelScope.launch {
            repository.getDownloads()
                .onSuccess { downloads ->
                    _uiState.update { it.copy(recentDownloads = downloads) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun loadTorrents() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.getTorrents()
                .onSuccess { torrents ->
                    _uiState.update { val s = it.copy(torrents = torrents, isLoading = false); s.copy(filteredTorrents = computeFilteredTorrents(s)) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun addLink(link: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, addLinkSuccess = false) }

            if (link.startsWith("magnet:")) {
                addMagnet(link)
            } else {
                addDirectLink(link)
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun addMagnet(magnet: String) {
        repository.addMagnet(magnet)
            .onSuccess { torrentId ->
                val files = pollForFiles(torrentId)
                if (files.isNullOrEmpty()) {
                    repository.selectFiles(torrentId, "all")
                    _uiState.update { it.copy(addLinkSuccess = true) }
                    loadTorrents()
                } else {
                    _uiState.update { it.copy(pendingFilePicker = TorrentFilesState(torrentId = torrentId, files = files)) }
                }
            }
            .onFailure { e ->
                _uiState.update { it.copy(error = "Failed to add magnet: ${e.message}") }
            }
    }

    private suspend fun pollForFiles(torrentId: String): List<TorrentFile>? {
        for (i in 0 until 10) {
            delay(1000)
            val info = repository.getTorrentInfo(torrentId).getOrNull() ?: continue
            if (info.status == "waiting_files_selection" || !info.files.isNullOrEmpty()) {
                return info.files
            }
        }
        return null
    }

    fun confirmFileSelection(torrentId: String, selectedIds: List<Int>, folderName: String? = null) {
        val picker = _uiState.value.pendingFilePicker ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(pendingFilePicker = null) }
            if (picker.links.isEmpty()) {
                // New-magnet flow: tell RD which files to process
                val param = if (selectedIds.isEmpty()) "all" else selectedIds.joinToString(",")
                repository.selectFiles(torrentId, param)
                _uiState.update { it.copy(addLinkSuccess = true) }
                loadTorrents()
            } else {
                // DOWNLOADED flow: map selected file IDs → their RD links → download
                _uiState.update { it.copy(isLoading = true) }
                val selectedFilesInOrder = picker.files.filter { it.selected == 1 }
                val linksToDownload = selectedFilesInOrder.mapIndexedNotNull { i, file ->
                    if (file.id in selectedIds) picker.links.getOrNull(i) else null
                }
                val subFolder = if (picker.isMultiFile) folderName?.takeIf { it.isNotBlank() } else null
                var successCount = 0
                for (link in linksToDownload) {
                    repository.unrestrictLink(link)
                        .onSuccess { response ->
                            val entity = repository.queueDownload(link, response, subFolder = subFolder)
                            DownloadService.startDownload(context, entity.id)
                            successCount++
                        }
                }
                _uiState.update {
                    if (successCount > 0)
                        it.copy(
                            downloadStarted = "${picker.files.firstOrNull()?.path?.substringBefore("/") ?: torrentId} ($successCount files)",
                            isLoading = false
                        )
                    else
                        it.copy(error = "Failed to start downloads", isLoading = false)
                }
            }
        }
    }

    fun dismissFilePicker(torrentId: String) {
        val picker = _uiState.value.pendingFilePicker ?: return
        viewModelScope.launch {
            if (picker.links.isEmpty()) {
                repository.deleteTorrent(torrentId)
            }
            _uiState.update { it.copy(pendingFilePicker = null) }
            loadTorrents()
        }
    }

    private suspend fun addDirectLink(link: String) {
        repository.unrestrictLink(link)
            .onSuccess { response ->
                val entity = repository.queueDownload(link, response)
                DownloadService.startDownload(context, entity.id)
                _uiState.update { it.copy(addLinkSuccess = true) }
                loadDownloads()
            }
            .onFailure { e ->
                _uiState.update { it.copy(error = "Failed to unrestrict: ${e.message}") }
            }
    }

    fun downloadFromCache(download: Download) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.unrestrictLink(download.link)
                .onSuccess { response ->
                    val entity = repository.queueDownload(download.link, response)
                    DownloadService.startDownload(context, entity.id)
                    _uiState.update { it.copy(addLinkSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Failed to start download: ${e.message}") }
                }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun downloadTorrentLinks(torrent: TorrentInfo) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.getTorrentInfo(torrent.id)
                .onSuccess { info ->
                    if (info.links.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                error = "No links available. Torrent may still be processing.",
                                isLoading = false
                            )
                        }
                        return@launch
                    }

                    var successCount = 0
                    for (link in info.links) {
                        repository.unrestrictLink(link)
                            .onSuccess { response ->
                                val entity = repository.queueDownload(link, response)
                                DownloadService.startDownload(context, entity.id)
                                successCount++
                            }
                    }

                    if (successCount > 0) {
                        _uiState.update {
                            it.copy(
                                downloadStarted = "${info.filename} ($successCount files)",
                                isLoading = false
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(error = "Failed to start downloads", isLoading = false)
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun openTorrentFilePicker(torrent: TorrentInfo) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.getTorrentInfo(torrent.id)
                .onSuccess { info ->
                    val files = info.files ?: emptyList()
                    val selectedIds = files.filter { it.selected == 1 }.map { it.id }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingFilePicker = TorrentFilesState(
                                torrentId = torrent.id,
                                torrentName = torrent.filename.substringBeforeLast("."),
                                files = files,
                                links = info.links,
                                initialSelection = selectedIds,
                                isMultiFile = info.links.size > 1
                            )
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun deleteTorrent(torrent: TorrentInfo) {
        viewModelScope.launch {
            repository.deleteTorrent(torrent.id)
                .onSuccess {
                    _uiState.update { state ->
                        val s = state.copy(torrents = state.torrents.filter { it.id != torrent.id })
                        s.copy(filteredTorrents = computeFilteredTorrents(s))
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            coroutineScope {
                launch { loadUser() }
                launch {
                    repository.getDownloads()
                        .onSuccess { downloads ->
                            _uiState.update { it.copy(recentDownloads = downloads) }
                        }
                }
                launch {
                    repository.getTorrents()
                        .onSuccess { torrents ->
                            _uiState.update { val s = it.copy(torrents = torrents); s.copy(filteredTorrents = computeFilteredTorrents(s)) }
                        }
                }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun computeFilteredTorrents(state: HomeUiState): List<TorrentInfo> {
        val q = state.searchQuery.trim().lowercase()
        var list = state.torrents
        if (q.isNotEmpty()) list = list.filter { it.filename.lowercase().contains(q) }
        list = when (state.statusFilter) {
            StatusFilter.DOWNLOADING -> list.filter { it.status == TorrentStatus.DOWNLOADING.value }
            StatusFilter.DOWNLOADED  -> list.filter { it.status == TorrentStatus.DOWNLOADED.value }
            StatusFilter.ERROR       -> list.filter {
                it.status in listOf(TorrentStatus.ERROR.value, TorrentStatus.MAGNET_ERROR.value,
                                    TorrentStatus.DEAD.value, TorrentStatus.VIRUS.value)
            }
            StatusFilter.ALL -> list
        }
        val comparator: Comparator<TorrentInfo> = when (state.sortBy) {
            SortBy.NAME       -> compareBy { it.filename.lowercase() }
            SortBy.SIZE       -> compareBy { it.bytes }
            SortBy.STATUS     -> compareBy { it.status }
            SortBy.DATE_ADDED -> compareBy { it.added }
        }
        return if (state.sortAscending) list.sortedWith(comparator)
               else list.sortedWith(comparator.reversed())
    }

    fun setSearchQuery(q: String) {
        _uiState.update { val s = it.copy(searchQuery = q); s.copy(filteredTorrents = computeFilteredTorrents(s)) }
    }

    fun setSortBy(sort: SortBy) {
        _uiState.update { val s = it.copy(sortBy = sort); s.copy(filteredTorrents = computeFilteredTorrents(s)) }
    }

    fun toggleSortDirection() {
        _uiState.update { val s = it.copy(sortAscending = !it.sortAscending); s.copy(filteredTorrents = computeFilteredTorrents(s)) }
    }

    fun setStatusFilter(filter: StatusFilter) {
        _uiState.update { val s = it.copy(statusFilter = filter); s.copy(filteredTorrents = computeFilteredTorrents(s)) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(addLinkSuccess = false) }
    }

    fun clearDownloadStarted() {
        _uiState.update { it.copy(downloadStarted = null) }
    }
}
