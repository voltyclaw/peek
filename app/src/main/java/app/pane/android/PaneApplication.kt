package app.pane.android

import android.app.Application
import app.pane.android.app.AppContainer
import app.pane.android.app.DefaultAppContainer

class PaneApplication : Application() {
    val container: AppContainer by lazy { DefaultAppContainer(applicationContext) }
}
