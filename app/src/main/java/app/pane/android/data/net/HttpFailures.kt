package app.pane.android.data.net

import app.pane.android.domain.model.SourceFailure

/** HTTP status to a typed failure. 401/403 are private, 404/410 are gone, anything else can be retried. */
fun httpFailure(status: Int, detail: String? = null): SourceFailure {
    val message = detail ?: "HTTP $status"
    return when (status) {
        401, 403 -> SourceFailure.Private(message = message)
        404, 410 -> SourceFailure.Gone(message = message)
        else -> SourceFailure.Network(message = message)
    }
}

fun loginWallFailure(detail: String? = null): SourceFailure.Private =
    SourceFailure.Private(message = detail ?: "login")
