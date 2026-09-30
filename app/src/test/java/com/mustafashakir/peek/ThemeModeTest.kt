package com.mustafashakir.peek

import com.mustafashakir.peek.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
