package app.pane.android

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.pane.android.ui.theme.LightPaneColors
import app.pane.android.ui.theme.paneTypography
import app.pane.android.ui.theme.tokens.PaneTokenType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokensTest {
    @Test
    fun materialTypographyUsesTheT2Scale() {
        val type = paneTypography()
        val display = PaneTokenType.Display(app.pane.android.ui.theme.PaneDisplay, app.pane.android.ui.theme.Inter)
        val title = PaneTokenType.Title(app.pane.android.ui.theme.PaneDisplay, app.pane.android.ui.theme.Inter)
        val titlePage = PaneTokenType.TitlePage(app.pane.android.ui.theme.PaneDisplay, app.pane.android.ui.theme.Inter)
        val body = PaneTokenType.Body(app.pane.android.ui.theme.PaneDisplay, app.pane.android.ui.theme.Inter)
        val label = PaneTokenType.Label(app.pane.android.ui.theme.PaneDisplay, app.pane.android.ui.theme.Inter)
        val meta = PaneTokenType.Meta(app.pane.android.ui.theme.PaneDisplay, app.pane.android.ui.theme.Inter)
        assertEquals(display, type.displayLarge)
        assertEquals(title, type.titleLarge)
        assertEquals(titlePage, type.titleMedium)
        assertEquals(body, type.bodyLarge)
        assertEquals(label, type.labelLarge)
        assertEquals(meta, type.labelSmall)
        assertEquals(44.sp, type.displayLarge.fontSize)
        assertEquals(FontWeight(600), type.titleLarge.fontWeight)
        assertEquals(13.sp, type.labelSmall.fontSize)
        assertEquals(0.em, type.labelSmall.letterSpacing)
        assertEquals(FontWeight(500), type.displayLarge.fontWeight)
    }

    @Test
    fun geistAliasesAndEightSpTextAreGone() {
        val roots = sourceRoot().resolve("src/main/java").walk().filter { it.extension == "kt" }
        roots.forEach { file ->
            val text = file.readText()
            assertFalse(file.path, Regex("""\bGeist(Mono)?\b""").containsMatchIn(text))
            assertFalse(file.path, Regex("""(?<![\d.])8\.sp""").containsMatchIn(text))
        }
    }

    @Test
    fun coralIsNotTheAppAccentAndPlayerHexesUseTokens() {
        val themes = sourceFile("src/main/res/values/themes.xml").readText()
        val themesV27 = sourceFile("src/main/res/values-v27/themes.xml").readText()
        assertFalse(themes.contains("#D4886A"))
        assertFalse(themesV27.contains("Theme.Material.Light"))
        assertTrue(themes.contains("@color/pane_color_mute"))
        assertTrue(themesV27.contains("@color/pane_color_mute"))
        listOf(
            "src/main/java/app/pane/android/ui/tiktok/TikTokSurface.kt",
            "src/main/java/app/pane/android/ui/player/PlayerView.kt",
            "src/main/java/app/pane/android/ui/media/VideoPlayback.kt",
        ).forEach { relative ->
            val text = sourceFile(relative).readText()
            assertFalse(text, text.contains("0xFFDAD4CE"))
            assertFalse(text, text.contains("0xFFF4EFEA"))
            assertFalse(text, text.contains("0xFF8A827A"))
            assertFalse(text, text.contains("0xFF262018"))
            assertFalse(text, text.contains("0xFF191412"))
        }
    }

    @Test
    fun variableFontsAreBundledAndLightPaletteStays() {
        assertTrue(sourceFile("src/main/res/font/inter_variable.ttf").isFile)
        assertTrue(sourceFile("src/main/res/font/inter_tight_variable.ttf").isFile)
        assertFalse(sourceFile("src/main/res/font/inter_regular.ttf").exists())
        assertEquals(LightPaneColors.night, true)
        val theme = sourceFile("src/main/java/app/pane/android/ui/theme/Theme.kt").readText()
        assertTrue(theme.contains("lightColorScheme"))
        assertTrue(theme.contains("LightPaneColors"))
    }

    private fun sourceFile(relative: String): File {
        val direct = sourceRoot().resolve(relative)
        if (direct.exists() || direct.parentFile?.isDirectory == true) return direct
        return File(relative)
    }

    private fun sourceRoot(): File {
        if (File("src/main").isDirectory) return File(".")
        if (File("app/src/main").isDirectory) return File("app")
        return File(".")
    }
}
