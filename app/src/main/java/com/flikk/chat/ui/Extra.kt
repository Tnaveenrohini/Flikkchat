package com.flikk.chat.ui

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.flikk.chat.data.Backup
import com.flikk.chat.data.Chat
import com.flikk.chat.data.ChatRow
import com.flikk.chat.data.Msg
import com.flikk.chat.data.gidOf
import com.flikk.chat.data.isGroup
import com.flikk.chat.data.preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- Message info ----------------

@Composable
fun MessageInfoScreen(msgId: String, onBack: () -> Unit) {
    val version by Chat.db.changes.collectAsState()
    val m = remember(version) { Chat.db.msg(msgId) }
    val rc = remember(version) { Chat.db.receipts(msgId) }
    SubPage(tr("Message info"), onBack) {
        if (m == null) return@SubPage
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier.clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)).background(BubbleMine).padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(preview(m), color = Color.White, fontSize = 15.sp)
                Text(clock(m.ts), color = OnAccentMuted, fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
            }
        }
        val read = rc.filter { it.second >= 3 }
        val delivered = rc.filter { it.second == 2 }
        listOf(tr("Read by") to read, tr("Delivered to") to delivered).forEach { (title, people) ->
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(BubbleThem).padding(vertical = 8.dp),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DoneAll, null, tint = if (title == tr("Read by")) Accent else Muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                if (people.isEmpty()) Text(tr("No one yet"), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                people.forEach { (uid, _, ts) ->
                    val n = Chat.nameOf(uid)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(n, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(n, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                        Text(if (ts > 0) longTime(ts) else "", fontSize = 12.sp, color = Muted)
                    }
                }
            }
        }
    }
}

// ---------------- Search ----------------

@Composable
fun SearchScreen(onBack: () -> Unit, openChat: (String) -> Unit, openMedia: (String, String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var chip by remember { mutableIntStateOf(0) }
    val chips = listOf(tr("All"), tr("Photos"), tr("Videos"), tr("Links"), tr("Audio"))
    val version by Chat.db.changes.collectAsState()
    val chats by produceState(initialValue = emptyList<ChatRow>(), q, version) {
        value = if (q.isBlank()) emptyList() else withContext(Dispatchers.IO) { Chat.db.chats().filter { it.name.contains(q.trim(), true) } }
    }
    val results by produceState(initialValue = emptyList<Msg>(), q, chip, version) {
        value = withContext(Dispatchers.IO) {
            when (chip) {
                1 -> Chat.db.searchType("image")
                2 -> Chat.db.searchType("video")
                3 -> Chat.db.search("http").filter { it.text.contains("http://") || it.text.contains("https://") }
                4 -> Chat.db.searchType("audio")
                else -> if (q.isBlank()) emptyList() else Chat.db.search(q.trim())
            }.filter { q.isBlank() || chip == 0 || it.text.contains(q.trim(), true) || (Chat.db.peer(it.peer)?.name ?: "").contains(q.trim(), true) }
        }
    }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 20.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, tr("Back"), tint = Ink, modifier = Modifier.size(20.dp)) }
            Box(Modifier.weight(1f)) { SearchPill(q, { q = it }, tr("Search")) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            itemsIndexed(chips) { i, c ->
                Box(
                    Modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
                        .then(if (chip == i) Modifier.background(Accent) else Modifier.border(1.dp, Line, RoundedCornerShape(17.dp)))
                        .clickable { chip = i }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(c, color = if (chip == i) OnAccent else Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (chats.isNotEmpty()) item { SectionLabel(tr("Chats")) }
            items(chats, key = { "c" + it.peer }) { c -> ChatRowItem(c) { openChat(c.peer) } }
            if (results.isNotEmpty()) item { SectionLabel(if (chip == 0) tr("Messages") else chips[chip]) }
            if (chip == 1 || chip == 2) {
                item {
                    val rows = results.filter { it.media.isNotEmpty() }.chunked(3)
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        rows.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { m -> MediaTile(m, Modifier.weight(1f)) { openMedia(m.peer, m.id) } }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            } else items(results, key = { "m" + it.id }) { m ->
                Column(Modifier.fillMaxWidth().clickable { openChat(m.peer) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Row {
                        Text(Chat.db.peer(m.peer)?.name ?: "", fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                        Text(listTime(m.ts), fontSize = 12.sp, color = Muted)
                    }
                    Text(highlight(preview(m), q.trim()), color = Muted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (q.isNotBlank() && chats.isEmpty() && results.isEmpty()) item {
                Text(tr("No results for \"{0}\"", q), color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
            }
        }
    }
}

private fun highlight(text: String, q: String) = buildAnnotatedString {
    if (q.isEmpty()) { append(text); return@buildAnnotatedString }
    var i = 0
    while (true) {
        val j = text.indexOf(q, i, ignoreCase = true)
        if (j < 0) { append(text.substring(i)); break }
        append(text.substring(i, j))
        withStyle(SpanStyle(color = Accent, fontWeight = FontWeight.Bold)) { append(text.substring(j, j + q.length)) }
        i = j + q.length
    }
}

@Composable
fun MediaTile(m: Msg, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2340)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        AsyncImage(model = File(m.media), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (m.type == "video") {
            Icon(Icons.Filled.PlayArrow, tr("Video"), tint = Color.White, modifier = Modifier.size(30.dp))
            if (m.dur > 0) Text(duration(m.dur), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
        }
    }
}

// ---------------- Create poll ----------------

@Composable
fun PollCreateScreen(peer: String, onDone: () -> Unit) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var multi by remember { mutableStateOf(false) }
    val valid = question.isNotBlank() && options.count { it.isNotBlank() } >= 2
    SubPage(tr("Create poll"), onDone) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            BoxField(tr("Question"), question, { question = it.take(200) }, focused = question.isEmpty())
            Spacer(Modifier.height(18.dp))
            Text(tr("Options"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DayInk)
            Spacer(Modifier.height(8.dp))
            options.forEachIndexed { i, o ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Box(Modifier.weight(1f)) { BoxField("", o, { options[i] = it.take(80) }) }
                    if (options.size > 2) IconButton(onClick = { options.removeAt(i) }) { Icon(Icons.Filled.Close, tr("Remove"), tint = Muted) }
                }
                Text(tr("Option {0}", i + 1), fontSize = 11.sp, color = Muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            }
            if (options.size < 10) Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { options.add("") }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, null, tint = Accent)
                Spacer(Modifier.width(6.dp))
                Text(tr("Add option"), color = Accent, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))
        Card { ToggleRow(tr("Allow multiple answers"), "", multi) { multi = it } }
        Box(Modifier.padding(horizontal = 16.dp)) {
            PillButton(tr("Send poll"), enabled = valid) {
                Chat.sendPoll(peer, question, options.toList(), multi)
                onDone()
            }
        }
    }
}

// ---------------- Photo / video send preview ----------------

@Composable
fun SendPreviewScreen(peer: String, uri: Uri, isVideo: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }
    var hd by remember { mutableStateOf(false) }
    var viewOnce by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val name = Chat.db.peer(peer)?.name ?: ""
    Column(Modifier.fillMaxSize().background(NavBg).imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, tr("Close"), tint = Color.White) }
            Spacer(Modifier.weight(1f))
            if (!isVideo) PreviewChip(Icons.Filled.Hd, "HD", hd) { hd = !hd }
            Spacer(Modifier.width(8.dp))
            PreviewChip(Icons.Outlined.Timer, tr("View once"), viewOnce) { viewOnce = !viewOnce }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            if (isVideo) Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp)).background(Color(0xFF2A2D3A)).padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (caption.isEmpty()) Text(tr("Caption"), color = NavMuted, fontSize = 15.sp)
                BasicTextField(
                    caption, { caption = it.take(500) }, singleLine = true,
                    textStyle = TextStyle(fontFamily = Jakarta, fontSize = 15.sp, color = Color.White),
                    cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(LightPalette.accent).clickable(enabled = !busy) {
                    busy = true
                    scope.launch {
                        val err = Chat.sendMedia(peer, uri, isVideo, caption, null, hd = hd, viewOnce = viewOnce)
                        busy = false
                        if (err != null) Toast.makeText(ctx, tr(err), Toast.LENGTH_LONG).show() else onDone()
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Filled.Send, tr("Send"), tint = Color.White)
            }
        }
        if (name.isNotEmpty()) Text("→ $name", color = NavMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 24.dp, bottom = 10.dp))
    }
}

@Composable
private fun PreviewChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) Color.White else Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) NavBg else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (on) NavBg else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------- Media viewer ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(peer: String, msgId: String, onBack: () -> Unit, openForward: (String) -> Unit) {
    val ctx = LocalContext.current
    val version by Chat.db.changes.collectAsState()
    val all = remember(version) { Chat.db.media(peer) }
    if (all.isEmpty()) { LaunchedEffect(Unit) { onBack() }; return }
    val start = all.indexOfFirst { it.id == msgId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { all.size }
    val scope = rememberCoroutineScope()
    val cur = all[pager.currentPage.coerceIn(0, all.lastIndex)]
    var askDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, tr("Back"), tint = Color.White, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text(if (cur.fromMe) tr("You") else Chat.nameOf(cur.sender.ifEmpty { cur.peer }), color = Color.White, fontWeight = FontWeight.Bold)
                Text(longTime(cur.ts), color = NavMuted, fontSize = 12.sp)
            }
            IconButton(onClick = { Chat.star(cur) }) { Icon(if (cur.starred) Icons.Filled.Star else Icons.Filled.StarBorder, tr("Star"), tint = Color.White) }
            IconButton(onClick = { openForward(cur.id) }) { Icon(Icons.AutoMirrored.Filled.Shortcut, tr("Forward"), tint = Color.White) }
        }
        HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth()) { i ->
            val m = all[i]
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(model = File(m.media), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                    .clickable(enabled = m.type == "video") { openExternal(ctx, m) })
                if (m.type == "video") Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, tr("Play video"), tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }
        if (cur.text.isNotBlank()) Text(cur.text, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            itemsIndexed(all, key = { _, m -> m.id }) { i, m ->
                AsyncImage(
                    model = File(m.media), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                        .border(2.dp, if (i == pager.currentPage) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { scope.launch { pager.animateScrollToPage(i) } },
                )
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ViewerAction(Icons.Filled.Share, tr("Share")) { shareFile(ctx, cur) }
            ViewerAction(Icons.Filled.Download, tr("Save")) {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { saveToGallery(ctx, cur) }
                    Toast.makeText(ctx, if (ok) tr("Saved to gallery") else tr("Could not save"), Toast.LENGTH_SHORT).show()
                }
            }
            ViewerAction(Icons.Filled.Delete, tr("Delete")) { askDelete = true }
        }
    }
    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        title = { Text(tr("Delete for me?")) },
        confirmButton = { TextButton(onClick = { askDelete = false; Chat.deleteForMe(cur) }) { Text(tr("Delete"), color = Danger) } },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text(tr("Cancel"), color = Muted) } },
        containerColor = BubbleThem,
    )
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

private fun fileUri(ctx: android.content.Context, m: Msg) = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(m.media))

private fun openExternal(ctx: android.content.Context, m: Msg) = try {
    ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(fileUri(ctx, m), m.mime.ifEmpty { "video/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
} catch (_: Exception) {}

private fun shareFile(ctx: android.content.Context, m: Msg) = try {
    ctx.startActivity(Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType(m.mime.ifEmpty { "image/*" }).putExtra(Intent.EXTRA_STREAM, fileUri(ctx, m))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), tr("Share")))
} catch (_: Exception) {}

private fun saveToGallery(ctx: android.content.Context, m: Msg): Boolean = try {
    val video = m.type == "video"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "Flikkchat_" + File(m.media).name)
        put(MediaStore.MediaColumns.MIME_TYPE, m.mime.ifEmpty { if (video) "video/mp4" else "image/jpeg" })
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/Flikkchat")
    }
    val col = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val uri = ctx.contentResolver.insert(col, values)
    if (uri != null) {
        ctx.contentResolver.openOutputStream(uri)?.use { out -> File(m.media).inputStream().use { it.copyTo(out) } }
        true
    } else false
} catch (_: Exception) { false }

// ---------------- Media, links & docs ----------------

@Composable
fun MediaLinksScreen(peer: String, onBack: () -> Unit, openMedia: (String) -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val version by Chat.db.changes.collectAsState()
    val media = remember(version) { Chat.db.media(peer).reversed() }
    val links = remember(version) { Chat.db.withLinks(peer) }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, tr("Back"), tint = Ink, modifier = Modifier.size(20.dp)) }
            Text(Chat.db.peer(peer)?.name ?: "", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(Surface).padding(4.dp)) {
            listOf(tr("Media"), tr("Links"), tr("Docs")).forEachIndexed { i, t ->
                Box(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(11.dp)).background(if (tab == i) BubbleThem else Color.Transparent)
                        .clickable { tab = i },
                    contentAlignment = Alignment.Center,
                ) { Text(t, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (tab == i) Ink else Muted) }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> if (media.isEmpty()) Empty(tr("No photos or videos yet")) else LazyVerticalGrid(
                GridCells.Fixed(3), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            ) { items(media, key = { it.id }) { m -> MediaTile(m, Modifier) { openMedia(m.id) } } }
            1 -> if (links.isEmpty()) Empty(tr("No links yet")) else LazyColumn {
                items(links, key = { it.id }) { m ->
                    val url = Regex("https?://\\S+").find(m.text)?.value ?: ""
                    Column(Modifier.fillMaxWidth().clickable {
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
                    }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(url, color = Accent, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listTime(m.ts), color = Muted, fontSize = 12.sp)
                    }
                }
            }
            else -> Empty(tr("Documents are not supported yet"))
        }
    }
}

@Composable
private fun Empty(t: String) = Text(t, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp))

// ---------------- Archived ----------------

@Composable
fun ArchivedScreen(onBack: () -> Unit, openChat: (String) -> Unit) {
    val version by Chat.db.changes.collectAsState()
    val chats by produceState(initialValue = emptyList<ChatRow>(), version) {
        value = withContext(Dispatchers.IO) { Chat.db.chats().filter { Chat.isArchived(it.peer) && !Chat.isLocked(it.peer) } }
    }
    var menuFor by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Header(tr("Archived"), onBack = onBack)
        Text(tr("These chats stay archived even when new messages arrive."), color = Muted, fontSize = 13.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
        LazyColumn {
            items(chats, key = { it.peer }) { c -> ChatRowItem(c, onLong = { menuFor = c.peer }) { openChat(c.peer) } }
        }
        if (chats.isEmpty()) Empty(tr("No archived chats"))
    }
    menuFor?.let { p ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(Chat.db.peer(p)?.name ?: "") },
            confirmButton = { TextButton(onClick = { Chat.setArchived(p, false); menuFor = null }) { Text(tr("Unarchive"), color = Accent) } },
            dismissButton = { TextButton(onClick = { menuFor = null }) { Text(tr("Cancel"), color = Muted) } },
            containerColor = BubbleThem,
        )
    }
}

// ---------------- Locked chats ----------------

@Composable
fun LockedChatsScreen(onBack: () -> Unit, openChat: (String) -> Unit, authenticate: (() -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { authenticate { open = true } }
    val version by Chat.db.changes.collectAsState()
    val chats by produceState(initialValue = emptyList<ChatRow>(), version) {
        value = withContext(Dispatchers.IO) { Chat.db.chats().filter { Chat.isLocked(it.peer) } }
    }
    var menuFor by remember { mutableStateOf<String?>(null) }
    if (!open) {
        Column(
            Modifier.fillMaxSize().background(Bg).statusBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(AccentSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Fingerprint, null, tint = Accent, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(tr("Locked chats"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(tr("Use your fingerprint or screen lock to open."), color = Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            PillButton(tr("Unlock")) { authenticate { open = true } }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onBack) { Text(tr("Cancel"), color = Muted) }
        }
        return
    }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Header(tr("Locked chats"), onBack = onBack)
        LazyColumn { items(chats, key = { it.peer }) { c -> ChatRowItem(c, onLong = { menuFor = c.peer }) { openChat(c.peer) } } }
        if (chats.isEmpty()) Empty(tr("No locked chats. Long-press a chat to lock it."))
    }
    menuFor?.let { p ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(Chat.db.peer(p)?.name ?: "") },
            confirmButton = { TextButton(onClick = { Chat.setLocked(p, false); menuFor = null }) { Text(tr("Unlock chat"), color = Accent) } },
            dismissButton = { TextButton(onClick = { menuFor = null }) { Text(tr("Cancel"), color = Muted) } },
            containerColor = BubbleThem,
        )
    }
}

// ---------------- Chat backup ----------------

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var askRestore by remember { mutableStateOf(false) }
    var videos by remember { mutableStateOf(Chat.flag("backup_videos", true)) }
    var at by remember { mutableStateOf(Chat.lastBackupAt) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            busy = true; msg = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { Backup.export(ctx, uri, videos) }
                busy = false; at = Chat.lastBackupAt
                msg = r.fold({ tr("Backup ready ✅ ({0} media files)", it) }, { tr("Backup failed: {0}", it.message ?: "") })
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true; msg = tr("Restoring…")
            scope.launch {
                val r = withContext(Dispatchers.IO) { Backup.restore(ctx, uri) }
                busy = false
                r.fold({ Backup.restartApp(ctx) }, { msg = tr("Restore failed: {0}", it.message ?: "") })
            }
        }
    }
    SubPage(tr("Chat backup"), onBack) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(BubbleThem).padding(18.dp),
        ) {
            Text(tr("Last backup"), fontSize = 13.sp, color = Muted)
            Text(if (at > 0) longTime(at) else tr("Never"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            if (at > 0) Text(tr("Size: {0}", fmtSize(Chat.lastBackupSize)), fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(12.dp))
            Text(tr("Your chats stay only on this phone. Back up to Google Drive (pick it in the file picker) so you can restore on a new phone."),
                fontSize = 13.sp, lineHeight = 19.sp, color = Muted)
            Spacer(Modifier.height(16.dp))
            PillButton(if (busy) tr("Backing up…") else tr("Back up now"), busy = busy) {
                exportLauncher.launch("Flikkchat-backup-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".zip")
            }
            msg?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Accent, fontSize = 13.sp) }
        }
        Card {
            ToggleRow(tr("Include videos"), tr("Videos make the backup much bigger"), videos) { videos = it; Chat.setFlag("backup_videos", it) }
            LinkRow(tr("Restore from backup"), sub = tr("Current chats will be replaced")) { if (!busy) askRestore = true }
        }
    }
    if (askRestore) AlertDialog(
        onDismissRequest = { askRestore = false },
        title = { Text(tr("Restore backup?")) },
        text = { Text(tr("Chats on this phone will be replaced with the backup. The app will restart."), color = Muted) },
        confirmButton = {
            TextButton(onClick = { askRestore = false; importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                Text(tr("Choose file"), color = Accent)
            }
        },
        dismissButton = { TextButton(onClick = { askRestore = false }) { Text(tr("Cancel"), color = Muted) } },
        containerColor = BubbleThem,
    )
}

fun fmtSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%d KB".format(b / (1L shl 10))
    else -> "$b B"
}

// ---------------- Chat theme & wallpaper ----------------

val BubbleColors = listOf(0L, 0xFF0F5E4AL, 0xFF9D174DL, 0xFF9A3412L, 0xFF1E4E8CL, 0xFF14161FL)
val WallColors = listOf(0L, 0xFFE9E4FFL, 0xFFDDF3EAL, 0xFFFCE7F1L, 0xFFFDEBDDL, 0xFFE3EDFBL, 0xFF2A2340L, 0xFF14161FL)

@Composable
fun ChatThemeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version by Chat.db.changes.collectAsState()
    val bubble = remember(version) { Chat.bubbleColor }
    val wall = remember(version) { Chat.wallpaper }
    val font = remember(version) { Chat.fontSize }
    var mode by remember { mutableStateOf(Chat.themeMode) }
    val bubbleC = if (bubble == 0L) BubbleMine else Color(bubble)
    val wallC = if (wall == 0L) ChatBg else Color(wall)
    SubPage(tr("Chat theme"), onBack) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth().height(170.dp).clip(RoundedCornerShape(22.dp))
                .background(wallC).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(tr("Does this theme look good?"), fontSize = fontSp(font), color = Ink,
                modifier = Modifier.clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)).background(BubbleThem).padding(horizontal = 14.dp, vertical = 10.dp))
            Text(tr("Looks great!"), fontSize = fontSp(font), color = Color.White,
                modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)).background(bubbleC).padding(horizontal = 14.dp, vertical = 10.dp))
        }
        Caps(tr("Bubble colour"))
        Swatches(BubbleColors, bubble) { Chat.bubbleColor = it }
        Caps(tr("Wallpaper"))
        Swatches(WallColors, wall) { Chat.wallpaper = it }
        Caps(tr("App theme"))
        Card {
            listOf(0 to tr("System default"), 1 to tr("Light"), 2 to tr("Dark")).forEach { (v, label) ->
                LinkRow(label, if (mode == v) "✓" else "") { mode = v; Chat.themeMode = v; applyTheme(ctx) }
            }
        }
        Caps(tr("Theme colour"))
        AccentSwatches(Modifier.padding(horizontal = 16.dp))
        Caps(tr("Font size"))
        Card {
            listOf(0 to tr("Small"), 1 to tr("Medium"), 2 to tr("Large")).forEach { (v, label) ->
                LinkRow(label, if (font == v) "✓" else "") { Chat.fontSize = v }
            }
        }
    }
}

fun fontSp(size: Int) = when (size) { 0 -> 13.sp; 2 -> 18.sp; else -> 15.sp }

@Composable
private fun Swatches(colors: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) {
        items(colors) { c ->
            val col = if (c == 0L) (if (colors === BubbleColors) Accent else ChatBg) else Color(c)
            Box(
                Modifier.size(46.dp).border(3.dp, if (c == selected) Accent else Color.Transparent, CircleShape).padding(5.dp)
                    .clip(CircleShape).background(col).border(1.dp, Line, CircleShape).clickable { onPick(c) },
            )
        }
    }
}

// ---------------- Group settings ----------------

@Composable
fun GroupSettingsScreen(gid: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by Chat.db.changes.collectAsState()
    val g = remember(version) { Chat.db.group(gid) } ?: return
    val iAmAdmin = Chat.me in g.admins
    var rename by remember { mutableStateOf<String?>(null) }
    fun set(flag: Char, adminsOnly: Boolean) {
        val f = g.flags.filter { it != flag } + (if (adminsOnly) flag.toString() else "")
        scope.launch { if (!Chat.setGroupFlags(gid, f)) Toast.makeText(ctx, tr("Only admins can change this"), Toast.LENGTH_SHORT).show() }
    }
    SubPage(tr("Group settings"), onBack) {
        Text(g.name + if (!iAmAdmin) " · " + tr("admins only") else "", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 28.dp, bottom = 12.dp))
        Caps(tr("Members can"))
        Card {
            ToggleRow(tr("Edit group info"), tr("Name"), !g.adminsOnlyEdit) { if (iAmAdmin) set('e', !it) }
            ToggleRow(tr("Send messages"), tr("When off, only admins can send"), !g.adminsOnlySend) { if (iAmAdmin) set('s', !it) }
            ToggleRow(tr("Add other members"), "", !g.adminsOnlyAdd) { if (iAmAdmin) set('a', !it) }
        }
        if (iAmAdmin || !g.adminsOnlyEdit) Card { LinkRow(tr("Change group name"), g.name) { rename = g.name } }
        Caps(tr("Admins"))
        Card {
            g.admins.forEach { uid ->
                val n = if (uid == Chat.me) tr("You") else Chat.nameOf(uid)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(if (uid == Chat.me) Chat.myName else n, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(n, fontWeight = FontWeight.Bold, color = Ink)
                }
            }
        }
    }
    rename?.let { cur ->
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text(tr("Group name")) },
            text = { BoxField("", cur, { rename = it.take(50) }, focused = true) },
            confirmButton = {
                TextButton(onClick = {
                    val n = cur; rename = null
                    scope.launch { if (!Chat.renameGroup(gid, n)) Toast.makeText(ctx, tr("Could not rename group"), Toast.LENGTH_SHORT).show() }
                }, enabled = cur.isNotBlank()) { Text(tr("Save"), color = Accent) }
            },
            dismissButton = { TextButton(onClick = { rename = null }) { Text(tr("Cancel"), color = Muted) } },
            containerColor = BubbleThem,
        )
    }
}

// ---------------- Share location ----------------

@Composable
fun ShareLocationScreen(peer: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loc by remember { mutableStateOf<android.location.Location?>(null) }
    var loading by remember { mutableStateOf(true) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) scope.launch { loc = Chat.currentLocation(); loading = false }
        else { loading = false; Toast.makeText(ctx, tr("Location permission needed"), Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(Unit) { perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    Column(Modifier.fillMaxSize().background(ChatBg)) {
        Header(tr("Share location"), onBack = onDone)
        Box(
            Modifier.padding(16.dp).fillMaxWidth().height(260.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFDDE7DD)),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) CircularProgressIndicator(color = Accent)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.LocationOn, null, tint = Danger, modifier = Modifier.size(48.dp))
                val l = loc
                Text(
                    if (l != null) "%.5f, %.5f".format(Locale.US, l.latitude, l.longitude) else tr("Location not found. Turn on GPS."),
                    color = Color(0xFF14161F), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                if (l != null) TextButton(onClick = {
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${l.latitude},${l.longitude}?q=${l.latitude},${l.longitude}"))) } catch (_: Exception) {}
                }) { Text(tr("Open in maps"), color = Accent) }
            }
        }
        Card {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = loc != null) {
                    val l = loc ?: return@clickable
                    val lat = "%.6f".format(Locale.US, l.latitude); val lng = "%.6f".format(Locale.US, l.longitude)
                    Chat.send(peer, "📍 " + tr("My location") + "\nhttps://maps.google.com/?q=$lat,$lng")
                    onDone()
                }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MyLocation, null, tint = OnAccent)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(tr("Send current location"), fontWeight = FontWeight.Bold, color = Ink)
                    loc?.let { Text(tr("Accurate to {0} metres", it.accuracy.toInt()), fontSize = 12.sp, color = Muted) }
                }
            }
        }
        Card {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, null, tint = Muted)
                Spacer(Modifier.width(12.dp))
                Text(tr("Live location needs a server, so it is not available yet."), fontSize = 13.sp, color = Muted)
            }
        }
    }
}

// ---------------- Two-step verification ----------------

@Composable
fun TwoStepScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(if (Chat.hasPin) 2 else 0) }
    SubPage(tr("Two-step verification"), onBack) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            if (step == 2) {
                Icon(Icons.Filled.Lock, null, tint = Accent, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text(tr("Two-step verification is on"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Spacer(Modifier.height(8.dp))
                Text(tr("Anyone who registers your number on a new phone will need this PIN."), color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
                PillButton(tr("Change PIN")) { pin = ""; confirm = ""; step = 0 }
                Spacer(Modifier.height(10.dp))
                PillButton(tr("Turn off"), bg = Surface, fg = Danger) {
                    scope.launch {
                        if (Chat.setPin(null)) { step = 0; Toast.makeText(ctx, tr("Turned off"), Toast.LENGTH_SHORT).show() }
                        else Toast.makeText(ctx, tr("Network problem. Try again."), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Text(if (step == 0) tr("Create a 6-digit PIN") else tr("Confirm your PIN"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Spacer(Modifier.height(8.dp))
                Text(tr("We will ask for this PIN when your number is registered on a new phone. Extra security for your account."),
                    color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(24.dp))
                val v = if (step == 0) pin else confirm
                BoxField("", v, { t -> val d = t.filter { it.isDigit() }.take(6); if (step == 0) pin = d else confirm = d },
                    focused = true, keyboard = KeyboardType.NumberPassword, big = true)
                Spacer(Modifier.height(24.dp))
                PillButton(tr("Next"), enabled = v.length == 6) {
                    if (step == 0) step = 1
                    else if (confirm != pin) { Toast.makeText(ctx, tr("PINs do not match"), Toast.LENGTH_SHORT).show(); confirm = "" }
                    else scope.launch {
                        if (Chat.setPin(pin)) { step = 2; Toast.makeText(ctx, tr("PIN saved ✅"), Toast.LENGTH_SHORT).show() }
                        else Toast.makeText(ctx, tr("Network problem. Try again."), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

// ---------------- Language ----------------

@Composable
fun LanguageScreen(onBack: () -> Unit) {
    SubPage(tr("App language"), onBack) {
        Card {
            Languages.forEach { (code, label) ->
                LinkRow(label, if (appLang == code) "✓" else "") {
                    Chat.language = code
                    appLang = code
                }
            }
        }
        Text(tr("English is the default. Chats are not translated, only the app screens."),
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 28.dp))
    }
}

// ---------------- Groups helper ----------------
fun groupOf(peer: String) = if (isGroup(peer)) Chat.db.group(gidOf(peer)) else null
