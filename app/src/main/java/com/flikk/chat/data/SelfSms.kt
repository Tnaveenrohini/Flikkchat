package com.flikk.chat.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.security.SecureRandom

/**
 * Free number verification (Firebase SMS / DLT avasaram ledu):
 * ee phone SIM nunchi user ichina number ki SMS pampi, adi ade phone ki vachindo chustam.
 * Vaste — aa number SIM ee phone lone undi.
 */
object SelfSms {
    private var receiver: BroadcastReceiver? = null

    fun newCode(): String = (100000 + SecureRandom().nextInt(900000)).toString()

    /** SMS receive listener. onCode = SMS lo code dorikite. */
    fun listen(ctx: Context, code: String, onCode: (String) -> Unit) {
        stop(ctx)
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val msgs = try { Telephony.Sms.Intents.getMessagesFromIntent(intent) } catch (_: Exception) { null } ?: return
                val body = msgs.joinToString("") { it.messageBody ?: "" }
                if (body.contains(code)) onCode(code)
            }
        }
        receiver = r
        ContextCompat.registerReceiver(
            ctx.applicationContext, r, IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION), ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun stop(ctx: Context) {
        receiver?.let { try { ctx.applicationContext.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
    }

    /** null = SMS pampindi; lekapothe error */
    fun send(ctx: Context, phone: String, code: String): String? = try {
        @Suppress("DEPRECATION")
        val sm: SmsManager = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        sm.sendTextMessage(phone, null, "Double Bubble code: $code\nIdi mee number verify cheyyadaniki. Evvariki share cheyyakandi.", null, null)
        null
    } catch (e: Exception) {
        "Could not send SMS from this phone. Check SIM and SMS balance."
    }
}
