package com.khobor.eknojor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

fun bn(n: Int): String =
    n.toString().map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")

fun timeAgo(ms: Long): String {
    if (ms <= 0L) return "এখনো হয়নি"
    val mins = ((System.currentTimeMillis() - ms) / 60000L).toInt()
    return when {
        mins < 1 -> "এইমাত্র"
        mins < 60 -> "${bn(mins)} মিনিট আগে"
        mins < 60 * 24 -> "${bn(mins / 60)} ঘণ্টা আগে"
        else -> "${bn(mins / (60 * 24))} দিন আগে"
    }
}

fun openLink(ctx: Context, url: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(ctx, "লিংক খোলা যায়নি", Toast.LENGTH_SHORT).show()
    }
}

fun shareArticle(ctx: Context, a: Article) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, a.title + "\n" + a.link)
    }
    ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
