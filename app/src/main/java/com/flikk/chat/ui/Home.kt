package com.flikk.chat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.flikk.chat.data.CallLog
import com.flikk.chat.data.Chat
import com.flikk.chat.data.ChatRow
import com.flikk.chat.data.Msg
import com.flikk.chat.data.StatusItem
import com.flikk.chat.data.isGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** "Flikk" + accent "chat" */
@Composable
fun Brand(size: TextUnit = 28.sp) {
    Text(
        buildAnnotatedString {
            append("Flikk")
            withStyle(SpanStyle(color = Accent)) { append("chat") }
        },
        fontSize = size, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, color = Ink,
    )
}

@Composable
private fun TopBar(title: @Composable () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { title() }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { actions() }
    }
}

@Composable
private fun Title(t: String) =
    Text(t, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, color = Ink)

@Composable
private fun SquareFab(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(60.dp).shadow(14.dp, RoundedCornerShape(20.dp), ambientColor = Accent, spotColor = Accent)
            .clip(RoundedCornerShape(20.dp)).background(Accent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp)) }
}

private const val NAV_SPACE = 112

// ================= Home =================

@Composable
fun HomeScreen(
    openChat: (String) -> Unit,
    openContacts: () -> Unit,
    openProfile: () -> Unit,
    openNewGroup: () -> Unit,
    openStarred: () -> Unit,
    openStatusText: () -> Unit,
    openStatusViewer: (String) -> Unit,
    openCallPicker: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = tab != 0) { tab = 0 }

    Box(Modifier.fillMaxSize().background(Bg)) {
        when (tab) {
            0 -> ChatsTab(openChat, openContacts, openProfile, openNewGroup, openStarred)
            1 -> UpdatesTab(openStatusText, openStatusViewer)
            2 -> GroupsTab(openChat, openNewGroup)
            else -> CallsTab(openCallPicker, openContacts)
        }
        BottomNav(tab, onSelect = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BottomNav(tab: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    Row(
        modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 18.dp).fillMaxWidth().height(68.dp)
            .clip(RoundedCornerShape(34.dp)).background(if (palette.dark) Surface else NavBg).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem("Chats", tab == 0, { onSelect(0) }) { c -> Icon(Icons.AutoMirrored.Filled.Chat, null, tint = c, modifier = Modifier.size(22.dp)) }
        NavItem("Updates", tab == 1, { onSelect(1) }) { c -> DashedCircle(c) }
        NavItem("Groups", tab == 2, { onSelect(2) }) { c -> Icon(Icons.Filled.Groups, null, tint = c, modifier = Modifier.size(24.dp)) }
        NavItem("Calls", tab == 3, { onSelect(3) }) { c -> Icon(Icons.Filled.Call, null, tint = c, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val c = if (selected) Color.White else NavMuted
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon(c) }
        Spacer(Modifier.height(3.dp))
        Text(label, color = c, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun DashedCircle(c: Color) {
    Canvas(Modifier.size(20.dp)) {
        drawCircle(
            color = c, radius = size.minDimension / 2 - 1.dp.toPx(),
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 2.5.dp.toPx()))),
        )
    }
}

// ================= Chats tab =================

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
            .then(if (selected) Modifier.background(Accent) else Modifier.border(1.dp, Line, RoundedCornerShape(17.dp)).background(Bg))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) Color.White else Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun SearchPill(q: String, onChange: (String) -> Unit, hint: String) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).background(Surface).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (q.isEmpty()) Text(hint, color = Muted, fontSize = 15.sp)
            BasicTextField(
                value = q, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = Ink, fontFamily = Jakarta),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ChatsTab(
    openChat: (String) -> Unit, openContacts: () -> Unit, openProfile: () -> Unit,
    openNewGroup: () -> Unit, openStarred: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by Chat.db.changes.collectAsState()
    val chats by produceState(initialValue = emptyList<ChatRow>(), version) {
        value = withContext(Dispatchers.IO) { Chat.db.chats() }
    }
    var q by remember { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val results by produceState(initialValue = emptyList<Msg>(), q, version) {
        value = if (q.isBlank()) emptyList() else withContext(Dispatchers.IO) { Chat.db.search(q.trim()) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val err = Chat.postPhotoStatus(uri, "")
            Toast.makeText(ctx, err ?: "Status pettesam ✅", Toast.LENGTH_SHORT).show()
        }
    }

    val unreadCount = chats.count { it.unread > 0 }
    val shown = chats.filter {
        (q.isBlank() || it.name.contains(q.trim(), ignoreCase = true)) && when (filter) {
            1 -> it.unread > 0
            2 -> isGroup(it.peer)
            3 -> Chat.isFav(it.peer)
            else -> true
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = NAV_SPACE.dp)) {
            item {
                TopBar(title = { Brand() }) {
                    RoundIcon(Icons.Filled.PhotoCamera, "Camera") {
                        camera.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    Box {
                        RoundIcon(Icons.Filled.MoreVert, "More options") { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("New group") }, onClick = { menu = false; openNewGroup() })
                            DropdownMenuItem(text = { Text("Starred messages") }, onClick = { menu = false; openStarred() })
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; openProfile() })
                        }
                    }
                }
                Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)) {
                    SearchPill(q, { q = it }, "Search chats, people, messages")
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 10.dp),
                ) {
                    item { Chip("All", filter == 0) { filter = 0 } }
                    item { Chip(if (unreadCount > 0) "Unread $unreadCount" else "Unread", filter == 1) { filter = 1 } }
                    item { Chip("Groups", filter == 2) { filter = 2 } }
                    item { Chip("Favourites", filter == 3) { filter = 3 } }
                }
            }
            items(shown, key = { it.peer }) { c ->
                ChatRowItem(c, onLong = {
                    val fav = Chat.toggleFav(c.peer)
                    Toast.makeText(ctx, if (fav) "⭐ Favourites lo add chesam" else "Favourites nunchi teesesam", Toast.LENGTH_SHORT).show()
                }) { openChat(c.peer) }
            }
            if (q.isNotBlank() && results.isNotEmpty()) {
                item { SectionLabel("Messages") }
                items(results, key = { "m" + it.id }) { m ->
                    Column(Modifier.fillMaxWidth().clickable { openChat(m.peer) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Row {
                            Text(Chat.db.peer(m.peer)?.name ?: "", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = Ink)
                            Text(listTime(m.ts), fontSize = 12.sp, color = Muted)
                        }
                        Text(m.text, color = Muted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (shown.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            q.isNotBlank() -> "\"$q\" ki chats levu"
                            filter == 3 -> "Favourites levu. Chat meeda long press chesi add cheyyandi ⭐"
                            filter != 0 -> "Ikkada chats levu"
                            else -> "Inka chats levu.\nKinda button nokki friends tho chat start cheyyandi."
                        },
                        color = Muted, textAlign = TextAlign.Center, fontSize = 14.sp,
                    )
                }
            }
        }
        SquareFab(Icons.Filled.AddComment, "New chat", Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 104.dp)) {
            openContacts()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRowItem(c: ChatRow, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(c.name, 54.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, color = Ink,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (Chat.isFav(c.peer)) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Star, null, tint = Color(0xFFE3A008), modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(listTime(c.lastTs), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (c.unread > 0) Accent else Muted)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.lastFromMe) { Ticks(c.lastStatus, 16.dp); Spacer(Modifier.width(4.dp)) }
                Text(c.lastText, color = Muted, fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (c.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(Accent).padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(c.unread.toString(), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ================= Updates tab =================

@Composable
private fun UpdatesTab(openText: () -> Unit, openViewer: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by Chat.db.changes.collectAsState()
    val all by produceState(initialValue = emptyList<StatusItem>(), version) {
        value = withContext(Dispatchers.IO) { Chat.purgeExpired(); Chat.db.statuses() }
    }
    val me = Chat.me
    val mine = all.filter { it.owner == me }
    val others = all.filter { it.owner != me && !Chat.isBlocked(it.owner) }.groupBy { it.owner }
        .map { (owner, list) -> Triple(owner, list, list.any { !it.seen }) }
        .sortedWith(compareByDescending<Triple<String, List<StatusItem>, Boolean>> { it.third }.thenByDescending { it.second.maxOf { s -> s.ts } })
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            Toast.makeText(ctx, "Status pedtunnam...", Toast.LENGTH_SHORT).show()
            val err = Chat.postPhotoStatus(uri, "")
            Toast.makeText(ctx, err ?: "Status pettesam ✅", Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = NAV_SPACE.dp)) {
            item {
                TopBar(title = { Title("Updates") }) {
                    RoundIcon(Icons.Filled.Edit, "Text status", openText)
                }
                Text("Status", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Box(
                            Modifier.size(width = 96.dp, height = 150.dp).clip(RoundedCornerShape(20.dp)).background(Surface)
                                .clickable { if (mine.isNotEmpty()) openViewer(me ?: "") else openText() },
                        ) {
                            mine.lastOrNull()?.let { StatusCardBg(it) }
                            Box(Modifier.align(Alignment.TopCenter).padding(top = 14.dp)) {
                                Avatar(Chat.myName, 52.dp)
                                Box(
                                    Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(Surface).padding(3.dp)
                                        .clip(CircleShape).background(Accent),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                            }
                            Text("My status", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (mine.isNotEmpty()) Color.White else Ink, textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
                        }
                    }
                    items(others, key = { "c" + it.first }) { (owner, list, unseen) ->
                        val name = Chat.nameOf(owner)
                        Box(
                            Modifier.size(width = 96.dp, height = 150.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF3A2F6B))
                                .clickable { openViewer(owner) },
                        ) {
                            StatusCardBg(list.last())
                            Box(
                                Modifier.padding(10.dp).size(40.dp).border(3.dp, if (unseen) Accent else Color(0xFFC9C6D8), CircleShape)
                                    .padding(3.dp).clip(CircleShape).background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) { Text(initials(name), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Ink) }
                            Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                        }
                    }
                }
                if (others.isNotEmpty()) SectionLabel("Recent")
            }
            items(others, key = { "r" + it.first }) { (owner, list, unseen) ->
                val name = Chat.nameOf(owner)
                Row(
                    Modifier.fillMaxWidth().clickable { openViewer(owner) }.padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(54.dp).border(2.5.dp, if (unseen) Accent else Color(0xFFC9C6D8), CircleShape).padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) { Avatar(name, 46.dp) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                        Text(longTime(list.maxOf { it.ts }), color = Muted, fontSize = 13.sp)
                    }
                }
            }
            if (others.isEmpty()) item {
                Text(
                    "Mee contacts status pedithe ikkada kanipistundi. Status 24 hours tarvatha automatic ga pothundi.",
                    color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                )
            }
        }
        SquareFab(Icons.Filled.PhotoCamera, "Photo status", Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 104.dp)) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

@Composable
private fun StatusCardBg(s: StatusItem) {
    if (s.type == "image" && s.media.isNotEmpty()) {
        AsyncImage(model = File(s.media), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
    } else {
        Box(Modifier.fillMaxSize().background(Color(s.color.takeIf { it != 0L } ?: 0xFF3A2F6B)))
    }
}

// ================= Groups tab =================

@Composable
private fun GroupsTab(openChat: (String) -> Unit, openNewGroup: () -> Unit) {
    val version by Chat.db.changes.collectAsState()
    val groups by produceState(initialValue = emptyList<ChatRow>(), version) {
        value = withContext(Dispatchers.IO) { Chat.db.chats().filter { isGroup(it.peer) } }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = NAV_SPACE.dp)) {
            item {
                TopBar(title = { Title("Groups") }) { RoundIcon(Icons.Filled.GroupAdd, "New group", openNewGroup) }
            }
            items(groups, key = { it.peer }) { c -> ChatRowItem(c) { openChat(c.peer) } }
            if (groups.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(AccentSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Groups, null, tint = Accent, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Inka groups levu", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                    Spacer(Modifier.height(6.dp))
                    Text("Family, friends, office kosam group create cheyyandi.", color = Muted, textAlign = TextAlign.Center, fontSize = 14.sp)
                }
            }
        }
        SquareFab(Icons.Filled.GroupAdd, "New group", Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 104.dp), onClick = openNewGroup)
    }
}

// ================= Calls tab =================

@Composable
private fun CallsTab(openCallPicker: (Boolean) -> Unit, openContacts: () -> Unit) {
    val caller = rememberCaller()
    val version by Chat.db.changes.collectAsState()
    var missedOnly by remember { mutableStateOf(false) }
    val calls by produceState(initialValue = emptyList<CallLog>(), version) {
        value = withContext(Dispatchers.IO) { Chat.db.calls() }
    }
    val shown = if (missedOnly) calls.filter { it.missed } else calls
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = NAV_SPACE.dp)) {
        item {
            TopBar(title = { Title("Calls") })
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(Icons.Filled.Call, "Call", Modifier.weight(1f)) { openCallPicker(false) }
                ActionTile(Icons.Filled.Videocam, "Video", Modifier.weight(1f)) { openCallPicker(true) }
                ActionTile(Icons.Filled.Contacts, "Contacts", Modifier.weight(1f), onClick = openContacts)
                ActionTile(Icons.Filled.PhoneMissed, if (missedOnly) "All" else "Missed", Modifier.weight(1f)) { missedOnly = !missedOnly }
            }
            SectionLabel(if (missedOnly) "Missed" else "Recent")
        }
        items(shown, key = { it.id }) { c ->
            val name = Chat.nameOf(c.peer)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(name, 50.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (c.missed) Danger else Ink)
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (c.outgoing) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived, null,
                            tint = if (c.missed) Danger else Online, modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(longTime(c.ts) + if (c.dur > 0) " · ${duration(c.dur)}" else "", color = Muted, fontSize = 13.sp)
                    }
                }
                IconButton(onClick = { caller(c.peer, c.video) }) {
                    Icon(if (c.video) Icons.Filled.Videocam else Icons.Filled.Call, (if (c.video) "Video call " else "Voice call ") + name, tint = Ink)
                }
            }
        }
        if (shown.isEmpty()) item {
            Text(
                if (missedOnly) "Missed calls levu 👍" else "Inka calls levu. Paina Call / Video nokki call cheyyandi.",
                color = Muted, textAlign = TextAlign.Center, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(78.dp).clip(RoundedCornerShape(20.dp)).background(Surface).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

// ================= Call picker =================

@Composable
fun CallPickerScreen(video: Boolean, onBack: () -> Unit) {
    val caller = rememberCaller()
    val call by com.flikk.chat.data.Calls.state.collectAsState()
    LaunchedEffect(call != null) { if (call != null) onBack() }
    val people by produceState(initialValue = Chat.db.people()) {
        value = withContext(Dispatchers.IO) { Chat.findContacts().map { com.flikk.chat.data.Peer(it.uid, it.name, it.phone) } }
    }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Header(title = if (video) "Video call" else "Voice call", subtitle = "Contact select cheyyandi", onBack = onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            items(people, key = { it.uid }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { caller(p.uid, video) }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(p.name, 50.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                        Text(p.phone, color = Muted, fontSize = 13.sp)
                    }
                    Icon(if (video) Icons.Filled.Videocam else Icons.Filled.Call, null, tint = Accent)
                }
            }
            if (people.isEmpty()) item {
                Text("Flikkchat lo contacts levu", color = Muted, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center)
            }
        }
    }
}
