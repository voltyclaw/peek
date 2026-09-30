package com.mustafashakir.peek

import com.mustafashakir.peek.ui.home.LinkSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkSettingsTest {
    @Test
    fun modernAndroidOpensTheOpenByDefaultScreen() {
        assertEquals("android.settings.APP_OPEN_BY_DEFAULT_SETTINGS", LinkSettings.action(31))
        assertEquals("android.settings.APP_OPEN_BY_DEFAULT_SETTINGS", LinkSettings.action(36))
    }

    @Test
    fun olderAndroidOpensAppDetails() {
        assertEquals("android.settings.APPLICATION_DETAILS_SETTINGS", LinkSettings.action(26))
        assertEquals("android.settings.APPLICATION_DETAILS_SETTINGS", LinkSettings.action(30))
    }
}
