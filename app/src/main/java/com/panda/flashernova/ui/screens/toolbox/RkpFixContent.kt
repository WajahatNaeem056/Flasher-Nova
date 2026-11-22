package com.panda.flashernova.ui.screens.toolbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import com.panda.flashernova.ui.components.AnimatedConfirmDialog
import com.panda.flashernova.ui.components.DialogButton
import com.panda.flashernova.ui.theme.softShadow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.RkpFixContent(navController: NavController) {
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(0) }
    var showSelect by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf<String?>(null) }
    val output = remember { mutableStateListOf<String>() }

    LaunchedEffect(page) {
        if (page != 1) return@LaunchedEffect
        output.clear()
        try {
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val dir = "/sdcard/FlasherNova/persist_backup"
            Shell.cmd("mkdir -p $dir").exec()
            output.add("=== RKP Fix ===")
            output.add("")

            if (mode == "elite_gen5") {
                output.add("→ [1/3] Copying tool and setting permissions...")
                val toolFile = java.io.File(context.filesDir, "KmInstallKeybox")
                try {
                    context.assets.open("KmInstallKeybox").use { input ->
                        toolFile.outputStream().use { out -> input.copyTo(out) }
                    }
                    output.add("  ✓ Tool extracted to ${toolFile.absolutePath}")
                } catch (e: Exception) {
                    output.add("  ! Failed to extract tool: ${e.message}")
                }
                val r0a = Shell.cmd("cp ${toolFile.absolutePath} /data/local/tmp/ 2>&1").exec()
                r0a.out.forEach { if (it.isNotBlank()) output.add("  ${it.trim()}") }
                r0a.err.forEach { if (it.isNotBlank()) output.add("  ! ${it.trim()}") }
                val r0b = Shell.cmd("chmod 755 /data/local/tmp/KmInstallKeybox 2>&1").exec()
                r0b.out.forEach { if (it.isNotBlank()) output.add("  ${it.trim()}") }
                r0b.err.forEach { if (it.isNotBlank()) output.add("  ! ${it.trim()}") }
                output.add(if (r0a.isSuccess && r0b.isSuccess) "  ✓ Tool ready" else "  ! Tool preparation may have failed")
                output.add("")

                output.add("→ [2/3] Backing up persist partition...")
                val r1 = Shell.cmd("dd if=/dev/block/by-name/persist of=$dir/persist_orig.img 2>&1").exec()
                r1.out.forEach { output.add("  ${it.trim()}") }
                r1.err.forEach { if (it.isNotBlank()) output.add("  ! ${it.trim()}") }
                output.add(if (r1.isSuccess) "  ✓ Backup complete" else "  ! Backup may have failed")
                output.add("")

                output.add("→ [3/3] Running RKP fix...")
                val r2 = Shell.cmd("LD_LIBRARY_PATH=/vendor/lib64/hw /data/local/tmp/KmInstallKeybox Keybox_file Device_ID true true 2>&1").exec()
                r2.out.forEach { output.add("  ${it.trim()}") }
                r2.err.forEach { if (it.isNotBlank()) output.add("  ! ${it.trim()}") }
                output.add("")
                val combined = (r2.out + r2.err).joinToString(" ")
                when {
                    combined.contains("InstallKeybox is done", true) -> output.add("✓ RKP fix succeeded — InstallKeybox is done")
                    combined.contains("Error", true) || combined.contains("Failed", true) -> output.add("✗ RKP fix failed")
                    else -> output.add("! Finished, please check the output above")
                }
            } else {
                output.add("→ [1/2] Backing up persist partition...")
                val r1 = Shell.cmd("dd if=/dev/block/by-name/persist of=$dir/persist_orig.img 2>&1").exec()
                r1.out.forEach { output.add("  ${it.trim()}") }
                r1.err.forEach { if (it.isNotBlank()) output.add("  ! ${it.trim()}") }
                output.add(if (r1.isSuccess) "  ✓ Backup complete" else "  ! Backup may have failed")
                output.add("")

                output.add("→ [2/2] Running RKP fix...")
                val r2 = Shell.cmd("LD_LIBRARY_PATH=/vendor/lib64/hw KmInstallKeybox Keybox_file Device_ID true rkp 2>&1").exec()
                r2.out.forEach { output.add("  ${it.trim()}") }
                r2.err.forEach { if (it.isNotBlank()) output.add("  ! ${it.trim()}") }
                output.add("")
                val combined = (r2.out + r2.err).joinToString(" ")
                when {
                    combined.contains("InstallKeybox is done", true) -> output.add("✓ RKP fix succeeded — InstallKeybox is done")
                    combined.contains("Error", true) || combined.contains("Failed", true) -> output.add("✗ RKP fix failed")
                    else -> output.add("! Finished, please check the output above")
                }
            }

            output.add("")
            output.add("--- Log: $dir/rkp_fix_$now.log ---")
            try {
                val tmpLog = java.io.File(context.filesDir, "rkp_tmp.log")
                tmpLog.writeText(output.joinToString("\n"))
                Shell.cmd("cp $tmpLog $dir/rkp_fix_$now.log").exec()
                tmpLog.delete()
            } catch (_: Exception) {}
            page = 2
        } catch (e: Exception) {
            output.add("✗ Error: ${e.message}")
            page = 2
        }
    }

    if (page == 0) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            Icon(Icons.Outlined.Security, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Text("RKP Fix Tool", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "This feature is experimental and for trying out only.\nQualcomm platforms only. Do not use on MediaTek!\nIt may cause device problems.\nIf this feature causes problems, please do not file an issue.",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().softShadow(cornerRadius = 16.dp, alpha = 0.06f, offsetY = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "This feature helps with TEE freezing and unavailable RKP on Snapdragon devices caused by unlocking the bootloader.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Qualcomm platforms only. Do not use on MediaTek.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showSelect = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) { Text("Start", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (page >= 1) {
        Column {
            Spacer(Modifier.height(8.dp))
            Text("RKP Fix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    for (line in output) {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 16.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (page == 1) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Running...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (page == 2) {
                Button(
                    onClick = { navController.navigate("main") { popUpTo("main") { inclusive = true } } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Done", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showSelect) {
        DeviceSelectDialog(
            onSelectEliteGen5 = {
                showSelect = false
                mode = "elite_gen5"
                showConfirm = true
            },
            onSelectLegacy = {
                showSelect = false
                mode = "legacy"
                showConfirm = true
            },
            onDismiss = { showSelect = false }
        )
    }

    if (showConfirm) {
        AnimatedConfirmDialog(
            visible = true,
            title = "⚠ Warning",
            message = "This will back up the persist partition and run the RKP fix.\n\nQualcomm Snapdragon platforms only!\nNever use on MediaTek devices!",
            confirmText = "Continue",
            cancelText = "Cancel",
            onConfirm = { showConfirm = false; page = 1 },
            onDismiss = { showConfirm = false; mode = null },
            destructive = true
        )
    }
}

@Composable
private fun DeviceSelectDialog(
    onSelectEliteGen5: () -> Unit,
    onSelectLegacy: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp, 22.dp, 24.dp, 18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(Modifier.padding(8.dp))
                        Text(
                            "Select device type",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.padding(10.dp))
                    Text(
                        "Choose the matching fix for your device's processor",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.padding(14.dp))

                    SelectOptionRow(
                        icon = Icons.Outlined.Memory,
                        title = "For Qcom 8 Elite Gen 5 [Beta]",
                        subtitle = "Next-gen Snapdragon 8 Elite platform",
                        onClick = onSelectEliteGen5
                    )
                    Spacer(Modifier.height(10.dp))
                    SelectOptionRow(
                        icon = Icons.Outlined.Security,
                        title = "Processors up to 8 Elite",
                        subtitle = "Snapdragon 8 Elite and earlier platforms",
                        onClick = onSelectLegacy
                    )

                    Spacer(Modifier.padding(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        DialogButton("Cancel", onClick = onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(cornerRadius = 18.dp, alpha = 0.06f, offsetY = 2.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
