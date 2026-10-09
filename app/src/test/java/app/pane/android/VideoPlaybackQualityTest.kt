package app.pane.android

import app.pane.android.ui.media.VideoPlaybackQuality
import app.pane.android.ui.media.VideoQuality
import app.pane.android.ui.model.VideoSourceUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackQualityTest {
    @Test
    fun autoUsesAdaptiveAndHighUsesTheProgressiveFile() {
        val sources = listOf(
            VideoSourceUiModel(url = "https://v.redd.it/clip/DASHPlaylist.mpd", adaptive = true),
            VideoSourceUiModel(url = "https://v.redd.it/clip/DASH_720.mp4", width = 1280, height = 720),
        )

        assertEquals("https://v.redd.it/clip/DASHPlaylist.mpd", VideoPlaybackQuality.pick(sources, VideoQuality.Auto))
        assertEquals("https://v.redd.it/clip/DASH_720.mp4", VideoPlaybackQuality.pick(sources, VideoQuality.High))
        assertNull(VideoPlaybackQuality.pick(sources, VideoQuality.Low))
        assertEquals(
            listOf(VideoQuality.Auto, VideoQuality.High),
            VideoPlaybackQuality.options(sources).map { it.quality },
        )
    }

    @Test
    fun progressiveLadderUsesHighMiddleAndLow() {
        val sources = listOf(
            VideoSourceUiModel(url = "https://video.example/low.mp4", bitrate = 256_000),
            VideoSourceUiModel(url = "https://video.example/mid.mp4", bitrate = 832_000),
            VideoSourceUiModel(url = "https://video.example/high.mp4", bitrate = 2_176_000),
        )

        assertEquals("https://video.example/mid.mp4", VideoPlaybackQuality.pick(sources, VideoQuality.Auto))
        assertEquals("https://video.example/high.mp4", VideoPlaybackQuality.pick(sources, VideoQuality.High))
        assertEquals("https://video.example/mid.mp4", VideoPlaybackQuality.pick(sources, VideoQuality.Medium))
        assertEquals("https://video.example/low.mp4", VideoPlaybackQuality.pick(sources, VideoQuality.Low))
        val options = VideoPlaybackQuality.options(sources)
        assertEquals(listOf(VideoQuality.Auto, VideoQuality.High, VideoQuality.Low), options.map { it.quality })
        assertTrue(options.none { it.quality == VideoQuality.Medium })
    }

    @Test
    fun aSingleUrlHidesTheQualityControl() {
        val sources = listOf(VideoSourceUiModel(url = "https://video.example/only.mp4", bitrate = 800_000))

        assertTrue(VideoPlaybackQuality.options(sources).isEmpty())
        assertEquals(
            "https://video.example/only.mp4",
            VideoPlaybackQuality.urlFor(sources, VideoQuality.High, "https://video.example/only.mp4"),
        )
    }

    @Test
    fun knownHeightsUseTheirOwnLabels() {
        val sources = listOf(
            VideoSourceUiModel(url = "https://video.example/hd.mp4", width = 1920, height = 1080),
            VideoSourceUiModel(url = "https://video.example/sd.mp4", width = 854, height = 480),
        )

        assertEquals(
            listOf("Auto", "1080p", "480p"),
            VideoPlaybackQuality.renditions(sources).map { it.label },
        )
        assertEquals("https://video.example/hd.mp4", VideoPlaybackQuality.pick(sources, VideoQuality.High))
        assertEquals("https://video.example/sd.mp4", VideoPlaybackQuality.pick(sources, VideoQuality.Low))
        assertNull(VideoPlaybackQuality.resolutionLabel(null))
        assertEquals("720p", VideoPlaybackQuality.resolutionLabel(720))
        assertEquals("640p", VideoPlaybackQuality.resolutionLabel(640))
    }

    @Test
    fun portraitUsesTheShortSide() {
        val sources = listOf(
            VideoSourceUiModel(url = "https://video.example/tall.mp4", width = 1080, height = 1920, bitrate = 4_000_000),
            VideoSourceUiModel(url = "https://video.example/mid.mp4", width = 720, height = 1280, bitrate = 1_500_000),
        )

        assertEquals(
            listOf("Auto", "1080p", "720p"),
            VideoPlaybackQuality.renditions(sources).map { it.label },
        )
        assertEquals("https://video.example/tall.mp4", VideoPlaybackQuality.pick(sources, VideoQuality.High))
        assertEquals("https://video.example/mid.mp4", VideoPlaybackQuality.pick(sources, VideoQuality.Low))
    }

    @Test
    fun duplicateHeightsKeepTheBetterBitrateAndCapTheLadder() {
        val sources = (listOf(2160, 1440, 1080, 720, 540, 480, 360, 240)).map { height ->
            VideoSourceUiModel(
                url = "https://video.example/$height.mp4",
                width = height * 16 / 9,
                height = height,
                bitrate = height * 1_000,
            )
        } + VideoSourceUiModel(
            url = "https://video.example/1080-small.mp4",
            width = 1920,
            height = 1080,
            bitrate = 100,
        )

        val menu = VideoPlaybackQuality.renditions(sources)
        assertEquals(
            listOf("Auto", "2160p", "1440p", "1080p", "720p", "240p"),
            menu.map { it.label },
        )
        assertEquals("https://video.example/1080.mp4", menu.first { it.label == "1080p" }.url)
        assertEquals("https://video.example/2160.mp4", VideoPlaybackQuality.pick(sources, VideoQuality.High))
        assertEquals("https://video.example/1080.mp4", VideoPlaybackQuality.pick(sources, VideoQuality.Medium))
        assertEquals("https://video.example/240.mp4", VideoPlaybackQuality.pick(sources, VideoQuality.Low))
    }

    @Test
    fun unlabeledVariantsCollapseToFourRows() {
        val sources = (1..12).map { index ->
            VideoSourceUiModel(url = "https://video.example/$index.mp4", bitrate = index * 100_000)
        }

        assertEquals(
            listOf("Auto", "High", "Medium", "Low"),
            VideoPlaybackQuality.renditions(sources).map { it.label },
        )
        assertEquals("https://video.example/12.mp4", VideoPlaybackQuality.pick(sources, VideoQuality.High))
        assertEquals("https://video.example/1.mp4", VideoPlaybackQuality.pick(sources, VideoQuality.Low))
        assertEquals("https://video.example/6.mp4", VideoPlaybackQuality.pick(sources, VideoQuality.Medium))
    }

    @Test
    fun missingHeightsStayHighAndLow() {
        val sources = listOf(
            VideoSourceUiModel(url = "https://video.example/high.mp4", bitrate = 2_000_000),
            VideoSourceUiModel(url = "https://video.example/low.mp4", bitrate = 400_000),
        )

        assertEquals(
            listOf("Auto", "High", "Low"),
            VideoPlaybackQuality.renditions(sources).map { it.label },
        )
    }

    @Test
    fun duplicateUrlsDoNotInventASecondQuality() {
        val sources = listOf(
            VideoSourceUiModel(url = "https://video.example/same.mp4", width = 640, height = 360),
            VideoSourceUiModel(url = "https://video.example/same.mp4", bitrate = 500_000),
        )

        assertTrue(VideoPlaybackQuality.options(sources).isEmpty())
    }
}
