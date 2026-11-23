package com.flikk.chat.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.flikk.chat.MainActivity
import com.flikk.chat.R
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

data class PeerState(
    val name: String,
    val about: String,
    val lastActive: Long,
    val typingTo: String?,
    val typingAt: Long,
)

data class Contact(val uid: String, val name: String, val phone: String)

/**
 * Flikkchat engine.
 *
 * Model (WhatsApp laaga):
 *  - Chats, photos, videos, voice annee phone lo (SQLite + app files).
 *  - Firestore kevalam "postman": inbox/{receiver}/msgs lo message pedtam,
 *    receiver teesukoni phone lo save chesi, server nunchi DELETE chestadu.
 *  - Media: chinna chunks ga inbox/{receiver}/chunks lo (free plan lo Storage ledu kabatti).
 *  - Ticks: inbox/{sender}/receipts/{msgId_uid} {msg, status: 2/3, by}.
 *  - Groups: groups/{gid} {name, members, admins}. Sender prathi member inbox ki pampistadu.
 */
object Chat {
    const val ONLINE_WINDOW_MS = 70_000L
    const val TYPING_WINDOW_MS = 5_000L
    private const val CHUNK = 700_000
    private const val CHANNEL = "messages"

    private lateinit var app: Application
    lateinit var db: LocalDb
        private set
    private lateinit var prefs: SharedPreferences

    /** google-services.json tho build ayite true. */
    var ready = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fs: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val pushLock = Mutex()
    private val inFlight = mutableSetOf<String>()

    @Volatile var openChat: String? = null
    @Volatile private var foreground = false

    private var started = false
    private val regs = mutableListOf<ListenerRegistration>()
    private var heartbeat: Job? = null
    private var lastTypingSent = 0L

    val me: String? get() = if (ready) auth.currentUser?.uid else null
    val loggedIn: Boolean get() = me != null && prefs.getBoolean("registered", false)
    val myName: String get() = prefs.getString("name", "") ?: ""
    val myPhone: String get() = prefs.getString("phone", "") ?: ""
    val myAbout: String get() = prefs.getString("about", "") ?: ""

    fun init(application: Application) {
        app = application
        db = LocalDb(application)
        prefs = application.getSharedPreferences("flikkchat", Context.MODE_PRIVATE)
        ready = FirebaseApp.getApps(application).isNotEmpty()
        createChannel()
        if (loggedIn) start()
    }

    fun nameOf(uid: String): String = when (uid) {
        me -> "You"
        else -> db.peer(uid)?.name?.takeIf { it.isNotBlank() } ?: "Unknown"
    }

    // ---------- Phone number ----------

    /** "98765 43210" / "098765..." / "+91 98765..." → "+919876543210" */
    fun normalize(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            raw.trim().startsWith("+") && digits.length in 8..15 -> "+$digits"
            raw.trim().startsWith("00") && digits.length in 10..17 -> "+" + digits.drop(2)
            digits.length == 10 -> "+91$digits"
            digits.length == 11 && digits.startsWith("0") -> "+91" + digits.drop(1)
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            else -> null
        }
    }

    // ---------- Login ----------

    /** null = success, lekapothe error message */
    suspend fun register(name: String, rawPhone: String): String? = withContext(Dispatchers.IO) {
        val phone = normalize(rawPhone) ?: return@withContext "Phone number correct ga ivvandi"
        if (name.isBlank()) return@withContext "Mee peru ivvandi"
        try {
            val user = auth.currentUser ?: try {
                withTimeout(20_000) { auth.signInAnonymously().await().user }
            } catch (e: TimeoutCancellationException) {
                return@withContext "Internet slow ga undi, malli try cheyyandi"
            } catch (e: Exception) {
                val m = e.message ?: ""
                return@withContext if (m.contains("ADMIN_ONLY", true) || m.contains("disabled", true) || m.contains("CONFIGURATION_NOT_FOUND", true))
                    "Firebase lo Authentication → Anonymous enable cheyyandi"
                else "Login error: $m"
            }
            val uid = user?.uid ?: return@withContext "Login fail ayyindi, malli try cheyyandi"
            // Firestore database lekapothe write eppatiki complete avvadu — timeout pettam
            val ok = withTimeoutOrNull(20_000) {
                fs.collection("users").document(uid).set(
                    mapOf(
                        "name" to name.trim(),
                        "phone" to phone,
                        "about" to "Hey! I'm on Flikkchat",
                        "lastActive" to System.currentTimeMillis(),
                    ),
                    SetOptions.merge()
                ).await()
                fs.collection("phones").document(phone).set(mapOf("uid" to uid)).await()
                true
            }
            if (ok == null) return@withContext "Server reach avvaledu. Firebase lo Firestore Database create chesara? (Internet kuda check cheyyandi)"
            prefs.edit()
                .putString("name", name.trim())
                .putString("phone", phone)
                .putString("about", "Hey! I'm on Flikkchat")
                .putBoolean("registered", true)
                .apply()
            start()
            null
        } catch (e: Exception) {
            val m = e.message ?: "network problem"
            if (m.contains("PERMISSION_DENIED", true)) "Firestore Rules publish cheyyandi (firestore.rules)" else "Error: $m"
        }
    }

    suspend fun updateProfile(name: String, about: String): String? = withContext(Dispatchers.IO) {
        val uid = me ?: return@withContext "Login avvaledu"
        if (name.isBlank()) return@withContext "Peru empty undakudadu"
        try {
            fs.collection("users").document(uid)
                .set(mapOf("name" to name.trim(), "about" to about.trim()), SetOptions.merge()).await()
            prefs.edit().putString("name", name.trim()).putString("about", about.trim()).apply()
            null
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    // ---------- Sync engine ----------

    @Synchronized
    fun start() {
        if (started || !ready) return
        val uid = me ?: return
        started = true

        // 1) Kotha messages
        regs += fs.collection("inbox").document(uid).collection("msgs")
            .addSnapshotListener { snap, _ ->
                snap ?: return@addSnapshotListener
                for (ch in snap.documentChanges) {
                    if (ch.type != DocumentChange.Type.ADDED) continue
                    val d = ch.document
                    scope.launch { receive(uid, d) }
                }
            }

        // 2) Ticks (delivered / read)
        regs += fs.collection("inbox").document(uid).collection("receipts")
            .addSnapshotListener { snap, _ ->
                snap ?: return@addSnapshotListener
                for (ch in snap.documentChanges) {
                    if (ch.type == DocumentChange.Type.REMOVED) continue
                    val d = ch.document
                    scope.launch { onReceipt(d) }
                }
            }

        // 3) Pampakunda unna messages malli pampu
        scope.launch { retryPending() }

        // 4) Online heartbeat
        heartbeat = scope.launch {
            while (isActive) {
                if (foreground) touchPresence()
                delay(30_000)
            }
        }
    }

    private suspend fun retryPending() { for (m in db.pending()) push(m) }

    private suspend fun ensurePeer(uid: String) {
        if (uid.isEmpty() || uid == me || db.peer(uid) != null) return
        try {
            val u = fs.collection("users").document(uid).get().await()
            val phone = u.getString("phone") ?: ""
            db.upsertPeer(uid, u.getString("name") ?: phone, phone, overwriteName = false)
        } catch (_: Exception) {
            db.upsertPeer(uid, "Unknown", "", overwriteName = false)
        }
    }

    private suspend fun receive(myUid: String, d: DocumentSnapshot) {
        val id = d.id
        val from = d.getString("from") ?: return
        val type = d.getString("type") ?: "text"
        val group = d.getString("group")
        val ref = d.reference

        // Delete for everyone
        if (type == "del") {
            val target = d.getString("target")
            val local = target?.let { db.msg(it) }
            if (local != null && !local.fromMe && (local.sender == from || local.peer == from)) {
                if (local.media.isNotEmpty()) File(local.media).delete()
                db.markDeleted(local.id)
            }
            try { ref.delete().await() } catch (_: Exception) {}
            return
        }

        if (db.msg(id) != null) { // already save ayyindi
            try { ref.delete().await() } catch (_: Exception) {}
            return
        }

        ensurePeer(from)
        val peerKey = if (group != null) "g:$group" else from
        if (group != null && (type == "sys" || db.group(group) == null)) refreshGroup(group)

        // Media chunks download
        var mediaPath = ""
        val n = (d.getLong("n") ?: 0L).toInt()
        val mime = d.getString("mime") ?: ""
        if (n > 0) {
            val buf = ByteArrayOutputStream()
            val chunkRefs = (0 until n).map {
                fs.collection("inbox").document(myUid).collection("chunks").document("${id}_$it")
            }
            try {
                for (c in chunkRefs) {
                    val cd = c.get().await()
                    val b = cd.getBlob("b") ?: return // inka upload avutundi — taruvatha malli
                    buf.write(b.toBytes())
                }
            } catch (_: Exception) {
                return
            }
            val f = File(Media.dir(app), "$id.${Media.ext(mime)}")
            f.writeBytes(buf.toByteArray())
            mediaPath = f.absolutePath
            for (c in chunkRefs) try { c.delete() } catch (_: Exception) {}
        }

        val isOpen = foreground && openChat == peerKey
        val m = Msg(
            id = id, peer = peerKey, fromMe = false,
            text = d.getString("text") ?: "",
            ts = d.getLong("ts") ?: System.currentTimeMillis(),
            status = 0, sender = from, type = type, media = mediaPath, mime = mime,
            dur = d.getLong("dur") ?: 0L,
            replyId = d.getString("rid") ?: "", replyText = d.getString("rtext") ?: "",
            replyName = d.getString("rname") ?: "", fwd = d.getBoolean("fwd") ?: false,
        )
        val fresh = db.insertMsg(m, readByMe = isOpen || type == "sys")
        try {
            if (type != "sys") sendReceipt(from, id, if (isOpen) 3 else 2, myUid)
            // Server nunchi teeseyi — data phone lo matrame
            ref.delete().await()
        } catch (_: Exception) {}

        if (fresh && !isOpen && type != "sys") {
            val title = db.peer(peerKey)?.name ?: "Flikkchat"
            val body = if (group != null) "${nameOf(from)}: ${preview(m)}" else preview(m)
            notifyMessage(peerKey, title, body)
        }
    }

    private suspend fun onReceipt(d: DocumentSnapshot) {
        val msgId = d.getString("msg") ?: d.id
        val status = (d.getLong("status") ?: return).toInt()
        val by = d.getString("by") ?: ""
        val local = db.msg(msgId)
        if (local != null) {
            if (isGroup(local.peer)) {
                val members = db.group(gidOf(local.peer))?.members?.filter { it != me } ?: emptyList()
                db.updateStatus(msgId, db.groupReceipt(msgId, by, status, members))
            } else {
                db.updateStatus(msgId, status)
            }
        }
        try { d.reference.delete().await() } catch (_: Exception) {}
    }

    private suspend fun sendReceipt(to: String, msgId: String, status: Int, myUid: String) {
        fs.collection("inbox").document(to).collection("receipts").document("${msgId}_$myUid")
            .set(mapOf("msg" to msgId, "status" to status, "by" to myUid)).await()
    }

    private fun recipients(peer: String): List<String> =
        if (isGroup(peer)) db.group(gidOf(peer))?.members?.filter { it != me } ?: emptyList()
        else listOf(peer)

    // ---------- Sending ----------

    fun send(
        peer: String, text: String, type: String = "text", media: String = "", mime: String = "",
        dur: Long = 0, reply: Msg? = null, fwd: Boolean = false,
    ) {
        val t = text.trim()
        if (type == "text" && t.isEmpty()) return
        val uid = me ?: return
        val m = Msg(
            id = UUID.randomUUID().toString(), peer = peer, fromMe = true, text = t,
            ts = System.currentTimeMillis(), status = 0, sender = uid, type = type,
            media = media, mime = mime, dur = dur,
            replyId = reply?.id ?: "", replyText = reply?.let { preview(it) } ?: "",
            replyName = reply?.let { if (it.fromMe) myName else nameOf(it.sender.ifEmpty { it.peer }) } ?: "",
            fwd = fwd,
        )
        db.insertMsg(m)
        scope.launch { push(m) }
    }

    /** Photo / video pampu. Error unte message return chestundi. */
    suspend fun sendMedia(peer: String, uri: Uri, isVideo: Boolean, caption: String, reply: Msg?): String? =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            try {
                if (isVideo) {
                    val (f, mime) = Media.prepareVideo(app, uri, id)
                        ?: return@withContext "Video 16 MB kanna chinnadi undali"
                    send(peer, caption, "video", f.absolutePath, mime, Media.durationMs(f.absolutePath), reply)
                } else {
                    val f = Media.prepareImage(app, uri, id)
                    send(peer, caption, "image", f.absolutePath, "image/jpeg", 0, reply)
                }
                null
            } catch (e: Exception) {
                "Pampalekapoyam: ${e.message}"
            }
        }

    fun sendVoice(peer: String, file: File, dur: Long, reply: Msg?) =
        send(peer, "", "audio", file.absolutePath, "audio/mp4", dur, reply)

    private suspend fun push(m: Msg) {
        val uid = me ?: return
        synchronized(inFlight) { if (!inFlight.add(m.id)) return }
        try {
            pushLock.withLock {
                val to = recipients(m.peer)
                val payload = mutableMapOf<String, Any>(
                    "from" to uid, "ts" to m.ts, "type" to m.type, "text" to m.text,
                )
                if (isGroup(m.peer)) payload["group"] = gidOf(m.peer)
                if (m.replyId.isNotEmpty()) {
                    payload["rid"] = m.replyId; payload["rtext"] = m.replyText; payload["rname"] = m.replyName
                }
                if (m.fwd) payload["fwd"] = true
                if (m.dur > 0) payload["dur"] = m.dur
                var chunks: List<ByteArray> = emptyList()
                if (m.media.isNotEmpty()) {
                    val f = File(m.media)
                    if (!f.exists()) return@withLock
                    val bytes = f.readBytes()
                    chunks = (bytes.indices step CHUNK).map { bytes.copyOfRange(it, minOf(it + CHUNK, bytes.size)) }
                    payload["n"] = chunks.size
                    payload["mime"] = m.mime
                }
                for (r in to) {
                    val inbox = fs.collection("inbox").document(r)
                    chunks.forEachIndexed { i, c ->
                        inbox.collection("chunks").document("${m.id}_$i")
                            .set(mapOf("from" to uid, "b" to Blob.fromBytes(c))).await()
                    }
                    inbox.collection("msgs").document(m.id).set(payload).await()
                }
                db.updateStatus(m.id, 1)
            }
        } catch (_: Exception) {
            // Internet vachaka malli try chestundi
        } finally {
            synchronized(inFlight) { inFlight.remove(m.id) }
        }
    }

    private fun sendControl(peer: String, data: Map<String, Any>) {
        val uid = me ?: return
        val to = recipients(peer)
        scope.launch {
            val payload = data.toMutableMap()
            payload["from"] = uid
            payload["ts"] = System.currentTimeMillis()
            if (isGroup(peer)) payload["group"] = gidOf(peer)
            for (r in to) {
                try {
                    fs.collection("inbox").document(r).collection("msgs")
                        .document(UUID.randomUUID().toString()).set(payload).await()
                } catch (_: Exception) {}
            }
        }
    }

    fun forward(m: Msg, peers: List<String>) {
        for (p in peers) {
            var media = ""
            if (m.media.isNotEmpty()) {
                val src = File(m.media)
                if (!src.exists()) continue
                val copy = File(Media.dir(app), UUID.randomUUID().toString() + "." + src.extension)
                src.copyTo(copy)
                media = copy.absolutePath
            }
            send(p, m.text, m.type, media, m.mime, m.dur, null, fwd = true)
        }
    }

    fun deleteForEveryone(m: Msg) {
        if (!m.fromMe) return
        if (m.media.isNotEmpty()) File(m.media).delete()
        db.markDeleted(m.id)
        sendControl(m.peer, mapOf("type" to "del", "target" to m.id))
    }

    fun deleteForMe(m: Msg) {
        if (m.media.isNotEmpty()) File(m.media).delete()
        db.deleteForMe(m.id)
    }

    fun star(m: Msg) = db.setStarred(m.id, !m.starred)

    /** Chat open chesinappudu — blue ticks pampu. */
    fun markRead(peer: String) {
        val unread = db.takeUnread(peer)
        val uid = me ?: return
        NotificationManagerCompat.from(app).cancel(peer.hashCode())
        if (unread.isEmpty()) return
        scope.launch {
            try {
                val batch = fs.batch()
                for ((id, sender) in unread) {
                    val to = sender.ifEmpty { peer }
                    if (isGroup(to)) continue
                    batch.set(
                        fs.collection("inbox").document(to).collection("receipts").document("${id}_$uid"),
                        mapOf("msg" to id, "status" to 3, "by" to uid)
                    )
                }
                batch.commit().await()
            } catch (_: Exception) {}
        }
    }

    // ---------- Groups ----------

    suspend fun refreshGroup(gid: String) {
        try {
            val g = fs.collection("groups").document(gid).get().await()
            if (!g.exists()) return
            @Suppress("UNCHECKED_CAST")
            val members = (g.get("members") as? List<String>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val admins = (g.get("admins") as? List<String>) ?: emptyList()
            db.upsertGroup(Group(gid, g.getString("name") ?: "Group", members, admins))
            for (u in members) ensurePeer(u)
        } catch (_: Exception) {
            // Permission ledu = group nunchi teesesaru
            val uid = me ?: return
            db.group(gid)?.let { db.upsertGroup(it.copy(members = it.members - uid)) }
        }
    }

    private fun sysMessage(gid: String, text: String) {
        val uid = me ?: return
        val peer = "g:$gid"
        val m = Msg(UUID.randomUUID().toString(), peer, true, text, System.currentTimeMillis(), 3, uid, "sys")
        db.insertMsg(m)
        sendControl(peer, mapOf("type" to "sys", "text" to text))
    }

    suspend fun createGroup(name: String, members: List<String>): String? = withContext(Dispatchers.IO) {
        val uid = me ?: return@withContext null
        if (name.isBlank() || members.isEmpty()) return@withContext null
        val gid = UUID.randomUUID().toString()
        val all = (members + uid).distinct()
        try {
            fs.collection("groups").document(gid).set(
                mapOf(
                    "name" to name.trim(), "members" to all, "admins" to listOf(uid),
                    "createdBy" to uid, "createdAt" to System.currentTimeMillis(),
                )
            ).await()
        } catch (_: Exception) {
            return@withContext null
        }
        db.upsertGroup(Group(gid, name.trim(), all, listOf(uid)))
        sysMessage(gid, "$myName created group \"${name.trim()}\"")
        "g:$gid"
    }

    suspend fun addMembers(gid: String, uids: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (uids.isEmpty()) return@withContext false
        try {
            fs.collection("groups").document(gid)
                .update("members", FieldValue.arrayUnion(*uids.toTypedArray())).await()
            refreshGroup(gid)
            sysMessage(gid, "$myName added " + uids.joinToString(", ") { nameOf(it) })
            true
        } catch (_: Exception) { false }
    }

    suspend fun removeMember(gid: String, uid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            sysMessage(gid, "$myName removed ${nameOf(uid)}")
            delay(1500) // removed member ki kuda message velladaniki
            fs.collection("groups").document(gid).update(
                mapOf("members" to FieldValue.arrayRemove(uid), "admins" to FieldValue.arrayRemove(uid))
            ).await()
            refreshGroup(gid)
            true
        } catch (_: Exception) { false }
    }

    suspend fun leaveGroup(gid: String): Boolean = withContext(Dispatchers.IO) {
        val uid = me ?: return@withContext false
        try {
            sysMessage(gid, "$myName left")
            delay(1500)
            fs.collection("groups").document(gid).update(
                mapOf("members" to FieldValue.arrayRemove(uid), "admins" to FieldValue.arrayRemove(uid))
            ).await()
            db.group(gid)?.let { db.upsertGroup(it.copy(members = it.members - uid, admins = it.admins - uid)) }
            true
        } catch (_: Exception) { false }
    }

    // ---------- Presence & typing ----------

    fun setForeground(fg: Boolean) {
        foreground = fg
        if (fg && loggedIn) {
            scope.launch {
                touchPresence()
                retryPending()
            }
        } else if (!fg && loggedIn) {
            scope.launch {
                val uid = me ?: return@launch
                try {
                    fs.collection("users").document(uid)
                        .set(mapOf("lastActive" to System.currentTimeMillis() - ONLINE_WINDOW_MS), SetOptions.merge())
                } catch (_: Exception) {}
            }
        }
    }

    private fun touchPresence() {
        val uid = me ?: return
        fs.collection("users").document(uid)
            .set(mapOf("lastActive" to System.currentTimeMillis()), SetOptions.merge())
    }

    fun typing(peer: String) {
        val uid = me ?: return
        if (isGroup(peer)) return
        val now = System.currentTimeMillis()
        if (now - lastTypingSent < 2_500) return
        lastTypingSent = now
        fs.collection("users").document(uid)
            .set(mapOf("typingTo" to peer, "typingAt" to now), SetOptions.merge())
    }

    fun peerState(uid: String): Flow<PeerState?> = callbackFlow {
        val reg = fs.collection("users").document(uid).addSnapshotListener { d, _ ->
            if (d == null || !d.exists()) {
                trySend(null)
            } else {
                trySend(
                    PeerState(
                        name = d.getString("name") ?: "",
                        about = d.getString("about") ?: "",
                        lastActive = d.getLong("lastActive") ?: 0L,
                        typingTo = d.getString("typingTo"),
                        typingAt = d.getLong("typingAt") ?: 0L,
                    )
                )
            }
        }
        awaitClose { reg.remove() }
    }

    // ---------- Contacts ----------

    /** Phone contacts lo Flikkchat vaadutunna vaallu. */
    suspend fun findContacts(): List<Contact> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext db.people().map { Contact(it.uid, it.name, it.phone) }
        }
        val book = LinkedHashMap<String, String>() // phone -> name
        app.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val p = normalize(c.getString(1) ?: continue) ?: continue
                if (p != myPhone) book.putIfAbsent(p, name)
            }
        }
        val uid = me ?: return@withContext emptyList()
        val found = mutableListOf<Contact>()
        for (chunk in book.keys.toList().chunked(30)) {
            try {
                val snap = fs.collection("phones").whereIn(FieldPath.documentId(), chunk).get().await()
                for (d in snap.documents) {
                    val u = d.getString("uid") ?: continue
                    if (u == uid) continue
                    val name = book[d.id] ?: d.id
                    found += Contact(u, name, d.id)
                    db.upsertPeer(u, name, d.id, overwriteName = true)
                }
            } catch (_: Exception) {}
        }
        found.sortedBy { it.name.lowercase() }
    }

    // ---------- Notifications ----------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = app.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyMessage(peer: String, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(app, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_PEER, peer)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            app, peer.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(app).notify(peer.hashCode(), n)
    }
}
