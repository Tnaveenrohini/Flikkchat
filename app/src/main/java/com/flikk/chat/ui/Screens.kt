package com.flikk.chat.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.flikk.chat.data.Chat
import com.flikk.chat.data.ChatRow
import com.flikk.chat.data.Contact
import com.flikk.chat.data.Msg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------- Setup (Firebase config ledu) ----------------

@Composable
fun SetupScreen() {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar("F", 84.dp)
        Spacer(Modifier.height(20.dp))
        Text("Flikkchat", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Pink)
        Spacer(Modifier.height(16.dp))
        Text(
            "Firebase setup inka avvaledu.\n\ngoogle-services.json file ni repo lo app/ folder lo pettandi, " +
                "kotha APK build ayyaka chat start avutundi.",
            textAlign = TextAlign.Center, color = Muted,
        )
    }
}

// ---------------- Login ----------------

@Composable
fun LoginScreen(onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Flikkchat", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Pink)
        Text("Made in India 🇮🇳 chat", color = Muted)
        Spacer(Modifier.height(36.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Mee peru") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = phone, onValueChange = { phone = it.take(16) },
            label = { Text("Phone number") }, singleLine = true,
            prefix = { Text("+91 ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = Color(0xFFFF6B6B), fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                busy = true; error = null
                scope.launch {
                    val raw = if (phone.trim().startsWith("+")) phone else "+91" + phone.filter { it.isDigit() }
                    val err = Chat.register(name, raw)
                    busy = false
                    if (err == null) onDone() else error = err
                }
            },
            enabled = !busy && name.isNotBlank() && phone.count { it.isDigit() } >= 10,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Pink),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Continue", fontSize = 16.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Mee chats mee phone lone save avutayi. Server lo store avvavu.",
            color = Muted, fontSize = 12.sp,
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Pink, focusedLabelColor = Pink, cursorColor = Pink,
)

// ---------------- Home (chats list) ----------------

@Composable
fun HomeScreen(openChat: (String) -> Unit, openContacts: () -> Unit, openProfile: () -> Unit) {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val version by Chat.db.changes.collectAsState()
    val chats by produceState(initialValue = emptyList<ChatRow>(), version) {
        value = withContext(Dispatchers.IO) { Chat.db.chats() }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Header(title = "Flikkchat", actions = {
                IconButton(onClick = openProfile) { Icon(Icons.Filled.Person, "Profile") }
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = openContacts, containerColor = Pink, contentColor = Color.White) {
                Icon(Icons.AutoMirrored.Filled.Message, "New chat")
            }
        },
    ) { pad ->
        if (chats.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Inka chats levu", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Kinda pink button nokki friends tho chat start cheyyandi 💬", color = Muted, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(chats, key = { it.peer }) { c -> ChatListItem(c) { openChat(c.peer) } }
            }
        }
    }
}

@Composable
private fun ChatListItem(c: ChatRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(c.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(listTime(c.lastTs), fontSize = 12.sp, color = if (c.unread > 0) Pink else Muted)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.lastFromMe) { Ticks(c.lastStatus); Spacer(Modifier.width(4.dp)) }
                Text(c.lastText, color = Muted, fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (c.unread > 0) {
                    Box(
                        Modifier.clip(CircleShape).background(Pink).padding(horizontal = 7.dp, vertical = 2.dp),
                    ) { Text(c.unread.toString(), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ---------------- Contacts ----------------

@Composable
fun ContactsScreen(onBack: () -> Unit, openChat: (String) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var loading by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<Contact>?>(null) }
    var reload by remember { mutableLongStateOf(0L) }

    LaunchedEffect(granted, reload) {
        if (!granted) { launcher.launch(Manifest.permission.READ_CONTACTS); return@LaunchedEffect }
        loading = true
        contacts = Chat.findContacts()
        loading = false
    }

    val invite = {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(
            Intent.EXTRA_TEXT,
            "Hey! Flikkchat lo chat cheddam 💬 Made in India app 🇮🇳\nhttps://github.com/Tnaveenrohini/Flikkchat/releases/latest"
        )
        ctx.startActivity(Intent.createChooser(i, "Invite"))
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Header(
                title = "Select contact",
                subtitle = contacts?.let { "${it.size} on Flikkchat" },
                onBack = onBack,
                actions = { IconButton(onClick = { reload++ }) { Icon(Icons.Filled.Refresh, "Refresh") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { invite() }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Pink), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PersonAdd, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("Invite friends", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                HorizontalDivider(color = Line)
            }
            when {
                !granted -> item {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Friends ni chudataniki contacts permission kavali", color = Muted, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { launcher.launch(Manifest.permission.READ_CONTACTS) },
                            colors = ButtonDefaults.buttonColors(containerColor = Pink)) { Text("Allow") }
                    }
                }
                loading -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Pink)
                    }
                }
                contacts.isNullOrEmpty() -> item {
                    Text(
                        "Mee contacts lo inka evaru Flikkchat lo levu.\nPaina 'Invite friends' tho pilavandi!",
                        color = Muted, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                    )
                }
                else -> items(contacts!!, key = { it.uid }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { openChat(c.uid) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(c.name)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(c.phone, color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Chat ----------------

@Composable
fun ChatScreen(peer: String, onBack: () -> Unit) {
    val version by Chat.db.changes.collectAsState()
    val msgs by produceState(initialValue = emptyList<Msg>(), peer, version) {
        value = withContext(Dispatchers.IO) { Chat.db.messages(peer) }
    }
    val local by produceState(initialValue = Chat.db.peer(peer), version) { value = Chat.db.peer(peer) }
    val state by Chat.peerState(peer).collectAsState(initial = null)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    DisposableEffect(peer) {
        Chat.openChat = peer
        onDispose { if (Chat.openChat == peer) Chat.openChat = null }
    }
    LaunchedEffect(version) { withContext(Dispatchers.IO) { Chat.markRead(peer) } }

    val name = local?.name?.takeIf { it.isNotBlank() } ?: state?.name ?: "Chat"
    val subtitle = state?.let { s ->
        when {
            s.typingTo == Chat.me && now - s.typingAt < Chat.TYPING_WINDOW_MS -> "typing..."
            now - s.lastActive < Chat.ONLINE_WINDOW_MS -> "online"
            else -> lastSeen(s.lastActive)
        }
    }

    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(0) }

    Column(Modifier.fillMaxSize().background(Color.Black).imePadding()) {
        Header(title = name, subtitle = subtitle, onBack = onBack, leading = { Avatar(name, 40.dp) })
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
                    Bubble(m)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                value = text,
                onValueChange = { text = it; if (it.isNotEmpty()) Chat.typing(peer) },
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(26.dp),
                maxLines = 5,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Surface, unfocusedContainerColor = Surface,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Pink,
                ),
            )
            Spacer(Modifier.width(8.dp))
            FloatingActionButton(
                onClick = { Chat.send(peer, text); text = "" },
                containerColor = Pink, contentColor = Color.White,
                shape = CircleShape, modifier = Modifier.size(54.dp),
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
        }
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
private fun Bubble(m: Msg) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        contentAlignment = if (m.fromMe) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (m.fromMe) 16.dp else 4.dp,
                        bottomEnd = if (m.fromMe) 4.dp else 16.dp,
                    )
                )
                .background(if (m.fromMe) BubbleMe else BubbleThem)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(m.text, fontSize = 15.sp, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(clock(m.ts), fontSize = 11.sp, color = Muted)
                if (m.fromMe) { Spacer(Modifier.width(4.dp)); Ticks(m.status, 15.dp) }
            }
        }
    }
}

// ---------------- Profile ----------------

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    var name by remember { mutableStateOf(Chat.myName) }
    var about by remember { mutableStateOf(Chat.myAbout) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(Color.Black).imePadding()) {
        Header(title = "Profile", onBack = onBack)
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(name.ifBlank { "?" }, 110.dp)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(about, { about = it.take(140) }, label = { Text("About") },
                modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            Spacer(Modifier.height(12.dp))
            Text("Phone: ${Chat.myPhone}", color = Muted, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        msg = Chat.updateProfile(name, about) ?: "Saved ✅"
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pink),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Save") }
            if (msg != null) { Spacer(Modifier.height(10.dp)); Text(msg!!, color = Muted) }
        }
    }
}
