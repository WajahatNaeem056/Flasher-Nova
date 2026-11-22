package com.panda.flashernova.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.panda.flashernova.R
import com.panda.flashernova.ui.components.ListItem
import com.panda.flashernova.ui.components.ListItemIconColors
import com.panda.flashernova.ui.components.SlotCard
import kotlinx.serialization.ExperimentalSerializationApi

@ExperimentalMaterial3Api
@ExperimentalSerializationApi
@Composable
fun FlashHomeContent(
    viewModel: MainViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val slotSfxA = if (viewModel.isAb) "_a" else ""

    SlotCard(
        title = stringResource(if (viewModel.isAb) R.string.slot_a else R.string.slot),
        viewModel = viewModel.slotA,
        navController = navController,
        isSlotScreen = true
    )
    Spacer(Modifier.height(12.dp))
    AnimatedVisibility(!viewModel.slotA.isRefreshing.value) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ListItem(
                title = stringResource(R.string.flash),
                subtitle = "Choose an AK3 / image / KernelSU driver to flash",
                leadingIcon = Icons.Outlined.Build,
                leadingColors = ListItemIconColors(
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                onClick = { navController.navigate("slot$slotSfxA/flash") }
            )
            ListItem(
                title = stringResource(R.string.backup),
                subtitle = "Back up the current partition locally",
                leadingIcon = Icons.Outlined.Backup,
                leadingColors = ListItemIconColors(
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                onClick = {
                    viewModel.slotA.clearFlash(context)
                    navController.navigate("slot$slotSfxA/backup")
                }
            )
            ListItem(
                title = stringResource(R.string.restore),
                subtitle = "Restore partitions from history",
                leadingIcon = Icons.Outlined.Restore,
                leadingColors = ListItemIconColors(
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                onClick = { navController.navigate("slot$slotSfxA/backups") }
            )
            ListItem(
                title = stringResource(R.string.check_kernel_version),
                subtitle = "Extract the kernel version from the boot image",
                leadingIcon = Icons.Outlined.Info,
                leadingColors = ListItemIconColors(
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                onClick = { if (!viewModel.slotA.isRefreshing.value) viewModel.slotA.getKernel(context) }
            )
            if (viewModel.slotA.hasVendorDlkm) {
                VendorDlkmItems(viewModel, context)
            }
        }
    }

    if (viewModel.isAb) {
        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(20.dp))
        SlotCard(
            title = stringResource(R.string.slot_b),
            viewModel = viewModel.slotB!!,
            navController = navController,
            isSlotScreen = true
        )
        Spacer(Modifier.height(12.dp))
        AnimatedVisibility(!viewModel.slotB!!.isRefreshing.value) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ListItem(
                    title = stringResource(R.string.flash),
                    subtitle = "Flash to slot B",
                    leadingIcon = Icons.Outlined.Build,
                    leadingColors = ListItemIconColors(
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    onClick = { navController.navigate("slot_b/flash") }
                )
                ListItem(
                    title = stringResource(R.string.backup),
                    subtitle = "Back up slot B",
                    leadingIcon = Icons.Outlined.Backup,
                    leadingColors = ListItemIconColors(
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    onClick = {
                        viewModel.slotB!!.clearFlash(context)
                        navController.navigate("slot_b/backup")
                    }
                )
                ListItem(
                    title = stringResource(R.string.restore),
                    subtitle = "Restore slot B from history",
                    leadingIcon = Icons.Outlined.Restore,
                    leadingColors = ListItemIconColors(
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    onClick = { navController.navigate("slot_b/backups") }
                )
                ListItem(
                    title = stringResource(R.string.check_kernel_version),
                    subtitle = "Check the slot B kernel version",
                    leadingIcon = Icons.Outlined.Info,
                    leadingColors = ListItemIconColors(
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        content = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    onClick = {
                        if (!viewModel.slotB!!.isRefreshing.value) viewModel.slotB!!.getKernel(context)
                    }
                )
            }
        }
    }

    Spacer(Modifier.height(28.dp))
    ListItem(
        title = stringResource(R.string.operation_history),
        subtitle = "View all flash / backup / reboot operations",
        leadingIcon = Icons.Outlined.History,
        leadingColors = ListItemIconColors(
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        onClick = { navController.navigate("history") }
    )
}

@Composable
private fun VendorDlkmItems(viewModel: MainViewModel, context: android.content.Context) {
    if (viewModel.slotA.isVendorDlkmMounted) {
        ActionListItem(
            icon = Icons.Outlined.Storage,
            title = stringResource(R.string.unmount_vendor_dlkm),
            onClick = { viewModel.slotA.unmountVendorDlkm(context) }
        )
    } else if (viewModel.slotA.isVendorDlkmMapped) {
        ActionListItem(
            icon = Icons.Outlined.Storage,
            title = stringResource(R.string.mount_vendor_dlkm),
            onClick = { viewModel.slotA.mountVendorDlkm(context) }
        )
        ActionListItem(
            icon = Icons.Outlined.Storage,
            title = stringResource(R.string.unmap_vendor_dlkm),
            onClick = { viewModel.slotA.unmapVendorDlkm(context) }
        )
    } else {
        ActionListItem(
            icon = Icons.Outlined.Storage,
            title = stringResource(R.string.map_vendor_dlkm),
            onClick = { viewModel.slotA.mapVendorDlkm(context) }
        )
    }
}

@Composable
private fun ActionListItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    ListItem(
        title = title,
        leadingIcon = icon,
        leadingColors = ListItemIconColors(
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        onClick = onClick
    )
}
