package app.pane.android

import app.pane.android.ui.navigation.BackBehavior
import org.junit.Assert.assertEquals
import org.junit.Test

class BackBehaviorTest {
    @Test
    fun missingOrUnknownStorageKeepsBackClosingPeek() {
        assertEquals(BackBehavior.ClosePeek, BackBehavior.fromStorage(null))
        assertEquals(BackBehavior.ClosePeek, BackBehavior.fromStorage(""))
        assertEquals(BackBehavior.ClosePeek, BackBehavior.fromStorage("somewhere-else"))
    }

    @Test
    fun storedNamesRoundTrip() {
        assertEquals(BackBehavior.ClosePeek, BackBehavior.fromStorage(BackBehavior.ClosePeek.storageValue))
        assertEquals(BackBehavior.GoHome, BackBehavior.fromStorage(BackBehavior.GoHome.storageValue))
    }
}
