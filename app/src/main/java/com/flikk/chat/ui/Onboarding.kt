package com.flikk.chat.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flikk.chat.data.Chat
import kotlinx.coroutines.launch

/** Design lo peda pill button */
@Composable
fun PillButton(
    text: String, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false,
    bg: Color = Accent, fg: Color = OnAccent, onClick: () -> Unit,
) {
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 2.dp)
        else Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Design lo label + box input */
@Composable
fun BoxField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    focused: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, big: Boolean = false,
) {
    Column(modifier) {
        if (label.isNotEmpty()) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DayInk)
            Spacer(Modifier.height(8.dp))
        }
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
                .border(if (focused) 2.dp else 1.5.dp, if (focused) Accent else Line, RoundedCornerShape(16.dp))
                .background(Bg).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                textStyle = TextStyle(fontFamily = Jakarta, fontSize = if (big) 18.sp else 16.sp, color = Ink,
                    fontWeight = if (big) FontWeight.Bold else FontWeight.SemiBold, letterSpacing = if (big) 1.sp else 0.sp),
                cursorBrush = SolidColor(Accent), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------- Welcome ----------------

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(NavBg).statusBarsPadding().navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 34.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            WBubble(tr("Namaskaram!"), left = true, Modifier.offset(x = 10.dp, y = 60.dp))
            WBubble(tr("How are you?"), left = false, Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 130.dp))
            Row(
                Modifier.offset(x = 40.dp, y = 210.dp).clip(RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp))
                    .background(Color(0xFF2A2D3A)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, null, tint = NavBg, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                listOf(10, 18, 14, 22, 12, 16, 8).forEachIndexed { i, h ->
                    Box(Modifier.padding(horizontal = 1.dp).size(3.dp, h.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i < 4) Color(0xFFC9BFFF) else Color(0xFF6B6F80)))
                }
                Spacer(Modifier.width(8.dp))
                Text("0:12", fontSize = 12.sp, color = NavMuted)
            }
            Row(
                Modifier.align(Alignment.TopEnd).offset(x = (-30).dp, y = 290.dp)
                    .clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)).background(LightPalette.accent)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Videocam, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Video call"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(LightPalette.accent), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(tr("Close to your people,\nalways."), color = Color.White, fontSize = 38.sp, lineHeight = 40.sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp)
        Spacer(Modifier.height(14.dp))
        Text(tr("Free messages, voice and video calls. Your chats stay on your phone."),
            color = Color(0xFFC3C5D3), fontSize = 16.sp, lineHeight = 24.sp)
        Spacer(Modifier.height(28.dp))
        PillButton(tr("Get started"), bg = Color.White, fg = NavBg, onClick = onStart)
        Spacer(Modifier.height(14.dp))
        Text(tr("By continuing, you agree to the Double Bubble Terms and Privacy Policy."),
            color = NavMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun BoxScope.WBubble(text: String, left: Boolean, modifier: Modifier) {
    Text(
        text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(
            if (left) RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp) else RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
        ).background(if (left) Color(0xFF2A2D3A) else LightPalette.accent).padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

// ---------------- Phone login ----------------

@Composable
fun PhoneLoginScreen(onBack: () -> Unit, onNext: (String) -> Unit) {
    var phone by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 34.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.offset(x = (-10).dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, tr("Back"), tint = Ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(tr("Enter your phone number"), fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp, color = Ink)
        Spacer(Modifier.height(10.dp))
        Text(tr("Your friends will find you on Double Bubble with this number."), fontSize = 15.sp, lineHeight = 22.sp, color = Muted)
        Spacer(Modifier.height(30.dp))
        Text(tr("Country"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DayInk)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, Line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Text("🇮🇳  India", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink) }
        Spacer(Modifier.height(18.dp))
        Text(tr("Phone number"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DayInk)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(76.dp, 56.dp).clip(RoundedCornerShape(16.dp)).background(Surface), contentAlignment = Alignment.Center) {
                Text("+91", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
            }
            BoxField("", phone, { phone = it.filter { c -> c.isDigit() || c == ' ' }.take(12); error = null },
                Modifier.weight(1f), focused = true, keyboard = KeyboardType.Phone, big = true)
        }
        error?.let { Text(it, color = Danger, fontSize = 13.sp) }
        Spacer(Modifier.weight(1f))
        PillButton(tr("Next"), enabled = phone.count { it.isDigit() } == 10) {
            val n = Chat.normalize("+91" + phone.filter { it.isDigit() })
            if (n == null) error = tr("Enter a 10-digit number") else onNext(n)
        }
    }
}

// ---------------- Profile setup ----------------

@Composable
fun ProfileSetupScreen(phone: String, onBack: () -> Unit, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var about by rememberSaveable { mutableStateOf(tr("Hey there! I am on Double Bubble")) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var needPin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 34.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-10).dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, tr("Back"), tint = Ink, modifier = Modifier.size(20.dp))
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) { Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Accent)) }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(tr("Your profile"), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, color = Ink)
        Spacer(Modifier.height(10.dp))
        Text(tr("Your contacts will see your name and about."), fontSize = 15.sp, color = Muted)
        Spacer(Modifier.height(30.dp))
        Box(Modifier.align(Alignment.CenterHorizontally)) {
            if (name.isBlank()) Box(Modifier.size(128.dp).clip(RoundedCornerShape(44.dp)).background(Surface), contentAlignment = Alignment.Center) {
                Text("?", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Muted)
            } else Avatar(name, 128.dp, rounded = 44.dp)
        }
        Spacer(Modifier.height(30.dp))
        BoxField(tr("Name"), name, { name = it.take(40) }, focused = true)
        Spacer(Modifier.height(18.dp))
        BoxField(tr("About"), about, { about = it.take(140) })
        if (needPin) {
            Spacer(Modifier.height(18.dp))
            BoxField(tr("Two-step verification PIN"), pin, { pin = it.filter { c -> c.isDigit() }.take(6) }, focused = true, keyboard = KeyboardType.NumberPassword)
        }
        error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Danger, fontSize = 13.sp) }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = Muted, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(phone, color = Muted, fontSize = 13.sp)
        }
        PillButton(tr("Start chatting"), enabled = name.isNotBlank(), busy = busy) {
            busy = true; error = null
            scope.launch {
                val err = Chat.register(name, phone, if (needPin) pin else null)
                if (err == null && about.isNotBlank()) Chat.updateProfile(name, about)
                busy = false
                when {
                    err == null -> onDone()
                    err == Chat.PIN_REQUIRED -> { needPin = true; error = tr("This number has two-step verification. Enter the PIN.") }
                    else -> error = tr(err)
                }
            }
        }
    }
}

// ---------------- OTP (real SMS) ----------------

/** SMS lo vachina 6-digit code. Auto-read ayite automatic ga verify. */
@Composable
fun OtpScreen(phone: String, onBack: () -> Unit, onVerified: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val activity = remember(ctx) {
        var c = ctx
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        c as? android.app.Activity
    }
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var verId by rememberSaveable { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf<com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(true) }
    var wait by remember { mutableIntStateOf(60) }

    fun finish(cred: com.google.firebase.auth.PhoneAuthCredential) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            val err = Chat.verifyOtp(cred)
            busy = false
            if (err == null) onVerified() else error = tr(err)
        }
    }
    val listener = remember {
        object : Chat.OtpListener {
            override fun onCodeSent(verificationId: String, t: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken) {
                verId = verificationId; token = t; sending = false; wait = 60
            }
            override fun onAutoVerified(cred: com.google.firebase.auth.PhoneAuthCredential) {
                sending = false
                cred.smsCode?.let { code = it }
                finish(cred)
            }
            override fun onFailed(message: String) { sending = false; error = tr(message) }
        }
    }
    fun send(resend: Boolean) {
        val a = activity ?: return
        sending = true; error = null
        Chat.sendOtp(a, phone, if (resend) token else null, listener)
    }
    LaunchedEffect(Unit) { if (verId == null) send(false) }
    LaunchedEffect(sending, verId) {
        while (wait > 0 && verId != null) { kotlinx.coroutines.delay(1000); wait-- }
    }

    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 34.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.offset(x = (-10).dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, tr("Back"), tint = Ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(tr("Enter the 6-digit code"), fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp, color = Ink)
        Spacer(Modifier.height(10.dp))
        Text(
            if (sending) tr("Sending SMS to {0}…", phone) else tr("We sent an SMS with a code to {0}", phone),
            fontSize = 15.sp, lineHeight = 22.sp, color = Muted,
        )
        Spacer(Modifier.height(30.dp))
        // 6 boxes (okate hidden text field)
        Box {
            androidx.compose.foundation.text.BasicTextField(
                value = code,
                onValueChange = { v ->
                    val d = v.filter { it.isDigit() }.take(6)
                    code = d; error = null
                    val id = verId
                    if (d.length == 6 && id != null) finish(Chat.otpCredential(id, d))
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
                modifier = Modifier.fillMaxWidth().height(60.dp),
                decorationBox = { inner ->
                    Box {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(6) { i ->
                                val ch = code.getOrNull(i)?.toString() ?: ""
                                val active = i == code.length
                                Box(
                                    Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(14.dp)).background(Surface)
                                        .border(2.dp, if (active) Accent else Line, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center,
                                ) { Text(ch, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink) }
                            }
                        }
                        Box(Modifier.size(1.dp)) { inner() }
                    }
                },
            )
        }
        Spacer(Modifier.height(14.dp))
        error?.let { Text(it, color = Danger, fontSize = 14.sp, lineHeight = 20.sp) }
        Spacer(Modifier.height(10.dp))
        if (!sending) {
            if (wait > 0) Text(tr("Resend code in {0}s", wait), color = Muted, fontSize = 14.sp)
            else Text(tr("Resend code"), color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { code = ""; send(true) }.padding(vertical = 6.dp))
        }
        Spacer(Modifier.weight(1f))
        PillButton(tr("Verify"), enabled = code.length == 6 && verId != null && !busy, busy = busy || sending) {
            verId?.let { finish(Chat.otpCredential(it, code)) }
        }
    }
}

/** Paata users (OTP lekunda login ayinavallu) — okkasari number verify cheyyali */
@Composable
fun VerifyNumberGate(onVerify: (String) -> Unit) {
    val phone = Chat.myPhone
    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 60.dp, bottom = 34.dp),
    ) {
        Text(tr("Verify your number"), fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        Spacer(Modifier.height(12.dp))
        Text(
            tr("Double Bubble now checks every number with an SMS code. This keeps your account safe. Your chats stay as they are."),
            fontSize = 15.sp, lineHeight = 22.sp, color = Muted,
        )
        Spacer(Modifier.height(24.dp))
        Text(phone, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.weight(1f))
        PillButton(tr("Send code"), enabled = phone.isNotEmpty()) { onVerify(phone) }
    }
}
