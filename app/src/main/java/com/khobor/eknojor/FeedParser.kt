package com.khobor.eknojor

import android.text.Html
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Reads RSS 2.0 / RSS 1.0 / Atom feeds. Only the title, a short plain-text summary, the link and
 * the date are kept. Full article text is never copied.
 */
object FeedParser {

    private const val MAX_SUMMARY = 220

    private val datePatterns = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "dd MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd HH:mm:ss"
    )

    fun parse(bytes: ByteArray, source: Source, now: Long): List<Article> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(bytes, leadingJunk(bytes), bytes.size - leadingJunk(bytes)), null)

        val out = ArrayList<Article>()
        var inItem = false
        var title = ""
        var link = ""
        var desc = ""
        var date = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.name
                if (name == "item" || name == "entry") {
                    inItem = true
                    title = ""; link = ""; desc = ""; date = ""
                } else if (inItem) {
                    when (name) {
                        "title" -> title = readText(parser)
                        "link" -> {
                            val href = parser.getAttributeValue(null, "href")
                            val rel = parser.getAttributeValue(null, "rel")
                            val text = readText(parser)
                            if (href != null) {
                                if (rel == null || rel == "alternate") link = href
                            } else if (text.isNotEmpty()) {
                                link = text
                            }
                        }
                        "description", "summary" -> desc = readText(parser)
                        "pubDate", "published", "dc:date" -> date = readText(parser)
                        "updated" -> {
                            val t = readText(parser)
                            if (date.isEmpty()) date = t
                        }
                        "source" -> readText(parser) // ignore (can contain its own title)
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                val name = parser.name
                if ((name == "item" || name == "entry") && inItem) {
                    inItem = false
                    val cleanTitle = clean(title)
                    val cleanLink = link.trim()
                    if (cleanTitle.isNotEmpty() &&
                        (cleanLink.startsWith("https://") || cleanLink.startsWith("http://"))
                    ) {
                        var published = parseDate(date) ?: now
                        if (published > now) published = now
                        out.add(
                            Article(
                                id = sha1(source.id + "|" + cleanLink),
                                title = cleanTitle,
                                link = cleanLink,
                                summary = summarize(desc, cleanTitle),
                                publishedAt = published,
                                sourceId = source.id,
                                sourceName = source.name,
                                region = source.region,
                                kind = source.kind
                            )
                        )
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    /** Some servers send a BOM or blank lines before the XML header, which the parser rejects. */
    private fun leadingJunk(b: ByteArray): Int {
        var i = 0
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) i = 3
        while (i < b.size && (b[i] == ' '.code.toByte() || b[i] == '\n'.code.toByte() ||
                b[i] == '\r'.code.toByte() || b[i] == '\t'.code.toByte())) i++
        return i
    }

    /** Reads the text of the current element (and skips any nested tags). */
    private fun readText(p: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.TEXT -> if (depth == 1) sb.append(p.text)
                XmlPullParser.END_DOCUMENT -> return sb.toString().trim()
                else -> {}
            }
        }
        return sb.toString().trim()
    }

    @Suppress("DEPRECATION")
    private fun clean(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun summarize(desc: String, title: String): String {
        var s = clean(desc)
        if (s.startsWith(title)) s = s.removePrefix(title).trim()
        if (s.length > MAX_SUMMARY) s = s.substring(0, MAX_SUMMARY).trimEnd() + "…"
        return s
    }

    private fun parseDate(raw: String): Long? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        for (p in datePatterns) {
            try {
                val f = SimpleDateFormat(p, Locale.ENGLISH)
                f.timeZone = TimeZone.getTimeZone("UTC")
                val d = f.parse(t)
                if (d != null) return d.time
            } catch (e: ParseException) {
                // try the next pattern
            }
        }
        return null
    }

    private fun sha1(s: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }.substring(0, 16)
    }
}
