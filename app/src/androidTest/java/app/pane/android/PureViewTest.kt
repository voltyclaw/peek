package app.pane.android

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import app.pane.android.ui.home.HomeView
import app.pane.android.ui.model.ViewerUiState
import app.pane.android.ui.player.PlayerView
import app.pane.android.ui.preview.PeekPreviewFixtures
import app.pane.android.ui.theme.PaneTheme
import app.pane.android.ui.viewer.ViewerView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PureViewTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeRendersRepositoryStateAndForwardsSelection() {
        var openedUrl: String? = null
        composeRule.setContent {
            PaneTheme {
                HomeView(
                    uiState = PeekPreviewFixtures.home,
                    onPasteClick = {},
                    onRecentLink = { openedUrl = it },
                )
            }
        }

        composeRule.onNodeWithText("A quiet morning in Kyoto").assertIsDisplayed().performClick()
        assertEquals("preview://kyoto", openedUrl)
    }

    @Test
    fun homeForwardsPasteFromEmptyState() {
        var pasteClicked = false
        composeRule.setContent {
            PaneTheme {
                HomeView(
                    uiState = app.pane.android.ui.model.HomeUiState.Empty,
                    onPasteClick = { pasteClicked = true },
                    onRecentLink = {},
                )
            }
        }

        composeRule.onNodeWithText("Paste from clipboard").assertIsDisplayed().performClick()
        assertEquals(true, pasteClicked)
    }

    @Test
    fun viewerRendersStateAndForwardsBack() {
        var backPressed = false
        composeRule.setContent {
            PaneTheme {
                ViewerView(
                    uiState = PeekPreviewFixtures.video,
                    onBack = { backPressed = true },
                    onRefresh = {},
                    onOpenMedia = {},
                )
            }
        }

        composeRule.onNodeWithText("Mara Chen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(true, backPressed)
    }

    @Test
    fun viewerOpensPhotoAtItsCarouselIndex() {
        var openedIndex: Int? = null
        composeRule.setContent {
            PaneTheme {
                ViewerView(
                    uiState = PeekPreviewFixtures.carousel,
                    onBack = {},
                    onRefresh = {},
                    onOpenMedia = { openedIndex = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Kyoto photo").performClick()
        assertEquals(1, openedIndex)
    }

    @Test
    fun viewerCopiesTheSelectedCarouselItem() {
        var copiedMediaId: String? = null
        composeRule.setContent {
            PaneTheme {
                ViewerView(
                    uiState = PeekPreviewFixtures.carousel,
                    onBack = {},
                    onRefresh = {},
                    onOpenMedia = {},
                    onCopyMedia = { copiedMediaId = it.id },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Kyoto photo").performTouchInput {
            swipeRight()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Media options").performClick()
        composeRule.onNodeWithText("Copy media").performClick()
        composeRule.waitForIdle()

        assertEquals("carousel-one", copiedMediaId)
    }

    @Test
    fun fullScreenShowsTheSelectedPhotoAndTapClosesIt() {
        var backed = false
        composeRule.setContent {
            PaneTheme {
                PlayerView(
                    uiState = PeekPreviewFixtures.carousel,
                    initialMediaIndex = 1,
                    onBack = { backed = true },
                    onMore = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Kyoto photo").assertIsDisplayed()
        composeRule.onNodeWithText("Mara Chen").assertDoesNotExist()
        composeRule.onNodeWithText("PHOTO").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Kyoto photo").performClick()
        assertEquals(true, backed)
    }

    @Test
    fun loadingViewerShowsBackWithoutViewerChrome() {
        var backPressed = false
        composeRule.setContent {
            PaneTheme {
                ViewerView(ViewerUiState.Loading(), onBack = { backPressed = true }, onRefresh = {}, onOpenMedia = {})
            }
        }

        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("VIEWING POST").assertDoesNotExist()
        composeRule.onNodeWithText("VIDEO PREVIEW").assertDoesNotExist()
        assertEquals(true, backPressed)
    }

    @Test
    fun loadingViewerShowsCancelThatTriggersBack() {
        var backPressed = false
        composeRule.setContent {
            PaneTheme {
                ViewerView(ViewerUiState.Loading(0.4f, "Fetching the page"), onBack = { backPressed = true }, onRefresh = {}, onOpenMedia = {})
            }
        }

        composeRule.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        assertEquals(true, backPressed)
    }
}
