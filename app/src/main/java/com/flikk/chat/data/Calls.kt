package com.flikk.chat.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.flikk.chat.MainActivity
import com.flikk.chat.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class Phase { OUTGOING, INCOMING, CONNECTING, CONNECTED, ENDED }

data class CallState(
    val id: String,
    val peer: String,
    val video: Boolean,
    val outgoing: Boolean,
    val phase: Phase,
    val connectedAt: Long = 0,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val cameraOff: Boolean = false,
    val endReason: String = "",
)

/**
 * 📞 Voice / video calls — WebRTC (phone to phone direct).
 * Signaling (offer/answer/candidates) Firestore calls/{id} lo.
 * Audio/video WebRTC (DTLS-SRTP) tho already encrypted.
 */
object Calls {
    private const val CHANNEL = "calls"
    private const val NOTIF_ID = 4242
    private const val RING_TIMEOUT = 45_000L

    private lateinit var app: Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val fs: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<CallState?>(null)
    val state: StateFlow<CallState?> = _state

    private val _remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo: StateFlow<VideoTrack?> = _remoteVideo
    private val _localVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo: StateFlow<VideoTrack?> = _localVideo

    val egl: EglBase by lazy { EglBase.create() }
    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var capturer: CameraVideoCapturer? = null
    private var texHelper: SurfaceTextureHelper? = null
    private val regs = mutableListOf<ListenerRegistration>()
    private var timeout: Job? = null
    private var startedAt = 0L
    private var remoteSet = false
    private val pendingRemote = mutableListOf<IceCandidate>()

    fun init(application: Application) {
        app = application
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = app.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), null)
                }
            )
        }
    }

    private fun factory(): PeerConnectionFactory = factory ?: run {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions())
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
            .also { factory = it }
    }

    fun hasPermissions(video: Boolean): Boolean {
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (video) need += Manifest.permission.CAMERA
        return need.all { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun call() = fs.collection("calls")

    // ---------------- Outgoing ----------------

    fun start(peer: String, video: Boolean) {
        val me = Chat.me ?: return
        if (_state.value != null) return
        val id = UUID.randomUUID().toString()
        startedAt = System.currentTimeMillis()
        _state.value = CallState(id, peer, video, outgoing = true, phase = Phase.OUTGOING)
        scope.launch {
            try {
                setupPeer(id, video, isCaller = true)
                val offer = createSdp(offer = true)
                setLocal(offer)
                call().document(id).set(
                    mapOf(
                        "from" to me, "to" to peer, "video" to video, "state" to "ringing",
                        "ts" to System.currentTimeMillis(),
                        "offer" to mapOf("type" to offer.type.canonicalForm(), "sdp" to offer.description),
                    )
                ).await()
                Chat.sendTo(listOf(peer), mapOf("type" to "call", "call" to id, "video" to video))
                watchCall(id, isCaller = true)
                timeout = scope.launch {
                    delay(RING_TIMEOUT)
                    if (_state.value?.phase == Phase.OUTGOING) end("No answer", remoteState = "missed")
                }
            } catch (e: Exception) {
                end("Call fail: ${e.message}")
            }
        }
    }

    // ---------------- Incoming ----------------

    fun onIncoming(id: String, from: String, video: Boolean) {
        scope.launch {
            if (_state.value != null) {
                try { call().document(id).update("state", "busy").await() } catch (_: Exception) {}
                return@launch
            }
            // Silence unknown callers: contacts lo lekapothe ring cheyyadu, missed ga log
            if (Chat.flag("silence_unknown", false) && !Chat.inContacts(Chat.db.peer(from)?.phone ?: "")) {
                Chat.db.logCall(CallLog(id, from, false, video, System.currentTimeMillis(), 0, true))
                return@launch
            }
            // Call inka ring avutunda?
            val doc = try { call().document(id).get().await() } catch (_: Exception) { null }
            if (doc == null || doc.getString("state") != "ringing") return@launch
            startedAt = System.currentTimeMillis()
            _state.value = CallState(id, from, video, outgoing = false, phase = Phase.INCOMING)
            watchCall(id, isCaller = false)
            showIncomingNotification(from, video)
            timeout = scope.launch {
                delay(RING_TIMEOUT)
                if (_state.value?.phase == Phase.INCOMING) end("Missed call", missed = true)
            }
        }
    }

    fun accept() {
        val st = _state.value ?: return
        if (st.phase != Phase.INCOMING) return
        cancelNotification()
        _state.value = st.copy(phase = Phase.CONNECTING, speaker = st.video)
        scope.launch {
            try {
                val doc = call().document(st.id).get().await()
                @Suppress("UNCHECKED_CAST")
                val offer = doc.get("offer") as? Map<String, Any> ?: error("Offer ledu")
                setupPeer(st.id, st.video, isCaller = false)
                setRemote(SessionDescription(SessionDescription.Type.OFFER, offer["sdp"] as String))
                val answer = createSdp(offer = false)
                setLocal(answer)
                call().document(st.id).update(
                    mapOf(
                        "state" to "accepted",
                        "answer" to mapOf("type" to answer.type.canonicalForm(), "sdp" to answer.description),
                    )
                ).await()
            } catch (e: Exception) {
                end("Call fail: ${e.message}")
            }
        }
    }

    fun decline() = end("Declined", remoteState = "declined")

    fun hangup() = end("Call ended", remoteState = "ended")

    // ---------------- Controls ----------------

    fun toggleMute() {
        val st = _state.value ?: return
        audioTrack?.setEnabled(st.muted)
        _state.value = st.copy(muted = !st.muted)
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val st = _state.value ?: return
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = !st.speaker
        _state.value = st.copy(speaker = !st.speaker)
    }

    fun toggleCamera() {
        val st = _state.value ?: return
        _localVideo.value?.setEnabled(st.cameraOff)
        _state.value = st.copy(cameraOff = !st.cameraOff)
    }

    fun switchCamera() { capturer?.switchCamera(null) }

    // ---------------- WebRTC ----------------

    private suspend fun iceServers(): List<PeerConnection.IceServer> {
        val list = mutableListOf(
            PeerConnection.IceServer.builder(listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")).createIceServer()
        )
        // Optional TURN server (Firestore config/turn: {urls: [...], username, credential})
        try {
            val t = fs.collection("config").document("turn").get().await()
            @Suppress("UNCHECKED_CAST")
            val urls = t.get("urls") as? List<String>
            if (!urls.isNullOrEmpty()) {
                list += PeerConnection.IceServer.builder(urls)
                    .setUsername(t.getString("username") ?: "")
                    .setPassword(t.getString("credential") ?: "")
                    .createIceServer()
            }
        } catch (_: Exception) {}
        return list
    }

    private suspend fun setupPeer(id: String, video: Boolean, isCaller: Boolean) {
        val f = factory()
        val myCol = call().document(id).collection(if (isCaller) "c_caller" else "c_callee")
        val config = PeerConnection.RTCConfiguration(iceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val peer = f.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) {
                myCol.add(mapOf("sdp" to c.sdp, "mid" to (c.sdpMid ?: ""), "idx" to c.sdpMLineIndex))
            }
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {
                scope.launch {
                    when (s) {
                        PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> {
                            val st = _state.value ?: return@launch
                            if (st.phase != Phase.CONNECTED) {
                                timeout?.cancel()
                                _state.value = st.copy(phase = Phase.CONNECTED, connectedAt = System.currentTimeMillis())
                            }
                        }
                        PeerConnection.IceConnectionState.FAILED ->
                            end("Connection fail (network block chesindi — TURN server kavali)", remoteState = "ended")
                        else -> {}
                    }
                }
            }
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                val t = receiver.track()
                if (t is VideoTrack) scope.launch { _remoteVideo.value = t }
            }
            override fun onSignalingChange(s: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
            override fun onAddStream(s: MediaStream) {}
            override fun onRemoveStream(s: MediaStream) {}
            override fun onDataChannel(d: DataChannel) {}
            override fun onRenegotiationNeeded() {}
        }) ?: error("PeerConnection create avvaledu")
        pc = peer

        audioSource = f.createAudioSource(MediaConstraints())
        audioTrack = f.createAudioTrack("audio0", audioSource).also { peer.addTrack(it, listOf("s0")) }

        if (video) {
            val enumerator = Camera2Enumerator(app)
            val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull()
            if (name != null) {
                val cap = enumerator.createCapturer(name, null)
                val helper = SurfaceTextureHelper.create("cam", egl.eglBaseContext)
                val src = f.createVideoSource(false)
                cap.initialize(helper, app, src.capturerObserver)
                cap.startCapture(1280, 720, 30)
                val track = f.createVideoTrack("video0", src)
                peer.addTrack(track, listOf("s0"))
                capturer = cap; texHelper = helper; videoSource = src
                _localVideo.value = track
            }
        }

        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = video

        // Avatali vaipu candidates
        val other = call().document(id).collection(if (isCaller) "c_callee" else "c_caller")
        regs += other.addSnapshotListener { snap, _ ->
            snap ?: return@addSnapshotListener
            for (ch in snap.documentChanges) {
                val d = ch.document
                val c = IceCandidate(d.getString("mid") ?: "", (d.getLong("idx") ?: 0L).toInt(), d.getString("sdp") ?: continue)
                if (remoteSet) pc?.addIceCandidate(c) else pendingRemote += c
            }
        }
    }

    private fun watchCall(id: String, isCaller: Boolean) {
        regs += call().document(id).addSnapshotListener { d, _ ->
            d ?: return@addSnapshotListener
            val stateStr = d.getString("state") ?: return@addSnapshotListener
            when (stateStr) {
                "accepted" -> if (isCaller && !remoteSet) {
                    @Suppress("UNCHECKED_CAST")
                    val ans = d.get("answer") as? Map<String, Any> ?: return@addSnapshotListener
                    scope.launch {
                        try {
                            _state.value = _state.value?.copy(phase = Phase.CONNECTING)
                            setRemote(SessionDescription(SessionDescription.Type.ANSWER, ans["sdp"] as String))
                        } catch (e: Exception) { end("Call fail: ${e.message}") }
                    }
                } else if (!isCaller && _state.value?.phase == Phase.INCOMING) {
                    // Vere device lo answer chesaru
                    end("Answered elsewhere")
                }
                "declined" -> if (isCaller) end("Declined")
                "busy" -> if (isCaller) end("Busy")
                "ended", "missed" -> end(if (_state.value?.phase == Phase.INCOMING) "Missed call" else "Call ended",
                    missed = _state.value?.phase == Phase.INCOMING)
            }
        }
    }

    private suspend fun createSdp(offer: Boolean): SessionDescription = suspendCancellableCoroutine { cont ->
        val c = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        val obs = object : SdpObserver {
            override fun onCreateSuccess(s: SessionDescription) { if (cont.isActive) cont.resume(s) }
            override fun onCreateFailure(e: String?) { if (cont.isActive) cont.resumeWithException(IllegalStateException(e)) }
            override fun onSetSuccess() {}
            override fun onSetFailure(e: String?) {}
        }
        val p = pc ?: run { cont.resumeWithException(IllegalStateException("No connection")); return@suspendCancellableCoroutine }
        if (offer) p.createOffer(obs, c) else p.createAnswer(obs, c)
    }

    private suspend fun setSdp(local: Boolean, s: SessionDescription): Unit = suspendCancellableCoroutine { cont ->
        val obs = object : SdpObserver {
            override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
            override fun onSetFailure(e: String?) { if (cont.isActive) cont.resumeWithException(IllegalStateException(e)) }
            override fun onCreateSuccess(s: SessionDescription) {}
            override fun onCreateFailure(e: String?) {}
        }
        val p = pc ?: run { cont.resumeWithException(IllegalStateException("No connection")); return@suspendCancellableCoroutine }
        if (local) p.setLocalDescription(obs, s) else p.setRemoteDescription(obs, s)
    }

    private suspend fun setLocal(s: SessionDescription) = setSdp(true, s)

    private suspend fun setRemote(s: SessionDescription) {
        setSdp(false, s)
        remoteSet = true
        for (c in pendingRemote) pc?.addIceCandidate(c)
        pendingRemote.clear()
    }

    // ---------------- End ----------------

    @Synchronized
    private fun end(reason: String, remoteState: String? = null, missed: Boolean = false) {
        val st = _state.value ?: return
        if (st.phase == Phase.ENDED) return
        timeout?.cancel()
        cancelNotification()
        if (remoteState != null) {
            scope.launch { try { call().document(st.id).set(mapOf("state" to remoteState), SetOptions.merge()).await() } catch (_: Exception) {} }
        }
        val dur = if (st.connectedAt > 0) System.currentTimeMillis() - st.connectedAt else 0L
        Chat.db.logCall(CallLog(st.id, st.peer, st.outgoing, st.video, startedAt, dur, missed || (!st.outgoing && st.connectedAt == 0L)))
        if (missed) showMissedNotification(st.peer, st.video)

        regs.forEach { it.remove() }; regs.clear()
        try { capturer?.stopCapture() } catch (_: Exception) {}
        capturer?.dispose(); capturer = null
        _localVideo.value?.dispose(); _localVideo.value = null
        _remoteVideo.value = null
        videoSource?.dispose(); videoSource = null
        texHelper?.dispose(); texHelper = null
        audioTrack?.dispose(); audioTrack = null
        audioSource?.dispose(); audioSource = null
        pc?.dispose(); pc = null
        remoteSet = false; pendingRemote.clear()
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = false

        _state.value = st.copy(phase = Phase.ENDED, endReason = reason)
        scope.launch {
            delay(1500)
            if (_state.value?.id == st.id) _state.value = null
        }
    }

    // ---------------- Notifications ----------------

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(
        app, 99, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun showIncomingNotification(from: String, video: Boolean) {
        if (!canNotify()) return
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Chat.nameOf(from))
            .setContentText(if (video) "📹 Incoming video call" else "📞 Incoming voice call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(openIntent())
            .setFullScreenIntent(openIntent(), true)
            .build()
        NotificationManagerCompat.from(app).notify(NOTIF_ID, n)
    }

    @SuppressLint("MissingPermission")
    private fun showMissedNotification(from: String, video: Boolean) {
        if (!canNotify()) return
        val n = NotificationCompat.Builder(app, "messages")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Chat.nameOf(from))
            .setContentText(if (video) "Missed video call" else "Missed voice call")
            .setAutoCancel(true)
            .setContentIntent(openIntent())
            .build()
        NotificationManagerCompat.from(app).notify(from.hashCode() + 7, n)
    }

    private fun cancelNotification() = NotificationManagerCompat.from(app).cancel(NOTIF_ID)
}
