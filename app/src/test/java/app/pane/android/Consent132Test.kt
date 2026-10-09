package app.pane.android

import app.pane.android.data.webview.EmbedWebProfiles
import app.pane.android.data.webview.SiteClearPath
import app.pane.android.data.webview.withdrawClearsAllSiteData
import app.pane.android.data.webview.withdrawSheetIsFull
import app.pane.android.ui.tiktok.TikTokConsentLinks
import app.pane.android.domain.source.EmbedNote
import app.pane.android.ui.home.withdrawBodyRes
import app.pane.android.ui.home.withdrawnSnackbarRes
import app.pane.android.ui.tiktok.tikTokAgreementAnnotated
import app.pane.android.ui.youtube.YouTubeConsentLinks
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Consent132Test {
    @Test
    fun hebrewTikTokAgreementKeepsFourLinksOutsideThePrefixes() {
        listOf("values-he", "values-iw").forEach { locale ->
            val strings = loadStrings(locale)
            val template = strings.getValue("tt_agree_line")
            val labels = listOf(
                strings.getValue("tt_agree_link_pane_terms"),
                strings.getValue("tt_agree_link_pane_privacy"),
                strings.getValue("tt_agree_link_tiktok_terms"),
                strings.getValue("tt_agree_link_tiktok_privacy"),
            )
            val rendered = String.format(template, *labels.toTypedArray())
            assertEquals(
                "ההפעלה משמעותה הסכמה לתנאי השימוש ולמדיניות הפרטיות של Pane. חלים גם תנאי השירות ומדיניות הפרטיות של TikTok.",
                rendered,
            )
            val slotted = String.format(template, "\u0001", "\u0002", "\u0003", "\u0004")
            val annotated = tikTokAgreementAnnotated(
                slotted,
                listOf(
                    "\u0001" to (labels[0] to TikTokConsentLinks.PANE_TERMS),
                    "\u0002" to (labels[1] to YouTubeConsentLinks.panePrivacy("he")),
                    "\u0003" to (labels[2] to TikTokConsentLinks.TIKTOK_TERMS),
                    "\u0004" to (labels[3] to TikTokConsentLinks.TIKTOK_PRIVACY),
                ),
            )
            assertEquals(rendered, annotated.text)
            assertEquals(4, annotated.getLinkAnnotations(0, annotated.length).size)
        }
    }

    @Test
    fun englishTikTokAgreementStillReadsAsTheFullSentence() {
        val strings = loadStrings("values")
        val rendered = String.format(
            strings.getValue("tt_agree_line"),
            strings.getValue("tt_agree_link_pane_terms"),
            strings.getValue("tt_agree_link_pane_privacy"),
            strings.getValue("tt_agree_link_tiktok_terms"),
            strings.getValue("tt_agree_link_tiktok_privacy"),
        )
        assertEquals(
            "By playing, you agree to Pane's Terms and Privacy Policy. TikTok's Terms and Privacy Policy also apply.",
            rendered,
        )
        val surface = sourceFile("src/main/java/app/pane/android/ui/tiktok/TikTokSurface.kt").readText()
        assertFalse(surface.contains("indexOf(\"Pane's Terms\")"))
    }

    @Test
    fun withdrawSheetIsChosenBeforeClearAndSnackbarAfterIt() {
        assertFalse(withdrawSheetIsFull(multiProfileSupported = false, profileExists = true, profileAssigned = true))
        assertFalse(withdrawSheetIsFull(multiProfileSupported = true, profileExists = false, profileAssigned = true))
        assertFalse(withdrawSheetIsFull(multiProfileSupported = true, profileExists = true, profileAssigned = false))
        assertTrue(withdrawSheetIsFull(multiProfileSupported = true, profileExists = true, profileAssigned = true))

        assertTrue(EmbedWebProfiles.predictsFullSheet(EmbedWebProfiles.YOUTUBE, multiProfileSupported = true, profileExists = true))
        assertTrue(EmbedWebProfiles.predictsFullSheet(EmbedWebProfiles.TIKTOK, multiProfileSupported = true, profileExists = true))
        assertFalse(EmbedWebProfiles.predictsFullSheet(EmbedWebProfiles.INSTAGRAM, multiProfileSupported = true, profileExists = true))
        assertFalse(EmbedWebProfiles.predictsFullSheet(EmbedWebProfiles.THREADS, multiProfileSupported = true, profileExists = true))
        assertFalse(EmbedWebProfiles.predictsFullSheet(EmbedWebProfiles.YOUTUBE, multiProfileSupported = true, profileExists = false))

        EmbedNote.entries.forEach { note ->
            assertTrue(withdrawBodyRes(note, fullClear = true) != withdrawBodyRes(note, fullClear = false))
        }
        assertEquals(R.string.consent_youtube_withdraw_body_full, withdrawBodyRes(EmbedNote.YouTube, fullClear = true))
        assertEquals(R.string.consent_youtube_withdraw_body_partial, withdrawBodyRes(EmbedNote.YouTube, fullClear = false))
        assertEquals(R.string.consent_tiktok_withdraw_body_partial, withdrawBodyRes(EmbedNote.TikTok, fullClear = false))
        assertEquals(R.string.consent_instagram_withdraw_body_partial, withdrawBodyRes(EmbedNote.Instagram, fullClear = false))
        assertEquals(R.string.consent_threads_withdraw_body_partial, withdrawBodyRes(EmbedNote.Threads, fullClear = false))

        val sheetPromisedFull = withdrawSheetIsFull(true, true, true)
        val deleteFailed = withdrawClearsAllSiteData(SiteClearPath.Profile, profileDeleted = false)
        assertTrue(sheetPromisedFull)
        assertFalse(deleteFailed)
        assertEquals(R.string.consent_withdrawn_snackbar_full, withdrawnSnackbarRes(true))
        assertEquals(R.string.consent_withdrawn_snackbar_partial, withdrawnSnackbarRes(deleteFailed))
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
