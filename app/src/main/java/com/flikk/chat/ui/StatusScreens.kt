package com.flikk.chat.ui

import android.widget.Toast
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

val StatusColors = listOf(0xFF4B2FE0, 0xFF7C4DFF, 0xFF00897B, 0xFFEF6C00, 0xFF1E88E5, 0xFF43A047, 0xFF5D4037, 0xFF263238)

// ---------------- Text status ----------------

@Composable
fun TextStatusScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var ci by remember { mutableIntStateOf(0) }
    val bg = Color(StatusColors[ci])
    Box(Modifier.fillMaxSize().background(bg).imePadding()) {
        TextField(
            value = text, onValueChange = { text = it.take(500) },
            placeholder = { Text("Type a status", fontSize = 28.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 30.sp, color = Color.White, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color.White,
            ),
        )
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { ci = (ci + 1) % StatusColors.size }) { Icon(Icons.Filled.Palette, "Color", tint = Color.White) }
        }
        if (text.isNotBlank()) {
            FloatingActionButton(
                onClick = {
                    val t = text
                    scope.launch { Chat.postTextStatus(t, StatusColors[ci]); onDone() }
                },
                containerColor = Color.White, contentColor = bg,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Post") }
        }
    }
}

// ---------------- Status viewer ----------------

@Composable
fun StatusViewer(owner: String, onDone: () -> Unit) {
    val version by Chat.db.changes.collectAsState()
    val items by produceState<List<StatusItem>?>(initialValue = null, version) {
        value = withContext(Dispatchers.IO) { Chat.db.statuses().filter { it.owner == owner } }
    }
    val list = items ?: return
    if (list.isEmpty()) { LaunchedEffect(Unit) { onDone() }; return }
    val isMine = owner == Chat.me

    var index by remember { mutableIntStateOf(if (isMine) 0 else list.indexOfFirst { !it.seen }.coerceAtLeast(0)) }
    var progress by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    var showViews by remember { mutableStateOf(false) }
    val cur = list[index.coerceIn(0, list.lastIndex)]

    LaunchedEffect(cur.id) {
        if (!isMine) Chat.viewStatus(cur)
        progress = 0f
        while (progress < 1f) {
            delay(50)
            if (!paused && !showViews) progress += 50f / 5000f
        }
        if (index < list.lastIndex) index++ else onDone()
    }

    Box(
        Modifier.fillMaxSize().background(if (cur.type == "text") Color(cur.color.takeIf { it != 0L } ?: 0xFF4B2FE0) else Color.Black)
            .pointerInput(cur.id) {
                detectTapGestures(
                    onPress = { paused = true; tryAwaitRelease(); paused = false },
                    onTap = { o ->
                        if (o.x < size.width / 3f) { if (index > 0) index-- else progress = 0f }
                        else { if (index < list.lastIndex) index++ else onDone() }
                    },
                )
            },
    ) {
        if (cur.type == "text") {
            Text(
                cur.text, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(28.dp),
            )
        } else {
            AsyncImage(model = File(cur.media), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            if (cur.text.isNotBlank()) Text(
                cur.text, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f))
                    .navigationBarsPadding().padding(16.dp).padding(bottom = if (isMine) 40.dp else 0.dp),
            )
        }

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                list.forEachIndexed { i, _ ->
                    LinearProgressIndicator(
                        progress = { if (i < index) 1f else if (i == index) progress.coerceIn(0f, 1f) else 0f },
                        color = Color.White, trackColor = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    )
                }
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDone) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                Avatar(if (isMine) Chat.myName else Chat.nameOf(owner), 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (isMine) "My status" else Chat.nameOf(owner), color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(listTime(cur.ts), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                }
                if (isMine) IconButton(onClick = {
                    Chat.deleteMyStatus(cur)
                    if (index > 0 && index >= list.lastIndex) index--
                }) { Icon(Icons.Filled.Delete, "Delete", tint = Color.White) }
            }
        }

        if (isMine) {
            val views = remember(cur.id, version) { Chat.db.views(cur.id) }
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.45f))
                    .clickable { showViews = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("${views.size}", color = Color.White)
            }
            if (showViews) {
                AlertDialog(
                    onDismissRequest = { showViews = false },
                    title = { Text("Viewed by ${views.size}") },
                    text = {
                        Column {
                            if (views.isEmpty()) Text("Inka evaru chudaledu", color = Muted)
                            views.forEach { u ->
                                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(Chat.nameOf(u), 32.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(Chat.nameOf(u))
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showViews = false }) { Text("OK", color = Accent) } },
                    containerColor = Surface,
                )
            }
        }
    }
}
