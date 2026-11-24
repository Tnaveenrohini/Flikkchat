package com.flikk.chat.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App language. English default; Telugu, Tamil, Hindi translations [Strings.kt] lo.
 * Key = English text. Translation lekapothe English ne chupistundi.
 */
var appLang by mutableStateOf("en")

val Languages = listOf(
    "en" to "English",
    "te" to "తెలుగు (Telugu)",
    "ta" to "தமிழ் (Tamil)",
    "hi" to "हिन्दी (Hindi)",
)

fun tr(s: String): String {
    if (appLang == "en") return s
    return Translations[appLang]?.get(s) ?: s
}

/** {0}, {1} placeholders */
fun tr(s: String, vararg args: Any): String {
    var r = tr(s)
    args.forEachIndexed { i, a -> r = r.replace("{$i}", a.toString()) }
    return r
}
