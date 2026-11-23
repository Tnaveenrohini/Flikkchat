package com.flikk.chat.ui

import android.Manifest
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
        else Toast.makeText(ctx, "Call ki mic${if (p.second) " / camera" else ""} permission kavali", Toast.LENGTH_LONG).show()
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
        else { Toast.makeText(ctx, "Call ki permission kavali", Toast.LENGTH_LONG).show(); Calls.decline() }
    }
    val status = when (st.phase) {
        Phase.OUTGOING -> "Calling…"
        Phase.INCOMING -> if (st.video) "Incoming video call" else "Incoming voice call"
        Phase.CONNECTING -> "Connecting…"
        Phase.CONNECTED -> "Encrypted · " + duration(now - st.connectedAt)
        Phase.ENDED -> st.endReason
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
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
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.8f), Color.Transparent)))
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
                BigBtn(Icons.Filled.CallEnd, EndRed, "Decline") { Calls.decline() }
                BigBtn(if (st.video) Icons.Filled.Videocam else Icons.Filled.Call, Color(0xFF16A34A), "Accept") {
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
                    .fillMaxWidth().clip(RoundedCornerShape(34.dp)).background(Ink.copy(alpha = 0.78f))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (st.video) {
                    CtrlBtn(Icons.Filled.Cameraswitch, "Flip camera") { Calls.switchCamera() }
                    CtrlBtn(if (st.cameraOff) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                        if (st.cameraOff) "Turn camera on" else "Turn camera off", active = st.cameraOff) { Calls.toggleCamera() }
                } else {
                    CtrlBtn(Icons.AutoMirrored.Filled.VolumeUp, "Speaker", active = st.speaker) { Calls.toggleSpeaker() }
                }
                CtrlBtn(if (st.muted) Icons.Filled.MicOff else Icons.Filled.Mic, if (st.muted) "Unmute" else "Mute microphone", active = st.muted) { Calls.toggleMute() }
                Box(
                    Modifier.size(width = 72.dp, height = 56.dp).clip(RoundedCornerShape(28.dp)).background(EndRed)
                        .clickable { Calls.hangup() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.CallEnd, "End call", tint = Color.White, modifier = Modifier.size(26.dp)) }
            }
        }
    }
}

@Composable
private fun CtrlBtn(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(if (active) Color.White else Color.White.copy(alpha = 0.14f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (active) Ink else Color.White, modifier = Modifier.size(23.dp)) }
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
