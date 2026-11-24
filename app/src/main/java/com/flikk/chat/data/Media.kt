package com.flikk.chat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import kotlin.math.max
import kotlin.math.min

object Media {
    const val MAX_VIDEO_BYTES = 16L * 1024 * 1024
    private const val MAX_SIDE = 1600

    fun dir(ctx: Context): File = File(ctx.filesDir, "media").apply { mkdirs() }

    fun ext(mime: String): String = when {
        mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
        mime.startsWith("image/") -> "jpg"
        mime.contains("mp4") -> "mp4"
        mime.contains("3gpp") -> "3gp"
        mime.contains("webm") -> "webm"
        mime.contains("matroska") -> "mkv"
        mime.startsWith("audio/") -> "m4a"
        mime.startsWith("video/") -> "mp4"
        else -> "bin"
    }

    /** Photo ni chinnadi (max 1600px, JPEG 80%) chesi app folder lo save. */
    fun prepareImage(ctx: Context, uri: Uri, id: String, hd: Boolean = false): File {
        val maxSide = if (hd) 3000 else MAX_SIDE
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = min(1f, maxSide.toFloat() / max(w, h))
                dec.setTargetSize(max(1, (w * scale).toInt()), max(1, (h * scale).toInt()))
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: error("Photo open avvaledu")
        }
        val out = File(dir(ctx), "$id.jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, if (hd) 92 else 80, it) }
        return out
    }

    fun size(ctx: Context, uri: Uri): Long =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L

    /** Video ni app folder loki copy. null = chala pedda video. */
    fun prepareVideo(ctx: Context, uri: Uri, id: String): Pair<File, String>? {
        val sz = size(ctx, uri)
        if (sz > MAX_VIDEO_BYTES) return null
        val mime = ctx.contentResolver.getType(uri) ?: "video/mp4"
        val out = File(dir(ctx), "$id.${ext(mime)}")
        ctx.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            ?: error("Video open avvaledu")
        if (out.length() > MAX_VIDEO_BYTES) { out.delete(); return null }
        return out to mime
    }

    fun durationMs(path: String): Long = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(path)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        d
    } catch (_: Exception) { 0L }
}

/** Voice message recorder (AAC .m4a) */
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording get() = rec != null

    fun start(id: String): Boolean = try {
        val f = File(Media.dir(ctx), "$id.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(48_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        rec = r; file = f; startedAt = System.currentTimeMillis()
        true
    } catch (_: Exception) {
        rec?.release(); rec = null
        false
    }

    /** (file, duration) — chala chinnadi (< 1 sec) ayite null */
    fun stop(): Pair<File, Long>? {
        val r = rec ?: return null
        val f = file
        val dur = System.currentTimeMillis() - startedAt
        rec = null
        return try {
            r.stop(); r.release()
            if (f == null || dur < 800) { f?.delete(); null } else f to dur
        } catch (_: Exception) {
            r.release(); f?.delete(); null
        }
    }

    fun cancel() {
        val r = rec ?: return
        rec = null
        try { r.stop() } catch (_: Exception) {}
        r.release()
        file?.delete()
    }
}
