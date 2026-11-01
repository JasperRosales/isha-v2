package dev.jasper.ishav2.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** Keyless web search (DuckDuckGo lite, Bing fallback) and readable page text. */
object WebTools {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) isha/0.1 Mobile Safari/537.36"

    private data class Result(val title: String, val url: String, val snippet: String)

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        if (query.isBlank()) throw ApiException(400, "Search query is empty.")
        val results = runCatching { searchDuckDuckGo(query) }.getOrElse { emptyList() }
            .ifEmpty { runCatching { searchBing(query) }.getOrElse { emptyList() } }
        buildJsonObject {
            put("query", query)
            put("results", buildJsonArray {
                for (result in results.take(8)) {
                    add(buildJsonObject {
                        put("title", result.title)
                        put("url", result.url)
                        put("snippet", result.snippet)
                    })
                }
            })
            if (results.isEmpty()) put("note", "No results. Try a shorter or different query.")
        }.toString()
    }

    suspend fun read(url: String, maxChars: Int = 16000): String = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull()
            ?: throw ApiException(400, "That is not a valid http(s) URL.")
        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") {
            throw ApiException(400, "Only http(s) URLs can be read.")
        }
        if (isPrivateHost(httpUrl.host)) {
            throw ApiException(400, "Refusing to read a private or local address.")
        }
        val request = Request.Builder()
            .url(httpUrl)
            .header("user-agent", USER_AGENT)
            .header("accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
            .build()
        val call = Http.client.newCall(request)
        call.timeout().timeout(30, TimeUnit.SECONDS)
        val response = call.awaitResponse()
        response.use {
            if (!it.isSuccessful) {
                throw ApiException(it.code, "Could not read $url (HTTP ${it.code}).")
            }
            val body = it.body ?: throw ApiException(502, "The page returned no content.")
            val text = body.byteStream().use { stream -> readLimited(stream, 2_500_000) }
            val document = Jsoup.parse(text, httpUrl.toString())
            buildJsonObject {
                put("url", httpUrl.toString())
                put("title", document.title().take(200))
                put("text", readableText(document).take(maxChars))
            }.toString()
        }
    }

    private suspend fun searchDuckDuckGo(query: String): List<Result> {
        val url = "https://html.duckduckgo.com/html/".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("q", query)
            .build()
        val document = fetchDocument(url.toString())
        return document.select("div.result").mapNotNull { element ->
            val link = element.selectFirst("a.result__a") ?: return@mapNotNull null
            val title = link.text().trim()
            val href = unwrapDuckDuckGo(link.attr("href"))
            val snippet = element.selectFirst(".result__snippet")?.text()?.trim().orEmpty()
            if (title.isBlank() || href.isBlank()) null else Result(title, href, snippet)
        }
    }

    private suspend fun searchBing(query: String): List<Result> {
        val url = "https://www.bing.com/search".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("q", query)
            .build()
        val document = fetchDocument(url.toString())
        return document.select("li.b_algo").mapNotNull { element ->
            val link = element.selectFirst("h2 a") ?: return@mapNotNull null
            val title = link.text().trim()
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            val snippet = (element.selectFirst(".b_caption p") ?: element.selectFirst("p"))
                ?.text()?.trim().orEmpty()
            if (title.isBlank() || href.isBlank()) null else Result(title, href, snippet)
        }
    }

    private suspend fun fetchDocument(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .header("user-agent", USER_AGENT)
            .header("accept-language", "en-US,en;q=0.9")
            .build()
        val call = Http.client.newCall(request)
        call.timeout().timeout(25, TimeUnit.SECONDS)
        val response = call.awaitResponse()
        response.use {
            if (!it.isSuccessful) throw ApiException(it.code, "Search failed (HTTP ${it.code}).")
            val text = it.body?.byteStream()?.use { stream -> readLimited(stream, 2_500_000) }
                ?: throw ApiException(502, "Search returned no content.")
            return Jsoup.parse(text, url)
        }
    }

    private fun readableText(document: Document): String {
        document.select("script, style, noscript, svg, header, footer, nav, form, aside, iframe")
            .remove()
        val root = document.selectFirst("article")
            ?: document.selectFirst("main")
            ?: document.body()
        val lines = root.text()
            .replace('\u00a0', ' ')
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return lines.joinToString("\n")
    }

    private fun unwrapDuckDuckGo(href: String): String {
        if (!href.contains("uddg=")) return href
        val encoded = href.substringAfter("uddg=").substringBefore('&')
        return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(href)
    }

    private fun readLimited(stream: java.io.InputStream, limit: Int): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0
        while (total < limit) {
            val read = stream.read(buffer)
            if (read < 0) break
            val keep = minOf(read, limit - total)
            out.write(buffer, 0, keep)
            total += keep
        }
        return out.toString("UTF-8")
    }

    private fun isPrivateHost(host: String): Boolean {
        val value = host.lowercase()
        if (value == "localhost" || value.endsWith(".local") || value.endsWith(".internal")) return true
        if (value == "::1" || value == "[::1]") return true
        val parts = value.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() != null }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            if (a == 10 || a == 127 || a == 0) return true
            if (a == 192 && b == 168) return true
            if (a == 172 && b in 16..31) return true
            if (a == 169 && b == 254) return true
        }
        return false
    }
}
