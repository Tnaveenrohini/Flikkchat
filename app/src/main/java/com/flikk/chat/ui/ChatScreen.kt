package com.flikk.chat.ui

import android.Manifest
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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
    openContactInfo: (String) -> Unit = {},
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

    // ----- Camera (photo teesi pampu) -----
    var shotFile by remember { mutableStateOf<File?>(null) }
    val shooter = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = shotFile
        shotFile = null
        if (ok && f != null && f.length() > 0) {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            val reply = replyTo
            replyTo = null
            scope.launch {
                val err = Chat.sendMedia(peer, uri, false, "", reply)
                f.delete()
                if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
            }
        } else f?.delete()
    }
    val camPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            val f = File(com.flikk.chat.data.Media.dir(ctx), "shot_${UUID.randomUUID()}.jpg")
            shotFile = f
            shooter.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
        } else Toast.makeText(ctx, "Camera permission kavali", Toast.LENGTH_SHORT).show()
    }
    fun takePhoto() = camPermission.launch(Manifest.permission.CAMERA)

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

    var showAttach by remember { mutableStateOf(false) }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            val reply = replyTo; replyTo = null
            scope.launch { Chat.shareContact(peer, uri, reply)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val reply = replyTo; replyTo = null
            scope.launch { Chat.sendAudioFile(peer, uri, reply)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }
        }
    }
    val locPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) {
            Toast.makeText(ctx, "Location teesukuntunnam…", Toast.LENGTH_SHORT).show()
            val reply = replyTo; replyTo = null
            scope.launch { Chat.shareLocation(peer, reply)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }
        } else Toast.makeText(ctx, "Location permission kavali", Toast.LENGTH_SHORT).show()
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) contactPicker.launch(null) else Toast.makeText(ctx, "Contacts permission kavali", Toast.LENGTH_SHORT).show()
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(ChatBg).imePadding()) {
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
                leading = { Avatar(name, 42.dp) },
                onTitleClick = if (group) ({ openGroupInfo(gidOf(peer)) }) else ({ openContactInfo(peer) }),
                actions = {
                    if (timer > 0) Icon(Icons.Filled.Timer, "Disappearing", tint = Accent, modifier = Modifier.size(20.dp))
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
                                text = { Text(if (blocked) "Unblock" else "Block", color = if (blocked) Color.White else Danger) },
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
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
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
                Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(10.dp)).background(E2eBg)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Filled.Lock, null, tint = E2eInk, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Messages are end-to-end encrypted and saved only on your phone.",
                            fontSize = 11.5.sp, lineHeight = 16.sp, color = E2eInk, textAlign = TextAlign.Center,
                        )
                    }
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
                    .clip(RoundedCornerShape(16.dp)).background(Bg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(4.dp).height(48.dp).background(Accent))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(if (r.fromMe) "You" else Chat.nameOf(r.sender.ifEmpty { r.peer }),
                        color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(preview(r), color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { replyTo = null }) { Icon(Icons.Filled.Close, "Cancel reply", tint = Muted) }
            }
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(
                Modifier.weight(1f).heightIn(min = 50.dp).shadow(2.dp, RoundedCornerShape(25.dp))
                    .clip(RoundedCornerShape(25.dp)).background(Bg).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (recording) {
                    IconButton(onClick = { stopRec(false) }) { Icon(Icons.Filled.Delete, "Cancel recording", tint = Danger) }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(EndRed))
                    Spacer(Modifier.width(8.dp))
                    Text(duration(now - recStart), color = Ink, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text("Recording...", color = Muted, fontSize = 13.sp)
                } else {
                    Box(Modifier.weight(1f).padding(start = 12.dp, top = 12.dp, bottom = 12.dp)) {
                        if (text.isEmpty()) Text("Message", color = Muted, fontSize = 15.sp)
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it; if (it.isNotEmpty()) Chat.typing(peer) },
                            textStyle = TextStyle(fontSize = 15.sp, color = Ink, fontFamily = Jakarta),
                            cursorBrush = SolidColor(Accent),
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    IconButton(onClick = { showAttach = true }, modifier = Modifier.size(40.dp, 44.dp)) {
                        Icon(Icons.Filled.AttachFile, "Attach", tint = Muted, modifier = Modifier.size(21.dp))
                    }
                    if (text.isEmpty()) IconButton(onClick = { takePhoto() }, modifier = Modifier.size(40.dp, 44.dp)) {
                        Icon(Icons.Outlined.PhotoCamera, "Camera", tint = Muted, modifier = Modifier.size(21.dp))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            val canSend = text.isNotBlank() || recording
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(Accent).clickable {
                    when {
                        recording -> stopRec(true)
                        text.isNotBlank() -> { Chat.send(peer, text, reply = replyTo); text = ""; replyTo = null }
                        else -> startRec()
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                if (canSend) Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(22.dp))
                else Icon(Icons.Filled.Mic, "Record voice message", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        }
    }

    if (showAttach) {
        AttachSheet(
            onClose = { showAttach = false },
            onGallery = { showAttach = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            onCamera = { showAttach = false; takePhoto() },
            onLocation = {
                showAttach = false
                locPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            onContact = {
                showAttach = false
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) contactPicker.launch(null)
                else contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            },
            onAudio = { showAttach = false; audioPicker.launch("audio/*") },
        )
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
                                colors = RadioButtonDefaults.colors(selectedColor = Accent))
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
            confirmButton = { TextButton(onClick = { askClear = false; Chat.clearChat(peer) }) { Text("Clear", color = Accent) } },
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
                    Text("Security code", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(code, fontSize = 16.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Mee friend phone lo kuda ide code unte, connection safe.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showCode = null }) { Text("OK", color = Accent) } },
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
                            Text("Delete for everyone", color = Accent)
                        }
                    }
                    TextButton(onClick = { Chat.deleteForMe(m); askDelete = null }) { Text("Delete for me", color = Accent) }
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
        Modifier.fillMaxWidth().background(Bg).statusBarsPadding().height(68.dp).padding(horizontal = 4.dp),
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
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            label, fontSize = 12.sp, color = DayInk, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(DayChipBg).padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun SysChip(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            text, fontSize = 12.sp, color = DayInk, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(DayChipBg).padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: Msg, showSender: Boolean, selected: Boolean, onLongPress: () -> Unit, onClick: () -> Unit) {
    val mine = m.fromMe
    val shape = RoundedCornerShape(
        topStart = 20.dp, topEnd = 20.dp,
        bottomStart = if (mine) 20.dp else 6.dp,
        bottomEnd = if (mine) 6.dp else 20.dp,
    )
    val ink = if (mine) Color.White else Ink
    val sub = if (mine) OnAccentMuted else Muted
    Box(
        Modifier.fillMaxWidth()
            .background(if (selected) Accent.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 3.dp),
        contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .then(if (mine) Modifier else Modifier.shadow(1.dp, shape))
                .clip(shape)
                .background(if (mine) Accent else BubbleThem)
                .padding(6.dp),
        ) {
            if (showSender) {
                val sn = Chat.nameOf(m.sender)
                Text(sn, color = avatarColor(sn), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp, end = 8.dp))
            }
            if (m.fwd && !m.deleted) {
                Text("↪ Forwarded", color = sub, fontSize = 12.sp, fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp, end = 8.dp))
            }
            if (m.replyId.isNotEmpty() && !m.deleted) {
                Row(
                    Modifier.padding(bottom = 4.dp).clip(RoundedCornerShape(13.dp))
                        .background(if (mine) Color.White.copy(alpha = 0.16f) else Surface)
                        .height(IntrinsicSize.Min),
                ) {
                    if (!mine) Box(Modifier.width(4.dp).fillMaxHeight().background(Accent))
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(m.replyName.ifEmpty { "Message" }, color = if (mine) Color.White else Accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text(m.replyText, color = if (mine) OnAccentMuted else Muted, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            when {
                m.deleted -> Text(
                    "🚫 This message was deleted", color = sub, fontStyle = FontStyle.Italic, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                m.type == "image" -> MediaThumb(m, isVideo = false)
                m.type == "video" -> MediaThumb(m, isVideo = true)
                m.type == "audio" -> AudioRow(m)
            }
            if (!m.deleted && m.text.isNotEmpty()) {
                Text(m.text, fontSize = 15.sp, lineHeight = 21.sp, color = ink,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Row(
                Modifier.align(Alignment.End).padding(horizontal = 8.dp).padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (m.starred) { Icon(Icons.Filled.Star, null, tint = sub, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(3.dp)) }
                Text(clock(m.ts), fontSize = 11.sp, color = sub)
                if (mine && !m.deleted) { Spacer(Modifier.width(4.dp)); Ticks(m.status, 16.dp, onAccent = true) }
            }
        }
    }
}

@Composable
private fun MediaThumb(m: Msg, isVideo: Boolean) {
    Box(
        Modifier.size(width = 240.dp, height = 170.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF2A2340)),
        contentAlignment = Alignment.Center,
    ) {
        if (m.media.isNotEmpty()) {
            AsyncImage(
                model = File(m.media), contentDescription = if (isVideo) "Video" else "Photo",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(if (isVideo) Icons.Filled.Videocam else Icons.Filled.Image, null, tint = Color(0xFFD9D3F5), modifier = Modifier.size(30.dp))
        }
        if (isVideo) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, "Play video", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            if (m.dur > 0) {
                Text(
                    duration(m.dur), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
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
    val mine = m.fromMe
    val playing = VoicePlayer.playing == m.id
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) {
        pos = 0
        while (playing) { pos = VoicePlayer.position().toLong(); delay(150) }
    }
    // Message id nunchi waveform (prathi voice ki different)
    val bars = remember(m.id) {
        val r = java.util.Random(m.id.hashCode().toLong())
        List(30) { 6 + r.nextInt(21) }
    }
    val progress = if (m.dur > 0) (pos.toFloat() / m.dur).coerceIn(0f, 1f) else 0f
    val done = if (mine) Color.White else Accent
    val todo = if (mine) Color.White.copy(alpha = 0.4f) else Color(0xFFC9C6D8)
    Column(Modifier.width(238.dp).padding(start = 6.dp, end = 6.dp, top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(if (mine) Color.White else Accent)
                    .clickable(enabled = m.media.isNotEmpty()) { VoicePlayer.toggle(m) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play voice message",
                    tint = if (mine) Accent else Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                bars.forEachIndexed { i, h ->
                    Box(Modifier.weight(1f).height(h.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i < (progress * bars.size).toInt()) done else todo))
                }
            }
        }
        Text(duration(if (playing) pos else m.dur), fontSize = 11.sp, color = if (mine) OnAccentMuted else Muted,
            modifier = Modifier.padding(start = 48.dp, top = 2.dp))
    }
}

@Composable
private fun AttachSheet(
    onClose: () -> Unit, onGallery: () -> Unit, onCamera: () -> Unit,
    onLocation: () -> Unit, onContact: () -> Unit, onAudio: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color(0x7314161F)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(BubbleThem)
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 34.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFD6D4E0)))
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Share", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).background(Surface).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Close", tint = Ink, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            val items = listOf(
                Triple(Icons.Filled.Image, "Gallery", Color(0xFFE7E3FF) to Color(0xFF3A20C2)) to onGallery,
                Triple(Icons.Outlined.PhotoCamera, "Camera", Color(0xFFFCE1F0) to Color(0xFF9D174D)) to onCamera,
                Triple(Icons.Filled.LocationOn, "Location", Color(0xFFD7F5EC) to Color(0xFF0F5E4A)) to onLocation,
                Triple(Icons.Filled.Person, "Contact", Color(0xFFFFF1C7) to Color(0xFF854D0E)) to onContact,
                Triple(Icons.Filled.Headphones, "Audio", Color(0xFFFFE4D6) to Color(0xFF9A3412)) to onAudio,
            )
            items.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                    row.forEach { (t, action) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = action).padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(t.third.first), contentAlignment = Alignment.Center) {
                                Icon(t.first, null, tint = t.third.second, modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(t.second, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
