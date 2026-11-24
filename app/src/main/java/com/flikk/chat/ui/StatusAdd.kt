package com.flikk.chat.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.foundation.border
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.delay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.flikk.chat.data.Chat
import com.flikk.chat.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private data class GalleryItem(val uri: Uri, val video: Boolean)

private fun galleryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasGallery(ctx: Context): Boolean = galleryPermissions().any {
    ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
}

/** Phone lo kotha photos + videos (paatavi taruvatha) */
private fun recentMedia(ctx: Context): List<GalleryItem> = try {
    val files = MediaStore.Files.getContentUri("external")
    val type = MediaStore.Files.FileColumns.MEDIA_TYPE
    ctx.contentResolver.query(
        files,
        arrayOf(MediaStore.Files.FileColumns._ID, type),
        "$type=? OR $type=?",
        arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()),
        MediaStore.Files.FileColumns.DATE_ADDED + " DESC",
    )?.use { c ->
        buildList {
            while (c.moveToNext() && size < 150) {
                val id = c.getLong(0)
                val video = c.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                add(GalleryItem(ContentUris.withAppendedId(base, id), video))
            }
        }
    } ?: emptyList()
} catch (_: Exception) { emptyList() }

// ---------------- Add status ----------------

@Composable
fun AddStatusScreen(onClose: () -> Unit, openText: () -> Unit, openVoice: () -> Unit, openPreview: (Uri, Boolean) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasGallery(ctx)) }
    var reload by remember { mutableIntStateOf(0) }
    var filter by remember { mutableIntStateOf(0) } // 0 all, 1 photos, 2 videos
    var filterMenu by remember { mutableStateOf(false) }
    val items by produceState(initialValue = emptyList<GalleryItem>(), granted, reload) {
        value = if (granted) withContext(Dispatchers.IO) { recentMedia(ctx) } else emptyList()
    }
    val askGallery = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasGallery(ctx); reload++
    }
    LaunchedEffect(Unit) { if (!granted) askGallery.launch(galleryPermissions()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val video = ctx.contentResolver.getType(uri)?.startsWith("video") == true
            openPreview(uri, video)
        }
    }

    // Camera: photo / video
    var shot by remember { mutableStateOf<File?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = shot; shot = null
        if (ok && f != null && f.length() > 0) openPreview(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f), false)
        else f?.delete()
    }
    val takeVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val f = shot; shot = null
        if (ok && f != null && f.length() > 0) openPreview(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f), true)
        else f?.delete()
    }
    var wantVideo by remember { mutableStateOf(false) }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) { Toast.makeText(ctx, tr("Camera permission needed"), Toast.LENGTH_SHORT).show(); return@rememberLauncherForActivityResult }
        val f = File(Media.dir(ctx), "shot_${UUID.randomUUID()}." + if (wantVideo) "mp4" else "jpg")
        shot = f
        val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        if (wantVideo) takeVideo.launch(u) else takePhoto.launch(u)
    }

    Column(Modifier.fillMaxSize().background(NavBg)) {
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp)) {
            Box(Modifier.align(Alignment.TopCenter).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f)))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("Close"), tint = Color.White) }
                Text(tr("Add status"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(end = 48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatusOption(Icons.Filled.Edit, tr("Text")) { openText() }
            StatusOption(Icons.Filled.Videocam, tr("Video")) { wantVideo = true; camPerm.launch(Manifest.permission.CAMERA) }
            StatusOption(Icons.Filled.PhotoLibrary, tr("Gallery")) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }
            StatusOption(Icons.Filled.Mic, tr("Voice")) { openVoice() }
        }
        val filterNames = listOf(tr("Recents"), tr("Photos"), tr("Videos"))
        Box(Modifier.padding(start = 16.dp, bottom = 10.dp)) {
            Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { filterMenu = true }.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(filterNames[filter], color = Color.White.copy(alpha = 0.75f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ArrowDropDown, null, tint = Color.White.copy(alpha = 0.75f))
            }
            DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                filterNames.forEachIndexed { i, n -> DropdownMenuItem(text = { Text(n) }, onClick = { filter = i; filterMenu = false }) }
            }
        }
        if (!granted) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("Allow access to your photos and videos to see them here."), color = NavMuted, fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Text(tr("Allow access"), color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(LightPalette.accent)
                        .clickable { askGallery.launch(galleryPermissions()) }.padding(horizontal = 22.dp, vertical = 10.dp))
            }
        } else {
            LazyVerticalGrid(
                GridCells.Fixed(3), Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "camera") {
                    Column(
                        Modifier.aspectRatio(1f).background(Color(0xFF181A22)).border(1.dp, Color.White.copy(alpha = 0.12f))
                            .clickable { wantVideo = false; camPerm.launch(Manifest.permission.CAMERA) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, tr("Camera"), tint = Color(0xFF25D366), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(tr("Camera"), color = Color.White.copy(alpha = 0.75f), fontSize = 17.sp)
                    }
                }
                val shown = when (filter) { 1 -> items.filter { !it.video }; 2 -> items.filter { it.video }; else -> items }
                items(shown, key = { it.uri.toString() }) { g ->
                    Box(Modifier.aspectRatio(1f).background(Color(0xFF22252F)).clickable { openPreview(g.uri, g.video) }) {
                        AsyncImage(model = g.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (g.video) Icon(Icons.Filled.Videocam, tr("Video"), tint = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(4.dp)) {
        Box(Modifier.size(width = 72.dp, height = 56.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

// ---------------- Status preview (caption + post) ----------------

@Composable
fun StatusPreviewScreen(uri: Uri, isVideo: Boolean, onBack: () -> Unit, onPosted: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color.Black).imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.Close, tr("Close"), tint = Color.White) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (isVideo) StatusVideo(uri, Modifier.fillMaxWidth(), loop = true)
            else AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp)).background(Color(0xFF2A2D3A)).padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (caption.isEmpty()) Text(tr("Add a caption…"), color = NavMuted, fontSize = 15.sp)
                BasicTextField(
                    caption, { caption = it.take(500) }, singleLine = true,
                    textStyle = TextStyle(fontFamily = Jakarta, fontSize = 15.sp, color = Color.White),
                    cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Status (My contacts)"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 8.dp))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(LightPalette.accent).clickable(enabled = !busy) {
                    busy = true
                    scope.launch {
                        Toast.makeText(ctx, tr("Posting status..."), Toast.LENGTH_SHORT).show()
                        val err = if (isVideo) Chat.postVideoStatus(uri, caption) else Chat.postPhotoStatus(uri, caption)
                        busy = false
                        if (err != null) Toast.makeText(ctx, tr(err), Toast.LENGTH_LONG).show()
                        else { Toast.makeText(ctx, tr("Status posted ✅"), Toast.LENGTH_SHORT).show(); onPosted() }
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Filled.Send, tr("Post"), tint = Color.White)
            }
        }
    }
}

// ---------------- Video player (status) ----------------

/** Simple VideoView. onReady = duration ms. */
@Composable
fun StatusVideo(
    uri: Uri, modifier: Modifier = Modifier, loop: Boolean = false, paused: Boolean = false,
    onReady: (Long) -> Unit = {}, onEnd: () -> Unit = {},
) {
    var ready by remember(uri) { mutableStateOf(false) }
    AndroidView(
        factory = { c ->
            VideoView(c).apply {
                setVideoURI(uri)
                setOnPreparedListener { mp ->
                    mp.isLooping = loop
                    ready = true
                    onReady(mp.duration.toLong().coerceAtLeast(1000L))
                    start()
                }
                setOnCompletionListener { onEnd() }
                setOnErrorListener { _, _, _ -> onEnd(); true }
            }
        },
        update = { v ->
            if (ready) {
                if (paused && v.isPlaying) v.pause()
                else if (!paused && !v.isPlaying) v.start()
            }
        },
        onRelease = { it.stopPlayback() },
        modifier = modifier,
    )
}

/** Status thumbnail kosam play icon */
@Composable
fun PlayBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

// ---------------- Voice status ----------------

@Composable
fun VoiceStatusScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val rec = remember { com.flikk.chat.data.VoiceRecorder(ctx) }
    var ci by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }
    var result by remember { mutableStateOf<Pair<File, Long>?>(null) }
    val maxMs = 30_000L
    val bg = Color(StatusColors[ci])
    DisposableEffect(Unit) { onDispose { if (rec.isRecording) rec.cancel() } }
    fun stop() {
        recording = false
        result = rec.stop()
        if (result == null) Toast.makeText(ctx, tr("Recording too short"), Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(recording) {
        val t0 = System.currentTimeMillis()
        while (recording) {
            elapsed = System.currentTimeMillis() - t0
            if (elapsed >= maxMs) stop()
            delay(100)
        }
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(ctx, tr("Microphone permission needed for voice messages"), Toast.LENGTH_SHORT).show()
        else if (rec.start(UUID.randomUUID().toString())) { result = null; elapsed = 0; recording = true }
        else Toast.makeText(ctx, tr("Could not start recording"), Toast.LENGTH_SHORT).show()
    }
    Column(Modifier.fillMaxSize().background(bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { result?.first?.delete(); onDone() }) { Icon(Icons.Filled.Close, tr("Close"), tint = Color.White) }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 8.dp)) {
                StatusColors.forEachIndexed { i, c ->
                    Box(Modifier.size(24.dp).border(2.dp, if (i == ci) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                        .padding(3.dp).clip(CircleShape).background(Color(c)).clickable { ci = i })
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val shown = result?.second ?: elapsed
            Text("%d:%02d".format(shown / 60000, (shown / 1000) % 60), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                when { recording -> tr("Recording..."); result != null -> tr("Ready to post"); else -> tr("Tap the mic to record (up to 30 seconds)") },
                color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 30.dp),
            )
            Spacer(Modifier.height(36.dp))
            Box(
                Modifier.size(110.dp).clip(CircleShape).background(if (recording) Color(0xFFE5484D) else Color.White.copy(alpha = 0.22f))
                    .clickable {
                        if (recording) stop()
                        else { result?.first?.delete(); result = null; mic.launch(Manifest.permission.RECORD_AUDIO) }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, tr("Record voice message"), tint = Color.White, modifier = Modifier.size(52.dp)) }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (result != null) IconButton(onClick = { result?.first?.delete(); result = null; elapsed = 0 }) {
                Icon(Icons.Filled.Delete, tr("Delete"), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            val r = result
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(if (r == null) Color.White.copy(alpha = 0.4f) else Color.White)
                    .clickable(enabled = r != null) {
                        if (r != null) {
                            Chat.postVoiceStatus(r.first, StatusColors[ci])
                            Toast.makeText(ctx, tr("Status posted ✅"), Toast.LENGTH_SHORT).show()
                            result = null
                            onDone()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.Send, tr("Post"), tint = bg) }
        }
    }
}
