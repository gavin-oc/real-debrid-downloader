# Architecture

## Layers
```
data/           - Room entities, DAOs, API clients, repositories
  local/        - DownloadEntity, DownloadDao, AppDatabase
  remote/       - RealDebridApi, AuthInterceptor
  repository/   - DownloadRepository, SettingsRepository
  model/        - API response models (Download, TorrentInfo, UnrestrictResponse, User)
download/       - DownloadEngine, StallDetector, FileDownloader (legacy, unused)
service/        - DownloadService (foreground service)
ui/             - Compose screens, ViewModels
  screens/      - home/, cache/, settings/
di/             - Hilt DI modules (AppModule)
```

## Key Classes

| Class | Role |
|-------|------|
| `DownloadEngine` | Parallel chunk downloader with bounded byte ranges, progress, pause/cancel |
| `StallDetector` | Monitors single-stream downloads for 30s stalls, triggers retry |
| `DownloadService` | Foreground service orchestrating engine lifecycle, notifications, DB updates |
| `DownloadRepository` | Bridges RD API + local DB; queues downloads from unrestrict responses |
| `SettingsRepository` | DataStore preferences (API token, download path, concurrent limit) |
| `RealDebridApi` | Retrofit interface for all RD endpoints (unrestrict, torrents, downloads) |
| `AuthInterceptor` | Injects Bearer token from SettingsRepository into API requests |

## Download Flow
```
User adds link/magnet
    ↓
RealDebridApi.unrestrictLink(url) → UnrestrictResponse (CDN download URL)
    ↓
DownloadRepository.queueDownload() → DownloadEntity saved to Room DB
    ↓
DownloadService.startDownload(id)
    ↓
DownloadEngine.download(url, outputFile)
    ├── getFileSizeViaRange() → bytes=0-0 probe (header only, no body read)
    ├── IF totalBytes > 0: downloadParallel()
    │     ├── Pre-allocate file with setLength(totalBytes)
    │     ├── Split into 4MB chunks
    │     ├── 8 concurrent async workers with semaphore
    │     ├── Each chunk: bounded Range: bytes=start-end
    │     ├── chunkFailed flag aborts siblings on first permanent failure
    │     └── Progress reporter on 200ms interval
    └── ELSE: downloadSingleStream() (fallback, open-ended range — known CDN issue)
    ↓
DownloadService.handleResult() → updates DB status, notification, MediaScanner
```

## OkHttp Client Configuration (AppModule)

Two separate clients:
- **API client**: AuthInterceptor, 30s timeouts, BODY-level logging
- **Downloader client**: No auth (URL is pre-authenticated), 5min read/write timeouts, HEADERS logging

## CDN Behavior (Real-Debrid Lity 2.0)

- Multiple nodes (nyk3, nyk6, nyk7, etc.), some partially broken
- Open-ended `Range: bytes=0-` causes connection holds without FIN
- Bounded `Range: bytes=start-end` works correctly
- Some nodes accept TCP, return HTTP 206, then send 0 bytes and close
- Solution: probe with `bytes=0-0`, then parallel bounded chunks

## SAF Path Handling

Settings stores download path as SAF content URI (`content://com.android.externalstorage.documents/tree/primary%3ADownload%2FDebrid`).
`DownloadService.resolveDirectory()` converts to filesystem path (`/storage/emulated/0/Download/Debrid`) for `RandomAccessFile` compatibility.

## Known Architectural Gaps

1. **No auto-recovery from expired CDN URLs** — `DownloadEntity` lacks `torrentId`; can't re-unrestrict
2. **No chunk-level resume** — parallel path re-downloads all chunks on restart
3. **Cancel/completion race** — `cancelDownload` and `handleResult` can both write final DB status
4. **Legacy `FileDownloader`** — dead code still wired into DI
