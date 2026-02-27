# Lessons Learned: Real-Debrid Downloader

## Project Overview

Built an Android app for downloading files via Real-Debrid with:
- OkHttp download engine with stall detection and auto-recovery
- Kotlin/Compose/MVVM architecture
- Room database for persistence
- Foreground service with notifications

## What Worked Well

### 1. Core Architecture Decisions

- **DownloadEngine with Flow-based progress** - Clean, reactive, works well with Compose
- **Foreground Service** - Correct choice for long-running downloads (vs WorkManager)
- **Room + StateFlow** - Reactive UI updates from database changes
- **Sealed classes for results** - `DownloadResult.Success/Error/Cancelled` is type-safe and clear
- **Factory pattern for engine** - Allows creating fresh engine instances per download

### 2. Technical Implementations

- **StallDetector** - 30-second timeout with pause-awareness works reliably
- **Range header support** - Resume downloads after pause/failure
- **Exponential backoff** - Prevents hammering failed servers
- **AtomicBoolean/AtomicLong** - Thread-safe state without locks
- **ConcurrentHashMap for active downloads** - Safe concurrent access in service

### 3. Clean Patterns

```
UI Screen → ViewModel → Repository → DAO/API
                              ↓
                     DownloadService → DownloadEngine
```

Simple, predictable data flow.

---

## What Didn't Work (Cleanup Required)

### 1. Duplicate Download Implementations

**Created both:**
- `DownloadEngine.kt` (coroutine/Flow-based) ✓ Used
- `FileDownloader.kt` (callback-based) ✗ Unused

**Impact:** ~135 lines of dead code

**Lesson:** Pick one approach. Flow-based is more idiomatic for modern Android with Compose.

### 2. Unnecessary Abstraction Layers

**Deleted files (~861 lines):**
```
DownloadManager.kt    - 106 lines (wrapper around engine)
DownloadTask.kt       -  71 lines (data class wrapper)
DownloadWorker.kt     - 164 lines (WorkManager - wrong tool)
DebugDownloadActivity - 374 lines (unrequested debug UI)
DebugDownloadViewModel- 146 lines (unrequested debug VM)
```

**Lesson:** Service + Engine is sufficient. Don't build management layers speculatively.

### 3. Duplicate Utility Functions

**`formatBytes()` exists in 4+ places:**
- `DownloadsScreen.kt`
- `CacheScreen.kt`
- `EngineProgress` class
- `DownloadState.kt`

**Lesson:** Create single utility file:
```kotlin
// utils/FormatUtils.kt
fun Long.formatBytes(): String
fun Long.formatSpeed(): String
fun Long.formatEta(): String
```

### 4. Duplicate State Representations

**Three overlapping concepts:**
- `DownloadStatus` enum (in entity) ✓ Used
- `DownloadState` sealed class ✗ Unused
- `EngineEvent` sealed class (engine-specific, fine)

**Lesson:** One source of truth for states. The enum in the entity is sufficient for UI.

### 5. Duplicate Database Classes

- `AppDatabase.kt` ✓ Used
- `DownloadDatabase.kt` ✗ Unused

**Lesson:** One database class per app.

---

## What To Do Differently

### 1. Start Minimal

**Instead of:** Building engine + manager + worker + service + debug UI all at once

**Do:**
1. DownloadEngine (core logic)
2. DownloadService (runs engine)
3. Basic UI showing progress
4. Verify it works end-to-end
5. Add features incrementally

### 2. One Implementation Per Concern

| Concern | Create | Don't Create |
|---------|--------|--------------|
| Download execution | DownloadEngine | FileDownloader, DownloadManager |
| Background work | Foreground Service | WorkManager for downloads |
| State tracking | DownloadStatus enum | DownloadState sealed class |
| Database | AppDatabase | DownloadDatabase |
| Formatting | FormatUtils.kt | Inline functions everywhere |

### 3. Don't Pre-Build Debug Tools

Debug UI (520 lines) was deleted immediately. Build debugging when you need it, not speculatively.

### 4. Verify Before Expanding

After each component:
- Does it compile?
- Does it run?
- Does it integrate with existing code?

Don't build 5 components then debug them all at once.

### 5. Respect Existing Patterns

If the codebase uses enums for status, don't add a sealed class for the same concept. Follow what's there.

---

## CDN-Specific Lessons (Real-Debrid Lity 2.0)

### 6. Never Use Open-Ended Range Requests on RD CDN

`Range: bytes=0-` causes the CDN to hold the connection open without sending a FIN. The client blocks on body reads until the full read timeout (5 minutes). Always use bounded ranges: `bytes=start-end`.

**Probe pattern:** `bytes=0-0` to get `Content-Range: bytes 0-0/totalSize` from header only. Never read the body of the probe response.

### 7. CDN Nodes Are Unreliable — Fail Fast

Some CDN nodes (nyk3, nyk6, nyk7) accept TCP, return HTTP 206, then send 0 bytes and close. With `MAX_CHUNK_RETRIES=30` and long delays, a single dead node stalled downloads for 24 minutes. Reduced to `MAX_CHUNK_RETRIES=5` with `CHUNK_RETRY_DELAY_MS=1000` — fails in ~30s instead.

### 8. Progress Tracking Is Hard With Retries

Three separate progress bugs found:
1. **Incomplete chunk path** — forgot to subtract `actualBytes` from counter before returning error (fixed: `downloadedBytes.addAndGet(-actualBytes)`)
2. **Exception path** — `actualBytes` scoped inside `try` block, catch block couldn't access it to undo progress (fixed: moved to outer scope)
3. **Inter-chunk failure** — one chunk fails, 7 others keep running and adding bytes for ~40s (fixed: `chunkFailed` AtomicBoolean aborts siblings)

**Lesson:** Every exit path from a download loop must account for the bytes already added to the progress counter. Track `actualBytes` at the widest possible scope.

### 9. SAF Content URIs Are Not Filesystem Paths

Android's Storage Access Framework returns URIs like `content://com.android.externalstorage.documents/tree/primary%3ADownload%2FDebrid`. Passing this to `File()` or `RandomAccessFile()` crashes with `ENOENT`. Must convert to real path: parse the URI, extract volume + relative path, map `primary` → `/storage/emulated/0/`.

**Gotcha:** `outputFile.parentFile?.mkdirs()` silently returns `false` on content URIs — no crash, no error, just a directory that doesn't exist. The crash only surfaces later when `RandomAccessFile` tries to open the file.

### 10. SupervisorJob Silently Swallows Exceptions

`CoroutineScope(SupervisorJob() + Dispatchers.IO)` will absorb child coroutine failures. If the download job throws an uncaught exception, `handleResult` is never called — the DB entry stays in DOWNLOADING status forever, the notification stays up, and the service never stops. Always wrap the entire job body in try-catch when using SupervisorJob.

---

## Code Quality Checklist (For Future Projects)

Before considering a component "done":

- [ ] No duplicate implementations of the same functionality
- [ ] No unused files or classes
- [ ] Utility functions in one place, not scattered
- [ ] One state representation per domain concept
- [ ] No speculative abstractions (YAGNI)
- [ ] Debug code only when debugging is needed
- [ ] Compiles and integrates with existing code
- [ ] Every error/cancel exit path undoes side effects (progress counters, file handles)
- [ ] Content URIs vs filesystem paths handled correctly
- [ ] HTTP logging gated on BuildConfig.DEBUG
- [ ] Concurrent state mutations are atomic (cancel + completion race)

---

## Summary

| Category | Lines Wasted | Root Cause |
|----------|-------------|------------|
| Duplicate downloader | ~135 | Built two approaches |
| Manager/Task/Worker | ~341 | Speculative abstraction |
| Debug UI | ~520 | Built unrequested features |
| Duplicate utilities | ~40 | Copy-paste |
| Duplicate state class | ~43 | Didn't check existing patterns |
| Duplicate database | ~12 | Didn't clean up |
| **Total** | **~1,091** | **Over-engineering** |

**Key Insight:** The final working solution is simpler than what was initially built. Complexity was removed, not added, to make it work.

**Mantra:** Build the simplest thing that works. Add complexity only when there's a concrete, immediate need.
