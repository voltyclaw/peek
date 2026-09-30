package app.pane.android

import app.pane.android.ui.navigation.PeekBackAction
import app.pane.android.ui.navigation.peekBackAction
import org.junit.Assert.assertEquals
import org.junit.Test

class PeekBackTest {
    @Test
    fun aSharedLinkFinishesBackToTheCallingApp() {
        assertEquals(PeekBackAction.Finish, peekBackAction(launchedFromViewLink = true, stackSize = 3))
        assertEquals(PeekBackAction.Finish, peekBackAction(launchedFromViewLink = true, stackSize = 2))
    }

    @Test
    fun backGoesHomeStaysInPeekEvenWhenALinkOpenedTheApp() {
        assertEquals(
            PeekBackAction.Pop,
            peekBackAction(launchedFromViewLink = true, stackSize = 3, backClosesPeek = false),
        )
        assertEquals(
            PeekBackAction.Pop,
            peekBackAction(launchedFromViewLink = true, stackSize = 2, backClosesPeek = false),
        )
        assertEquals(
            PeekBackAction.DeferToSystem,
            peekBackAction(launchedFromViewLink = true, stackSize = 1, backClosesPeek = false),
        )
    }

    @Test
    fun aHomeSessionPopsUntilHomeThenLeavesBackToTheSystem() {
        assertEquals(PeekBackAction.Pop, peekBackAction(launchedFromViewLink = false, stackSize = 3))
        assertEquals(PeekBackAction.Pop, peekBackAction(launchedFromViewLink = false, stackSize = 2))
        assertEquals(PeekBackAction.DeferToSystem, peekBackAction(launchedFromViewLink = false, stackSize = 1))
        assertEquals(PeekBackAction.DeferToSystem, peekBackAction(launchedFromViewLink = true, stackSize = 1))
    }
}
