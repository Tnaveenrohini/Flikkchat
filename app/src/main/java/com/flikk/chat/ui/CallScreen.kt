package com.flikk.chat.ui

import android.Manifest
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.graphics.Brush
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.flikk.chat.data.CallState
import com.flikk.chat.data.Calls
import com.flikk.chat.data.Chat
import com.flikk.chat.data.Phase
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private val Green = Color(0xFF22C55E)
private val Red = Color(0xFFEF4444)

/** Call start cheyyadaniki (permissions adigi) */
@Composable
fun rememberCaller(): (String, Boolean) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val p = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (res.values.all { it }) Calls.start(p.first, p.second)
        else Toast.makeText(ctx, tr(if (p.second) "Microphone / camera permission needed for calls" else "Microphone permission needed for calls"), Toast.LENGTH_LONG).show()
    }
    return { peer, video ->
        if (Calls.hasPermissions(video)) Calls.start(peer, video)
        else {
            pending = peer to video
            launcher.launch(
                if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                else arrayOf(Manifest.permission.RECORD_AUDIO)
            )
        }
    }
}

@Composable
private fun VideoView(track: VideoTrack, mirror: Boolean, overlay: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    val renderer = remember {
        SurfaceViewRenderer(ctx).apply {
            init(Calls.egl.eglBaseContext, null)
            setMirror(mirror)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
            if (overlay) setZOrderMediaOverlay(true)
        }
    }
    DisposableEffect(track) {
        try { track.addSink(renderer) } catch (_: Exception) {}
        onDispose { try { track.removeSink(renderer) } catch (_: Exception) {} }
    }
    DisposableEffect(Unit) { onDispose { renderer.release() } }
    AndroidView(factory = { renderer }, modifier = modifier)
}

@Composable
fun CallScreen(st: CallState) {
    val ctx = LocalContext.current
    val remote by Calls.remoteVideo.collectAsState()
    val local by Calls.localVideo.collectAsState()
    val name = Chat.nameOf(st.peer)
    BackHandler(enabled = true) { /* call lo back pani cheyyadu */ }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    val acceptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) Calls.accept()
        else { Toast.makeText(ctx, tr("Permission needed for calls"), Toast.LENGTH_LONG).show(); Calls.decline() }
    }
    val status = when (st.phase) {
        Phase.OUTGOING -> tr("Calling…")
        Phase.INCOMING -> if (st.video) tr("Incoming video call") else tr("Incoming voice call")
        Phase.CONNECTING -> tr("Connecting…")
        Phase.CONNECTED -> tr("Encrypted") + " · " + duration(now - st.connectedAt)
        Phase.ENDED -> tr(st.endReason)
    }

    if (st.phase == Phase.INCOMING) {
        IncomingLayout(st, name) {
            if (Calls.hasPermissions(st.video)) Calls.accept()
            else acceptLauncher.launch(
                if (st.video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                else arrayOf(Manifest.permission.RECORD_AUDIO)
            )
        }
        return
    }
    if (!st.video) {
        VoiceCallLayout(st, name, status) {
            if (Calls.hasPermissions(false)) Calls.accept() else acceptLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
        return
    }

    Box(Modifier.fillMaxSize().background(NavBg)) {
        val r = remote
        val showRemote = st.video && r != null && st.phase == Phase.CONNECTED
        if (showRemote && r != null) {
            VideoView(r, mirror = false, overlay = false, modifier = Modifier.fillMaxSize())
        } else {
            // Voice call / inka connect avvaledu: peda avatar
            Column(
                Modifier.fillMaxSize().background(Color(0xFF241E3D)),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Avatar(name, 128.dp)
                Spacer(Modifier.height(22.dp))
                Text(name, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(status, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = if (st.phase == Phase.ENDED) Color(0xFFFCA5A5) else Color(0xFFD9D3F5))
                Spacer(Modifier.height(120.dp))
            }
        }

        // Paina bar (video lo)
        if (showRemote) {
            Box(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(NavBg.copy(alpha = 0.8f), Color.Transparent)))
                    .statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 40.dp),
            ) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, null, tint = Color(0xFFD9D3F5), modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(status, fontSize = 12.sp, color = Color(0xFFD9D3F5))
                    }
                }
            }
        }

        // Naa camera (chinna box)
        val l = local
        if (st.video && l != null && !st.cameraOff && st.phase != Phase.INCOMING && st.phase != Phase.ENDED) {
            VideoView(
                l, mirror = true, overlay = true,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 92.dp, end = 16.dp)
                    .size(width = 112.dp, height = 160.dp).clip(RoundedCornerShape(22.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(22.dp)),
            )
        }

        when (st.phase) {
            Phase.INCOMING -> Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BigBtn(Icons.Filled.CallEnd, EndRed, tr("Decline")) { Calls.decline() }
                BigBtn(if (st.video) Icons.Filled.Videocam else Icons.Filled.Call, Color(0xFF16A34A), tr("Accept")) {
                    if (Calls.hasPermissions(st.video)) Calls.accept()
                    else acceptLauncher.launch(
                        if (st.video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                        else arrayOf(Manifest.permission.RECORD_AUDIO)
                    )
                }
            }
            Phase.ENDED -> {}
            else -> Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 26.dp)
                    .fillMaxWidth().clip(RoundedCornerShape(34.dp)).background(NavBg.copy(alpha = 0.78f))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (st.video) {
                    CtrlBtn(Icons.Filled.Cameraswitch, tr("Flip camera")) { Calls.switchCamera() }
                    CtrlBtn(if (st.cameraOff) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                        if (st.cameraOff) tr("Turn camera on") else tr("Turn camera off"), active = st.cameraOff) { Calls.toggleCamera() }
                } else {
                    CtrlBtn(Icons.AutoMirrored.Filled.VolumeUp, tr("Speaker"), active = st.speaker) { Calls.toggleSpeaker() }
                }
                CtrlBtn(if (st.muted) Icons.Filled.MicOff else Icons.Filled.Mic, if (st.muted) tr("Unmute") else tr("Mute microphone"), active = st.muted) { Calls.toggleMute() }
                Box(
                    Modifier.size(width = 72.dp, height = 56.dp).clip(RoundedCornerShape(28.dp)).background(EndRed)
                        .clickable { Calls.hangup() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.CallEnd, tr("End call"), tint = Color.White, modifier = Modifier.size(26.dp)) }
            }
        }
    }
}

@Composable
private fun CtrlBtn(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(if (active) Color.White else Color.White.copy(alpha = 0.14f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (active) NavBg else Color.White, modifier = Modifier.size(23.dp)) }
}

@Composable
private fun BigBtn(icon: ImageVector, bg: Color, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 📞 Voice call — design lo laaga rings tho avatar */
@Composable
private fun VoiceCallLayout(st: CallState, name: String, status: String, onAccept: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(NavBg).statusBarsPadding().navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = NavMuted, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(6.dp))
            Text(tr("End-to-end encrypted"), fontSize = 12.sp, color = NavMuted)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(220.dp).border(2.dp, Color(0x2EC9BFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(180.dp).border(2.dp, Color(0x52C9BFFF), CircleShape), contentAlignment = Alignment.Center) {
                    Avatar(name, 140.dp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(name, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(
                if (st.phase == Phase.CONNECTED) status.substringAfter(" · ") else status,
                fontSize = 16.sp, color = if (st.phase == Phase.ENDED) Color(0xFFFCA5A5) else Color(0xFFC3C5D3),
            )
        }
        when (st.phase) {
            Phase.INCOMING -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigBtn(Icons.Filled.CallEnd, EndRed, tr("Decline")) { Calls.decline() }
                BigBtn(Icons.Filled.Call, Color(0xFF16A34A), tr("Accept"), onClick = onAccept)
            }
            Phase.ENDED -> Spacer(Modifier.height(76.dp))
            else -> {
                Row(Modifier.padding(bottom = 34.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    GridBtn(Icons.AutoMirrored.Filled.VolumeUp, tr("Speaker"), st.speaker) { Calls.toggleSpeaker() }
                    GridBtn(if (st.muted) Icons.Filled.MicOff else Icons.Filled.Mic, if (st.muted) tr("Unmute") else tr("Mute"), st.muted) { Calls.toggleMute() }
                }
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(EndRed).clickable { Calls.hangup() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.CallEnd, tr("End call"), tint = Color.White, modifier = Modifier.size(32.dp)) }
            }
        }
    }
}

@Composable
private fun GridBtn(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(if (active) Color.White else Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (active) NavBg else Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

private val QuickReplies = listOf(
    tr("Can't talk now. I'll call you back."),
    tr("I'm in a meeting."),
    tr("On my way."),
    tr("Call me later."),
)

/** 📲 Incoming call screen */
@Composable
private fun IncomingLayout(st: CallState, name: String, onAccept: () -> Unit) {
    var showReplies by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(NavBg).statusBarsPadding().navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (st.video) Icons.Filled.Videocam else Icons.Filled.Call, null, tint = NavMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(tr(if (st.video) tr("Double Bubble video call") else tr("Double Bubble voice call")), fontSize = 13.sp, color = NavMuted, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(200.dp).border(2.dp, Color(0x2EC9BFFF), CircleShape), contentAlignment = Alignment.Center) {
                Avatar(name, 150.dp)
            }
            Spacer(Modifier.height(22.dp))
            Text(name, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(tr("Ringing…"), fontSize = 16.sp, color = Color(0xFFC3C5D3))
        }
        Row(
            Modifier.clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { showReplies = true }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.Message, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("Reply with message"), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(36.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            BigBtn(Icons.Filled.CallEnd, EndRed, tr("Decline")) { Calls.decline() }
            BigBtn(if (st.video) Icons.Filled.Videocam else Icons.Filled.Call, Color(0xFF16A34A), tr("Accept"), onClick = onAccept)
        }
    }
    if (showReplies) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showReplies = false },
            title = { Text(tr("Reply with message")) },
            text = {
                Column {
                    QuickReplies.forEach { r ->
                        Text(tr(r), color = Ink, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable {
                            showReplies = false
                            Calls.declineWithMessage(tr(r))
                        }.padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { showReplies = false }) { Text(tr("Cancel"), color = Muted) } },
            containerColor = BubbleThem,
        )
    }
}
