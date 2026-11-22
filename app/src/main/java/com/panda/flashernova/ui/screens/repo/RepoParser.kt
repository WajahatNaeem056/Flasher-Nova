package com.panda.flashernova.ui.screens.repo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import java.io.IOException

/**
 * Parsing layer for the two endpoints (pure functions that do not touch Android APIs, so they can be unit tested).
 *
 * The official source is a plain-text link list; all metadata is in the path and file name:
 *   AnyKernel3_Official/MIXKI/6.6.x/BakaSU/AK3_BAKASU_6.6.89_35259_SUSFS_KPN_LZ4_DS_IN_ADIOS_BBG.zip
 *   AnyKernel3_Official/OORB/BakaSU/AK3_BAKASU_ONEP13T_35259_SUSFS_KPM_BBG_IN_FIX_HMBRID_DS.zip
 *
 * The community source is a JSON array with complete fields (author / features / hash / notes / time).
 */
internal object RepoParser {

    const val RAW_PREFIX: String =
        "https://raw.githubusercontent.com/Bacillusf/RKF-Resource-Repo/product/"

    private val MANAGER_VERSION_REGEX = Regex("""^\d{4,6}$""")
    private val KERNEL_VERSION_REGEX = Regex("""(?<![\d.])(\d+\.\d+\.\d+)(?![\d.])""")
    private val SERIES_REGEX = Regex("""^\d+\.\d+\.x$""")
    private val NUMERIC_REGEX = Regex("""^\d+(\.\d+)*$""")
    private val DEVICE_REGEX = Regex("""_ONEP([A-Za-z0-9]+)(?=_|$)""")

    private val MANAGER_LABELS = mapOf(
        "BAKASU" to "BakaSU",
        "KSUNEXT" to "KSU-Next",
        "KSU-NEXT" to "KSU-Next",
        "KSUN" to "KSUN",
        "SUKISU" to "SukiSU",
        "KSU" to "KSU",
        "RESUKI" to "ReSukiSU",
        "RESUKISU" to "ReSukiSU",
        "NONE" to null,
    )

    private val MANAGER_ORDER =
        listOf("KSU-Next", "KSU", "SukiSU", "ReSukiSU", "BakaSU", "KSUN")

    /**
     * Feature display order: rare, informative ones first; DS / IN / BBG appear in 100% of official-source entries,
     * Pushed to the end so the first three tags are not the same on every card.
     */
    private val FEATURE_ORDER = listOf(
        "SUSFS", "NOSUSFS", "KPM", "KPN", "LZ4", "HMBRID", "FIX", "SSG", "BBR", "ADIOS",
        "DROIDSPACE", "UFW-SUPPORT", "NTSYNC", "DS", "IN", "BBG",
    )

    fun normalizeManager(raw: String?): String? {
        val key = raw?.trim()?.uppercase().orEmpty()
        if (key.isEmpty()) return null
        return if (MANAGER_LABELS.containsKey(key)) MANAGER_LABELS[key] else raw?.trim()
    }

    fun managerRank(manager: String?): Int {
        if (manager == null) return 99
        val index = MANAGER_ORDER.indexOf(manager)
        return if (index < 0) 50 else index
    }

    fun kernelRank(version: String?): Int {
        val parts = version?.split('.') ?: return -1
        if (parts.size < 3) return -1
        val major = parts[0].toIntOrNull() ?: return -1
        val minor = parts[1].toIntOrNull() ?: return -1
        val patch = parts[2].toIntOrNull() ?: return -1
        return major * 1_000_000 + minor * 1_000 + patch
    }

    fun seriesRank(series: String?): Int {
        val head = series?.removeSuffix(".x") ?: return -1
        val parts = head.split('.')
        if (parts.size < 2) return -1
        val major = parts[0].toIntOrNull() ?: return -1
        val minor = parts[1].toIntOrNull() ?: return -1
        return major * 1000 + minor
    }

    /**
     * Display names of feature tokens. The three abbreviations in the official source are expanded following product convention; other tokens are shown in uppercase as-is.
     * The index includes both abbreviations and display names, so searching "betternet" or "IN" both hit.
     */
    private val FEATURE_LABELS = mapOf(
        "IN" to "BetterNet",
        "DS" to "DroidSpace",
        "LZ4" to "Lz4-Zarm",
    )

    fun featureLabel(token: String): String = FEATURE_LABELS[token.uppercase()] ?: token

    fun featureRank(feature: String): Int {
        val index = FEATURE_ORDER.indexOf(feature.uppercase())
        return if (index < 0) 80 else index
    }

    fun sortFeatures(features: List<String>): List<String> =
        features.distinct().sortedWith(compareBy({ featureRank(it) }, { it }))

    /** Official source: one raw direct link per line. */
    fun parseOfficial(body: String): List<RepoItem> {
        val result = ArrayList<RepoItem>()
        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            if (!line.startsWith("http", ignoreCase = true)) continue
            if (!line.endsWith(".zip", ignoreCase = true)) continue
            val path = if (line.startsWith(RAW_PREFIX)) {
                line.removePrefix(RAW_PREFIX)
            } else {
                line.substringAfter("/product/", "")
            }
            if (path.isEmpty()) continue
            val segments = path.split('/')
            if (segments.size < 4) continue
            val filename = segments.last()
            val kind = when {
                segments.contains("MIXKI") -> RepoKind.UNIVERSAL
                segments.contains("OORB") -> RepoKind.ONEPLUS
                else -> continue
            }
            val tokens = filename.removeSuffix(".zip").split('_')
            val manager = normalizeManager(segments.getOrNull(segments.size - 2))
                ?: tokens.firstNotNullOfOrNull { normalizeManager(it) }
            result.add(
                RepoItem(
                    filename = filename,
                    url = line,
                    source = RepoSource.OFFICIAL,
                    kind = kind,
                    manager = manager,
                    managerVer = tokens.firstOrNull { it.matches(MANAGER_VERSION_REGEX) },
                    deviceModel = if (kind == RepoKind.ONEPLUS) parseDeviceModel(filename) else null,
                    kernelVersion = KERNEL_VERSION_REGEX.find(filename)?.groupValues?.get(1),
                    kernelSeries = if (kind == RepoKind.UNIVERSAL) {
                        segments.getOrNull(2)?.takeIf { it.matches(SERIES_REGEX) }
                    } else {
                        null
                    },
                    features = sortFeatures(collectFeatures(tokens)),
                )
            )
        }
        return result
    }

    /** Community source: JSON array, 15 fields per entry. */
    fun parseCommunity(body: String): List<RepoItem> {
        val json = Json { ignoreUnknownKeys = true }
        val array = try {
            json.parseToJsonElement(body).jsonArray
        } catch (e: Exception) {
            throw IOException("Failed to parse community data", e)
        }
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val filename = obj.string("filename") ?: return@mapNotNull null
            val downloadUrl = obj.string("download_url") ?: return@mapNotNull null
            val author = obj["author"] as? JsonObject
            val featuresElement = obj["features"]
            val features = if (featuresElement is JsonArray) {
                featuresElement.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            } else {
                emptyList()
            }
            val device = obj.string("device")
            RepoItem(
                filename = filename,
                url = downloadUrl,
                source = RepoSource.COMMUNITY,
                kind = RepoKind.COMMUNITY,
                manager = normalizeManager(obj.string("manager")),
                managerVer = obj.string("manager_version"),
                deviceModel = device?.takeIf { it.isNotBlank() },
                kernelVersion = KERNEL_VERSION_REGEX.find(device.orEmpty())?.groupValues?.get(1),
                kernelSeries = null,
                features = sortFeatures(features),
                sizeHuman = obj.string("size_human"),
                sha256 = obj.string("sha256"),
                authorLogin = author?.string("login"),
                authorUrl = author?.string("url"),
                notes = obj.string("notes"),
                publishedAt = obj.string("published_at"),
                githubUrl = obj.string("github_url"),
            )
        }
    }

    /**
     * "Latest" only marks universal kernel flash packages from the official source: the one with the highest kernel version within the same (kernel series + manager).
     * The manager version number is constant across all packages of the same manager (35259 / 33548 ...), so using it for duplicate detection would fill the screen with stars;
     * OORB has only one package per model and the community source is not comparable, so neither is marked.
     */
    fun markLatest(items: List<RepoItem>) {
        items.forEach { it.isLatest = false }
        val groups = items
            .filter { it.kind == RepoKind.UNIVERSAL && it.kernelVersion != null }
            .groupBy { (it.kernelSeries ?: "") + "|" + (it.manager ?: "") }
        for (group in groups.values) {
            group.maxByOrNull { kernelRank(it.kernelVersion) }?.isLatest = true
        }
    }

    private fun parseDeviceModel(filename: String): String? {
        val code = DEVICE_REGEX.find(filename)?.groupValues?.get(1) ?: return null
        return prettyOnePlusModel(code)
    }

    /**
     * Features = all tokens in the file name other than structural tokens.
     * Structural tokens: AK3 / GKI / MTK (the platform is not distinguished and is ignored as required), ONEP, manager name, pure numeric version numbers.
     */
    fun collectFeatures(tokens: List<String>): List<String> {
        val result = LinkedHashSet<String>()
        for (raw in tokens) {
            val token = raw.uppercase()
            if (token.isEmpty()) continue
            if (token == "AK3" || token == "GKI" || token == "MTK" || token == "ZIP") continue
            if (token.startsWith("ONEP")) continue
            if (MANAGER_LABELS.containsKey(token)) continue
            if (token.matches(NUMERIC_REGEX)) continue
            result.add(token)
        }
        return result.toList()
    }

    /**
     * Split model codes such as `ONEP13T` / `ONEPACE5PRO` / `ONEPPAD2MT6991` into readable names.
     * Greedy dictionary scan + two merge rules: `CE/N/MT` sticks to the number after it, and a number sticks to the single-letter suffix after it
     * (T/R/V/X/S/G/E/A), so 13T -> "13T", ACE5PRO -> "Ace 5 Pro", and 5G is not split apart.
     */
    fun prettyOnePlusModel(code: String): String {
        val dictionary = listOf(
            "NORDCE" to "Nord CE",
            "ACERACE" to "Ace Race",
            "ACEPRO" to "Ace Pro",
            "NORD" to "Nord",
            "ACE" to "Ace",
            "PADGO" to "Pad Go",
            "PAD" to "Pad",
            "TURBO" to "Turbo",
            "OPEN" to "Open",
            "PRO" to "Pro",
            "RACE" to "Race",
            "ULTRA" to "Ultra",
            "LITE" to "Lite",
            "CE" to "CE",
            "GO" to "Go",
            "SE" to "SE",
            "MT" to "MT",
            "5G" to "5G",
        )
        val source = code.uppercase()
        val tokens = mutableListOf<String>()
        var index = 0
        while (index < source.length) {
            val char = source[index]
            if (char.isDigit()) {
                var end = index
                while (end < source.length && source[end].isDigit()) end++
                tokens.add(source.substring(index, end))
                index = end
                continue
            }
            // NORDN30SE5GV -> Nord / N / 30 / SE / 5G / V; the N must be split out on its own so it can stick to 30
            if (source.startsWith("NORDN", index)) {
                tokens.add("Nord")
                tokens.add("N")
                index += 5
                continue
            }
            val hit = dictionary.firstOrNull { source.startsWith(it.first, index) }
            if (hit != null) {
                tokens.add(hit.second)
                index += hit.first.length
                continue
            }
            tokens.add(char.toString())
            index++
        }
        val suffixes = setOf("T", "R", "V", "X", "S", "G", "E", "A")
        val merged = mutableListOf<String>()
        for (token in tokens) {
            val last = merged.lastOrNull()
            if (last != null && token.firstOrNull()?.isDigit() == true &&
                (last == "CE" || last == "N" || last == "MT")
            ) {
                merged[merged.size - 1] = last + token
                continue
            }
            if (last != null && last.all { it.isDigit() } &&
                token.length == 1 && suffixes.contains(token)
            ) {
                merged[merged.size - 1] = last + token
                continue
            }
            merged.add(token)
        }
        return "OnePlus " + merged.joinToString(" ")
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
