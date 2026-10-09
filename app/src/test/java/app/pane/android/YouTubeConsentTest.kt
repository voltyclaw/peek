package app.pane.android

import app.pane.android.BrowserTrampoline
import app.pane.android.data.youtube.YouTubeLinkContentRepository
import app.pane.android.data.youtube.YouTubeUrls
import app.pane.android.data.youtube.expiredYouTubeCookies
import app.pane.android.domain.youtube.MapYouTubeConsentStore
import app.pane.android.domain.youtube.YouTubeConsentKeys
import app.pane.android.domain.youtube.YouTubeConsentPolicy
import app.pane.android.domain.youtube.YouTubeDataApi
import app.pane.android.domain.youtube.YouTubeEntry
import app.pane.android.domain.youtube.YouTubePlayer
import app.pane.android.domain.youtube.YouTubeSession
import app.pane.android.domain.youtube.YouTubeSiteData
import app.pane.android.domain.youtube.YouTubeSurface
import app.pane.android.domain.youtube.YouTubeVideo
import app.pane.android.ui.youtube.consentPieces
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeConsentTest {
    @Test
    fun persistenceKeepsThePolicyVersionAndTimestamp() {
        val store = MapYouTubeConsentStore()
        val session = session(store)
        val at = 1_759_977_600_000L

        session.accept(VIDEO, at)

        val saved = store.read()
        assertEquals(YouTubeConsentPolicy.VERSION, saved?.policyVersion)
        assertEquals(at, saved?.acceptedAtEpochMillis)
        assertEquals(YouTubeConsentKeys.POLICY_VERSION, "policy_accepted_version")
        assertEquals(YouTubeConsentKeys.ACCEPTED_AT, "policy_accepted_at")

        val restored = session(store, api = RecordingYouTubeDataApi())
        assertTrue(restored.hasConsent())
        val again = restored.open(VIDEO, YouTubeEntry.View) as YouTubeSurface.Player
        assertEquals(YouTubePlayer.embedUrl(VIDEO), again.embedUrl)
    }

    @Test
    fun anOlderPolicyVersionDoesNotCountAsConsent() {
        val store = MapYouTubeConsentStore()
        store.write(app.pane.android.domain.youtube.YouTubeConsent("2020-01-01", 10L))
        val api = RecordingYouTubeDataApi()
        val session = session(store, api)

        assertFalse(session.hasConsent())
        assertTrue(session.open(VIDEO, YouTubeEntry.Share) is YouTubeSurface.Poster)
        assertTrue(api.ids.isEmpty())
    }

    @Test
    fun theGateBlocksTheDataApiAndThePlayerUntilPlayOnYouTube() {
        val api = RecordingYouTubeDataApi()
        val session = session(api = api)

        YouTubeEntry.entries.forEach { entry ->
            val surface = session.open(VIDEO, entry)
            assertTrue(surface is YouTubeSurface.Poster)
            assertEquals(VIDEO, surface.videoId)
        }
        assertTrue(api.ids.isEmpty())

        val player = session.accept(VIDEO, 50L)
        assertEquals(listOf(VIDEO), api.ids)
        assertEquals("https://www.youtube-nocookie.com/embed/$VIDEO", player.embedUrl)
        assertFalse(player.embedUrl.contains("://www.youtube.com/"))
    }

    @Test
    fun viewAndShareSkipThePosterAfterConsent() {
        val api = RecordingYouTubeDataApi()
        val session = session(api = api)
        session.accept(VIDEO, 50L)
        api.ids.clear()

        val view = session.open(VIDEO, YouTubeEntry.View)
        val share = session.open(VIDEO, YouTubeEntry.Share)

        assertTrue(view is YouTubeSurface.Player)
        assertTrue(share is YouTubeSurface.Player)
        assertEquals(listOf(VIDEO, VIDEO), api.ids)
        assertEquals(
            BrowserTrampoline.Decision.OpenInPane("https://www.youtube.com/watch?v=$VIDEO"),
            BrowserTrampoline.decide("https://www.youtube.com/watch?v=$VIDEO", enabled = true),
        )
        assertEquals(
            BrowserTrampoline.Decision.OpenInPane("https://youtu.be/$VIDEO"),
            BrowserTrampoline.decide("https://youtu.be/$VIDEO", enabled = true),
        )
    }

    @Test
    fun withdrawalClearsConsentCookiesAndBringsThePosterBack() {
        val store = MapYouTubeConsentStore()
        val api = RecordingYouTubeDataApi()
        val site = RecordingSiteData()
        val session = session(store, api, site)
        session.accept(VIDEO, 50L)
        api.ids.clear()

        session.withdraw()

        assertNull(store.read())
        assertEquals(1, site.clears)
        assertFalse(session.hasConsent())
        assertTrue(session.open(VIDEO, YouTubeEntry.View) is YouTubeSurface.Poster)
        assertTrue(api.ids.isEmpty())
    }

    @Test
    fun turningConsentOnWithoutThePosterDoesNotRecordIt() {
        val store = MapYouTubeConsentStore()
        val session = session(store)
        assertFalse(session.hasConsent())
        assertNull(store.read())
        assertTrue(session.open(VIDEO, YouTubeEntry.View) is YouTubeSurface.Poster)
    }

    @Test
    fun resolvingAYouTubeLinkDoesNotCallTheDataApi() = runBlocking {
        val api = RecordingYouTubeDataApi()
        val repository = YouTubeLinkContentRepository()
        val url = "https://www.youtube.com/watch?v=$VIDEO"

        val content = repository.resolve(url).getOrThrow()

        assertEquals(url, content.url)
        assertEquals("", content.title)
        assertTrue(api.ids.isEmpty())
        assertNull(YouTubeUrls.videoId("https://www.youtube.com/"))
        assertEquals(VIDEO, YouTubeUrls.videoId("https://youtu.be/$VIDEO"))
        assertEquals(VIDEO, YouTubeUrls.videoId("https://m.youtube.com/shorts/$VIDEO"))
        assertEquals(VIDEO, YouTubeUrls.videoId("https://www.youtube.com/watch?list=PL123&v=$VIDEO"))
    }

    @Test
    fun cookieWithdrawalExpiresEachYouTubeCookie() {
        assertEquals(
            listOf("VISITOR_INFO1_LIVE=; Max-Age=0; Path=/", "YSC=; Max-Age=0; Path=/"),
            expiredYouTubeCookies("VISITOR_INFO1_LIVE=abc; YSC=xyz"),
        )
        assertTrue(expiredYouTubeCookies(null).isEmpty())
    }

    @Test
    fun agreementLineKeepsHebrewPrefixesOutsideTheLinkText() {
        val paneTerms = "Pane's Terms"
        val panePrivacy = "Pane's Privacy Policy"
        val terms = "YouTube's Terms"
        val google = "Google's Privacy Policy"
        val english = consentPieces(
            "Tapping plays this video with YouTube's player, which shares data with Google, and means you agree to \u0001, \u0002, \u0003 and \u0004.",
            fourLinks(paneTerms, panePrivacy, terms, google),
        )
        assertEquals(paneTerms, english.first { it.url == "" }.label)
        assertEquals(terms, english.first { it.url == "https://www.youtube.com/t/terms" }.label)
        assertEquals(google, english.first { it.url == "https://policies.google.com/privacy" }.label)
        assertTrue(english.any { it.label.contains("shares data with Google") })

        val hePaneTerms = "תנאי השימוש של Pane"
        val hePanePrivacy = "מדיניות הפרטיות של Pane"
        val heTerms = "תנאי השימוש של YouTube"
        val heGoogle = "מדיניות הפרטיות של Google"
        val hebrew = consentPieces(
            "לחיצה תפעיל את הסרטון בנגן של YouTube, שמעביר מידע ל-Google, ומשמעותה הסכמה ל\u0001, ל\u0002, ל\u0003 ול\u0004.",
            fourLinks(hePaneTerms, hePanePrivacy, heTerms, heGoogle),
        )
        assertEquals("לחיצה תפעיל את הסרטון בנגן של YouTube, שמעביר מידע ל-Google, ומשמעותה הסכמה ל", hebrew.first().label)
        assertEquals(hePaneTerms, hebrew[1].label)
        assertEquals(", ל", hebrew[2].label)
        assertEquals(hePanePrivacy, hebrew[3].label)
        assertEquals(", ל", hebrew[4].label)
        assertEquals(heTerms, hebrew[5].label)
        assertEquals(" ול", hebrew[6].label)
        assertEquals(heGoogle, hebrew[7].label)
    }

    private fun fourLinks(paneTerms: String, panePrivacy: String, terms: String, google: String) = listOf(
        "\u0001" to (paneTerms to ""),
        "\u0002" to (panePrivacy to "https://pane.example/privacy"),
        "\u0003" to (terms to "https://www.youtube.com/t/terms"),
        "\u0004" to (google to "https://policies.google.com/privacy"),
    )

    private fun session(
        store: MapYouTubeConsentStore = MapYouTubeConsentStore(),
        api: RecordingYouTubeDataApi = RecordingYouTubeDataApi(),
        site: RecordingSiteData = RecordingSiteData(),
    ) = YouTubeSession(store, api, site)

    private class RecordingYouTubeDataApi : YouTubeDataApi {
        val ids = mutableListOf<String>()
        override fun fetch(videoId: String): YouTubeVideo {
            ids += videoId
            return YouTubeVideo(videoId)
        }
    }

    private class RecordingSiteData : YouTubeSiteData {
        var clears = 0
        override fun clear() {
            clears += 1
        }
    }

    private companion object {
        const val VIDEO = "dQw4w9WgXcQ"
    }
}
