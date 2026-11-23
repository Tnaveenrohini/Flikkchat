package com.flikk.chat

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.flikk.chat.data.Chat

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Chat.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = Chat.setForeground(true)
            override fun onStop(owner: LifecycleOwner) = Chat.setForeground(false)
        })
    }
}
