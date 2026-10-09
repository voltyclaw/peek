package app.pane.android

import app.pane.android.ui.media.ContinuablePlayer
import app.pane.android.ui.media.PlaybackSession
import app.pane.android.ui.media.SurfaceGesture
import app.pane.android.ui.media.SurfaceGestureAction
import app.pane.android.ui.media.VideoSurfaceKind
import app.pane.android.ui.media.applyCarry
import app.pane.android.ui.media.carry
import app.pane.android.ui.media.surfaceGestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackHandoffTest {
    @Test
    fun inlinePositionAndSoundCarryIntoFullscreen() {
        val session = session()
        val inline = session.acquire(POST, MEDIA, URL, freshMuted = true).player
        inline.positionMs = 12_345L
        inline.volume = 0.8f
        inline.playWhenReady = true

        val fullscreen = session.acquire(POST, MEDIA, URL, freshMuted = true)

        assertTrue(fullscreen.reused)
        assertSame(inline, fullscreen.player)
        assertEquals(12_345L, fullscreen.player.positionMs)
        assertEquals(0.8f, fullscreen.player.volume, 0.001f)
        assertTrue(fullscreen.player.playWhenReady)
        assertFalse(fullscreen.player.carry().muted)
    }

    @Test
    fun fullscreenPositionMuteAndPauseCarryBackInline() {
        val session = session()
        val inline = session.acquire(POST, MEDIA, URL, freshMuted = false).player
        inline.positionMs = 4_000L
        val fullscreen = session.acquire(POST, MEDIA, URL, freshMuted = true).player
        fullscreen.positionMs = 40_250L
        fullscreen.volume = 0f
        fullscreen.playWhenReady = false

        val back = session.acquire(POST, MEDIA, URL, freshMuted = true)

        assertTrue(back.reused)
        assertSame(inline, back.player)
        assertEquals(40_250L, back.player.positionMs)
        assertEquals(0f, back.player.volume, 0.001f)
        assertTrue(back.player.carry().muted)
        assertFalse(back.player.playWhenReady)
    }

    @Test
    fun aFreshOpenStartsMutedAtTheBeginning() {
        val acquired = session().acquire(POST, MEDIA, URL, freshMuted = true)

        assertFalse(acquired.reused)
        assertEquals(0L, acquired.player.positionMs)
        assertEquals(0f, acquired.player.volume, 0.001f)
        assertTrue(acquired.player.playWhenReady)
        assertEquals(
            listOf("volume:0.0", "play:false", "load:$URL@0", "seek:0", "play:true"),
            acquired.player.events,
        )
    }

    @Test
    fun recreatingAPlayerSeeksBeforeItPlaysAndKeepsVolume() {
        val source = FakePlayer()
        source.load(URL, 0L)
        source.positionMs = 8_888L
        source.volume = 0.35f
        source.playWhenReady = false
        val replacement = FakePlayer()

        applyCarry(replacement, HIGH, source.carry())

        assertEquals(8_888L, replacement.positionMs)
        assertEquals(0.35f, replacement.volume, 0.001f)
        assertFalse(replacement.playWhenReady)
        assertEquals(HIGH, replacement.mediaUrl)
        val loadAt = replacement.events.indexOfFirst { it.startsWith("load:") }
        val playAt = replacement.events.indexOfLast { it.startsWith("play:") }
        assertTrue(loadAt in 0 until playAt)
    }

    @Test
    fun aQualityChangeKeepsPositionAndSoundOnTheSamePlayer() {
        val session = session()
        val player = session.acquire(POST, MEDIA, URL, freshMuted = true).player
        player.positionMs = 15_000L
        player.volume = 1f
        player.playWhenReady = true

        assertTrue(session.switchUrl(POST, MEDIA, HIGH))

        assertEquals(HIGH, player.mediaUrl)
        assertEquals(15_000L, player.positionMs)
        assertEquals(1f, player.volume, 0.001f)
        assertTrue(player.playWhenReady)
        assertSame(player, session.acquire(POST, MEDIA, URL, freshMuted = true).player)
        assertEquals(HIGH, player.mediaUrl)
    }

    @Test
    fun gestureNavigationLeavesTheSharedPlayerInPlace() {
        val session = session()
        val inline = session.acquire(POST, MEDIA, URL, freshMuted = true).player
        inline.positionMs = 1_500L
        inline.volume = 0.4f
        inline.playWhenReady = false

        assertEquals(
            SurfaceGestureAction.ToggleControls,
            surfaceGestureAction(VideoSurfaceKind.Inline, SurfaceGesture.SingleTap),
        )
        assertEquals(
            SurfaceGestureAction.EnterFullscreen,
            surfaceGestureAction(VideoSurfaceKind.Inline, SurfaceGesture.DoubleTap),
        )
        assertEquals(
            SurfaceGestureAction.ExitFullscreen,
            surfaceGestureAction(VideoSurfaceKind.Fullscreen, SurfaceGesture.DoubleTap),
        )

        val fullscreen = session.acquire(POST, MEDIA, URL, freshMuted = true)
        assertTrue(fullscreen.reused)
        assertSame(inline, fullscreen.player)
        assertEquals(1_500L, fullscreen.player.positionMs)
        assertEquals(0.4f, fullscreen.player.volume, 0.001f)
        assertFalse(fullscreen.player.playWhenReady)
    }

    @Test
    fun anotherItemKeepsItsOwnPositionWhileTheFirstParks() {
        val session = session()
        val first = session.acquire(POST, MEDIA, URL, freshMuted = true).player
        first.positionMs = 5_000L
        first.volume = 0.4f
        first.playWhenReady = true

        val second = session.acquire(POST, "video-2", URL, freshMuted = true)
        assertFalse(second.reused)
        assertEquals(0L, second.player.positionMs)
        assertEquals(0f, second.player.volume, 0.001f)

        val back = session.acquire(POST, MEDIA, URL, freshMuted = false)
        assertFalse(back.reused)
        assertEquals(5_000L, back.player.positionMs)
        assertEquals(0.4f, back.player.volume, 0.001f)
        assertTrue(back.player.playWhenReady)
    }

    @Test
    fun parkingKeepsTheMapAndReleaseClearsIt() {
        val session = session()
        val player = session.acquire(POST, MEDIA, URL, freshMuted = true).player
        player.positionMs = 9_000L
        session.park()
        assertEquals(null, session.player)
        val resumed = session.acquire(POST, MEDIA, URL, freshMuted = true)
        assertEquals(9_000L, resumed.player.positionMs)
        session.release()
        val fresh = session.acquire(POST, MEDIA, URL, freshMuted = true)
        assertEquals(0L, fresh.player.positionMs)
        assertEquals(
            listOf("volume:0.0", "play:false", "load:$URL@0", "seek:0", "play:true"),
            fresh.player.events,
        )
    }

    @Test
    fun manualRotationReusesThePlayerAndKeepsPosition() {
        val session = session()
        val before = session.acquire(POST, MEDIA, URL, freshMuted = true).player
        before.positionMs = 22_000L
        before.volume = 0.6f
        before.playWhenReady = true

        val after = session.acquire(POST, MEDIA, URL, freshMuted = true)

        assertTrue(after.reused)
        assertSame(before, after.player)
        assertEquals(22_000L, after.player.positionMs)
        assertEquals(0.6f, after.player.volume, 0.001f)
        assertTrue(after.player.playWhenReady)
        assertFalse(after.player.events.contains("release"))
    }

    @Test
    fun leavingThePostDropsThePlayer() {
        val session = session()
        val player = session.acquire(POST, MEDIA, URL, freshMuted = true).player
        session.retainOnly(POST)
        assertSame(player, session.player)
        session.retainOnly(null)
        assertEquals(null, session.player)
        assertTrue(player.events.contains("release"))
        val fresh = session.acquire(POST, MEDIA, URL, freshMuted = true)
        assertFalse(fresh.reused)
        assertEquals(0L, fresh.player.positionMs)
    }

    private fun session() = PlaybackSession { FakePlayer() }

    private class FakePlayer : ContinuablePlayer {
        val events = mutableListOf<String>()
        override var mediaUrl: String? = null
        override var positionMs: Long = 0L
            set(value) {
                field = value
                events += "seek:$value"
            }
        override var playWhenReady: Boolean = false
            set(value) {
                field = value
                events += "play:$value"
            }
        override var volume: Float = 1f
            set(value) {
                field = value
                events += "volume:$value"
            }

        override fun load(url: String, positionMs: Long) {
            events += "load:$url@$positionMs"
            mediaUrl = url
            this.positionMs = positionMs
        }

        override fun release() {
            events += "release"
        }
    }

    private companion object {
        const val POST = "https://www.facebook.com/share/19j1v6TgD9/"
        const val MEDIA = "video-1"
        const val URL = "https://video.example/clip.mp4"
        const val HIGH = "https://video.example/clip-high.mp4"
    }
}
