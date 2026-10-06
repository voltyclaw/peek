package app.pane.android

import android.content.Intent
import app.pane.android.data.sample.SamplePosts
import app.pane.android.domain.model.LinkShims
import app.pane.android.domain.usecase.ExtractUrlFromTextUseCase

/**
 * Links that open Pane from another app.
 * VIEW is a tapped address. SEND is the system share sheet, which does not depend on
 * Meta's verified domains: Facebook, Instagram, and browsers list Pane when the shared
 * payload is text/plain or text/html.
 */
internal object IncomingLink {
    private val extractUrl = ExtractUrlFromTextUseCase()

    fun isExternalOpen(intent: Intent?): Boolean {
        val action = intent?.action
        return action == ACTION_VIEW || action == ACTION_SEND
    }

    fun urlFrom(intent: Intent?): String? {
        if (intent == null) return null
        val clip = intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        return urlFrom(
            action = intent.action,
            dataString = intent.dataString,
            extraText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            extraHtml = intent.getCharSequenceExtra(Intent.EXTRA_HTML_TEXT)?.toString(),
            clipText = clip?.text?.toString()?.takeIf { it.isNotBlank() }
                ?: clip?.htmlText?.takeIf { it.isNotBlank() }
                ?: clip?.uri?.toString(),
        )
    }

    internal fun urlFrom(
        action: String?,
        dataString: String?,
        extraText: String?,
        extraHtml: String?,
        clipText: String? = null,
    ): String? {
        val raw = when (action) {
            ACTION_VIEW -> {
                val data = dataString?.let { value ->
                    if (BuildConfig.DEBUG) {
                        SamplePosts.canonicalForDeepLink(value) ?: value.takeIf { it.isWebUrl() }
                    } else {
                        value.takeIf { it.isWebUrl() }
                    }
                }
                if (data != null && data.startsWith("pane:")) {
                    data
                } else {
                    preferStoryUrl(
                        listOfNotNull(
                            data,
                            extractUrl(extraText),
                            extractUrl(extraHtml),
                            extractUrl(clipText),
                        ),
                    )
                }
            }
            ACTION_SEND -> preferStoryUrl(
                listOfNotNull(
                    extractUrl(extraText),
                    extractUrl(extraHtml),
                    extractUrl(clipText),
                ),
            )
            else -> null
        } ?: return null
        return LinkShims.unwrap(raw)
    }

    /**
     * A share can put a short fb.watch or permalink in one extra and the full story
     * URL (story_fbid, bucket_id, share token) in another. Keep the fuller story URL.
     * When nothing is a story, the first candidate wins.
     */
    internal fun preferStoryUrl(candidates: List<String>): String? {
        val urls = candidates
            .map { LinkShims.unwrap(it.trim()) }
            .filter { it.isNotEmpty() }
        if (urls.isEmpty()) return null
        return urls.reduce { best, next ->
            if (storyScore(next) > storyScore(best)) next else best
        }
    }

    private fun storyScore(url: String): Int {
        val text = url.lowercase()
        var score = 0
        if ("/stories/" in text) score += 100
        if ("story_fbid=" in text) score += 40
        if ("bucket_id=" in text) score += 40
        if ("view_single=" in text) score += 20
        if ("mibextid=" in text || "igsh=" in text || "ig_story" in text) score += 10
        return score
    }

    private const val ACTION_VIEW = "android.intent.action.VIEW"
    private const val ACTION_SEND = "android.intent.action.SEND"

    private fun String.isWebUrl(): Boolean =
        startsWith("http://") || startsWith("https://")
}
