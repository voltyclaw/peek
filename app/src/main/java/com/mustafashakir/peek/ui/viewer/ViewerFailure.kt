package com.mustafashakir.peek.ui.viewer

import com.mustafashakir.peek.ui.model.ViewerUiState

/** Unsupported URLs keep the existing screen. Fetch and parse failures stay separate. */
fun viewerStateFor(url: String, error: Throwable): ViewerUiState =
    if (error is IllegalArgumentException) {
        ViewerUiState.Unavailable(url)
    } else {
        ViewerUiState.LoadFailed(
            url = url,
            reason = error.message?.takeIf { it.isNotBlank() } ?: "Couldn’t load this link",
        )
    }
