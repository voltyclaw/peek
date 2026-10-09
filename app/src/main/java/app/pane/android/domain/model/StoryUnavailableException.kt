package app.pane.android.domain.model

import java.io.IOException

/** A story link Pane recognized, but the media is not publicly fetchable. */
class StoryUnavailableException : IOException("Story unavailable")
