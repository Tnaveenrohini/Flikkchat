package com.flikk.chat.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Chat backup: database + photos/videos/voice anni okka .zip file lo.
 * File picker lo Google Drive select chesthe Drive lo save avutundi.
 */
object Backup {
    private const val DB_NAME = "flikkchat.db"

    fun export(ctx: Context, uri: Uri, includeVideos: Boolean = true): Result<Int> = runCatching {
        Chat.db.checkpoint()
        val dbFile = ctx.getDatabasePath(DB_NAME)
        val videoExt = setOf("mp4", "3gp", "webm", "mkv")
        val media = Media.dir(ctx).listFiles()?.filter { it.isFile && (includeVideos || it.extension.lowercase() !in videoExt) } ?: emptyList()
        ctx.contentResolver.openOutputStream(uri)?.use { os ->
            ZipOutputStream(os.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("db/$DB_NAME"))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                for (f in media) {
                    zip.putNextEntry(ZipEntry("media/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("Could not open file")
        Chat.lastBackupAt = System.currentTimeMillis()
        Chat.lastBackupSize = dbFile.length() + media.sumOf { it.length() }
        media.size
    }

    /** Restore chesi app restart chestundi. */
    fun restore(ctx: Context, uri: Uri): Result<Unit> = runCatching {
        val tmp = File(ctx.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var hasDb = false
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    // Safety: folder bayataki vellakunda
                    val ok = (name == "db/$DB_NAME") || (name.startsWith("media/") && !name.contains(".."))
                    if (!e.isDirectory && ok) {
                        val out = File(tmp, name)
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                        if (name == "db/$DB_NAME") hasDb = true
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("File open avvaledu")
        if (!hasDb) error("Idi Flikkchat backup file kaadu")

        Chat.db.close()
        val dbFile = ctx.getDatabasePath(DB_NAME)
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        File(tmp, "db/$DB_NAME").copyTo(dbFile, overwrite = true)
        val mediaDir = Media.dir(ctx)
        File(tmp, "media").listFiles()?.forEach { it.copyTo(File(mediaDir, it.name), overwrite = true) }
        tmp.deleteRecursively()
    }

    fun restartApp(ctx: Context) {
        val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
        ctx.startActivity(Intent.makeRestartActivityTask(i.component))
        Runtime.getRuntime().exit(0)
    }
}
