package com.khobor.eknojor

/** region: "bd", "world" or "both". kind: "news" or "factcheck". */
data class Source(
    val id: String,
    val name: String,
    val region: String,
    val kind: String,
    val url: String
)

data class Article(
    val id: String,
    val title: String,
    val link: String,
    val summary: String,
    val publishedAt: Long,
    val sourceId: String,
    val sourceName: String,
    val region: String,
    val kind: String
)

/** How widely a story is covered. This measures coverage, NOT proof of truth. */
enum class Tier { CONFIRMED, TWO, SINGLE }

data class Story(
    val id: String,
    val region: String,
    val articles: List<Article>,
    val sourceCount: Int,
    val tier: Tier,
    val latest: Long
) {
    val headline: Article get() = articles.first()
}

data class SourceStatus(val ok: Boolean, val time: Long, val count: Int, val error: String)
