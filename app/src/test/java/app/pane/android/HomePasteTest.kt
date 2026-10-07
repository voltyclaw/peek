package app.pane.android

import app.pane.android.ui.home.PasteOutcome
import app.pane.android.ui.home.pasteOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePasteTest {
    @Test
    fun anEmptyClipboardSaysNothingToPaste() {
        assertEquals(PasteOutcome.Empty, pasteOutcome(null, null))
        assertEquals(PasteOutcome.Empty, pasteOutcome("   ", null))
    }

    @Test
    fun clipboardTextWithoutALinkIsNotTheEmptyMessage() {
        assertEquals(PasteOutcome.NotALink, pasteOutcome("hello", null))
        assertEquals(PasteOutcome.Ready, pasteOutcome("https://x.com/ereliuer_eteer/status/1", "https://x.com/ereliuer_eteer/status/1"))
    }
}
