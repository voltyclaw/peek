package app.pane.android.domain.model

/**
 * A viewer failure whose kind is chosen where the signal is known.
 * [message] and [cause] are for logs. The screen never reads them.
 */
sealed class SourceFailure(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Unsupported(val url: String) : SourceFailure("Unsupported link: $url")

    /** Login wall, 401/403, checkpoint, or a private group. */
    class Private(
        val reason: PrivateReason = PrivateReason.Generic,
        message: String? = null,
        cause: Throwable? = null,
    ) : SourceFailure(message, cause)

    /** 404/410, removed, or expired. Bluesky kinds keep their own copy. */
    class Gone(
        val bluesky: BlueskyKind? = null,
        message: String? = null,
        cause: Throwable? = null,
    ) : SourceFailure(message, cause)

    data object StoryUnavailable : SourceFailure("Story unavailable")

    class Offline(cause: Throwable? = null) : SourceFailure(message = "offline", cause = cause)

    /** Timeout, reset, or 5xx. Also the screen for an error nobody classified. */
    class Network(
        cause: Throwable? = null,
        message: String? = null,
    ) : SourceFailure(message, cause)

    /** The page changed, or the markup was not a post. */
    class Parse(
        cause: Throwable? = null,
        message: String? = null,
    ) : SourceFailure(message, cause)

    enum class PrivateReason { Generic, Group }

    enum class BlueskyKind { Gone, Hidden, SignedIn }
}
