package com.panda.flashernova.ui.screens.backups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.panda.flashernova.R
import com.panda.flashernova.common.PartitionUtil
import com.panda.flashernova.ui.components.AnimatedConfirmDialog
import com.panda.flashernova.ui.components.DataCard
import com.panda.flashernova.ui.components.DataRow
import com.panda.flashernova.ui.components.DataSet
import com.panda.flashernova.ui.components.ListItem
import com.panda.flashernova.ui.components.ListItemIconColors
import com.panda.flashernova.ui.components.ViewButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.BackupsContent(
    viewModel: BackupsViewModel,
    navController: NavController
) {
    val context = LocalContext.current

    val monoStyle = MaterialTheme.typography.titleSmall.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium
    )

    val showDeleteConfirm = remember { mutableStateOf(false) }

    if (viewModel.currentBackup != null && viewModel.backups.containsKey(viewModel.currentBackup)) {
        DataCard(viewModel.currentBackup!!) {
            val cardWidth = remember { mutableIntStateOf(0) }
            val backupId = viewModel.currentBackup!!
            val currentBackup = viewModel.backups[backupId]
            if (currentBackup == null) return@DataCard
            DataRow(stringResource(R.string.backup_type), currentBackup.type, mutableMaxWidth = cardWidth)
            DataRow(stringResource(R.string.kernel_version), currentBackup.kernelVersion, mutableMaxWidth = cardWidth, clickable = true)
            if (currentBackup.type == "raw") {
                currentBackup.bootSha1?.takeIf { it.length >= 8 }?.let { sha1 ->
                    DataRow(
                        label = stringResource(R.string.boot_sha1),
                        value = sha1.substring(0, 8),
                        valueStyle = monoStyle,
                        mutableMaxWidth = cardWidth
                    )
                }
                if (currentBackup.hashes != null) {
                    val hashWidth = remember { mutableIntStateOf(0) }
                    DataSet(stringResource(R.string.hashes)) {
                        for (partitionName in PartitionUtil.PartitionNames) {
                            val hash = currentBackup.hashes[partitionName]
                            if (hash != null) {
                                DataRow(
                                    label = partitionName,
                                    value = hash.takeIf { it.isNotEmpty() }?.substring(0, 8) ?: "Hash not found!",
                                    valueStyle = monoStyle,
                                    mutableMaxWidth = hashWidth
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AnimatedVisibility(!viewModel.isRefreshing) {
            ListItem(
                title = stringResource(R.string.delete),
                subtitle = "Permanently delete this backup",
                leadingIcon = Icons.Outlined.Delete,
                leadingColors = ListItemIconColors(
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
                ),
                onClick = { showDeleteConfirm.value = true }
            )
        }
        AnimatedConfirmDialog(
            visible = showDeleteConfirm.value,
            title = "Warning",
            message = "Are you sure you want to delete this backup?",
            detail = viewModel.currentBackup,
            confirmText = stringResource(R.string.delete),
            cancelText = stringResource(R.string.cancel),
            onConfirm = {
                showDeleteConfirm.value = false
                viewModel.delete(context) { navController.popBackStack() }
            },
            onDismiss = { showDeleteConfirm.value = false },
            destructive = true
        )
    } else {
        DataCard(stringResource(R.string.backups))
        AnimatedVisibility(viewModel.needsMigration) {
            Spacer(Modifier.height(10.dp))
            ListItem(
                title = stringResource(R.string.migrate),
                subtitle = "Migrate backup data from older versions",
                leadingIcon = Icons.Outlined.Sync,
                leadingColors = ListItemIconColors(
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                onClick = { viewModel.migrate(context) }
            )
        }
        if (viewModel.backups.isNotEmpty()) {
            for (id in viewModel.backups.keys.sortedByDescending { it }) {
                val currentBackup = viewModel.backups[id]!!
                Spacer(Modifier.height(14.dp))
                DataCard(
                    title = id,
                    button = {
                        AnimatedVisibility(!viewModel.isRefreshing) {
                            ViewButton(onClick = {
                                navController.navigate("backups/$id")
                            })
                        }
                    }
                ) {
                    val cardWidth = remember { mutableIntStateOf(0) }
                    if (currentBackup.type == "raw") {
                        currentBackup.bootSha1?.takeIf { it.length >= 8 }?.let { sha1 ->
                            DataRow(
                                label = stringResource(R.string.boot_sha1),
                                value = sha1.substring(0, 8),
                                valueStyle = monoStyle,
                                mutableMaxWidth = cardWidth
                            )
                        }
                    }
                    DataRow(stringResource(R.string.kernel_version), currentBackup.kernelVersion, mutableMaxWidth = cardWidth, clickable = true)
                }
            }
        } else {
            Spacer(Modifier.height(40.dp))
            Text(
                stringResource(R.string.no_backups_found),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
