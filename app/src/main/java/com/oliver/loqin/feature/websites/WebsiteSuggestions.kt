/*
 * Loq In
 * Copyright (C) 2026 Loq In Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.oliver.loqin.feature.websites

/** Coarse buckets for the website suggestion filter chips, mirroring the app picker categories. */
enum class WebsiteSuggestionCategory {
    ALL,
    SOCIAL,
    VIDEO,
    NEWS,
    SHOPPING,
    GAMING,
}

/**
 * One curated suggestion. [quickPaths] are optional one-tap path rules offered alongside the
 * host, e.g. "youtube.com/shorts/..." or "youtube.com/reels/...".
 */
data class WebsiteSuggestion(
    val host: String,
    val category: WebsiteSuggestionCategory,
    val quickPaths: List<String> = emptyList(),
)

/**
 * Curated suggestion catalog shown below the user's saved rules. Hosts are normalized the same
 * way user-entered rules are (see DomainBlockStore.normalize).
 */
object WebsiteSuggestions {

    val all: List<WebsiteSuggestion> = listOf(
        WebsiteSuggestion(
            host = "youtube.com",
            category = WebsiteSuggestionCategory.VIDEO,
            quickPaths = listOf("youtube.com/shorts/*", "youtube.com/reels/*"),
        ),
        WebsiteSuggestion("tiktok.com", WebsiteSuggestionCategory.VIDEO),
        WebsiteSuggestion("twitch.tv", WebsiteSuggestionCategory.VIDEO),
        WebsiteSuggestion("netflix.com", WebsiteSuggestionCategory.VIDEO),
        WebsiteSuggestion(
            host = "instagram.com",
            category = WebsiteSuggestionCategory.SOCIAL,
            quickPaths = listOf("instagram.com/reels/*"),
        ),
        WebsiteSuggestion(
            host = "facebook.com",
            category = WebsiteSuggestionCategory.SOCIAL,
            quickPaths = listOf("facebook.com/reels/*", "facebook.com/watch/*"),
        ),
        WebsiteSuggestion("x.com", WebsiteSuggestionCategory.SOCIAL),
        WebsiteSuggestion("twitter.com", WebsiteSuggestionCategory.SOCIAL),
        WebsiteSuggestion("reddit.com", WebsiteSuggestionCategory.SOCIAL),
        WebsiteSuggestion("tumblr.com", WebsiteSuggestionCategory.SOCIAL),
        WebsiteSuggestion("pinterest.com", WebsiteSuggestionCategory.SOCIAL),
        WebsiteSuggestion("bsky.app", WebsiteSuggestionCategory.SOCIAL),
        WebsiteSuggestion("news.ycombinator.com", WebsiteSuggestionCategory.NEWS),
        WebsiteSuggestion("cnn.com", WebsiteSuggestionCategory.NEWS),
        WebsiteSuggestion("bbc.com", WebsiteSuggestionCategory.NEWS),
        WebsiteSuggestion("theguardian.com", WebsiteSuggestionCategory.NEWS),
        WebsiteSuggestion("nytimes.com", WebsiteSuggestionCategory.NEWS),
        WebsiteSuggestion("reuters.com", WebsiteSuggestionCategory.NEWS),
        WebsiteSuggestion("amazon.com", WebsiteSuggestionCategory.SHOPPING),
        WebsiteSuggestion("ebay.com", WebsiteSuggestionCategory.SHOPPING),
        WebsiteSuggestion("aliexpress.com", WebsiteSuggestionCategory.SHOPPING),
        WebsiteSuggestion("temu.com", WebsiteSuggestionCategory.SHOPPING),
        WebsiteSuggestion("shein.com", WebsiteSuggestionCategory.SHOPPING),
        WebsiteSuggestion("etsy.com", WebsiteSuggestionCategory.SHOPPING),
        WebsiteSuggestion("store.steampowered.com", WebsiteSuggestionCategory.GAMING),
        WebsiteSuggestion("roblox.com", WebsiteSuggestionCategory.GAMING),
        WebsiteSuggestion("epicgames.com", WebsiteSuggestionCategory.GAMING),
        WebsiteSuggestion("chess.com", WebsiteSuggestionCategory.GAMING),
    )

    fun forCategory(category: WebsiteSuggestionCategory): List<WebsiteSuggestion> =
        if (category == WebsiteSuggestionCategory.ALL) all else all.filter { it.category == category }

    /** Catalog category for a saved host, or null when the host is not in the catalog. */
    fun categoryOf(host: String): WebsiteSuggestionCategory? =
        all.firstOrNull { it.host.equals(host, ignoreCase = true) }?.category

    fun search(
        query: String,
        category: WebsiteSuggestionCategory = WebsiteSuggestionCategory.ALL,
    ): List<WebsiteSuggestion> {
        val base = forCategory(category)
        val q = query.trim().lowercase()
        if (q.isBlank()) return base
        return base.filter { it.host.contains(q) }
    }
}
