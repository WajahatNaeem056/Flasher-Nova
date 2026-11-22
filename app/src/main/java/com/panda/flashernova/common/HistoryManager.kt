package com.panda.flashernova.common

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.panda.flashernova.common.extensions.ExtendedFile.outputStream
import com.panda.flashernova.common.extensions.ExtendedFile.readText
import com.panda.flashernova.common.types.history.HistoryEntry
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

object HistoryManager {
    private const val TAG = "HistoryManager"
    private const val HISTORY_FILE = "history.json"

    val entries = mutableStateListOf<HistoryEntry>()

    private var fileSystemManager: FileSystemManager? = null

    fun init(fileSystemManager: FileSystemManager) {
        this.fileSystemManager = fileSystemManager
        load()
    }

    fun record(entry: HistoryEntry) {
        entries.add(0, entry)
        save()
    }

    fun clearAll() {
        entries.clear()
        save()
    }

    @SuppressLint("SdCardPath")
    private fun getHistoryFile() =
        fileSystemManager?.getFile("/sdcard/FlasherNova/$HISTORY_FILE")

    @OptIn(ExperimentalSerializationApi::class)
    private fun load() {
        try {
            val file = getHistoryFile() ?: return
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    val loaded = Json.decodeFromString<List<HistoryEntry>>(content)
                    entries.clear()
                    entries.addAll(loaded)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load history", e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun save() {
        try {
            val file = getHistoryFile() ?: return
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val indentedJson = Json { prettyPrint = true }
            file.outputStream().use { it.write(indentedJson.encodeToString(entries.toList()).toByteArray(Charsets.UTF_8)) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save history", e)
        }
    }
}
