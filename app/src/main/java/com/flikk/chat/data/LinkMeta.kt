package com.flikk.chat.data

import android.content.Context
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Link preview: page lo og:title / og:image teesukoni cache lo pettu. */
data class LinkInfo(val url: String, val title: String, val desc: String, val image: String, val host: String)

object LinkMeta {
    private val URL_RE = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val META_RE = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE)
    private val ATTR_RE = Regex("""([a-zA-Z:_-]+)\s*=\s*("([^"]*)"|'([^']*)')""")
    private val TITLE_RE = Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE)
    private val mem = ConcurrentHashMap<String, LinkInfo>()
    private val failed = ConcurrentHashMap<String, Long>()

    fun firstUrl(text: String): String? = URL_RE.find(text)?.value?.trimEnd('.', ',', ')', '!', '?')

    private fun cacheFile(ctx: Context, url: String) = File(File(ctx.cacheDir, "links").apply { mkdirs() }, url.hashCode().toString() + ".json")

    private fun unescape(s: String) = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    fun cached(url: String): LinkInfo? = mem[url]

    suspend fun get(ctx: Context, url: String): LinkInfo? = withContext(Dispatchers.IO) {
        mem[url]?.let { return@withContext it }
        if ((failed[url] ?: 0L) > System.currentTimeMillis() - 10 * 60_000L) return@withContext null
        val cf = cacheFile(ctx, url)
        if (cf.exists()) try {
            val j = JSONObject(cf.readText())
            val li = LinkInfo(url, j.optString("t"), j.optString("d"), j.optString("i"), j.optString("h"))
            mem[url] = li
            return@withContext li
        } catch (_: Exception) {}
        try {
            var c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
            c.setRequestProperty("Accept-Language", "en")
            // http → https redirects manually
            var hops = 0
            while (c.responseCode in 300..399 && hops < 5) {
                val loc = c.getHeaderField("Location") ?: break
                c.disconnect()
                c = URL(URL(url), loc).openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                hops++
            }
            val type = c.contentType ?: ""
            if (!type.contains("html", ignoreCase = true)) { c.disconnect(); failed[url] = System.currentTimeMillis(); return@withContext null }
            val html = c.inputStream.use { ins ->
                val buf = ByteArray(400_000)
                var n = 0
                while (n < buf.size) { val r = ins.read(buf, n, buf.size - n); if (r <= 0) break; n += r }
                String(buf, 0, n, Charsets.UTF_8)
            }
            val host = c.url.host.removePrefix("www.").removePrefix("m.")
            c.disconnect()
            val props = HashMap<String, String>()
            for (m in META_RE.findAll(html)) {
                val attrs = HashMap<String, String>()
                for (a in ATTR_RE.findAll(m.value)) attrs[a.groupValues[1].lowercase()] = a.groupValues[3].ifEmpty { a.groupValues[4] }
                val key = (attrs["property"] ?: attrs["name"] ?: continue).lowercase()
                val v = attrs["content"] ?: continue
                if (key !in props) props[key] = v
            }
            val title = unescape(props["og:title"] ?: props["twitter:title"] ?: TITLE_RE.find(html)?.groupValues?.get(1) ?: "")
            val desc = unescape(props["og:description"] ?: props["description"] ?: "")
            var img = props["og:image"] ?: props["twitter:image"] ?: ""
            if (img.isNotEmpty()) img = try { URL(URL(url), unescape(img)).toString() } catch (_: Exception) { "" }
            if (title.isBlank()) { failed[url] = System.currentTimeMillis(); return@withContext null }
            val li = LinkInfo(url, title.take(200), desc.take(300), img, host)
            mem[url] = li
            try { cf.writeText(JSONObject().put("t", li.title).put("d", li.desc).put("i", li.image).put("h", li.host).toString()) } catch (_: Exception) {}
            li
        } catch (_: Exception) {
            failed[url] = System.currentTimeMillis()
            null
        }
    }
}
