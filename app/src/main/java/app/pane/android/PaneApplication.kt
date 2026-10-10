package app.pane.android

import android.app.Application
import app.pane.android.app.AppContainer
import app.pane.android.app.DefaultAppContainer
import app.pane.android.data.webview.LegacySiteData

class PaneApplication : Application() {
    val container: AppContainer by lazy { DefaultAppContainer(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        BrowserTrampolinePreferences.applyStored(this)
        LegacySiteData.resume(this)
    }
}
