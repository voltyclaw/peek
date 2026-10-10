package app.pane.android

import app.pane.android.data.webview.ScraperProfileJanitor
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Test

class ScraperProfileJanitorTest {
    @Test
    fun wipeRunsOnlyWhenNothingIsAliveAndAgainAfterTheLastRelease() {
        val wipes = AtomicInteger(0)
        val janitor = ScraperProfileJanitor(supported = { true }, recreate = { wipes.incrementAndGet() })

        janitor.wipe()
        assertEquals(1, wipes.get())

        janitor.acquire()
        janitor.acquire()
        janitor.wipe()
        assertEquals(1, wipes.get())
        assertEquals(2, janitor.liveCount())

        janitor.releaseAndMaybeWipe()
        assertEquals(1, janitor.liveCount())
        assertEquals(1, wipes.get())

        janitor.releaseAndMaybeWipe()
        assertEquals(0, janitor.liveCount())
        assertEquals(2, wipes.get())
    }

    @Test
    fun wipeSkipsTheProfileWhenMultiProfileIsUnavailable() {
        val wipes = AtomicInteger(0)
        val janitor = ScraperProfileJanitor(supported = { false }, recreate = { wipes.incrementAndGet() })
        janitor.acquire()
        janitor.releaseAndMaybeWipe()
        assertEquals(0, wipes.get())
    }
}
