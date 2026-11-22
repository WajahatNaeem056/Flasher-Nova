package com.panda.flashernova.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panda.flashernova.ui.theme.LocalGlassTokens
import top.yukonga.miuix.kmp.blur.Backdrop

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun GlassNavigationBar(
    items: List<NavItem>,
    currentRoute: String?,
    onItemClick: (NavItem) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier
) {
    val routeSelectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    var selectedIndex by remember { mutableIntStateOf(routeSelectedIndex) }
    LaunchedEffect(routeSelectedIndex) {
        selectedIndex = routeSelectedIndex
    }
    val navPadding = WindowInsets.navigationBars.asPaddingValues()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 10.dp + navPadding.calculateBottomPadding()),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    NavigationBarTab(
                        selected = index == selectedIndex,
                        item = item,
                        onClick = {
                            selectedIndex = index
                            onItemClick(item)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavigationBarTab(
    selected: Boolean,
    item: NavItem,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) LocalGlassTokens.current.selectedContainer else Color.Transparent,
        label = "navTabContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "navTabContent"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .animateContentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = if (selected) 20.dp else 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = content,
            modifier = Modifier.size(24.dp)
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.label,
                color = content,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
