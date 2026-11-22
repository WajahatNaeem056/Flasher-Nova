package com.panda.flashernova.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.panda.flashernova.R

private val rebootOptions = listOf(
    "" to R.string.reboot,
    "recovery" to R.string.reboot_recovery,
    "bootloader" to R.string.reboot_bootloader,
    "download" to R.string.reboot_download,
    "edl" to R.string.reboot_edl,
)

private val MenuWidth = 216.dp
private val ButtonSize = 48.dp

/**
 * Power button + reboot menu.
 *
 * While the menu is open the button keeps a soft circular highlight, so it is obvious which
 * button the menu came from. The menu drops from the button with its right edge lined up to the
 * button's right edge (KernelSU-style), instead of sticking out past the screen edge.
 */
@Composable
fun RebootMenuButton(
    onSelect: (mode: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val buttonBg by animateColorAsState(
        targetValue = if (expanded) cs.surfaceContainerHigh else Color.Transparent,
        label = "rebootButtonBg"
    )

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            colors = IconButtonDefaults.iconButtonColors(containerColor = buttonBg),
        ) {
            Icon(
                imageVector = Icons.Outlined.PowerSettingsNew,
                contentDescription = "Reboot",
                tint = Color(0xFF64B5F6)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(MenuWidth),
            offset = DpOffset(x = ButtonSize - MenuWidth, y = 4.dp),
            shape = RoundedCornerShape(24.dp),
            containerColor = cs.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            rebootOptions.forEach { (mode, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurface,
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    onClick = {
                        expanded = false
                        onSelect(mode)
                    },
                )
            }
        }
    }
}
