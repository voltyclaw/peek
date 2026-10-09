package app.pane.android.data.net

/** Open-link HTTP limits. Connect stays under ten seconds so a dead route fails fast. */
object HttpTimeouts {
    const val CONNECT_MILLIS = 8_000
    const val READ_MILLIS = 10_000
}
