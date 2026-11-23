package com.flikk.chat.data

import android.content.SharedPreferences
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption.
 *  - Prathi phone ki oka EC (P-256) key pair. Private key phone daatadu.
 *  - Public key Firestore users/{uid}.pub lo.
 *  - Iddari madhya shared key = ECDH(naa private, vaalla public) → SHA-256 → AES-256-GCM.
 *  - Firebase ki kevalam locked (encrypted) data matrame kanipistundi.
 */
object Crypto {
    private const val PREF_PRIV = "e2e_priv"
    private const val PREF_PUB = "e2e_pub"
    private val rnd = SecureRandom()
    private val shared = ConcurrentHashMap<String, SecretKeySpec>()

    private lateinit var priv: PrivateKey
    lateinit var myPub: String
        private set

    fun init(prefs: SharedPreferences) {
        val p = prefs.getString(PREF_PRIV, null)
        val q = prefs.getString(PREF_PUB, null)
        if (p != null && q != null) {
            priv = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(b64d(p)))
            myPub = q
            return
        }
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec("secp256r1"), rnd)
        val kp = g.generateKeyPair()
        priv = kp.private
        myPub = b64(kp.public.encoded)
        prefs.edit().putString(PREF_PRIV, b64(kp.private.encoded)).putString(PREF_PUB, myPub).apply()
    }

    /** Peer public key (base64) tho shared AES key */
    fun keyFor(peerPub: String): SecretKeySpec = shared.getOrPut(peerPub) {
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(b64d(peerPub)))
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv)
        ka.doPhase(pub, true)
        val secret = ka.generateSecret()
        val k = MessageDigest.getInstance("SHA-256").apply {
            update(secret)
            update("flikkchat-e2e-v1".toByteArray())
        }.digest()
        SecretKeySpec(k, "AES")
    }

    /** iv(12) + ciphertext */
    fun encrypt(key: SecretKeySpec, data: ByteArray): ByteArray {
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + c.doFinal(data)
    }

    fun decrypt(key: SecretKeySpec, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
        return c.doFinal(data, 12, data.size - 12)
    }

    /** Iddari keys tho security code (WhatsApp "verify" laaga) */
    fun safetyCode(peerPub: String): String {
        val (a, b) = listOf(myPub, peerPub).sorted()
        val d = MessageDigest.getInstance("SHA-256").digest((a + b).toByteArray())
        return (0 until 12).joinToString(" ") { i ->
            val n = ((d[i * 2].toInt() and 0xff) shl 8) or (d[i * 2 + 1].toInt() and 0xff)
            "%05d".format(n % 100000)
        }
    }

    fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    fun b64d(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}
