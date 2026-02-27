# Build Order

- [x] DownloadEngine.kt (OkHttp, progress, pause/resume)
- [x] StallDetector.kt (30s timeout, auto-retry)
- [x] Room DB (Entity, DAO, Database)
- [x] DownloadService.kt (Foreground)
- [x] DownloadsScreen.kt (Compose UI)
- [x] ViewModel + Repository
- [x] RealDebridApi.kt (Retrofit)
- [x] CacheScreen.kt
- [x] Settings + Notifications
- [x] Build APK (ready - needs Android SDK)

Problem: Zero visibility into downloads, silent failures.
Solution: Full custom implementation with real-time tracking.

---

## Remaining Tasks

### Done (this session)

- [x] Fix exception path progress leak in `downloadChunk` catch block — `actualBytes` now subtracted on exception
- [x] Add inter-chunk cancellation — `chunkFailed` AtomicBoolean aborts sibling chunks when one permanently fails
- [x] Pre-allocate file before parallel downloads — `setLength(totalBytes)`, wrapped in try-catch for SAF paths
- [x] Fix SAF content URI crash in `getOutputFile` — added `resolveDirectory()` to convert `content://` URIs to filesystem paths

### Critical

- [ ] **Downloads stuck in DOWNLOADING status — never marked COMPLETED/FAILED** (DownloadService.kt:103-121)
  - `serviceScope.launch` with `SupervisorJob` silently swallows uncaught exceptions
  - `handleResult` never called → DB entry stuck in DOWNLOADING, notification stuck, service never stops
  - Also affects cancel: race between `cancelDownload` and `handleResult` can overwrite CANCELLED with COMPLETED/FAILED (DownloadService.kt:202-217)
  - Fix: wrap entire job body in try-catch, call `handleResult` with error on any throwable; gate `handleResult` DB writes on current status to prevent race

- [ ] **HTTP logging exposes auth token in release builds** (AppModule.kt:67-69, 122-125)
  - API client uses `Level.BODY` — logs Bearer token and full response bodies
  - Downloader client uses `Level.HEADERS` — logs all headers
  - Neither is gated on `BuildConfig.DEBUG`
  - Fix: wrap both in `if (BuildConfig.DEBUG)` guard

- [ ] **Expired CDN URL = permanent failure, no auto-recovery** (DownloadEntity.kt, DownloadRepository.kt)
  - RD unrestrict URLs are time-limited; retries hit the same expired URL
  - `DownloadEntity` has no `torrentId` or `rdDownloadId` field
  - Fix: add `torrentId: String? = null` to entity (Room migration), re-unrestrict on HTTP 403/410 in retry path

### High

- [ ] **Combine Home tab and Cache tab into a single screen**
  - Both tabs show essentially the same content (RD downloads/torrents)
  - Merge into one unified screen with filters/sections instead of two separate tabs
  - Simplifies navigation and reduces redundancy

- [ ] **Torrent file picker — browse and select individual files from a torrent**
  - When adding a torrent with multiple files, show a file list and let the user choose which to download
  - Currently calls `selectFiles(id, "all")` — should present the file list from `getTorrentInfo` and let user pick
  - Use the `files` array from TorrentInfo to build a selectable list UI

- [ ] **Properly clear/delete downloads and fix pause behavior**
  - Clearing a download should: cancel active engine, remove from `activeDownloads`, delete partial file, remove DB entry, dismiss notification
  - Pause should correctly persist state so resume works (currently pause only works while engine is in memory; service restart = full re-download)
  - Bulk clear (clear all completed, clear all failed, etc.)

- [ ] **Single-stream fallback uses open-ended `Range: bytes=N-`** (DownloadEngine.kt:322-327)
  - Same CDN stall pattern the parallel path was designed to avoid
  - Fix: either use bounded range (probe size first) or remove the Range header entirely for fresh starts

### Medium

- [ ] **Display human-readable path in Settings UI** (SettingsScreen.kt:143-147)
  - Shows raw `content://com.android.externalstorage.documents/tree/primary%3ADownload%2FDebrid`
  - Fix: reuse `resolveDirectory()` logic or extract volume:path from URI for display

- [ ] **StallDetector not reset between single-stream retries** (DownloadEngine.kt:288, 362)
  - Carries stale `lastProgressBytes`/`lastProgressTime` from previous attempt
  - Fix: call `stallDetector.reset()` at top of `executeDownload`

- [ ] **RandomAccessFile opened before try-block in `readBodyToFile`** (DownloadEngine.kt:358-360)
  - If `raf.seek()` throws, the file handle leaks (not inside try-catch)
  - Fix: move `raf` creation inside the try block, or use `use {}` scope

- [ ] **Parallel downloads always re-download all chunks on resume** (DownloadEngine.kt:81-154)
  - `downloadParallel` has no chunk-level resume; `setLength(totalBytes)` zeros unwritten regions
  - Low priority since parallel path is the primary path and chunks are small (4MB)

### Low

- [ ] **Remove dead `FileDownloader` class** (FileDownloader.kt, AppModule.kt:131-133)
  - Legacy blocking implementation, superseded by `DownloadEngine`
  - Still wired into DI via `FileDownloader.Factory`
  - Uses `Thread.sleep()` and open-ended Range headers

- [ ] **`instantAvailability` only checks one hash per call** (RealDebridApi.kt:75-76)
  - RD API supports comma-separated hashes; current interface requires N calls for N hashes

---

## Build Instructions

1. Install Android Studio or Android SDK
2. Set ANDROID_HOME environment variable
3. Create `local.properties`:
   ```
   sdk.dir=/path/to/Android/sdk
   ```
4. Build:
   ```bash
   ./gradlew assembleDebug    # Debug APK
   ./gradlew assembleRelease  # Release APK (needs signing)
   ```

APK output: `app/build/outputs/apk/debug/app-debug.apk`
