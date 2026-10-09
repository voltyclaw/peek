package app.pane.android.data.bluesky

internal data class BskyLabel(val value: String, val src: String = "", val neg: Boolean = false)

internal enum class BskyPostGate { Show, Warn, Hide, SignedOut }

internal enum class BskyMediaGate { Show, HideAdult, HideGraphic, Blur }

internal data class BskyModeration(
    val post: BskyPostGate,
    val media: BskyMediaGate,
    val avatarHidden: Boolean,
) {
    val blocksRoot: Boolean get() = post == BskyPostGate.Hide || post == BskyPostGate.SignedOut
}

internal object BskyLabels {
    private val adult = setOf("porn", "sexual", "graphic-media", "nudity")

    fun decide(postLabels: List<BskyLabel>, authorLabels: List<BskyLabel>, authorDid: String): BskyModeration {
        val post = active(postLabels)
        val author = active(authorLabels)
        val gate = when {
            post.any { it.value == "!hide" } || author.any { it.value == "!hide" } -> BskyPostGate.Hide
            signedOut(author, authorDid) || signedOut(post, authorDid) -> BskyPostGate.SignedOut
            post.any { it.value == "!warn" } || author.any { it.value == "!warn" } -> BskyPostGate.Warn
            else -> BskyPostGate.Show
        }
        val values = (post + author).map { it.value }.toSet()
        val media = when {
            "graphic-media" in values -> BskyMediaGate.HideGraphic
            "porn" in values || "sexual" in values -> BskyMediaGate.HideAdult
            "nudity" in values -> BskyMediaGate.Blur
            else -> BskyMediaGate.Show
        }
        val avatarHidden = author.any { it.value in adult }
        return BskyModeration(gate, media, avatarHidden)
    }

    private fun signedOut(labels: List<BskyLabel>, authorDid: String): Boolean =
        labels.any { it.value == "!no-unauthenticated" && it.src == authorDid && authorDid.isNotBlank() }

    private fun active(labels: List<BskyLabel>): List<BskyLabel> =
        labels.filter { !it.neg && it.value in KNOWN }

    private val KNOWN = setOf(
        "!hide",
        "!warn",
        "!no-unauthenticated",
        "porn",
        "sexual",
        "graphic-media",
        "nudity",
    )
}
