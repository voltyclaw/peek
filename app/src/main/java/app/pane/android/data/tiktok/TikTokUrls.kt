package app.pane.android.data.tiktok

import app.pane.android.domain.tiktok.TikTokLink
import app.pane.android.domain.tiktok.TikTokLinks

internal object TikTokUrls {
    fun parse(url: String): TikTokLink? = TikTokLinks.parse(url)
    fun supports(url: String): Boolean = TikTokLinks.supports(url)
    fun canonical(url: String): String? = TikTokLinks.parse(url)?.canonicalUrl
    fun isHost(url: String): Boolean = TikTokLinks.isHost(url)
}
