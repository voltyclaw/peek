package com.mustafashakir.peek.ui.viewer

internal object VideoAutoplay {
    fun shouldOpen(alreadyOpened: Boolean, isVideo: Boolean): Boolean =
        !alreadyOpened && isVideo
}
