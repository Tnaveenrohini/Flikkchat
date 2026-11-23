package com.flikk.chat.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Message status: 0 = pending (clock), 1 = sent ✓, 2 = delivered ✓✓, 3 = read (blue ✓✓) */
data class Msg(
    val id: String,
    val peer: String,
    val fromMe: Boolean,
    val text: String,
    val ts: Long,
    val status: Int,
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

/**
 * Chats annee ikkade — user phone lo (SQLite). Server lo emi save avvadu.
 */
class LocalDb(ctx: Context) : SQLiteOpenHelper(ctx, "flikkchat.db", null, 1) {

    private val _changes = MutableStateFlow(0L)
    /** Prathi change ki number perugutundi — UI dinni chusi refresh avutundi. */
    val changes: StateFlow<Long> = _changes

    private fun bump() = _changes.update { it + 1 }

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** true = kotha message (insert ayyindi), false = already undi */
    fun insertMsg(m: Msg, readByMe: Boolean = m.fromMe): Boolean {
        val cv = ContentValues().apply {
            put("id", m.id); put("peer", m.peer); put("from_me", if (m.fromMe) 1 else 0)
            put("text", m.text); put("ts", m.ts); put("status", m.status)
            put("read_by_me", if (readByMe) 1 else 0)
        }
        val row = writableDatabase.insertWithOnConflict("messages", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        if (row != -1L) bump()
        return row != -1L
    }

    /** Status mundu ki matrame vellali (read → delivered ki venakki raadu). */
    fun updateStatus(id: String, status: Int) {
        val cv = ContentValues().apply { put("status", status) }
        val n = writableDatabase.update("messages", cv, "id=? AND status<?", arrayOf(id, status.toString()))
        if (n > 0) bump()
    }

    fun messages(peer: String): List<Msg> = readableDatabase.rawQuery(
        "SELECT id, peer, from_me, text, ts, status FROM messages WHERE peer=? ORDER BY ts ASC",
        arrayOf(peer)
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(Msg(c.getString(0), c.getString(1), c.getInt(2) == 1, c.getString(3), c.getLong(4), c.getInt(5)))
        }
    }

    fun pending(): List<Msg> = readableDatabase.rawQuery(
        "SELECT id, peer, from_me, text, ts, status FROM messages WHERE from_me=1 AND status=0 ORDER BY ts ASC",
        null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(Msg(c.getString(0), c.getString(1), true, c.getString(3), c.getLong(4), 0))
        }
    }

    /** Chat open chesinappudu: chadavani messages ids ichi, vaatini read ga mark chestundi. */
    fun takeUnread(peer: String): List<String> {
        val ids = readableDatabase.rawQuery(
            "SELECT id FROM messages WHERE peer=? AND from_me=0 AND read_by_me=0", arrayOf(peer)
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        if (ids.isNotEmpty()) {
            val cv = ContentValues().apply { put("read_by_me", 1) }
            writableDatabase.update("messages", cv, "peer=? AND from_me=0 AND read_by_me=0", arrayOf(peer))
            bump()
        }
        return ids
    }

    fun chats(): List<ChatRow> = readableDatabase.rawQuery(
        """SELECT p.uid, p.name, p.phone, m.text, m.ts, m.from_me, m.status,
              (SELECT COUNT(*) FROM messages u WHERE u.peer=p.uid AND u.from_me=0 AND u.read_by_me=0)
           FROM peers p
           JOIN messages m ON m.id = (SELECT id FROM messages WHERE peer=p.uid ORDER BY ts DESC LIMIT 1)
           ORDER BY m.ts DESC""",
        null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                ChatRow(
                    c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getLong(4), c.getInt(5) == 1, c.getInt(6), c.getInt(7)
                )
            )
        }
    }

    fun peer(uid: String): Peer? = readableDatabase.rawQuery(
        "SELECT uid, name, phone FROM peers WHERE uid=?", arrayOf(uid)
    ).use { c -> if (c.moveToFirst()) Peer(c.getString(0), c.getString(1), c.getString(2)) else null }

    /** overwriteName=true: contacts lo unna peru tho update chestundi. */
    fun upsertPeer(uid: String, name: String, phone: String, overwriteName: Boolean) {
        val existing = peer(uid)
        val cv = ContentValues().apply {
            put("uid", uid)
            put("name", if (existing != null && !overwriteName) existing.name else name)
            put("phone", phone)
        }
        writableDatabase.insertWithOnConflict("peers", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        bump()
    }
}
