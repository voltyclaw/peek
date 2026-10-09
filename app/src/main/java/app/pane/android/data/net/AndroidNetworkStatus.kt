package app.pane.android.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.pane.android.domain.model.NetworkSignals
import app.pane.android.domain.model.NetworkStatus

class AndroidNetworkStatus(context: Context) : NetworkStatus {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun online(): Boolean {
        val active = manager?.activeNetwork ?: return manager == null
        val caps = manager.getNetworkCapabilities(active) ?: return false
        return NetworkSignals.online(
            hasDefaultNetwork = true,
            internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }
}
