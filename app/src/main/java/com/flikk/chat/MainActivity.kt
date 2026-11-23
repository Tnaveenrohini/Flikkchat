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
import com.flikk.chat.ui.HomeScreen
import com.flikk.chat.ui.LoginScreen
import com.flikk.chat.ui.ProfileScreen
import com.flikk.chat.ui.SetupScreen

sealed class Screen {
    data object Setup : Screen()
    data object Login : Screen()
    data object Home : Screen()
    data object Contacts : Screen()
    data object Profile : Screen()
    data class Conversation(val peer: String) : Screen()
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
                    )
                    Screen.Contacts -> ContactsScreen(
                        onBack = ::pop,
                        openChat = { stack = stack.dropLast(1) + Screen.Conversation(it) },
                    )
                    Screen.Profile -> ProfileScreen(onBack = ::pop)
                    is Screen.Conversation -> ChatScreen(peer = current.peer, onBack = ::pop)
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
    private fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
}
