package app.pane.android

import android.content.Context
import app.pane.android.ui.theme.DARK_THEME_ONLY
import app.pane.android.ui.theme.ThemeMode
import app.pane.android.ui.theme.ThemePreferences
import app.pane.android.ui.theme.forCurrentBuild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class ThemeModeTest {
    @Test
    fun lightStaysLight() {
        assertFalse(ThemeMode.Light.resolve(systemDark = true))
        assertFalse(ThemeMode.Light.resolve(systemDark = false))
    }

    @Test
    fun darkStaysDark() {
        assertTrue(ThemeMode.Dark.resolve(systemDark = true))
        assertTrue(ThemeMode.Dark.resolve(systemDark = false))
    }

    @Test
    fun systemFollowsThePlatform() {
        assertTrue(ThemeMode.System.resolve(systemDark = true))
        assertFalse(ThemeMode.System.resolve(systemDark = false))
    }

    @Test
    fun storedNameRoundTripsAndUnknownValuesUseSystem() {
        assertEquals(ThemeMode.Light, ThemeMode.fromStorage(ThemeMode.Light.storageValue))
        assertEquals(ThemeMode.Dark, ThemeMode.fromStorage(ThemeMode.Dark.storageValue))
        assertEquals(ThemeMode.System, ThemeMode.fromStorage(ThemeMode.System.storageValue))
        assertEquals(ThemeMode.System, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.System, ThemeMode.fromStorage("sepia"))
    }

    @Test
    fun thisBuildForcesDarkButKeepsTheOtherModes() {
        assertTrue(DARK_THEME_ONLY)
        assertEquals(ThemeMode.Dark, ThemeMode.Light.forCurrentBuild())
        assertEquals(ThemeMode.Dark, ThemeMode.System.forCurrentBuild())
        assertEquals(ThemeMode.Dark, ThemeMode.Dark.forCurrentBuild())
        assertEquals(3, ThemeMode.entries.size)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemePreferenceMigrationTest {
    @Test
    fun savedLightAndSystemAreRewrittenToDark() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("pane_options", Context.MODE_PRIVATE)
        ThemeMode.entries.filter { it != ThemeMode.Dark }.forEach { saved ->
            prefs.edit().putString("theme_mode", saved.storageValue).commit()
            assertEquals(ThemeMode.Dark, ThemePreferences.read(context))
            assertEquals(ThemeMode.Dark.storageValue, prefs.getString("theme_mode", null))
        }
        prefs.edit().remove("theme_mode").commit()
        assertEquals(ThemeMode.Dark, ThemePreferences.read(context))
        assertEquals(ThemeMode.Dark.storageValue, prefs.getString("theme_mode", null))
    }
}
