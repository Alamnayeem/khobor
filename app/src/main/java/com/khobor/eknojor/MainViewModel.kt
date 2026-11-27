package com.khobor.eknojor

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app

    val sources: List<Source> = NewsRepository.loadSources(app)

    var bdStories by mutableStateOf<List<Story>>(emptyList()); private set
    var worldStories by mutableStateOf<List<Story>>(emptyList()); private set
    var factChecks by mutableStateOf<List<Article>>(emptyList()); private set
    var saved by mutableStateOf<List<Article>>(emptyList()); private set
    var savedIds by mutableStateOf<Set<String>>(emptySet()); private set
    var statuses by mutableStateOf<Map<String, SourceStatus>>(emptyMap()); private set
    var refreshing by mutableStateOf(false); private set
    var lastRefresh by mutableStateOf(Store.lastRefresh(app)); private set
    var notifyEnabled by mutableStateOf(Store.notifyEnabled(app)); private set
    var intervalMinutes by mutableStateOf(Store.intervalMinutes(app)); private set
    var loaded by mutableStateOf(false); private set

    var sortByCoverage by mutableStateOf(false)

    init {
        statuses = Store.statuses(app)
        Scheduler.schedule(app, intervalMinutes, false)
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { Store.readArticles(ctx, Store.ARTICLES) }
            val savedList = withContext(Dispatchers.IO) { Store.readArticles(ctx, Store.SAVED) }
            saved = savedList
            savedIds = savedList.map { it.id }.toSet()
            rebuild(cached)
            loaded = true
            refreshIfStale()
        }
    }

    private suspend fun rebuild(articles: List<Article>) {
        val result = withContext(Dispatchers.Default) {
            val stories = Clusterer.cluster(articles.filter { it.kind == "news" })
            val bd = stories.filter { it.region == "bd" }.take(150)
            val world = stories.filter { it.region == "world" }.take(150)
            val fc = articles.filter { it.kind == "factcheck" }
                .sortedByDescending { it.publishedAt }.take(100)
            Triple(bd, world, fc)
        }
        bdStories = result.first
        worldStories = result.second
        factChecks = result.third
    }

    /** Refreshes when the data is older than 10 minutes (called when the app is opened). */
    fun refreshIfStale() {
        if (!loaded) return
        val age = System.currentTimeMillis() - lastRefresh
        if (age > 10L * 60 * 1000) refresh()
    }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            try {
                val all = NewsRepository.refresh(ctx)
                withContext(Dispatchers.Default) { Notifier.markSeen(ctx, all) }
                rebuild(all)
            } catch (e: Exception) {
                // Offline or all sources failed: keep showing the cached news.
            }
            statuses = Store.statuses(ctx)
            lastRefresh = Store.lastRefresh(ctx)
            refreshing = false
        }
    }

    fun toggleSaved(a: Article) {
        val list = if (a.id in savedIds) saved.filter { it.id != a.id } else listOf(a) + saved
        saved = list
        savedIds = list.map { it.id }.toSet()
        viewModelScope.launch(Dispatchers.IO) { Store.writeArticles(ctx, Store.SAVED, list) }
    }

    fun setNotify(on: Boolean) {
        Store.setNotifyEnabled(ctx, on)
        notifyEnabled = on
    }

    fun setInterval(minutes: Int) {
        Store.setIntervalMinutes(ctx, minutes)
        intervalMinutes = minutes
        Scheduler.schedule(ctx, minutes, true)
    }
}
