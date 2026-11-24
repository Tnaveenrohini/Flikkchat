package com.flikk.chat.ui

import android.Manifest
import android.provider.ContactsContract
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Link
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
            tr("Firebase is not set up yet.\n\nPut google-services.json in the app/ folder of the repo, ") +
                tr("chat will start after the new APK is built."),
            textAlign = TextAlign.Center, color = Muted,
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

// ---------------- New chat (contacts) ----------------

@Composable
fun ContactsScreen(onBack: () -> Unit, openChat: (String) -> Unit, openNewGroup: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var contacts by remember { mutableStateOf<List<Contact>?>(null) }
    var q by remember { mutableStateOf("") }
    val addContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (granted) contacts = null
    }
    LaunchedEffect(granted, contacts == null) {
        if (!granted) { launcher.launch(Manifest.permission.READ_CONTACTS); return@LaunchedEffect }
        if (contacts == null) contacts = Chat.findContacts()
    }
    val invite = {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(
            Intent.EXTRA_TEXT,
            tr("Hey! Let's chat on Flikkchat 💬 Made in India app 🇮🇳") + "\nhttps://github.com/Tnaveenrohini/Flikkchat/releases/latest"
        )
        ctx.startActivity(Intent.createChooser(i, tr("Invite")))
    }
    val shown = contacts.orEmpty().filter {
        q.isBlank() || it.name.contains(q.trim(), true) || it.phone.filter { c -> c.isDigit() }.contains(q.filter { c -> c.isDigit() }.ifEmpty { "\u0000" })
    }

    LazyColumn(Modifier.fillMaxSize().background(Bg)) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, tr("Back"), tint = Ink, modifier = Modifier.size(20.dp)) }
                Column {
                    Text(tr("New chat"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    Text(contacts?.let { tr("{0} contacts on Flikkchat", it.size) } ?: tr("Finding contacts…"), fontSize = 12.sp, color = Muted)
                }
            }
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp)) { SearchPill(q, { q = it }, tr("Search name or number")) }
            ShortcutRow(Icons.Filled.GroupAdd, tr("New group"), openNewGroup)
            ShortcutRow(Icons.Filled.PersonAdd, tr("New contact")) {
                addContact.launch(Intent(Intent.ACTION_INSERT).setType(ContactsContract.Contacts.CONTENT_TYPE))
            }
            ShortcutRow(Icons.Filled.Link, tr("Invite with link")) { invite() }
            SectionLabel(tr("Contacts on Flikkchat"))
        }
        when {
            !granted -> item {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("Allow contacts to find your friends"), color = Muted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    PillButton(tr("Allow contacts")) { launcher.launch(Manifest.permission.READ_CONTACTS) }
                }
            }
            contacts == null -> item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
            }
            shown.isEmpty() -> item {
                Text(
                    if (q.isNotBlank()) tr("No results for \"{0}\"", q) else tr("None of your contacts are on Flikkchat yet.\nInvite them with 'Invite with link' above!"),
                    color = Muted, textAlign = TextAlign.Center, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                )
            }
            else -> items(shown, key = { it.uid }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { openChat(c.uid) }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c.name, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(c.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                        Text(c.phone, color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
    }
}

@Composable
private fun ShortcutRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = OnAccent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
    }
}
