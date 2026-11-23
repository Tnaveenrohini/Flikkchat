package com.flikk.chat.ui

import android.Manifest
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.filled.AddIcCall
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Videocam
import com.flikk.chat.data.CallLog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.flikk.chat.data.Backup
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
        Brand(32.sp)
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
        Brand(36.sp)
        Text("Made in India 🇮🇳 chat", color = Muted, fontSize = 15.sp)
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
            Text(error!!, color = Danger, fontSize = 13.sp)
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
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(26.dp),
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
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent, focusedLabelColor = Accent, cursorColor = Accent,
)

@Composable
fun SectionLabel(t: String) {
    Text(t.uppercase(), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp))
}

// ---------------- Contacts ----------------

@Composable
fun ContactsScreen(onBack: () -> Unit, openChat: (String) -> Unit, openNewGroup: () -> Unit) {
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
        containerColor = Bg,
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
                    Modifier.fillMaxWidth().clickable { openNewGroup() }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Groups, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("New group", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Row(
                    Modifier.fillMaxWidth().clickable { invite() }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
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
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Allow") }
                    }
                }
                loading -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
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

// ---------------- Profile ----------------

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    var name by remember { mutableStateOf(Chat.myName) }
    var about by remember { mutableStateOf(Chat.myAbout) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var backupMsg by remember { mutableStateOf<String?>(null) }
    var askRestore by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            busy = true; backupMsg = "Backup avutundi..."
            scope.launch {
                val r = withContext(Dispatchers.IO) { Backup.export(ctx, uri) }
                busy = false
                backupMsg = r.fold({ "Backup ready ✅ ($it media files)" }, { "Backup fail: ${it.message}" })
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true; backupMsg = "Restore avutundi..."
            scope.launch {
                val r = withContext(Dispatchers.IO) { Backup.restore(ctx, uri) }
                busy = false
                r.fold({ Backup.restartApp(ctx) }, { backupMsg = "Restore fail: ${it.message}" })
            }
        }
    }
    if (askRestore) {
        AlertDialog(
            onDismissRequest = { askRestore = false },
            title = { Text("Restore backup?") },
            text = { Text("Ippudu phone lo unna chats backup file lo unna chats tho replace avutayi. App restart avutundi.", color = Muted) },
            confirmButton = {
                TextButton(onClick = { askRestore = false; importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                    Text("Choose file", color = Accent)
                }
            },
            dismissButton = { TextButton(onClick = { askRestore = false }) { Text("Cancel", color = Muted) } },
            containerColor = Surface,
        )
    }

    Column(Modifier.fillMaxSize().background(Bg).imePadding()) {
        Header(title = "Profile", onBack = onBack)
        Column(
            Modifier.padding(24.dp).verticalScroll(rememberScrollState()).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(26.dp),
            ) { Text("Save") }
            if (msg != null) { Spacer(Modifier.height(10.dp)); Text(msg!!, color = Muted) }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = Line)
            Spacer(Modifier.height(16.dp))
            Text("💾 Chat backup", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
            Text(
                "Chats, photos, videos, voice annee okka file lo save avutayi. File picker lo Google Drive select chesthe Drive lo untundi.",
                color = Muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { exportLauncher.launch("Flikkchat-backup-" + java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date()) + ".zip") },
                    enabled = !busy, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(12.dp),
                ) { Text("Backup") }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { askRestore = true },
                    enabled = !busy, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Surface), shape = RoundedCornerShape(12.dp),
                ) { Text("Restore", color = Ink) }
            }
            if (backupMsg != null) { Spacer(Modifier.height(10.dp)); Text(backupMsg!!, color = Muted) }
        }
    }
}
