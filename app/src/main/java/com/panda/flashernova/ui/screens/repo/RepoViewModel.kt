package com.panda.flashernova.ui.screens.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panda.flashernova.SharedViewModels
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Data source for the kernel repository.
 *
 * Both endpoints are provided by the official website (reflasher.site); the app no longer connects to GitHub directly:
 *  - OFFICIAL : plain text, one raw direct link per line, 121 lines in total;
 *  - COMMUNITY: JSON array, 15 fields per entry (author / features / hash / notes, etc.).
 */
enum class RepoSource(
    val label: String,
    val endpoint: String,
    val cacheName: String,
    val stampName: String,
) {
    OFFICIAL(
        "Official source",
        "https://reflasher.site/resources/api/official",
        "repo_official.txt",
        "repo_official.stamp",
    ),
    COMMUNITY(
        "Community source",
        "https://reflasher.site/resources/api/community",
        "repo_community.json",
        "repo_community.stamp",
    )
}

/**
 * Classification of entries. The official source is classified by path:
 *  - Path contains MIXKI -> universal kernel flash (generic kernel version package)
 *  - Path contains OORB  -> OnePlus per-model dedicated package
 * The community source has no classification mechanism and is uniformly treated as COMMUNITY.
 */
enum class RepoKind(val label: String) {
    UNIVERSAL("Universal kernel"),
    ONEPLUS("OnePlus models"),
    COMMUNITY("Community"),
}

/** Category filter for the official source (the community source has no categories, so this filter is hidden). */
enum class TypeFilter(val label: String, val kind: RepoKind?) {
    ALL("All types", null),
    UNIVERSAL("Universal kernel", RepoKind.UNIVERSAL),
    ONEPLUS("OnePlus models", RepoKind.ONEPLUS),
}

class RepoItem(
    val filename: String,
    val url: String,
    val source: RepoSource,
    val kind: RepoKind,
    val manager: String?,
    val managerVer: String?,
    val deviceModel: String?,
    val kernelVersion: String?,
    val kernelSeries: String?,
    val features: List<String>,
    val sizeHuman: String? = null,
    val sha256: String? = null,
    val authorLogin: String? = null,
    val authorUrl: String? = null,
    val notes: String? = null,
    val publishedAt: String? = null,
    val githubUrl: String? = null,
) {
    var isLatest by mutableStateOf(false)

    /** Display text for feature chips (IN->BetterNet / DS->DroidSpace / LZ4->Lz4-Zarm). */
    val featureLabels: List<String>
        get() = features.map { RepoParser.featureLabel(it) }

    /** First red capsule on the card: manager:manager version. The community source allows "no manager". */
    val managerVersion: String
        get() {
            val m = manager ?: return "No manager"
            val ver = managerVer ?: return m
            return "$m:$ver"
        }

    /** Second red capsule on the card: model (OORB / community) or kernel version (MIXKI). */
    val line2: String
        get() = when {
            deviceModel != null -> deviceModel
            kernelVersion != null -> "Kernel $kernelVersion"
            else -> "Unknown"
        }

    /** For fine-grained search: put every field visible or imaginable on the card into the index. */
    val searchBlob: String by lazy {
        buildString {
            append(filename).append(' ')
            deviceModel?.let { append(it).append(' ') }
            kernelVersion?.let { append(it).append(' ') }
            kernelSeries?.let { append(it).append(' ') }
            manager?.let { append(it).append(' ') }
            managerVer?.let { append(it).append(' ') }
            features.forEach { append(it).append(' ') }
            // Display names are indexed too, so searching BetterNet / DroidSpace / Lz4-Zarm also hits
            featureLabels.forEach { append(it).append(' ') }
            notes?.let { append(it).append(' ') }
            authorLogin?.let { append(it).append(' ') }
            append(kind.label).append(' ')
            append(source.label)
        }.lowercase()
    }
}

class DownloadState {
    var progress by mutableStateOf(0f)
    var downloading by mutableStateOf(false)
    var completed by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

/** ISO time from the community source -> human-readable. */
object RepoTime {
    private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val fullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun parseIso(value: String?): OffsetDateTime? =
        if (value.isNullOrBlank()) null else runCatching { OffsetDateTime.parse(value) }.getOrNull()

    /** "Today 16:48 / Yesterday 16:48 / 11-12 16:48". */
    fun human(value: String?): String? {
        val odt = parseIso(value) ?: return value
        val zone = ZoneId.systemDefault()
        val time = odt.atZoneSameInstant(zone)
        val today = LocalDate.now(zone)
        return when (time.toLocalDate()) {
            today -> "Today " + time.format(clockFormatter)
            today.minusDays(1) -> "Yesterday " + time.format(clockFormatter)
            else -> time.format(dateFormatter)
        }
    }

    /** Full time in the info table. */
    fun full(value: String?): String {
        val odt = parseIso(value) ?: return value ?: "—"
        return odt.atZoneSameInstant(ZoneId.systemDefault()).format(fullFormatter)
    }

    fun clock(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(clockFormatter)
}

class RepoViewModel : ViewModel() {
    companion object {
        const val TAG: String = "FlasherNova/RepoState"
        const val DOWNLOAD_DIR: String = "/sdcard/Download"
        const val LOCAL_DL_DIR: String = "repo_downloads"
    }

    private val client = OkHttpClient()
    private val officialItems = mutableListOf<RepoItem>()
    private val communityItems = mutableListOf<RepoItem>()
    private val loadedSources = mutableSetOf<RepoSource>()

    private val _allItems = mutableStateListOf<RepoItem>()
    private val _items = mutableStateListOf<RepoItem>()
    val downloadStates = mutableStateMapOf<String, DownloadState>()
    private var filesDir: File? = null

    var source by mutableStateOf(RepoSource.OFFICIAL)
        private set
    var query by mutableStateOf("")
    var typeFilter by mutableStateOf(TypeFilter.ALL)
        private set
    var versionKey by mutableStateOf<String?>(null)
        private set
    var managerKey by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var lastFetchedAt by mutableStateOf<Long?>(null)
        private set
    var pendingFlashItem by mutableStateOf<RepoItem?>(null)
        private set
    var infoItem by mutableStateOf<RepoItem?>(null)
        private set
    var downloadRequestItem by mutableStateOf<RepoItem?>(null)
        private set

    val items: List<RepoItem>
        get() = _items
    val allItems: SnapshotStateList<RepoItem>
        get() = _allItems

    /** Manager filter options are generated dynamically from the current source's data, so new managers added on the website need no app change. */
    val managerOptions: List<String>
        get() = _allItems.mapNotNull { it.manager }.distinct().sortedBy { RepoParser.managerRank(it) }

    /** Kernel series filter options are also generated dynamically (6.1.x / 6.6.x / 6.12.x ...), newest first. */
    val seriesOptions: List<String>
        get() = _allItems.mapNotNull { it.kernelSeries }.distinct().sortedByDescending { RepoParser.seriesRank(it) }

    val universalCount: Int
        get() = _allItems.count { it.kind == RepoKind.UNIVERSAL }

    val onePlusCount: Int
        get() = _allItems.count { it.kind == RepoKind.ONEPLUS }

    fun fetch(context: Context, force: Boolean = false) {
        if (isLoading) return
        val requested = source
        val alreadyLoaded = loadedSources.contains(requested)
        if (!alreadyLoaded) {
            loadCache(context, requested)
        } else if (!force) {
            return
        }

        isLoading = true
        loadError = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val body = httpGet(requested.endpoint)
                val parsed = when (requested) {
                    RepoSource.OFFICIAL -> RepoParser.parseOfficial(body)
                    RepoSource.COMMUNITY -> RepoParser.parseCommunity(body)
                }
                if (parsed.isEmpty()) throw IOException("The endpoint returned nothing")
                RepoParser.markLatest(parsed)
                saveCache(context, requested, body)
                withContext(Dispatchers.Main) {
                    replaceSourceItems(requested, parsed)
                    loadedSources.add(requested)
                    lastFetchedAt = System.currentTimeMillis()
                    if (source == requested) {
                        syncAllItems()
                        applyFilters()
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetch ${requested.label} failed", e)
                withContext(Dispatchers.Main) {
                    isLoading = false
                    loadError = e.message ?: "Failed to load"
                    applyFilters()
                }
            }
        }
    }

    fun selectSource(context: Context, value: RepoSource) {
        if (source == value) return
        source = value
        typeFilter = TypeFilter.ALL
        versionKey = null
        managerKey = null
        loadError = null
        lastFetchedAt = runCatching {
            stampFile(context, value).takeIf { it.exists() }?.readText()?.toLongOrNull()
        }.getOrNull()
        syncAllItems()
        applyFilters()
        fetch(context)
    }

    fun selectTypeFilter(value: TypeFilter) {
        typeFilter = value
        versionKey = null
        applyFilters()
    }

    fun selectVersionFilter(value: String?) {
        versionKey = value
        applyFilters()
    }

    fun selectManagerFilter(value: String?) {
        managerKey = value
        applyFilters()
    }

    fun updateQuery(value: String) {
        query = value
        applyFilters()
    }

    fun showInfo(item: RepoItem) {
        infoItem = item
    }

    fun hideInfo() {
        infoItem = null
    }

    /** Community source: tapping download shows the info first, and the download only starts after scrolling to the bottom and confirming. */
    fun requestDownload(item: RepoItem) {
        downloadRequestItem = item
    }

    fun cancelDownloadRequest() {
        downloadRequestItem = null
    }

    fun download(context: Context, item: RepoItem) {
        val state = downloadStates.getOrPut(item.url) { DownloadState() }
        if (state.downloading || state.completed) return
        filesDir = context.filesDir
        state.downloading = true
        state.progress = 0f
        state.error = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = downloadToFile(context, item, state)
                Shell.cmd("mkdir -p $DOWNLOAD_DIR").exec()
                val dest = "$DOWNLOAD_DIR/${item.filename}"
                val res = Shell.cmd("cp -f '${file.absolutePath}' '$dest'").exec()
                if (!res.isSuccess) throw IOException("Failed to save to the Downloads folder")
                withContext(Dispatchers.Main) {
                    state.downloading = false
                    state.completed = true
                    pendingFlashItem = item
                }
            } catch (e: Exception) {
                Log.e(TAG, "download failed", e)
                withContext(Dispatchers.Main) {
                    state.downloading = false
                    state.error = e.message ?: "Download failed"
                }
            }
        }
    }

    fun clearPending() {
        pendingFlashItem = null
    }

    fun flashToActiveSlot(item: RepoItem): String {
        val mainVM = SharedViewModels.mainViewModel
        val suffix = mainVM.slotSuffix
        val slotVM = if (mainVM.isAb && suffix == "_b") mainVM.slotB else mainVM.slotA
        slotVM?.flashActionType = "flashAk3"
        val localFile = filesDir?.let { File(File(it, LOCAL_DL_DIR), item.filename) }
        slotVM?.flashActionURI = if (localFile != null && localFile.exists()) {
            Uri.fromFile(localFile)
        } else {
            Uri.fromFile(File(DOWNLOAD_DIR, item.filename))
        }
        slotVM?.showConfirmDialog()
        return suffix
    }

    private suspend fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "FlasherNova")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Fetch failed: HTTP ${response.code}")
            return response.body.string()
        }
    }

    private suspend fun downloadToFile(context: Context, item: RepoItem, state: DownloadState): File {
        val dir = File(context.filesDir, LOCAL_DL_DIR)
        dir.mkdirs()
        val file = File(dir, item.filename)
        val request = Request.Builder()
            .url(item.url)
            .header("User-Agent", "FlasherNova")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        if (total > 0) state.progress = downloaded.toFloat() / total
                    }
                }
            }
        }
        return file
    }

    private fun replaceSourceItems(target: RepoSource, items: List<RepoItem>) {
        val list = if (target == RepoSource.OFFICIAL) officialItems else communityItems
        list.clear()
        list.addAll(items)
    }

    private fun syncAllItems() {
        val list = if (source == RepoSource.OFFICIAL) officialItems else communityItems
        _allItems.clear()
        _allItems.addAll(list)
    }

    private fun applyFilters() {
        val keyword = query.trim()
        val type = typeFilter
        val series = versionKey
        val manager = managerKey
        val filtered = _allItems.filter { item ->
            (keyword.isEmpty() || item.searchBlob.contains(keyword.lowercase())) &&
                (type == TypeFilter.ALL || item.kind == type.kind) &&
                (series == null || item.kernelSeries == series) &&
                (manager == null || item.manager == manager)
        }
        val sorted = if (source == RepoSource.OFFICIAL) {
            filtered.sortedWith { a, b ->
                var compare = a.kind.ordinal.compareTo(b.kind.ordinal)
                if (compare == 0) compare = RepoParser.seriesRank(b.kernelSeries).compareTo(RepoParser.seriesRank(a.kernelSeries))
                if (compare == 0) compare = RepoParser.managerRank(a.manager).compareTo(RepoParser.managerRank(b.manager))
                if (compare == 0) compare = RepoParser.kernelRank(b.kernelVersion).compareTo(RepoParser.kernelRank(a.kernelVersion))
                if (compare == 0) compare = a.filename.compareTo(b.filename)
                compare
            }
        } else {
            filtered
        }
        val (latest, rest) = sorted.partition { it.isLatest }
        _items.clear()
        _items.addAll(latest)
        _items.addAll(rest)
    }

    // ---------------------------------------------------------------- Cache

    private fun cacheFile(context: Context, target: RepoSource): File =
        File(context.filesDir, target.cacheName)

    private fun stampFile(context: Context, target: RepoSource): File =
        File(context.filesDir, target.stampName)

    private fun saveCache(context: Context, target: RepoSource, body: String) {
        runCatching {
            cacheFile(context, target).writeText(body)
            stampFile(context, target).writeText(System.currentTimeMillis().toString())
        }.onFailure { Log.e(TAG, "save cache failed", it) }
    }

    private fun loadCache(context: Context, target: RepoSource) {
        runCatching {
            val file = cacheFile(context, target)
            if (!file.exists() || file.length() == 0L) return@runCatching
            val body = file.readText()
            val parsed = when (target) {
                RepoSource.OFFICIAL -> RepoParser.parseOfficial(body)
                RepoSource.COMMUNITY -> RepoParser.parseCommunity(body)
            }
            if (parsed.isEmpty()) return@runCatching
            RepoParser.markLatest(parsed)
            replaceSourceItems(target, parsed)
            loadedSources.add(target)
            if (source == target) {
                syncAllItems()
                applyFilters()
                lastFetchedAt = stampFile(context, target)
                    .takeIf { it.exists() }?.readText()?.toLongOrNull()
            }
        }.onFailure { Log.e(TAG, "load cache failed", it) }
    }
}
