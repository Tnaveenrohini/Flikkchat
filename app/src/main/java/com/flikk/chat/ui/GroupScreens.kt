package com.flikk.chat.ui

import androidx.compose.foundation.background
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
                ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
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
    var picked by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val contacts by produceState<List<Contact>?>(initialValue = null) { value = Chat.findContacts() }

    Scaffold(
        containerColor = Bg,
        topBar = {
            Header(title = "New group", subtitle = if (picked.isEmpty()) "Members select cheyyandi" else "${picked.size} selected", onBack = onBack)
        },
        floatingActionButton = {
            if (name.isNotBlank() && picked.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        if (busy) return@FloatingActionButton
                        busy = true; error = null
                        scope.launch {
                            val peer = Chat.createGroup(name, picked.toList())
                            busy = false
                            if (peer != null) onCreated(peer) else error = "Group create avvaledu. Internet / Firestore rules check cheyyandi"
                        }
                    },
                    containerColor = Accent, contentColor = Color.White,
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Check, "Create")
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(50) },
                    label = { Text("Group name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp), colors = fieldColors(),
                )
                error?.let { Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
                SectionLabel("Contacts")
            }
            val list = contacts
            when {
                list == null -> item {
                    Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
                }
                list.isEmpty() -> item {
                    Text("Flikkchat lo inka contacts levu", color = Muted, modifier = Modifier.padding(16.dp))
                }
                else -> items(list, key = { it.uid }) { c ->
                    PickRow(c.name, c.phone, c.uid in picked) {
                        picked = if (c.uid in picked) picked - c.uid else picked + c.uid
                    }
                }
            }
        }
    }
}

// ---------------- Group info ----------------

@Composable
fun GroupInfoScreen(gid: String, onBack: () -> Unit) {
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
            topBar = { Header(title = "Add members", subtitle = "${toAdd.size} selected", onBack = { adding = false; toAdd = emptySet() }) },
            floatingActionButton = {
                if (toAdd.isNotEmpty()) FloatingActionButton(
                    onClick = {
                        val list = toAdd.toList()
                        adding = false; toAdd = emptySet()
                        scope.launch { Chat.addMembers(gid, list) }
                    },
                    containerColor = Accent, contentColor = Color.White,
                ) { Icon(Icons.Filled.Check, "Add") }
            },
        ) { pad ->
            val list = contacts?.filter { it.uid !in g.members }
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                when {
                    list == null -> item {
                        Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
                    }
                    list.isEmpty() -> item { Text("Add cheyyadaniki inka contacts levu", color = Muted, modifier = Modifier.padding(16.dp)) }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back", tint = Ink, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(g.name, 104.dp, rounded = 36.dp)
                Spacer(Modifier.height(14.dp))
                Text(g.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = Ink)
                Text("Group · ${g.members.size} members", color = Muted, fontSize = 14.sp)
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoTile(Icons.AutoMirrored.Filled.Chat, "Message", Modifier.weight(1f), onClick = onBack)
                if (iAmAdmin) InfoTile(Icons.Filled.PersonAdd, "Add", Modifier.weight(1f)) { adding = true }
                if (iAmMember) InfoTile(Icons.AutoMirrored.Filled.ExitToApp, "Exit", Modifier.weight(1f), tint = Danger) { askLeave = true }
            }
            if (media.isNotEmpty()) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(Bg).padding(14.dp),
                ) {
                    Text("Media", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        media.forEach { m ->
                            AsyncImage(
                                model = File(m.media), contentDescription = "Photo", contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).height(70.dp).clip(RoundedCornerShape(12.dp)),
                            )
                        }
                        repeat(4 - media.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Bg).padding(vertical = 6.dp)) {
                Text("${g.members.size} members", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
                g.members.sortedBy { if (it == me) "" else Chat.nameOf(it).lowercase() }.forEach { uid ->
                    val n = if (uid == me) "You" else Chat.nameOf(uid)
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
                            Text("Admin", color = AccentDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold,
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
            title = { Text("Remove ${Chat.nameOf(uid)}?") },
            confirmButton = {
                TextButton(onClick = { removeWho = null; scope.launch { Chat.removeMember(gid, uid) } }) { Text("Remove", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { removeWho = null }) { Text("Cancel", color = Muted) } },
            containerColor = Surface,
        )
    }
    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text("Exit \"${g?.name}\"?") },
            confirmButton = {
                TextButton(onClick = { askLeave = false; scope.launch { Chat.leaveGroup(gid) } }) { Text("Exit", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { askLeave = false }) { Text("Cancel", color = Muted) } },
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
    var picked by remember { mutableStateOf(setOf<String>()) }
    val targets by produceState(initialValue = emptyList<Target>()) {
        value = withContext(Dispatchers.IO) {
            val chats = Chat.db.chats()
                .filter { !it.peer.startsWith("g:") || (Chat.db.group(it.peer.removePrefix("g:"))?.members?.contains(Chat.me) == true) }
                .map { Target(it.peer, it.name, if (it.peer.startsWith("g:")) "Group" else it.phone) }
            val seen = chats.map { it.peer }.toSet()
            chats + Chat.db.people().filter { it.uid !in seen }.map { Target(it.uid, it.name, it.phone) }
        }
    }
    Scaffold(
        containerColor = Bg,
        topBar = { Header(title = "Forward to...", subtitle = if (picked.isEmpty()) null else "${picked.size} selected", onBack = onDone) },
        floatingActionButton = {
            if (picked.isNotEmpty()) FloatingActionButton(
                onClick = {
                    Chat.db.msg(msgId)?.let { Chat.forward(it, picked.toList()) }
                    onDone()
                },
                containerColor = Accent, contentColor = Color.White,
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(targets, key = { it.peer }) { t ->
                PickRow(t.name, t.sub, t.peer in picked) {
                    picked = if (t.peer in picked) picked - t.peer else picked + t.peer
                }
            }
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
    Scaffold(containerColor = Bg, topBar = { Header(title = "Starred messages", onBack = onBack) }) { pad ->
        if (list.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("⭐", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text("Message ni long press chesi star nokkandi", color = Muted, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(list, key = { it.id }) { m ->
                    Column(
                        Modifier.fillMaxWidth().clickable { openChat(m.peer) }.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val who = if (m.fromMe) "You" else Chat.nameOf(m.sender.ifEmpty { m.peer })
                            val chatName = Chat.db.peer(m.peer)?.name ?: ""
                            Text(
                                if (m.peer.startsWith("g:")) "$who ▸ $chatName" else who,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(listTime(m.ts), fontSize = 12.sp, color = Muted)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(preview(m), color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color = Line)
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }
    }
}
