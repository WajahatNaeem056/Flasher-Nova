package com.panda.flashernova.ui.screens.toolbox

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.panda.flashernova.toolbox.rkp.RkpCommands
import com.panda.flashernova.toolbox.rkp.RkpLauncher
import com.panda.flashernova.toolbox.rkp.RkpLevel
import com.panda.flashernova.toolbox.rkp.RkpLogger
import com.panda.flashernova.toolbox.rkp.RkpPrefs
import com.panda.flashernova.toolbox.rkp.RkpShell
import java.io.File

private data class RkpLogEntry(val level: Int, val line: String)

/**
 * RKPConfig -- a page ported into the toolbox from the standalone "True RKP" app, with identical functionality:
 * Read/apply config, renew provisioning, view status, generate CSR, verify certificate, engineer mode, clear data,
 * Clear/copy logs, set su path, persist logs to disk.
 *
 * Behavior specs were checked one by one against the original tool on a real device; see the comments of each class in the rkp package.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ColumnScope.RkpConfigContent(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { RkpPrefs(context) }

    // ---- Config state ----
    var enable by remember { mutableStateOf(prefs.enable()) }
    var host by remember { mutableStateOf(prefs.host()) }
    var strongbox by remember { mutableStateOf(prefs.strongbox()) }
    var tee by remember { mutableStateOf(prefs.tee()) }
    var timeout by remember { mutableStateOf(prefs.timeout().coerceIn(0, 60)) }

    // ---- Device status ----
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var hasStrongbox by remember { mutableStateOf(false) }
    var helperDesc by remember { mutableStateOf("") }

    // ---- Runtime status ----
    val logLines = remember { mutableStateListOf<RkpLogEntry>() }
    var busy by remember { mutableStateOf(false) }
    var showSuDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var suPath by remember { mutableStateOf(prefs.su()) }

    val logScroll = rememberScrollState()
    val logFile = remember { File(context.filesDir, "rkp.log") }

    val logger = remember {
        object : RkpLogger {
            override fun log(level: Int, line: String) {
                scope.launch(Dispatchers.Main) {
                    logLines.add(RkpLogEntry(level, line))
                    if (logLines.size > 2000) logLines.removeRange(0, 500)
                }
                runCatching { logFile.appendText(line + "\n") }
            }
        }
    }

    // ---- Startup self-check ----
    LaunchedEffect(Unit) {
        helperDesc = RkpLauncher.helperDescription(context)
        hasStrongbox = runCatching {
            context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
        }.getOrDefault(false)

        withContext(Dispatchers.IO) {
            val out = RkpShell.root(suPath, listOf("id"), 8000)
            val ok = out.stdoutText().contains("uid=0")
            withContext(Dispatchers.Main) { rootOk = ok }
            logger.log(
                if (ok) RkpLevel.OK else RkpLevel.WARN,
                if (ok) "Root available: ${out.stdoutText().trim()}" else "Root not granted: ${out.stderrText().trim()}"
            )
        }
        logger.log("RKPConfig is ready")
        logger.log(RkpLevel.DIM, "Namespace helper: $helperDesc")
        logger.log(RkpLevel.DIM, "Log file: ${logFile.absolutePath}")
    }

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) logScroll.animateScrollTo(logScroll.maxValue)
    }

    // ---- Generic execution wrapper ----
    fun runOp(
        name: String,
        cmds: List<String>,
        timeoutMs: Long,
        onResult: (RkpShell.Out) -> Unit = {},
    ) {
        if (busy) return
        busy = true
        scope.launch(Dispatchers.IO) {
            try {
                val out = RkpShell.root(
                    suPath, cmds, timeoutMs,
                    { line -> logger.log(RkpLevel.DIM, line) },
                    { line -> logger.log(RkpLevel.ERR, line) },
                )
                withContext(Dispatchers.Main) { onResult(out) }
                when {
                    out.suDenied -> logger.log(RkpLevel.ERR, "$name · unable to get root (you can set the su path below)")
                    out.timeout -> logger.log(RkpLevel.WARN, "$name · timed out")
                    else -> logger.log(RkpLevel.OK, "$name · done")
                }
            } catch (e: Exception) {
                logger.log(RkpLevel.ERR, "$name · failed: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) { busy = false }
            }
        }
    }

    fun doLoad() {
        runOp("Read config", RkpCommands.loadAll(), 15_000) { out ->
            val p = RkpCommands.parse(out)
            enable = p.enable
            host = p.host
            strongbox = p.strongbox
            tee = p.tee
            timeout = p.timeoutSec.coerceIn(0, 60)
            prefs.save(p.enable, p.host, p.strongbox, p.tee, p.timeoutSec)
            logger.log(RkpLevel.DIM, "getprop result $p")
        }
    }

    fun doApply() {
        prefs.save(enable, host, strongbox, tee, timeout)
        runOp(
            "Apply config",
            RkpCommands.applyAll(enable, host, strongbox, tee, timeout),
            20_000,
        ) { out ->
            if (!out.suDenied && out.stderr.isEmpty()) {
                logger.log("Config written; some parameters may need a reboot to take effect")
            }
        }
    }

    fun doRenew() {
        val apk = RkpLauncher.materialize(context)
        if (apk == null) {
            logger.log(RkpLevel.ERR, "rkpdapp.apk not found: not in assets, and com.android.rkpdapp is not installed on the device")
            return
        }
        logger.log(RkpLevel.DIM, "rkpdapp source: ${apk.absolutePath}")
        val cmd = RkpLauncher.fullCommand(context, null)
        logger.log(RkpLevel.DIM, "Command: $cmd")
        runOp("Renew provisioning", listOf(cmd), 180_000)
    }

    fun doDump() {
        val cmd = RkpLauncher.fullCommand(context, "dump")
        runOp("View status", listOf(RkpCommands.DUMP, cmd), 120_000)
    }

    fun doClear() {
        runOp("Clear data", RkpCommands.clearPackages(), 60_000)
    }

    fun copyLog() {
        val text = logLines.joinToString("\n") { it.line }
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("RKPConfig", text))
        logger.log(RkpLevel.OK, "Log copied to clipboard")
    }

    // ================================================================ UI

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(4.dp))

        // ---------- State ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "RKPConfig",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Configure Android Remote Key Provisioning (RKP), requires root",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                StatusLine(
                    "ROOT",
                    when (rootOk) {
                        true -> "Ready"
                        false -> "Unavailable"
                        null -> "Detecting…"
                    },
                    when (rootOk) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                StatusLine(
                    "StrongBox",
                    if (hasStrongbox) "Supported" else "Not supported",
                    if (hasStrongbox) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusLine("Namespace helper", helperDesc.ifEmpty { "-" }, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ---------- Config ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Remote Key Provisioning (RKP)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                SwitchRow(
                    title = "Enable RKP",
                    subtitle = "remote_provisioning.enable_rkpd",
                    checked = enable,
                    onChange = { enable = it }
                )

                Text("Server address", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("remoteprovisioning.googleapis.com") },
                    supportingText = { Text("remote_provisioning.hostname") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = host == "remoteprovisioning.googleapis.com",
                        onClick = { host = "remoteprovisioning.googleapis.com" },
                        label = { Text("Google") }
                    )
                    FilterChip(
                        selected = host == "remoteprovisioning.grapheneos.org",
                        onClick = { host = "remoteprovisioning.grapheneos.org" },
                        label = { Text("GrapheneOS") }
                    )
                }

                if (hasStrongbox) {
                    SwitchRow(
                        title = "Use StrongBox only",
                        subtitle = "strongbox.rkp_only",
                        checked = strongbox,
                        onChange = { strongbox = it }
                    )
                }

                SwitchRow(
                    title = "Use TEE only",
                    subtitle = "tee.rkp_only",
                    checked = tee,
                    onChange = { tee = it }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Connection timeout", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "connect_timeout_millis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "$timeout s",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = timeout.toFloat(),
                    onValueChange = { timeout = it.toInt().coerceIn(0, 60) },
                    valueRange = 0f..60f,
                    steps = 59
                )
            }
        }

        // ---------- Actions ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RkpActionButton(
                        "Read config",
                        Modifier.weight(1f),
                        filled = true,
                        enabled = !busy
                    ) { doLoad() }
                    RkpActionButton(
                        "Apply config",
                        Modifier.weight(1f),
                        filled = true,
                        enabled = !busy,
                        onLongClick = { showSuDialog = true }
                    ) { doApply() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RkpActionButton(
                        "Renew provisioning",
                        Modifier.weight(1f),
                        filled = true,
                        enabled = !busy
                    ) { doRenew() }
                    RkpActionButton(
                        "View status",
                        Modifier.weight(1f),
                        enabled = !busy
                    ) { doDump() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RkpActionButton(
                        "Generate CSR",
                        Modifier.weight(1f),
                        enabled = !busy
                    ) { runOp("Generate CSR", listOf(RkpCommands.CSR), 30_000) }
                    RkpActionButton(
                        "Verify certificate",
                        Modifier.weight(1f),
                        enabled = !busy
                    ) { runOp("Verify certificate", listOf(RkpCommands.CERTIFY), 30_000) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RkpActionButton(
                        "Engineer mode",
                        Modifier.weight(1f),
                        enabled = !busy
                    ) { runOp("Engineer mode", listOf(RkpCommands.ENGINEER), 15_000) }
                    RkpActionButton(
                        "Clear data",
                        Modifier.weight(1f),
                        filled = true,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        enabled = !busy
                    ) { showClearConfirm = true }
                }

                Text(
                    "Long-press \"Apply config\" to set the su path",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------- Logs ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Run log",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { copyLog() }) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "Copy log",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        logLines.clear()
                        logger.log("Log cleared")
                    }) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = "Clear log",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(10.dp)
                        .verticalScroll(logScroll)
                ) {
                    Column {
                        if (logLines.isEmpty()) {
                            Text(
                                "(No logs yet)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            logLines.takeLast(800).forEach { entry ->
                                Text(
                                    text = entry.line,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = levelColor(entry.level)
                                )
                            }
                        }
                    }
                }

                Text(
                    "This tool actually triggers key provisioning and modifies system properties",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // ---------- Dialogs ----------
    if (showSuDialog) {
        var input by remember { mutableStateOf(suPath) }
        AlertDialog(
            onDismissRequest = { showSuDialog = false },
            title = { Text("SU PATH") },
            text = {
                Column {
                    Text("Full path of the su executable")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        placeholder = { Text("/system/bin/su") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = input.trim()
                    if (v.isNotEmpty()) {
                        suPath = v
                        prefs.saveSu(v)
                        logger.log(RkpLevel.OK, "su path set to: $v")
                    }
                    showSuDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showSuDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear data") },
            text = { Text("This will clear the app data of rkpdapp and KeyAttestation. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    doClear()
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ================================================================ Widgets

@Composable
private fun StatusLine(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RkpActionButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val bg = if (filled) containerColor else MaterialTheme.colorScheme.surface
    val fg = when {
        !filled -> MaterialTheme.colorScheme.onSurface
        else -> contentColor
    }.copy(alpha = if (enabled) 1f else 0.4f)

    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg.copy(alpha = if (enabled) 1f else 0.5f), RoundedCornerShape(14.dp))
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        onClick = onClick,
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

@Composable
private fun levelColor(level: Int): Color = when (level) {
    RkpLevel.OK -> MaterialTheme.colorScheme.primary
    RkpLevel.ERR -> MaterialTheme.colorScheme.error
    RkpLevel.WARN -> MaterialTheme.colorScheme.tertiary
    RkpLevel.DIM -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}
