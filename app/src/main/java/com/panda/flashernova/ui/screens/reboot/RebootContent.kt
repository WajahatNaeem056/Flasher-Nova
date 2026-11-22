package com.panda.flashernova.ui.screens.reboot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.panda.flashernova.R
import com.panda.flashernova.ui.components.AnimatedConfirmDialog
import com.panda.flashernova.ui.components.ListItem
import com.panda.flashernova.ui.components.ListItemIconColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.RebootContent(
    viewModel: RebootViewModel,
    @Suppress("UNUSED_PARAMETER") ignoredNavController: NavController
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ListItem(
            title = stringResource(R.string.reboot),
            subtitle = "Reboot to system",
            leadingIcon = Icons.Outlined.PowerSettingsNew,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            onClick = { viewModel.showConfirm("") }
        )
        ListItem(
            title = stringResource(R.string.reboot_recovery),
            subtitle = "Reboot to recovery mode",
            leadingIcon = Icons.Outlined.Restore,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            onClick = { viewModel.showConfirm("recovery") }
        )
        ListItem(
            title = stringResource(R.string.reboot_bootloader),
            subtitle = "Reboot to Bootloader (fastboot)",
            leadingIcon = Icons.Outlined.Settings,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            onClick = { viewModel.showConfirm("bootloader") }
        )
        ListItem(
            title = stringResource(R.string.reboot_download),
            subtitle = "Reboot to Download mode",
            leadingIcon = Icons.Outlined.Download,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            onClick = { viewModel.showConfirm("download") }
        )
        ListItem(
            title = stringResource(R.string.reboot_edl),
            subtitle = "Reboot to emergency download mode",
            leadingIcon = Icons.Outlined.BugReport,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer
            ),
            onClick = { viewModel.showConfirm("edl") }
        )
    }

    AnimatedConfirmDialog(
        visible = viewModel.showConfirmDialog,
        title = "Warning",
        message = viewModel.confirmMessage,
        confirmText = "Reboot",
        cancelText = stringResource(R.string.cancel),
        onConfirm = { viewModel.executeReboot() },
        onDismiss = { viewModel.hideConfirm() },
        destructive = true
    )
}
