package com.flikk.chat.ui

import android.widget.Toast
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.flikk.chat.data.Chat
import com.flikk.chat.data.StatusItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

val StatusColors = listOf(0xFF4B2FE0, 0xFF0F5E4A, 0xFF9D174D, 0xFF9A3412, 0xFF1E4E8C, 0xFF14161F)

// ---------------- Text status ----------------

@Composable
fun TextStatusScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var ci by remember { mutableIntStateOf(0) }
    var font by remember { mutableIntStateOf(0) }
    val fonts = listOf(Jakarta, androidx.compose.ui.text.font.FontFamily.Serif, androidx.compose.ui.text.font.FontFamily.Monospace)
    val bg = Color(StatusColors[ci])
    Column(Modifier.fillMaxSize().background(bg).imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, tr("Close"), tint = Color.White) }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable { font = (font + 1) % fonts.size },
                contentAlignment = Alignment.Center,
            ) { Text("Aa", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = fonts[font]) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (text.isEmpty()) Text(tr("Type a status"), fontSize = 30.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, fontFamily = fonts[font])
            androidx.compose.foundation.text.BasicTextField(
                value = text, onValueChange = { text = it.take(500) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 30.sp, lineHeight = 38.sp, color = Color.White, textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold, fontFamily = fonts[font]),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth().padding(28.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusColors.forEachIndexed { i, c ->
                Box(
                    Modifier.size(30.dp).border(2.dp, if (i == ci) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                        .padding(3.dp).clip(CircleShape).background(Color(c)).clickable { ci = i },
                )
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Status (My contacts)"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 8.dp))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(if (text.isBlank()) Color.White.copy(alpha = 0.4f) else Color.White)
                    .clickable(enabled = text.isNotBlank()) {
                        val t = text
                        scope.launch { Chat.postTextStatus(t, StatusColors[ci]); onDone() }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.Send, tr("Post"), tint = bg) }
        }
    }
}

// ---------------- Status viewer ----------------

@Composable
fun StatusViewer(owner: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by Chat.db.changes.collectAsState()
    val items by produceState<List<StatusItem>?>(initialValue = null, version) {
        value = withContext(Dispatchers.IO) { Chat.db.statuses().filter { it.owner == owner } }
    }
    val list = items ?: return
    if (list.isEmpty()) { LaunchedEffect(Unit) { onDone() }; return }
    val isMine = owner == Chat.me
    val name = if (isMine) Chat.myName else Chat.nameOf(owner)

    var index by remember { mutableIntStateOf(if (isMine) 0 else list.indexOfFirst { !it.seen }.coerceAtLeast(0)) }
    var progress by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    var showViews by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf("") }
    val cur = list[index.coerceIn(0, list.lastIndex)]
    val timed = cur.type == "video" || cur.type == "voice"
    var mediaMs by remember(cur.id) { mutableStateOf(0L) }
    val hold = paused || showViews || menu || reply.isNotEmpty()

    LaunchedEffect(cur.id) {
        if (!isMine) Chat.viewStatus(cur)
        progress = 0f
        while (progress < 1f) {
            delay(50)
            val total = if (timed) mediaMs.toFloat() else 5000f
            val h = paused || showViews || menu || reply.isNotEmpty()
            if (!h && total > 0f) progress += 50f / total
        }
        if (index < list.lastIndex) index++ else onDone()
    }

    // Voice status player
    if (cur.type == "voice") {
        val player = remember(cur.id) {
            try {
                android.media.MediaPlayer().apply {
                    setDataSource(cur.media)
                    setOnCompletionListener { progress = 1f }
                    prepare()
                }
            } catch (_: Exception) { null }
        }
        DisposableEffect(cur.id) {
            if (player == null) progress = 1f
            else { mediaMs = player.duration.toLong().coerceAtLeast(1000L); player.start() }
            onDispose { try { player?.release() } catch (_: Exception) {} }
        }
        LaunchedEffect(hold) {
            try { if (hold) player?.pause() else if (player != null && !player.isPlaying && progress < 1f) player.start() } catch (_: Exception) {}
        }
    }

    val bg = when (cur.type) {
        "text", "voice" -> Color(cur.color.takeIf { it != 0L } ?: 0xFF4B2FE0)
        else -> Color.Black
    }
    val views = remember(cur.id, version) { if (isMine) Chat.db.views(cur.id) else emptyList() }

    Box(Modifier.fillMaxSize().background(bg).imePadding()) {
        // ---- Content (full screen; tap left/right, press = pause) ----
        Box(
            Modifier.fillMaxSize().pointerInput(cur.id) {
                detectTapGestures(
                    onPress = { paused = true; tryAwaitRelease(); paused = false },
                    onTap = { o ->
                        if (o.x < size.width / 3f) { if (index > 0) index-- else progress = 0f }
                        else { if (index < list.lastIndex) index++ else onDone() }
                    },
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            when (cur.type) {
                "video" -> key(cur.id) {
                    StatusVideo(android.net.Uri.fromFile(File(cur.media)), Modifier.fillMaxSize(), paused = hold,
                        onReady = { mediaMs = it }, onEnd = { progress = 1f })
                }
                "image" -> AsyncImage(model = File(cur.media), contentDescription = tr("Status photo"),
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                "voice" -> {
                    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "p").animateFloat(
                        1f, 1.18f, androidx.compose.animation.core.infiniteRepeatable(
                            androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "s")
                    Box(Modifier.size(150.dp).graphicsLayer { scaleX = if (hold) 1f else pulse; scaleY = if (hold) 1f else pulse }
                        .clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(104.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Mic, tr("Voice"), tint = Color.White, modifier = Modifier.size(52.dp))
                        }
                    }
                }
                else -> Text(cur.text, color = Color.White, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(30.dp))
            }
        }

        // ---- Top: progress + header ----
        Column(
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                .statusBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                list.forEachIndexed { i, _ ->
                    LinearProgressIndicator(
                        progress = { if (i < index) 1f else if (i == index) progress.coerceIn(0f, 1f) else 0f },
                        color = Color.White, trackColor = Color.White.copy(alpha = 0.35f), drawStopIndicator = {},
                        modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back"), tint = Color.White) }
                Box(Modifier.size(46.dp).border(2.dp, Color.White, CircleShape).padding(3.dp), contentAlignment = Alignment.Center) {
                    Avatar(name, 40.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (isMine) tr("My status") else name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(longTime(cur.ts), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, tr("More options"), tint = Color.White) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (isMine) {
                            DropdownMenuItem(text = { Text(tr("Delete")) }, onClick = {
                                menu = false
                                Chat.deleteMyStatus(cur)
                                if (index > 0 && index >= list.lastIndex) index--
                            })
                        }
                        DropdownMenuItem(text = { Text(tr("Share")) }, onClick = { menu = false; shareStatus(ctx, cur) })
                        if (!isMine) DropdownMenuItem(text = { Text(tr("Repost")) }, onClick = {
                            menu = false
                            scope.launch {
                                val err = Chat.repostStatus(cur)
                                Toast.makeText(ctx, tr(err ?: "Status posted ✅"), Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }
            }
        }

        // ---- Bottom ----
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (cur.text.isNotBlank() && cur.type != "text") {
                Text(cur.text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    lineHeight = 22.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp))
            }
            if (isMine) {
                Row(
                    Modifier.padding(top = 6.dp, bottom = 22.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.16f))
                        .clickable { showViews = true }.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${views.size}", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            } else {
                fun sendReply(t: String, toast: String) {
                    Chat.replyToStatus(cur, t)
                    Toast.makeText(ctx, toast, Toast.LENGTH_SHORT).show()
                }
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF23252E)).padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (reply.isEmpty()) Text(tr("Reply"), color = Color.White.copy(alpha = 0.8f), fontSize = 17.sp)
                            BasicTextField(
                                value = reply, onValueChange = { reply = it }, singleLine = true,
                                textStyle = TextStyle(fontFamily = Jakarta, fontSize = 16.sp, color = Color.White),
                                cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (reply.isEmpty()) {
                            Text("👍", fontSize = 26.sp, modifier = Modifier.clip(CircleShape).clickable { sendReply("👍", tr("Reply sent ✅")) }.padding(8.dp))
                            Text("👎", fontSize = 26.sp, modifier = Modifier.clip(CircleShape).clickable { sendReply("👎", tr("Reply sent ✅")) }.padding(8.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    if (reply.isNotBlank()) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(Accent).clickable {
                                sendReply(reply.trim(), tr("Reply sent ✅")); reply = ""
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.AutoMirrored.Filled.Send, tr("Send reply"), tint = OnAccent) }
                    } else {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF23252E)).clickable {
                                scope.launch {
                                    val err = Chat.repostStatus(cur)
                                    Toast.makeText(ctx, tr(err ?: "Status posted ✅"), Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Repeat, tr("Repost"), tint = Color.White) }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF23252E)).clickable { sendReply("❤️", tr("❤️ sent")) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.FavoriteBorder, tr("Like status"), tint = Color.White) }
                    }
                }
            }
        }
        if (isMine && showViews) ViewersSheet(cur, views, onClose = { showViews = false }, onDelete = {
            showViews = false
            Chat.deleteMyStatus(cur)
            if (index > 0 && index >= list.lastIndex) index--
        })
    }
}

/** WhatsApp laaga "Viewed by N" — kinda nunchi sheet */
@Composable
private fun ViewersSheet(st: StatusItem, views: List<Pair<String, Long>>, onClose: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.62f).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Bg).clickable(enabled = false) {},
        ) {
            Row(Modifier.fillMaxWidth().background(Accent).padding(start = 20.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Viewed by {0}", views.size), color = OnAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, tr("Delete status"), tint = OnAccent) }
                IconButton(onClick = { shareStatus(ctx, st) }) { Icon(Icons.Filled.Share, tr("Share"), tint = OnAccent) }
            }
            if (views.isEmpty()) Text(tr("No views yet"), color = Muted, modifier = Modifier.padding(24.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(views, key = { it.first }) { (u, ts) ->
                    val n = Chat.nameOf(u)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(n, 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(n, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            if (ts > 0) Text(longTime(ts), color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun shareStatus(ctx: android.content.Context, st: StatusItem) {
    try {
        val i = android.content.Intent(android.content.Intent.ACTION_SEND)
        if (st.media.isNotEmpty()) {
            val u = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(st.media))
            i.type = if (st.type == "video") "video/*" else "image/*"
            i.putExtra(android.content.Intent.EXTRA_STREAM, u)
            i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (st.text.isNotBlank()) i.putExtra(android.content.Intent.EXTRA_TEXT, st.text)
        } else {
            i.type = "text/plain"
            i.putExtra(android.content.Intent.EXTRA_TEXT, st.text)
        }
        ctx.startActivity(android.content.Intent.createChooser(i, tr("Share")))
    } catch (_: Exception) {}
}
