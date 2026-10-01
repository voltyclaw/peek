package app.pane.android

import android.content.Intent
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
            ACTION_VIEW -> dataString?.takeIf { it.isWebUrl() }
            ACTION_SEND -> extractUrl(
                extraText?.takeIf { it.isNotBlank() }
                    ?: extraHtml?.takeIf { it.isNotBlank() }
                    ?: clipText,
            )
            else -> null
        } ?: return null
        return LinkShims.unwrap(raw)
    }

    private const val ACTION_VIEW = "android.intent.action.VIEW"
    private const val ACTION_SEND = "android.intent.action.SEND"

    private fun String.isWebUrl(): Boolean =
        startsWith("http://") || startsWith("https://")
}
