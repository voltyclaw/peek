package app.pane.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import app.pane.android.ui.home.HomeView
import app.pane.android.ui.preview.PeekPreviewFixtures
import app.pane.android.ui.theme.PaneTheme
import app.pane.android.ui.viewer.ViewerView

@PreviewTest
@Preview(name = "Home Reference", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun homeReference() {
    PaneTheme { HomeView(PeekPreviewFixtures.home, {}, {}) }
}

@PreviewTest
@Preview(name = "Post Reference", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun postReference() {
    PaneTheme { ViewerView(PeekPreviewFixtures.post, onBack = {}, onRefresh = {}, onOpenMedia = {}) }
}

@PreviewTest
@Preview(name = "Video Reference", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun videoReference() {
    PaneTheme { ViewerView(PeekPreviewFixtures.video, onBack = {}, onRefresh = {}, onOpenMedia = {}) }
}

@PreviewTest
@Preview(name = "Loading Reference", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun loadingReference() {
    PaneTheme { ViewerView(PeekPreviewFixtures.loading, onBack = {}, onRefresh = {}, onOpenMedia = {}) }
}

@PreviewTest
@Preview(name = "Unsupported Link Reference", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun unsupportedLinkReference() {
    PaneTheme { ViewerView(PeekPreviewFixtures.unavailable, onBack = {}, onRefresh = {}, onOpenMedia = {}) }
}

@PreviewTest
@Preview(name = "Home Compact", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun homeCompact() {
    PaneTheme { HomeView(PeekPreviewFixtures.home, {}, {}) }
}

@PreviewTest
@Preview(name = "Viewer Wide Phone", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
fun viewerWidePhone() {
    PaneTheme { ViewerView(PeekPreviewFixtures.video, onBack = {}, onRefresh = {}, onOpenMedia = {}) }
}
