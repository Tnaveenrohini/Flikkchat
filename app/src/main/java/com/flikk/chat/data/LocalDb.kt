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
    val expires: Long = 0,
    val reactions: String = "",
    val votes: String = "",
    val viewOnce: Boolean = false,
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

data class StatusItem(
    val id: String, val owner: String, val type: String, val text: String,
    val color: Long, val media: String, val ts: Long, val seen: Boolean,
)

data class CallLog(
    val id: String, val peer: String, val outgoing: Boolean, val video: Boolean,
    val ts: Long, val dur: Long, val missed: Boolean,
)

const val STATUS_TTL = 24 * 3_600_000L

data class Group(
    val gid: String, val name: String, val members: List<String>, val admins: List<String>,
    /** "s" = admins matrame messages pampagalaru, "e" = admins matrame info edit, "a" = admins matrame members add */
    val flags: String = "",
) {
    val adminsOnlySend get() = 's' in flags
    val adminsOnlyEdit get() = 'e' in flags
    val adminsOnlyAdd get() = 'a' in flags
}

fun isGroup(peer: String) = peer.startsWith("g:")
fun gidOf(peer: String) = peer.removePrefix("g:")

/** Chat list lo last message preview */
/** Poll: text lo JSON {"q": question, "o": [options], "m": multi} */
data class Poll(val q: String, val options: List<String>, val multi: Boolean)

fun pollOf(m: Msg): Poll? = if (m.type != "poll") null else try {
    val j = org.json.JSONObject(m.text)
    val arr = j.getJSONArray("o")
    Poll(j.optString("q"), List(arr.length()) { arr.getString(it) }, j.optBoolean("m"))
} catch (_: Exception) { null }

/** votes / reactions: JSON {uid: value} */
fun jsonMap(s: String): Map<String, String> = if (s.isBlank()) emptyMap() else try {
    val j = org.json.JSONObject(s)
    j.keys().asSequence().associateWith { j.getString(it) }
} catch (_: Exception) { emptyMap() }

fun preview(m: Msg): String = when {
    m.deleted -> "🚫 This message was deleted"
    m.type == "poll" -> "📊 " + (pollOf(m)?.q ?: "Poll")
    m.viewOnce && m.type == "image" -> "📷 View once photo"
    m.viewOnce && m.type == "video" -> "🎥 View once video"
    m.type == "image" -> "📷 Photo" + if (m.text.isNotBlank()) " · ${m.text}" else ""
    m.type == "video" -> "🎥 Video" + if (m.text.isNotBlank()) " · ${m.text}" else ""
    m.type == "audio" -> "🎤 Voice message"
    else -> m.text
}

/**
 * Chats annee ikkade — user phone lo (SQLite). Server lo emi save avvadu.
 */
class LocalDb(ctx: Context) : SQLiteOpenHelper(ctx, "flikkchat.db", null, 5) {

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
        onUpgrade(db, 1, 5)
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
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE messages ADD COLUMN expires INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 4) {
            db.execSQL("CREATE TABLE IF NOT EXISTS statuses(id TEXT PRIMARY KEY, owner TEXT NOT NULL, type TEXT NOT NULL, text TEXT NOT NULL, color INTEGER NOT NULL, media TEXT NOT NULL, ts INTEGER NOT NULL, seen INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE IF NOT EXISTS calllog(id TEXT PRIMARY KEY, peer TEXT NOT NULL, outgoing INTEGER NOT NULL, video INTEGER NOT NULL, ts INTEGER NOT NULL, dur INTEGER NOT NULL, missed INTEGER NOT NULL)")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE messages ADD COLUMN reactions TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE messages ADD COLUMN votes TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE messages ADD COLUMN view_once INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE rcpt ADD COLUMN ts INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE groups ADD COLUMN flags TEXT NOT NULL DEFAULT ''")
        }
    }

    private val COLS = "id, peer, from_me, text, ts, status, sender, type, media, mime, dur, reply_id, reply_text, reply_name, fwd, deleted, starred, expires, reactions, votes, view_once"

    private fun Cursor.toMsg() = Msg(
        id = getString(0), peer = getString(1), fromMe = getInt(2) == 1, text = getString(3),
        ts = getLong(4), status = getInt(5), sender = getString(6), type = getString(7),
        media = getString(8), mime = getString(9), dur = getLong(10), replyId = getString(11),
        replyText = getString(12), replyName = getString(13), fwd = getInt(14) == 1,
        deleted = getInt(15) == 1, starred = getInt(16) == 1, expires = getLong(17),
        reactions = getString(18), votes = getString(19), viewOnce = getInt(20) == 1,
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
            put("expires", m.expires)
            put("reactions", m.reactions); put("votes", m.votes); put("view_once", if (m.viewOnce) 1 else 0)
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

    fun setField(id: String, col: String, value: String) {
        val cv = ContentValues().apply { put(col, value) }
        if (writableDatabase.update("messages", cv, "id=?", arrayOf(id)) > 0) bump()
    }

    /** Message info: (uid, status, time) */
    fun receipts(msgId: String): List<Triple<String, Int, Long>> = readableDatabase.rawQuery(
        "SELECT uid, status, ts FROM rcpt WHERE msg_id=? AND status<4 ORDER BY ts", arrayOf(msgId)
    ).use { c -> buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getInt(1), c.getLong(2))) } }

    fun media(peer: String): List<Msg> =
        query("SELECT $COLS FROM messages WHERE peer=? AND media!='' AND deleted=0 AND view_once=0 AND (type='image' OR type='video') ORDER BY ts ASC", arrayOf(peer))

    fun withLinks(peer: String): List<Msg> =
        query("SELECT $COLS FROM messages WHERE peer=? AND deleted=0 AND (text LIKE '%http://%' OR text LIKE '%https://%') ORDER BY ts DESC", arrayOf(peer))

    fun searchType(type: String): List<Msg> =
        query("SELECT $COLS FROM messages WHERE deleted=0 AND type=? AND view_once=0 ORDER BY ts DESC LIMIT 200", arrayOf(type))

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

    /** Time aipoyina (disappearing) messages delete — vaati media paths return */
    fun purgeExpired(now: Long): List<String> {
        val paths = readableDatabase.rawQuery(
            "SELECT media FROM messages WHERE expires>0 AND expires<? AND media!=''", arrayOf(now.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        val n = writableDatabase.delete("messages", "expires>0 AND expires<?", arrayOf(now.toString()))
        if (n > 0) bump()
        return paths
    }

    /** Chat clear — media paths return */
    fun clearChat(peer: String): List<String> {
        val paths = readableDatabase.rawQuery(
            "SELECT media FROM messages WHERE peer=? AND media!=''", arrayOf(peer)
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        writableDatabase.delete("messages", "peer=? AND type!='sys'", arrayOf(peer))
        bump()
        return paths
    }

    fun mediaByPeer(): Map<String, List<String>> = readableDatabase.rawQuery(
        "SELECT peer, media FROM messages WHERE media!=''", null
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) } }
        .groupBy({ it.first }, { it.second })

    /** Chat lo photos/videos/voice delete — paths return */
    fun clearMedia(peer: String): List<String> {
        val paths = readableDatabase.rawQuery("SELECT media FROM messages WHERE peer=? AND media!=''", arrayOf(peer))
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        val cv = ContentValues().apply { put("media", "") }
        writableDatabase.update("messages", cv, "peer=? AND media!=''", arrayOf(peer))
        bump()
        return paths
    }

    fun checkpoint() {
        try { readableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() } } catch (_: Exception) {}
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

    /**
     * 🔥 Streaks: iddaru (nenu + vaallu) message pampina varusa rojulu.
     * Ivala inka pampakapoyina, ninnati varaku unna streak chupistundi. 2+ rojulu matrame.
     */
    fun streaks(): Map<String, Int> {
        val now = System.currentTimeMillis()
        val off = java.util.TimeZone.getDefault().getOffset(now).toLong()
        val day = 86_400_000L
        val today = (now + off) / day
        val since = now - 400 * day
        val both = HashMap<String, HashSet<Long>>()
        readableDatabase.rawQuery(
            "SELECT peer, (ts + $off) / $day AS d, MAX(from_me), MIN(from_me) FROM messages " +
                "WHERE peer NOT LIKE 'g:%' AND type != 'sys' AND ts > $since GROUP BY peer, d", null
        ).use { c ->
            while (c.moveToNext()) {
                if (c.getInt(2) == 1 && c.getInt(3) == 0) both.getOrPut(c.getString(0)) { HashSet() }.add(c.getLong(1))
            }
        }
        val out = HashMap<String, Int>()
        for ((peer, days) in both) {
            var d = if (today in days) today else today - 1
            var n = 0
            while (d in days) { n++; d-- }
            if (n >= 2) out[peer] = n
        }
        return out
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
        "SELECT gid, name, members, admins, flags FROM groups WHERE gid=?", arrayOf(gid)
    ).use { c ->
        if (c.moveToFirst()) Group(c.getString(0), c.getString(1), c.getString(2).split(",").filter { it.isNotEmpty() },
            c.getString(3).split(",").filter { it.isNotEmpty() }, c.getString(4)) else null
    }

    fun upsertGroup(g: Group) {
        val cv = ContentValues().apply {
            put("gid", g.gid); put("name", g.name)
            put("members", g.members.joinToString(",")); put("admins", g.admins.joinToString(","))
            put("flags", g.flags)
        }
        writableDatabase.insertWithOnConflict("groups", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        upsertPeer("g:${g.gid}", g.name, "", overwriteName = true)
    }

    // ---------- Group receipts ----------

    /** Group lo member status save chesi, andari kanna takkuva status return chestundi. */
    fun groupReceipt(msgId: String, uid: String, status: Int, members: List<String>): Int {
        val now = System.currentTimeMillis()
        writableDatabase.execSQL("INSERT OR IGNORE INTO rcpt(msg_id, uid, status, ts) VALUES(?,?,?,?)", arrayOf<Any>(msgId, uid, status, now))
        writableDatabase.execSQL("UPDATE rcpt SET status=?, ts=? WHERE msg_id=? AND uid=? AND status<?", arrayOf<Any>(status, now, msgId, uid, status))
        if (members.isEmpty()) return status
        val got = readableDatabase.rawQuery("SELECT uid, status FROM rcpt WHERE msg_id=?", arrayOf(msgId)).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) }
        }
        return members.minOf { got[it] ?: 1 }
    }

    // ---------- Status ----------

    fun insertStatus(st: StatusItem): Boolean {
        val cv = ContentValues().apply {
            put("id", st.id); put("owner", st.owner); put("type", st.type); put("text", st.text)
            put("color", st.color); put("media", st.media); put("ts", st.ts); put("seen", if (st.seen) 1 else 0)
        }
        val r = writableDatabase.insertWithOnConflict("statuses", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        if (r != -1L) bump()
        return r != -1L
    }

    fun status(id: String): StatusItem? = statusQuery("WHERE id=?", arrayOf(id)).firstOrNull()

    /** 24 hours lopu statuses (paatavi mundu) */
    fun statuses(): List<StatusItem> =
        statusQuery("WHERE ts>? ORDER BY ts ASC", arrayOf((System.currentTimeMillis() - STATUS_TTL).toString()))

    private fun statusQuery(where: String, args: Array<String>): List<StatusItem> = readableDatabase.rawQuery(
        "SELECT id, owner, type, text, color, media, ts, seen FROM statuses $where", args
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                StatusItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4),
                    c.getString(5), c.getLong(6), c.getInt(7) == 1)
            )
        }
    }

    fun markStatusSeen(id: String) {
        val cv = ContentValues().apply { put("seen", 1) }
        if (writableDatabase.update("statuses", cv, "id=? AND seen=0", arrayOf(id)) > 0) bump()
    }

    fun deleteStatus(id: String) { writableDatabase.delete("statuses", "id=?", arrayOf(id)); bump() }

    fun purgeStatuses(): List<String> {
        val cut = (System.currentTimeMillis() - STATUS_TTL).toString()
        val paths = readableDatabase.rawQuery("SELECT media FROM statuses WHERE ts<? AND media!=''", arrayOf(cut))
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        if (writableDatabase.delete("statuses", "ts<?", arrayOf(cut)) > 0) bump()
        return paths
    }

    /** Naa status chusina vaallu */
    fun addView(statusId: String, uid: String) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO rcpt(msg_id, uid, status, ts) VALUES(?,?,4,?)", arrayOf<Any>(statusId, uid, System.currentTimeMillis()))
        bump()
    }

    /** (uid, chusina time) — kotha vaallu mundu */
    fun views(statusId: String): List<Pair<String, Long>> = readableDatabase.rawQuery(
        "SELECT uid, ts FROM rcpt WHERE msg_id=? AND status=4 ORDER BY ts DESC", arrayOf(statusId)
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getLong(1)) } }

    // ---------- Call log ----------

    fun logCall(c: CallLog) {
        val cv = ContentValues().apply {
            put("id", c.id); put("peer", c.peer); put("outgoing", if (c.outgoing) 1 else 0)
            put("video", if (c.video) 1 else 0); put("ts", c.ts); put("dur", c.dur); put("missed", if (c.missed) 1 else 0)
        }
        writableDatabase.insertWithOnConflict("calllog", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        bump()
    }

    fun calls(): List<CallLog> = readableDatabase.rawQuery(
        "SELECT id, peer, outgoing, video, ts, dur, missed FROM calllog ORDER BY ts DESC LIMIT 200", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                CallLog(c.getString(0), c.getString(1), c.getInt(2) == 1, c.getInt(3) == 1, c.getLong(4), c.getLong(5), c.getInt(6) == 1)
            )
        }
    }
}
