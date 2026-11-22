package com.panda.flashernova.ui.screens.toolbox

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import com.panda.flashernova.ui.components.AnimatedConfirmDialog
import com.panda.flashernova.ui.theme.softShadow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.DiagPortContent(navController: NavController) {
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(0) }
    var showConfirm by remember { mutableStateOf(false) }
    val output = remember { mutableStateListOf<String>() }

    LaunchedEffect(page) {
        if (page != 1) return@LaunchedEffect
        output.clear()
        try {
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val dir = "/sdcard/FlasherNova/diag_logs"
            Shell.cmd("mkdir -p $dir").exec()
            output.add("=== Enable Qualcomm Diag Port ===")
            output.add("")

            output.add("→ [1/2] Preparing...")
            Thread.sleep(500)
            output.add("  Done")
            output.add("")

            output.add("→ [2/2] Enabling Diag port...")
            val r = Shell.cmd("su -c \"setprop sys.usb.config diag,adb\" 2>&1").exec()
            r.out.forEach { if (it.isNotBlank()) output.add("  ${it.trim()}") }
            r.err.forEach { if (it.isNotBlank()) output.add("  ! ${it.trim()}") }
            output.add(if (r.isSuccess) "  Done" else "  ! Execution failed")
            output.add("")

            output.add("--- Log: $dir/diag_port_$now.log ---")
            try {
                val tmpLog = java.io.File(context.filesDir, "diag_tmp.log")
                tmpLog.writeText(output.joinToString("\n"))
                Shell.cmd("cp $tmpLog $dir/diag_port_$now.log").exec()
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
            Icon(Icons.Filled.Usb, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Text("Enable Qualcomm Diag Port", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "This feature is for Snapdragon devices only. Do not use on Dimensity devices!\nMake sure the phone is connected to a computer before enabling the port.\nIf this feature causes problems, please do not file an issue.",
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
                        "This feature uses the setprop command to enable the Diag debug port on Qualcomm devices,\nso tools such as QPST/QXDM can connect to the device.",
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
                onClick = { showConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) { Text("Start", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (page >= 1) {
        Column {
            Spacer(Modifier.height(8.dp))
            Text("Enable Qualcomm Diag Port", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Done", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showConfirm) {
        AnimatedConfirmDialog(
            visible = true,
            title = "Warning",
            message = "This feature is for Snapdragon devices only. Do not use on Dimensity devices!\nMake sure the phone is connected to a computer before enabling the port.\n\nContinue?",
            confirmText = "OK",
            cancelText = "Cancel",
            onConfirm = { showConfirm = false; page = 1 },
            onDismiss = { showConfirm = false },
            destructive = true
        )
    }
}
