package com.flikk.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.flikk.chat.data.Chat
import com.flikk.chat.data.Msg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ContactInfoScreen(peer: String, onBack: () -> Unit, openImage: (String) -> Unit, openMediaLinks: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val caller = rememberCaller()
    val version by Chat.db.changes.collectAsState()
    val local = remember(version) { Chat.db.peer(peer) }
    val state by Chat.peerState(peer).collectAsState(initial = null)
    val media by produceState(initialValue = emptyList<Msg>(), version) {
        value = withContext(Dispatchers.IO) {
            Chat.db.messages(peer).filter { (it.type == "image" || it.type == "video") && it.media.isNotEmpty() && !it.deleted }
        }
    }
    val name = local?.name ?: state?.name ?: "Contact"
    val muted = remember(version) { Chat.isMuted(peer) }
    val blocked = remember(version) { Chat.isBlocked(peer) }
    val timer = remember(version) { Chat.timerOf(peer) }
    var askTimer by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(ChatBg).verticalScroll(rememberScrollState())) {
        Row(Modifier.statusBarsPadding().padding(start = 6.dp, top = 14.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back", tint = Ink, modifier = Modifier.size(20.dp)) }
        }
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(name, 104.dp)
            Spacer(Modifier.height(10.dp))
            Text(name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink, textAlign = TextAlign.Center)
            Text(local?.phone ?: "", fontSize = 15.sp, color = Muted)
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Icons.Filled.Call, "Audio", Modifier.weight(1f)) { caller(peer, false) }
            Tile(Icons.Filled.Videocam, "Video", Modifier.weight(1f)) { caller(peer, true) }
            Tile(Icons.AutoMirrored.Filled.Chat, "Message", Modifier.weight(1f), onClick = onBack)
        }
        state?.about?.takeIf { it.isNotBlank() }?.let { about ->
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(BubbleThem).padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(about, fontSize = 15.sp, color = Ink)
                Text("About", fontSize = 12.sp, color = Muted)
            }
        }
        if (media.isNotEmpty()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(BubbleThem).padding(14.dp)) {
                Row {
                    Text(tr("Media, links & docs"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink,
                        modifier = Modifier.weight(1f).clickable(onClick = openMediaLinks))
                    Text("${media.size}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Accent)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val last = media.takeLast(4).reversed()
                    last.forEach { m ->
                        AsyncImage(
                            model = File(m.media), contentDescription = "Media", contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).height(70.dp).clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = m.type == "image") { openImage(m.media) },
                        )
                    }
                    repeat(4 - last.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(BubbleThem).padding(vertical = 4.dp)) {
            InfoRow(Icons.Filled.NotificationsOff, "Mute notifications", if (muted) "On" else "Off") { Chat.setMuted(peer, !muted) }
            InfoRow(Icons.Filled.Timer, "Disappearing messages", Chat.ttlLabel(timer).replaceFirstChar { it.uppercase() }) { askTimer = true }
            InfoRow(Icons.Filled.Lock, "Encryption", "Verify") {
                scope.launch { code = withContext(Dispatchers.IO) { Chat.safetyCode(peer) } ?: "Ee contact paata app vaadutunnaru — encryption ledu" }
            }
            InfoRow(Icons.Filled.DeleteSweep, "Clear chat", "") { askClear = true }
            InfoRow(Icons.Filled.Block, if (blocked) "Unblock $name" else "Block $name", "", color = Color(0xFFB91C1C)) { Chat.setBlocked(peer, !blocked) }
        }
        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }

    if (askTimer) {
        AlertDialog(
            onDismissRequest = { askTimer = false },
            title = { Text("Disappearing messages") },
            text = {
                Column {
                    listOf(0L, 86_400_000L, 7 * 86_400_000L, 90 * 86_400_000L).forEach { v ->
                        Text(
                            (if (v == timer) "● " else "○ ") + Chat.ttlLabel(v).replaceFirstChar { it.uppercase() },
                            fontSize = 16.sp, color = if (v == timer) Accent else Ink,
                            modifier = Modifier.fillMaxWidth().clickable { askTimer = false; if (v != timer) Chat.setTimer(peer, v) }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { askTimer = false }) { Text("Close", color = Muted) } },
            containerColor = BubbleThem,
        )
    }
    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text("Clear this chat?") },
            text = { Text("Messages, photos annee mee phone nunchi delete avutayi.", color = Muted) },
            confirmButton = { TextButton(onClick = { askClear = false; Chat.clearChat(peer) }) { Text("Clear", color = Danger) } },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text("Cancel", color = Muted) } },
            containerColor = BubbleThem,
        )
    }
    code?.let { c ->
        AlertDialog(
            onDismissRequest = { code = null },
            title = { Text("🔒 Encryption") },
            text = {
                Column {
                    Text("Mee iddari phones lo ide code unte, connection safe.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(c, fontSize = 16.sp, letterSpacing = 1.sp, color = Ink)
                }
            },
            confirmButton = { TextButton(onClick = { code = null }) { Text("OK", color = Accent) } },
            containerColor = BubbleThem,
        )
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(72.dp).clip(RoundedCornerShape(18.dp)).background(BubbleThem).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, color: Color = Ink, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (color == Ink) Muted else color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, fontSize = 14.sp, color = Muted)
    }
}
