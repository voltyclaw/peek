package app.pane.android

import android.content.pm.verify.domain.DomainVerificationUserState
import app.pane.android.ui.home.LinkSettings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun selectedAndVerifiedHostsCountAsOn() {
        assertTrue(LinkSettings.isHostEnabled(DomainVerificationUserState.DOMAIN_STATE_SELECTED))
        assertTrue(LinkSettings.isHostEnabled(DomainVerificationUserState.DOMAIN_STATE_VERIFIED))
        assertFalse(LinkSettings.isHostEnabled(DomainVerificationUserState.DOMAIN_STATE_NONE))
        assertFalse(LinkSettings.isHostEnabled(null))
    }

    @Test
    fun reportMatchesWhatTheSettingsScreenKept() {
        assertEquals(
            LinkSettings.LinkHandlingReport.HandlingOff,
            LinkSettings.report(linkHandlingAllowed = false, enabledCount = 3, hostCount = 10),
        )
        assertEquals(
            LinkSettings.LinkHandlingReport.NoneSelected,
            LinkSettings.report(linkHandlingAllowed = true, enabledCount = 0, hostCount = 10),
        )
        assertEquals(
            LinkSettings.LinkHandlingReport.SomeSelected,
            LinkSettings.report(linkHandlingAllowed = true, enabledCount = 2, hostCount = 10),
        )
        assertEquals(
            LinkSettings.LinkHandlingReport.AllSelected,
            LinkSettings.report(linkHandlingAllowed = true, enabledCount = 10, hostCount = 10),
        )
    }

    @Test
    fun webFiltersStayUnverifiedAndMatchTheHostsPeekDeclares() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("autoVerify=\"true\""))

        val filters = Regex("<intent-filter[\\s\\S]*?</intent-filter>").findAll(manifest).map { it.value }.toList()
        val webFilters = filters.filter { it.contains("android.intent.action.VIEW") }
        assertEquals(LinkSettings.webHosts.size, webFilters.size)
        webFilters.forEach { filter ->
            assertTrue(filter.contains("autoVerify=\"false\""))
            assertTrue(filter.contains("android.intent.category.BROWSABLE"))
            val hosts = Regex("""android:host="([^"]+)"""").findAll(filter).map { it.groupValues[1] }.toSet()
            assertEquals(1, hosts.size)
            Regex("""<data\b[^>]*>""").findAll(filter).forEach { data ->
                assertTrue(data.value.contains("android:scheme=\"https\""))
                assertTrue(data.value.contains("android:host=\""))
            }
        }

        val declaredHosts = Regex("android:host=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toSet()
        assertEquals(LinkSettings.webHosts.toSet(), declaredHosts)

        assertTrue(manifest.contains("android:pathPrefix=\"/p/\""))
        assertTrue(manifest.contains("android:pathPrefix=\"/reel/\""))
        assertTrue(manifest.contains("android:pathPrefix=\"/reels/\""))
        assertTrue(manifest.contains("android:pathPrefix=\"/comments/\""))
        assertTrue(manifest.contains("android:pathPrefix=\"/gallery/\""))
        assertTrue(manifest.contains("android:pathPattern=\"/r/.*/comments/.*\""))
        assertTrue(manifest.contains("android:pathPattern=\"/r/.*/s/.*\""))
    }
}
