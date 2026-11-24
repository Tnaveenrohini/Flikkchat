package com.flikk.chat.ui

import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.luminance
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
    palette = withAccent(if (dark) DarkPalette else LightPalette, Chat.accentColor)
}

// ---------------- Shared pieces ----------------

@Composable
fun SubPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(ChatBg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, tr("Back"), tint = Ink, modifier = Modifier.size(20.dp)) }
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
    ) { Box(Modifier.size(26.dp).clip(CircleShape).background(OnAccent)) }
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
            IconRow(Icons.Filled.Password, Color(0xFFE5E7EB), Color(0xFF374151), tr("Two-step verification"), tr(if (Chat.hasPin) tr("On") else tr("Off")), openTwoStep)
            IconRow(Icons.Filled.Info, Color(0xFFFFF1C7), Color(0xFF854D0E), tr("Starred messages"), tr("Messages you saved"), openStarred)
            IconRow(Icons.Filled.Share, Color(0xFFE7E3FF), Color(0xFF3A20C2), tr("Invite a friend"), tr("Share Double Bubble")) {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(
                    Intent.EXTRA_TEXT,
                    tr("Hey! Let's chat on Double Bubble 💬 Made in India app 🇮🇳") + "\nhttps://github.com/Tnaveenrohini/Double Bubble/releases/latest"
                )
                ctx.startActivity(Intent.createChooser(i, tr("Invite")))
            }
        }
        Text("Double Bubble · by Flikk · Made in India 🇮🇳", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
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
    SubPage(tr("Profile"), onBack) {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 26.dp)) { Avatar(name, 140.dp) }
        Card {
            FieldRow(Icons.Filled.Person, tr("Name"), name, true) { editing = "name"; draft = name }
            FieldRow(Icons.Filled.Info, tr("About"), about, true) { editing = "about"; draft = about }
            FieldRow(Icons.Filled.Phone, tr("Phone"), Chat.myPhone, false) {}
        }
        Text(tr("Your name and about are visible to your contacts."), fontSize = 12.sp, lineHeight = 18.sp, color = Muted,
            modifier = Modifier.padding(horizontal = 28.dp))
        msg?.let { Text(it, fontSize = 13.sp, color = Accent, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)) }
    }
    editing?.let { field ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (field == "name") tr("Your name") else tr("About")) },
            text = { BoxField("", draft, { draft = it.take(if (field == "name") 40 else 140) }, focused = true) },
            confirmButton = {
                TextButton(onClick = {
                    val n = if (field == "name") draft else name
                    val a = if (field == "about") draft else about
                    editing = null
                    scope.launch {
                        val err = Chat.updateProfile(n, a)
                        if (err == null) { name = n; about = a; msg = tr("Saved ✅") } else msg = tr(err)
                    }
                }, enabled = field != "name" || draft.isNotBlank()) { Text(tr("Save"), color = Accent) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(tr("Cancel"), color = Muted) } },
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
        if (edit) Icon(Icons.Filled.Edit, tr("Edit") + " " + label, tint = Accent, modifier = Modifier.size(20.dp))
    }
}

// ---------------- Privacy ----------------

@Composable
fun PrivacyScreen(onBack: () -> Unit, openBlocked: () -> Unit) {
    val version by Chat.db.changes.collectAsState()
    val blocked = remember(version) { Chat.blockedList().size }
    val hideSeen = remember(version) { Chat.hideSeen }
    SubPage(tr("Privacy"), onBack) {
        Caps(tr("Who can see my info"))
        Card {
            LinkRow(tr("Last seen and online"), if (hideSeen) tr("Nobody") else tr("Everyone")) { Chat.setFlag("hide_seen", !hideSeen) }
            LinkRow(tr("Blocked contacts"), if (blocked > 0) "$blocked" else tr("None"), onClick = openBlocked)
        }
        Caps(tr("Security"))
        Card {
            ToggleRow(tr("Read receipts"), tr("When off, you won't send blue ticks"), remember(version) { Chat.readReceipts }) { Chat.setFlag("read_receipts", it) }
            ToggleRow(tr("App lock"), tr("Open the app with fingerprint / screen lock"), remember(version) { Chat.flag("app_lock", false) }) { Chat.setFlag("app_lock", it) }
            ToggleRow(tr("Silence unknown callers"), tr("Calls from numbers not in contacts won't ring"), remember(version) { Chat.flag("silence_unknown", false) }) { Chat.setFlag("silence_unknown", it) }
        }
        Caps(tr("Encryption"))
        Card {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Lock, null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(tr("Messages, photos, voice, status and calls are all end-to-end encrypted. Chats stay on your phone."),
                    fontSize = 13.sp, lineHeight = 19.sp, color = Muted)
            }
        }
    }
}

@Composable
fun BlockedScreen(onBack: () -> Unit) {
    val version by Chat.db.changes.collectAsState()
    val list = remember(version) { Chat.blockedList() }
    SubPage(tr("Blocked contacts"), onBack) {
        if (list.isEmpty()) Text(tr("You haven't blocked anyone"), color = Muted, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center)
        else Card {
            list.forEach { uid ->
                val n = Chat.nameOf(uid)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(n, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(n, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = { Chat.setBlocked(uid, false) }) { Text(tr("Unblock"), color = Accent, fontWeight = FontWeight.Bold) }
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
    SubPage(tr("Notifications"), onBack) {
        Caps(tr("Messages"))
        Card {
            ToggleRow(tr("Show notifications"), tr("Alerts for new messages"), remember(version) { Chat.flag("notify_msgs", true) }) { Chat.setFlag("notify_msgs", it) }
            LinkRow(tr("Tone & vibrate"), sub = tr("Change in phone settings")) { openChannel("messages") }
        }
        Caps(tr("Groups"))
        Card {
            ToggleRow(tr("Show notifications"), tr("Alerts for group messages"), remember(version) { Chat.flag("notify_groups", true) }) { Chat.setFlag("notify_groups", it) }
        }
        Caps(tr("Calls"))
        Card { LinkRow(tr("Ringtone & vibrate"), sub = tr("Change in phone settings")) { openChannel("calls") } }
        Caps(tr("Other"))
        Card {
            ToggleRow(tr("Show preview"), tr("Message text in notifications"), remember(version) { Chat.flag("notify_preview", true) }) { Chat.setFlag("notify_preview", it) }
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
    SubPage(tr("Storage and data"), onBack) {
        val i = info
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(BubbleThem).padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(i?.let { size(it.total) } ?: "…", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.weight(1f))
                Text(tr("used by Double Bubble"), fontSize = 13.sp, color = Muted)
            }
            if (i != null && i.total > 0) {
                val parts = listOf(i.photos to Accent, i.videos to Color(0xFF0E7490), i.audio to Danger, i.db to Color(0xFFC9C6D8))
                Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Divider), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    parts.filter { it.first > 0 }.forEach { (v, c) -> Box(Modifier.weight(v.toFloat()).fillMaxSize().background(c)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(Accent, tr("Photos") + " ${size(i.photos)}")
                    Legend(Color(0xFF0E7490), tr("Videos") + " ${size(i.videos)}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(Danger, tr("Voice") + " ${size(i.audio)}")
                    Legend(Color(0xFFC9C6D8), tr("Chats") + " ${size(i.db)}")
                }
            }
        }
        Caps(tr("Biggest chats"))
        Card {
            val list = i?.byChat.orEmpty()
            if (list.isEmpty()) Text(tr("No media yet"), color = Muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            list.take(10).forEach { (peer, bytes) ->
                val n = Chat.db.peer(peer)?.name ?: tr("Chat")
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
        Text(tr("Tap a chat to clear its photos / videos / voice. Messages stay."),
            fontSize = 12.sp, color = Muted, modifier = Modifier.padding(horizontal = 28.dp))
    }
    clearPeer?.let { p ->
        AlertDialog(
            onDismissRequest = { clearPeer = null },
            title = { Text(tr("Clear media?")) },
            text = { Text(tr("Photos, videos and voice in {0} will be deleted from this phone.", Chat.db.peer(p)?.name ?: tr("Chat")), color = Muted) },
            confirmButton = { TextButton(onClick = { Chat.clearChatMedia(p); clearPeer = null }) { Text(tr("Clear"), color = Danger) } },
            dismissButton = { TextButton(onClick = { clearPeer = null }) { Text(tr("Cancel"), color = Muted) } },
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
            busy = true; backupMsg = tr("Backing up…")
            scope.launch {
                val r = withContext(Dispatchers.IO) { Backup.export(ctx, uri) }
                busy = false
                backupMsg = r.fold({ tr("Backup ready ✅ ({0} media files)", it) }, { tr("Backup failed: {0}", it.message ?: "") })
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true; backupMsg = tr("Restoring…")
            scope.launch {
                val r = withContext(Dispatchers.IO) { Backup.restore(ctx, uri) }
                busy = false
                r.fold({ Backup.restartApp(ctx) }, { backupMsg = tr("Restore failed: {0}", it.message ?: "") })
            }
        }
    }
    SubPage(tr("Chats"), onBack) {
        Caps(tr("Theme"))
        Card {
            listOf(0 to tr("Phone setting"), 1 to tr("Light"), 2 to tr("Dark")).forEach { (v, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        mode = v; Chat.themeMode = v; applyTheme(ctx)
                    }.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(if (mode == v) Accent else Line).padding(if (mode == v) 6.dp else 2.dp)
                            .clip(CircleShape).background(if (mode == v) OnAccent else BubbleThem),
                    )
                }
            }
        }
        Caps(tr("Chat backup"))
        Card {
            Text(
                tr("Chats, photos, videos and voice are saved in one file. Pick Google Drive in the file picker to save it there."),
                fontSize = 13.sp, lineHeight = 19.sp, color = Muted, modifier = Modifier.padding(16.dp),
            )
            LinkRow(tr("Back up now"), sub = "DoubleBubble-backup .zip") {
                if (!busy) exportLauncher.launch("DoubleBubble-backup-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".zip")
            }
            LinkRow(tr("Restore from backup"), sub = tr("Current chats will be replaced")) { if (!busy) askRestore = true }
            backupMsg?.let { Text(it, color = Accent, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
        }
        Card { LinkRow(tr("Starred messages"), onClick = openStarred) }
    }
    if (askRestore) {
        AlertDialog(
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
        Text(tr("Double Bubble locked"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text(tr("Use your fingerprint / screen lock to open"), color = NavMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp))
        PillButton(tr("Unlock"), bg = Color.White, fg = NavBg, onClick = onUnlock)
    }
}

// ---------------- Theme colour ----------------

/** 6 colours grid. Tap cheste ventane app antha aa colour. */
@Composable
fun AccentSwatches(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var cur by remember { mutableStateOf(Chat.accentColor) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AccentChoices.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { a ->
                    val on = a.value == cur
                    val c = Color(a.value)
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Surface)
                            .border(2.dp, if (on) c else Color.Transparent, RoundedCornerShape(18.dp))
                            .clickable { cur = a.value; Chat.accentColor = a.value; applyTheme(ctx) }
                            .padding(vertical = 14.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
                            if (on) androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Filled.Check, null,
                                tint = if (c.luminance() > 0.45f) Color(0xFF14140A) else Color.White, modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(tr(a.name), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeColourSheet(onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(BubbleThem)
                .clickable(enabled = false) {}.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Line))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("Theme colour"), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(tr("Buttons, badges, ticks and tabs change to this colour"), color = Muted, fontSize = 14.sp)
            }
            AccentSwatches()
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Accent).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Text(tr("Apply"), color = OnAccent, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}
