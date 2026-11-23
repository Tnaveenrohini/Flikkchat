package com.flikk.chat

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.flikk.chat.data.Calls
import com.flikk.chat.data.Chat
import com.flikk.chat.ui.BlockedScreen
import com.flikk.chat.ui.CallPickerScreen
import com.flikk.chat.ui.CallScreen
import com.flikk.chat.ui.ChatScreen
import com.flikk.chat.ui.ChatsSettingsScreen
import com.flikk.chat.ui.ContactInfoScreen
import com.flikk.chat.ui.ContactsScreen
import com.flikk.chat.ui.EditProfileScreen
import com.flikk.chat.ui.FlikkTheme
import com.flikk.chat.ui.ForwardScreen
import com.flikk.chat.ui.GroupInfoScreen
import com.flikk.chat.ui.HomeScreen
import com.flikk.chat.ui.ImageViewer
import com.flikk.chat.ui.LockScreen
import com.flikk.chat.ui.NewGroupScreen
import com.flikk.chat.ui.NotificationsScreen
import com.flikk.chat.ui.PhoneLoginScreen
import com.flikk.chat.ui.PrivacyScreen
import com.flikk.chat.ui.ProfileSetupScreen
import com.flikk.chat.ui.SettingsScreen
import com.flikk.chat.ui.SetupScreen
import com.flikk.chat.ui.StarredScreen
import com.flikk.chat.ui.StatusViewer
import com.flikk.chat.ui.StorageScreen
import com.flikk.chat.ui.TextStatusScreen
import com.flikk.chat.ui.WelcomeScreen
import com.flikk.chat.ui.applyTheme
import com.flikk.chat.ui.palette

sealed class Screen {
    data object Setup : Screen()
    data object Welcome : Screen()
    data object Login : Screen()
    data class ProfileSetup(val phone: String) : Screen()
    data object Home : Screen()
    data object Contacts : Screen()
    data object Profile : Screen()
    data object NewGroup : Screen()
    data object Starred : Screen()
    data object Settings : Screen()
    data object Privacy : Screen()
    data object Blocked : Screen()
    data object Notifications : Screen()
    data object Storage : Screen()
    data object ChatsSettings : Screen()
    data class Conversation(val peer: String) : Screen()
    data class ContactInfo(val peer: String) : Screen()
    data class GroupInfo(val gid: String) : Screen()
    data class Forward(val msgId: String) : Screen()
    data class Viewer(val path: String) : Screen()
    data object StatusText : Screen()
    data class StatusView(val owner: String) : Screen()
    data class CallPick(val video: Boolean) : Screen()
}

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_PEER = "peer"
        private const val LOCK_AFTER_MS = 60_000L
    }

    private var stack by mutableStateOf(listOf<Screen>())
    private var locked by mutableStateOf(false)
    private var firstStart = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(this)
        applyBars()
        stack = listOf(
            when {
                !Chat.ready -> Screen.Setup
                !Chat.loggedIn -> Screen.Welcome
                else -> Screen.Home
            }
        )
        handleIntent(intent)

        setContent {
            FlikkTheme {
                LaunchedEffect(palette.dark) { applyBars() }
                val call by Calls.state.collectAsState()
                LaunchedEffect(call != null) { showOverLock(call != null) }
                val active = call
                if (active != null) {
                    CallScreen(active)
                    return@FlikkTheme
                }
                if (locked) {
                    LockScreen(onUnlock = ::unlock)
                    return@FlikkTheme
                }
                val current = stack.last()
                BackHandler(enabled = stack.size > 1) { pop() }
                when (current) {
                    Screen.Setup -> SetupScreen()
                    Screen.Welcome -> WelcomeScreen(onStart = { push(Screen.Login) })
                    Screen.Login -> PhoneLoginScreen(onBack = ::pop, onNext = { push(Screen.ProfileSetup(it)) })
                    is Screen.ProfileSetup -> ProfileSetupScreen(current.phone, onBack = ::pop, onDone = { stack = listOf(Screen.Home) })
                    Screen.Home -> HomeScreen(
                        openChat = { push(Screen.Conversation(it)) },
                        openContacts = { push(Screen.Contacts) },
                        openProfile = { push(Screen.Settings) },
                        openNewGroup = { push(Screen.NewGroup) },
                        openStarred = { push(Screen.Starred) },
                        openStatusText = { push(Screen.StatusText) },
                        openStatusViewer = { push(Screen.StatusView(it)) },
                        openCallPicker = { push(Screen.CallPick(it)) },
                    )
                    Screen.Contacts -> ContactsScreen(
                        onBack = ::pop,
                        openChat = { replaceTop(Screen.Conversation(it)) },
                        openNewGroup = { replaceTop(Screen.NewGroup) },
                    )
                    Screen.Profile -> EditProfileScreen(onBack = ::pop)
                    Screen.Settings -> SettingsScreen(
                        onBack = ::pop,
                        openProfile = { push(Screen.Profile) },
                        openPrivacy = { push(Screen.Privacy) },
                        openChats = { push(Screen.ChatsSettings) },
                        openNotifications = { push(Screen.Notifications) },
                        openStorage = { push(Screen.Storage) },
                        openStarred = { push(Screen.Starred) },
                    )
                    Screen.Privacy -> PrivacyScreen(onBack = ::pop, openBlocked = { push(Screen.Blocked) })
                    Screen.Blocked -> BlockedScreen(onBack = ::pop)
                    Screen.Notifications -> NotificationsScreen(onBack = ::pop)
                    Screen.Storage -> StorageScreen(onBack = ::pop)
                    Screen.ChatsSettings -> ChatsSettingsScreen(onBack = ::pop, openStarred = { push(Screen.Starred) })
                    Screen.NewGroup -> NewGroupScreen(
                        onBack = ::pop,
                        onCreated = { replaceTop(Screen.Conversation(it)) },
                    )
                    Screen.Starred -> StarredScreen(
                        onBack = ::pop,
                        openChat = { push(Screen.Conversation(it)) },
                    )
                    is Screen.Conversation -> ChatScreen(
                        peer = current.peer,
                        onBack = ::pop,
                        openGroupInfo = { push(Screen.GroupInfo(it)) },
                        openForward = { push(Screen.Forward(it)) },
                        openImage = { push(Screen.Viewer(it)) },
                        openContactInfo = { push(Screen.ContactInfo(it)) },
                    )
                    is Screen.ContactInfo -> ContactInfoScreen(current.peer, onBack = ::pop, openImage = { push(Screen.Viewer(it)) })
                    is Screen.GroupInfo -> GroupInfoScreen(gid = current.gid, onBack = ::pop)
                    is Screen.Forward -> ForwardScreen(msgId = current.msgId, onDone = ::pop)
                    is Screen.Viewer -> ImageViewer(path = current.path, onBack = ::pop)
                    Screen.StatusText -> TextStatusScreen(onDone = ::pop)
                    is Screen.StatusView -> StatusViewer(owner = current.owner, onDone = ::pop)
                    is Screen.CallPick -> CallPickerScreen(video = current.video, onBack = ::pop)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // App lock: modatisari open chesinappudu / 1 nimisham kanna ekkuva background lo unte
        if (Chat.loggedIn && Chat.flag("app_lock", false)) {
            val away = Chat.backgroundAt > 0 && System.currentTimeMillis() - Chat.backgroundAt > LOCK_AFTER_MS
            if (firstStart || away) {
                locked = true
                unlock()
            }
        }
        firstStart = false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTheme(this)
    }

    private fun unlock() {
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            // Phone lo screen lock ledu — lock cheyyalem
            locked = false
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked = false }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Flikkchat unlock")
            .setSubtitle("Fingerprint / screen lock")
            .setAllowedAuthenticators(auth)
            .build()
        prompt.authenticate(info)
    }

    private fun applyBars() {
        val t = android.graphics.Color.TRANSPARENT
        val style = if (palette.dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Notification click → aa chat open */
    private fun handleIntent(intent: Intent?) {
        val peer = intent?.getStringExtra(EXTRA_PEER) ?: return
        if (Chat.loggedIn) stack = listOf(Screen.Home, Screen.Conversation(peer))
    }

    /** Incoming call — lock screen meeda kuda chupinchu */
    private fun showOverLock(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(on)
            setTurnScreenOn(on)
        } else {
            @Suppress("DEPRECATION")
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun push(s: Screen) { stack = stack + s }
    private fun replaceTop(s: Screen) { stack = stack.dropLast(1) + s }
    private fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
}
