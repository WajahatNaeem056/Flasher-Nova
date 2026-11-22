package com.panda.flashernova.ui.screens.slot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.panda.flashernova.R
import com.panda.flashernova.ui.components.ListItem
import com.panda.flashernova.ui.components.ListItemIconColors
import com.panda.flashernova.ui.components.SlotCard

@ExperimentalAnimationApi
@ExperimentalMaterial3Api
@ExperimentalUnitApi
@Composable
fun ColumnScope.SlotContent(
    viewModel: SlotViewModel,
    slotSuffix: String,
    navController: NavController
) {
    val context = LocalContext.current
    SlotCard(
        title = stringResource(
            when (slotSuffix) {
                "_a" -> R.string.slot_a
                "_b" -> R.string.slot_b
                else -> R.string.slot
            }
        ),
        viewModel = viewModel,
        navController = navController,
        isSlotScreen = true
    )
    Spacer(Modifier.height(14.dp))
    AnimatedVisibility(!viewModel.isRefreshing.value) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ListItem(
                title = stringResource(R.string.flash),
                subtitle = "Choose an AK3 / image / KernelSU driver",
                leadingIcon = Icons.Outlined.Build,
                leadingColors = ListItemIconColors(
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                onClick = { navController.navigate("slot$slotSuffix/flash") }
            )
            ListItem(
                title = stringResource(R.string.backup),
                subtitle = "Back up the current slot locally",
                leadingIcon = Icons.Outlined.Backup,
                leadingColors = ListItemIconColors(
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                onClick = {
                    viewModel.clearFlash(context)
                    navController.navigate("slot$slotSuffix/backup")
                }
            )
            ListItem(
                title = stringResource(R.string.restore),
                subtitle = "Restore from history",
                leadingIcon = Icons.Outlined.Restore,
                leadingColors = ListItemIconColors(
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                onClick = { navController.navigate("slot$slotSuffix/backups") }
            )
            ListItem(
                title = stringResource(R.string.check_kernel_version),
                subtitle = "Extract the kernel version from the boot image",
                leadingIcon = Icons.Outlined.Info,
                leadingColors = ListItemIconColors(
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                onClick = {
                    if (!viewModel.isRefreshing.value) viewModel.getKernel(context)
                }
            )
            if (viewModel.hasVendorDlkm) {
                VendorDlkmItems(viewModel, context, slotSuffix)
            }
        }
    }
}

@Composable
private fun VendorDlkmItems(
    viewModel: SlotViewModel,
    context: android.content.Context,
    slotSuffix: String
) {
    if (viewModel.isVendorDlkmMounted) {
        ListItem(
            title = stringResource(R.string.unmount_vendor_dlkm),
            leadingIcon = Icons.Outlined.Storage,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            onClick = { viewModel.unmountVendorDlkm(context) }
        )
    } else if (viewModel.isVendorDlkmMapped) {
        ListItem(
            title = stringResource(R.string.mount_vendor_dlkm),
            leadingIcon = Icons.Outlined.Storage,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            onClick = { viewModel.mountVendorDlkm(context) }
        )
        ListItem(
            title = stringResource(R.string.unmap_vendor_dlkm),
            leadingIcon = Icons.Outlined.Storage,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            onClick = { viewModel.unmapVendorDlkm(context) }
        )
    } else {
        ListItem(
            title = stringResource(R.string.map_vendor_dlkm),
            leadingIcon = Icons.Outlined.Storage,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            onClick = { viewModel.mapVendorDlkm(context) }
        )
    }
}
