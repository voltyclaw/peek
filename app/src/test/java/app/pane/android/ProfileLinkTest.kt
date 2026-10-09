package app.pane.android

import app.pane.android.data.links.PaneEntry
import app.pane.android.data.links.paneEntry
import app.pane.android.data.links.profileHandoffFinishes
import app.pane.android.data.links.mentionTapRecordsHistory
import app.pane.android.data.links.profileHandoffRecordsHistory
import app.pane.android.data.links.profileHandoffRecordsRecent
import app.pane.android.data.links.profileLink
import app.pane.android.ui.actions.ExternalStart
import app.pane.android.ui.actions.packagedAttempts
import app.pane.android.ui.actions.performExternalLaunch
import app.pane.android.ui.actions.shouldFinishAfterExternalOpen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileLinkTest {
    @Test
    fun profilesSkipTheViewerAndPostsDoNot() {
        assertEquals("https://x.com/jack", profileLink("https://x.com/jack")?.url)
        assertEquals(listOf("com.twitter.android"), profileLink("https://twitter.com/jack")?.packages)
        assertEquals("https://www.facebook.com/zuck", profileLink("https://www.facebook.com/zuck")?.url)
        assertEquals(
            listOf("com.facebook.katana", "com.facebook.lite"),
            profileLink("https://m.facebook.com/profile.php?id=4")?.packages,
        )
        assertEquals("https://www.facebook.com/profile.php?id=4", profileLink("https://www.facebook.com/profile.php?id=4")?.url)
        assertEquals("https://www.instagram.com/nasa/", profileLink("https://www.instagram.com/nasa/")?.url)
        assertEquals(listOf("com.instagram.android"), profileLink("https://instagram.com/nasa")?.packages)
        assertEquals("https://www.reddit.com/user/spez", profileLink("https://www.reddit.com/u/spez")?.url)
        assertEquals("https://www.reddit.com/user/spez", profileLink("https://www.reddit.com/user/spez")?.url)
        assertEquals("https://www.reddit.com/r/technology", profileLink("https://old.reddit.com/r/technology")?.url)
        assertEquals(listOf("com.reddit.frontpage"), profileLink("https://www.reddit.com/r/technology")?.packages)
        assertEquals("https://www.threads.net/@zuck", profileLink("https://www.threads.net/@zuck")?.url)
        assertEquals(listOf("com.instagram.barcelona"), profileLink("https://www.threads.net/@zuck")?.packages)

        listOf(
            "https://x.com/jack/status/20",
            "https://x.com/i/status/20",
            "https://x.com/home",
            "https://x.com/search",
            "https://x.com/explore",
            "https://x.com/settings",
            "https://x.com/hashtag/pane",
            "https://www.facebook.com/zuck/posts/pfbid0123",
            "https://www.facebook.com/share/p/AbCdEf/",
            "https://www.facebook.com/watch/?v=123",
            "https://www.facebook.com/reel/123",
            "https://www.instagram.com/p/abc123/",
            "https://www.instagram.com/reel/abc123/",
            "https://www.instagram.com/stories/nasa/123/",
            "https://www.instagram.com/tv/abc123/",
            "https://www.reddit.com/r/pics/comments/abc123/title/",
            "https://www.reddit.com/r/pics/s/abc123",
            "https://www.reddit.com/user/spez/comments/abc123/title/",
        ).forEach { url ->
            assertNull(url, profileLink(url))
            assertEquals(url, PaneEntry.Viewer, paneEntry(url))
        }
    }

    @Test
    fun aProfileHandoffDoesNotWriteRecentsAndFinishesOnlyForShareIn() {
        assertEquals(PaneEntry.ProfileHandoff, paneEntry("https://x.com/jack"))
        assertFalse(profileHandoffRecordsRecent())
        assertFalse(profileHandoffRecordsHistory())
        assertFalse(mentionTapRecordsHistory())
        assertTrue(profileHandoffFinishes(fromExternal = true, started = true))
        assertFalse(profileHandoffFinishes(fromExternal = false, started = true))
        assertFalse(profileHandoffFinishes(fromExternal = true, started = false))
        assertTrue(shouldFinishAfterExternalOpen(started = true, finishAfter = true))
        assertFalse(shouldFinishAfterExternalOpen(started = true, finishAfter = false))
        assertFalse(shouldFinishAfterExternalOpen(started = false, finishAfter = true))
    }

    @Test
    fun profileLaunchUsesExplicitPackagesAndNeverAPackageLessView() {
        val profile = "https://www.facebook.com/zuck"
        val attempts = packagedAttempts(profile)
        assertEquals(listOf("com.facebook.katana", "com.facebook.lite"), attempts.map { it.packageName })
        assertTrue(attempts.all { it.packageName.isNotBlank() })

        var chooser = false
        val started = mutableListOf<String>()
        val outcome = performExternalLaunch(
            url = profile,
            installed = { true },
            start = { attempt ->
                started += attempt.packageName
                if (attempt.packageName == "com.facebook.katana") ExternalStart.NotFound else ExternalStart.Started
            },
            openBrowser = { false },
            openChooser = { chooser = true; true },
        )
        assertEquals(listOf("com.facebook.katana", "com.facebook.lite"), started)
        assertEquals("com.facebook.lite", outcome.usedPackage)
        assertFalse(chooser)
        assertFalse(outcome.fellBackToBrowser)

        chooser = false
        val fallback = performExternalLaunch(
            url = "https://x.com/jack",
            installed = { false },
            start = { ExternalStart.Security },
            openBrowser = { true },
            openChooser = { chooser = true; true },
        )
        assertTrue(fallback.fellBackToBrowser)
        assertTrue(fallback.started)
        assertFalse(chooser)
    }
}
