package com.mustafashakir.peek.ui.player

/** Photos keep the comments sheet up. A video opens the way a tap already lowers it. */
internal fun playerShowsControlsOnOpen(videoUrl: String?): Boolean = videoUrl == null
