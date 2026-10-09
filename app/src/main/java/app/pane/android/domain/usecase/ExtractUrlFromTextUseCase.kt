package app.pane.android.domain.usecase

import java.net.URI

class ExtractUrlFromTextUseCase {
    operator fun invoke(text: CharSequence?): String? {
        val candidate = URL_PATTERN.find(text?.toString().orEmpty())
            ?.value
            ?.trimEnd(*TRAILING_PUNCTUATION)
            ?: return null

        return runCatching { URI(candidate) }
            .getOrNull()
            ?.takeIf { uri ->
                uri.scheme.equals("http", ignoreCase = true) ||
                    uri.scheme.equals("https", ignoreCase = true)
            }
            ?.takeIf { it.host?.isNotBlank() == true }
            ?.toASCIIString()
    }

    /** How many web URLs [text] contains. Paste keeps the first and can say so. */
    fun count(text: CharSequence?): Int {
        val raw = text?.toString().orEmpty()
        return URL_PATTERN.findAll(raw).count { match ->
            invoke(match.value) != null
        }
    }

    private companion object {
        val URL_PATTERN = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
        val TRAILING_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')
    }
}
