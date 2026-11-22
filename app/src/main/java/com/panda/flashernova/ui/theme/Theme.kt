package com.panda.flashernova.ui.theme

import androidx.compose.runtime.Composable

@Composable
fun FlasherNovaTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MiuixThemeProvider(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
