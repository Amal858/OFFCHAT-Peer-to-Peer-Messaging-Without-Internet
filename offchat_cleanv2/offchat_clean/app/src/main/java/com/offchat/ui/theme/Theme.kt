package com.offchat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark     = Color(0xFF0D1117)
val Surface    = Color(0xFF161B22)
val Surface2   = Color(0xFF1C2128)
val Border     = Color(0xFF21262D)
val Accent     = Color(0xFF00FF41)
val AccentDim  = Color(0xFF00CC34)
val TextPrimary= Color(0xFFE6EDF3)
val TextMuted  = Color(0xFF8B949E)
val TeacherClr = Color(0xFFFFB347)
val ErrorClr   = Color(0xFFFF6B6B)
val MsgBg      = Color(0xFF003D1A)
val MsgBorder  = Color(0xFF00FF41)

private val DarkColors = darkColorScheme(
    primary = Accent, onPrimary = Color.Black,
    background = BgDark, onBackground = TextPrimary,
    surface = Surface, onSurface = TextPrimary,
    surfaceVariant = Surface2, onSurfaceVariant = TextMuted,
    outline = Border, error = ErrorClr
)

@Composable
fun OffChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
