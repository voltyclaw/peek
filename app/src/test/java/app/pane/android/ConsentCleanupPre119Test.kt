package app.pane.android

import app.pane.android.data.webview.PRE119_WITHDRAW_COPY_IS_FULL
import app.pane.android.data.webview.SiteClearPath
import app.pane.android.data.webview.selectSiteClearPath
import app.pane.android.data.webview.withdrawSheetIsFull
import app.pane.android.data.webview.withdrawalSnackbarIsFull
import app.pane.android.domain.source.EmbedNote
import app.pane.android.ui.home.withdrawBodyRes
import app.pane.android.ui.home.withdrawnSnackbarRes
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentCleanupPre119Test {
    @Test
    fun pre119SheetAndSnackbarStayPartialForEveryCombination() {
        val flags = listOf(false, true)
        for (idbVerified in flags) {
            for (cacheCleared in flags) {
                for (layoutExpected in flags) {
                    for (webViewLive in flags) {
                        val sheet = withdrawSheetIsFull(
                            multiProfileSupported = false,
                            profileExists = true,
                            profileAssigned = true,
                        )
                        val snackbar = withdrawalSnackbarIsFull(
                            multiProfileSupported = false,
                            profileDeleted = false,
                            idbVerified = idbVerified,
                            cacheCleared = cacheCleared,
                            layoutExpected = layoutExpected,
                            webViewLive = webViewLive,
                        )
                        assertFalse(sheet)
                        assertFalse(snackbar)
                        assertEquals(sheet, snackbar)
                        assertEquals(R.string.consent_youtube_withdraw_body_partial, withdrawBodyRes(EmbedNote.YouTube, sheet))
                        assertEquals(R.string.consent_tiktok_withdraw_body_partial, withdrawBodyRes(EmbedNote.TikTok, snackbar))
                        assertEquals(R.string.consent_withdrawn_snackbar_partial, withdrawnSnackbarRes(snackbar))
                    }
                }
            }
        }
        assertFalse(PRE119_WITHDRAW_COPY_IS_FULL)
        assertEquals(
            SiteClearPath.OriginsFull,
            selectSiteClearPath(
                multiProfileSupported = false,
                idbVerified = true,
                cacheCleared = true,
                layoutExpected = true,
                webViewLive = false,
            ),
        )
        assertEquals(
            SiteClearPath.OriginsPartial,
            selectSiteClearPath(
                multiProfileSupported = false,
                idbVerified = true,
                cacheCleared = true,
                layoutExpected = false,
                webViewLive = false,
            ),
        )
        assertEquals(
            SiteClearPath.OriginsPartial,
            selectSiteClearPath(
                multiProfileSupported = false,
                idbVerified = true,
                cacheCleared = false,
            ),
        )
        assertEquals(
            SiteClearPath.OriginsPartial,
            selectSiteClearPath(
                multiProfileSupported = false,
                idbVerified = true,
                cacheCleared = true,
                layoutExpected = true,
                webViewLive = true,
            ),
        )
        assertEquals(SiteClearPath.Profile, selectSiteClearPath(multiProfileSupported = true, idbVerified = true, cacheCleared = true))
        val profileSheet = withdrawSheetIsFull(multiProfileSupported = true, profileExists = true, profileAssigned = true)
        val profileSnackbar = withdrawalSnackbarIsFull(
            multiProfileSupported = true,
            profileDeleted = true,
            idbVerified = true,
            cacheCleared = true,
            layoutExpected = true,
            webViewLive = false,
        )
        assertEquals(profileSheet, profileSnackbar)
        assertEquals(R.string.consent_withdrawn_snackbar_full, withdrawnSnackbarRes(profileSnackbar))
    }

    @Test
    fun withdrawalCopyTextIsUnchanged() {
        val english = loadStrings("values")
        assertEquals(
            "This withdraws your consent and clears YouTube's cookies and most site data and saved video details from Pane. Data Google already holds is not affected. Your stars, notes and tags stay. Pane asks again the next time you open a YouTube link.",
            english.getValue("consent_youtube_withdraw_body_partial"),
        )
        assertEquals(
            "This withdraws your consent and clears TikTok's cookies and most site data and saved TikTok details from Pane. Your stars, notes and tags stay. Pane asks again the next time you open a TikTok link.",
            english.getValue("consent_tiktok_withdraw_body_partial"),
        )
        assertEquals(
            "Consent withdrawn. %1\$s cookies and most site data cleared.",
            english.getValue("consent_withdrawn_snackbar_partial"),
        )
        val hebrew = loadStrings("values-he")
        val iw = loadStrings("values-iw")
        listOf(
            "consent_youtube_withdraw_body_partial",
            "consent_tiktok_withdraw_body_partial",
            "consent_withdrawn_snackbar_partial",
        ).forEach { key ->
            assertEquals(hebrew.getValue(key), iw.getValue(key))
            assertTrue(hebrew.getValue(key).contains("רוב"))
        }
    }

    private fun loadStrings(valuesDir: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(sourceFile("src/main/res/$valuesDir/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        val values = linkedMapOf<String, String>()
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            val name = node.attributes.getNamedItem("name").nodeValue
            values[name] = node.textContent.replace("\\'", "'").replace("\\\"", "\"")
        }
        return values
    }

    private fun sourceFile(relative: String): File {
        val direct = File(relative)
        if (direct.isFile) return direct
        val nested = File("app/$relative")
        if (nested.isFile) return nested
        return direct
    }
}
