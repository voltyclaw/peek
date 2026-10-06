package app.pane.android

import app.pane.android.ui.navigation.PeekBackAction
import app.pane.android.ui.navigation.peekBackAction
import app.pane.android.ui.navigation.peekLeaveVideoAction
import org.junit.Assert.assertEquals
import org.junit.Test

class PeekBackTest {
    @Test
    fun immersiveBackReturnsToTheFramedPost() {
        assertEquals(PeekBackAction.Pop, peekBackAction(launchedFromViewLink = true, stackSize = 3, topIsPlayer = true))
        assertEquals(PeekBackAction.Pop, peekBackAction(launchedFromViewLink = true, stackSize = 4, topIsPlayer = true))
    }

    @Test
    fun aSharedLinkFinishesBackToTheCallingAppFromTheFramedPost() {
        assertEquals(PeekBackAction.Finish, peekBackAction(launchedFromViewLink = true, stackSize = 2))
    }

    @Test
    fun sharedLinkBackFinishesEvenBelowADeeperStack() {
        assertEquals(
            PeekBackAction.Pop,
            peekBackAction(launchedFromViewLink = true, stackSize = 3, topIsPlayer = true),
        )
        assertEquals(
            PeekBackAction.Finish,
            peekBackAction(launchedFromViewLink = true, stackSize = 3),
        )
        assertEquals(
            PeekBackAction.Finish,
            peekBackAction(launchedFromViewLink = true, stackSize = 2),
        )
        assertEquals(
            PeekBackAction.DeferToSystem,
            peekBackAction(launchedFromViewLink = true, stackSize = 1),
        )
    }

    @Test
    fun endOfVideoLeaveReturnsToTheOpenerOrHome() {
        assertEquals(PeekBackAction.Finish, peekLeaveVideoAction(launchedFromViewLink = true))
        assertEquals(PeekBackAction.ClearToHome, peekLeaveVideoAction(launchedFromViewLink = false))
    }

    @Test
    fun aHomeSessionPopsUntilHomeThenLeavesBackToTheSystem() {
        assertEquals(PeekBackAction.Pop, peekBackAction(launchedFromViewLink = false, stackSize = 3))
        assertEquals(PeekBackAction.Pop, peekBackAction(launchedFromViewLink = false, stackSize = 2))
        assertEquals(PeekBackAction.DeferToSystem, peekBackAction(launchedFromViewLink = false, stackSize = 1))
        assertEquals(PeekBackAction.DeferToSystem, peekBackAction(launchedFromViewLink = true, stackSize = 1))
    }
}
