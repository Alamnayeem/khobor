package com.khobor.eknojor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** "Big news": a story covered by at least 4 different sources within the last 3 hours. */
object Notifier {

    private const val CHANNEL = "breaking"
    private const val MIN_SOURCES = 4
    private const val WINDOW_MS = 3L * 60 * 60 * 1000

    private fun hotStories(articles: List<Article>): List<Story> {
        val now = System.currentTimeMillis()
        return Clusterer.cluster(articles.filter { it.kind == "news" })
            .filter { it.sourceCount >= MIN_SOURCES && now - it.latest <= WINDOW_MS }
    }

    /** Called when the user is already looking at the app: remember, but do not notify. */
    fun markSeen(ctx: Context, articles: List<Article>) {
        Store.markNotified(ctx, hotStories(articles).map { it.id })
    }

    fun checkBreaking(ctx: Context, articles: List<Article>) {
        if (!Store.notifyEnabled(ctx)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val seen = Store.notified(ctx)
        val fresh = hotStories(articles).filter { it.id !in seen }.take(3)
        if (fresh.isEmpty()) return

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "বড় খবর", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        for (s in fresh) {
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_news)
                .setContentTitle("বড় খবর • ${bn(s.sourceCount)}টি সূত্রে")
                .setContentText(s.headline.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(s.headline.title))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(s.id.hashCode(), n)
        }
        Store.markNotified(ctx, fresh.map { it.id })
    }
}
