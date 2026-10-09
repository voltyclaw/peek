package app.pane.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pane.android.ui.home.HomeView
import app.pane.android.ui.theme.PaneTheme
import app.pane.android.ui.viewer.ViewerView

@Preview(name = "Home 390×844", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun HomePreview() {
    PaneTheme { HomeView(PeekPreviewFixtures.home, {}, {}) }
}

@Preview(name = "Post 390×844", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun PostPreview() {
    PaneTheme { ViewerView(PeekPreviewFixtures.post, onBack = {}, onRefresh = {}, onOpenMedia = {}) }
}

@Preview(name = "Video 390×844", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun VideoPreview() {
    PaneTheme { ViewerView(PeekPreviewFixtures.video, onBack = {}, onRefresh = {}, onOpenMedia = {}) }
}

@Preview(name = "Loading 390×844", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun LoadingPreview() {
    PaneTheme { ViewerView(PeekPreviewFixtures.loading, onBack = {}, onRefresh = {}, onOpenMedia = {}) }
}

@Preview(name = "Unsupported link 390×844", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun UnsupportedLinkPreview() {
    PaneTheme { ViewerView(PeekPreviewFixtures.unavailable, onBack = {}, onRefresh = {}, onOpenMedia = {}) }
}
