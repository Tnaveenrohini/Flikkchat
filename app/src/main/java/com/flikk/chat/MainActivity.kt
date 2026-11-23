package com.flikk.chat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flikk.chat.data.Chat
import com.flikk.chat.ui.ChatScreen
import com.flikk.chat.ui.ContactsScreen
import com.flikk.chat.ui.FlikkTheme
import com.flikk.chat.ui.ForwardScreen
import com.flikk.chat.ui.GroupInfoScreen
import com.flikk.chat.ui.HomeScreen
import com.flikk.chat.ui.ImageViewer
import com.flikk.chat.ui.LoginScreen
import com.flikk.chat.ui.NewGroupScreen
import com.flikk.chat.ui.ProfileScreen
import com.flikk.chat.ui.SetupScreen
import com.flikk.chat.ui.StarredScreen

sealed class Screen {
    data object Setup : Screen()
    data object Login : Screen()
    data object Home : Screen()
    data object Contacts : Screen()
    data object Profile : Screen()
    data object NewGroup : Screen()
    data object Starred : Screen()
    data class Conversation(val peer: String) : Screen()
    data class GroupInfo(val gid: String) : Screen()
    data class Forward(val msgId: String) : Screen()
    data class Viewer(val path: String) : Screen()
}

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PEER = "peer"
    }

    private var stack by mutableStateOf(listOf<Screen>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        stack = listOf(
            when {
                !Chat.ready -> Screen.Setup
                !Chat.loggedIn -> Screen.Login
                else -> Screen.Home
            }
        )
        handleIntent(intent)

        setContent {
            FlikkTheme {
                val current = stack.last()
                BackHandler(enabled = stack.size > 1) { pop() }
                when (current) {
                    Screen.Setup -> SetupScreen()
                    Screen.Login -> LoginScreen(onDone = { stack = listOf(Screen.Home) })
                    Screen.Home -> HomeScreen(
                        openChat = { push(Screen.Conversation(it)) },
                        openContacts = { push(Screen.Contacts) },
                        openProfile = { push(Screen.Profile) },
                        openNewGroup = { push(Screen.NewGroup) },
                        openStarred = { push(Screen.Starred) },
                    )
                    Screen.Contacts -> ContactsScreen(
                        onBack = ::pop,
                        openChat = { replaceTop(Screen.Conversation(it)) },
                        openNewGroup = { replaceTop(Screen.NewGroup) },
                    )
                    Screen.Profile -> ProfileScreen(onBack = ::pop)
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
                    )
                    is Screen.GroupInfo -> GroupInfoScreen(gid = current.gid, onBack = ::pop)
                    is Screen.Forward -> ForwardScreen(msgId = current.msgId, onDone = ::pop)
                    is Screen.Viewer -> ImageViewer(path = current.path, onBack = ::pop)
                }
            }
        }
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

    private fun push(s: Screen) { stack = stack + s }
    private fun replaceTop(s: Screen) { stack = stack.dropLast(1) + s }
    private fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
}
