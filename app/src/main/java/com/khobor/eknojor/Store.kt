package com.khobor.eknojor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Everything is stored locally on the phone (files + SharedPreferences). */
object Store {

    const val ARTICLES = "articles.json"
    const val SAVED = "saved.json"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("kn_prefs", Context.MODE_PRIVATE)

    // ---- articles -------------------------------------------------------------------------

    private fun toJson(a: Article) = JSONObject()
        .put("i", a.id).put("t", a.title).put("l", a.link).put("s", a.summary)
        .put("p", a.publishedAt).put("si", a.sourceId).put("sn", a.sourceName)
        .put("r", a.region).put("k", a.kind)

    private fun fromJson(o: JSONObject) = Article(
        id = o.getString("i"),
        title = o.getString("t"),
        link = o.getString("l"),
        summary = o.optString("s"),
        publishedAt = o.getLong("p"),
        sourceId = o.getString("si"),
        sourceName = o.getString("sn"),
        region = o.getString("r"),
        kind = o.getString("k")
    )

    fun readArticles(ctx: Context, name: String): List<Article> {
        val f = File(ctx.filesDir, name)
        if (!f.exists()) return emptyList()
        val out = ArrayList<Article>()
        try {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) out.add(fromJson(arr.getJSONObject(i)))
        } catch (e: Exception) {
            // Corrupt cache: use what was read so far.
        }
        return out
    }

    fun writeArticles(ctx: Context, name: String, list: List<Article>) {
        val arr = JSONArray()
        for (a in list) arr.put(toJson(a))
        val tmp = File(ctx.filesDir, "$name.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(File(ctx.filesDir, name))
    }

    // ---- source status --------------------------------------------------------------------

    fun statuses(ctx: Context): Map<String, SourceStatus> {
        val out = HashMap<String, SourceStatus>()
        try {
            val o = JSONObject(prefs(ctx).getString("status", "{}"))
            for (k in o.keys()) {
                val s = o.getJSONObject(k)
                out[k] = SourceStatus(s.getBoolean("ok"), s.getLong("t"), s.getInt("n"), s.optString("e"))
            }
        } catch (e: Exception) {
            // ignore corrupt data
        }
        return out
    }

    fun saveStatuses(ctx: Context, map: Map<String, SourceStatus>) {
        val o = JSONObject()
        for ((k, v) in map) {
            o.put(k, JSONObject().put("ok", v.ok).put("t", v.time).put("n", v.count).put("e", v.error))
        }
        prefs(ctx).edit().putString("status", o.toString()).apply()
    }

    // ---- settings -------------------------------------------------------------------------

    fun notifyEnabled(ctx: Context) = prefs(ctx).getBoolean("notify", false)
    fun setNotifyEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("notify", v).apply()

    fun intervalMinutes(ctx: Context) = prefs(ctx).getInt("interval", 60)
    fun setIntervalMinutes(ctx: Context, v: Int) = prefs(ctx).edit().putInt("interval", v).apply()

    fun lastRefresh(ctx: Context) = prefs(ctx).getLong("last_refresh", 0L)
    fun setLastRefresh(ctx: Context, v: Long) = prefs(ctx).edit().putLong("last_refresh", v).apply()

    // ---- stories we already notified about -----------------------------------------------

    fun notified(ctx: Context): Set<String> {
        val out = HashSet<String>()
        try {
            val arr = JSONArray(prefs(ctx).getString("notified", "[]"))
            for (i in 0 until arr.length()) out.add(arr.getString(i))
        } catch (e: Exception) {
            // ignore
        }
        return out
    }

    fun markNotified(ctx: Context, ids: Collection<String>) {
        val all = ArrayList<String>(notified(ctx))
        all.addAll(ids)
        val keep = all.takeLast(300)
        val arr = JSONArray()
        for (s in keep) arr.put(s)
        prefs(ctx).edit().putString("notified", arr.toString()).apply()
    }
}
