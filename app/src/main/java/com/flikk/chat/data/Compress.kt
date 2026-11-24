package com.flikk.chat.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/**
 * Pampe mundu video / audio ni chinnadi chestundi (WhatsApp laaga).
 * Video: H.264 720p (leda takkuva), duration batti bitrate — 16 MB lopu ravali.
 * Audio (MP3): AAC .m4a.
 */
object Compress {
    private const val TARGET_BYTES = 15L * 1024 * 1024
    private const val AUDIO_BR = 96_000L

    private data class Info(val w: Int, val h: Int, val durMs: Long)

    private fun info(ctx: Context, uri: Uri): Info? = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(ctx, uri)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        if (rot == 90 || rot == 270) Info(h, w, d) else Info(w, h, d)
    } catch (_: Exception) { null }

    private fun even(v: Double): Int = max(2, (v.toInt() / 2) * 2)

    /** true = out lo compressed video ready */
    suspend fun video(ctx: Context, uri: Uri, out: File): Boolean {
        val inf = info(ctx, uri) ?: return false
        if (inf.w <= 0 || inf.h <= 0) return false
        val sec = max(1L, inf.durMs / 1000)
        val vbr = ((TARGET_BYTES * 8) / sec - AUDIO_BR).coerceIn(250_000L, 2_000_000L)
        val shortTarget = when {
            vbr >= 1_200_000 -> 720
            vbr >= 600_000 -> 540
            else -> 480
        }
        val scale = min(1.0, shortTarget.toDouble() / min(inf.w, inf.h))
        val effects = if (scale < 1.0) {
            Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(even(inf.w * scale), even(inf.h * scale), Presentation.LAYOUT_SCALE_TO_FIT)))
        } else Effects.EMPTY
        val item = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(effects).build()
        return run(ctx, item, out) { b ->
            b.setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(ctx.applicationContext)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(vbr.toInt()).build())
                        .build()
                )
        }
    }

    /** MP3 / pedda audio → AAC .m4a */
    suspend fun audio(ctx: Context, uri: Uri, out: File): Boolean {
        val item = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setRemoveVideo(true).build()
        return run(ctx, item, out) { b -> b.setAudioMimeType(MimeTypes.AUDIO_AAC) }
    }

    private suspend fun run(ctx: Context, item: EditedMediaItem, out: File, setup: (Transformer.Builder) -> Transformer.Builder): Boolean {
        out.delete()
        val ok = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                try {
                    val t = setup(Transformer.Builder(ctx.applicationContext))
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (cont.isActive) cont.resume(true)
                            }
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                if (cont.isActive) cont.resume(false)
                            }
                        })
                        .build()
                    t.start(item, out.absolutePath)
                    cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { try { t.cancel() } catch (_: Exception) {} } }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(false)
                }
            }
        }
        if (!ok || !out.exists() || out.length() == 0L) { out.delete(); return false }
        return true
    }
}
