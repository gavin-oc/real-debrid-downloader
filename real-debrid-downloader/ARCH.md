# Architecture

## Package Structure

```
com.realdebrid.downloader/
├── RealDebridApp.kt              Application class; notification channels, WorkManager/Hilt setup
├── data/
│   ├── local/                    Room database
│   │   ├── AppDatabase.kt        Singleton DB; migrations v3→4, v4→5
│   │   ├── DownloadDao.kt        CRUD + progress queries (Flow + suspend)
│   │   └── DownloadEntity.kt     Persisted download state
│   ├── model/
│   │   └── Models.kt             API response data classes (see below)
│   ├── remote/
│   │   ├── RealDebridApi.kt      Retrofit interface for all RD endpoints
│   │   └── AuthInterceptor.kt    Injects Bearer token into every API request
│   └── repository/
│       ├── DownloadRepository.kt Wraps RealDebridApi + DownloadDao; Result<T> error handling
│       └── SettingsRepository.kt DataStore preferences (token, paths, limits, theme)
├── di/
│   └── AppModule.kt              Hilt module — all singleton providers
├── download/
│   ├── DownloadEngine.kt         Core parallel chunk downloader
│   ├── DownloadState.kt          Sealed class for download UI state
│   └── StallDetector.kt          30s stall monitor; triggers retry on single-stream
├── service/
│   └── DownloadService.kt        Foreground service; orchestrates engine lifecycle + notifications
└── ui/
    ├── MainActivity.kt           Handles share intents (SEND, VIEW, magnet:)
    ├── navigation/
    │   └── AppNavigation.kt      Compose NavHost + bottom bar (Home, Downloads, Settings)
    ├── screens/
    │   ├── home/
    │   │   ├── HomeScreen.kt     Add links/torrents; user info; torrent list with search/sort/filter
    │   │   └── HomeViewModel.kt  Profile load; link/magnet add; torrent file selection; auto-start
    │   ├── downloads/
    │   │   ├── DownloadsScreen.kt Download list; progress; bulk actions
    │   │   └── DownloadsViewModel.kt Observes local DB; delegates control to DownloadService
    │   └── settings/
    │       ├── SettingsScreen.kt Directory picker; token input; toggles
    │       └── SettingsViewModel.kt Reads/writes AppSettings; clear-all
    └── theme/
        └── Theme.kt              Material 3 theming; dark mode support
```

## Data Models

### API Models (`Models.kt`)

```kotlin
User(id, username, email, points, locale, avatar, type, premium, expiration)

UnrestrictResponse(id, filename, mimeType, filesize, link, host, chunks, crc, download, streamable)

Download(id, filename, mimeType, filesize, link, host, chunks, download, generated)

TorrentInfo(
    id, filename, hash, bytes, host, split,
    progress: Float,   // API returns decimals e.g. 36.14
    status: String, added, links, ended, speed, seeders, files, originalFilename
)

TorrentFile(id, path, bytes, selected)

enum TorrentStatus {
    MAGNET_ERROR, MAGNET_CONVERSION, WAITING_FILES_SELECTION, QUEUED,
    DOWNLOADING, DOWNLOADED, ERROR, VIRUS, COMPRESSING, UPLOADING, DEAD
}
```

### Local Models (`DownloadEntity`)

```kotlin
DownloadEntity(
    id: String (UUID, PK),
    filename, originalUrl, downloadUrl, mimeType,
    fileSize, bytesDownloaded,
    progress: Int (0–100),
    status: DownloadStatus,
    localPath, errorMessage, retryCount,
    rdDownloadId,    // for URL re-unrestricting on CDN expiry (added v3→4)
    subFolder,       // torrent multi-file grouping (added v4→5)
    createdAt, updatedAt, completedAt
)

enum DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }
```

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
    │     ├── Up to 4 concurrent async workers with Semaphore
    │     ├── Each chunk: bounded Range: bytes=start-end
    │     ├── Per-chunk retry: up to 3x with exponential backoff (max 5s)
    │     ├── chunkFailed flag aborts sibling workers on first permanent failure
    │     └── Progress emitted on 250ms interval via SharedFlow
    └── ELSE: downloadSingleStream() + StallDetector (fallback)
    ↓
DownloadService.handleResult() → updates DB status, sends notification, triggers MediaScanner
```

## OkHttp Clients (AppModule)

Two separate clients, both provided via Hilt:

| | API Client | Downloader Client |
|---|---|---|
| Auth | `AuthInterceptor` (Bearer token) | None (URL is pre-authenticated) |
| Timeouts | 30s connect/read/write | 5min read/write |
| Logging | BODY level | HEADERS level |
| Redirects | Default | `followRedirects = true` |

## DownloadEngine — Core Algorithm

**Parallel chunk strategy:**
1. **Probe**: `bytes=0-0` range request reads only response headers to get `Content-Range: bytes 0-0/TOTAL`
2. **Pre-allocate**: `RandomAccessFile.setLength(totalBytes)` before any writes
3. **Chunk split**: 4MB chunks (configurable `CHUNK_SIZE`)
4. **Parallel writes**: Up to 4 concurrent coroutines, each writing to its own byte range with `Semaphore`
5. **Bounded ranges**: Always `bytes=start-end` — open-ended `bytes=0-` causes CDN to hold connections without FIN

**Error handling:**
- Chunk failure: retry up to 3x with exponential backoff (100ms base, max 5s)
- Short reads (incomplete chunk): re-adds partial bytes to progress, retries
- Sibling abort: `chunkFailed` AtomicBoolean stops all peers on first permanent failure
- Single-stream stall: `StallDetector` triggers retry after 30s of no progress

**State flow:**
```kotlin
// Progress
SharedFlow<EngineProgress>(replay=1)   // bytesDownloaded, totalBytes, speedBps

// Events
SharedFlow<EngineEvent>                // Started | Completed | Failed | Cancelled | Retrying | StallDetected

// Control
suspend fun pause()
suspend fun resume()
suspend fun cancel()
```

## DownloadService — Foreground Service

- Type: `dataSync` foreground service (required Android 12+)
- Active downloads: `ConcurrentHashMap<String, ActiveDownload>`

**Concurrent download enforcement:**
- `maxConcurrentDownloads` setting respected for auto-start
- Manual `START` / `RESUME` (user-initiated) **bypasses** the concurrent limit
- Auto-start from `HomeViewModel` respects the limit

**Intent actions handled:**
`START`, `PAUSE`, `RESUME`, `CANCEL`, `DELETE`, `DELETE_ALL`

**CDN URL expiry recovery:**
- On HTTP 403/410, service re-calls `unrestrictLink` using stored `rdDownloadId`
- Updates `downloadUrl` in DB and retries automatically

**Notifications:**
- Channel 1 (in-progress): ongoing with progress bar, pause/cancel actions
- Channel 2 (completed): dismissible, opens file or shows error

## DI — Hilt AppModule

| Provider | Scope |
|----------|-------|
| `Json` (kotlinx) | Singleton; `ignoreUnknownKeys = true` |
| `DataStore<Preferences>` | Singleton |
| `SettingsRepository` | Singleton |
| `AuthInterceptor` | Singleton |
| `OkHttpClient` (API) | Singleton |
| `OkHttpClient` (Downloader) | Singleton |
| `Retrofit` | Singleton |
| `RealDebridApi` | Singleton |
| `AppDatabase` | Singleton |
| `DownloadDao` | Singleton |
| `DownloadRepository` | Singleton |
| `DownloadEngine.Factory` | Singleton; creates per-download engine instances |

## Navigation

Bottom bar with 3 destinations (Compose NavHost):

| Route | Screen | ViewModel |
|-------|--------|-----------|
| `home` | Add links, user profile, recent RD downloads, torrent list | `HomeViewModel` |
| `downloads` | Local download queue with real-time progress | `DownloadsViewModel` |
| `settings` | Token, path, limits, notifications, theme | `SettingsViewModel` |

## CDN Behavior (Real-Debrid Lity 2.0)

- Multiple edge nodes (nyk3, nyk6, nyk7, …); some partially broken
- Open-ended `Range: bytes=0-` → connection hold without FIN (never delivers data)
- Bounded `Range: bytes=start-end` → works correctly
- Some nodes: return HTTP 206, then send 0 bytes and close → chunk retry handles this
- Fix: always probe with `bytes=0-0`, then download with explicit bounded chunks

## SAF Path Handling

Settings stores the download path as a SAF content URI:
```
content://com.android.externalstorage.documents/tree/primary%3ADownload%2FDebrid
```
`DownloadService.resolveDirectory()` converts this to a filesystem path:
```
/storage/emulated/0/Download/Debrid
```
Required for `RandomAccessFile` compatibility with pre-allocated parallel writes.

## Key Libraries

```kotlin
// Compose
androidx.compose.bom:2024.02.01
androidx.compose.material3
androidx.compose.material:material-icons-extended
androidx.navigation:navigation-compose:2.7.6

// DI
com.google.dagger:hilt-android:2.51.1
androidx.hilt:hilt-navigation-compose:1.1.0
androidx.hilt:hilt-work:1.1.0

// Network
com.squareup.retrofit2:retrofit:2.9.0
com.squareup.okhttp3:okhttp:4.12.0
org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2
com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0

// Persistence
androidx.datastore:datastore-preferences:1.0.0
androidx.room:room-runtime:2.6.1

// Background
androidx.work:work-runtime-ktx:2.9.0
```

## Known Architectural Gaps

1. **No chunk-level resume** — parallel path re-downloads all chunks on restart; only single-stream has partial resume
2. **Cancel/completion race** — `cancelDownload` and `handleResult` can both write final DB status; no mutex between them
3. **Legacy `FileDownloader`** — dead code, still wired into DI; not used by `DownloadService`
4. **No torrent polling backoff** — `HomeViewModel` polls torrent status on a fixed interval with no exponential backoff when idle
