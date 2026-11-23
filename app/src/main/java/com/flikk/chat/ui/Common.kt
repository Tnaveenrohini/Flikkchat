package com.flikk.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flikk.chat.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------- Flikkchat design tokens (light + dark) ----------
data class Palette(
    val dark: Boolean,
    val accent: Color, val accentDeep: Color, val accentSoft: Color,
    val ink: Color, val bg: Color, val chatBg: Color, val surface: Color, val line: Color,
    val muted: Color, val bubbleThem: Color, val dayChip: Color, val dayInk: Color,
    val e2eBg: Color, val e2eInk: Color, val divider: Color,
)

val LightPalette = Palette(
    dark = false,
    accent = Color(0xFF4B2FE0), accentDeep = Color(0xFF3A20C2), accentSoft = Color(0xFFEEEAFF),
    ink = Color(0xFF14161F), bg = Color(0xFFFFFFFF), chatBg = Color(0xFFF6F5FB), surface = Color(0xFFF3F2F8),
    line = Color(0xFFE3E1EC), muted = Color(0xFF5F6375), bubbleThem = Color(0xFFFFFFFF),
    dayChip = Color(0xFFE9E7F2), dayInk = Color(0xFF4A4E60),
    e2eBg = Color(0xFFFFF6DB), e2eInk = Color(0xFF6B4E00), divider = Color(0xFFECEAF3),
)

val DarkPalette = Palette(
    dark = true,
    accent = Color(0xFF6D55F5), accentDeep = Color(0xFFB9ABFF), accentSoft = Color(0xFF2A2550),
    ink = Color(0xFFF1F0F7), bg = Color(0xFF0F1117), chatBg = Color(0xFF0B0D12), surface = Color(0xFF1C1F2B),
    line = Color(0xFF2C2F3D), muted = Color(0xFFA9ACBD), bubbleThem = Color(0xFF1C1F2B),
    dayChip = Color(0xFF1C1F2B), dayInk = Color(0xFFC3C5D3),
    e2eBg = Color(0xFF2B2412), e2eInk = Color(0xFFF5D78A), divider = Color(0xFF1C1F2B),
)

/** Ippati theme. Activity / Settings marchinappudu matrame set avutundi (composition lo kaadu). */
var palette by mutableStateOf(LightPalette)

val Accent get() = palette.accent
val AccentDeep get() = palette.accentDeep
val AccentSoft get() = palette.accentSoft
val Ink get() = palette.ink
val Bg get() = palette.bg
val ChatBg get() = palette.chatBg
val Surface get() = palette.surface
val Line get() = palette.line
val Muted get() = palette.muted
val BubbleThem get() = palette.bubbleThem
val DayChipBg get() = palette.dayChip
val DayInk get() = palette.dayInk
val E2eBg get() = palette.e2eBg
val E2eInk get() = palette.e2eInk
val Divider get() = palette.divider
val NavMuted = Color(0xFFA9ACBD)
val Online = Color(0xFF15803D)
val OnlineDot = Color(0xFF16A34A)
val Danger = Color(0xFFC2410C)
val EndRed = Color(0xFFDC2626)
val ReadMint = Color(0xFF9EF0C9)
val OnAccentMuted = Color(0xFFE4DEFF)
val NavBg = Color(0xFF14161F)

@OptIn(ExperimentalTextApi::class)
private fun jakarta(w: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

@Composable
fun FlikkTheme(content: @Composable () -> Unit) {
    val base = Typography()
    fun TextStyle.j() = copy(fontFamily = Jakarta)
    MaterialTheme(
        colorScheme = if (palette.dark) darkColorScheme(
            primary = Accent, onPrimary = Color.White, secondary = Accent,
            background = Bg, onBackground = Ink, surface = Bg, onSurface = Ink,
            surfaceVariant = Surface, onSurfaceVariant = Muted, outline = Line,
            surfaceContainer = Surface, surfaceContainerHigh = Surface,
        ) else lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            secondary = Accent,
            background = Bg,
            onBackground = Ink,
            surface = Bg,
            onSurface = Ink,
            surfaceVariant = Surface,
            onSurfaceVariant = Muted,
            outline = Line,
            surfaceContainer = Bg,
            surfaceContainerHigh = Bg,
        ),
        typography = Typography(
            displayLarge = base.displayLarge.j(), displayMedium = base.displayMedium.j(), displaySmall = base.displaySmall.j(),
            headlineLarge = base.headlineLarge.j(), headlineMedium = base.headlineMedium.j(), headlineSmall = base.headlineSmall.j(),
            titleLarge = base.titleLarge.j(), titleMedium = base.titleMedium.j(), titleSmall = base.titleSmall.j(),
            bodyLarge = base.bodyLarge.j(), bodyMedium = base.bodyMedium.j(), bodySmall = base.bodySmall.j(),
            labelLarge = base.labelLarge.j(), labelMedium = base.labelMedium.j(), labelSmall = base.labelSmall.j(),
        ),
        content = content,
    )
}

/** Sub-screens kosam header (back + title) */
@Composable
fun Header(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    subtitleColor: Color = Muted,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(68.dp).padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back", tint = Ink, modifier = Modifier.size(20.dp)) }
            } else {
                Spacer(Modifier.width(14.dp))
            }
            val click = if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier
            Row(Modifier.weight(1f).then(click), verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title, fontSize = if (onBack == null && leading == null) 28.sp else 17.sp,
                        fontWeight = if (onBack == null && leading == null) FontWeight.ExtraBold else FontWeight.Bold,
                        letterSpacing = if (onBack == null && leading == null) (-0.8).sp else 0.sp,
                        color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrEmpty()) {
                        Text(
                            subtitle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (subtitle.contains("typing") || subtitle == "online") Online else subtitleColor,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            actions()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
    }
}

/** Round grey icon button (design lo header buttons) */
@Composable
fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).clip(CircleShape).background(Surface),
    ) { Icon(icon, label, tint = Ink, modifier = Modifier.size(22.dp)) }
}

private val palette = listOf(
    Color(0xFFE7E3FF) to Color(0xFF3A20C2),
    Color(0xFFFFE4D6) to Color(0xFF9A3412),
    Color(0xFFD7F5EC) to Color(0xFF0F5E4A),
    Color(0xFFDCEBFF) to Color(0xFF1E4E8C),
    Color(0xFFFCE1F0) to Color(0xFF9D174D),
    Color(0xFFFFF1C7) to Color(0xFF854D0E),
    Color(0xFFE5E7EB) to Color(0xFF374151),
)

private fun pair(name: String) = palette[Math.floorMod(name.hashCode(), palette.size)]

/** Peru nunchi text color (group lo sender name) */
fun avatarColor(name: String): Color = pair(name).second

fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it[0].isLetterOrDigit() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

@Composable
fun Avatar(name: String, size: Dp = 54.dp, rounded: Dp? = null) {
    val (bg, fg) = pair(name)
    Box(
        Modifier.size(size).clip(if (rounded != null) RoundedCornerShape(rounded) else CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials(name), color = fg, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.33f).sp)
    }
}

@Composable
fun Ticks(status: Int, size: Dp = 16.dp, onAccent: Boolean = false) {
    val dim = if (onAccent) OnAccentMuted else Muted
    when (status) {
        0 -> Icon(Icons.Filled.Schedule, "Pending", tint = dim, modifier = Modifier.size(size - 3.dp))
        1 -> Icon(Icons.Filled.Done, "Sent", tint = dim, modifier = Modifier.size(size))
        2 -> Icon(Icons.Filled.DoneAll, "Delivered", tint = dim, modifier = Modifier.size(size))
        else -> Icon(Icons.Filled.DoneAll, "Read", tint = if (onAccent) ReadMint else Accent, modifier = Modifier.size(size))
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

fun clock(ts: Long): String = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(ts))

fun listTime(ts: Long): String {
    val now = System.currentTimeMillis()
    return when {
        sameDay(ts, now) -> clock(ts)
        sameDay(ts, now - 86_400_000L) -> "Yesterday"
        now - ts < 6 * 86_400_000L -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ts))
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(ts))
    }
}

/** "Today, 9:45 AM" laaga (calls, status) */
fun longTime(ts: Long): String {
    val now = System.currentTimeMillis()
    val t = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))
    return when {
        now - ts < 60 * 60_000L -> "${((now - ts) / 60_000L).coerceAtLeast(1)} minutes ago"
        sameDay(ts, now) -> "Today, $t"
        sameDay(ts, now - 86_400_000L) -> "Yesterday, $t"
        else -> SimpleDateFormat("EEE, h:mm a", Locale.getDefault()).format(Date(ts))
    }
}

fun dayLabel(ts: Long): String {
    val now = System.currentTimeMillis()
    return when {
        sameDay(ts, now) -> "Today"
        sameDay(ts, now - 86_400_000L) -> "Yesterday"
        else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(ts))
    }
}

fun lastSeen(ts: Long): String {
    if (ts <= 0) return ""
    val now = System.currentTimeMillis()
    val t = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))
    return when {
        sameDay(ts, now) -> "last seen today at $t"
        sameDay(ts, now - 86_400_000L) -> "last seen yesterday at $t"
        else -> "last seen " + SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(ts))
    }
}

fun duration(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
