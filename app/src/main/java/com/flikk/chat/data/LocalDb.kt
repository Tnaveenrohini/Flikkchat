package com.flikk.chat.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Message status: 0 = pending (clock), 1 = sent ✓, 2 = delivered ✓✓, 3 = read (blue ✓✓)
 * type: text | image | video | audio | sys
 * peer: 1-to-1 ayite friend uid, group ayite "g:<groupId>"
 */
data class Msg(
    val id: String,
    val peer: String,
    val fromMe: Boolean,
    val text: String,
    val ts: Long,
    val status: Int,
    val sender: String = "",
    val type: String = "text",
    val media: String = "",
    val mime: String = "",
    val dur: Long = 0,
    val replyId: String = "",
    val replyText: String = "",
    val replyName: String = "",
    val fwd: Boolean = false,
    val deleted: Boolean = false,
    val starred: Boolean = false,
)

data class ChatRow(
    val peer: String,
    val name: String,
    val phone: String,
    val lastText: String,
    val lastTs: Long,
    val lastFromMe: Boolean,
    val lastStatus: Int,
    val unread: Int,
)

data class Peer(val uid: String, val name: String, val phone: String)

data class Group(val gid: String, val name: String, val members: List<String>, val admins: List<String>)

fun isGroup(peer: String) = peer.startsWith("g:")
fun gidOf(peer: String) = peer.removePrefix("g:")

/** Chat list lo last message preview */
fun preview(m: Msg): String = when {
    m.deleted -> "🚫 This message was deleted"
    m.type == "image" -> "📷 Photo" + if (m.text.isNotBlank()) " · ${m.text}" else ""
    m.type == "video" -> "🎥 Video" + if (m.text.isNotBlank()) " · ${m.text}" else ""
    m.type == "audio" -> "🎤 Voice message"
    else -> m.text
}

/**
 * Chats annee ikkade — user phone lo (SQLite). Server lo emi save avvadu.
 */
class LocalDb(ctx: Context) : SQLiteOpenHelper(ctx, "flikkchat.db", null, 2) {

    private val _changes = MutableStateFlow(0L)
    /** Prathi change ki number perugutundi — UI dinni chusi refresh avutundi. */
    val changes: StateFlow<Long> = _changes

    fun bump() = _changes.update { it + 1 }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE messages(
                id TEXT PRIMARY KEY,
                peer TEXT NOT NULL,
                from_me INTEGER NOT NULL,
                text TEXT NOT NULL,
                ts INTEGER NOT NULL,
                status INTEGER NOT NULL,
                read_by_me INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX idx_msg_peer ON messages(peer, ts)")
        db.execSQL("CREATE TABLE peers(uid TEXT PRIMARY KEY, name TEXT NOT NULL, phone TEXT NOT NULL)")
        onUpgrade(db, 1, 2)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            for (col in listOf(
                "sender TEXT NOT NULL DEFAULT ''",
                "type TEXT NOT NULL DEFAULT 'text'",
                "media TEXT NOT NULL DEFAULT ''",
                "mime TEXT NOT NULL DEFAULT ''",
                "dur INTEGER NOT NULL DEFAULT 0",
                "reply_id TEXT NOT NULL DEFAULT ''",
                "reply_text TEXT NOT NULL DEFAULT ''",
                "reply_name TEXT NOT NULL DEFAULT ''",
                "fwd INTEGER NOT NULL DEFAULT 0",
                "deleted INTEGER NOT NULL DEFAULT 0",
                "starred INTEGER NOT NULL DEFAULT 0",
            )) db.execSQL("ALTER TABLE messages ADD COLUMN $col")
            db.execSQL("CREATE TABLE IF NOT EXISTS groups(gid TEXT PRIMARY KEY, name TEXT NOT NULL, members TEXT NOT NULL, admins TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS rcpt(msg_id TEXT NOT NULL, uid TEXT NOT NULL, status INTEGER NOT NULL, PRIMARY KEY(msg_id, uid))")
        }
    }

    private val COLS = "id, peer, from_me, text, ts, status, sender, type, media, mime, dur, reply_id, reply_text, reply_name, fwd, deleted, starred"

    private fun Cursor.toMsg() = Msg(
        id = getString(0), peer = getString(1), fromMe = getInt(2) == 1, text = getString(3),
        ts = getLong(4), status = getInt(5), sender = getString(6), type = getString(7),
        media = getString(8), mime = getString(9), dur = getLong(10), replyId = getString(11),
        replyText = getString(12), replyName = getString(13), fwd = getInt(14) == 1,
        deleted = getInt(15) == 1, starred = getInt(16) == 1,
    )

    private fun query(sql: String, args: Array<String>?): List<Msg> =
        readableDatabase.rawQuery(sql, args).use { c -> buildList { while (c.moveToNext()) add(c.toMsg()) } }

    /** true = kotha message (insert ayyindi), false = already undi */
    fun insertMsg(m: Msg, readByMe: Boolean = m.fromMe): Boolean {
        val cv = ContentValues().apply {
            put("id", m.id); put("peer", m.peer); put("from_me", if (m.fromMe) 1 else 0)
            put("text", m.text); put("ts", m.ts); put("status", m.status)
            put("read_by_me", if (readByMe) 1 else 0)
            put("sender", m.sender); put("type", m.type); put("media", m.media); put("mime", m.mime)
            put("dur", m.dur); put("reply_id", m.replyId); put("reply_text", m.replyText)
            put("reply_name", m.replyName); put("fwd", if (m.fwd) 1 else 0)
            put("deleted", if (m.deleted) 1 else 0); put("starred", if (m.starred) 1 else 0)
        }
        val row = writableDatabase.insertWithOnConflict("messages", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        if (row != -1L) bump()
        return row != -1L
    }

    fun msg(id: String): Msg? = query("SELECT $COLS FROM messages WHERE id=?", arrayOf(id)).firstOrNull()

    /** Status mundu ki matrame vellali (read → delivered ki venakki raadu). */
    fun updateStatus(id: String, status: Int) {
        val cv = ContentValues().apply { put("status", status) }
        val n = writableDatabase.update("messages", cv, "id=? AND status<?", arrayOf(id, status.toString()))
        if (n > 0) bump()
    }

    fun setStarred(id: String, starred: Boolean) {
        val cv = ContentValues().apply { put("starred", if (starred) 1 else 0) }
        writableDatabase.update("messages", cv, "id=?", arrayOf(id)); bump()
    }

    fun markDeleted(id: String) {
        val cv = ContentValues().apply { put("deleted", 1); put("text", ""); put("media", ""); put("starred", 0) }
        writableDatabase.update("messages", cv, "id=?", arrayOf(id)); bump()
    }

    fun deleteForMe(id: String) {
        writableDatabase.delete("messages", "id=?", arrayOf(id)); bump()
    }

    fun messages(peer: String): List<Msg> = query("SELECT $COLS FROM messages WHERE peer=? ORDER BY ts ASC", arrayOf(peer))

    fun pending(): List<Msg> = query("SELECT $COLS FROM messages WHERE from_me=1 AND status=0 AND deleted=0 ORDER BY ts ASC", null)

    fun starred(): List<Msg> = query("SELECT $COLS FROM messages WHERE starred=1 ORDER BY ts DESC", null)

    fun search(q: String): List<Msg> = query(
        "SELECT $COLS FROM messages WHERE deleted=0 AND type!='sys' AND text LIKE ? ORDER BY ts DESC LIMIT 100",
        arrayOf("%$q%")
    )

    /** Chat open chesinappudu: chadavani messages (id, sender) ichi, vaatini read ga mark chestundi. */
    fun takeUnread(peer: String): List<Pair<String, String>> {
        val ids = readableDatabase.rawQuery(
            "SELECT id, sender FROM messages WHERE peer=? AND from_me=0 AND read_by_me=0", arrayOf(peer)
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) } }
        if (ids.isNotEmpty()) {
            val cv = ContentValues().apply { put("read_by_me", 1) }
            writableDatabase.update("messages", cv, "peer=? AND from_me=0 AND read_by_me=0", arrayOf(peer))
            bump()
        }
        return ids
    }

    fun chats(): List<ChatRow> {
        val rows = readableDatabase.rawQuery(
            """SELECT p.uid, p.name, p.phone, m.id,
                  (SELECT COUNT(*) FROM messages u WHERE u.peer=p.uid AND u.from_me=0 AND u.read_by_me=0)
               FROM peers p
               JOIN messages m ON m.id = (SELECT id FROM messages WHERE peer=p.uid ORDER BY ts DESC LIMIT 1)
               ORDER BY m.ts DESC""",
            null
        ).use { c -> buildList { while (c.moveToNext()) add(listOf(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4).toString())) } }
        return rows.mapNotNull { r ->
            val m = msg(r[3]) ?: return@mapNotNull null
            val who = if (isGroup(m.peer) && !m.fromMe && m.type != "sys") (peer(m.sender)?.name?.let { "$it: " } ?: "") else ""
            ChatRow(r[0], r[1], r[2], who + preview(m), m.ts, m.fromMe && m.type != "sys", m.status, r[4].toInt())
        }
    }

    fun peer(uid: String): Peer? = readableDatabase.rawQuery(
        "SELECT uid, name, phone FROM peers WHERE uid=?", arrayOf(uid)
    ).use { c -> if (c.moveToFirst()) Peer(c.getString(0), c.getString(1), c.getString(2)) else null }

    /** Contacts (groups kaakunda) */
    fun people(): List<Peer> = readableDatabase.rawQuery(
        "SELECT uid, name, phone FROM peers WHERE uid NOT LIKE 'g:%' AND phone!='' ORDER BY name COLLATE NOCASE", null
    ).use { c -> buildList { while (c.moveToNext()) add(Peer(c.getString(0), c.getString(1), c.getString(2))) } }

    /** overwriteName=true: contacts lo unna peru tho update chestundi. */
    fun upsertPeer(uid: String, name: String, phone: String, overwriteName: Boolean) {
        val existing = peer(uid)
        val cv = ContentValues().apply {
            put("uid", uid)
            put("name", if (existing != null && !overwriteName) existing.name else name)
            put("phone", phone.ifEmpty { existing?.phone ?: "" })
        }
        writableDatabase.insertWithOnConflict("peers", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        bump()
    }

    // ---------- Groups ----------

    fun group(gid: String): Group? = readableDatabase.rawQuery(
        "SELECT gid, name, members, admins FROM groups WHERE gid=?", arrayOf(gid)
    ).use { c ->
        if (c.moveToFirst()) Group(c.getString(0), c.getString(1), c.getString(2).split(",").filter { it.isNotEmpty() },
            c.getString(3).split(",").filter { it.isNotEmpty() }) else null
    }

    fun upsertGroup(g: Group) {
        val cv = ContentValues().apply {
            put("gid", g.gid); put("name", g.name)
            put("members", g.members.joinToString(",")); put("admins", g.admins.joinToString(","))
        }
        writableDatabase.insertWithOnConflict("groups", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        upsertPeer("g:${g.gid}", g.name, "", overwriteName = true)
    }

    // ---------- Group receipts ----------

    /** Group lo member status save chesi, andari kanna takkuva status return chestundi. */
    fun groupReceipt(msgId: String, uid: String, status: Int, members: List<String>): Int {
        writableDatabase.execSQL("INSERT OR IGNORE INTO rcpt(msg_id, uid, status) VALUES(?,?,?)", arrayOf<Any>(msgId, uid, status))
        writableDatabase.execSQL("UPDATE rcpt SET status=? WHERE msg_id=? AND uid=? AND status<?", arrayOf<Any>(status, msgId, uid, status))
        if (members.isEmpty()) return status
        val got = readableDatabase.rawQuery("SELECT uid, status FROM rcpt WHERE msg_id=?", arrayOf(msgId)).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) }
        }
        return members.minOf { got[it] ?: 1 }
    }
}
