package com.panda.flashernova.ui.screens.backups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.panda.flashernova.R
import com.panda.flashernova.ui.components.AnimatedConfirmDialog
import com.panda.flashernova.ui.components.DataCard
import com.panda.flashernova.ui.components.DataRow
import com.panda.flashernova.ui.components.FlashList
import com.panda.flashernova.ui.components.ListItem
import com.panda.flashernova.ui.components.ListItemIconColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUnitApi::class)
@Composable
fun ColumnScope.FullBackupContent(
    viewModel: BackupsViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val showStartConfirm = remember { mutableStateOf(false) }
    val summary = viewModel.fullBackupSummary

    LaunchedEffect(Unit) {
        if (viewModel.fullBackupSummary == null && viewModel.fullBackupOutput.isEmpty()) {
            viewModel.prepareFullBackup(context)
        }
    }

    if (viewModel.fullBackupOutput.isNotEmpty()) {
        FlashList("Full backup log", viewModel.fullBackupOutput) {
            AnimatedVisibility(!viewModel.isRefreshing && viewModel.fullBackupWasSuccessful != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Spacer(Modifier.height(6.dp))
                    DataCard("Backup result") {
                        val cardWidth = remember { mutableIntStateOf(0) }
                        DataRow("Succeeded", "${viewModel.fullBackupSuccessCount}", mutableMaxWidth = cardWidth)
                        DataRow("Skipped", "${viewModel.fullBackupSkipCount}", mutableMaxWidth = cardWidth)
                        DataRow("Failed", "${viewModel.fullBackupFailCount}", mutableMaxWidth = cardWidth)
                        DataRow("Backup directory", viewModel.fullBackupDirectory, mutableMaxWidth = cardWidth, clickable = true)
                    }
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        onClick = {
                            viewModel.clearFullBackupOutput()
                            viewModel.prepareFullBackup(context)
                        }
                    ) {
                        Text("Back to backup settings")
                    }
                }
            }
        }
        return
    }

    DataCard("Full partition backup") {
        val cardWidth = remember { mutableIntStateOf(0) }
        if (summary != null) {
            DataRow("Platform", summary.platform, mutableMaxWidth = cardWidth)
            DataRow("Source path", summary.sourceDir, mutableMaxWidth = cardWidth, clickable = true)
            DataRow("Total partitions", "${summary.totalPartitions}", mutableMaxWidth = cardWidth)
            DataRow("Skipped partitions", summary.skippedPartitions.ifEmpty { listOf("None") }.joinToString(" "), mutableMaxWidth = cardWidth)
            DataRow("To back up", "${summary.backupCount}", mutableMaxWidth = cardWidth)
            DataRow("Estimated size", formatFullBackupBytes(summary.estimatedBytes), mutableMaxWidth = cardWidth)
        } else if (viewModel.isRefreshing) {
            Text(
                "Detecting platform, root access and partition size…",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "No backup-capable partitions detected yet. Tap to detect again.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    DataCard("Backup directory") {
        OutlinedTextField(
            value = viewModel.fullBackupDirectory,
            onValueChange = { viewModel.updateFullBackupDirectory(it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Custom backup directory") },
            leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Folder, contentDescription = null) },
            supportingText = { Text("Default: /sdcard/FlasherNova/backups/<model>_FullBackup") }
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                enabled = !viewModel.isRefreshing,
                onClick = {
                    viewModel.resetFullBackupDirectory()
                    viewModel.prepareFullBackup(context)
                }
            ) {
                Text("Reset directory")
            }
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                enabled = !viewModel.isRefreshing,
                onClick = { viewModel.prepareFullBackup(context) }
            ) {
                Text("Detect again")
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    AnimatedVisibility(!viewModel.isRefreshing) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ListItem(
                title = "Start full backup",
                subtitle = summary?.let { "Needs about ${formatFullBackupBytes(it.estimatedBytes)} of free space; userdata / sdc skipped" }
                    ?: "Detect platform and partition size first",
                leadingIcon = Icons.Filled.Storage,
                leadingColors = ListItemIconColors(
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                trailingIcon = Icons.Filled.PlayArrow,
                enabled = summary != null,
                onClick = { showStartConfirm.value = true }
            )
            ListItem(
                title = "Refresh partition info",
                subtitle = "Recount partitions and estimated space usage",
                leadingIcon = Icons.Filled.Refresh,
                leadingColors = ListItemIconColors(
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                onClick = { viewModel.prepareFullBackup(context) }
            )
        }
    }

    AnimatedConfirmDialog(
        visible = showStartConfirm.value,
        title = "Start full backup",
        message = "All partitions except userdata / sdc will be backed up, estimated size ${formatFullBackupBytes(summary?.estimatedBytes ?: 0L)}.",
        detail = viewModel.fullBackupDirectory,
        confirmText = "Start backup",
        cancelText = androidx.compose.ui.res.stringResource(R.string.cancel),
        onConfirm = {
            showStartConfirm.value = false
            viewModel.startFullBackup(context)
        },
        onDismiss = { showStartConfirm.value = false }
    )
}

private fun formatFullBackupBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (index == 0) {
        "$bytes ${units[index]}"
    } else {
        String.format(Locale.US, "%.2f %s", value, units[index])
    }
}
