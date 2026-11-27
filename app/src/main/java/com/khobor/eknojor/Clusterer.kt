package com.khobor.eknojor

import java.util.Locale
import kotlin.math.abs

/**
 * Groups articles about the same event into one Story, by comparing the words of their headlines.
 * This is a similarity heuristic, so it can occasionally group or split wrongly.
 */
object Clusterer {

    private const val WINDOW_MS = 36L * 60 * 60 * 1000
    private const val MIN_SHARED = 3
    private const val THRESHOLD = 0.45

    private val splitter = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    private val stop = hashSetOf(
        "the", "of", "in", "to", "and", "for", "on", "at", "is", "as", "by", "with", "from",
        "ও", "এবং", "এর", "এই", "কে", "থেকে", "করে", "না", "হবে", "জন্য", "যে", "তার", "আর"
    )

    private class Acc(first: Article, firstTokens: Set<String>) {
        val members = ArrayList<Article>().apply { add(first) }
        val tokenSets = ArrayList<Set<String>>().apply { add(firstTokens) }
    }

    fun tokens(title: String): Set<String> =
        title.lowercase(Locale.ROOT).split(splitter)
            .filter { it.length >= 2 && it !in stop }
            .toSet()

    private fun score(a: Set<String>, b: Set<String>): Double {
        var inter = 0
        for (t in a) if (t in b) inter++
        if (inter < MIN_SHARED) return 0.0
        return inter.toDouble() / (a.size + b.size - inter).toDouble()
    }

    /** Returns stories sorted by newest first. */
    fun cluster(articles: List<Article>): List<Story> {
        val sorted = articles.sortedByDescending { it.publishedAt }
        val accs = ArrayList<Acc>()

        for (a in sorted) {
            val t = tokens(a.title)
            var best: Acc? = null
            var bestScore = 0.0
            if (t.size >= MIN_SHARED) {
                for (acc in accs) {
                    if (abs(acc.members[0].publishedAt - a.publishedAt) > WINDOW_MS) continue
                    for (ts in acc.tokenSets) {
                        val s = score(t, ts)
                        if (s > bestScore) {
                            bestScore = s
                            best = acc
                        }
                    }
                }
            }
            if (best != null && bestScore >= THRESHOLD) {
                best.members.add(a)
                best.tokenSets.add(t)
            } else {
                accs.add(Acc(a, t))
            }
        }

        return accs.map { acc ->
            val sources = acc.members.map { it.sourceId }.toSet().size
            val tier = when {
                sources >= 3 -> Tier.CONFIRMED
                sources == 2 -> Tier.TWO
                else -> Tier.SINGLE
            }
            val region = if (acc.members.any { it.region == "bd" }) "bd" else "world"
            Story(
                id = acc.members.last().id,
                region = region,
                articles = acc.members,
                sourceCount = sources,
                tier = tier,
                latest = acc.members.first().publishedAt
            )
        }
    }
}
