package app.pane.android.data.x

import app.pane.android.data.resolver.UrlResolver

fun interface XPageLoader : UrlResolver<ParsedXPost>

/** One later page of public replies, addressed by the conversation's bottom cursor. */
interface XConversationPager {
    suspend fun loadReplies(statusId: String, cursor: String): XReplyPage
}

data class XReplyPage(
    val replies: List<ParsedXReply>,
    val nextCursor: String?,
    val blocked: Boolean,
)
