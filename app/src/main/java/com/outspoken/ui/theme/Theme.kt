package com.outspoken.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle

@Composable
fun OutspokenTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        CompositionLocalProvider(
            LocalContentColor provides Ink,
            LocalTextStyle provides TextStyle(fontFamily = Urbanist, color = Ink),
            content = content,
        )
    }
}
