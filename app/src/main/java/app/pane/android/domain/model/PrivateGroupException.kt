package app.pane.android.domain.model

import java.io.IOException

/** The fetched page describes a group, not the requested post. */
class PrivateGroupException : IOException("Private group")
