package app.pane.android.data.cache

import app.pane.android.domain.model.LinkContent
import kotlinx.serialization.Serializable

@Serializable
data class LinkContentCacheEntry(
    val key: String,
    val content: LinkContent,
    val resolverId: String? = null,
)

@Serializable
data class LinkContentCacheDocument(
    val entries: List<LinkContentCacheEntry> = emptyList(),
)
