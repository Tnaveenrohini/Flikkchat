package com.flikk.chat.ui

import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.flikk.chat.data.Chat
import com.flikk.chat.data.Contact
import com.flikk.chat.data.Msg
import com.flikk.chat.data.preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
private fun PickRow(name: String, sub: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(name)
            if (checked) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(Accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, null, tint = OnAccent, modifier = Modifier.size(14.dp)) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------- New group ----------------

@Composable
fun NewGroupScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(listOf<Contact>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val contacts by produceState<List<Contact>?>(initialValue = null) { value = Chat.findContacts() }

    Box(Modifier.fillMaxSize().background(Bg)) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, tr("Back"), tint = Ink, modifier = Modifier.size(20.dp)) }
                    Column {
                        Text(tr("New group"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                        Text(if (picked.isEmpty()) tr("Select members") else tr("{0} selected", picked.size), fontSize = 12.sp, color = Muted)
                    }
                }
                Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)) {
                    BoxField(tr("Group name"), name, { name = it.take(50) }, focused = name.isEmpty())
                }
                if (picked.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(picked, key = { "p" + it.uid }) { c ->
                            Column(Modifier.width(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box {
                                    Avatar(c.name, 54.dp)
                                    Box(
                                        Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Bg).padding(2.dp)
                                            .clip(CircleShape).background(Muted).clickable { picked = picked - c },
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Filled.Close, tr("Remove {0}", c.name), tint = Color.White, modifier = Modifier.size(12.dp)) }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(c.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
                error?.let { Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            }
            val list = contacts
            when {
                list == null -> item {
                    Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
                }
                list.isEmpty() -> item {
                    Text(tr("None of your contacts are on Flikkchat yet"), color = Muted, modifier = Modifier.padding(20.dp))
                }
                else -> items(list, key = { it.uid }) { c ->
                    val on = picked.any { it.uid == c.uid }
                    Row(
                        Modifier.fillMaxWidth().clickable { picked = if (on) picked.filter { it.uid != c.uid } else picked + c }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(c.name, 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                            Text(c.phone, color = Muted, fontSize = 13.sp)
                        }
                        Box(
                            Modifier.size(26.dp).clip(CircleShape)
                                .then(if (on) Modifier.background(Accent) else Modifier.border(2.dp, Line, CircleShape)),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Icons.Filled.Check, null, tint = OnAccent, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
            item { Spacer(Modifier.height(110.dp)) }
        }
        if (name.isNotBlank() && picked.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp)
            ) {
                PillButton(tr("Create group"), busy = busy) {
                    busy = true; error = null
                    scope.launch {
                        val peer = Chat.createGroup(name, picked.map { it.uid })
                        busy = false
                        if (peer != null) onCreated(peer) else error = tr("Could not create group. Check internet / Firestore rules")
                    }
                }
            }
        }
    }
}

// ---------------- Group info ----------------

@Composable
fun GroupInfoScreen(gid: String, onBack: () -> Unit, openSettings: () -> Unit = {}, openMediaLinks: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val version by Chat.db.changes.collectAsState()
    val group by produceState(initialValue = Chat.db.group(gid), version) { value = Chat.db.group(gid) }
    LaunchedEffect(gid) { withContext(Dispatchers.IO) { Chat.refreshGroup(gid) } }

    var adding by remember { mutableStateOf(false) }
    var toAdd by remember { mutableStateOf(setOf<String>()) }
    var removeWho by remember { mutableStateOf<String?>(null) }
    var askLeave by remember { mutableStateOf(false) }
    val contacts by produceState<List<Contact>?>(initialValue = null, adding) { if (adding) value = Chat.findContacts() }

    val g = group
    val me = Chat.me
    val iAmAdmin = g != null && me in g.admins
    val iAmMember = g != null && me in g.members

    if (adding && g != null) {
        Scaffold(
            containerColor = Bg,
            topBar = { Header(title = tr("Add members"), subtitle = tr("{0} selected", toAdd.size), onBack = { adding = false; toAdd = emptySet() }) },
            floatingActionButton = {
                if (toAdd.isNotEmpty()) FloatingActionButton(
                    onClick = {
                        val list = toAdd.toList()
                        adding = false; toAdd = emptySet()
                        scope.launch { Chat.addMembers(gid, list) }
                    },
                    containerColor = Accent, contentColor = OnAccent,
                ) { Icon(Icons.Filled.Check, tr("Add")) }
            },
        ) { pad ->
            val list = contacts?.filter { it.uid !in g.members }
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                when {
                    list == null -> item {
                        Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
                    }
                    list.isEmpty() -> item { Text(tr("No more contacts to add"), color = Muted, modifier = Modifier.padding(16.dp)) }
                    else -> items(list, key = { it.uid }) { c ->
                        PickRow(c.name, c.phone, c.uid in toAdd) {
                            toAdd = if (c.uid in toAdd) toAdd - c.uid else toAdd + c.uid
                        }
                    }
                }
            }
        }
        return
    }

    val media by produceState(initialValue = emptyList<Msg>(), version) {
        value = withContext(Dispatchers.IO) {
            Chat.db.messages("g:$gid").filter { it.type == "image" && it.media.isNotEmpty() && !it.deleted }.takeLast(4).reversed()
        }
    }
    if (g == null) {
        Box(Modifier.fillMaxSize().background(ChatBg), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
        return
    }
    LazyColumn(Modifier.fillMaxSize().background(ChatBg)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 12.dp, top = 14.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBackIos, tr("Back"), tint = Ink, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(g.name, 104.dp, rounded = 36.dp)
                Spacer(Modifier.height(14.dp))
                Text(g.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = Ink)
                Text(tr("Group · {0} members", g.members.size), color = Muted, fontSize = 14.sp)
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoTile(Icons.AutoMirrored.Filled.Chat, tr("Message"), Modifier.weight(1f), onClick = onBack)
                if (iAmAdmin) InfoTile(Icons.Filled.PersonAdd, tr("Add"), Modifier.weight(1f)) { adding = true }
                if (iAmMember) InfoTile(Icons.Filled.Settings, tr("Settings"), Modifier.weight(1f), onClick = openSettings)
                if (iAmMember) InfoTile(Icons.AutoMirrored.Filled.ExitToApp, tr("Exit"), Modifier.weight(1f), tint = Danger) { askLeave = true }
            }
            if (media.isNotEmpty()) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(Bg).padding(14.dp),
                ) {
                    Text(tr("Media, links & docs"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink,
                        modifier = Modifier.clickable(onClick = openMediaLinks))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        media.forEach { m ->
                            AsyncImage(
                                model = File(m.media), contentDescription = tr("Photo"), contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).height(70.dp).clip(RoundedCornerShape(12.dp)),
                            )
                        }
                        repeat(4 - media.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Bg).padding(vertical = 6.dp)) {
                Text(tr("{0} members", g.members.size), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
                g.members.sortedBy { if (it == me) "" else Chat.nameOf(it).lowercase() }.forEach { uid ->
                    val n = if (uid == me) tr("You") else Chat.nameOf(uid)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = iAmAdmin && uid != me) { removeWho = uid }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(if (uid == me) Chat.myName else n, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                            Chat.db.peer(uid)?.phone?.takeIf { it.isNotEmpty() && uid != me }?.let { Text(it, color = Muted, fontSize = 12.sp) }
                        }
                        if (uid in g.admins) {
                            Text(tr("Admin"), color = AccentDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(AccentSoft).padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.navigationBarsPadding())
        }
    }

    removeWho?.let { uid ->
        AlertDialog(
            onDismissRequest = { removeWho = null },
            title = { Text(tr("Remove {0}?", Chat.nameOf(uid))) },
            confirmButton = {
                TextButton(onClick = { removeWho = null; scope.launch { Chat.removeMember(gid, uid) } }) { Text(tr("Remove"), color = Accent) }
            },
            dismissButton = { TextButton(onClick = { removeWho = null }) { Text(tr("Cancel"), color = Muted) } },
            containerColor = Surface,
        )
    }
    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text(tr("Exit \"{0}\"?", g?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = { askLeave = false; scope.launch { Chat.leaveGroup(gid) } }) { Text(tr("Exit"), color = Accent) }
            },
            dismissButton = { TextButton(onClick = { askLeave = false }) { Text(tr("Cancel"), color = Muted) } },
            containerColor = Surface,
        )
    }
}

@Composable
private fun InfoTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier,
    tint: Color = Accent, onClick: () -> Unit,
) {
    Column(
        modifier.height(72.dp).clip(RoundedCornerShape(18.dp)).background(Bg).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

// ---------------- Forward ----------------

private data class Target(val peer: String, val name: String, val sub: String)

@Composable
fun ForwardScreen(msgId: String, onDone: () -> Unit) {
    var picked by remember { mutableStateOf(listOf<String>()) }
    var q by remember { mutableStateOf("") }
    val targets by produceState(initialValue = emptyList<Target>()) {
        value = withContext(Dispatchers.IO) {
            val chats = Chat.db.chats()
                .filter { !it.peer.startsWith("g:") || (Chat.db.group(it.peer.removePrefix("g:"))?.members?.contains(Chat.me) == true) }
                .map { Target(it.peer, it.name, if (it.peer.startsWith("g:")) tr("Group") else it.phone) }
            val seen = chats.map { it.peer }.toSet()
            chats + Chat.db.people().filter { it.uid !in seen }.map { Target(it.uid, it.name, it.phone) }
        }
    }
    val shown = targets.filter { q.isBlank() || it.name.contains(q.trim(), true) }
    Box(Modifier.fillMaxSize().background(Bg)) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, tr("Back"), tint = Ink, modifier = Modifier.size(20.dp)) }
                    Text(tr("Forward to…"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                }
                Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)) { SearchPill(q, { q = it }, tr("Search")) }
                SectionLabel(tr("Recent chats"))
            }
            items(shown, key = { it.peer }) { t ->
                val on = t.peer in picked
                Row(
                    Modifier.fillMaxWidth().clickable { picked = if (on) picked - t.peer else picked + t.peer }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(t.name, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                        if (t.sub.isNotEmpty()) Text(t.sub, color = Muted, fontSize = 13.sp)
                    }
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).then(if (on) Modifier.background(Accent) else Modifier.border(2.dp, Line, CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Icon(Icons.Filled.Check, null, tint = OnAccent, modifier = Modifier.size(16.dp)) }
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
        if (picked.isNotEmpty()) Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(BubbleThem).navigationBarsPadding()
                .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(picked.joinToString(", ") { p -> targets.firstOrNull { it.peer == p }?.name ?: "" }, color = Ink, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Accent).clickable {
                    Chat.db.msg(msgId)?.let { Chat.forward(it, picked) }
                    onDone()
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.Send, tr("Send"), tint = Color.White) }
        }
    }
}

// ---------------- Starred ----------------

@Composable
fun StarredScreen(onBack: () -> Unit, openChat: (String) -> Unit) {
    val version by Chat.db.changes.collectAsState()
    val list by produceState(initialValue = emptyList<Msg>(), version) {
        value = withContext(Dispatchers.IO) { Chat.db.starred() }
    }
    Column(Modifier.fillMaxSize().background(ChatBg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 16.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, tr("Back"), tint = Ink, modifier = Modifier.size(20.dp)) }
            Text(tr("Starred"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        }
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⭐", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text(tr("Long-press a message and tap Star to save it here."), color = Muted, textAlign = TextAlign.Center)
            }
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            items(list, key = { it.id }) { m ->
                val who = if (m.fromMe) tr("You") else Chat.nameOf(m.sender.ifEmpty { m.peer })
                val chatName = Chat.db.peer(m.peer)?.name ?: ""
                Row(
                    Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(BubbleThem)
                        .clickable { openChat(m.peer) }.padding(14.dp),
                ) {
                    Avatar(if (m.fromMe) Chat.myName else who, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (m.peer.startsWith("g:")) "$who › $chatName" else who, fontWeight = FontWeight.Bold, color = Ink,
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listTime(m.ts), fontSize = 12.sp, color = Muted)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(preview(m), color = DayInk, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Filled.Star, null, tint = Color(0xFFE3A008), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ---------------- Image viewer ----------------

@Composable
fun ImageViewer(path: String, onBack: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val tState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = File(path), contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
                .transformable(tState)
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        )
        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back"), tint = Color.White)
        }
    }
}
