package com.mustafashakir.peek.ui.player

/** Photos and video open on the picture, with the comments sheet lowered. */
internal fun playerShowsControlsOnOpen(): Boolean = false

/** Swiping onto a video brings the controls back. Image pages stay on the picture. */
internal fun playerShowsControlsAfterPageChange(videoUrl: String?): Boolean = !videoUrl.isNullOrBlank()
