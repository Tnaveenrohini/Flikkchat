package com.flikk.chat.data

import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.EmailAuthProvider
import java.util.concurrent.TimeUnit
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseException
import android.app.Activity
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
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

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
    const val PIN_REQUIRED = "PIN_REQUIRED"
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
    /** Firebase lo phone number OTP tho verify ayyinda? */
    val phoneVerified: Boolean get() = !auth.currentUser?.phoneNumber.isNullOrEmpty()

    /**
     * Free mode: Firebase SMS (Blaze) badulu self-SMS verification. Login gate ledu.
     * Firebase Phone Auth ki maaralante ee flag true cheyyandi.
     */
    const val FIREBASE_OTP = false

    // ---------- ✉️ Email + Password login (Firebase free plan lo pani chestundi) ----------

    val myEmail: String get() = auth.currentUser?.email.orEmpty()

    private fun emailError(e: Exception): String {
        val m = (e.message ?: "") + " " + e.javaClass.simpleName
        return when {
            e is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters"
            e is FirebaseAuthUserCollisionException -> "An account with this email already exists. Log in instead."
            m.contains("OPERATION_NOT_ALLOWED", true) || m.contains("not enabled", true) || m.contains("disabled", true) ->
                "Enable Authentication → Email/Password in Firebase"
            e is FirebaseAuthInvalidUserException -> "No account with this email. Sign up first."
            e is FirebaseAuthInvalidCredentialsException && m.contains("email", true) && m.contains("badly", true) -> "Enter a valid email"
            e is FirebaseAuthInvalidCredentialsException -> "Wrong email or password"
            e is FirebaseTooManyRequestsException -> "Too many tries. Please try again later."
            e is FirebaseNetworkException -> "Network problem. Try again."
            else -> "Login error: " + (e.message ?: "unknown")
        }
    }

    /** Kotha account. Paata (anonymous) login unte daaniki email link — chats alage. null = success */
    suspend fun emailSignUp(email: String, password: String): String? = withContext(Dispatchers.IO) {
        val em = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(em).matches()) return@withContext "Enter a valid email"
        if (password.length < 6) return@withContext "Password must be at least 6 characters"
        try {
            withTimeout(25_000) {
                val cur = auth.currentUser
                if (cur != null && cur.isAnonymous) cur.linkWithCredential(EmailAuthProvider.getCredential(em, password)).await()
                else { if (cur != null) auth.signOut(); auth.createUserWithEmailAndPassword(em, password).await() }
            }
            null
        } catch (e: TimeoutCancellationException) { "Internet is slow, please try again" } catch (e: Exception) { emailError(e) }
    }

    /**
     * Login. Profile (name, phone) Firestore lo unte ventane ready — first = error, second = profile ready?
     */
    suspend fun emailLogin(email: String, password: String): Pair<String?, Boolean> = withContext(Dispatchers.IO) {
        val em = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(em).matches()) return@withContext "Enter a valid email" to false
        if (password.isEmpty()) return@withContext "Enter your password" to false
        try {
            val user = withTimeout(25_000) {
                if (auth.currentUser != null) auth.signOut()
                auth.signInWithEmailAndPassword(em, password).await().user
            } ?: return@withContext "Login failed, please try again" to false
            val doc = try { withTimeout(15_000) { fs.collection("users").document(user.uid).get().await() } } catch (_: Exception) { null }
            val name = doc?.getString("name").orEmpty()
            val phone = doc?.getString("phone").orEmpty()
            if (name.isNotBlank() && phone.isNotBlank()) {
                prefs.edit()
                    .putString("name", name).putString("phone", phone)
                    .putString("about", doc?.getString("about") ?: "")
                    .putBoolean("registered", true).apply()
                restart()
                null to true
            } else null to false
        } catch (e: TimeoutCancellationException) { "Internet is slow, please try again" to false } catch (e: Exception) { emailError(e) to false }
    }

    suspend fun resetPassword(email: String): String? = withContext(Dispatchers.IO) {
        val em = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(em).matches()) return@withContext "Enter a valid email"
        try { auth.sendPasswordResetEmail(em).await(); null } catch (e: Exception) { emailError(e) }
    }

    // ---------- 📱 Real OTP (Firebase Phone Auth) ----------

    interface OtpListener {
        fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken)
        fun onAutoVerified(cred: PhoneAuthCredential)
        fun onFailed(message: String)
    }

    /** Error ki mana message + Firebase original message (problem kanukkodaniki) */
    private fun otpError(e: Exception): String {
        val raw = e.message?.trim().orEmpty()
        val friendly = otpFriendly(e)
        return if (raw.isEmpty() || friendly.startsWith("OTP error")) friendly else "$friendly\n\n($raw)"
    }

    private fun otpFriendly(e: Exception): String {
        val m = (e.message ?: "") + " " + e.javaClass.simpleName
        return when {
            m.contains("BILLING_NOT_ENABLED", true) -> "SMS OTP needs the Firebase Blaze plan"
            m.contains("not authorized", true) || m.contains("app-not-authorized", true) || m.contains("Play Integrity", true) ->
                "Add the app's SHA-1 and SHA-256 in Firebase project settings"
            m.contains("region", true) -> "SMS to India is blocked. In Firebase: Authentication → Settings → SMS region policy → allow India"
            m.contains("OPERATION_NOT_ALLOWED", true) || m.contains("sign-in provider is disabled", true) -> "Enable Authentication → Phone in Firebase"
            e is FirebaseTooManyRequestsException || m.contains("quota", true) || m.contains("TOO_MANY", true) -> "Too many tries. Please try again later."
            e is FirebaseAuthInvalidCredentialsException && m.contains("phone", true) -> "Enter a valid phone number"
            e is FirebaseAuthInvalidCredentialsException -> "Wrong code. Check the SMS and try again."
            e is FirebaseNetworkException -> "Network problem. Try again."
            else -> "OTP error: " + (e.message ?: "unknown")
        }
    }

    fun sendOtp(activity: Activity, phone: String, resend: PhoneAuthProvider.ForceResendingToken?, l: OtpListener) {
        val b = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) = l.onAutoVerified(credential)
                override fun onVerificationFailed(e: FirebaseException) = l.onFailed(otpError(e))
                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) = l.onCodeSent(verificationId, token)
            })
        if (resend != null) b.setForceResendingToken(resend)
        try { PhoneAuthProvider.verifyPhoneNumber(b.build()) } catch (e: Exception) { l.onFailed(otpError(e)) }
    }

    fun otpCredential(verificationId: String, code: String): PhoneAuthCredential = PhoneAuthProvider.getCredential(verificationId, code)

    /**
     * OTP correct ayite login. Paata (anonymous) account unte daaniki phone link chestam — uid maaradu, chats alage untayi.
     * null = success.
     */
    suspend fun verifyOtp(cred: PhoneAuthCredential): String? = withContext(Dispatchers.IO) {
        try {
            withTimeout(30_000) {
                val cur = auth.currentUser
                when {
                    cur == null -> auth.signInWithCredential(cred).await()
                    cur.phoneNumber.isNullOrEmpty() -> try {
                        cur.linkWithCredential(cred).await()
                    } catch (e: FirebaseAuthUserCollisionException) {
                        val c2 = e.updatedCredential ?: throw e
                        auth.signInWithCredential(c2).await()
                    }
                    else -> auth.signInWithCredential(cred).await()
                }
                // Kotha token (phone_number tho) — Firestore rules ki kavali
                auth.currentUser?.getIdToken(true)?.await()
            }
            if (prefs.getBoolean("registered", false)) restart()
            null
        } catch (e: TimeoutCancellationException) {
            "Internet is slow, please try again"
        } catch (e: Exception) {
            otpError(e)
        }
    }
    val myAbout: String get() = prefs.getString("about", "") ?: ""

    fun init(application: Application) {
        app = application
        db = LocalDb(application)
        prefs = application.getSharedPreferences("flikkchat", Context.MODE_PRIVATE)
        ready = FirebaseApp.getApps(application).isNotEmpty()
        Crypto.init(prefs)
        Calls.init(application)
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
    suspend fun register(name: String, rawPhone: String, pin: String? = null): String? = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext "Enter your name"
        val phone = normalize(rawPhone) ?: return@withContext "Enter a valid phone number"
        try {
            val user = auth.currentUser ?: try {
                withTimeout(20_000) { auth.signInAnonymously().await().user }
            } catch (e: TimeoutCancellationException) {
                return@withContext "Internet is slow, please try again"
            } catch (e: Exception) {
                val m = e.message ?: ""
                return@withContext if (m.contains("ADMIN_ONLY", true) || m.contains("disabled", true) || m.contains("CONFIGURATION_NOT_FOUND", true))
                    "Enable Authentication → Anonymous in Firebase"
                else "Login error: $m"
            }
            val uid = user?.uid ?: return@withContext "Login failed, please try again"
            if (!pinOkFor(phone, uid, pin)) return@withContext if (pin == null) PIN_REQUIRED else "Wrong PIN"
            // Firestore database lekapothe write eppatiki complete avvadu — timeout pettam
            val ok = withTimeoutOrNull(20_000) {
                fs.collection("users").document(uid).set(
                    mapOf(
                        "name" to name.trim(),
                        "phone" to phone,
                        "about" to "Hey! I'm on Double Bubble",
                        "lastActive" to System.currentTimeMillis(),
                    ),
                    SetOptions.merge()
                ).await()
                fs.collection("phones").document(phone).set(mapOf("uid" to uid)).await()
                true
            }
            if (ok == null) return@withContext "Could not reach the server. Did you create the Firestore Database in Firebase? (Check your internet too)"
            prefs.edit()
                .putString("name", name.trim())
                .putString("phone", phone)
                .putString("about", "Hey! I'm on Double Bubble")
                .putBoolean("registered", true)
                .apply()
            start()
            null
        } catch (e: Exception) {
            val m = e.message ?: "network problem"
            if (m.contains("PERMISSION_DENIED", true)) "Publish the Firestore rules (firestore.rules)" else "Error: $m"
        }
    }

    suspend fun updateProfile(name: String, about: String): String? = withContext(Dispatchers.IO) {
        val uid = me ?: return@withContext "Not logged in"
        if (name.isBlank()) return@withContext "Name cannot be empty"
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

    /** Listeners malli start (OTP verify ayyaka kotha token tho) */
    @Synchronized
    fun restart() {
        regs.forEach { try { it.remove() } catch (_: Exception) {} }
        regs.clear()
        started = false
        start()
    }

    @Synchronized
    fun start() {
        if (started || !ready) return
        if (FIREBASE_OTP && !phoneVerified) return
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

        // 3) Naa public key publish (encryption kosam)
        fs.collection("users").document(uid).set(mapOf("pub" to Crypto.myPub), SetOptions.merge())

        // 4) Pampakunda unna messages malli pampu
        scope.launch { retryPending() }

        // 5) Online heartbeat + disappearing messages cleanup
        heartbeat = scope.launch {
            while (isActive) {
                if (foreground) touchPresence()
                purgeExpired()
                delay(30_000)
            }
        }
    }

    private suspend fun retryPending() { for (m in db.pending()) push(m) }

    fun purgeExpired() {
        for (p in db.purgeExpired(System.currentTimeMillis())) File(p).delete()
        for (p in db.purgeStatuses()) File(p).delete()
    }

    // ---------- Encryption keys ----------

    private val pubKeys = ConcurrentHashMap<String, String>()

    /** Peer public key (lekapothe null = old app, encryption ledu) */
    private suspend fun pubOf(uid: String, refresh: Boolean = false): String? {
        if (!refresh) pubKeys[uid]?.let { return it }
        return try {
            val p = fs.collection("users").document(uid).get().await().getString("pub")
            if (p != null) pubKeys[uid] = p
            p
        } catch (_: Exception) { pubKeys[uid] }
    }

    suspend fun safetyCode(uid: String): String? = pubOf(uid)?.let { Crypto.safetyCode(it) }

    // ---------- Settings (phone lo matrame) ----------

    fun flag(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)
    fun setFlag(key: String, v: Boolean) {
        prefs.edit().putBoolean(key, v).apply()
        db.bump()
        if (key == "hide_seen") scope.launch { touchPresence() }
    }
    val readReceipts get() = flag("read_receipts", true)
    val hideSeen get() = flag("hide_seen", false)

    /** 0 = phone setting, 1 = light, 2 = dark */
    /** 0 phone setting, 1 light, 2 dark. Default dark (Streaks look). */
    var themeMode: Int
        get() = prefs.getInt("theme", 2)
        set(v) { prefs.edit().putInt("theme", v).apply() }

    /** Theme colour (ARGB). Default Sunshine yellow. */
    var accentColor: Long
        get() = prefs.getLong("accent", 0xFFFFE14D)
        set(v) { prefs.edit().putLong("accent", v).apply() }

    @Volatile var backgroundAt = 0L
        private set

    private fun mutedSet(): Set<String> = prefs.getStringSet("muted", emptySet()) ?: emptySet()
    fun isMuted(peer: String) = peer in mutedSet()
    fun setMuted(peer: String, muted: Boolean) {
        val s = mutedSet().toMutableSet()
        if (muted) s += peer else s -= peer
        prefs.edit().putStringSet("muted", s).apply()
        db.bump()
    }

    fun blockedList(): List<String> = blockedSet().toList()

    /** Phone contacts lo ee number unda? */
    fun inContacts(phone: String): Boolean {
        if (phone.isEmpty()) return false
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return true
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
            app.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { it.count > 0 } ?: false
        } catch (_: Exception) { true }
    }

    // ---------- Share: location / contact / audio file ----------

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): android.location.Location? = withContext(Dispatchers.IO) {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val providers = lm.getProviders(true)
        var best: android.location.Location? = null
        for (p in providers) {
            val l = try { lm.getLastKnownLocation(p) } catch (_: Exception) { null } ?: continue
            if (best == null || l.time > best.time) best = l
        }
        if (best != null && System.currentTimeMillis() - best.time < 10 * 60_000L) return@withContext best
        if (Build.VERSION.SDK_INT >= 30 && providers.isNotEmpty()) {
            val p = if (android.location.LocationManager.GPS_PROVIDER in providers) android.location.LocationManager.GPS_PROVIDER else providers.first()
            val fresh = kotlinx.coroutines.withTimeoutOrNull(15_000) {
                kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
                    lm.getCurrentLocation(p, null, app.mainExecutor) { loc -> if (cont.isActive) cont.resumeWith(Result.success(loc)) }
                }
            }
            if (fresh != null) return@withContext fresh
        }
        best
    }

    suspend fun shareLocation(peer: String, reply: Msg?): String? {
        val l = currentLocation() ?: return "Location not found. Turn on GPS and try again"
        val lat = "%.6f".format(java.util.Locale.US, l.latitude)
        val lng = "%.6f".format(java.util.Locale.US, l.longitude)
        send(peer, "📍 My location\nhttps://maps.google.com/?q=$lat,$lng", reply = reply)
        return null
    }

    suspend fun shareContact(peer: String, contactUri: Uri, reply: Msg?): String? = withContext(Dispatchers.IO) {
        try {
            var name = ""
            var id = ""
            app.contentResolver.query(contactUri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) { id = c.getString(0); name = c.getString(1) ?: "" }
            }
            if (id.isEmpty()) return@withContext "Could not open contact"
            val nums = mutableListOf<String>()
            app.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?", arrayOf(id), null
            )?.use { c -> while (c.moveToNext()) c.getString(0)?.let { nums += it } }
            send(peer, "👤 $name\n" + nums.distinct().joinToString("\n"), reply = reply)
            null
        } catch (e: Exception) { "Could not send contact" }
    }

    /**
     * Video ni compress chesi app folder lo pettu (WhatsApp laaga).
     * Chinna video (4 MB lopu) ayite alage copy. null = compress ayina 16 MB kanna pedda.
     */
    suspend fun smallVideo(uri: Uri, id: String): Pair<File, String>? {
        val size = Media.size(app, uri)
        if (size in 1..(4L * 1024 * 1024)) return Media.prepareVideo(app, uri, id)
        val out = File(Media.dir(app), "$id.mp4")
        if (Compress.video(app, uri, out)) {
            if (out.length() <= Media.MAX_VIDEO_BYTES) return out to "video/mp4"
            out.delete()
        }
        return withContext(Dispatchers.IO) { Media.prepareVideo(app, uri, id) }
    }

    suspend fun sendAudioFile(peer: String, uri: Uri, reply: Msg?): String? = withContext(Dispatchers.IO) {
        try {
            val size = Media.size(app, uri)
            var mime = app.contentResolver.getType(uri) ?: "audio/mpeg"
            val id = UUID.randomUUID().toString()
            var f: File? = null
            // 1.5 MB kanna pedda audio → AAC lo compress
            if (size < 0 || size > 1_500_000L) {
                val out = File(Media.dir(app), "$id.m4a")
                if (Compress.audio(app, uri, out) && (size < 0 || out.length() < size)) { f = out; mime = "audio/mp4" } else out.delete()
            }
            val file: File = f ?: run {
                if (size > Media.MAX_VIDEO_BYTES) return@withContext "Audio must be smaller than 16 MB"
                val dst = File(Media.dir(app), id + "." + (if (mime.contains("mpeg")) "mp3" else Media.ext(mime)))
                app.contentResolver.openInputStream(uri)?.use { i -> dst.outputStream().use { o -> i.copyTo(o) } } ?: return@withContext "Could not open file"
                dst
            }
            if (file.length() > Media.MAX_VIDEO_BYTES) { file.delete(); return@withContext "Audio must be smaller than 16 MB" }
            send(peer, "", "audio", file.absolutePath, mime, Media.durationMs(file.absolutePath), reply)
            null
        } catch (e: Exception) { "Could not send audio" }
    }

    /** Status ki reply (chat lo vellutundi) */
    fun replyToStatus(st: StatusItem, text: String) {
        val quote = Msg(st.id, st.owner, false, when (st.type) {
            "image" -> "📷 Status" + (if (st.text.isNotBlank()) ": ${st.text}" else "")
            "video" -> "🎥 Status" + (if (st.text.isNotBlank()) ": ${st.text}" else "")
            "voice" -> "🎤 Status"
            else -> "Status: ${st.text}"
        },
            st.ts, 0, st.owner)
        send(st.owner, text, reply = quote)
    }

    // ---------- Storage ----------

    data class StorageInfo(val total: Long, val photos: Long, val videos: Long, val audio: Long, val db: Long, val byChat: List<Pair<String, Long>>)

    suspend fun storageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        var photos = 0L; var videos = 0L; var audio = 0L
        Media.dir(app).listFiles()?.forEach { f ->
            when (f.extension.lowercase()) {
                "jpg", "jpeg", "png", "webp" -> photos += f.length()
                "m4a", "mp3", "aac", "ogg", "wav" -> audio += f.length()
                else -> videos += f.length()
            }
        }
        val dbSize = app.getDatabasePath("flikkchat.db").length()
        val byChat = db.mediaByPeer().map { (peer, paths) -> peer to paths.sumOf { File(it).length() } }
            .filter { it.second > 0 }.sortedByDescending { it.second }
        StorageInfo(photos + videos + audio + dbSize, photos, videos, audio, dbSize, byChat)
    }

    fun clearChatMedia(peer: String) {
        for (p in db.clearMedia(peer)) File(p).delete()
    }

    // ---------- Reactions, polls, pin, view once ----------

    private fun mapJson(m: Map<String, String>) = org.json.JSONObject(m as Map<*, *>).toString()

    /** Emoji reaction (same emoji malli nokkithe teesestundi) */
    fun react(m: Msg, emoji: String) {
        val uid = me ?: return
        val cur = jsonMap(m.reactions).toMutableMap()
        val v = if (cur[uid] == emoji) "" else emoji
        if (v.isEmpty()) cur.remove(uid) else cur[uid] = v
        db.setField(m.id, "reactions", mapJson(cur))
        sendControl(m.peer, mapOf("type" to "react", "target" to m.id, "v" to v))
    }

    fun sendPoll(peer: String, question: String, options: List<String>, multi: Boolean) {
        val j = org.json.JSONObject().put("q", question.trim()).put("o", org.json.JSONArray(options.map { it.trim() }.filter { it.isNotEmpty() })).put("m", multi)
        send(peer, j.toString(), "poll")
    }

    /** Poll vote: indexes "0,2" */
    fun vote(m: Msg, index: Int) {
        val uid = me ?: return
        val poll = pollOf(m) ?: return
        val cur = jsonMap(m.votes).toMutableMap()
        val mine = cur[uid]?.split(",")?.filter { it.isNotEmpty() }?.map { it.toInt() }?.toMutableSet() ?: mutableSetOf()
        if (index in mine) mine -= index else { if (!poll.multi) mine.clear(); mine += index }
        val v = mine.sorted().joinToString(",")
        if (v.isEmpty()) cur.remove(uid) else cur[uid] = v
        db.setField(m.id, "votes", mapJson(cur))
        sendControl(m.peer, mapOf("type" to "vote", "target" to m.id, "v" to v))
    }

    fun pinned(peer: String): String? = prefs.getString("pin_$peer", null)
    fun setPinned(peer: String, msgId: String?) {
        prefs.edit().apply { if (msgId == null) remove("pin_$peer") else putString("pin_$peer", msgId) }.apply()
        db.bump()
    }

    /** View once: okkasari chusaka media delete */
    fun consumeViewOnce(m: Msg) {
        if (m.fromMe || !m.viewOnce) return
        if (m.media.isNotEmpty()) File(m.media).delete()
        db.setField(m.id, "media", "")
    }

    // ---------- Archive & locked chats ----------

    private fun setOf(key: String): Set<String> = prefs.getStringSet(key, emptySet()) ?: emptySet()
    private fun toggleIn(key: String, peer: String, on: Boolean) {
        val s = setOf(key).toMutableSet()
        if (on) s += peer else s -= peer
        prefs.edit().putStringSet(key, s).apply()
        db.bump()
    }
    fun isArchived(peer: String) = peer in setOf("archived")
    fun setArchived(peer: String, on: Boolean) = toggleIn("archived", peer, on)
    fun isLocked(peer: String) = peer in setOf("locked")
    fun setLocked(peer: String, on: Boolean) = toggleIn("locked", peer, on)

    // ---------- Group settings ----------

    suspend fun setGroupFlags(gid: String, flags: String): Boolean = withContext(Dispatchers.IO) {
        try {
            fs.collection("groups").document(gid).update("flags", flags).await()
            refreshGroup(gid)
            sysMessage(gid, "$myName changed group settings")
            true
        } catch (_: Exception) { false }
    }

    suspend fun renameGroup(gid: String, name: String): Boolean = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext false
        try {
            fs.collection("groups").document(gid).update("name", name.trim()).await()
            refreshGroup(gid)
            sysMessage(gid, "$myName changed the group name to \"${name.trim()}\"")
            true
        } catch (_: Exception) { false }
    }

    // ---------- Chat look (bubble, wallpaper, font) ----------

    var bubbleColor: Long
        get() = prefs.getLong("bubble", 0L)
        set(v) { prefs.edit().putLong("bubble", v).apply(); db.bump() }
    var wallpaper: Long
        get() = prefs.getLong("wall", 0L)
        set(v) { prefs.edit().putLong("wall", v).apply(); db.bump() }
    /** 0 small, 1 medium, 2 large */
    var fontSize: Int
        get() = prefs.getInt("font", 1)
        set(v) { prefs.edit().putInt("font", v).apply(); db.bump() }

    /** App language: en, te, ta, hi */
    var language: String
        get() = prefs.getString("lang", "en") ?: "en"
        set(v) { prefs.edit().putString("lang", v).apply() }

    // ---------- Backup info ----------

    var lastBackupAt: Long
        get() = prefs.getLong("backup_at", 0L)
        set(v) { prefs.edit().putLong("backup_at", v).apply() }
    var lastBackupSize: Long
        get() = prefs.getLong("backup_size", 0L)
        set(v) { prefs.edit().putLong("backup_size", v).apply() }

    // ---------- Two-step PIN ----------

    private fun pinHash(phone: String, pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest("flikkchat-pin:$phone:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    val hasPin get() = prefs.getBoolean("has_pin", false)

    suspend fun setPin(pin: String?): Boolean = withContext(Dispatchers.IO) {
        val uid = me ?: return@withContext false
        try {
            val v: Any = if (pin == null) FieldValue.delete() else pinHash(myPhone, pin)
            fs.collection("users").document(uid).update("pin", v).await()
            prefs.edit().putBoolean("has_pin", pin != null).apply()
            true
        } catch (_: Exception) { false }
    }

    /** Ee number ki vere account lo PIN unte, adi check */
    private suspend fun pinOkFor(phone: String, uid: String, pin: String?): Boolean {
        val owner = try { fs.collection("phones").document(phone).get().await().getString("uid") } catch (_: Exception) { null }
        if (owner == null) return true
        // OTP tho same account ki vachina (kotha phone / reinstall) — PIN unte adagali
        val hash = try { fs.collection("users").document(owner).get().await().getString("pin") } catch (_: Exception) { null }
        if (hash.isNullOrEmpty()) return true
        return pin != null && pinHash(phone, pin) == hash
    }

    // ---------- Favourites ----------

    private fun favSet(): Set<String> = prefs.getStringSet("favs", emptySet()) ?: emptySet()
    fun isFav(peer: String) = peer in favSet()
    /** true = ippudu favourite */
    fun toggleFav(peer: String): Boolean {
        val s = favSet().toMutableSet()
        val now = if (peer in s) { s -= peer; false } else { s += peer; true }
        prefs.edit().putStringSet("favs", s).apply()
        db.bump()
        return now
    }

    // ---------- Block ----------

    private fun blockedSet(): Set<String> = prefs.getStringSet("blocked", emptySet()) ?: emptySet()
    fun isBlocked(uid: String) = uid in blockedSet()
    fun setBlocked(uid: String, blocked: Boolean) {
        val s = blockedSet().toMutableSet()
        if (blocked) s += uid else s -= uid
        prefs.edit().putStringSet("blocked", s).apply()
        db.bump()
    }

    // ---------- Disappearing messages ----------

    fun timerOf(peer: String): Long = prefs.getLong("ttl_$peer", 0L)

    fun ttlLabel(ttl: Long): String = when (ttl) {
        0L -> "off"
        86_400_000L -> "24 hours"
        7 * 86_400_000L -> "7 days"
        90 * 86_400_000L -> "90 days"
        else -> "${ttl / 3_600_000} hours"
    }

    fun setTimer(peer: String, ttl: Long) {
        prefs.edit().putLong("ttl_$peer", ttl).apply()
        val text = if (ttl > 0) "$myName turned on disappearing messages (${ttlLabel(ttl)})"
        else "$myName turned off disappearing messages"
        sysNotice(peer, text, mapOf("ttl" to ttl))
    }

    fun clearChat(peer: String) {
        for (p in db.clearChat(peer)) File(p).delete()
    }

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
        if (type == "react" || type == "vote") {
            val target = d.getString("target")
            val local = target?.let { db.msg(it) }
            if (local != null) {
                val col = if (type == "react") "reactions" else "votes"
                val cur = jsonMap(if (type == "react") local.reactions else local.votes).toMutableMap()
                val v = d.getString("v") ?: ""
                if (v.isEmpty()) cur.remove(from) else cur[from] = v
                db.setField(local.id, col, org.json.JSONObject(cur as Map<*, *>).toString())
            }
            try { ref.delete().await() } catch (_: Exception) {}
            return
        }
        if (type == "sdel") {
            val target = d.getString("target")
            val st = target?.let { db.status(it) }
            if (st != null && st.owner == from) {
                if (st.media.isNotEmpty()) File(st.media).delete()
                db.deleteStatus(st.id)
            }
            try { ref.delete().await() } catch (_: Exception) {}
            return
        }
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

        // Block chesina vaalla messages teeseyi
        if (isBlocked(from)) {
            val nn = (d.getLong("n") ?: 0L).toInt()
            for (i in 0 until nn) try {
                fs.collection("inbox").document(myUid).collection("chunks").document("${id}_$i").delete()
            } catch (_: Exception) {}
            try { ref.delete().await() } catch (_: Exception) {}
            return
        }

        // 🔒 Decrypt
        val encrypted = d.getLong("enc") == 1L
        var key: javax.crypto.spec.SecretKeySpec? = null
        var text = d.getString("text") ?: ""
        var rtext = d.getString("rtext") ?: ""
        var rname = d.getString("rname") ?: ""
        if (encrypted) {
            val ct = Crypto.b64d(d.getString("ct") ?: "")
            var plain: ByteArray? = null
            for (refresh in listOf(false, true)) {
                val pub = pubOf(from, refresh) ?: continue
                val k = Crypto.keyFor(pub)
                plain = try { Crypto.decrypt(k, ct) } catch (_: Exception) { null }
                if (plain != null) { key = k; break }
            }
            if (plain == null) {
                text = "🔒 Could not open this message (security key changed)"
            } else {
                val j = JSONObject(String(plain))
                text = j.optString("text"); rtext = j.optString("rtext"); rname = j.optString("rname")
            }
        }

        // 📞 Incoming call
        if (type == "call") {
            val callId = d.getString("call")
            val ts = d.getLong("ts") ?: 0L
            try { ref.delete().await() } catch (_: Exception) {}
            if (callId != null && !isBlocked(from) && System.currentTimeMillis() - ts < 60_000) {
                ensurePeer(from)
                Calls.onIncoming(callId, from, d.getBoolean("video") ?: false)
            }
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
                    val raw = b.toBytes()
                    if (encrypted) {
                        val k = key ?: return
                        buf.write(Crypto.decrypt(k, raw))
                    } else buf.write(raw)
                }
            } catch (_: Exception) {
                return
            }
            val f = File(Media.dir(app), "$id.${Media.ext(mime)}")
            f.writeBytes(buf.toByteArray())
            mediaPath = f.absolutePath
            for (c in chunkRefs) try { c.delete() } catch (_: Exception) {}
        }

        // 🟢 Status
        if (type == "status") {
            val ts = d.getLong("ts") ?: System.currentTimeMillis()
            if (System.currentTimeMillis() - ts < STATUS_TTL) {
                db.insertStatus(StatusItem(id, from, if (mediaPath.isEmpty()) "text" else if (mime.startsWith("video")) "video" else if (mime.startsWith("audio")) "voice" else "image", text,
                    d.getLong("color") ?: 0L, mediaPath, ts, false))
            } else if (mediaPath.isNotEmpty()) File(mediaPath).delete()
            try { ref.delete().await() } catch (_: Exception) {}
            return
        }

        // Disappearing messages timer marchinappudu
        if (type == "sys" && d.contains("ttl")) {
            prefs.edit().putLong("ttl_$peerKey", d.getLong("ttl") ?: 0L).apply()
        }
        val ttl = d.getLong("exp") ?: 0L

        val isOpen = foreground && openChat == peerKey
        val m = Msg(
            id = id, peer = peerKey, fromMe = false,
            text = text,
            ts = d.getLong("ts") ?: System.currentTimeMillis(),
            status = 0, sender = from, type = type, media = mediaPath, mime = mime,
            dur = d.getLong("dur") ?: 0L,
            replyId = d.getString("rid") ?: "", replyText = rtext,
            replyName = rname, fwd = d.getBoolean("fwd") ?: false,
            expires = if (ttl > 0) System.currentTimeMillis() + ttl else 0L,
            viewOnce = d.getBoolean("vo") ?: false,
        )
        val fresh = db.insertMsg(m, readByMe = isOpen || type == "sys")
        try {
            if (type != "sys") sendReceipt(from, id, if (isOpen && readReceipts) 3 else 2, myUid)
            // Server nunchi teeseyi — data phone lo matrame
            ref.delete().await()
        } catch (_: Exception) {}

        val wantNotify = if (group != null) flag("notify_groups", true) else flag("notify_msgs", true)
        if (fresh && !isOpen && type != "sys" && wantNotify && !isMuted(peerKey)) {
            val hidden = isLocked(peerKey)
            val title = if (hidden) "Double Bubble" else db.peer(peerKey)?.name ?: "Double Bubble"
            val body = when {
                hidden || !flag("notify_preview", true) -> "New message"
                group != null -> "${nameOf(from)}: ${preview(m)}"
                else -> preview(m)
            }
            notifyMessage(peerKey, title, body)
        }
    }

    private suspend fun onReceipt(d: DocumentSnapshot) {
        val msgId = d.getString("msg") ?: d.id
        val status = (d.getLong("status") ?: return).toInt()
        val by = d.getString("by") ?: ""
        if (status == 4) {
            if (db.status(msgId) != null) db.addView(msgId, by)
            try { d.reference.delete().await() } catch (_: Exception) {}
            return
        }
        val local = db.msg(msgId)
        if (local != null) {
            if (isGroup(local.peer)) {
                val members = db.group(gidOf(local.peer))?.members?.filter { it != me } ?: emptyList()
                db.updateStatus(msgId, db.groupReceipt(msgId, by, status, members))
            } else {
                db.groupReceipt(msgId, by, status, emptyList())
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
        dur: Long = 0, reply: Msg? = null, fwd: Boolean = false, viewOnce: Boolean = false,
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
            expires = timerOf(peer).let { if (it > 0) System.currentTimeMillis() + it else 0L },
            viewOnce = viewOnce,
        )
        db.insertMsg(m)
        scope.launch { push(m) }
    }

    /** Photo / video pampu. Error unte message return chestundi. */
    suspend fun sendMedia(
        peer: String, uri: Uri, isVideo: Boolean, caption: String, reply: Msg?,
        hd: Boolean = false, viewOnce: Boolean = false,
    ): String? =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            try {
                if (isVideo) {
                    val (f, mime) = smallVideo(uri, id)
                        ?: return@withContext "Video is too long to send. Try a shorter video."
                    send(peer, caption, "video", f.absolutePath, mime, Media.durationMs(f.absolutePath), reply, viewOnce = viewOnce)
                } else {
                    val f = Media.prepareImage(app, uri, id, hd)
                    send(peer, caption, "image", f.absolutePath, "image/jpeg", 0, reply, viewOnce = viewOnce)
                }
                null
            } catch (e: Exception) {
                "Could not send"
            }
        }

    fun sendVoice(peer: String, file: File, dur: Long, reply: Msg?) =
        send(peer, "", "audio", file.absolutePath, "audio/mp4", dur, reply)

    private suspend fun push(m: Msg) {
        if (me == null) return
        synchronized(inFlight) { if (!inFlight.add(m.id)) return }
        try {
            pushLock.withLock {
                if (deliver(m, recipients(m.peer))) db.updateStatus(m.id, 1)
            }
        } catch (_: Exception) {
            // Internet vachaka malli try chestundi
        } finally {
            synchronized(inFlight) { inFlight.remove(m.id) }
        }
    }

    /** Prathi recipient ki 🔒 encrypt chesi inbox lo pedtundi. Exception = network problem. */
    private suspend fun deliver(m: Msg, to: List<String>, extra: Map<String, Any> = emptyMap()): Boolean {
        val uid = me ?: return false
        val base = mutableMapOf<String, Any>("from" to uid, "ts" to m.ts, "type" to m.type)
        base.putAll(extra)
        if (isGroup(m.peer)) base["group"] = gidOf(m.peer)
        if (m.replyId.isNotEmpty()) base["rid"] = m.replyId
        if (m.fwd) base["fwd"] = true
        if (m.viewOnce) base["vo"] = true
        if (m.dur > 0) base["dur"] = m.dur
        if (m.expires > 0 && m.type != "status") base["exp"] = (m.expires - m.ts).coerceAtLeast(60_000L)
        var bytes: ByteArray? = null
        if (m.media.isNotEmpty()) {
            val f = File(m.media)
            if (!f.exists()) return false
            bytes = f.readBytes()
            base["mime"] = m.mime
        }
        for (r in to) {
            val payload = base.toMutableMap()
            val pub = pubOf(r, refresh = true)
            val key = pub?.let { Crypto.keyFor(it) }
            if (key != null) {
                val j = JSONObject().put("text", m.text).put("rtext", m.replyText).put("rname", m.replyName)
                payload["enc"] = 1
                payload["ct"] = Crypto.b64(Crypto.encrypt(key, j.toString().toByteArray()))
            } else {
                // Paata app (key ledu) — plain
                payload["text"] = m.text
                if (m.replyId.isNotEmpty()) { payload["rtext"] = m.replyText; payload["rname"] = m.replyName }
            }
            val inbox = fs.collection("inbox").document(r)
            val b = bytes
            if (b != null) {
                val chunks = (b.indices step CHUNK).map { b.copyOfRange(it, minOf(it + CHUNK, b.size)) }
                payload["n"] = chunks.size
                chunks.forEachIndexed { i, c ->
                    val data = if (key != null) Crypto.encrypt(key, c) else c
                    inbox.collection("chunks").document("${m.id}_$i")
                        .set(mapOf("from" to uid, "b" to Blob.fromBytes(data))).await()
                }
            }
            inbox.collection("msgs").document(m.id).set(payload).await()
        }
        return true
    }

    // ---------- Status ----------

    /** Naa contacts (block chesinavaallu kaakunda) */
    private fun statusAudience(): List<String> = db.people().map { it.uid }.filter { it != me && !isBlocked(it) }

    suspend fun postTextStatus(text: String, color: Long): Boolean = withContext(Dispatchers.IO) {
        val uid = me ?: return@withContext false
        val st = StatusItem(UUID.randomUUID().toString(), uid, "text", text.trim(), color, "", System.currentTimeMillis(), true)
        db.insertStatus(st)
        shareStatus(st)
        true
    }

    suspend fun postPhotoStatus(uri: Uri, caption: String): String? = withContext(Dispatchers.IO) {
        val uid = me ?: return@withContext "Not logged in"
        val id = UUID.randomUUID().toString()
        try {
            val f = Media.prepareImage(app, uri, id)
            val st = StatusItem(id, uid, "image", caption.trim(), 0, f.absolutePath, System.currentTimeMillis(), true)
            db.insertStatus(st)
            shareStatus(st)
            null
        } catch (e: Exception) { "Could not post status" }
    }

    /** Video status (16 MB lopu) */
    suspend fun postVideoStatus(uri: Uri, caption: String): String? = withContext(Dispatchers.IO) {
        val uid = me ?: return@withContext "Not logged in"
        val id = UUID.randomUUID().toString()
        try {
            val (f, mime) = smallVideo(uri, id) ?: return@withContext "Video is too long to send. Try a shorter video."
            val st = StatusItem(id, uid, "video", caption.trim(), 0, f.absolutePath, System.currentTimeMillis(), true)
            db.insertStatus(st)
            shareStatus(st, mime)
            null
        } catch (e: Exception) { "Could not post status" }
    }

    /** Voice status (recorder file) */
    fun postVoiceStatus(file: File, color: Long) {
        val uid = me ?: return
        val st = StatusItem(UUID.randomUUID().toString(), uid, "voice", "", color, file.absolutePath, System.currentTimeMillis(), true)
        db.insertStatus(st)
        shareStatus(st, "audio/mp4")
    }

    /** Vere vaalla status ni naa status ga malli pettu */
    suspend fun repostStatus(st: StatusItem): String? = withContext(Dispatchers.IO) {
        when (st.type) {
            "text" -> { postTextStatus(st.text, st.color); null }
            "image" -> postPhotoStatus(Uri.fromFile(File(st.media)), st.text)
            "video" -> postVideoStatus(Uri.fromFile(File(st.media)), st.text)
            "voice" -> try {
                val f = File(Media.dir(app), "${UUID.randomUUID()}.m4a")
                File(st.media).copyTo(f)
                postVoiceStatus(f, st.color); null
            } catch (_: Exception) { "Could not post status" }
            else -> null
        }
    }

    private fun shareStatus(st: StatusItem, mime: String = if (st.media.isNotEmpty()) "image/jpeg" else "") {
        val m = Msg(st.id, "", true, st.text, st.ts, 0, me ?: "", "status", st.media, mime)
        val to = statusAudience()
        scope.launch {
            try { deliver(m, to, mapOf("color" to st.color)) } catch (_: Exception) {}
        }
    }

    fun deleteMyStatus(st: StatusItem) {
        if (st.media.isNotEmpty()) File(st.media).delete()
        db.deleteStatus(st.id)
        // Andari phones nunchi kuda teeseyi
        sendTo(statusAudience(), mapOf("type" to "sdel", "target" to st.id))
    }

    /** Vere vaalla status chusinappudu — vaallaki "seen" pampu */
    fun viewStatus(st: StatusItem) {
        val uid = me ?: return
        if (st.seen || st.owner == uid) return
        db.markStatusSeen(st.id)
        scope.launch {
            try {
                fs.collection("inbox").document(st.owner).collection("receipts").document("${st.id}_$uid")
                    .set(mapOf("msg" to st.id, "status" to 4, "by" to uid)).await()
            } catch (_: Exception) {}
        }
    }

    private fun sendControl(peer: String, data: Map<String, Any>) = sendTo(recipients(peer), data, peer)

    /** Chinna control message (call, sys, delete...) */
    fun sendTo(to: List<String>, data: Map<String, Any>, peer: String = "") {
        val uid = me ?: return
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

    /** Anni chats read ani mark cheyyi */
    fun markAllRead() {
        for (c in db.chats()) if (c.unread > 0) markRead(c.peer)
    }

    /** Chat open chesinappudu — blue ticks pampu. */
    fun markRead(peer: String) {
        val unread = db.takeUnread(peer)
        val uid = me ?: return
        NotificationManagerCompat.from(app).cancel(peer.hashCode())
        if (unread.isEmpty() || !readReceipts) return
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
            db.upsertGroup(Group(gid, g.getString("name") ?: "Group", members, admins, g.getString("flags") ?: ""))
            for (u in members) ensurePeer(u)
        } catch (_: Exception) {
            // Permission ledu = group nunchi teesesaru
            val uid = me ?: return
            db.group(gid)?.let { db.upsertGroup(it.copy(members = it.members - uid)) }
        }
    }

    private fun sysMessage(gid: String, text: String) = sysNotice("g:$gid", text)

    private fun sysNotice(peer: String, text: String, extra: Map<String, Any> = emptyMap()) {
        val uid = me ?: return
        val m = Msg(UUID.randomUUID().toString(), peer, true, text, System.currentTimeMillis(), 3, uid, "sys")
        db.insertMsg(m)
        sendControl(peer, mapOf("type" to "sys", "text" to text) + extra)
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
        if (!fg) backgroundAt = System.currentTimeMillis()
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
                        .set(mapOf("lastActive" to if (hideSeen) 0L else System.currentTimeMillis() - ONLINE_WINDOW_MS), SetOptions.merge())
                } catch (_: Exception) {}
            }
        }
    }

    private fun touchPresence() {
        val uid = me ?: return
        fs.collection("users").document(uid)
            .set(mapOf("lastActive" to if (hideSeen) 0L else System.currentTimeMillis()), SetOptions.merge())
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
