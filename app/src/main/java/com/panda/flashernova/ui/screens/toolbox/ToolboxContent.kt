package com.panda.flashernova.ui.screens.toolbox

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.panda.flashernova.AdbTcpService
import com.panda.flashernova.ui.components.ListItem
import com.panda.flashernova.ui.components.ListItemIconColors
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSerializationApi::class)
@Composable
fun ColumnScope.ToolboxContent(
    navController: NavController
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(4.dp))

        ListItem(
            title = "Unpack",
            subtitle = "Payload-Dumper unpacking and unpack records",
            leadingIcon = Icons.Outlined.FolderOpen,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            onClick = { navController.navigate("toolbox/unpack") }
        )

        Spacer(Modifier.height(4.dp))

        ListItem(
            title = "Full partition backup",
            subtitle = "Back up all partitions; records appear on the backup page",
            leadingIcon = Icons.Outlined.Storage,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            onClick = { navController.navigate("toolbox/full_backup") }
        )

        Spacer(Modifier.height(4.dp))

        ListItem(
            title = "Fix RKP",
            subtitle = "Fix TEE/RKP issues on Snapdragon devices caused by unlocking the bootloader (experimental)",
            leadingIcon = Icons.Outlined.Build,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer
            ),
            onClick = { navController.navigate("toolbox/rkp_fix") }
        )

        ListItem(
            title = "RKPConfig",
            subtitle = "Configure remote key provisioning: read/apply properties, renew provisioning, view status",
            leadingIcon = Icons.Outlined.Security,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            onClick = { navController.navigate("toolbox/rkp_config") }
        )

        ListItem(
            title = "Enable Qualcomm Diag Port",
            subtitle = "Enable the Snapdragon Diag debug port (QPST/QXDM)",
            leadingIcon = Icons.Outlined.Usb,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer
            ),
            onClick = { navController.navigate("toolbox/diag_port") }
        )

        WirelessDebugCard()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WirelessDebugCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var portText by remember { mutableStateOf("") }
    var pendingPort by remember { mutableStateOf<Int?>(null) }

    val started = AdbTcpService.isRunning.value
    val startedPort = AdbTcpService.currentPort.value

    val portNumber = portText.toIntOrNull()
    val isValid = portNumber != null && portNumber in 1024..65535
    val showInvalid = portText.isNotEmpty() && !isValid

    LaunchedEffect(started) {
        if (!started) {
            portText = ""
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val detected = withContext(Dispatchers.IO) {
                try {
                    val prop = Shell.cmd("getprop service.adb.tcp.port").exec().out.firstOrNull()?.trim().orEmpty()
                    val value = prop.toIntOrNull()
                    if (value != null && value in 1024..65535) value else null
                } catch (e: Exception) {
                    null
                }
            }
            AdbTcpService.syncDetected(context, detected)
            delay(1000)
        }
    }

    fun startAdb(port: Int) {
        scope.launch(Dispatchers.IO) {
            // First check whether the adb tcp debug port is already enabled: 0-1023 counts as not enabled, a valid port counts as enabled.
            val currentProp = Shell.cmd("getprop service.adb.tcp.port").exec().out.firstOrNull()?.trim().orEmpty()
            val currentPort = currentProp.toIntOrNull()
            val alreadyOpenPort = if (currentPort != null && currentPort in 1024..65535) currentPort else null

            if (alreadyOpenPort != null) {
                // Already enabled: skip enabling the port, send the notification directly and gray it out.
                withContext(Dispatchers.Main) {
                    AdbTcpService.start(context, alreadyOpenPort)
                }
                return@launch
            }

            val result = Shell.cmd("setprop service.adb.tcp.port $port && stop adbd && start adbd").exec()
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    AdbTcpService.start(context, port)
                } else {
                    Toast.makeText(context, "Failed to enable: ${result.err.joinToString("\n")}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val port = pendingPort
        pendingPort = null
        if (port != null) {
            if (granted) {
                startAdb(port)
            } else {
                Toast.makeText(context, "Please enable notification permission", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun onSend() {
        val port = portNumber ?: return
        if (port !in 1024..65535) return
        if (hasNotificationPermission(context)) {
            startAdb(port)
        } else {
            pendingPort = port
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "Enable wireless debugging port",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Start the adbd service on a specified port",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { newValue -> portText = newValue.filter { it.isDigit() }.take(5) },
                    modifier = Modifier.weight(1f),
                    enabled = !started,
                    singleLine = true,
                    label = { Text("Port") },
                    placeholder = { Text("1024-65535") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { onSend() },
                    enabled = isValid && !started
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (isValid && !started) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                    )
                }
            }

            if (showInvalid && !started) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Invalid port, please enter a number between 1024 and 65535",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (started) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "adbd service started on ${startedPort}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { AdbTcpService.stop(context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Stop")
                }
            }
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun ColumnScope.UnpackHubContent(
    navController: NavController
) {
    val context = LocalContext.current
    val result = remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        result.value = it
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(4.dp))

        ListItem(
            title = "Unpack",
            subtitle = "Choose payload.bin and extract img partition files",
            leadingIcon = Icons.Outlined.FolderOpen,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            onClick = { launcher.launch("*/*") }
        )

        ListItem(
            title = "Unpack records",
            subtitle = "View unpacked img files",
            leadingIcon = Icons.Outlined.Inventory2,
            leadingColors = ListItemIconColors(
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            onClick = { navController.navigate("toolbox/unpack_records") }
        )
    }

    result.value?.let { uri ->
        val fileName = uri.lastPathSegment ?: ""
        if (fileName.endsWith(".bin", ignoreCase = true)) {
            navController.navigate("toolbox/payload?uri=${Uri.encode(uri.toString())}")
        } else {
            Toast.makeText(context, "Please select a .bin file", Toast.LENGTH_SHORT).show()
        }
        result.value = null
    }
}
