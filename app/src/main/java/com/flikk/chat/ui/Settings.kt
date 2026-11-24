package com.flikk.chat.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flikk.chat.data.Backup
import com.flikk.chat.data.Chat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Chat.themeMode + phone setting batti palette set */
fun applyTheme(ctx: Context) {
    val sysDark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val dark = when (Chat.themeMode) { 1 -> false; 2 -> true; else -> sysDark }
    palette = if (dark) DarkPalette else LightPalette
}

// ---------------- Shared pieces ----------------

@Composable
fun SubPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(ChatBg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back", tint = Ink, modifier = Modifier.size(20.dp)) }
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp), content = content)
    }
}

@Composable
fun Caps(t: String) = Text(
    t.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = 0.6.sp,
    modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 8.dp),
)

@Composable
fun Card(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(BubbleThem).padding(vertical = 4.dp),
    content = content,
)

@Composable
fun Toggle(on: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) Accent else Color(0xFFD6D4E0))
            .clickable { onChange(!on) }.padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White)) }
}

@Composable
fun ToggleRow(label: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Muted, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Toggle(on, label, onChange)
    }
}

@Composable
fun LinkRow(label: String, value: String = "", sub: String = "", color: Color = Ink, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color)
            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Muted)
        }
        if (value.isNotEmpty()) Text(value, fontSize = 14.sp, color = Muted)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun IconRow(icon: ImageVector, bg: Color, fg: Color, label: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(sub, fontSize = 12.sp, color = Muted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted, modifier = Modifier.size(20.dp))
    }
}

// ---------------- Settings ----------------

@Composable
fun SettingsScreen(
    onBack: () -> Unit, openProfile: () -> Unit, openPrivacy: () -> Unit, openChats: () -> Unit,
    openNotifications: () -> Unit, openStorage: () -> Unit, openStarred: () -> Unit,
    openLanguage: () -> Unit = {}, openBackup: () -> Unit = {}, openTwoStep: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val version by Chat.db.changes.collectAsState()
    val about = remember(version) { Chat.myAbout }
    SubPage(tr("Settings"), onBack) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(BubbleThem).clickable(onClick = openProfile).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(Chat.myName, 62.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(Chat.myName, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Text(about, fontSize = 13.sp, color = Muted)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(Surface), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.QrCode, null, tint = Accent, modifier = Modifier.size(20.dp))
            }
        }
        Card {
            IconRow(Icons.Filled.Key, Color(0xFFE7E3FF), Color(0xFF3A20C2), tr("Account"), tr("Profile, two-step verification"), openProfile)
            IconRow(Icons.Filled.Lock, Color(0xFFD7F5EC), Color(0xFF0F5E4A), tr("Privacy"), tr("Last seen, blocked, app lock"), openPrivacy)
            IconRow(Icons.AutoMirrored.Filled.Chat, Color(0xFFDCEBFF), Color(0xFF1E4E8C), tr("Chats"), tr("Theme, wallpaper, font size"), openChats)
            IconRow(Icons.Filled.CloudUpload, Color(0xFFD7F5EC), Color(0xFF0F5E4A), tr("Chat backup"), tr("Back up and restore"), openBackup)
            IconRow(Icons.Filled.Notifications, Color(0xFFFFE4D6), Color(0xFF9A3412), tr("Notifications"), tr("Message, group and call tones"), openNotifications)
            IconRow(Icons.Filled.Storage, Color(0xFFFFF1C7), Color(0xFF854D0E), tr("Storage and data"), tr("Space used, clear media"), openStorage)
        }
        Card {
            IconRow(Icons.Filled.Language, Color(0xFFFCE1F0), Color(0xFF9D174D), tr("App language"), Languages.first { it.first == appLang }.second, openLanguage)
            IconRow(Icons.Filled.Password, Color(0xFFE5E7EB), Color(0xFF374151), tr("Two-step verification"), tr(if (Chat.hasPin) "On" else "Off"), openTwoStep)
            IconRow(Icons.Filled.Info, Color(0xFFFFF1C7), Color(0xFF854D0E), tr("Starred messages"), tr("Messages you saved"), openStarred)
            IconRow(Icons.Filled.Share, Color(0xFFE7E3FF), Color(0xFF3A20C2), tr("Invite a friend"), tr("Share Flikkchat")) {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(
                    Intent.EXTRA_TEXT,
                    "Hey! Flikkchat lo chat cheddam 💬 Made in India app 🇮🇳\nhttps://github.com/Tnaveenrohini/Flikkchat/releases/latest"
                )
                ctx.startActivity(Intent.createChooser(i, "Invite"))
            }
        }
        Text("Flikkchat · Made in India 🇮🇳", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
    }
}

// ---------------- Edit profile ----------------

@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(Chat.myName) }
    var about by remember { mutableStateOf(Chat.myAbout) }
    var editing by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    SubPage("Profile", onBack) {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 26.dp)) { Avatar(name, 140.dp) }
        Card {
            FieldRow(Icons.Filled.Person, "Name", name, true) { editing = "name"; draft = name }
            FieldRow(Icons.Filled.Info, "About", about, true) { editing = "about"; draft = about }
            FieldRow(Icons.Filled.Phone, "Phone", Chat.myPhone, false) {}
        }
        Text("Mee peru, about mee contacts ki kanipistayi.", fontSize = 12.sp, lineHeight = 18.sp, color = Muted,
            modifier = Modifier.padding(horizontal = 28.dp))
        msg?.let { Text(it, fontSize = 13.sp, color = Accent, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)) }
    }
    editing?.let { field ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (field == "name") "Mee peru" else "About") },
            text = { BoxField("", draft, { draft = it.take(if (field == "name") 40 else 140) }, focused = true) },
            confirmButton = {
                TextButton(onClick = {
                    val n = if (field == "name") draft else name
                    val a = if (field == "about") draft else about
                    editing = null
                    scope.launch {
                        val err = Chat.updateProfile(n, a)
                        if (err == null) { name = n; about = a; msg = "Saved ✅" } else msg = err
                    }
                }, enabled = field != "name" || draft.isNotBlank()) { Text("Save", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel", color = Muted) } },
            containerColor = BubbleThem,
        )
    }
}

@Composable
private fun FieldRow(icon: ImageVector, label: String, value: String, edit: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = edit, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
        if (edit) Icon(Icons.Filled.Edit, "Edit $label", tint = Accent, modifier = Modifier.size(20.dp))
    }
}

// ---------------- Privacy ----------------

@Composable
fun PrivacyScreen(onBack: () -> Unit, openBlocked: () -> Unit) {
    val version by Chat.db.changes.collectAsState()
    val blocked = remember(version) { Chat.blockedList().size }
    val hideSeen = remember(version) { Chat.hideSeen }
    SubPage("Privacy", onBack) {
        Caps("Who can see my info")
        Card {
            LinkRow("Last seen and online", if (hideSeen) "Nobody" else "Everyone") { Chat.setFlag("hide_seen", !hideSeen) }
            LinkRow("Blocked contacts", if (blocked > 0) "$blocked" else "None", onClick = openBlocked)
        }
        Caps("Security")
        Card {
            ToggleRow("Read receipts", "Off chesthe, mee nunchi blue ticks vellavu", remember(version) { Chat.readReceipts }) { Chat.setFlag("read_receipts", it) }
            ToggleRow("App lock", "Fingerprint / screen lock tho app open cheyyandi", remember(version) { Chat.flag("app_lock", false) }) { Chat.setFlag("app_lock", it) }
            ToggleRow("Silence unknown callers", "Contacts lo leni numbers nunchi calls ring avvavu", remember(version) { Chat.flag("silence_unknown", false) }) { Chat.setFlag("silence_unknown", it) }
        }
        Caps("Encryption")
        Card {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Lock, null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Messages, photos, voice, status, calls annee end-to-end encrypted. Chats mee phone lone untayi.",
                    fontSize = 13.sp, lineHeight = 19.sp, color = Muted)
            }
        }
    }
}

@Composable
fun BlockedScreen(onBack: () -> Unit) {
    val version by Chat.db.changes.collectAsState()
    val list = remember(version) { Chat.blockedList() }
    SubPage("Blocked contacts", onBack) {
        if (list.isEmpty()) Text("Evarini block cheyyaledu", color = Muted, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center)
        else Card {
            list.forEach { uid ->
                val n = Chat.nameOf(uid)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(n, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(n, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = { Chat.setBlocked(uid, false) }) { Text("Unblock", color = Accent, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ---------------- Notifications ----------------

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version by Chat.db.changes.collectAsState()
    fun openChannel(id: String) {
        val i = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, id)
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:" + ctx.packageName))
        ctx.startActivity(i)
    }
    SubPage("Notifications", onBack) {
        Caps("Messages")
        Card {
            ToggleRow("Show notifications", "New messages ki alert", remember(version) { Chat.flag("notify_msgs", true) }) { Chat.setFlag("notify_msgs", it) }
            LinkRow("Tone & vibrate", sub = "Phone settings lo marchandi") { openChannel("messages") }
        }
        Caps("Groups")
        Card {
            ToggleRow("Show notifications", "Group messages ki alert", remember(version) { Chat.flag("notify_groups", true) }) { Chat.setFlag("notify_groups", it) }
        }
        Caps("Calls")
        Card { LinkRow("Ringtone & vibrate", sub = "Phone settings lo marchandi") { openChannel("calls") } }
        Caps("Other")
        Card {
            ToggleRow("Show preview", "Message text notification lo", remember(version) { Chat.flag("notify_preview", true) }) { Chat.setFlag("notify_preview", it) }
        }
    }
}

// ---------------- Storage ----------------

private fun size(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%d KB".format(b / (1L shl 10))
    else -> "$b B"
}

@Composable
fun StorageScreen(onBack: () -> Unit) {
    val version by Chat.db.changes.collectAsState()
    val info by produceState<Chat.StorageInfo?>(initialValue = null, version) { value = Chat.storageInfo() }
    var clearPeer by remember { mutableStateOf<String?>(null) }
    SubPage("Storage and data", onBack) {
        val i = info
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(BubbleThem).padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(i?.let { size(it.total) } ?: "…", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.weight(1f))
                Text("Flikkchat use chestundi", fontSize = 13.sp, color = Muted)
            }
            if (i != null && i.total > 0) {
                val parts = listOf(i.photos to Accent, i.videos to Color(0xFF0E7490), i.audio to Danger, i.db to Color(0xFFC9C6D8))
                Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Divider), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    parts.filter { it.first > 0 }.forEach { (v, c) -> Box(Modifier.weight(v.toFloat()).fillMaxSize().background(c)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(Accent, "Photos ${size(i.photos)}")
                    Legend(Color(0xFF0E7490), "Videos ${size(i.videos)}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(Danger, "Voice ${size(i.audio)}")
                    Legend(Color(0xFFC9C6D8), "Chats ${size(i.db)}")
                }
            }
        }
        Caps("Biggest chats")
        Card {
            val list = i?.byChat.orEmpty()
            if (list.isEmpty()) Text("Media inka ledu", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            list.take(10).forEach { (peer, bytes) ->
                val n = Chat.db.peer(peer)?.name ?: "Chat"
                Row(
                    Modifier.fillMaxWidth().clickable { clearPeer = peer }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(n, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(n, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                    Text(size(bytes), fontSize = 14.sp, color = Muted)
                }
            }
        }
        Text("Chat meeda tap chesi photos / videos / voice clear cheyyochu. Messages untayi.",
            fontSize = 12.sp, color = Muted, modifier = Modifier.padding(horizontal = 28.dp))
    }
    clearPeer?.let { p ->
        AlertDialog(
            onDismissRequest = { clearPeer = null },
            title = { Text("Media clear cheyyala?") },
            text = { Text("${Chat.db.peer(p)?.name ?: "Ee chat"} lo photos, videos, voice ee phone nunchi delete avutayi.", color = Muted) },
            confirmButton = { TextButton(onClick = { Chat.clearChatMedia(p); clearPeer = null }) { Text("Clear", color = Danger) } },
            dismissButton = { TextButton(onClick = { clearPeer = null }) { Text("Cancel", color = Muted) } },
            containerColor = BubbleThem,
        )
    }
}

@Composable
private fun Legend(c: Color, t: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
    Spacer(Modifier.width(6.dp))
    Text(t, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DayInk)
}

// ---------------- Chats (theme + backup) ----------------

@Composable
fun ChatsSettingsScreen(onBack: () -> Unit, openStarred: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(Chat.themeMode) }
    var busy by remember { mutableStateOf(false) }
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
    SubPage("Chats", onBack) {
        Caps("Theme")
        Card {
            listOf(0 to "Phone setting", 1 to "Light", 2 to "Dark").forEach { (v, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        mode = v; Chat.themeMode = v; applyTheme(ctx)
                    }.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(if (mode == v) Accent else Line).padding(if (mode == v) 6.dp else 2.dp)
                            .clip(CircleShape).background(if (mode == v) Color.White else BubbleThem),
                    )
                }
            }
        }
        Caps("Chat backup")
        Card {
            Text(
                "Chats, photos, videos, voice annee okka file lo save avutayi. File picker lo Google Drive select chesthe Drive lo untundi.",
                fontSize = 13.sp, lineHeight = 19.sp, color = Muted, modifier = Modifier.padding(16.dp),
            )
            LinkRow("Back up now", sub = "Flikkchat-backup .zip") {
                if (!busy) exportLauncher.launch("Flikkchat-backup-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".zip")
            }
            LinkRow("Restore from backup", sub = "Ippati chats replace avutayi") { if (!busy) askRestore = true }
            backupMsg?.let { Text(it, color = Accent, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
        }
        Card { LinkRow("Starred messages", onClick = openStarred) }
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
            containerColor = BubbleThem,
        )
    }
}

// ---------------- App lock ----------------

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(NavBg).statusBarsPadding().navigationBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(LightPalette.accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("Flikkchat locked", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text("Open cheyyadaniki fingerprint / screen lock vaadandi", color = NavMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp))
        PillButton("Unlock", bg = Color.White, fg = NavBg, onClick = onUnlock)
    }
}
