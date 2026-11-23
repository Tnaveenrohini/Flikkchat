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
import android.os.Build
import android.provider.ContactsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.flikk.chat.MainActivity
import com.flikk.chat.R
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

data class PeerState(
    val name: String,
    val lastActive: Long,
    val typingTo: String?,
    val typingAt: Long,
)

data class Contact(val uid: String, val name: String, val phone: String)

/**
 * Flikkchat engine.
 *
 * Model (WhatsApp laaga):
 *  - Chats annee phone lo SQLite lo (LocalDb).
 *  - Firestore kevalam "postman": inbox/{receiver}/msgs lo message pedtam,
 *    receiver teesukoni phone lo save chesi, server nunchi DELETE chestadu.
 *  - Ticks kosam receipts: inbox/{sender}/receipts/{msgId} {status: 2/3}.
 */
object Chat {
    const val ONLINE_WINDOW_MS = 70_000L
    const val TYPING_WINDOW_MS = 5_000L
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
                    val from = d.getString("from") ?: continue
                    val text = d.getString("text") ?: ""
                    val ts = d.getLong("ts") ?: System.currentTimeMillis()
                    val id = d.id
                    scope.launch { receive(uid, id, from, text, ts) }
                }
            }

        // 2) Ticks (delivered / read)
        regs += fs.collection("inbox").document(uid).collection("receipts")
            .addSnapshotListener { snap, _ ->
                snap ?: return@addSnapshotListener
                for (ch in snap.documentChanges) {
                    if (ch.type == DocumentChange.Type.REMOVED) continue
                    val d = ch.document
                    val status = (d.getLong("status") ?: continue).toInt()
                    db.updateStatus(d.id, status)
                    d.reference.delete()
                }
            }

        // 3) Pampakunda unna messages malli pampu
        scope.launch { for (m in db.pending()) push(m) }

        // 4) Online heartbeat
        heartbeat = scope.launch {
            while (isActive) {
                if (foreground) touchPresence()
                delay(30_000)
            }
        }
    }

    private suspend fun receive(myUid: String, id: String, from: String, text: String, ts: Long) {
        if (db.peer(from) == null) {
            try {
                val u = fs.collection("users").document(from).get().await()
                val phone = u.getString("phone") ?: ""
                db.upsertPeer(from, u.getString("name") ?: phone, phone, overwriteName = false)
            } catch (_: Exception) {
                db.upsertPeer(from, "Unknown", "", overwriteName = false)
            }
        }
        val isOpen = foreground && openChat == from
        val fresh = db.insertMsg(Msg(id, from, false, text, ts, 0), readByMe = isOpen)
        try {
            // Delivered (or read, chat open lo unte)
            sendReceipt(from, id, if (isOpen) 3 else 2, myUid)
            // Server nunchi teeseyi — data phone lo matrame
            fs.collection("inbox").document(myUid).collection("msgs").document(id).delete().await()
        } catch (_: Exception) {}
        if (fresh && !isOpen) notifyMessage(from, db.peer(from)?.name ?: "Flikkchat", text)
    }

    private suspend fun sendReceipt(to: String, msgId: String, status: Int, myUid: String) {
        fs.collection("inbox").document(to).collection("receipts").document(msgId)
            .set(mapOf("status" to status, "by" to myUid)).await()
    }

    fun send(peer: String, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val m = Msg(UUID.randomUUID().toString(), peer, true, t, System.currentTimeMillis(), 0)
        db.insertMsg(m)
        scope.launch { push(m) }
    }

    private suspend fun push(m: Msg) {
        val uid = me ?: return
        try {
            fs.collection("inbox").document(m.peer).collection("msgs").document(m.id)
                .set(mapOf("from" to uid, "text" to m.text, "ts" to m.ts)).await()
            db.updateStatus(m.id, 1)
        } catch (_: Exception) {
            // Internet vachaka start() lo malli try chestundi
        }
    }

    /** Chat open chesinappudu — blue ticks pampu. */
    fun markRead(peer: String) {
        val ids = db.takeUnread(peer)
        val uid = me ?: return
        if (ids.isEmpty()) return
        scope.launch {
            try {
                val batch = fs.batch()
                for (id in ids) {
                    batch.set(
                        fs.collection("inbox").document(peer).collection("receipts").document(id),
                        mapOf("status" to 3, "by" to uid)
                    )
                }
                batch.commit().await()
            } catch (_: Exception) {}
        }
        NotificationManagerCompat.from(app).cancel(peer.hashCode())
    }

    // ---------- Presence & typing ----------

    fun setForeground(fg: Boolean) {
        foreground = fg
        if (fg && loggedIn) {
            scope.launch {
                touchPresence()
                // App open chesinappudu pending messages malli try
                for (m in db.pending()) push(m)
            }
        } else if (!fg && loggedIn) {
            scope.launch {
                try {
                    fs.collection("users").document(me ?: return@launch)
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
            return@withContext emptyList()
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
    private fun notifyMessage(peer: String, name: String, text: String) {
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
            .setContentTitle(name)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(app).notify(peer.hashCode(), n)
    }
}
