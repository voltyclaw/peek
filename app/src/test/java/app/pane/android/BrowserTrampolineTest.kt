package app.pane.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTrampolineTest {
    @Test
    fun offHandsEveryWebLinkOff() {
        val facebook = "https://www.facebook.com/share/p/AbCdEf/"
        val google = "https://www.google.com/search?q=pane"
        assertEquals(BrowserTrampoline.Decision.HandOff(facebook), BrowserTrampoline.decide(facebook, enabled = false))
        assertEquals(BrowserTrampoline.Decision.HandOff(google), BrowserTrampoline.decide(google, enabled = false))
    }

    @Test
    fun onOpensFacebookAndInstagramHostsInPane() {
        val post = "https://www.facebook.com/share/p/AbCdEf/"
        assertEquals(BrowserTrampoline.Decision.OpenInPane(post), BrowserTrampoline.decide(post, enabled = true))
        val mobile = "https://m.facebook.com/story.php?story_fbid=1&id=2"
        assertEquals(BrowserTrampoline.Decision.OpenInPane(mobile), BrowserTrampoline.decide(mobile, enabled = true))
        listOf(
            "facebook.com",
            "www.facebook.com",
            "m.facebook.com",
            "mbasic.facebook.com",
            "lm.facebook.com",
            "l.facebook.com",
            "web.facebook.com",
            "touch.facebook.com",
            "fb.com",
            "www.fb.com",
            "fb.watch",
            "fb.me",
            "www.fb.me",
            "instagram.com",
            "www.instagram.com",
            "m.instagram.com",
            "l.instagram.com",
        ).forEach { host ->
            assertTrue(host, BrowserTrampoline.isMetaHost(host))
        }
    }

    @Test
    fun onUnwrapsAMetaShimIntoThePost() {
        val wrapped =
            "https://lm.facebook.com/l.php?u=https%3A%2F%2Fl.instagram.com%2F%3Fu%3Dhttps%253A%252F%252Fwww.instagram.com%252Freel%252FDapVyootsZw%252F"
        assertEquals(
            BrowserTrampoline.Decision.OpenInPane("https://www.instagram.com/reel/DapVyootsZw/"),
            BrowserTrampoline.decide(wrapped, enabled = true),
        )
    }

    @Test
    fun onHandsAShimToAForeignSiteBackAsTheOriginalUrl() {
        val original = "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Farticle&h=AT0"
        assertEquals(
            BrowserTrampoline.Decision.HandOff(original),
            BrowserTrampoline.decide(original, enabled = true),
        )
    }

    @Test
    fun onKeepsSupportedPostsInPaneAndHandsOtherSitesOff() {
        listOf(
            "https://www.reddit.com/r/android/comments/abc123/title/",
            "https://x.com/AexodusCapital/status/2105362146922000492",
            "https://twitter.com/pane/status/2105362146922000492",
            "https://www.instagram.com/reel/DapVyootsZw/",
            "https://www.instagram.com/p/abc123/",
        ).forEach { url ->
            assertEquals(url, BrowserTrampoline.Decision.OpenInPane(url), BrowserTrampoline.decide(url, enabled = true))
        }
        listOf(
            "https://www.facebook.com/marketplace/item/1234567890",
            "https://www.google.com/",
            "https://benamiartgallery.com/",
            "https://notfacebook.com/post",
            "https://www.reddit.com/",
            "https://x.com/home",
        ).forEach { url ->
            assertEquals(url, BrowserTrampoline.Decision.HandOff(url), BrowserTrampoline.decide(url, enabled = true))
        }
        assertEquals(
            BrowserTrampoline.Decision.OpenInSource("https://x.com/AexodusCapital"),
            BrowserTrampoline.decide("https://x.com/AexodusCapital", enabled = true),
        )
        assertEquals(
            BrowserTrampoline.Decision.OpenInSource("https://www.facebook.com/zuck"),
            BrowserTrampoline.decide("https://www.facebook.com/zuck", enabled = true),
        )
        assertEquals(
            BrowserTrampoline.Decision.OpenInSource("https://www.instagram.com/nasa/"),
            BrowserTrampoline.decide("https://www.instagram.com/nasa/", enabled = true),
        )
        listOf("reddit.com", "www.reddit.com", "x.com", "twitter.com", "notfacebook.com", "facebook.com.evil.test", "").forEach { host ->
            assertFalse(host, BrowserTrampoline.isMetaHost(host))
        }
    }

    @Test
    fun blankAndNonWebUrlsAreIgnored() {
        assertEquals(BrowserTrampoline.Decision.Ignore, BrowserTrampoline.decide(null, enabled = true))
        assertEquals(BrowserTrampoline.Decision.Ignore, BrowserTrampoline.decide("   ", enabled = false))
        assertEquals(BrowserTrampoline.Decision.Ignore, BrowserTrampoline.decide("pane://post", enabled = true))
        assertEquals(BrowserTrampoline.Decision.Ignore, BrowserTrampoline.decide("ftp://facebook.com/a", enabled = true))
    }

    @Test
    fun browserDefaultsPromptDependsOnTheRoleApi() {
        assertEquals(
            BrowserTrampoline.BrowserDefaultsTarget.DefaultApps,
            BrowserTrampoline.browserDefaultsTarget(sdkInt = 26, roleAvailable = false, roleHeld = false),
        )
        assertEquals(
            BrowserTrampoline.BrowserDefaultsTarget.DefaultApps,
            BrowserTrampoline.browserDefaultsTarget(sdkInt = 28, roleAvailable = true, roleHeld = false),
        )
        assertEquals(
            BrowserTrampoline.BrowserDefaultsTarget.RoleRequest,
            BrowserTrampoline.browserDefaultsTarget(sdkInt = 29, roleAvailable = true, roleHeld = false),
        )
        assertEquals(
            BrowserTrampoline.BrowserDefaultsTarget.RoleRequest,
            BrowserTrampoline.browserDefaultsTarget(sdkInt = 36, roleAvailable = true, roleHeld = false),
        )
        assertEquals(
            BrowserTrampoline.BrowserDefaultsTarget.DefaultApps,
            BrowserTrampoline.browserDefaultsTarget(sdkInt = 36, roleAvailable = true, roleHeld = true),
        )
        assertEquals(
            BrowserTrampoline.BrowserDefaultsTarget.DefaultApps,
            BrowserTrampoline.browserDefaultsTarget(sdkInt = 36, roleAvailable = false, roleHeld = false),
        )
    }

    @Test
    fun handoffPrefersChromeAndNeverReturnsPane() {
        val pane = BrowserTrampoline.Candidate("app.pane.android", "app.pane.android.BrowserTrampolineActivity")
        val chrome = BrowserTrampoline.Candidate("com.android.chrome", "com.google.android.apps.chrome.Main")
        val firefox = BrowserTrampoline.Candidate("org.mozilla.firefox", "org.mozilla.firefox.App")
        assertEquals(chrome, BrowserTrampoline.pickHandoff(listOf(pane, firefox, chrome), OWN))
        assertNull(BrowserTrampoline.pickHandoff(listOf(pane), OWN))
        assertEquals(firefox, BrowserTrampoline.pickHandoff(listOf(pane, firefox), OWN))
    }

    @Test
    fun savedBrowserWinsOverChromeWhenThatPackageIsInstalled() {
        val chrome = BrowserTrampoline.Candidate("com.android.chrome", "com.google.android.apps.chrome.Main")
        val firefox = BrowserTrampoline.Candidate("org.mozilla.firefox", "org.mozilla.firefox.App")
        val brave = BrowserTrampoline.Candidate("com.brave.browser", "com.brave.browser.BrowserActivity")
        assertEquals(
            firefox,
            BrowserTrampoline.pickHandoff(
                listOf(chrome, brave, firefox),
                OWN,
                preferredPackage = "org.mozilla.firefox",
            ),
        )
        assertEquals(
            brave,
            BrowserTrampoline.pickHandoff(
                listOf(firefox, brave),
                OWN,
                preferredPackage = "com.brave.browser",
                systemPackage = "org.mozilla.firefox",
            ),
        )
    }

    @Test
    fun missingSavedBrowserFallsBackToChromeThenTheSystemBrowser() {
        val pane = BrowserTrampoline.Candidate(OWN, "app.pane.android.BrowserTrampolineActivity")
        val chrome = BrowserTrampoline.Candidate("com.android.chrome", "com.google.android.apps.chrome.Main")
        val firefox = BrowserTrampoline.Candidate("org.mozilla.firefox", "org.mozilla.firefox.App")
        val samsung = BrowserTrampoline.Candidate("com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.SBrowserMainActivity")
        val other = BrowserTrampoline.Candidate("com.example.browser", "com.example.browser.MainActivity")
        val resolver = BrowserTrampoline.Candidate("com.android.intentresolver", "com.android.intentresolver.Chooser")
        assertEquals(
            chrome,
            BrowserTrampoline.pickHandoff(listOf(firefox, chrome), OWN, preferredPackage = "com.opera.browser"),
        )
        assertEquals(
            chrome,
            BrowserTrampoline.pickHandoff(listOf(pane, firefox, chrome), OWN, systemPackage = "org.mozilla.firefox"),
        )
        assertEquals(
            chrome,
            BrowserTrampoline.pickHandoff(listOf(pane, chrome), OWN, preferredPackage = OWN),
        )
        assertEquals(
            other,
            BrowserTrampoline.pickHandoff(listOf(samsung, other), OWN, systemPackage = "com.example.browser"),
        )
        assertEquals(
            samsung,
            BrowserTrampoline.pickHandoff(listOf(other, samsung), OWN, systemPackage = "com.missing.browser"),
        )
        assertNull(
            BrowserTrampoline.pickHandoff(
                listOf(pane, resolver, BrowserTrampoline.Candidate("android", "android.app.Resolver")),
                OWN,
                preferredPackage = "com.android.chrome",
            ),
        )
    }

    @Test
    fun browserListKeepsInstalledPackagesAndSortsKnownOnesFirst() {
        val ordered = BrowserTrampoline.orderBrowsers(
            browsers = listOf(
                "com.example.zebra" to "Zebra",
                "org.mozilla.firefox" to "Firefox",
                "com.android.chrome" to "Chrome",
                "com.example.alpha" to "Alpha",
            ),
            packageName = { it.first },
            label = { it.second },
        )
        assertEquals(
            listOf("com.android.chrome", "org.mozilla.firefox", "com.example.alpha", "com.example.zebra"),
            ordered.map { it.first },
        )
    }

    @Test
    fun handoffFallsBackToSamsungThenAnyOtherBrowser() {
        val pane = BrowserTrampoline.Candidate("app.pane.android", "app.pane.android.MainActivity")
        val samsung = BrowserTrampoline.Candidate("com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.SBrowserMainActivity")
        val other = BrowserTrampoline.Candidate("com.example.browser", "com.example.browser.MainActivity")
        assertEquals(samsung, BrowserTrampoline.pickHandoff(listOf(pane, other, samsung), OWN))
        assertEquals(other, BrowserTrampoline.pickHandoff(listOf(pane, other), OWN))
        assertEquals(other, BrowserTrampoline.pickHandoff(listOf(other, pane), OWN))
    }

    private companion object {
        const val OWN = "app.pane.android"
    }
}
