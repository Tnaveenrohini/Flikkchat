package com.flikk.chat.ui

import android.Manifest
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.flikk.chat.data.Chat
import com.flikk.chat.data.Msg
import com.flikk.chat.data.VoiceRecorder
import com.flikk.chat.data.gidOf
import com.flikk.chat.data.isGroup
import com.flikk.chat.data.preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Okkate voice message play avutundi */
object VoicePlayer {
    var playing by mutableStateOf<String?>(null)
        private set
    private var mp: MediaPlayer? = null

    fun toggle(m: Msg) {
        if (playing == m.id) { stop(); return }
        stop()
        try {
            mp = MediaPlayer().apply {
                setDataSource(m.media)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            playing = m.id
        } catch (_: Exception) { stop() }
    }

    fun position(): Int = try { mp?.currentPosition ?: 0 } catch (_: Exception) { 0 }

    fun stop() {
        try { mp?.release() } catch (_: Exception) {}
        mp = null
        playing = null
    }
}

@Composable
fun ChatScreen(
    peer: String,
    onBack: () -> Unit,
    openGroupInfo: (String) -> Unit,
    openForward: (String) -> Unit,
    openImage: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val group = isGroup(peer)

    val version by Chat.db.changes.collectAsState()
    val msgs by produceState(initialValue = emptyList<Msg>(), peer, version) {
        value = withContext(Dispatchers.IO) { Chat.db.messages(peer) }
    }
    val local by produceState(initialValue = Chat.db.peer(peer), version) { value = Chat.db.peer(peer) }
    val groupInfo by produceState(initialValue = if (group) Chat.db.group(gidOf(peer)) else null, version) {
        value = if (group) Chat.db.group(gidOf(peer)) else null
    }
    val state by (if (group) kotlinx.coroutines.flow.flowOf(null) else Chat.peerState(peer)).collectAsState(initial = null)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    DisposableEffect(peer) {
        Chat.openChat = peer
        onDispose {
            if (Chat.openChat == peer) Chat.openChat = null
            VoicePlayer.stop()
        }
    }
    LaunchedEffect(version) { withContext(Dispatchers.IO) { Chat.markRead(peer) } }

    val name = local?.name?.takeIf { it.isNotBlank() } ?: groupInfo?.name ?: state?.name ?: "Chat"
    val amMember = !group || (groupInfo?.members?.contains(Chat.me) ?: true)
    val subtitle = if (group) {
        groupInfo?.members?.joinToString(", ") { Chat.nameOf(it) }
    } else state?.let { s ->
        when {
            s.typingTo == Chat.me && now - s.typingAt < Chat.TYPING_WINDOW_MS -> "typing..."
            now - s.lastActive < Chat.ONLINE_WINDOW_MS -> "online"
            else -> lastSeen(s.lastActive)
        }
    }

    var text by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Msg?>(null) }
    var replyTo by remember { mutableStateOf<Msg?>(null) }
    var askDelete by remember { mutableStateOf<Msg?>(null) }
    var menu by remember { mutableStateOf(false) }
    val caller = rememberCaller()
    var askTimer by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf<String?>(null) }
    val blocked = remember(version) { !group && Chat.isBlocked(peer) }
    val timer = remember(version) { Chat.timerOf(peer) }
    LaunchedEffect(peer) { withContext(Dispatchers.IO) { Chat.purgeExpired() } }
    val listState = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(0) }
    BackHandler(enabled = selected != null) { selected = null }

    // ----- Voice recording -----
    val recorder = remember { VoiceRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var recStart by remember { mutableLongStateOf(0L) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(ctx, "Voice message ki mic permission kavali", Toast.LENGTH_SHORT).show()
    }
    fun startRec() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO); return
        }
        VoicePlayer.stop()
        if (recorder.start(UUID.randomUUID().toString())) { recording = true; recStart = System.currentTimeMillis() }
        else Toast.makeText(ctx, "Recording start avvaledu", Toast.LENGTH_SHORT).show()
    }
    fun stopRec(send: Boolean) {
        recording = false
        if (!send) { recorder.cancel(); return }
        val r = recorder.stop()
        if (r == null) Toast.makeText(ctx, "Chala chinna recording", Toast.LENGTH_SHORT).show()
        else { Chat.sendVoice(peer, r.first, r.second, replyTo); replyTo = null }
    }

    // ----- Photo / video picker -----
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val isVideo = ctx.contentResolver.getType(uri)?.startsWith("video") == true
            val caption = text
            val reply = replyTo
            text = ""; replyTo = null
            scope.launch {
                val err = Chat.sendMedia(peer, uri, isVideo, caption, reply)
                if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).imePadding()) {
        val sel = selected
        if (sel != null) {
            SelectionBar(
                m = sel,
                onClose = { selected = null },
                onReply = { replyTo = sel; selected = null },
                onStar = { Chat.star(sel); selected = null },
                onCopy = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("message", sel.text))
                    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    selected = null
                },
                onForward = { selected = null; openForward(sel.id) },
                onDelete = { askDelete = sel; selected = null },
            )
        } else {
            Header(
                title = name, subtitle = subtitle, onBack = onBack,
                leading = { Avatar(name, 40.dp) },
                onTitleClick = if (group) ({ openGroupInfo(gidOf(peer)) }) else null,
                actions = {
                    if (timer > 0) Icon(Icons.Filled.Timer, "Disappearing", tint = Pink, modifier = Modifier.size(20.dp))
                    if (!group && !blocked) {
                        IconButton(onClick = { caller(peer, true) }) { Icon(Icons.Filled.Videocam, "Video call") }
                        IconButton(onClick = { caller(peer, false) }) { Icon(Icons.Filled.Call, "Voice call") }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (group) DropdownMenuItem(text = { Text("Group info") }, onClick = { menu = false; openGroupInfo(gidOf(peer)) })
                            DropdownMenuItem(text = { Text("Disappearing messages") }, onClick = { menu = false; askTimer = true })
                            if (!group) DropdownMenuItem(text = { Text("Encryption 🔒") }, onClick = {
                                menu = false
                                scope.launch {
                                    showCode = withContext(Dispatchers.IO) { Chat.safetyCode(peer) } ?: "Ee contact paata app vaadutunnaru — encryption ledu"
                                }
                            })
                            DropdownMenuItem(text = { Text("Clear chat") }, onClick = { menu = false; askClear = true })
                            if (!group) DropdownMenuItem(
                                text = { Text(if (blocked) "Unblock" else "Block", color = if (blocked) Color.White else Color(0xFFFF6B6B)) },
                                onClick = { menu = false; Chat.setBlocked(peer, !blocked) },
                            )
                        }
                    }
                },
            )
        }
        HorizontalDivider(color = Line)

        val reversed = remember(msgs) { msgs.asReversed() }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
            state = listState,
            reverseLayout = true,
        ) {
            items(reversed.size, key = { reversed[it].id }) { i ->
                val m = reversed[i]
                val older = reversed.getOrNull(i + 1)
                Column {
                    if (older == null || dayLabel(older.ts) != dayLabel(m.ts)) DayChip(dayLabel(m.ts))
                    if (m.type == "sys") SysChip(m.text)
                    else Bubble(
                        m = m,
                        showSender = group && !m.fromMe && (older == null || older.sender != m.sender || older.type == "sys"),
                        selected = selected?.id == m.id,
                        onLongPress = { if (!m.deleted) selected = m },
                        onClick = {
                            if (selected != null) { selected = if (selected?.id == m.id) null else m }
                            else openMedia(ctx, m, openImage)
                        },
                    )
                }
            }
            item(key = "e2e") {
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "🔒 Messages end-to-end encrypted. Flikkchat / Firebase kuda chadavalevu.",
                        fontSize = 12.sp, color = Color(0xFFE0C36A), textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(8.dp)).background(Surface)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        if (blocked) {
            Text(
                "Meeru ee contact ni block chesaru. Unblock cheyyadaniki tap cheyyandi.",
                color = Muted, textAlign = TextAlign.Center, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().clickable { Chat.setBlocked(peer, false) }
                    .navigationBarsPadding().padding(18.dp),
            )
        } else if (!amMember) {
            Text(
                "Meeru ee group lo member kaadu",
                color = Muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(18.dp),
            )
        } else {
        replyTo?.let { r ->
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Surface),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(4.dp).height(48.dp).background(Pink))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(if (r.fromMe) "You" else Chat.nameOf(r.sender.ifEmpty { r.peer }),
                        color = Pink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(preview(r), color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { replyTo = null }) { Icon(Icons.Filled.Close, "Cancel reply", tint = Muted) }
            }
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (recording) {
                Row(
                    Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(26.dp)).background(Surface),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { stopRec(false) }) { Icon(Icons.Filled.Delete, "Cancel", tint = Color(0xFFFF6B6B)) }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color.Red))
                    Spacer(Modifier.width(8.dp))
                    Text(duration(now - recStart), color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Recording...", color = Muted, fontSize = 13.sp)
                }
            } else {
                TextField(
                    value = text,
                    onValueChange = { text = it; if (it.isNotEmpty()) Chat.typing(peer) },
                    placeholder = { Text("Message") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(26.dp),
                    maxLines = 5,
                    trailingIcon = {
                        IconButton(onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }) { Icon(Icons.Filled.AttachFile, "Photo / video", tint = Muted) }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Surface, unfocusedContainerColor = Surface,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Pink,
                    ),
                )
            }
            Spacer(Modifier.width(8.dp))
            val canSend = text.isNotBlank() || recording
            FloatingActionButton(
                onClick = {
                    when {
                        recording -> stopRec(true)
                        text.isNotBlank() -> { Chat.send(peer, text, reply = replyTo); text = ""; replyTo = null }
                        else -> startRec()
                    }
                },
                containerColor = Pink, contentColor = Color.White,
                shape = CircleShape, modifier = Modifier.size(54.dp),
            ) {
                if (canSend) Icon(Icons.AutoMirrored.Filled.Send, "Send") else Icon(Icons.Filled.Mic, "Voice message")
            }
        }
        }
    }

    if (askTimer) {
        val options = listOf(0L to "Off", 86_400_000L to "24 hours", 7 * 86_400_000L to "7 days", 90 * 86_400_000L to "90 days")
        AlertDialog(
            onDismissRequest = { askTimer = false },
            title = { Text("Disappearing messages") },
            text = {
                Column {
                    Text("Kotha messages ee time tarvatha rendu phones nunchi delete avutayi.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    for ((v, label) in options) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                askTimer = false
                                if (v != timer) Chat.setTimer(peer, v)
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = v == timer, onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = Pink))
                            Spacer(Modifier.width(10.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { askTimer = false }) { Text("Close", color = Muted) } },
            containerColor = Surface,
        )
    }
    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text("Clear this chat?") },
            text = { Text("Ee chat lo messages, photos annee mee phone nunchi delete avutayi.", color = Muted) },
            confirmButton = { TextButton(onClick = { askClear = false; Chat.clearChat(peer) }) { Text("Clear", color = Pink) } },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text("Cancel", color = Muted) } },
            containerColor = Surface,
        )
    }
    showCode?.let { code ->
        AlertDialog(
            onDismissRequest = { showCode = null },
            title = { Text("🔒 Encryption") },
            text = {
                Column {
                    Text("Messages, photos, voice annee end-to-end encrypted. Mee iddari phones lo matrame open avutayi.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Security code", color = Pink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(code, fontSize = 16.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Mee friend phone lo kuda ide code unte, connection safe.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showCode = null }) { Text("OK", color = Pink) } },
            containerColor = Surface,
        )
    }

    askDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { askDelete = null },
            title = { Text("Delete message?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (m.fromMe && !m.deleted) {
                        TextButton(onClick = { Chat.deleteForEveryone(m); askDelete = null }) {
                            Text("Delete for everyone", color = Pink)
                        }
                    }
                    TextButton(onClick = { Chat.deleteForMe(m); askDelete = null }) { Text("Delete for me", color = Pink) }
                    TextButton(onClick = { askDelete = null }) { Text("Cancel", color = Muted) }
                }
            },
            containerColor = Surface,
        )
    }
}

private fun openMedia(ctx: Context, m: Msg, openImage: (String) -> Unit) {
    if (m.media.isEmpty() || m.deleted) return
    when (m.type) {
        "image" -> openImage(m.media)
        "video" -> try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(m.media))
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, m.mime.ifEmpty { "video/*" })
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (_: Exception) {
            Toast.makeText(ctx, "Video play cheyyadaniki app ledu (Flikk install cheyyandi!)", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun SelectionBar(
    m: Msg,
    onClose: () -> Unit,
    onReply: () -> Unit,
    onStar: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Surface).statusBarsPadding().height(60.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onReply) { Icon(Icons.AutoMirrored.Filled.Reply, "Reply") }
        IconButton(onClick = onStar) { Icon(if (m.starred) Icons.Filled.Star else Icons.Filled.StarBorder, "Star") }
        if (m.text.isNotEmpty()) IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, "Copy") }
        IconButton(onClick = onForward) { Icon(Icons.AutoMirrored.Filled.Shortcut, "Forward") }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
    }
}

@Composable
private fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            label, fontSize = 12.sp, color = Muted,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Surface).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SysChip(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            text, fontSize = 12.sp, color = Color(0xFFE0C36A), textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Surface).padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: Msg, showSender: Boolean, selected: Boolean, onLongPress: () -> Unit, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(if (selected) Pink.copy(alpha = 0.18f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 2.dp),
        contentAlignment = if (m.fromMe) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            Modifier
                .widthIn(max = 290.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (m.fromMe) 16.dp else 4.dp,
                        bottomEnd = if (m.fromMe) 4.dp else 16.dp,
                    )
                )
                .background(if (m.fromMe) BubbleMe else BubbleThem)
                .padding(4.dp),
        ) {
            if (showSender) {
                val sn = Chat.nameOf(m.sender)
                Text(sn, color = avatarColor(sn), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, end = 8.dp))
            }
            if (m.fwd && !m.deleted) {
                Text("↪ Forwarded", color = Muted, fontSize = 12.sp, fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, end = 8.dp))
            }
            if (m.replyId.isNotEmpty() && !m.deleted) {
                Row(
                    Modifier.padding(4.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.35f))
                        .height(IntrinsicSize.Min),
                ) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(Pink))
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                        Text(m.replyName.ifEmpty { "Message" }, color = Pink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(m.replyText, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            when {
                m.deleted -> Text(
                    "🚫 This message was deleted", color = Muted, fontStyle = FontStyle.Italic, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                m.type == "image" -> MediaThumb(m, isVideo = false)
                m.type == "video" -> MediaThumb(m, isVideo = true)
                m.type == "audio" -> AudioRow(m)
            }
            if (!m.deleted && m.text.isNotEmpty()) {
                Text(m.text, fontSize = 15.sp, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Row(
                Modifier.align(Alignment.End).padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (m.starred) { Icon(Icons.Filled.Star, null, tint = Muted, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(3.dp)) }
                Text(clock(m.ts), fontSize = 11.sp, color = Muted)
                if (m.fromMe && !m.deleted) { Spacer(Modifier.width(4.dp)); Ticks(m.status, 15.dp) }
            }
        }
    }
}

@Composable
private fun MediaThumb(m: Msg, isVideo: Boolean) {
    Box(
        Modifier.size(width = 260.dp, height = 260.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0D0D0D)),
        contentAlignment = Alignment.Center,
    ) {
        if (m.media.isNotEmpty()) {
            AsyncImage(
                model = File(m.media), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(if (isVideo) "🎥" else "📷", fontSize = 40.sp)
        }
        if (isVideo) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(34.dp))
            }
            if (m.dur > 0) {
                Text(
                    duration(m.dur), color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun AudioRow(m: Msg) {
    val playing = VoicePlayer.playing == m.id
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) {
        pos = 0
        while (playing) { pos = VoicePlayer.position().toLong(); delay(200) }
    }
    Row(Modifier.width(240.dp).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (m.media.isNotEmpty()) VoicePlayer.toggle(m) }) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { if (m.dur > 0) (pos.toFloat() / m.dur).coerceIn(0f, 1f) else 0f },
                color = Pink, trackColor = Muted.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.height(4.dp))
            Text(duration(if (playing) pos else m.dur), fontSize = 11.sp, color = Muted)
        }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.Mic, null, tint = Pink, modifier = Modifier.size(20.dp))
    }
}
