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
- [x] **Downloads stuck in DOWNLOADING** — `engine.download()` wrapped in try-catch inside job; `CancellationException` silently absorbed; `handleResult` guards against CANCELLED→COMPLETED/FAILED race by checking DB status before writing (DownloadService.kt)
- [x] **HTTP logging exposes auth token in release** — both `HttpLoggingInterceptor` usages wrapped in `if (BuildConfig.DEBUG)` (AppModule.kt)
- [x] **Single-stream open-ended Range header** — `Range: bytes=0-` no longer sent on fresh starts; header only added when `existingBytes > 0` (DownloadEngine.kt:327)
- [x] **Expired CDN URL = permanent failure** — added `rdDownloadId: String?` to `DownloadEntity`, DB migrated 3→4, stored on queue; `handleResult` re-unrestricts on HTTP 403/410 and retries once (DownloadService.kt, AppDatabase.kt, DownloadRepository.kt)

### High

- [x] **Combine Home tab and Cache tab into a single screen**
  - Both tabs show essentially the same content (RD downloads/torrents)
  - Merge into one unified screen with filters/sections instead of two separate tabs
  - Simplifies navigation and reduces redundancy; recentDownloads shown in "Recent Downloads" section below Torrents

- [x] **Torrent file picker — browse and select individual files from a torrent**
  - When adding a torrent with multiple files, show a file list and let the user choose which to download
  - Currently calls `selectFiles(id, "all")` — should present the file list from `getTorrentInfo` and let user pick
  - Use the `files` array from TorrentInfo to build a selectable list UI
  - Extended to DOWNLOADED torrents: tapping Download opens the picker pre-checked with already-selected files; cancel does not delete the torrent

- [x] **Pause/Cancel status not reflected in UI** — `DownloadDao.updateProgress` WHERE clause now includes `AND status = 'DOWNLOADING'`; `handleProgress` guards notification update with `isPaused()` check (DownloadDao.kt, DownloadService.kt)

- [x] **Multi-file torrent: folder download with custom name**
  - For DOWNLOADED torrents with more than one file, show an "Open Folder" button instead of "Download"
  - Tapping "Open Folder" opens a dialog: folder name field (pre-filled with torrent filename, editable) + file picker
  - All selected files are downloaded into `<outputDir>/<folderName>/`
  - Single-file torrents keep the existing "Download" button behavior (no subfolder)
  - Affected files: HomeScreen.kt (TorrentCard button logic), HomeViewModel.kt (folder name param), DownloadRepository / DownloadService (prepend subfolder to output path)

- [x] **Clear All in Downloads page with confirmation dialog**
  - Add a "Clear All" button/action in the Downloads screen toolbar or menu
  - Tapping it shows an "Are you sure?" confirmation dialog before proceeding
  - On confirm: cancel all active downloads, remove all DB entries, delete partial files, dismiss notifications
  - Affected files: DownloadsScreen.kt (UI button + dialog), DownloadsViewModel.kt (clearAll action), DownloadRepository / DownloadService (bulk cancel + cleanup)

- [x] **Properly clear/delete downloads and fix pause behavior**
  - Clearing a download should: cancel active engine, remove from `activeDownloads`, delete partial file, remove DB entry, dismiss notification
  - Pause should correctly persist state so resume works (currently pause only works while engine is in memory; service restart = full re-download)
  - Bulk clear (clear all completed, clear all failed, etc.)

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

## Lessons Learned

### SupervisorJob silently eats child coroutine exceptions
`serviceScope.launch` with `SupervisorJob` will not propagate `CancellationException` or any other throwable thrown inside the child. Any code after the throwing call (e.g. `handleResult`) is simply never reached. **Always wrap the body of a `SupervisorJob` child in try-catch** and explicitly handle both `CancellationException` and general exceptions.

### Coroutine cancellation requires special handling — never re-throw CancellationException in service code
`job.cancel()` causes `engine.download()` to throw `CancellationException`. If you catch it and re-throw, the parent `SupervisorJob` sees a cancelled child and may behave unexpectedly. Absorb it silently; the `cancelDownload()` path already handles all cleanup.

### Concurrent cancel + result = race condition in DB status
`cancelDownload()` sets status to CANCELLED in a separate coroutine. If `engine.download()` returns `Success` just before the cancel takes effect, `handleResult` overwrites CANCELLED with COMPLETED. **Gate all terminal DB writes on the current DB status** — read it inside `handleResult` before writing.

### Open-ended Range header (`bytes=0-`) triggers CDN stall
Real-Debrid's CDN holds open-ended range connections without sending a FIN. The read blocks until the timeout. **Never send `Range: bytes=0-` on a fresh download.** Either probe the file size first (use `bytes=0-0`) or omit the header entirely.

### HttpLoggingInterceptor at `Level.BODY` logs Bearer tokens in plaintext
Any OkHttp logging interceptor above `Level.NONE` will log Authorization headers. **Always gate on `BuildConfig.DEBUG`** — failing to do so leaks API credentials to logcat in production builds.

### RD unrestrict URLs are time-limited — store the original link, not just the CDN URL
CDN URLs expire (HTTP 403/410). Retrying with the same URL loops forever. **Store `rdDownloadId` (from `UnrestrictResponse.id`) and `originalUrl` at queue time** so the service can call `unrestrictLink` again on expiry to get a fresh CDN URL.

### Progress loop race condition — guard DB updates with a status WHERE clause
`handleProgress` fires every ~200ms and unconditionally writes `DOWNLOADING` back to the DB, overwriting `PAUSED` or `CANCELLED`. Fix: add `AND status = 'DOWNLOADING'` to the SQL WHERE clause so the UPDATE is a no-op when state has already changed. Also gate notification updates on `isPaused()` to prevent "Paused" text being overwritten by stale progress events.

### Room schema changes require explicit migrations
Adding a nullable column to an `@Entity` without a corresponding `Migration` causes a crash at startup on existing installs. **Bump `@Database(version = N)` and add a `Migration(N-1, N)` with `ALTER TABLE ... ADD COLUMN`** every time the entity schema changes. Keep `fallbackToDestructiveMigration()` only during development.

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
