package app.pane.android.domain.model

/**
 * The author row is a name and, when Pane actually has one, a handle or page name.
 * The source word (Facebook, FACEBOOK, X, …) is never a display name, and it is never
 * repeated as a second line.
 */
object AuthorLines {
    data class Presented(val name: String, val metadata: String)

    fun present(name: String, metadata: String): Presented {
        val cleanName = name.trim().takeUnless(::isSourceLabel).orEmpty()
        val meta = metadata.trim()
        val cleanMeta = when {
            meta.isEmpty() || isSourceLabel(meta) -> ""
            cleanName.isNotEmpty() && meta.equals(cleanName, ignoreCase = true) -> ""
            else -> meta
        }
        return when {
            cleanName.isNotEmpty() -> Presented(cleanName, cleanMeta)
            cleanMeta.isNotEmpty() -> Presented(cleanMeta, "")
            else -> Presented("", "")
        }
    }

    fun isSourceLabel(value: String): Boolean =
        value.trim().lowercase() in SOURCE_NAMES

    private val SOURCE_NAMES = setOf(
        "facebook",
        "instagram",
        "x",
        "twitter",
        "reddit",
    )
}
