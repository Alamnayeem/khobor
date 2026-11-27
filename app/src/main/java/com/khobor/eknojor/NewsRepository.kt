package com.khobor.eknojor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads every feed in assets/sources.json, merges the results and keeps 3 days of news. */
object NewsRepository {

    private const val UA = "Mozilla/5.0 (Linux; Android 13) KhoborEkNojor/1.0"
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val KEEP_MS = 3L * 24 * 60 * 60 * 1000
    private const val MAX_ARTICLES = 1500

    private val mutex = Mutex()

    fun loadSources(ctx: Context): List<Source> {
        val out = ArrayList<Source>()
        try {
            val text = ctx.assets.open("sources.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Source(
                        o.getString("id"), o.getString("name"), o.getString("region"),
                        o.optString("kind", "news"), o.getString("url")
                    )
                )
            }
        } catch (e: Exception) {
            // An unreadable sources file means no sources; the Settings screen will show it.
        }
        return out
    }

    /** Fetches all sources, updates the cache and returns the merged article list. */
    suspend fun refresh(ctx: Context): List<Article> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = ctx.applicationContext
            val sources = loadSources(app)
            val now = System.currentTimeMillis()
            val gate = Semaphore(6)

            val results = coroutineScope {
                sources.map { s ->
                    async {
                        gate.withPermit { fetchOne(s, now) }
                    }
                }.awaitAll()
            }

            val statuses = HashMap<String, SourceStatus>()
            val fresh = ArrayList<Article>()
            for ((s, r) in results) {
                statuses[s.id] = r.first
                fresh.addAll(r.second)
            }

            val merged = LinkedHashMap<String, Article>()
            for (a in Store.readArticles(app, Store.ARTICLES)) merged[a.id] = a
            for (a in fresh) merged[a.id] = a
            val result = merged.values
                .filter { now - it.publishedAt <= KEEP_MS }
                .sortedByDescending { it.publishedAt }
                .take(MAX_ARTICLES)

            Store.writeArticles(app, Store.ARTICLES, result)
            Store.saveStatuses(app, statuses)
            Store.setLastRefresh(app, now)
            result
        }
    }

    private fun fetchOne(s: Source, now: Long): Pair<Source, Pair<SourceStatus, List<Article>>> {
        return try {
            val bytes = download(s.url)
            val items = FeedParser.parse(bytes, s, now)
            if (items.isEmpty()) throw IOException("কোনো খবর পাওয়া যায়নি")
            Pair(s, Pair(SourceStatus(true, now, items.size, ""), items))
        } catch (e: Exception) {
            Pair(s, Pair(SourceStatus(false, now, 0, e.message ?: e.javaClass.simpleName), emptyList()))
        }
    }

    private fun secure(url: String): String =
        if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url

    private fun download(startUrl: String): ByteArray {
        var url = secure(startUrl)
        var redirects = 0
        while (true) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", UA)
                conn.setRequestProperty(
                    "Accept",
                    "application/rss+xml, application/atom+xml, application/xml, text/xml, */*"
                )
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: throw IOException("রিডাইরেক্ট ঠিকানা নেই")
                    redirects++
                    if (redirects > 5) throw IOException("অনেক রিডাইরেক্ট")
                    url = secure(URL(URL(url), loc).toString())
                } else if (code != 200) {
                    throw IOException("HTTP $code")
                } else {
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    conn.inputStream.use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > MAX_BYTES) throw IOException("ফিড অনেক বড়")
                        }
                    }
                    return out.toByteArray()
                }
            } finally {
                conn.disconnect()
            }
        }
    }
}
