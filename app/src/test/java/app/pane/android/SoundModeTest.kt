package app.pane.android

import app.pane.android.ui.navigation.SoundMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundModeTest {
    @Test
    fun missingStorageStartsMuted() {
        assertEquals(SoundMode.Muted, SoundMode.fromStorage(null))
        assertEquals(SoundMode.Muted, SoundMode.fromStorage(""))
        assertEquals(SoundMode.Muted, SoundMode.fromStorage("loud"))
    }

    @Test
    fun storedNamesRoundTrip() {
        assertEquals(SoundMode.Muted, SoundMode.fromStorage(SoundMode.Muted.storageValue))
        assertEquals(SoundMode.On, SoundMode.fromStorage(SoundMode.On.storageValue))
        assertEquals(SoundMode.RememberLast, SoundMode.fromStorage(SoundMode.RememberLast.storageValue))
    }
}
