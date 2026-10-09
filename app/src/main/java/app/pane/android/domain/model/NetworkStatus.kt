package app.pane.android.domain.model

import java.io.IOException

/** True when a validated default network can carry the open. */
fun interface NetworkStatus {
    fun online(): Boolean

    companion object {
        val Always: NetworkStatus = NetworkStatus { true }
    }
}

object NetworkSignals {
    fun online(hasDefaultNetwork: Boolean, internet: Boolean, validated: Boolean): Boolean =
        hasDefaultNetwork && internet && validated
}

/** No validated default network. The viewer says you're offline. */
class OfflineException : IOException("offline")
