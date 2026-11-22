package com.panda.flashernova.ui.screens.history

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.panda.flashernova.common.AutoBackupManager
import com.panda.flashernova.common.HistoryManager

class HistoryViewModel : ViewModel() {

    val entries get() = HistoryManager.entries

    val isEmpty get() = entries.isEmpty()

    fun clearAll() {
        HistoryManager.clearAll()
    }

    fun rollback(context: Context, timestamp: Long, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = AutoBackupManager.rollback(context, timestamp)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, if (ok) "Roll back succeeded" else "Roll back failed", Toast.LENGTH_SHORT).show()
                onDone()
            }
        }
    }
}
