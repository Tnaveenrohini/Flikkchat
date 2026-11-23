package com.flikk.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val Pink = Color(0xFFFF3D8B)
val Surface = Color(0xFF141414)
val Line = Color(0xFF1F1F1F)
val Muted = Color(0xFFA3A3A3)
val ReadBlue = Color(0xFF4FC3F7)
val BubbleMe = Color(0xFF3A1426)
val BubbleThem = Color(0xFF1C1C1E)

@Composable
fun FlikkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pink,
            onPrimary = Color.White,
            secondary = Pink,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            surfaceVariant = Surface,
            onSurfaceVariant = Muted,
            outline = Line,
        ),
        content = content,
    )
}

@Composable
fun Header(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .statusBarsPadding()
            .height(60.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        } else {
            Box(Modifier.padding(start = 12.dp))
        }
        val click = if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier
        Row(Modifier.weight(1f).then(click), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Box(Modifier.padding(start = 10.dp))
        }
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, fontSize = if (onBack == null && leading == null) 24.sp else 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, fontSize = 12.sp, color = if (subtitle == "typing...") Pink else Muted, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        }
        actions()
    }
}

private val palette = listOf(Pink, Color(0xFF7C4DFF), Color(0xFF00BFA5), Color(0xFFFF9100), Color(0xFF2979FF))

fun avatarColor(name: String): Color = palette[Math.floorMod(name.hashCode(), palette.size)]

@Composable
fun Avatar(name: String, size: Dp = 48.dp) {
    val c = avatarColor(name)
    Box(
        Modifier.size(size).clip(CircleShape).background(c.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            color = c, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp,
        )
    }
}

@Composable
fun Ticks(status: Int, size: Dp = 16.dp) {
    when (status) {
        0 -> Icon(Icons.Filled.Schedule, "Pending", tint = Muted, modifier = Modifier.size(size - 2.dp))
        1 -> Icon(Icons.Filled.Done, "Sent", tint = Muted, modifier = Modifier.size(size))
        2 -> Icon(Icons.Filled.DoneAll, "Delivered", tint = Muted, modifier = Modifier.size(size))
        else -> Icon(Icons.Filled.DoneAll, "Read", tint = ReadBlue, modifier = Modifier.size(size))
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

fun clock(ts: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))

fun listTime(ts: Long): String {
    val now = System.currentTimeMillis()
    return when {
        sameDay(ts, now) -> clock(ts)
        sameDay(ts, now - 86_400_000L) -> "Yesterday"
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(ts))
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
    return when {
        sameDay(ts, now) -> "last seen today at ${clock(ts)}"
        sameDay(ts, now - 86_400_000L) -> "last seen yesterday at ${clock(ts)}"
        else -> "last seen " + SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(ts))
    }
}

fun duration(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
